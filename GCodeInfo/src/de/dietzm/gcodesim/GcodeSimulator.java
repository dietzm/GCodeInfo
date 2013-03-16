package de.dietzm.gcodesim;

import java.awt.Color;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;

import de.dietzm.gcodesim.GcodePainter.Commands;


/**
 * Gcode simulator - Free for non-commercial use.
 * 
 * @author mail@dietzm.de
 */

@SuppressWarnings("serial")
public class GcodeSimulator extends Frame {

	/**
	 * 0.55 Added x/y Offset when gcodes are out of range
	 * 0.6 Added "Loading Model..."  , optimize repaint
	 * 0.65 Added Model details
	 * 0.66 toggle details (model, layer , layer speed , model speed, layer summary) 
	 * 0.67 press (f) to open another file
	 * 0.68 Java 1.6 compatible 
	 * 0.69 Fixed average values (height,temp) , support skeinforge comments , guess diameter, show weight and price
	 * 0.70 Refactored Simulator to abstract AWT implementation (allow other UI implementations like android)
	 * 0.71 Undisruptive zoom (no restart), nextLayer completes the layer painting (not skips it), previous layer cmd added. Printbed grid.
	 * 0.80 Nice looking labels for current infos,  pageing for layer details, about/help dialog.
	 * 		Fixed acceleration (ignore acceleration for speed distribution), use acceleration for paint and layer time. 
	 * 		Smoother Painting by splitting longer lines into multiple 
	 * 0.81 Add mouse listerners , Add default gcode , percent of loading
	 * 0.90 Add Front and Side View, Add experimental printing support (/dev/ttyUSB0 , s-shortcut) 
	 */
	public static final String VERSION = "v0.90";	
	GcodePainter gp;
	AWTGraphicRenderer awt;


	public GcodeSimulator() {
		setTitle("GCode Print Simulator " + VERSION);
		setBackground(Color.black);
	}
	
	public void init(String filename,InputStream in) throws IOException{
		awt = new AWTGraphicRenderer(GcodePainter.bedsizeX, GcodePainter.bedsizeY,this);
		gp = new GcodePainter(awt);
		updateSize();
		setVisible(true);	
		setBackground(Color.black);
		addListeners();
		gp.start(filename,in);				
	}

	public GcodeSimulator getFrame(){
	return this;
	}

	/**
	 * Main class for GCodeSimulator
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		GcodeSimulator gs = new GcodeSimulator();
		String filename;
		InputStream in = null;
		if (args.length < 1 || !new File(args[0]).exists()) {
			filename = "/gcodesim.gcode";
			in= gs.getClass().getResourceAsStream(filename);			
		} else {
			filename = args[0];
		}
		gs.init(filename,in);
		gs.requestFocus();

	}

	static String openFileBrowser(Frame gs) {
		String filename =null;
//		JFileChooser fc = new JFileChooser();
//		fc.setFileFilter(new FileFilter() {
//			
//			@Override
//			public String getDescription() {
//				return "*.gcode";
//			}
//			
//			@Override
//			public boolean accept(File arg0) {
//				if(arg0.getName().toLowerCase().endsWith(".gcode")) return true;
//				if(arg0.isDirectory()) return true;
//				return false;
//			}
//		});
//		
//		int ret = fc.showOpenDialog(gs);
//		if (ret == JFileChooser.APPROVE_OPTION) {
//			filename = fc.getSelectedFile().getAbsolutePath();
//		}else{
//			System.exit(0);
//		}
	
// 		Native file dialog is better but it has a bug when selecting recent files :-(
		
		FileDialog fd = new FileDialog(gs, "Choose a gcode file");
		fd.setModal(true);
		fd.setFilenameFilter(new FilenameFilter() {
			@Override
			public boolean accept(File arg0, String arg1) {
				if(arg1.toLowerCase().endsWith(".gcode")) return true;
				return false;
			}
		});
		fd.requestFocus();
		fd.setVisible(true);
		// Choosing a "recently used" file will fail because of redhat
		// bugzilla 881425 / jdk 7165729
		if (fd.getFile() == null)	return null;
		filename = fd.getDirectory() + fd.getFile();
		
		return filename;
	}

	private void updateSize() {
		int[] sz = gp.getSize();
		setSize(sz[0],sz[1]);
	}
	
	private void addListeners() {
		
		addMouseWheelListener(new MouseWheelListener() {
			
			@Override
			public void mouseWheelMoved(MouseWheelEvent arg0) {
				int mwrot=arg0.getWheelRotation();
				
				if( arg0.isControlDown() || arg0.isAltDown() ) {
				//Zoom
					if (gp.getZoom() > 1 && mwrot < 0)	gp.setZoom((float) gp.getZoom() + mwrot/10f);
					if (gp.getZoom() < 8 && mwrot > 0)	gp.setZoom(gp.getZoom() + mwrot/10f);
					updateSize();
				}else{
					//Speedup
					if( mwrot > 0){
						gp.toggleSpeed(false);
					}else{
						gp.toggleSpeed(true);
					}
					
				}
			}
		});
		
		addMouseListener(new MouseListener() {
			
			@Override
			public void mouseReleased(MouseEvent arg0) {
			}
			
			@Override
			public void mousePressed(MouseEvent arg0) {
			}
			
			@Override
			public void mouseExited(MouseEvent arg0) {
			}
			
			@Override
			public void mouseEntered(MouseEvent arg0) {
			}
			
			@Override
			public void mouseClicked(MouseEvent arg0) {
				if(arg0.getButton() == MouseEvent.BUTTON3){
					if(arg0.isAltDown() || arg0.isControlDown()){
						gp.setCmd(Commands.OPENFILE);
					}else{
						gp.toggleModeldetails();
						updateSize();
					}
				}else if(arg0.getButton() == MouseEvent.BUTTON2){
						gp.showHelp();
				}else{
					if(arg0.getPoint().x > GcodePainter.bedsizeX * gp.getZoom()+gp.gap ){
						gp.toggleType();
					}else{
						if(arg0.isAltDown() || arg0.isControlDown()){
							gp.setCmd(Commands.PREVIOUSLAYER);
						}else {
							gp.setCmd(Commands.NEXTLAYER);
						}
					}
				}
				
			}
		});
		// WindowListener
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent arg0) {
				if (arg0.getKeyChar() == '+') {
					gp.toggleSpeed(true);
				} else if (arg0.getKeyChar() == '-') {
					gp.toggleSpeed(false);
				} else if (arg0.getKeyChar() == 'n') { // next layer
					gp.setCmd(Commands.NEXTLAYER);
				} else if (arg0.getKeyChar() == 'b') { // layer before
					gp.setCmd(Commands.PREVIOUSLAYER);
				} else if (arg0.getKeyChar() == 'd') { // debug
					gp.setCmd(Commands.DEBUG);
				} else if (arg0.getKeyChar() == 'm') { // modeldetails
					gp.toggleModeldetails();
					updateSize();
				} else if (arg0.getKeyChar() == 't') { // detailstype
					gp.toggleType();
				} else if (arg0.getKeyChar() == 'r') { // restart
					gp.setCmd(Commands.RESTART);
				} else if (arg0.getKeyChar() == 'i') { // zoom in
					if (gp.getZoom() < 8)
						gp.setZoom(gp.getZoom() + 0.5f);
					updateSize();
					// TODO
					// printstroke = new BasicStroke(zoom - 0.5f,
					// BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
					// gp.setCmd(Commands.REPAINTLAYERS);
				} else if (arg0.getKeyChar() == 'o') { // zoom out
					if (gp.getZoom() > 1)
						gp.setZoom((float) gp.getZoom() - 0.5f);
					updateSize();
					// TODO
					// printstroke = new BasicStroke(zoom - 0.5f,
					// BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
					// gp.setCmd(Commands.RESTART);
				} else if (arg0.getKeyChar() == 'q') {
					System.exit(0);
				} else if (arg0.getKeyChar() == 'f') { // open file
					gp.setCmd(Commands.OPENFILE);
				} else if (arg0.getKeyChar() == 'p') {
					gp.togglePause();
				} else if (arg0.getKeyChar() == 's') {
					gp.togglePrint();
				} else {
					gp.showHelp();
				}

			}

		

			@Override
			public void keyReleased(KeyEvent arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void keyPressed(KeyEvent arg0) {
				// TODO Auto-generated method stub

			}
		});
	}

	@Override
	public void paint(Graphics g) {
		g.drawImage(awt.getImage(), 4, 28, this);		
		super.paint(g);
	}
	
	
	/**
	 * Overwritten to avoid "clear" on every paint which causes flashing
	 * 
	 * @param g
	 */
	@Override
	public void update(Graphics g) {
		paint(g);
	}

}

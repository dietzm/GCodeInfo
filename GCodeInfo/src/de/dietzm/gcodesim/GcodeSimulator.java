package de.dietzm.gcodesim;

import java.awt.Color;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

import de.dietzm.Model;
import de.dietzm.gcodesim.GcodePainter.Commands;


/**
 * Gcode simulator - Free for non-commercial use.
 * 
 * @author mail@dietzm.de
 */

@SuppressWarnings("serial")
public class GcodeSimulator extends Frame implements ActionListener {




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
	 * 0.91 Show current speed and remaining print time. Fixed double buffering bug. 
	 * 0.92 Fixed double buffering bug for MAC OS. Show modeldetails by default. Added Menubar 
	 * 0.93 Fixed some multi-threading bugs. Some performance improvements. Icon added. zoom on resize. filedialog path and timer.
	 * 0.94 Fixed temperatur bug , optimize label repaint,
	 * 0.95 Experimental Edit Mode added (Modify menu) 
	 * 0.96 Display Extrusion speed, pause and changed fan output. Fixed grey spots bug.
	 * 0.97 More resilient against errors (Ignore some unknown Gcodes instead of failing) 
	 */
	public static final String VERSION = "v0.97";	
	GcodePainter gp;
	AWTGraphicRenderer awt;
	boolean showdetails =true;


	public GcodeSimulator() {
		setTitle("GCode Print Simulator " + VERSION);
		setBackground(Color.black);
	}
	
	public void init(String filename,InputStream in) throws IOException{
		awt = new AWTGraphicRenderer(GcodePainter.bedsizeX, GcodePainter.bedsizeY,this);
		gp = new GcodePainter(awt);
		updateSize(showdetails);
		setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource( "/icon.png" )));
		
		setMenuBar(getMyMenubar());
		setVisible(true);
		setBackground(Color.black);
		
		addListeners();
		gp.start(filename,in);				
	}
	
	
	private void addMenuItem(Menu parent, String name, String key, int keycode){
		MenuItem it = new MenuItem(name);
	    it.addActionListener(this);
	    it.setActionCommand(key);
	    //MenuShortcut ms = new MenuShortcut(KeyEvent.getExtendedKeyCodeForChar(key.charAt(0))); ONLY JAVA 1.7
	    MenuShortcut ms = new MenuShortcut(keycode);
	    it.setShortcut(ms);
	    parent.add (it);
	}
	
	protected MenuBar getMyMenubar () {
		    MenuBar ml = new MenuBar ();
		    Menu datei = new Menu ("File");
		    addMenuItem(datei, "Load File", "f",KeyEvent.VK_F);
		    addMenuItem(datei, "Exit", "q",KeyEvent.VK_Q);

		    ml.add(datei);		    
		    
		    Menu control = new Menu ("Control");
		    addMenuItem(control, "Pause", "p",KeyEvent.VK_P);
		    control.addSeparator();
		    addMenuItem(control, "Increase Speed", "+",KeyEvent.VK_PLUS);
		    addMenuItem(control, "Decrease Speed", "-",KeyEvent.VK_MINUS);
		    control.addSeparator();
		    addMenuItem(control, "Next Layer", "n",KeyEvent.VK_N);
		    addMenuItem(control, "Previous Layer", "b",KeyEvent.VK_B);
		    control.addSeparator();
		    addMenuItem(control, "Restart", "r",KeyEvent.VK_R);
		    
		    
		
		    
		    Menu view = new Menu ("View");
		    addMenuItem(view, "Zoom In", "i",KeyEvent.VK_I);
		    addMenuItem(view, "Zoom Out", "o",KeyEvent.VK_O);
		    view.addSeparator();
		    addMenuItem(view, "Show/Hide Details", "m",KeyEvent.VK_M);
		    addMenuItem(view, "Toggle Detail type", "t",KeyEvent.VK_T);
		    		    
		    Menu about = new Menu ("About");
		    addMenuItem(about, "About/Help", "h",KeyEvent.VK_H);
		    		    
		    
		    Menu edit = new Menu ("Modify (Experimental)");
		    edit.add("Experimental Edit Mode");
		    edit.addSeparator();
		    addMenuItem(edit, "Speedup Layer by 10%", "w",KeyEvent.VK_W);
		    addMenuItem(edit, "Slowdown Layer by 10%", "e",KeyEvent.VK_E);
		    addMenuItem(edit, "Increase extrusion by 10%", "z",KeyEvent.VK_Z);
		    addMenuItem(edit, "Decrease extrusion by 10%", "u",KeyEvent.VK_U);
		    addMenuItem(edit, "Delete layer", "g",KeyEvent.VK_G);
		    addMenuItem(edit, "Save Modifications", "a",KeyEvent.VK_A);
		    
		    
		    ml.add(control);
		    ml.add(edit);
		    ml.add(view);
		    ml.add(about);
		    
		    
		    return ml;
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
	
	@Override
	public void actionPerformed(ActionEvent arg0) {
		char a = arg0.getActionCommand().charAt(0);
		//Forward Event to keylistener (don't duplicate code)
		getKeyListeners()[0].keyTyped(new KeyEvent(this, 0, 0, 0, (int)a,a));
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
		final FileDialog fd = new FileDialog(gs, "Choose a gcode file");
		final Thread gpt = Thread.currentThread();
// 		Native file dialog is better but it has a bug when selecting recent files :-(
		Timer t = new Timer(); 
		t.schedule(new TimerTask() {
			@Override
			public void run() {
				if(fd.isVisible()){					
					gpt.interrupt();
					fd.setVisible(false);
				}
			}
		}, 100000);
		
		if(new File(System.getProperty("user.home")+"/Desktop/3D/MODELS").exists()){
			fd.setDirectory(System.getProperty("user.home")+"/Desktop/3D/MODELS");
		}else{
				fd.setDirectory(System.getProperty("user.dir"));
		}
		
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
		t.cancel();
		return filename;
	}

	private void updateSize(boolean details) {
		if((getExtendedState() & Frame.MAXIMIZED_BOTH) == 0){
			int[] sz = gp.getSize(details);
			setSize(sz[0],sz[1]);
		}
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
					updateSize(showdetails);
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
						showdetails=!showdetails;
						updateSize(showdetails);
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
			
		}
		
	);
		
		addComponentListener(new ComponentAdapter() {

			@Override
			public void componentResized(ComponentEvent e) {
				super.componentResized(e);
				float size = getHeight();
				float currsize = gp.getSize(false)[1];
				
				if(currsize!=size){
					//System.out.println("Size:"+size+" Curr:"+currsize);
					//float fac = size/currsize;
					float fac = (size-(55+(size/12)))/GcodePainter.bedsizeY;
					//float z = gp.getZoom();
					//System.out.println("Zoom:"+z);
					//gp.setZoom(z*(fac));
					gp.setZoom((fac));
				}
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
					showdetails=!showdetails;
					updateSize(showdetails);
				} else if (arg0.getKeyChar() == 't') { // detailstype
					gp.toggleType();
				} else if (arg0.getKeyChar() == 'r') { // restart
					gp.setCmd(Commands.RESTART);
				} else if (arg0.getKeyChar() == 'i') { // zoom in
					if (gp.getZoom() < 8)
						gp.setZoom(gp.getZoom() + 0.5f);
						updateSize(showdetails);
					// TODO
					// printstroke = new BasicStroke(zoom - 0.5f,
					// BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
					// gp.setCmd(Commands.REPAINTLAYERS);
				} else if (arg0.getKeyChar() == 'o') { // zoom out
					if (gp.getZoom() > 1)
						gp.setZoom((float) gp.getZoom() - 0.5f);
						updateSize(showdetails);

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
				} else if (arg0.getKeyChar() == 'h') {
					gp.showHelp();
					
				//EDIT MODE
				} else if (arg0.getKeyChar() == 'g') {
					Model.deleteLayer(Collections.singleton(gp.getCurrentLayer()));
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'w') {
					Model.changeSpeed(Collections.singleton(gp.getCurrentLayer()),10);
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'e') {
					Model.changeSpeed(Collections.singleton(gp.getCurrentLayer()),-10);
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'z') {
					Model.changeExtrusion(Collections.singleton(gp.getCurrentLayer()),10);
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'u') {
					Model.changeExtrusion(Collections.singleton(gp.getCurrentLayer()),-10);
					gp.setCmd(Commands.REANALYSE);
				} else if (arg0.getKeyChar() == 'a') {
					try {
						gp.model.saveModel(gp.model.getFilename()+"-new");
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					if(arg0.getKeyCode() != 0){ //ignore CTRL modifiers
						gp.showHelp();
					}
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

	
	public void paint(Graphics g) {
		//g.drawImage(awt.getImage(), 4, 28, this);
		awt.drawImage(g);
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

package de.dietzm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

/**
 * Gcode simulator - Free for non-commercial use.
 * 
 * @author mail@dietzm.de
 */

@SuppressWarnings("serial")
public class GcodeSimulator extends Frame implements Runnable {

	/**
	 * 0.55 Added x/y Offset when gcodes are out of range
	 * 0.6 Added "Loading Model..."  , optimize repaint
	 * 0.65 Added Model details
	 * 0.66 toggle details (model, layer , layer speed , model speed, layer summary) 
	 * 0.67 press (f) to open another file
	 * 0.68 Java 1.6 compatible 
	 * 0.69 Fixed average values (height,temp) , support skeinforge comments , guess diameter, show weight and price
	 */
	public static final String VERSION = "v0.69";
	
	// Model & Layer Info
	Model model = null;
	ArrayList<Layer> layers;

	int zoom = 4;
	float speedup = 5;
	int pause = 0;
	float Xoffset=0,Yoffset=0;
	private static final int bedsizeX = 200;
	private static final int bedsizeY = 200;
	private static final boolean PAINTTRAVEL = true;
	String cmd = null;
	boolean modeldetails=false;
	int detailstype=0;
	

	private BufferedImage offImg;
	Thread gcodepainter=null;
	private final GraphicsConfiguration gfxConf = GraphicsEnvironment.getLocalGraphicsEnvironment()
			.getDefaultScreenDevice().getDefaultConfiguration();
	private BasicStroke travelstroke = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1,
			new float[] { 1, 2 }, 0);
	private BasicStroke printstroke = new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);

	public GcodeSimulator() {
		setBackground(Color.black);
		setSize(bedsizeX * zoom + 40, bedsizeY * zoom + 100);
		setTitle("GCode Print Simulator " + VERSION);
		addListeners();
		setVisible(true);
	}
public GcodeSimulator getFrame(){
	return this;
}

	public void start(String filename) throws IOException {
		repaint();
		model = new Model(filename);
		// *6 because max zoom is 6
		offImg = gfxConf.createCompatibleImage(bedsizeX * 7, bedsizeY * 7);
		
		//Start repaint timer
		Timer repainttimer = new Timer();
		repainttimer.schedule(new TimerTask() {			
			@Override
			public void run() {
				repaint();				
			}
		},500, 500);
		
		model.loadModel();
		model.analyze();
		
		layers = new ArrayList<Layer>(model.getLayer().values());
		Collections.sort(layers);
		gcodepainter = new Thread(this);
		repainttimer.cancel(); //Stop repainttimer
		gcodepainter.start();
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
		if (args.length < 1 || !new File(args[0]).exists()) {
			filename = openFileBrowser(gs);
		} else {
			filename = args[0];
		}
		
		gs.start(filename);
		gs.requestFocus();

	}

	private static String openFileBrowser(GcodeSimulator gs) {
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
		if (fd.getFile() == null)
			System.exit(0); // cancel pressed
		filename = fd.getDirectory() + fd.getFile();
		
		return filename;
	}

	private void addListeners() {
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
					if(speedup >= 1){
						speedup++;					
					}else{
						speedup+=0.1;
					}
					gcodepainter.interrupt();
				}
				if (arg0.getKeyChar() == '-') {
					if (speedup > 1){
						speedup--;
					}else if (speedup > 0.5){
						speedup-=0.1;
					}
					gcodepainter.interrupt();
				}
				if (arg0.getKeyChar() == 'n') { // next layer
					cmd = "n";
					gcodepainter.interrupt();
				}
				if (arg0.getKeyChar() == 'd') { // debug
					cmd = "d";
					gcodepainter.interrupt();
				}
				if (arg0.getKeyChar() == 'm') { // modeldetails
					modeldetails=!modeldetails;
					setSize(bedsizeX * zoom + 40+(modeldetails?487:0), bedsizeY * zoom + 100);
					if(modeldetails){
						cmd="p";
						gcodepainter.interrupt();
					}
				}
				if (arg0.getKeyChar() == 't') { // detailstype
					if(detailstype<4){
						detailstype++;
					}else {
						detailstype=0;
					}
					if(modeldetails){
						cmd="p";
						gcodepainter.interrupt();
					}
				}
				
				if (arg0.getKeyChar() == 'r') { // restart
					cmd = "r";
					gcodepainter.interrupt();
				}
				if (arg0.getKeyChar() == 'i') { // zoom in
					if (zoom < 6)
						zoom++;
					setSize(bedsizeX * zoom + 40+(modeldetails?487:0), bedsizeY * zoom + 100);
					printstroke = new BasicStroke(zoom - 0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
					cmd = "r";
					gcodepainter.interrupt();
				}
				if (arg0.getKeyChar() == 'o') { // zoom out
					if (zoom > 1)
						zoom--;
					setSize(bedsizeX * zoom + 40+(modeldetails?487:0), bedsizeY * zoom + 100);
					printstroke = new BasicStroke(zoom - 0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
					cmd = "r";
					gcodepainter.interrupt();
				}

				if (arg0.getKeyChar() == 'q') {
					System.exit(0);
				}
				if (arg0.getKeyChar() == 'f') { //open file
					cmd = "e";
					gcodepainter.interrupt();
					
				}
				if (arg0.getKeyChar() == 'p') {
					if (pause == 0) {
						pause = 999999;
					} else {
						pause = 0;
						gcodepainter.interrupt();
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

	@Override
	public void paint(Graphics g) {
		if(gcodepainter == null){
			g.setColor(Color.red);
			g.setFont(g.getFont().deriveFont(50f));
			g.drawString("Please Wait",220,200);
			g.drawString("Loading Model......",200,270);
			if(model != null){
				g.clearRect(200,285,300,75);
				g.drawString(""+model.getGcodes().size(),220,340);
			}
		}else{
			g.drawImage(offImg, 10, 40, this);
		}
		super.paint(g);
	}

	@Override
	/**
	 * Paint the gcodes to a offline image (offbuf) and trigger repaint
	 */
	public void run() {
		Color[] colors = new Color[] { Color.red, Color.blue, Color.yellow, Color.cyan, Color.green,
				Color.magenta, Color.orange };
		Color travel = Color.darkGray;
		Graphics2D g2 = (Graphics2D) offImg.getGraphics();
		// g2.scale(zoom, zoom);
		
		calculateOffset();

		while (true) {
			if (layers != null) {
				A: for (Layer lay : layers) {
					float activespeedup = speedup;

					Color col = colors[lay.getNumber() % colors.length];
					g2.setColor(col);
					paintLabel(g2, lay);
					printBed(g2, col, lay);

					ArrayList<GCode> gc = lay.getGcodes();
					float[] lastpos = null;

					// Paint all Gcodes
					for (GCode gCode : gc) {
						float[] pos = gCode.getCurrentPosition();
						if (lastpos != null) {
							if (gCode.isExtruding()) {
								g2.setColor(col);								
								printLine(g2, lastpos, pos);
							} else if (PAINTTRAVEL) {
								g2.setColor(travel);
								g2.setStroke(travelstroke);
								printLine(g2, lastpos, pos);
								g2.setStroke(printstroke);
							}
							if (gCode.getFanspeed()!=0){
								//TODO draw a small fan 
							}
						}
						lastpos = pos;

						// Sleep for the time the gcode took
						try {
							if (activespeedup != speedup) {
								activespeedup = speedup;
								paintLabel(g2, lay);
							}
							if ("n".equals(cmd)) { // next layer
								cmd = null;
								continue A;
							}
							if ("d".equals(cmd)) { // next layer
								cmd = null;
								System.out.println(gCode);
							}
							if ("r".equals(cmd)) { // restart
								cmd = null;
								g2.clearRect(0, 0, offImg.getWidth(), offImg.getHeight());
								break A;
							}
							if("p".equals(cmd)){ //repaint labels 
								cmd=null;
								paintLabel(g2, lay);
							}
							if("e".equals(cmd)){ //exit
								cmd=null;
								g2.clearRect(0, 0, offImg.getWidth(), offImg.getHeight());
								String file = openFileBrowser(getFrame());
								if(file != null) {									
									gcodepainter= null;
									model=null;
									layers=null;
								}
								try {
									start(file);
									requestFocus();
								} catch (IOException e) {
									e.printStackTrace();
								}
								return;
							}
							Thread.sleep((int) ((gCode.getTime() * 1000) / activespeedup) + pause);
						} catch (InterruptedException e) {
						}
						repaint();
					}

				}
				g2.clearRect(0, 0, bedsizeX + 10, bedsizeY);
			}
		}

	}

	private void calculateOffset() {
		//Calculate offset
		if(model.getBoundaries()[0] >= bedsizeX || model.getBoundaries()[1] <= 0){
			//67,139/2  =  0
			//250-150=200
			float midpoint = model.getBoundaries()[0]-(model.getDimension()[0]/2);
			Xoffset = bedsizeX/2-midpoint;
		}else{
			Xoffset=0;
		}
		if(model.getBoundaries()[2] >= bedsizeY || model.getBoundaries()[3] <= 0){
			//67,139/2  =  0
			//250-150=200
			float midpoint = model.getBoundaries()[2]-(model.getDimension()[1]/2);
			Yoffset = bedsizeY/2-midpoint;
		}else{
			Yoffset=0;
		}
		System.out.println("X/Y Offset:"+Xoffset+"/"+Yoffset);
	}

	private void printLine(Graphics2D g2, float[] lastpos, float[] pos) {
		g2.draw(new Line2D.Float((lastpos[0]+Xoffset) * zoom, (bedsizeY * zoom) - ((lastpos[1]+Yoffset) * zoom), (pos[0]+Xoffset) * zoom,
				(bedsizeY * zoom) - ((pos[1]+Yoffset) * zoom)));

	}

	private void printBed(Graphics2D g2, Color layercol, Layer lay) {
		g2.setColor(Color.white);
		g2.drawRect(1, 1, bedsizeX * zoom, bedsizeY * zoom); // Draw print bed
		g2.drawRect(bedsizeX * zoom + 1, 1, 20, bedsizeY * zoom); // Draw print
																	// bed
		g2.setColor(layercol);
		float factor = (bedsizeY * zoom - 2) / (model.getDimension()[2] * 10);
		// System.out.println("Print:"+factor+" "+(int)((lay.getZPosition()*10)*factor)+" LH:"+lay.getLayerheight());
		g2.fillRect(bedsizeX * zoom + 1, bedsizeY * zoom - (int) ((lay.getZPosition() * 10) * factor), 20,
				(int) (lay.getLayerheight() * 10 * factor)); // Draw print bed
		
		if(Xoffset != 0 || Yoffset != 0){
			g2.setStroke(travelstroke);
			g2.setColor(Color.white);
			g2.draw(new Line2D.Float(Xoffset*zoom-10, Yoffset*zoom, Xoffset*zoom+100, Yoffset*zoom));
			g2.draw(new Line2D.Float(Xoffset*zoom, Yoffset*zoom+10, Xoffset*zoom, Yoffset*zoom-100));
			g2.setStroke(printstroke);
		}
	}

	private void paintLabel(Graphics2D g2, Layer lay) {
		g2.clearRect(1, bedsizeY * zoom + 5, bedsizeX * zoom+(modeldetails?457:0), bedsizeY * zoom);
		String label = lay.getLayerSummaryReport() + "   Speed:" + speedup + "x ("+GCode.round2digits(lay.getTime()/speedup)+")";
		g2.drawString(label, 1, bedsizeY * zoom + 15);
		String help = "Key Help: i/o=Zoom +/-=Speed n=NextLayer r=Restart p=Pause q=Quit m=Details t=toggleDetails f=OpenFile";
		g2.drawString(help, 1, bedsizeY * zoom + 30);
		if (modeldetails){
			g2.clearRect(bedsizeX * zoom + 30, 1, 480, bedsizeY * zoom); // Draw print
			g2.drawRect(bedsizeX * zoom + 30, 1, 480, bedsizeY * zoom); // Draw print			
			// bed
			String details;
			switch (detailstype) {
			case 0:
				 details = "--------------- Model details ------------------\n"+model.getModelDetailReport()+model.guessPrice(model.guessDiameter());
				break;
			case 1:
				 details = "--------------- Layer details ------------------\n"+lay.getLayerDetailReport();
				break;
			case 2:
				details = model.getModelLayerSummaryReport();
				break;
			case 3:
				 details = model.getModelSpeedReport();
				break;
			case 4:
				details = lay.getLayerSpeedReport();
				break;
			default:
				 details = model.getModelDetailReport();
				break;
			}
			
			
			String[] det = details.split("\n");
			for (int i = 0; i < det.length; i++) {
				int y = 30+i*25; //avoid painting across the border
				if(y >= bedsizeY*zoom) break;
				g2.drawString(det[i], bedsizeX*zoom+35,y );
				
			}
			
		}
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

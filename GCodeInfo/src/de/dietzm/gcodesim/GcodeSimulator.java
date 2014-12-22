package de.dietzm.gcodesim;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Label;
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
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import de.dietzm.Constants;
import de.dietzm.Position;
import de.dietzm.Temperature;
import de.dietzm.gcodes.GCodeFactory;
import de.dietzm.gcodes.bufferfactory.GCodeFactoryBuffer;
import de.dietzm.gcodesim.GcodePainter.Commands;
import de.dietzm.print.ConsoleIf;
import de.dietzm.print.Dummy;
import de.dietzm.print.ReceiveBuffer;
import de.dietzm.print.SerialPrinter;


/**
 * Gcode simulator - Free for non-commercial use.
 * 
 * @author mail@dietzm.de
 */

@SuppressWarnings("serial")
public class GcodeSimulator extends JFrame implements ActionListener {

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
	 * 0.98 Support for center format G2/G3 Gcodes (Arc) - Radius format not supported yet
	 * 0.99 clicked on speedup label, toggles pause. Replaced blue with a lighter pink to improve readability. Paint nozzle location.
	 * 0.99a Bug: click on speedup label, toggles pause but did also switch layer
	 * 1.01 Added more Gcodes 
	 * 1.02 Added step by step execution (debug mode), Added fast forward/rewind, Show current Gcode when in pause (debug mode), Added increase/decrease in large steps (10x),
	 * 1.03 Fixed large problem with printing (layers were reordered by Z-pos) , better support of z-Lift
	 * 1.04 Network receiver added (Android). confirm on stop printing.  prevent other buttons on print
	 * 1.05+1.06 Android improvements
	 * 1.07 Gcodepainter cleanup, paint GCode when printing, switch background color when printing, network send
	 * 1.08 Mode indicator (print vs simulate) , Framerate counter (disabled)
	 * 1.10 Significantly reduced memory footprint, reworked printing code (decouple render from print), avoid creating many objects
	 * 1.11 fixed boundary calculation, Add makeware specific extrusion code ( A instead of E)
	 * 1.12 GCode creation through factory. Add makeware specific extrusion code ( B instead of E), removed experimental modifications, fixed temp for first layer, fixed comments
	 * 1.13 Fixed network send bug
	 * 1.14 fixed some more bugs. improved load time
	 * 1.15 fixed G4 NPE
	 * 1.16 Improved load error handling. print wrong gcodes in window. fixed double whitespace error
	 * 1.17 Many performance improvments, Paint extruder, MacOS load bug
	 * 1.18 label paint errors fixed , config file for networkip & path
	 * 1.21 buttonbar, print panel, gcodeprintr banner
	 * 1.22 hide banner, settings dialog, bedsize,themes
	 * 1.23 support bfb gcodes, 
	 * 1.24 jump to layer added, fixed nozzle offset, fixed help dialog,
	 * 1.25 fixed bug with nozzle pos when painting long lines
	 * 1.26 network sender , autostart & autosave 
	 * 1.28 redesign, T0: answers, bfb fix
	 * 1.29 snapshot function, paint dual nozzle even when m128, aspect ratio + resize
	 */
	
	
public class PrintrPanel extends JPanel {
		
		public PrintrPanel(){
			
		}
}
	
	public class PainterPanel extends JPanel {
		
		public PainterPanel(){
			
		}
		 public Dimension getPreferredSize() {
		        return new Dimension(500,500);
		    }
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			if(awt != null)	awt.drawImage(g);
		}
	}
	public static final String VERSION = "v1.29";	
	GcodePainter gp;
	AWTGraphicRenderer awt;
	boolean showdetails =true;
	static String snapshotimage=null; //Take snapshot only
	static int bedsizeX=200;
	static int bedsizeY=200;
	static String dualoffsetXY = "0:0";
	BufferedImage img = null;
	PainterPanel painter = null;
	JTextArea cons;
	KeyListener keyl = null;
	boolean showprintpanel = false;
	boolean showbanner = true;
	static boolean roundbed=false;
	JPanel printpanel = null;
	JButton banner = null;
	static String theme = "default";
	
	//Properties
	static String networkip = "192.168.0.50";
	static String lastfilepath = System.getProperty("user.dir");


	public GcodeSimulator() {
		setTitle("GCode Print Simulator " + VERSION);
		setBackground(Color.black);
		setLayout(new BorderLayout(0,0));
		
		painter = new PainterPanel();
		add(painter,BorderLayout.CENTER);
		img = null;
		try {
			
		    img = ImageIO.read(GcodeSimulator.class.getResourceAsStream("/GcodePrintrBanner1.png"));
		   // System.out.println("image read done"+img.getWidth());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(showbanner){
			initBanner();
			add(banner,BorderLayout.NORTH);
		}
		add(getButtonPanel(),BorderLayout.WEST);
		if(showprintpanel){
			printpanel=getPrintButtonPanel();
			add(printpanel,BorderLayout.EAST);
		}
		
	}

	private JButton initBanner() {
		banner = new JButton(new ImageIcon(img.getScaledInstance(-1, 80, Image.SCALE_SMOOTH)));
		banner.setBackground(new Color(0x0872a4));
		banner.setOpaque(true);
		banner.setPreferredSize(new Dimension(1600, 80));
		banner.setActionCommand("GCodePrintr");
		banner.addActionListener(this);
		return banner;
	}
	
	public JPanel getButtonPanel(){
		JPanel bp = new JPanel();
		bp.setPreferredSize(new Dimension(70, 1000));
		bp.setLayout(new FlowLayout());
		addButton(bp,"Load","f");
		addButton(bp,"Send","x");
		addButton(bp,"Details","t");
		addButton(bp,"Speed+","+");
		addButton(bp,"Speed-","-");
		addButton(bp,"Next","n");
		addButton(bp,"Back","b");
		addButton(bp,"Pause","p");
		addButton(bp,"Restart","r");
		addButton(bp,"Exit","q");
		return bp;
	}
	
	public JPanel getPrintButtonPanel(){
		JPanel bp = new JPanel();
		bp.setPreferredSize(new Dimension(210, 1000));
		bp.setLayout(new FlowLayout());
		bp.add(new JLabel("        Motor Control       "));
		addButton(bp,"X-Home","XHome");
		addButton(bp,"Y+","Y+");
		addButton(bp,"Z+","Z+");
		addButton(bp,"X-","X-");
		addButton(bp,"Z-Home","ZHome");
		addButton(bp,"X+","X+");
		addButton(bp,"Y-Home","YHome");
		addButton(bp,"Y-","Y-");
		addButton(bp,"Z-","Z-");
		bp.add(new JLabel("    Extruder & Heat   "));
		addButton(bp,"Extrude","Extrude");
		addButton(bp,"Retract","Retract");
		addButton(bp,"Fan","Fan");
		bp.add(new JLabel("      Print Control      "));
		addButton(bp,"Connect","Connect");
		addButton(bp,"Reset","Reset");
		addButton(bp,"Print","Print");
		
		bp.add(new JLabel("      Console      "));
		cons = new JTextArea(">");
		cons.setPreferredSize(new Dimension(200,2000));
		JScrollPane scp = new JScrollPane(cons);
		scp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scp.setPreferredSize(new Dimension(205,200));
		bp.add(scp);
		return bp;
	}

	private void addButton(JPanel bp,String title,String cmd) {
		JButton load = new JButton(title);
		load.setPreferredSize(new Dimension(65, 60));
		load.addActionListener(this);
		load.setActionCommand(cmd);
		load.setToolTipText(title);
		load.setMargin(new Insets(0, 0, 0, 0));
		load.setFont(load.getFont().deriveFont(5));
		bp.add(load);
	}
	
	public void init(String filename,InputStream in) throws IOException{
		awt = new AWTGraphicRenderer(bedsizeX, bedsizeX,this,theme);
		float fac = (awt.getHeight()-(55+(awt.getHeight()/12)))/bedsizeX;
	//	GCodeFactory.setCustomFactory(new GCodeFactoryBuffer());
		gp = new GcodePainter(awt,true,fac,bedsizeX,bedsizeX,GcodePainter.defaultzoommod); //todo pass bedsize
		if(!dualoffsetXY.equals("0:0")){
			Position pos = Constants.parseOffset(dualoffsetXY);
			gp.setExtruderOffset(1, pos);
			gp.setExtruderOffset(0, new Position(0,0));
		}
		gp.setBedIsRound(roundbed);
	//	System.out.println("Zoom:"+fac);
		//gp.setZoom((fac));
		updateSize(showdetails);
		setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource( "/icon.png" )));
		
		setJMenuBar(getMyMenubar());
		setVisible(snapshotimage==null);
		setBackground(Color.black);
		
		addListeners();

		

		
		gp.start(filename,in,null);	
		this.requestFocus();
		if(snapshotimage!=null){
			createSnapshotImage();

		}
	}

	private void createSnapshotImage() throws IOException {
		File outputfile = new File(snapshotimage);
		System.out.println("Save snapshot of gcode to "+snapshotimage);
		long time = System.currentTimeMillis();
		gp.setSnapshotMode();
		gp.jumptoLayer(10000);
		gp.togglePause();
		gp.setPainttravel(false);
		int lc = gp.model.getLayercount(true);
		System.out.println("Layers:"+lc);
		if(lc==0) System.exit(2);
		int retry=0;
		while(gp.getCurrentLayer() == null){
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {}
			retry++;
			if(retry > 20){
				System.err.println("Timeout waiting for painter");
				System.exit(3);
			}
			
		}
		int ln=0;
		while( (ln=gp.getCurrentLayer().getNumber()) < (lc-1) ){
			try {
				Thread.sleep(500);
				System.out.print(".");
			} catch (InterruptedException e) {
		}
		}
		int[] sz = gp.getSize(false);
		BufferedImage dest = awt.offimg.getSubimage(0, 0, sz[0]-gp.getGap(), sz[1]);
		ImageIO.write(dest, "jpg", outputfile);
		System.out.println("\nDone (Time: "+ (System.currentTimeMillis()-time)+")");
		System.exit(0);
	}
	
	
	private JMenuItem addMenuItem(JMenu parent, String name, String key, int keycode){
		JMenuItem it = new JMenuItem(name);
	    it.addActionListener(this);
	    it.setActionCommand(key);
	    //MenuShortcut ms = new MenuShortcut(KeyEvent.getExtendedKeyCodeForChar(key.charAt(0))); ONLY JAVA 1.7
	//    MenuShortcut ms = new MenuShortcut(keycode);
	    it.setAccelerator(KeyStroke.getKeyStroke(keycode, 0));
	    parent.add (it);
	    return it;
	}
	
	protected JMenuBar getMyMenubar () {
		    JMenuBar ml = new JMenuBar();
		    JMenu datei = new JMenu ("File");
		    addMenuItem(datei, "Load File", "f",KeyEvent.VK_F);
		    addMenuItem(datei, "Network Send", "x",KeyEvent.VK_X);
		    addMenuItem(datei, "Save Image", "c",KeyEvent.VK_C);
		    addMenuItem(datei, "Exit", "q",KeyEvent.VK_Q);

		    ml.add(datei);		    
		    
		    JMenu control = new JMenu ("Control");
		    addMenuItem(control, "Pause", "p",KeyEvent.VK_P);
		    control.addSeparator();
		    addMenuItem(control, "Increase Speed", "+",KeyEvent.VK_PLUS);
		    addMenuItem(control, "Increase Speed by 10", "/",KeyEvent.VK_SLASH);
		    addMenuItem(control, "Decrease Speed", "-",KeyEvent.VK_MINUS);
		    addMenuItem(control, "Decrease Speed by 10", "*",KeyEvent.VK_ASTERISK);
		    control.addSeparator();
		    addMenuItem(control, "Next Layer", "n",KeyEvent.VK_N);
		    addMenuItem(control, "Previous Layer", "b",KeyEvent.VK_B);
		    addMenuItem(control, "Jump to Layer", "l",KeyEvent.VK_L);
		    control.addSeparator();
		    addMenuItem(control, "Step Forward", " ",KeyEvent.VK_RIGHT);
		    addMenuItem(control, "Step Backward", "\b",KeyEvent.VK_LEFT);
		    control.addSeparator();
		    addMenuItem(control, "Restart", "r",KeyEvent.VK_R);
		    
		    
		
		    
		    JMenu view = new JMenu ("View");
		    addMenuItem(view, "Zoom In", "i",KeyEvent.VK_I);
		    addMenuItem(view, "Zoom Out", "o",KeyEvent.VK_O);
		    view.addSeparator();
		    addMenuItem(view, "Show/Hide Details", "m",KeyEvent.VK_M);
		    addMenuItem(view, "Toggle Detail type", "t",KeyEvent.VK_T);
		    		    
		    JMenu about = new JMenu ("About");
		    addMenuItem(about, "About/Help", "h",KeyEvent.VK_H);
		    addMenuItem(about, "Settings", "s",KeyEvent.VK_S).setEnabled(true);;
		   
		    
		    JCheckBoxMenuItem cbmenu = new JCheckBoxMenuItem("Show Print Panel");
		    cbmenu.setSelected(showprintpanel);
		    cbmenu.setActionCommand("printpanel");
		    cbmenu.addActionListener(this);
		 //   MenuShortcut ms = new MenuShortcut(KeyEvent.VK_Y);
		    cbmenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, 0));
		    about.add(cbmenu);
		    JCheckBoxMenuItem banmenu = new JCheckBoxMenuItem("Show GCodePrintr Banner");
		    banmenu.setSelected(showbanner);
		    banmenu.setActionCommand("bannerpanel");
		    banmenu.addActionListener(this);
		 //   MenuShortcut ms = new MenuShortcut(KeyEvent.VK_Y);
		    banmenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0));
		    about.add(banmenu);
		    
		    JMenu edit = new JMenu ("Modify (Experimental)");
		    edit.add("Experimental Edit Mode");
		    edit.addSeparator();
		    addMenuItem(edit, "Speedup Layer by 10%", "w",KeyEvent.VK_W);
		    addMenuItem(edit, "Slowdown Layer by 10%", "e",KeyEvent.VK_E);
		    addMenuItem(edit, "Increase extrusion by 10%", "z",KeyEvent.VK_Z);
		    addMenuItem(edit, "Decrease extrusion by 10%", "u",KeyEvent.VK_U);
		    addMenuItem(edit, "Delete layer", "g",KeyEvent.VK_G);
		    addMenuItem(edit, "Save Modifications", "a",KeyEvent.VK_A);
		    
		    
		    ml.add(control);
		   // ml.add(edit);
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
		readConfig();
		if (args.length < 1 || !new File(args[0]).exists()) {
			filename = "/gcodesim.gcode";
			in= gs.getClass().getResourceAsStream(filename);			
		} else {
			filename = args[0];
			if(args.length == 2){
				snapshotimage = args[1];
				if(!snapshotimage.endsWith(".jpg")){
					System.err.println("Error: "+snapshotimage+ " Filename must end with .jpg .");
					System.exit(1);
				}
			}
		}
		gs.init(filename,in);
		gs.requestFocus();

	}
	
	
	public static void readConfig(){
		
		String homedir = System.getProperty("user.home");
		String sep = System.getProperty("file.separator");
		File config = new File(homedir+sep+".gcodesim");
		
		if(config.exists()){
			try {
				Properties prop = new Properties();
				prop.load(new FileInputStream(config));
				lastfilepath = prop.getProperty("lastfilepath",System.getProperty("user.dir"));
				networkip  = prop.getProperty("networkip","192.168.0.50");
				String bedsize = prop.getProperty("bedsize","200");
				theme = prop.getProperty("theme","default");
				roundbed = Boolean.parseBoolean(prop.getProperty("roundbed","false"));
				dualoffsetXY=prop.getProperty("dualoffsetXY","0:0");
				bedsizeX=Integer.parseInt(bedsize);
				bedsizeY=bedsizeX;
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NumberFormatException nfe){
				nfe.printStackTrace();
			}
		}else{
			System.out.println("Config file does not exist:"+config);
		}
		
		
	}
	
	public static void storeConfig(){
		
		String homedir = System.getProperty("user.home");
		String sep = System.getProperty("file.separator");
		File config = new File(homedir+sep+".gcodesim");
		
		try {
				Properties prop = new Properties();
				prop.setProperty("lastfilepath", lastfilepath);
				prop.setProperty("networkip", networkip);
				prop.setProperty("bedsize", String.valueOf(bedsizeX));
				prop.setProperty("theme", theme);
				prop.setProperty("roundbed", String.valueOf(roundbed));
				prop.setProperty("dualoffsetXY", dualoffsetXY);	
				prop.store(new FileOutputStream(config),"Gcode Simulator Config");
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		
		
	}
	
	public static void openWebpage(URI uri) {
	    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
	    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
	        try {
	            desktop.browse(uri);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	}
	
	@Override
	public void actionPerformed(ActionEvent arg0) {
		if(arg0.getActionCommand().equals("GCodePrintr")){		
					try {
						openWebpage(new URI("http://gcodeprintr.dietzm.de"));
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}		
			this.requestFocus();
			return;
		}
		if(arg0.getActionCommand().equals("printpanel")){		
			showprintpanel = !showprintpanel;
			if(showprintpanel){
				if(printpanel == null){
					printpanel=getPrintButtonPanel();
				}
				add(printpanel,BorderLayout.EAST);
				revalidate();
			}else{
				remove(printpanel);
				revalidate();
			}
			return;
		}
		if(arg0.getActionCommand().equals("bannerpanel")){		
			showbanner = !showbanner;
			if(showbanner){
				if(banner == null){
					initBanner();
				}
				add(banner,BorderLayout.NORTH);
				revalidate();
			}else{
				remove(banner);
				revalidate();
			}
			return;
		}
		if(arg0.getActionCommand().equals("Connect")){
			try {
				
				ConsoleIf console=new ConsoleIf() {
					
					public void updateState(CharSequence statemsg,CharSequence detail, int perc){
						
					}
					
					@Override
					public void setWakeLock(boolean active) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void setTemp(Temperature temp) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void setPrinting(boolean printing) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void log(String tag, String value, ReceiveBuffer buf) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void log(String tag, String value) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public boolean hasWakeLock() {
						// TODO Auto-generated method stub
						return false;
					}
					
					@Override
					public void clearConsole() {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public int chooseDialog(String[] items, String[] values, int type) {
					System.out.println("Choosedialog");
					return 1;
					}
					
					@Override
					public void appendTextNoCR(CharSequence... txt) {
						for (CharSequence n : txt) {
							System.out.println(n);
							cons.append(n.toString());
				         }
					}
					
					@Override
					public void appendText(CharSequence... txt) {
						for (CharSequence n : txt) {
							System.out.println(n);
							cons.append(n.toString()+"\n");
				         }
														
					}
				};
				SerialPrinter	sio = new SerialPrinter(console);
				
				
				gp.setPrintercon(sio);
				
				sio.connect(new Dummy(sio, console),115200);	
				sio.connectTo("usb");
				} catch (NoClassDefFoundError er) {
					//er.printStackTrace();
					System.out.println("Opening COM Port FAILED ! RXTX Jar Missing.  " + er);
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Opening COM Port FAILED ! " + e);
				} catch (UnsatisfiedLinkError ule){
					//ule.printStackTrace();
					System.out.println("Opening COM Port FAILED ! RXTX Jar Missing.  " + ule);
				}
		
				return;
		}
		
		
		char a = arg0.getActionCommand().charAt(0);
		//Forward Event to keylistener (don't duplicate code)
		keyl.keyTyped(new KeyEvent(this, 0, 0, 0, (int)a,a));
	}
	
	public void showJumpToLayerDialog(){
		final JDialog in = new JDialog(this,"Jump to layer number",true);
		in.setLayout(new FlowLayout());
		in.setBackground(Color.lightGray);
		final JTextField tf2 = new JTextField(15);
		final JLabel status = new JLabel("                                                    ");
		tf2.setText("1");
//		tf2.setSize(200,20);
		JButton btn1 = new JButton("Jump");
		JButton btn2 = new JButton("Cancel");
		btn2.setActionCommand("Cancel");
		
		in.addWindowListener(new WindowListener() {			
			@Override
			public void windowOpened(WindowEvent arg0) {}			
			@Override
			public void windowIconified(WindowEvent arg0) {	}			
			@Override
			public void windowDeiconified(WindowEvent arg0) {}			
			@Override
			public void windowDeactivated(WindowEvent arg0) {}			
			@Override
			public void windowClosing(WindowEvent arg0) {
				in.setVisible(false);				
			}			
			@Override
			public void windowClosed(WindowEvent arg0) {
				in.setVisible(false);				
			}			
			@Override
			public void windowActivated(WindowEvent arg0) {	}
		});
		ActionListener action= new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if(e.getActionCommand().equals("Cancel")){
					in.dispose();
					return;
				}
				String lar = tf2.getText();
				
				try {
					gp.jumptoLayer(Integer.parseInt(lar));
					in.repaint();
					in.setVisible(false);
				} catch (NumberFormatException e1) {
					status.setText("Invalid Layer Number:"+e1.getMessage());
					in.repaint();
				}				
			}
		};
		btn1.addActionListener(action);
		btn2.addActionListener(action);
		tf2.addActionListener(action);
		in.add(new Label("Enter Layer Number"));
		in.add(tf2);
		in.add(btn1);
		in.add(btn2);
		in.add(status);
		in.setSize(330,120);
		in.setVisible(true);
	}

	
	public void showNetworkIPDialog(){
		final JDialog in = new JDialog(this,"Send to GCodeSimulator/GCodePrintr for Android",true);
		in.setLayout(new FlowLayout());
		in.setBackground(Color.lightGray);
		final JTextField tf2 = new JTextField(15);
		final JLabel status = new JLabel("                                                    ");
		tf2.setText(networkip);
//		tf2.setSize(200,20);
		JButton btn1 = new JButton("Send");
		JButton btn2 = new JButton("Cancel");
		final JCheckBox autostart = new JCheckBox("Autostart Print");
		final JCheckBox autosave = new JCheckBox("Autosave File");
		
		btn2.setActionCommand("Cancel");
		
		in.addWindowListener(new WindowListener() {			
			@Override
			public void windowOpened(WindowEvent arg0) {}			
			@Override
			public void windowIconified(WindowEvent arg0) {	}			
			@Override
			public void windowDeiconified(WindowEvent arg0) {}			
			@Override
			public void windowDeactivated(WindowEvent arg0) {}			
			@Override
			public void windowClosing(WindowEvent arg0) {
				in.setVisible(false);				
			}			
			@Override
			public void windowClosed(WindowEvent arg0) {
				in.setVisible(false);				
			}			
			@Override
			public void windowActivated(WindowEvent arg0) {	}
		});
		ActionListener action= new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if(e.getActionCommand().equals("Cancel")){
					in.dispose();
					return;
				}
				try {
					NetworkPrinter netp = new NetworkPrinter();
					status.setText("Sending file ... please wait");
					in.repaint();
					networkip=tf2.getText();
					storeConfig();
					int flags=0;
					if(autostart.isSelected()){ flags = (flags | NetworkPrinter.AUTOSTART_PRINT);}
					if(autosave.isSelected()){ flags = (flags | NetworkPrinter.AUTOSAVE_MODEL);}
					netp.sendToReceiver(networkip, gp.model,flags);					
					status.setText("Sending file ... done");
					in.repaint();
					in.setVisible(false);
				}catch (UnknownHostException uh){
					status.setText("Invalid IP address:"+uh.getMessage());
					in.repaint();
					//uh.printStackTrace();
				}catch(ConnectException ce) {
					status.setText("Connect error:"+ce.getMessage());
					in.repaint();
					//ce.printStackTrace();
				}catch (IOException e2) {
					status.setText("IO Error:"+e2.getMessage());
					in.repaint();
					//e2.printStackTrace();
				}catch (Exception e2) {
					status.setText("Error:"+e2.getMessage());
					in.repaint();
					//e2.printStackTrace();
				}
				
			}
		};
		btn1.addActionListener(action);
		btn2.addActionListener(action);
		tf2.addActionListener(action);
		in.add(new Label("Enter IP Address"));
		in.add(tf2);
		in.add(autostart);
		in.add(autosave);
		in.add(btn1);
		in.add(btn2);

		in.add(status);
		in.setSize(330,160);
		in.setVisible(true);
	}

	static String openFileBrowser(Frame gs) {
		String filename =null;
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
		
	   if(lastfilepath != null){
				   fd.setDirectory(lastfilepath);
			   }else{
				   fd.setDirectory(System.getProperty("user.dir"));
	   }
		
		
		fd.setModal(true);
		if (System.getProperty("os.name").startsWith("Mac OS X")) {
			//Allow all filename due to a MacoS bug ?
		}else{
			fd.setFilenameFilter(new FilenameFilter() {
			@Override
			public boolean accept(File arg0, String arg1) {
				if(arg1.toLowerCase().endsWith(".gcode")) return true;
				if(arg1.toLowerCase().endsWith(".gc")) return true;
				return false;
			}
		});
		}
		fd.requestFocus();
		fd.setVisible(true);
		// Choosing a "recently used" file will fail because of redhat
		// bugzilla 881425 / jdk 7165729
		if (fd.getFile() == null)	return null;
		filename = fd.getDirectory() + fd.getFile();
		t.cancel();
		lastfilepath=fd.getDirectory();
		storeConfig();
		return filename;
	}

	private void updateSize(boolean details) {
		if((getExtendedState() & Frame.MAXIMIZED_BOTH) == 0){
			int[] sz = gp.getSize(details);
			int width = sz[0]+80; //70 for button bar
			int height = sz[1];
			if(showprintpanel){
				width=width+210;
			}
			if(showbanner){
				height=height+80;
			}
			height=height+60;//window decorations
			setSize(width,height);
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
					//Speedup (only if in left box
					if(arg0.getPoint().x < bedsizeX * gp.getZoom()+gp.defaultgap ){
						if( mwrot > 0){
							gp.toggleSpeed(false);
						}else{
							gp.toggleSpeed(true);
						}
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
					int speedboxpos = (int)(((bedsizeX*gp.getZoom()+gp.defaultgap)/100)*82)+6;
					int speedboxsize=(int)((bedsizeX*gp.getZoom()+gp.defaultgap)/100)*12;
					int mousex=arg0.getPoint().x;
					//if clicked on speedup label, toggle pause
					if(mousex >= speedboxpos && mousex <= speedboxpos+speedboxsize && arg0.getPoint().y > bedsizeY*gp.getZoom()+55){
						gp.togglePause();
					}else if(arg0.getPoint().x > bedsizeX * gp.getZoom()+gp.defaultgap ){
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
				
				float size = getHeight()-(showbanner?80:0);
				
				
				float currsize = gp.getSize(false)[1];
				
				if(currsize!=size){
					//System.out.println("Size:"+size+" Curr:"+currsize);
					//float fac = size/currsize;
					float fac = (size-(55+(size/12)))/bedsizeY;
					//float fac = (size-(55+(size/2)))/bedsizeX;
					//float z = gp.getZoom();
					//System.out.println("Zoom:"+z);
					//gp.setZoom(z*(fac));
					gp.setZoom((fac));
				}
				
				//Ratio
				//System.out.println("Update ratio");
				gp.setZoomMod(getRatioMod());
				repaint();
				
				
			}
		
		});
		
		keyl = new KeyListener() {

			@Override
			public void keyTyped(KeyEvent arg0) {
				if (arg0.getKeyChar() == '+') {
					gp.toggleSpeed(true);
				} else if (arg0.getKeyChar() == '-') {
					gp.toggleSpeed(false);
				} else if (arg0.getKeyChar() == '/') {
					gp.toggleSpeed(true,10);
				} else if (arg0.getKeyChar() == '*') {
					gp.toggleSpeed(false,10);
				} else if (arg0.getKeyChar() == ' ') {
					gp.doStep(true);
				} else if (arg0.getKeyChar() == KeyEvent.VK_BACK_SPACE) {
					gp.doStep(false);
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
					System.out.println("Pause");
					gp.togglePause();
				} else if (arg0.getKeyChar() == 's') {
					new OptionFrame().setVisible(true);
				} else if (arg0.getKeyChar() == 'x') {
					showNetworkIPDialog();
				} else if (arg0.getKeyChar() == 'h') {
					gp.showHelp();
				} else if (arg0.getKeyChar() == 'c') {
					try {
						int[] sz = gp.getSize(showdetails);
						BufferedImage dest = awt.offimg.getSubimage(0, 0, sz[0], sz[1]);
						String fn = new SimpleDateFormat("MMddyyyy-ss").format(new Date());
						ImageIO.write(dest, "jpg", new File("gcodeimg_"+fn+".jpg"));
						System.out.println("Saved file "+"gcodesim_"+fn+".jpg");
					} catch (IOException e) {
						System.err.println("Failed to save file");
					}
				} else if (arg0.getKeyChar() == 'l') {
					showJumpToLayerDialog();
				//EDIT MODE
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
		};
		
	}

	
	public void paint1(Graphics g) {
		//g.drawImage(awt.getImage(), 4, 28, this);
		//awt.drawImage(g);
	//	g.drawImage(img,0,(int)(awt.getWidth()/3f),null);
		
//		//Paint current print point
//		g.fillOval((int)awt.getPos()[0]+4,(int)awt.getPos()[1]+53,4,4);
//		g.setColor(Color.white);
//		g.drawOval((int)awt.getPos()[0]-2,(int)awt.getPos()[1]+47,16,16);
//		g.drawOval((int)awt.getPos()[0]+0,(int)awt.getPos()[1]+49,12,12);
//		g.drawOval((int)awt.getPos()[0]+2,(int)awt.getPos()[1]+51,8,8);
//		g.drawOval((int)awt.getPos()[0]+4,(int)awt.getPos()[1]+53,4,4);
		super.paint(g);
	}
	
	
	/**
	 * Overwritten to avoid "clear" on every paint which causes flashing
	 * 
	 * @param g
	 */
	public void update1(Graphics g) {
		paint(g);
	}

	private float getRatioMod() {
		//Calculate default ratio w/h for gp (based on default zoommod)
		float defaultratio = (GcodePainter.defaultgap +bedsizeX + (bedsizeX / GcodePainter.defaultzoommod *2)) / (bedsizeY + (bedsizeY/GcodePainter.boxheightfactor)); 
		//Get available screen space
		int maxwid = getWidth();
		float maxhigh2 = getHeight()-(showbanner?80:0);
		//Calculate effective screen ratio 
		float screenratio = ((float)maxwid/(float)maxhigh2);
		
		if(screenratio < 1.465f){
			//4:3 screen, prevent details column from getting too small
			screenratio = 1.465f;
		}
		if(screenratio > 2){
			//loooong screen , prevent column from getting to wide
			screenratio = 2f;
		}
		
		//calculate new zoommod based on screen ratio to align ratios
		return bedsizeX *2 / ((screenratio * (bedsizeY + (bedsizeY/GcodePainter.boxheightfactor))) - GcodePainter.defaultgap - bedsizeX );
		
	}

}


package de.dietzm.gcodesim;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

import de.dietzm.Position;
import de.dietzm.SerialIO;


public class AWTGraphicRenderer implements GraphicRenderer {

	Color[] backcol = new Color[]{Color.BLACK,new Color(0,90,120),Color.red};
	Color[] colors = new Color[] { Color.red, Color.cyan, Color.yellow, Color.magenta, Color.green,
			Color.orange, Color.pink, Color.white, Color.darkGray };

	Frame frame;

	Graphics2D g = null;
	private final GraphicsConfiguration gfxConf = GraphicsEnvironment.getLocalGraphicsEnvironment()
			.getDefaultScreenDevice().getDefaultConfiguration();
	BufferedImage offimg , offimg2;
	int[] pos = new int[5];
	int activeEx = 0;
	SerialIO sio = null;
	String titletxt = null;	
	long titletime=0;
	float zoom = 1;
	Position[] extruderoffset = null;

	// Color[] colors = new Color[] { new Color(0xffAAAA), new Color(0xffBAAA),
	// new Color(0xffCAAA), new Color(0xffDAAA),
	// new Color(0xffEAAA), new Color(0xffFAAA), new Color(0xffFFAA) ,
	// Color.white, Color.darkGray};
	// Stroke 0=travel 1=print
	private BasicStroke stroke[] = {
			new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[] { 1, 2 }, 0),
			new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND),
			new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[] { 1, 6 }, 0) };

	public AWTGraphicRenderer(int bedsizeX, int bedsizeY, Frame frame,String theme) {
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int wmax = (int)Math.min(d.getWidth(),1920);
		int hmax = (int)(Math.min(d.getHeight(),1200));
		//System.out.println("Window: w:"+wmax+" h:"+hmax);
		offimg = gfxConf.createCompatibleImage(wmax, hmax);
		offimg2= gfxConf.createCompatibleImage(wmax,hmax);
		g = (Graphics2D) offimg.getGraphics();
		g.setBackground(Color.black);
		g.setFont(Font.decode(Font.SANS_SERIF));
		this.frame = frame;

		// Experiment with more colors
		// colors = new Color[302];
		// int rgb = 0x994444;
		// for (int i = 0; i < colors.length; i++) {
		// colors[i]=new Color(rgb);
		// System.out.println(rgb);
		// if(i % 2 == 0){
		// rgb+=0x000005;
		// }else{
		// rgb+=0x000500;
		// }
		// }
		// colors[colors.length-2]=Color.white;
		// colors[colors.length-1]=Color.darkGray;
		//int theme= 5;
		if(theme.equalsIgnoreCase("default")){
			backcol= new Color[] { Color.BLACK, new Color(0, 90, 120)};
			colors= new Color[] { Color.RED, Color.BLUE, Color.YELLOW, Color.CYAN, Color.GREEN, Color.MAGENTA,
					Color.LIGHT_GRAY, Color.WHITE, Color.DARK_GRAY };
		}else if(theme.equalsIgnoreCase("gray")){
			//GRAY Theme
			backcol= new Color[] { new Color(220,220,220), new Color(0, 90, 120)};
			colors = new Color[15];
			int val= 35;
			for (int i = 0; i < 13; i++) {
				val=val+13;
				colors[i]=new Color(val,val,val);
			}
			colors[13]=Color.DARK_GRAY;
			colors[14]=Color.GRAY;
		}else if(theme.equalsIgnoreCase("autumn")){
			//Autumn theme
			backcol= new Color[] { new Color(229,219,170), new Color(171, 200, 165)};
			colors= new Color[] { new Color(33,144,148),
					 			new Color(38,130,33),
					 			new Color(148,147,33),
					 			new Color(63,78,119), //blau
					 			new Color(130,33,45),
					 			new Color(127,86,147),
					 			new Color(228,95,10),
					 			Color.DARK_GRAY, 
					 			new Color(135,94,15)};
		}
//		case 4:
//			backcol= new Color[] { new Color(195,199,255), new Color(198, 240, 220)};
//			colors= new Color[] { new Color(0x44,0x55,0x90),
//					 			Color.DARK_GRAY, 
//					 			Color.CYAN };
//			//faint=true;
//			break;
//		case 5:
////			//Theme from xml
////			Resources myres = view.getResources();
////			backcol = myres.getIntArray(R.array.xmlthemeback);
////			colors = myres.getIntArray(R.array.xmltheme);
//			break;
//		default:
//			backcol= new Color[] { Color.BLACK, new Color(0, 90, 120)};
//			colors= new Color[] { Color.RED, Color.BLUE, Color.YELLOW, Color.CYAN, Color.GREEN, Color.MAGENTA,
//					Color.LIGHT_GRAY, Color.WHITE, Color.DARK_GRAY };
//			
//			break;
//		}

	}

	@Override
	public String browseFileDialog() {
		String var = GcodeSimulator.openFileBrowser(frame);
		frame.requestFocus();
		return var;
	}

	@Override
	public void clearrect(float x, float y, float w, float h,int colitem) {
		g.setBackground(backcol[colitem]);
		g.clearRect((int) x, (int) y, (int) w, (int) h);
	}
	
	public void faintRect(float x,float y, float w,float h){
		//TODO
	}

	/**
	 * Clone the image to avoid problems with multithreading (EDT vs
	 * GCodePainter thread)
	 */
	@SuppressWarnings("unused")
	private void cloneImage() {
		// ColorModel cm = offimg.getColorModel();
		// boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		// WritableRaster raster = offimg.copyData(null);
		// offimg2 = new BufferedImage(cm, raster, isAlphaPremultiplied, null);
		// BufferedImage currentImage = new
		// BufferedImage(width,height,BufferedImage.TYPE_3BYTE_BGR);

		// Fastest method to clone the image (DOES NOT WORK for MACOS)
		// int[] frame =
		// ((DataBufferInt)offimg.getRaster().getDataBuffer()).getData();
		// int[] imgData =
		// ((DataBufferInt)offimg2.getRaster().getDataBuffer()).getData();
		// System.arraycopy(frame,0,imgData,0,frame.length);
	}

	public void drawArc(int x, int y, int width, int height, int theta, int delta) {
		g.drawArc(x, y, width, height, theta, delta);
	}
	public int getColorNr(){
		return colors.length;
	}

	/**
	 * Draw the buffer to the real surface
	 * @param gp
	 */
	public synchronized void drawImage(Graphics gp1) {

		Graphics2D gp = (Graphics2D) offimg2.getGraphics();
		
		gp.drawImage(offimg, 0, 0, frame);
		if(getPos()[0] != 0){
			paintNozzle(gp,getPos()[0],getPos()[1],true);
			if(extruderoffset != null && extruderoffset[1] != null){
				for (int i = 0; i < extruderoffset.length; i++) {
					if(extruderoffset[i] != null && activeEx!=i && extruderoffset[activeEx] != null ){
						int xoff = (int)(getPos()[0]+((extruderoffset[i].x-extruderoffset[activeEx].x)*zoom) );
						int yoff = (int)(getPos()[1]+((extruderoffset[i].y-extruderoffset[activeEx].y)*zoom));
						paintNozzle(gp,xoff,yoff,false);
					}
				}				
			}

		//	paintExtruder(gp,getPos()[2],getPos()[4]);
			paintExtruder(gp,getPos()[3],getPos()[2],getPos()[4]);

		}
		//gp.drawOval((int) getPos()[2] + 2, (int) getPos()[4] + 51, 8, 8);
		//gp.drawOval((int) getPos()[3] + 2, (int) getPos()[4] + 51, 8, 8);
		
		//Blinking mode "simulation" or "print"
		// gp.drawOval((int)getPos()[0]+4,(int)getPos()[1]+53,4,4);
		if(titletxt!=null && System.currentTimeMillis()-titletime > 1500){
			if(System.currentTimeMillis()-titletime > 3000){
			titletime = System.currentTimeMillis();
			}
			gp.setColor(Color.GREEN);
			gp.setFont(gp.getFont().deriveFont(26f));
			gp.drawString(titletxt, 20 ,40);
		}
		gp1.drawImage(offimg2, 0,0, frame);
	}

	private void paintNozzle(Graphics2D gp, int x , int y, boolean active) {
			// Paint current print point (nozzle)
			//System.out.println("x"+getPos()[0]+" y"+ getPos()[1]);
			if(active){
				gp.setColor(g.getColor());
				gp.fillOval((int) x -2, (int) y -2, 4, 4);
				gp.setColor(Color.white);
			}else{
				gp.setColor(Color.lightGray);
			}
			gp.drawOval((int) x -8 , (int) y -8, 16, 16);
			gp.drawOval((int) x -6, (int) y -6, 12, 12);
			gp.drawOval((int)x -4, (int) y -4, 8, 8);
	
		
	}

	private void paintExtruder(Graphics2D gp, int pos,int poss, int zpos) {
//		gp.setColor(Color.gray);
//		gp.fillRect(730, zpos + 51-15, 250, 3);
//		
		//side&front Extruder
		gp.setColor(Color.white);
		gp.fillRect(pos + 2, zpos -25, 15, 20); //hotend
		gp.drawLine(pos+2, zpos -5, pos+2+7, zpos ); //hotend
		gp.drawLine(pos+2+14, zpos -5, pos+2+7, zpos); //hotend
		
		 //Extruder
		gp.fillRect(pos + 2-13, zpos -45, 46, 28); 
		 //gears	
		gp.setColor(Color.lightGray);
		gp.fillOval(pos + 2+2, zpos -53, 35, 35);//Large gear
		gp.fillOval(pos -6, zpos -41, 11, 11); //small gear
		
//		gp.drawOval(pos + 2+3, zpos + 51-49, 33, 33);
//		gp.drawOval(pos + 2+4, zpos + 51-48, 31, 31);
		gp.setColor(Color.white);
		gp.fillOval(pos + 2+14, zpos -41, 11, 11);
//		gp.drawOval(pos + 2+15, zpos + 51-37, 9, 9);		
		gp.fillOval(pos -2, zpos -37, 3, 3);
		
		gp.fillOval(poss+2, zpos -1, 3, 3); //sideview
		//Filament
		gp.setColor(g.getColor());
		gp.drawArc(pos-185, zpos-130, 190, 190, 0, 40);
	}

	@Override
	public void drawline(float x, float y, float x1, float y1) {
		g.draw(new Line2D.Float(x, y, x1, y1));
	}

	@Override
	public void drawrect(float x, float y, float w, float h) {
		g.drawRect((int) x, (int) y, (int) w, (int) h);
	}

	@Override
	public void drawtext(CharSequence text, float x, float y) {
		// g.getFontMetrics();
		g.drawString(text.toString(), x, y);
	}

	public void drawtext(CharSequence text, float x, float y, float w) {
		String txt=text.toString();
		int wide = g.getFontMetrics().stringWidth(txt);
		float center = (w - wide) / 2;
		g.drawString(txt, x + center, y);

	}

	@Override
	public void fillrect(float x, float y, float w, float h) {
		g.fillRect((int) x, (int) y, (int) w, (int) h);
	}

	public int getHeight() {
		return offimg.getHeight();
	}

	public synchronized int[] getPos() {
		return pos;
	}
	
	public void setActiveExtruder(int ex){
		activeEx=ex;
	}

	public int getWidth() {
		return offimg.getWidth();
	}




	public synchronized void repaint() {
		// cloneImage();
		frame.repaint();

		// try {
		// //Wait for repaint to happen to avoid changing the image in between.
		// (causes flickering)
		// wait(10);
		// } catch (InterruptedException e) {
		// }
	}
	
	/**
	 * Set the offset and implicit the number of extruders
	 * @param offset
	 */
	public void setExtruderOffset(Position[] offset,float zoom){
		extruderoffset=offset;
		this.zoom=zoom;
	}

	public void setColor(int idx) {
		g.setColor(colors[idx]);
	}

	public void setFontSize(float font) {
		g.setFont(g.getFont().deriveFont(font));
	}

	public synchronized void setPos(int x, int y) {
		// System.out.println("POS: X:"+x+" Y:"+y );
		pos[0] = x;
		pos[1] = y;
	}
	
	/**
	 * Set Position for the side & Front view
	 * @param x1
	 * @param x2
	 * @param z
	 */
	@Override
	public void setPos(int x1,int x2,int z) {
		pos[2] = x1;
		pos[3] = x2;
		pos[4] = z;
	}

	public void setStroke(int idx) {
		g.setStroke(stroke[idx]);
	}
	
	public void setTitle(String txt){
		titletxt=txt;
	}
}

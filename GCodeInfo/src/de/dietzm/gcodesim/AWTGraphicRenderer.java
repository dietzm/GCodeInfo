package de.dietzm.gcodesim;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import de.dietzm.GCode;
import de.dietzm.SerialIO;


public class AWTGraphicRenderer implements GraphicRenderer {
	
	SerialIO sio = null;

	@Override
	public boolean print(GCode code){
		if(sio == null){
			try {
				sio = new SerialIO("/dev/ttyUSB0");
			} catch (NoClassDefFoundError er) {
				er.printStackTrace();
				setColor(7);
				drawtext("Opening COM Port FAILED ! RXTX Jar Missing.  "+er,10, 100,600);
				return false;
			} catch (Exception e) {
				e.printStackTrace();
				setColor(7);
				drawtext("Opening COM Port FAILED ! "+e,10, 100,600);
				return false;
			} 
		}
		try {
			System.out.println("Print:["+code.getCodeline()+"]");
			String result = sio.addToPrintQueue(code);
			System.out.println("RESULT:["+result+"]");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	Graphics2D g = null;
	Color[] colors = new Color[] { Color.red, Color.blue, Color.yellow, Color.cyan, Color.green,
			Color.magenta, Color.orange , Color.white, Color.darkGray};
	
	//Stroke 0=travel 1=print
	private BasicStroke stroke[] = {
			new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1,new float[] { 1, 2 }, 0),
			new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND),
			new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1,new float[] { 1,6}, 0)
			};
	BufferedImage offimg ;
	private final GraphicsConfiguration gfxConf = GraphicsEnvironment.getLocalGraphicsEnvironment()
			.getDefaultScreenDevice().getDefaultConfiguration();
	
	Frame frame;
	
	public AWTGraphicRenderer(int bedsizeX,int bedsizeY, Frame frame){
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		offimg = gfxConf.createCompatibleImage((int)d.getWidth(),(int)d.getWidth());
		g = (Graphics2D) offimg.getGraphics();
		g.setBackground(Color.black);
		g.setFont(Font.decode(Font.SANS_SERIF));
		this.frame=frame;
		
	}

	public void setFontSize(float font){
		g.setFont(g.getFont().deriveFont(font));
	}
	
	
	public void setStroke(int idx){
		g.setStroke(stroke[idx]);
	}
	public void setColor(int idx){
		g.setColor(colors[idx]);
	}
	
	@Override
	public void drawline(float x, float y, float x1, float y1) {
		g.draw(new Line2D.Float(x,y,x1,y1));
	}

	@Override
	public void drawrect(float x, float y, float w, float h) {
		g.drawRect((int)x,(int)y,(int)w,(int)h);
	}
	

	@Override
	public void fillrect(float x, float y, float w, float h) {
		g.fillRect((int)x,(int)y,(int)w,(int)h);
	}


	@Override
	public void drawtext(String text, float x, float y) {
		//g.getFontMetrics(); 
		g.drawString(text,x,y);
	}
	
	
	public void drawtext(String text, float x, float y, float w) {
		int wide = g.getFontMetrics().stringWidth(text);
		float center = (w-wide)/2;
		g.drawString(text,x+center,y);

	}

	@Override
	public void clearrect(float x, float y, float w, float h) {
		g.clearRect((int)x, (int)y,(int) w,(int) h);
	}

	public int getWidth(){
		return offimg.getWidth();
	}
	public int getHeight(){
		return offimg.getHeight();
	}
	public void repaint(){
		frame.repaint();
	}
	public synchronized BufferedImage getImage(){
		return offimg;
	}

	@Override
	public String browseFileDialog() {
		String var = GcodeSimulator.openFileBrowser(frame);
		frame.requestFocus();
		return var;
	}
}

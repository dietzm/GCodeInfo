package de.dietzm.gcodesim;

import de.dietzm.Position;

public interface GraphicRenderer {
	
	public final int LAYER=0;
	public final int ZPOS=1;
	public final int XYSPEED=2;
	public final int ESPEED=3;
	public final int RTIME=4;
	public final int SPEEDUP=5;
	public final int FAN=6;
	public final int SIDEVIEW=7;
	public final int FRONTVIEW=8;
	public final int DETAILS=9;
	public final int LHEIGHT=10;
	public final int AVGSPEED=11;
	public final int ZHEIGHT=12;
	public final int PRINTTIME=13;
	public final int COST=14;
	public final int PRINT=15;
	public final int WAIT=16;
	public final int PAUSE=17;

	public void setColor(int idx);
	public void setStroke(int idx);
	public int getColorNr();
	
	public void drawline(float x,float y, float x1,float y1);
	public void drawrect(float x,float y, float w,float h);
	
	public void setPos(int x, int y);
	public void drawArc(int x,int y,int width, int height,int theta, int delta);
	public void fillArc(int x, int y, int width, int height, int theta, int delta, int colitem) ;
	public void fillrect(float x,float y, float w,float h);
	public void drawtext(int id, float x, float y,float w);
	public void drawtext(CharSequence text,float x, float y);
	public void clearrect(float x,float y, float w,float h,int colitem);
	public void setActiveExtruder(int ex);
	public void setExtruderOffset(Position[] offset,float zoom);
	public void faintRect(float x,float y, float w,float h);
	public int getWidth();
	public int getHeight();
	public void repaint();
	public void setFontSize(float font);
	public String browseFileDialog();
	public void drawtext(CharSequence text, float x, float y, float w);
	
	public void setTitle(String txt);
	/**
	 * Set Position for the side & Front view
	 * @param x1
	 * @param x2
	 * @param z
	 */
	public void setPos(int x1,int x2,int z);

}

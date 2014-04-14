package de.dietzm.gcodesim;

import de.dietzm.Position;
import de.dietzm.gcodes.GCode;

public interface GraphicRenderer {

	public void setColor(int idx);
	public void setStroke(int idx);
	public int getColorNr();
	
	public void drawline(float x,float y, float x1,float y1);
	public void drawrect(float x,float y, float w,float h);
	
	public void setPos(int x, int y);
	public void drawArc(int x,int y,int width, int height,int theta, int delta);
	public void fillrect(float x,float y, float w,float h);
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

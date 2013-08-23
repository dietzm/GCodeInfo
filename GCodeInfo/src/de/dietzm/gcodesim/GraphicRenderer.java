package de.dietzm.gcodesim;

import de.dietzm.GCode;

public interface GraphicRenderer {

	public void setColor(int idx);
	public void setStroke(int idx);
	
	public void drawline(float x,float y, float x1,float y1);
	public void drawrect(float x,float y, float w,float h);
	
	public void setPos(int x, int y);
	public void drawArc(int x,int y,int width, int height,int theta, int delta);
	public void fillrect(float x,float y, float w,float h);
	public void drawtext(String text,float x, float y);
	public void clearrect(float x,float y, float w,float h);
	public int getWidth();
	public int getHeight();
	public void repaint();
	public void setFontSize(float font);
	public String browseFileDialog();
	public void drawtext(String text, float x, float y, float w);

}

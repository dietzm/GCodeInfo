package de.dietzm;

/**
 * Current Position. A class is more memory efficient than an array
 * @author mdietz
 *
 */
public class Position {
	public float x,y;
	
	public Position(float x1,float y1){
		x=x1;
		y=y1;
	}
	
	public void updatePos(Position pos){
		x=pos.x;
		y=pos.y;
	}
	
	public void updatePos(float x1 , float y1){
		x=x1;
		y=y1;
	}
	
	public float getDistance(float x1 , float y1){
		float xmove = Math.abs(x - x1);
		float ymove = Math.abs(y - y1);
		if (xmove + ymove == 0)
			return 0;
		float move = (float) Math.sqrt((xmove * xmove) + (ymove * ymove));
		return move;
	}
}

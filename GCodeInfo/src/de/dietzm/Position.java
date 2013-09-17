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
		//z=z1;
	}
	
	public void updatePos(Position pos){
		x=pos.x;
		y=pos.y;
		//z=pos.z;
	}
}

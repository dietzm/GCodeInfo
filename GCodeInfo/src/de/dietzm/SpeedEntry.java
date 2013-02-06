package de.dietzm;

import java.util.Set;
import java.util.TreeSet;

/**
 * Class to represent a certain speed (for speed categorization)
 * @author mdietz
 *
 */
public class SpeedEntry {
	
	public enum Speedtype{BOTH,NONE,PRINT,TRAVEL};
	float distance=0;
	Set<Integer> layers = new TreeSet<Integer>();
	float speed;
	float time=0;
	Speedtype type = Speedtype.NONE ;
	
	public SpeedEntry(float speed, float time){
		this.time=time;
		this.speed=speed;
	}
	
	public SpeedEntry(float speed, float time,int layer){
		this.time=time;
		this.speed=speed;
		addLayers(layer);
	}
	public void addDistance(float distance) {
		this.distance += distance;
	}
	
	public void addLayers(int layer) {
		this.layers.add(layer);
	}
	public void addTime(float time) {
		this.time += time;
	}
	public float getDistance() {
		return distance;
	}
	public Set<Integer> getLayers() {
		return layers;
	}
	
	public float getSpeed() {
		return speed;
	}
	public float getTime() {
		return time;
	}
	public Speedtype getType() {
		return type;
	}
	/**
	 * Set the speed type taking the current type into account
	 * PRINT+TRAVEL=Both
	 * @param tp
	 */	
	public void setPrint(Speedtype tp) {
		switch (tp) {
		case PRINT:
			if(type==Speedtype.TRAVEL || type==Speedtype.BOTH){
				type=Speedtype.BOTH;
			}else{
				type=Speedtype.PRINT;
			}
			break;
		case TRAVEL:
			if(type==Speedtype.PRINT || type==Speedtype.BOTH){
				type=Speedtype.BOTH;
			}else{
				type=Speedtype.TRAVEL;
			}
			break;
		default:
			type=tp;
			break;
		}
	}
	public void setSpeed(float speed) {
		this.speed = speed;
	}
	public void setTime(float time) {
		this.time = time;
	}
	

}

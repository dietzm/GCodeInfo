package de.dietzm;

import de.dietzm.gcodes.GCode;

public class GCodeMod {

	/**
	 * Update the bed temp values 
	 * @param absolute values for bed 
	 */
	public static void changeBedTemp(GCode gc, float bed){
		
	}

	/**
	 * Update the extrusion values 
	 * @param percent , negative value for slower
	 */
	public static void changeExtrusion(GCode gc,int percent){}

	/**
	 * Update the extruder temp values 
	 * @param absolute values 
	 */
	public static void changeExtTemp(GCode gc,float extr){}

	/**
	 * Update the fan speed 
	 * @param absolute value (0 = off, 255=full speed)
	 */
	public static void changeFan(GCode gc,int value){}

	/**
	 * Update the layerheight (and the corresponding extrusion rate)
	 * @param percent , negative value for slower
	 */
	public static void changeLayerHeight(GCode gc,int percent){}

	/**
	 * Update the speed values 
	 * @param percent , negative value for slower
	 */
	public static void changeSpeed(GCode gc,int percent, boolean printonly){}

	public static void changeToComment(GCode gc){}

	/**
	 * Add offset to x (no extrusion rate change)
	 * @param absolute value 
	 */
	public static void changeXOffset(GCode gc,float value){}

	/**
	 * Add offset to y (no extrusion rate change)
	 * @param absolute value 
	 */
	public static void changeYOffset(GCode gc,float value){}

	/**
	 * Add offset to z (no extrusion rate change)
	 * @param absolute value 
	 */
	public static void changeZOffset(GCode gc,float value){}

}

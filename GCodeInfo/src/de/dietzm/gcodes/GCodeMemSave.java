package de.dietzm.gcodes;

import de.dietzm.Constants;
import de.dietzm.Position;
import de.dietzm.Constants.GCDEF;


/**
 * Memory efficient GCode Class
 * This is a generic GCode class, which can hold all different kinds of gcodes.
 * There are specialized gcode classes which can be used to save even more memory.
 * @author mdietz
 *
 */
public class GCodeMemSave extends GCodeAbstract {
		
	//Parsed values, not changed by analyse 
	private float e=Float.MAX_VALUE;
	private float f=Float.MAX_VALUE; //Speed
	private float x=Float.MAX_VALUE;
	private float y=Float.MAX_VALUE;
	private float z=Float.MAX_VALUE;
	//Store additional values in separate class to avoid memory consumption if not needed
	private Extended ext =null;
	private class Extended{ 
		private float ix=Float.MAX_VALUE;
		private float jy=Float.MAX_VALUE;
		private float kz=Float.MAX_VALUE;
		private float r=Float.MAX_VALUE; //Reuse also for T (tool change, offset)	
		private float s_ext=Float.MAX_VALUE;
		private float s_bed=Float.MAX_VALUE;
		private float s_fan=Float.MAX_VALUE;
		private String unit = null; //default is mm
	}
	
	//Dynamic values updated by analyse	 (7MB for 300000 gcodes)
	private float timeaccel; //track acceleration as extra time 
	private float distance;
	private float extrusion;
	private short fanspeed; //remember with less accuracy (just for display)
	private float curX,curY;
	
	//private float extemp,bedtemp;	
	@Override
	public short getFanspeed() {
		return fanspeed;
	}

	/**
	 * Set the fanspeed to remember how long the fan is turned on.
	 * This does NOT change the s_fan variable and will not written to gcode on save
	 * @param fanspeed
	 */
	@Override
	public void setFanspeed(float fanspeed) {
		this.fanspeed = (short)fanspeed;
	}

	@Override
	public float getExtemp() {
		if(!isInitialized(Constants.SE_MASK)) return -255;
		return ext.s_ext;
	}

	@Override
	public float getBedtemp() {
		if(!isInitialized(Constants.SB_MASK)) return -255;
		return ext.s_bed;
	}


	

	
	public GCodeMemSave(byte[] line,GCDEF gc){
		super(line,gc);
	}

	public GCodeMemSave(String line,GCDEF gc){
		super(line,gc);
	}
	@Override
	public float getS_Bed() {
		return ext.s_bed;
	}

	
	/**
	 * 
	 * @param pos pass a position object to avoid object creation
	 * @return
	 */
	@Override
	public Position getCurrentPosition(Position pos) {
		pos.x=curX;
		pos.y=curY;
		return pos;
	}
	@Override
	public float getDistance() {
		return distance;
	}
	
	@Override
	public float getE() {
		return e;
	}
	
	@Override
	public float getS_Ext() {
		return ext.s_ext;
	}

	@Override
	public float getExtrusion() {
		return extrusion;
	}

	@Override
	public float getF() {
		return f;
	}
	@Override
	public float getS_Fan() {
		return ext.s_fan;
	}

	@Override
	public Position getPosition(Position reference){
		return new Position( isInitialized(Constants.X_MASK)?x:reference.x,isInitialized(Constants.Y_MASK)?y:reference.y);
	}
	/**
	 * Speed in mm/s based on distance/time
	 * @return
	 */
	@Override
	public float getSpeed(){
		return Constants.round2digits((distance/timeaccel));
	}
	
	/**
	 * Do soem tricks to make sure that the saved output file is 100% equal to the input file (if nothing has been changed)
	 * @param prefix
	 * @param val
	 * @param digits
	 * @return
	 */
	protected String getIfInit(String prefix,float val,int digits,int mask){
		if(!isInitialized(mask)) return "";
		if(digits==0){
			String var = String.format(" "+prefix+"%.1f", val);
			return Constants.removeTrailingZeros(var); //remove trailing zero
		}
		if("E".equals(prefix) && val == 0) return " E0";  //replace E0.0000 with E0 
		return String.format(" "+prefix+"%."+digits+"f", val);		
	}



	@Override
	public String getUnit() {
		if(ext==null) return "mm";
		return ext.unit;
	}
	
	public void setUnit(String unit){
		if(ext==null) ext = new Extended();
		ext.unit=unit;
	}

	@Override
	public float getX() {
		return x;
	}
	@Override
	public float getIx() {
		if(ext==null) return 0;
		return ext.ix;
	}

	@Override
	public float getJy() {
		if(ext==null) return 0;
		return ext.jy;
	}

	@Override
	public float getKz() {
		if(ext==null) return 0;
		return ext.kz;
	}
	
	@Override
	public float getR() {
		if(ext==null) return 0;
		return ext.r;
	}


	@Override
	public float getY() {
		return y;
	}

	/**
	 * Get Z position change. Rounded to 2 digits behind comma.
	 * @return zPosition
	 */
	@Override
	public float getZ() {
		return z;
	}

	/**
	 * Is filament getting extruded or restract
	 * @return true is extruding
	 */
	@Override
	public boolean isExtrudeOrRetract(){
		return ( isInitialized(Constants.E_MASK) && extrusion != 0 );
	}

	/**
	 * Is filament getting extruded (no incl. retract)
	 * @return true is extruding
	 */
	@Override
	public boolean isExtruding(){
		return ( isInitialized(Constants.E_MASK) || extrusion > 0 );
	}
	

	



	

//	/**
//	 * Set the bedtemp to remember the configured temp.
//	 * This does NOT change the s_bed variable and will not written to gcode on save
//	 * @param fanspeed
//	 */
//	public void setBedtemp(float bedtemp) {
//		this.bedtemp = bedtemp;
//	}



	@Override
	public void setCurrentPosition(Position currentPosition) {
		curX=currentPosition.x;
		curY=currentPosition.y;
	}

	@Override
	public void setDistance(float distance) {
		this.distance = distance;
	}

//	/**
//	 * Set the exttemp to remember the configured temp.
//	 * This does NOT change the s_ext variable and will not written to gcode on save
//	 * @param fanspeed
//	 */
//	public void setExtemp(float extemp) {
//		this.extemp = extemp;
//	}
	
	@Override
	public void setExtrusion(float extrusion) {
		this.extrusion = extrusion;
	}
	
	@Override
	public void setTimeAccel(float time) {
		this.timeaccel = time;
	}
	
	
	@Override
	public float getTimeAccel() {
		return this.timeaccel;
	}
	
	
	/**
	 * Get Extrusion speed (mm/min)
	 * @return
	 */
	@Override
	public float getExtrusionSpeed(){
		return (extrusion/timeaccel)*60f;
	}
	

	@Override
	public String toString() {		
		String var = ":  "+toStringRaw();
		var+="\tExtrusion:"+extrusion;
		var+="\tDistance:"+distance;
		var+="\tPosition:"+curX+"x"+curY;
		var+="\tTime:"+timeaccel;
		return var;
	}
	

	@Override
	public String toCSV() {		
		String var = String.valueOf(getSpeed());
		var+=";"+extrusion;
		var+=";"+distance;
		var+=";"+timeaccel;
		var+=";"+fanspeed;
		return var;
	}
	
	public void setInitialized(short mask, float value){
		if(mask > Constants.Z_MASK && ext==null)ext=new Extended();
		
		switch (mask) {
		case Constants.E_MASK:
			e = value;
			break;
		case Constants.X_MASK:
			x  = value;
			break;
		case Constants.Y_MASK:
			y = value;
			break;
		case Constants.Z_MASK:
			z = value;
			break;
		case Constants.F_MASK:
			f = value;
			break;
		case Constants.SE_MASK:
			ext.s_ext = value;
			break;
		case Constants.SB_MASK:
			ext.s_bed  = value;
			break;
		case Constants.SF_MASK:
			ext.s_fan = value;	
			break;
		case Constants.IX_MASK:
			 ext.ix  = value;
			 break;
		case Constants.JY_MASK:
			 ext.jy  = value;
			 break;
		case Constants.KZ_MASK:
			ext.kz = value;
			break;
		case Constants.R_MASK:
			ext.r = value;
			break;
		default:
			break;
		}
		
	}
	
	/**
	 * check if a particular field is initialized
	 * Only check one field at once
	 * @param mask
	 * @return boolean
	 */
	@Override
	public boolean isInitialized(int mask){
		switch (mask) {
		case Constants.E_MASK:
			return e != Float.MAX_VALUE;
		case Constants.X_MASK:
			return x != Float.MAX_VALUE;
		case Constants.Y_MASK:
			return y != Float.MAX_VALUE;
		case Constants.Z_MASK:
			return z != Float.MAX_VALUE;
		case Constants.F_MASK:
			return f != Float.MAX_VALUE;
		case Constants.SE_MASK:
			return ext != null && ext.s_ext != Float.MAX_VALUE;
		case Constants.SB_MASK:
			return ext != null && ext.s_bed != Float.MAX_VALUE;
		case Constants.SF_MASK:
			return ext != null && ext.s_fan != Float.MAX_VALUE;		
		case Constants.IX_MASK:
			return ext != null && ext.ix != Float.MAX_VALUE;		
		case Constants.JY_MASK:
			return ext != null && ext.jy != Float.MAX_VALUE;		
		case Constants.KZ_MASK:
			return ext != null && ext.kz != Float.MAX_VALUE;		
		case Constants.R_MASK:
			return ext != null && ext.r != Float.MAX_VALUE;		
		default:
			break;
		}
		return false;
	}
	


}

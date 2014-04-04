package de.dietzm.gcodes;

import de.dietzm.Constants;
import de.dietzm.Position;
import de.dietzm.Constants.GCDEF;



public class GCodeZ extends GCodeAbstract {
		
	private float z=Float.MAX_VALUE;
			
	//Dynamic values updated by analyse	 (7MB for 300000 gcodes)
	private float time;
	private float timeaccel; //track acceleration as extra time 
	private float distance;
	private short fanspeed; //remember with less accuracy (just for display)

	
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


	
	public GCodeZ(String line,int linenr,GCDEF gc){
		super(line,linenr,gc);
	}

	public GCodeZ(String line,GCDEF gc){
		super(line,0,gc);
	}

	
	/**
	 * 
	 * @param pos pass a position object to avoid object creation
	 * @return
	 */
	@Override
	public Position getCurrentPosition(Position pos) {
		return null;
	}
	@Override
	public float getDistance() {
		return distance;
	}
	
	@Override
	public float getE() {
		return 0;
	}
	


	@Override
	public float getExtrusion() {
		return 0;
	}

	@Override
	public float getF() {
		return 0;
	}


	@Override
	public Position getPosition(Position reference){
		return null;
	}
	/**
	 * Speed in mm/s based on distance/time
	 * @return
	 */
	@Override
	public float getSpeed(){
		return Constants.round2digits((distance/time));
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
	public float getTime() {
		return time;
	}

	@Override
	public String getUnit() {
		return null;
	}
	
	public void setUnit(String unit){

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
		return false;
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
		
	}
	

	


	@Override
	public void setTime(float time) {
		this.time = time;
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
		return 0;
	}
	

	@Override
	public String toString() {		
		String var = lineindex+":  "+toStringRaw();
		var+="\tExtrusion:"+0;
		var+="\tDistance:"+distance;
		var+="\tPosition:"+0+"x"+0;
		var+="\tTime:"+time;
		return var;
	}
	

	@Override
	public String toCSV() {		
		String var = String.valueOf(getSpeed());
		var+=";"+0;
		var+=";"+distance;
		var+=";"+time;
		var+=";"+fanspeed;
		return var;
	}
	
	public void setInitialized(short mask, float value){
		switch (mask) {

		case Constants.Z_MASK:
			z = value;
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
		
		case Constants.Z_MASK:
			return z != Float.MAX_VALUE;
	
		default:
			break;
		}
		return false;
	}

	@Override
	public boolean isExtruding() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public float getY() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getR() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getKz() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getJy() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getIx() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getX() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getS_Fan() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getS_Ext() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getS_Bed() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getBedtemp() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getExtemp() {
		// TODO Auto-generated method stub
		return 0;
	}
	


}

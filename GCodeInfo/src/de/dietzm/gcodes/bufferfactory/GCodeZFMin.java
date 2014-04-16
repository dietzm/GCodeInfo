package de.dietzm.gcodes.bufferfactory;

import de.dietzm.Constants;
import de.dietzm.Position;
import de.dietzm.Constants.GCDEF;
import de.dietzm.gcodes.MemoryEfficientLenString;
import de.dietzm.gcodes.MemoryEfficientString;



public class GCodeZFMin extends GCodeAbstractNoData {
		
	private float z=Float.MAX_VALUE;
	private float f=Float.MAX_VALUE; 	
			
	//Dynamic values updated by analyse	 (7MB for 300000 gcodes)
	private float time;
	private float timeaccel; //track acceleration as extra time 
	private float distance;
	

	
	//private float extemp,bedtemp;	
	@Override
	public short getFanspeed() {
		return 0;
	}

	/**
	 * Set the fanspeed to remember how long the fan is turned on.
	 * This does NOT change the s_fan variable and will not written to gcode on save
	 * @param fanspeed
	 */
	@Override
	public void setFanspeed(float fanspeed) {
		
	}


	


	public GCodeZFMin(String line,GCDEF gc){
		super(gc);
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
		return f;
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
	public MemoryEfficientString getCodeline() {
		byte[] buf = new byte[256]; //TODO 256 might be too small
		int len = getCodeline(buf);
		return new MemoryEfficientLenString(buf,len);
	}



	@Override
	public int getCodeline(byte[] buffer) {
		int len = 0;
		//G1 
		byte[] gc1=getGcode().getBytes();
		System.arraycopy(gc1,0,buffer,0,gc1.length);
		len=gc1.length;
		
		
		buffer[len++]=Constants.spaceb;
		buffer[len++]=Constants.Zb;
		len = Constants.floatToString3(z,buffer,len);
		
		buffer[len++]=Constants.spaceb;
		buffer[len++]=Constants.Fb;
		len = Constants.floatToString0(f,buffer,len);
				
		buffer[len++]=Constants.newlineb;	
		return len;
	}
	

	

	@Override
	public String toString() {		
		String var = ":  ";
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
		return var;
	}
	
	public void setInitialized(short mask, float value){
		switch (mask) {

		case Constants.Z_MASK:
			z = value;
			break;
		case Constants.F_MASK:
			f = value;
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
		case Constants.F_MASK:
			return f != Float.MAX_VALUE;
	
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

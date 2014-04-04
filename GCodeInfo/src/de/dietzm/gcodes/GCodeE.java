package de.dietzm.gcodes;

import de.dietzm.Constants;
import de.dietzm.Position;
import de.dietzm.Constants.GCDEF;


public class GCodeE extends GCodeAbstract {

	private float e=Float.MAX_VALUE; //will be initalitzed with absolut extrusion 
	
	//Dynamic values updated by analyse	 (7MB for 300000 gcodes)
	private float time;
	private float timeaccel; //track acceleration as extra time 
//	private float distance;
	//private short fanspeed; //remember with less accuracy (just for display)
	
	
	public GCodeE(String line, int linenr, GCDEF code) {
		super(line, linenr, code);
	}


	@Override
	public void setInitialized(short mask, float value) {
		switch (mask) {
		case Constants.E_MASK:
			e = value;
			break;
		default:
			break;
		}

	}
	@Override
	public String toCSV() {		
		String var = String.valueOf(getSpeed());
		var+=";"+e;
		return var;
	}
	
	

	@Override
	public String toString() {		
		String var = lineindex+":  "+toStringRaw();
		var+="\tExtrusion:"+e;
		return var;
	}
	



	@Override
	public float getS_Fan() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getF() {
		// TODO Auto-generated method stub
		return 0;
	}



	@Override
	public float getS_Ext() {
		// TODO Auto-generated method stub
		return 0;
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




	@Override
	public void setUnit(String unit) {
		// TODO Auto-generated method stub
		
	}







	@Override
	public float getDistance() {
		return 0;
	}



	@Override
	public float getE() {
		return e;
	}



	@Override
	public float getExtrusion() {
		if(!isInitialized(Constants.E_MASK)) return 0;
		return e;
	}




	//private float extemp,bedtemp;	
		@Override
		public short getFanspeed() {
			return Short.MAX_VALUE;
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
		return 0;
	}



	@Override
	public float getTime() {
		return time;
	}



	@Override
	public float getTimeAccel() {
		return timeaccel;
	}





	@Override
	public float getX() {
		return 0;
	}



	@Override
	public float getY() {
		return 0;
	}



	/**
	 * Is filament getting extruded or restract
	 * @return true is extruding
	 */
	@Override
	public boolean isExtrudeOrRetract(){
		return ( isInitialized(Constants.E_MASK) && e != 0 );
	}



	/**
	 * Is filament getting extruded (no incl. retract)
	 * @return true is extruding
	 */
	@Override
	public boolean isExtruding(){
		return ( isInitialized(Constants.E_MASK) && e > 0 );
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
		default:
			break;
		}
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
	}



	
		@Override
		public void setExtrusion(float extrusion) {
			e = extrusion; //overwrite to save memory 
		}



	/**
	 * Set the fanspeed to remember how long the fan is turned on.
	 * This does NOT change the s_fan variable and will not written to gcode on save
	 * @param fanspeed
	 */
	@Override
	public void setFanspeed(float fanspeed) {
	}



	@Override
	public void setTime(float time) {
		this.time=time;
	}



	@Override
	public void setTimeAccel(float time) {
		this.timeaccel=time;
	}


	@Override
	public float getZ() {
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
	public String getUnit() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public float getExtrusionSpeed() {
		// TODO Auto-generated method stub
		return 0;
	}

}

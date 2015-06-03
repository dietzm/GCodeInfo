package de.dietzm.gcodes.bufferfactory;

import de.dietzm.Constants;
import de.dietzm.Position;
import de.dietzm.Constants.GCDEF;
import de.dietzm.gcodes.MemoryEfficientLenString;
import de.dietzm.gcodes.MemoryEfficientString;


public class GCodeFMin extends GCodeAbstractNoData {

	//private float e=Float.MAX_VALUE; //will be initalitzed with absolut extrusion 
	private float f=Float.MAX_VALUE;  
	//Dynamic values updated by analyse	 (7MB for 300000 gcodes)
	private float timeaccel; //track acceleration as extra time 
//	private float distance;
	//private float extrusion; //remember with less accuracy (just for display)
	
	
	public GCodeFMin(String line,  GCDEF code) {
		super(  code);
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
		buffer[len++]=Constants.Fb;
		len = Constants.floatToString0(f,buffer,len);
		
			
		buffer[len++]=Constants.newlineb;	
		return len;
	}

	@Override
	public void setInitialized(short mask, float value) {
		switch (mask) {
		case Constants.F_MASK:
			f = value;
			break;
		default:
			break;
		}

	}
	@Override
	public String toCSV() {		
		String var = String.valueOf(getSpeed());
		var+=";";
		var+=";";
		return var;
	}
	
	

	@Override
	public String toString() {		
		String var = ":  ";
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
		return f;
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
		return Float.MAX_VALUE;
	}



	@Override
	public float getExtrusion() {
		return 0;
	}





	//private float extemp,bedtemp;	
	@Override
	public short getFanspeed() {
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
		return f;
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
		return false;
	}



	/**
	 * Is filament getting extruded (no incl. retract)
	 * @return true is extruding
	 */
	@Override
	public boolean isExtruding(){
		return false; //( isInitialized(Constants.E_MASK) && extrusion > 0 );
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
		case Constants.F_MASK:
			return f != Float.MAX_VALUE;
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
		}



	/**
	 * Set the fanspeed to remember how long the fan is turned on.
	 * This does NOT change the s_fan variable and will not written to gcode on save
	 * @param fanspeed
	 */
	@Override
	public void setFanspeed(float fanspeed) {
		//this.fanspeed = (short)0;
	}






	@Override
	public void setTimeAccel(float time) {
		this.timeaccel = time;
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
		return 0;
	}

}

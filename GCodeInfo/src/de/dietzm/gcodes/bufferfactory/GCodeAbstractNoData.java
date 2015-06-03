package de.dietzm.gcodes.bufferfactory;

import de.dietzm.Constants;
import de.dietzm.Position;
import de.dietzm.Constants.GCDEF;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.MemoryEfficientString;

/**
 * Abstract Class GCode
 * 
 * This class represents a parsed GCode line with it parameters as well as some calculated metadata like time, distance and more.
 * The abstract class contains the bare minimum to allow sub classes to implement the required fields only. 
 * Specialized sub classes can store different kind of gcodes and provide only the required fields. This allows to 
 * save significant amount of memory, which would be wasted when using a single class with all fields. 
 * A typical gcode file can have more than 500000 lines and this code is used on low memory devices and therefore every byte is important.
 *     
 * 
 * @author mdietz
 *
 */
public abstract class GCodeAbstractNoData implements GCode {

   // protected byte[] data; //Store String in a more memory efficient way
    protected short gcode;

	public GCodeAbstractNoData(GCDEF code){
	//	data=line;
		gcode=code.getId();
	}

	
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getComment()
	 */
	@Override
	public String getComment() {
		return "";
	}
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#isComment()
	 */
	@Override
	public boolean isComment(){
		 return Constants.GCDEF.COMMENT.equals(gcode); 
	}
	

	
	/**
	 * Find comments and strip them, init the comment filed
	 * @param clv
	 * @return
	 */
	protected String stripComment(String clv) {
		int idx;
		if((idx = clv.indexOf(';')) != -1){
			//is a comment
			clv=clv.substring(0, idx);
		}else if((idx = clv.indexOf("(<")) != -1){
			//is a comment
			clv=clv.substring(0, idx);
		}else if((idx = clv.indexOf('(')) != -1){
			//is a comment
			clv=clv.substring(0, idx);
		}
		return clv.trim();
	}

	
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#isPrintable()
	 */
	@Override
	public boolean isPrintable(){
		 return !Constants.GCDEF.COMMENT.equals(gcode) ; 
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getCodeline()
	 */
	@Override
	public MemoryEfficientString getCodeline() {
//			if(getGcode() == GCDEF.COMMENT || getGcode() ==GCDEF.UNKNOWN){
//				return new MemoryEfficientString(data, Constants.newline);
//			}
//				
//			byte[] gc1=getGcode().getBytes();
//			int len=gc1.length+2+data.length;
//			byte[] newdata = new byte[len];
//		
//			System.arraycopy(gc1,0,newdata,0,gc1.length);
//			System.arraycopy(data,0,newdata,gc1.length+1,data.length);
//			newdata[gc1.length]=' ';
//			newdata[len-1]='\n';			
//			
			return new MemoryEfficientString("UNKNOWN codeline :"+getGcode());	
	}
	
	
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getCodeline(byte[])
	 */
	@Override
	public int getCodeline(byte[] buffer) {
//			if(getGcode() == GCDEF.COMMENT || getGcode() ==GCDEF.UNKNOWN){
//				System.arraycopy(data,0,buffer,0,data.length);
//				buffer[data.length]=Constants.newlineb;
//				return data.length+1;
//			}
//			
//			byte[] gc1=getGcode().getBytes();
//			int len = (gc1.length+2+data.length); //+2 for space and \n
//			if(buffer.length < len){
//				System.err.println("GCode.getCodeline: Buffer to small");
//				return -1;
//			}		
//			System.arraycopy(gc1,0,buffer,0,gc1.length);
//			System.arraycopy(data,0,buffer,gc1.length+1,data.length);
//			buffer[gc1.length]=Constants.spaceb;
//			buffer[len-1]=Constants.newlineb;	
			
			return 0;			
	}
	
	


	


	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getGcode()
	 */
	@Override
	public Constants.GCDEF getGcode() {
		return GCDEF.getGCDEF(gcode);
	}
	
	public short getGcodeId(){
		return gcode;
	}

	

	

	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#isBuffered()
	 */
	@Override
	public boolean isBuffered() {
		//if(gcode==null) return false;
		if (Constants.GCDEF.G0.equals(gcode) || Constants.GCDEF.G1.equals(gcode) || Constants.GCDEF.G2.equals(gcode)  || Constants.GCDEF.M106.equals(gcode) || Constants.GCDEF.M107.equals(gcode)   ||Constants.GCDEF.G29.equals(gcode) || Constants.GCDEF.G30.equals(gcode) || Constants.GCDEF.G31.equals(gcode) || Constants.GCDEF.G32.equals(gcode)){
			return true;
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#isLongRunning()
	 */
	@Override
	public boolean isLongRunning() {
		if (Constants.GCDEF.M190.equals(gcode) || Constants.GCDEF.M109.equals(gcode) || Constants.GCDEF.G28.equals(gcode) || Constants.GCDEF.M29.equals(gcode) ){
			return true;
		}
		return false;
	}
	
	public abstract void setUnit(String unit);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#isInitialized(int)
	 */
	@Override
	public abstract boolean isInitialized(int mask);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#setInitialized(short, float)
	 */
	@Override
	public abstract void setInitialized(short mask, float value);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#toCSV()
	 */
	@Override
	public abstract String toCSV();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#toString()
	 */
	@Override
	public String toString(){
		return getCodeline().toString();
	}
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getExtrusionSpeed()
	 */
	@Override
	public abstract float getExtrusionSpeed();
	@Override
	public abstract float getTimeAccel();
//	{
//		if(getTime() == 0) return 0; //prevent NaN
//		return getDistance() / (((Math.min(40*60,getDistance() *60/getTime())+getDistance() *60/getTime())/2) / 60);
//	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#setTimeAccel(float)
	 */
	@Override
	public abstract void setTimeAccel(float time);

	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#setExtrusion(float)
	 */
	@Override
	public abstract void setExtrusion(float extrusion);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#setDistance(float)
	 */
	@Override
	public abstract void setDistance(float distance);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#setCurrentPosition(de.dietzm.Position)
	 */
	@Override
	public abstract void setCurrentPosition(Position currentPosition);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#isExtruding()
	 */
	@Override
	public abstract boolean isExtruding();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#isExtrudeOrRetract()
	 */
	@Override
	public abstract boolean isExtrudeOrRetract();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getZ()
	 */
	@Override
	public abstract float getZ();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getY()
	 */
	@Override
	public abstract float getY();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getR()
	 */
	@Override
	public abstract float getR();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getKz()
	 */
	@Override
	public abstract float getKz();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getJy()
	 */
	@Override
	public abstract float getJy();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getIx()
	 */
	@Override
	public abstract float getIx();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getX()
	 */
	@Override
	public abstract float getX();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getUnit()
	 */
	@Override
	public abstract String getUnit();

	//protected abstract String getIfInit(String prefix, float val, int digits, int mask);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getSpeed()
	 */
	@Override
	public abstract float getSpeed();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getPosition(de.dietzm.Position)
	 */
	@Override
	public abstract Position getPosition(Position reference);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getS_Fan()
	 */
	@Override
	public abstract float getS_Fan();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getF()
	 */
	@Override
	public abstract float getF();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getExtrusion()
	 */
	@Override
	public abstract float getExtrusion();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getS_Ext()
	 */
	@Override
	public abstract float getS_Ext();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getE()
	 */
	@Override
	public abstract float getE();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getDistance()
	 */
	@Override
	public abstract float getDistance();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getCurrentPosition(de.dietzm.Position)
	 */
	@Override
	public abstract Position getCurrentPosition(Position pos);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getS_Bed()
	 */
	@Override
	public abstract float getS_Bed();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getBedtemp()
	 */
	@Override
	public abstract float getBedtemp();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getExtemp()
	 */
	@Override
	public abstract float getExtemp();
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#setFanspeed(float)
	 */
	@Override
	public abstract void setFanspeed(float fanspeed);
	/* (non-Javadoc)
	 * @see de.dietzm.gcodes.GCode#getFanspeed()
	 */
	@Override
	public abstract short getFanspeed();

}
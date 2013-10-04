package de.dietzm.gcodes;

import java.io.UnsupportedEncodingException;

import de.dietzm.Constants;
import de.dietzm.Position;
import de.dietzm.Constants.GCDEF;

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
public abstract class GCode {

    protected byte[] data; //Store String in a more memory efficient way
    protected short gcode;
	protected int lineindex;
	
	public GCode(String line,int linenr,GCDEF code){
		/*
		 * Append newline to avoid copying byte[] and creating new strings when sending to printer
		 * Requires a bit more memory now, but avoids memory allocations during print
		 * TODO instead of appending newline here we should rather not cut it off during read file
		 */
	//	String linewcr= new StringBuilder().append(line).append(Constants.newlinec).toString();
		updateDataArray(line);
		lineindex=linenr;
		gcode=code.getId();
	}

	
	public String getComment() {
		return MemoryEfficientString.toString(data);
	}
	/**
	 * Is printable, returns false for comments only and unknown GCODES
	 * @return
	 */
	public boolean isComment(){
		 return Constants.GCDEF.INVALID.equals(gcode); 
	}
	
	public void setLineindex(int lineindex) {
		this.lineindex = lineindex;
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

	
	/**
	 * Is printable, returns false for comments only and unknown GCODES
	 * @return
	 */
	public boolean isPrintable(){
		 return !Constants.GCDEF.INVALID.equals(gcode) ; 
	}
	
	public MemoryEfficientString getCodeline() {
			byte[] gc1=getGcode().getBytes();
			int len=gc1.length+2+data.length;
			byte[] newdata = new byte[len];
		
			System.arraycopy(gc1,0,newdata,0,gc1.length);
			System.arraycopy(data,0,newdata,gc1.length+1,data.length);
			newdata[gc1.length]=' ';
			newdata[len-1]='\n';			
			
			return new MemoryEfficientString(newdata);	
	}
	
	
	/**
	 * get codeline into the provided buffer.
	 * Save memory allocations
	 * @param buffer
	 * @return len, how much data is written to buffer,-1 if buffer is too small
	 */
	public int getCodeline(byte[] buffer) {
			byte[] gc1=getGcode().getBytes();
			int len = (gc1.length+2+data.length); //+2 for space and \n
			if(buffer.length < len){
				System.err.println("GCode.getCodeline: Buffer to small");
				return -1;
			}		
			System.arraycopy(gc1,0,buffer,0,gc1.length);
			System.arraycopy(data,0,buffer,gc1.length+1,data.length);
			buffer[gc1.length]=Constants.spaceb;
			buffer[len-1]=Constants.newlineb;	
			
			return len;			
	}
	
	

	protected void updateDataArray(String line) {
		/**
		 * Store a memory efficient copy only
		 */
		 try {
		      data = line.getBytes(Constants.ENCODING);
		    } catch (UnsupportedEncodingException e) {
		      throw new RuntimeException("Unexpected: " + Constants.ENCODING + " not supported!");
		    }
	}
	public int getLineindex() {
		return lineindex;
	}
	public Constants.GCDEF getGcode() {
		return GCDEF.getGCDEF(gcode);
	}
	protected MemoryEfficientString subSequence(int start, int end) {
	  if (start < 0 || end > (data.length)) {
	    throw new IllegalArgumentException("Illegal range " +
	      start + "-" + end + " for sequence of length " + data.length);
	  }
	  byte[] newdata = new byte[end-start];
	  System.arraycopy(data,start,newdata,0,end-start);
	  return new MemoryEfficientString(newdata);
	}
	
	protected String toStringRaw() {
	  try {
	    return new String(data, 0, data.length, Constants.ENCODING);
	  } catch (UnsupportedEncodingException e) {
	    throw new RuntimeException("Unexpected: " + Constants.ENCODING + " not supported");
	  }
	}
	

	/**
	 * Is it a gcode which is buffered by the firmware
	 * @return boolean true if buffered gcodes
	 */
	public boolean isBuffered() {
		//if(gcode==null) return false;
		if (Constants.GCDEF.G0.equals(gcode) || Constants.GCDEF.G1.equals(gcode) || Constants.GCDEF.G2.equals(gcode)  || Constants.GCDEF.M106.equals(gcode) || Constants.GCDEF.M107.equals(gcode)   ||Constants.GCDEF.G29.equals(gcode) || Constants.GCDEF.G30.equals(gcode) || Constants.GCDEF.G31.equals(gcode) || Constants.GCDEF.G32.equals(gcode)){
			return true;
		}
		return false;
	}

	/**
	 * Is it a gcode which is long running and should not cause a timeout
	 * @return boolean true if buffered gcodes
	 */
	public boolean isLongRunning() {
		if (Constants.GCDEF.M190.equals(gcode) || Constants.GCDEF.M109.equals(gcode) || Constants.GCDEF.G28.equals(gcode) ){
			return true;
		}
		return false;
	}
	
	protected abstract void setUnit(String unit);
	public abstract boolean isInitialized(int mask);
	public abstract void setInitialized(short mask, float value);
	public abstract String toCSV();
	public String toString(){
		return getCodeline().toString();
	}
	public abstract float getExtrusionSpeed();
	public abstract float getTimeAccel();
	public abstract void setTimeAccel(float time);
	public abstract void setTime(float time);
	public abstract void setExtrusion(float extrusion);
	public abstract void setDistance(float distance);
	public abstract void setCurrentPosition(Position currentPosition);
	public abstract boolean isExtruding();
	public abstract boolean isExtrudeOrRetract();
	public abstract float getZ();
	public abstract float getY();
	public abstract float getR();
	public abstract float getKz();
	public abstract float getJy();
	public abstract float getIx();
	public abstract float getX();
	public abstract String getUnit();
	public abstract float getTime();
	//protected abstract String getIfInit(String prefix, float val, int digits, int mask);
	public abstract float getSpeed();
	public abstract Position getPosition(Position reference);
	public abstract float getS_Fan();
	public abstract float getF();
	public abstract float getExtrusion();
	public abstract float getS_Ext();
	public abstract float getE();
	public abstract float getDistance();
	public abstract Position getCurrentPosition(Position pos);
	public abstract float getS_Bed();
	public abstract float getBedtemp();
	public abstract float getExtemp();
	public abstract void setFanspeed(float fanspeed);
	public abstract short getFanspeed();

}
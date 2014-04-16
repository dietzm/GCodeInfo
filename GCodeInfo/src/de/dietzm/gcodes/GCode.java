package de.dietzm.gcodes;

import de.dietzm.Constants;
import de.dietzm.Position;

public interface GCode {

	public String getComment();

	/**
	 * Is printable, returns false for comments only and unknown GCODES
	 * 
	 * @return
	 */
	public boolean isComment();

	

	public void setUnit(String unit);

	/**
	 * Is printable, returns false for comments only and unknown GCODES
	 * 
	 * @return
	 */
	public boolean isPrintable();

	public MemoryEfficientString getCodeline();

	/**
	 * get codeline into the provided buffer. Save memory allocations
	 * 
	 * @param buffer
	 * @return len, how much data is written to buffer,-1 if buffer is too small
	 */
	public int getCodeline(byte[] buffer);



	public Constants.GCDEF getGcode();

	/**
	 * Is it a gcode which is buffered by the firmware
	 * 
	 * @return boolean true if buffered gcodes
	 */
	public boolean isBuffered();

	/**
	 * Is it a gcode which is long running and should not cause a timeout
	 * 
	 * @return boolean true if buffered gcodes
	 */
	public boolean isLongRunning();

	public boolean isInitialized(int mask);

	public void setInitialized(short mask, float value);

	public String toCSV();

	public String toString();

	public float getExtrusionSpeed();

	public float getTimeAccel();

	public void setTimeAccel(float time);

	public void setTime(float time);

	public void setExtrusion(float extrusion);

	public void setDistance(float distance);

	public void setCurrentPosition(Position currentPosition);

	public boolean isExtruding();

	public boolean isExtrudeOrRetract();

	public float getZ();

	public float getY();

	public float getR();

	public float getKz();

	public float getJy();

	public float getIx();

	public float getX();

	public String getUnit();

	public float getTime();

	// protected String getIfInit(String prefix, float val, int digits, int
	// mask);
	public float getSpeed();

	public Position getPosition(Position reference);

	public float getS_Fan();

	public float getF();

	public float getExtrusion();

	public float getS_Ext();

	public float getE();

	public float getDistance();

	public Position getCurrentPosition(Position pos);

	public float getS_Bed();

	public float getBedtemp();

	public float getExtemp();

	public void setFanspeed(float fanspeed);

	public short getFanspeed();

}
package de.dietzm.print;

import java.io.InputStream;

import de.dietzm.gcodes.GCode;

public interface Printer {
	
	public boolean addToPrintQueue(GCode code,boolean manual);
	public void setPrintMode(boolean isprinting, boolean interrupt);
	
	public boolean isPrinting();
	public boolean isPause();
	public GCode getCurrentGCode();
	public int getCurrentLine();
	public int getPrintSpeed();
	
	
}

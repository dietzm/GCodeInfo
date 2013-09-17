package de.dietzm.gcodesim;

import java.io.InputStream;

import de.dietzm.GCode;

public interface Printer {
	
	public boolean addToPrintQueue(GCode code,boolean manual);
	public void setPrintMode(boolean isprinting);
	
	public boolean isPrinting();
	public boolean isPause();
	public GCode getCurrentGCode();
	
	
}

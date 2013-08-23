package de.dietzm.gcodesim;

import de.dietzm.GCode;

public interface Printer {
	
	public boolean addToPrintQueue(GCode code,boolean manual);
	public void setPrintMode(boolean isprinting);
	
}

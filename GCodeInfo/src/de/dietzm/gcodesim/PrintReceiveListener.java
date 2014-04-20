package de.dietzm.gcodesim;

import java.io.InputStream;

public interface PrintReceiveListener {
	public boolean printreceived(String msg,InputStream in,boolean autostart,boolean savemodel,int filesize);
	public boolean printrecv_executeGcode(String Gcode);
	public boolean printrecv_setconnected(boolean connected);
	public boolean printrecv_stopprint();
}

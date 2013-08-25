package de.dietzm.gcodesim;

import java.io.InputStream;

public interface PrintReceiveListener {
	public boolean printreceived(String msg,InputStream in);
}

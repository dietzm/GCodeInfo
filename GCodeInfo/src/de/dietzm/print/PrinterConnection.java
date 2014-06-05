package de.dietzm.print;


public interface PrinterConnection {
	
	public final static int BLUETOOTH=1;
	public final static int USBOTG=0;
	public final static int DUMMY=2;

	public abstract void reset() throws Exception;
	
	/**
	 * 1st time initialize the connection
	 * @throws Exception
	 */
	public abstract boolean init(boolean reset) throws Exception;

	public abstract boolean enumerate();
	
	public abstract void requestDevice(String device);

	public abstract void closeDevice() throws Exception;
	
	public abstract void  writeBuffer(ReceiveBuffer wbuf);
	
	public abstract void read(ReceiveBuffer rbuf, int timeout);
	
	public int getType();
	
	public int tryrecover();

}
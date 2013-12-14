package de.dietzm;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeMemSave;
import de.dietzm.print.Printer;

public class SerialIO implements SerialPortEventListener, Printer{

	
	States state = new States();
	
	
	public class States{
		public boolean connected=false;
		public boolean fan=false;
		public boolean pause=false;
		public boolean debug=false;
		public float extemp=0;
		public float bedtemp=0;
		public float distance=1;
		public boolean printing=false;
		public float[] lastpos = new float[]{0f,0f,0f};
		public float lastE=0;
		public int baud=115200;
//		public boolean absolute
		
		public float getX(){
			return lastpos[0];
		}
		public float getY(){
			return lastpos[1];
		}
		public float getZ(){
			return lastpos[2];
		}
	}
	
	@Override
	public void setPrintMode(boolean isprinting) {
		// TODO Auto-generated method stub
		
	}
	public boolean isPrinting(){
		return state.printing;
	}
	public boolean isPause(){
		return state.pause;
	}
	public int getLastPrintedLine(){
		return 0;
	}
	
	public GCode getCurrentGCode(){
		return null;
	}


	InputStream inputStream =null;
	OutputStream outputStream = null;
	StringBuffer result = new StringBuffer();
	LinkedBlockingQueue<GCode> printQueue = new LinkedBlockingQueue<GCode>(1);
	
	public SerialIO() throws Exception{
		
	}
	

	
	public boolean addToPrintQueue(GCode code,boolean manual){
		if(!state.connected){
		//	cons.appendText("Not connected");
			try {
				Thread.sleep(500); //test only
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
		}
		if(manual && state.printing){
		//	cons.appendText("Stop printing first");
			return false;
		}
		if(!manual && !state.printing){
		//	cons.appendText("Printing stopped");
			return false;
		}
		
		System.out.println("About to add code "+code.hashCode()+" to print queue:"+printQueue.size());
		try {
			printQueue.put(code);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	private synchronized String printAndWaitQueue() throws Exception {
		GCode code = printQueue.take();
		if(code==null) return null;
		long starttime = System.currentTimeMillis();
		System.out.println("Print gcode "+code.getCodeline() +" Hash:"+code.hashCode());
		outputStream.write((code.getCodeline()).getBytes());
		while (true) {
			result.setLength(0);
			wait(10000);
			String tempresult=result.toString().trim();
			if (tempresult.length() == 0) {
				return null; // timeout
			}
			if (tempresult.trim().endsWith("ok")) {
				long endtime = System.currentTimeMillis();
				System.out.println("GCode "+code.hashCode()+" Measured Time: "+(endtime-starttime)+"ms");
				System.out.println("GCode "+code.hashCode()+" CTime: "+code.getTimeAccel()+"s");
				System.out.println("-----------------------------------------------------");
				return tempresult.trim();
			}
			System.out.println("Result "+code.hashCode()+" :"+tempresult);
			// Wait longer for the final result
		}

	}
	@Override
	public void serialEvent(SerialPortEvent arg0)  {
	//	System.out.println("Serial Event."+arg0.getEventType());
		if(arg0.getEventType() == SerialPortEvent.DATA_AVAILABLE){

			synchronized(this){
					byte[] a = new byte[1024];
					int len=0;
					try {
						len = inputStream.read(a);
					} catch (IOException e) {
						e.printStackTrace();
					}
					result.append(new String(a,0,len));
					System.out.println("Data Received:"+result.toString().trim());
					
					//wait for full lines before notifying
					if(result.toString().endsWith("\n")){
						notify();
					}else{
						System.out.println("Incomplete response, wait for more");
					}
				this.notify();
			}		
		}
		if(arg0.getEventType() == SerialPortEvent.OUTPUT_BUFFER_EMPTY){
//			try {
//			//	print("M114\n");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
		}
		
		
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		SerialIO sio=new SerialIO();
		sio.connect("/dev/ttyUSB0");
		while(true){
			sio.addToPrintQueue(new GCodeMemSave("M114\n", 1,null),true);
			Thread.sleep(1000);
		}
		
	}

	
	public void connect(String port) throws NoSuchPortException{
		CommPortIdentifier cp = CommPortIdentifier.getPortIdentifier(port);
		SerialPort serialPort=null;

		  try {
			  serialPort = 
	                (SerialPort) cp.open("GCodeSimulator", 2000);
	            } catch (PortInUseException e) {
	            System.out.println("Port in use.");
	            } 
		  
		  try {
	         outputStream=   serialPort.getOutputStream();
	         inputStream= serialPort.getInputStream();      
		  } catch (IOException e) {
			  System.out.println("Port err."+e);			  
		  }
	 
	            try {
	            serialPort.setSerialPortParams(115200, 
	                               SerialPort.DATABITS_8, 
	                               SerialPort.STOPBITS_1, 
	                               SerialPort.PARITY_NONE);
	            } catch (UnsupportedCommOperationException e) {
	            	System.out.println("Port err."+e);		
	            }
	            
	            
	            try {
	                serialPort.notifyOnDataAvailable(true);
	                serialPort.notifyOnBreakInterrupt(true);
	                serialPort.notifyOnFramingError(true);
	                serialPort.notifyOnOverrunError(true);
	                serialPort.notifyOnParityError(true);
	              //  serialPort.notifyOnOutputEmpty(true);
	                serialPort.addEventListener(this);
	            } catch (Exception e) {
	            System.out.println("Error setting event notification");
	            System.out.println(e.toString());
	            System.exit(-1);
	            }
	            
	            System.out.println("Port successfull opened");

	           
	}
}

package de.dietzm.print;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

public class RXTXPrint implements PrinterConnection,SerialPortEventListener {

	InputStream inputStream =null;
	OutputStream outputStream = null;
	SerialPort serialPort=null;
	StringBuffer result = new StringBuffer();
	ConsoleIf cons;
	SerialPrinter sio;
	private final ByteBuffer mReadBuffer = ByteBuffer.allocate(4096);
	private final ReceiveBuffer mReadLineBuffer = new ReceiveBuffer(4096);
	private static int READ_SIZE = 64;
	
	@Override
	public void serialEvent(SerialPortEvent arg0) {
//		
//		if(arg0.getEventType() == SerialPortEvent.DATA_AVAILABLE){
//
//			synchronized(this){
//					cons.appendText("ReadEvent");
//					byte[] a = new byte[1024];
//					int len=0;
//					try {
//						len = inputStream.read(a);
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
//					result.append(new String(a,0,len));
//					System.out.println("Data Received:"+result.toString().trim());
//					
//					//wait for full lines before notifying
//					if(result.toString().endsWith("\n")){
//						notify();
//					}else{
//						System.out.println("Incomplete response, wait for more");
//					}
//				this.notify();
//			}		
//		}
		if(arg0.getEventType() == SerialPortEvent.OUTPUT_BUFFER_EMPTY){
//			try {
//			//	print("M114\n");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
		}
		
		
	}

	public RXTXPrint(SerialPrinter sio,ConsoleIf cons) {
	this.cons=cons;
	this.sio=sio;
	}

	@Override
	public void reset() throws Exception {
		cons.appendText("Reset");
		// TODO Auto-generated method stub

	}

	@Override
	public boolean init(boolean reset) throws Exception {
		// TODO Auto-generated method stub
		cons.appendText("Init");
		return true;
	}

	@Override
	public boolean enumerate() {
		Enumeration en = CommPortIdentifier.getPortIdentifiers();
		ArrayList<String> names = new ArrayList<String>();
		while (en.hasMoreElements()) {
			CommPortIdentifier object = (CommPortIdentifier) en.nextElement();
			System.out.println("Port:"+object.getName());
			names.add(object.getName());
		}
		String[] namesa = names.toArray(new String[0]);
		cons.chooseDialog(namesa, namesa, 1);
		return true;
	}

	@Override
	public void requestDevice(String device) {
		 System.out.println("Port connect:"+device);	
		 cons.appendText("Open Port:"+device);
		  try {
			  CommPortIdentifier cp = CommPortIdentifier.getPortIdentifier(device);
				
			  serialPort = 
	                (SerialPort) cp.open("GCodeSimulator", 2000);
	           
			  serialPort.enableReceiveTimeout(100);
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
		  } catch (Exception e) {
	            System.out.println("Port in use."+e);
	            cons.appendText("Error connecting"+e);
	            return;
	            }   
	       System.out.println("Port successfull opened");
			cons.appendText("Waiting for printer response");
			sio.startRunnerThread();

	}

	@Override
	public void closeDevice() throws Exception {
		serialPort.close();

	}

	@Override
	public void writeBuffer(ReceiveBuffer wbuf) {
		//cons.appendText("Write:");
		try {
			outputStream.write(wbuf.array,0,wbuf.length());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void read(ReceiveBuffer rbuf,int timeout) {
		//cons.appendText("Read:");
		try {
			long time = System.currentTimeMillis();
			while ((System.currentTimeMillis() - time) < timeout && !sio.state.reset) {
				sio.state.readcalls++;
				int len = inputStream.read(mReadBuffer.array(), 0,READ_SIZE);
				if (len > 0) {
					mReadBuffer.position(len);
					rbuf.append(mReadBuffer);
					// Log.d(serial,"RECV:"+new String(mReadBuffer.array(),0,len));
					mReadBuffer.clear();
					return;
				}
			}
		} catch (Exception e) {
			sio.onRunError(e, "OTG read");
		}

	}

	@Override
	public int getType() {
		// TODO Auto-generated method stub
		return 0;
	}



}

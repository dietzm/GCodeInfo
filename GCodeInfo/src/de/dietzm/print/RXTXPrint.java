package de.dietzm.print;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RXTXPrint implements PrinterConnection,SerialPortEventListener {

	InputStream inputStream =null;
	OutputStream outputStream = null;
	StringBuffer result = new StringBuffer();
	
	@Override
	public void serialEvent(SerialPortEvent arg0) {
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

	public RXTXPrint() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void reset() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean init(boolean reset) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean enumerate() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void requestDevice(String device) {
		

		  try {
			  CommPortIdentifier cp = CommPortIdentifier.getPortIdentifier(device);
				SerialPort serialPort=null;
			  serialPort = 
	                (SerialPort) cp.open("GCodeSimulator", 2000);
	           
		  
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
	            System.out.println("Port in use.");
	            }   
	       System.out.println("Port successfull opened");


	}

	@Override
	public void closeDevice() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void writeBuffer(ReceiveBuffer wbuf) {
		try {
			outputStream.write(wbuf.array);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void read(ReceiveBuffer rbuf) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getType() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int tryrecover() {
		// TODO Auto-generated method stub
		return 0;
	}

}

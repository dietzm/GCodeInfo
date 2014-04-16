package de.dietzm.print;

import de.dietzm.Constants.GCDEF;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.MemoryEfficientString;

public class Dummy implements PrinterConnection {
	
	boolean isM105 =false;
	boolean isReset =false;
	boolean isSend =false;
	boolean odd = false;
	byte[] memT = new MemoryEfficientString("ok T:179.2 /0.0 B:50.6 /0.0 T0:14.9 /0.0 @:0 B@:0\n").getBytes();
	byte[] memT1 = new MemoryEfficientString("ok T:181.2 /0.0 B:48.6 /0.0 T0:10.9 /0.0 @:0 B@:0\n").getBytes();

	byte[] memS = new MemoryEfficientString("start grbl\n").getBytes();
	private final ReceiveBuffer sendbuf = new ReceiveBuffer(4096);
	SerialPrinter sio;
	ConsoleIf cons;
	GCode[] buffer = new GCode[16];
	int buffercnt=0;
	int bufferpos=0;
	int sdcnt = 0;

	
	public Dummy(SerialPrinter sio,ConsoleIf cons) {
		this.sio=sio;
		this.cons=cons;
	}
	
	@Override
	public void reset() throws Exception {
		isReset=true;
		isSend=true;
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public boolean init(boolean reset) throws Exception {
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		cons.appendText("start");
		cons.appendText("Dummy Marlin V1.00");
		
		cons.appendText("Successfully connected !");
		return true;
	}
	
	public int tryrecover(){
		return 0;
	}
	
	@Override
	public boolean enumerate() {
		//requestDevice("TEST");
		cons.chooseDialog(new String[]{"USB1","USB2"}, new String[]{"USB1","USB2"},1);
		return true;
	}

	@Override
	public void requestDevice(String device) {
		cons.appendText("Waiting for printer response");
		sio.startRunnerThread();
		
	}

	@Override
	public void closeDevice() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void writeBuffer(ReceiveBuffer wbuf) {
		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
		}
		if(wbuf.length() >=4 && wbuf.charAt(0)=='M' && wbuf.charAt(1)=='1' &&wbuf.charAt(2)=='0' &&wbuf.charAt(3)=='5'   ){
			isM105=true;
		}
		sendbuf.put(wbuf.array,0,wbuf.length());
		isSend=true;
		buffer[bufferpos++] = sio.state.lastgcode;
		buffercnt++;
	}

	@Override
	public void read(ReceiveBuffer rbuf) {
		try {
			if(buffercnt == buffer.length){
				bufferpos=0;
				Thread.sleep((long)(buffer[bufferpos].getTimeAccel()*1000));
				buffercnt--;
			}
			//System.out.println(sio.state.lastgcode.getTimeAccel()*1000+"Sec");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(!isSend) return;
		isSend=false;
		//TODO state.readcalls++;
		if(isM105){
			if(odd){
				System.arraycopy(memT, 0, rbuf.array, 0, memT.length);
				rbuf.setlength(memT.length);
			}else{
				System.arraycopy(memT1, 0, rbuf.array, 0, memT1.length);
				rbuf.setlength(memT1.length);
			}
			odd = !odd;
			isM105=false;
		}else if(sendbuf.startsWithString("M20")){
			byte[] sd1 = new MemoryEfficientString("begin\nfile1.gco\n/folder/file2.gco\nfile3.gco\n/folder/FILE.gco\nend\nok\n").getBytes();
			System.arraycopy(sd1, 0, rbuf.array, 0, sd1.length);
			rbuf.setlength(sd1.length);
		}else if(sendbuf.startsWithString("M27")){
			sdcnt++;
			if(sdcnt<10){
				byte[] sd1 = new MemoryEfficientString("SD printing byte "+(sdcnt*100)+"/1000\nok\n").getBytes();
				System.arraycopy(sd1, 0, rbuf.array, 0, sd1.length);
				rbuf.setlength(sd1.length);
			}else{
				byte[] sd1 = new MemoryEfficientString("SD printing byte 1000/1000\nok\nPrinting done\nok\n").getBytes();
				System.arraycopy(sd1, 0, rbuf.array, 0, sd1.length);
				rbuf.setlength(sd1.length);
				if(sdcnt>15) sdcnt=0;
			}
			
		}else if(isReset){
			System.arraycopy(memS, 0, rbuf.array, 0, memS.length);
			rbuf.setlength(memS.length);
			isReset=false;
		}else{
			rbuf.array[0]='o';
			rbuf.array[1]='k';
			rbuf.array[2]='\n';
			rbuf.setlength(3);
		}
		
	}

	public ReceiveBuffer read(boolean clear) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getType() {
		// TODO Auto-generated method stub
		return 0;
	}
	
}
package de.dietzm.gcodesim;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class NetworkPrintReceiver implements Runnable{
	ServerSocket sock =null;
	GcodePainter gp=null;
	
	public NetworkPrintReceiver(GcodePainter gp) throws IOException {
		this.gp=gp;
		sock = new ServerSocket(53232);
		new Thread(this).start();
	}
	
	public Socket waitForData() throws Exception{
		Socket s = sock.accept();
		return s;
	}
	
	public void run(){
		try {
			while(true){
				Socket sin = waitForData();
				gp.start("GCode from "+sin.getInetAddress(), sin.getInputStream());
				sin.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

}

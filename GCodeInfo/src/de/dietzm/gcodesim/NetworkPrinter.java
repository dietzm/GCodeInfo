package de.dietzm.gcodesim;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import de.dietzm.GCode;
import de.dietzm.Model;

public class NetworkPrinter implements Runnable{
	ServerSocket sock =null;
	PrintReceiveListener gp=null;
	Thread th;
	public static final int PORT = 53232;
	
	public NetworkPrinter() {
		
	}
		
	public void startPrintReceiver(PrintReceiveListener gp)throws IOException {
		this.gp=gp;
		sock = new ServerSocket(PORT);
		th= new Thread(this);
		th.start();
	}
	
	public void sendToReceiver(String ip,Model mod)throws IOException{
		Socket cs = new Socket(ip,PORT);
		OutputStream out = cs.getOutputStream();
		BufferedOutputStream bufout = new BufferedOutputStream(out);
		ArrayList<GCode> gcodes = mod.getGcodes();
		for (GCode gCode : gcodes) {
			bufout.write((gCode.getCodeline()+"\n").getBytes());
		}
		bufout.flush();
		cs.close();		
	}
	
	private Socket waitForData() throws Exception{
		Socket s = sock.accept();
		return s;
	}
	
	public void run(){
		try {
			while(!Thread.interrupted()){
				Socket sin = waitForData();
				gp.printreceived("GCode from "+sin.getInetAddress(), sin.getInputStream());
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
	
	public void stopPrintReceiver(){
		try {
			th.interrupt();
			sock.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

}

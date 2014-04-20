package de.dietzm.gcodesim;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import de.dietzm.Constants;
import de.dietzm.Model;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeStore;

public class NetworkPrinter implements Runnable {
	ServerSocket sock = null;
	PrintReceiveListener gp = null;
	Thread th;
	public static final int PORT = 53232;
	public static int DEFAULT = 0;
	public static int RECEIVE_GCODE = 1; // if not specified, only a single gcode is executed
	public static int FILENAME_SPECIFIED = 2;
	public static int FILESIZE_SPECIFIED = 4;
	public static int AUTOSTART_PRINT = 8;
	public static int AUTOSAVE_MODEL = 16;
	// Direct actions, are executed before the gcode is received or executed
	public static int CONNECT = 32;
	public static int DISCONNECT = 64;
	public static int STOP_PRINT = 128;

	public NetworkPrinter() {

	}

	public void startPrintReceiver(PrintReceiveListener gp) throws IOException {
		this.gp = gp;
		sock = new ServerSocket(PORT);
		th = new Thread(this);
		th.setName("NetworkListener");
		th.start();
	}

	public void sendToReceiver(String ip, Model mod , int flags) throws IOException {
		Socket cs = new Socket(ip, PORT);
		OutputStream out = cs.getOutputStream();
		BufferedOutputStream bufout = new BufferedOutputStream(out, 32768);
		byte[] magic = {0x3B,(byte)0xC0,(byte)0xDE};
		bufout.write(magic);
		bufout.write((byte)(RECEIVE_GCODE | FILENAME_SPECIFIED | flags));
		
		//send filename
		String filename = new File(mod.getFilename()).getName();
		byte[] filebyte = filename.getBytes();
		byte flen = (byte)Math.min(filebyte.length, 255);
		bufout.write(flen);
		bufout.write(filebyte,0,flen);
		
		//Final newline before send
		bufout.write(Constants.newline);
		
		byte[] transBuf = new byte[4096];
		int len = 0;
		GCodeStore gcodes = mod.getGcodes();
		for (GCode gCode : gcodes) {
		//	try{
			len = gCode.getCodeline(transBuf);
			bufout.write(transBuf, 0, len);
//			}catch(Exception e){
//				System.out.println("send failed for gCode line:"+idx);
//			}
			// System.out.println("Send:"+new String(transBuf,0,len));
		}
		bufout.flush();
		cs.close();
	}

	private Socket waitForData() throws Exception {
		Socket s = sock.accept();
		return s;
	}

	public void run() {
		
			while (!Thread.interrupted()) {
				try {
				Socket sin = waitForData();
				String filename = sin.getInetAddress().getHostAddress() + "_" + new SimpleDateFormat("yyMMdd_HHmmss").format(new Date()) + ".gcode";
				InputStream in = sin.getInputStream();
				BufferedInputStream bufin = new BufferedInputStream(in);
				bufin.mark(4);
				byte[] magic = new byte[3];
				int len = bufin.read(magic);
				// Magic is 0x3B 0xC0 0xDE - (3B=; to be handled as comment by
				// older versions)
				if (len == 3 && magic[0] == (byte)0x3B && magic[1] == (byte)0xC0 && magic[2] == (byte)0xDE) {
					parseCommandHeader(filename, bufin);
				} else {
					bufin.reset(); //3bytes are not the magic.. reset to pos 0
					gp.printreceived(filename, bufin, false, false,0);
				}
				sin.close();
		
				} catch (IOException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
	}

	private void parseCommandHeader(String filename, BufferedInputStream bufin) throws IOException {
		// Magic detected
		System.out.println("NetworkReceiver MAGIC detected");
		int flags = bufin.read();

		if ((flags & DISCONNECT) != 0) {
			gp.printrecv_setconnected(false);
		}
		if ((flags & CONNECT) != 0) {
			gp.printrecv_setconnected(true);
		}
		if ((flags & STOP_PRINT) != 0) {
			gp.printrecv_stopprint();
		}

		// Receive gcode file
		if ((flags & RECEIVE_GCODE) != 0) {
			boolean autostart = false;
			boolean savefile = false;
			int filesize = 0;

			if ((flags & FILENAME_SPECIFIED) != 0) {
				// get filename Up to 255 chars
				int namelen= bufin.read();
				byte[] name= new byte[namelen];
				int rlen = bufin.read(name);
				filename=new String(name,0,rlen);
			}
			if ((flags & FILESIZE_SPECIFIED) != 0) {
				// get filesize
				byte[] size= new byte[4];
				int rlen = bufin.read(size);
				if(rlen==4){
					filesize=Constants.bytetoInt(size);
				}							
			}
			if ((flags & AUTOSTART_PRINT) != 0) {
				autostart = true;
				System.out.println("netrec: autostart");
			}
			if ((flags & AUTOSAVE_MODEL) != 0) {
				savefile = true;
				System.out.println("netrec: autosave");
			}
		
			int finish = bufin.read();
			if(finish != 0x13){
				System.out.println("Error: wrong finish byte received");
			}
			gp.printreceived(filename, bufin, autostart, savefile, filesize);
			
		//execute gcode file
		}else{
			byte[] gcode = new byte[256]; // max 256
			int len1 = bufin.read(gcode);
			if(len1 > 0){
				String gc = new String(gcode, 0, len1);
				gp.printrecv_executeGcode(gc);
			}
			// TODO send response
		}
	}

	public void stopPrintReceiver() {
		try {
			th.interrupt();
			sock.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

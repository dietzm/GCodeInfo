package de.dietzm.print;


import java.util.ArrayList;
import java.util.Date;

import de.dietzm.Constants;
import de.dietzm.Constants.GCDEF;
import de.dietzm.Model;
import de.dietzm.SerialIO.States;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeFactory;
import de.dietzm.gcodes.MemoryEfficientLenString;
import de.dietzm.gcodes.MemoryEfficientString;


public class SerialPrinter implements Runnable, Printer {

	public static final GCode G0 = GCodeFactory.getGCode("G0", 0);
	public static final GCode M105 = GCodeFactory.getGCode("M105", -105);
	public static final GCode M20 = GCodeFactory.getGCode("M20", -20);
	public static final GCode M27 = GCodeFactory.getGCode("M27", -20);
	public static final String serial = "SERIAL"; //log tag
	public static final String io = "IO"; //log tag	
	
	private ConsoleIf cons = null;
	private PrinterConnection mConn = null;
	private final PrintQueue printQueue = new PrintQueue();;
	private final ReceiveBuffer ioBuffer = new ReceiveBuffer(4096);
	private StringBuffer sdfiles = new StringBuffer();
	
	private Thread runner = null;
	private long printstart; //time when print started
	private long sendtime = 0;
	private long starttime = 0;
	private long garbagetime = 0;
	
	private long lastTempWatch = 0; //time when last tempwatch happened
	private float testrunavg = 0;
	private int tempwatchintervall = 10000;
	private boolean resetoninit=true;
	private boolean logerrors=true;
	private boolean recover = false;
	public boolean isResetoninit() {
		return resetoninit;
	}

	public void setResetoninit(boolean resetoninit) {
		this.resetoninit = resetoninit;
	}

	private int movespeed = 3000;
	int timeout = 10000;
	
	
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public void setMovespeed(int movespeed) {
		this.movespeed = movespeed;
	}

	// Print status & Control
	public States state = new States(); //TODO not public

	public static enum Axis {
		E, X, Y, Z
	}

	public class States {
		
		public static final String CONNECTED="Connected";
		public static final String FINISHED="Print Finshed";
		public static final String DISCONNECTED="Disconnected";
		public static final String CONNECTING="Connecting";
		public static final String PRINTING="Printing";
		public static final String STREAMING="Streaming to SD";
		public static final String PAUSED="Printing Paused";
		public static final String CONNECTMSG = "Please connect";
		public static final String CONNECTINGMSG ="Establishing printer connection";
		public static final String CONNECTEDMSG ="Ready to start printing";
		public int baud = 115200;
		public float bedtemp = 0;
		public boolean connected = false;
		public boolean connecting = false;
		public boolean debug = false;
		public float distance = 1;
		public float extemp = 0;
		public boolean fan = false;
		public float lastE = 0;
		public GCode lastgcode = G0;
		public float[] lastpos = new float[] { 0f, 0f, 0f };
		public boolean pause = false;
		public boolean printing = false;
		public boolean streaming = false;
		public boolean sdprint = false;
		public boolean reset = false;
		public int printspeed = 100;
		public int timeouts=0;
		public int timeoutline=0;
		public int unexpected=0;
		public int swallows=0;
		public int readcalls=0;
		public String serialtype="";
		public int percentCompleted=0;
		public MemoryEfficientString tempstring = new MemoryEfficientString(new byte[64]);
		public MemoryEfficientLenString timestring = new MemoryEfficientLenString(new byte[32]);
		// public boolean absolute
		boolean testrun = false;

		public float getX() {
			return lastpos[0];
		}

		public float getY() {
			return lastpos[1];
		}

		public float getZ() {
			return lastpos[2];
		}
	}

	public SerialPrinter( ConsoleIf console) {
		this.cons = console;
		cons.appendText("Not connected. Press connect button to establish printer connection.");
	}
	
	public void setConsole(ConsoleIf conso){
		this.cons=conso;
	}

	/**
	 * Add a gcode to the print queue for async execution
	 * 
	 * @param code
	 * @param allow during manual operation only
	 * @return true if the command has been added to the queue successfully
	 */
	public boolean addToPrintQueue(GCode code, boolean manualOnly) {
		if (!code.isPrintable()) {
			// Just skip
			return true;
		}
		if (state.connecting) {
			cons.appendText("Still connecting. Please wait until connecting is established.");
			return false;
		}
		if (!state.connected) {
			cons.appendText("Not connected");
			return false;
		}
		if (state.reset) {
			cons.appendText("Still resetting....");
			return false;
		}
		if (manualOnly && state.printing && !state.pause) {
			cons.appendText("Stop or Pause printing first");
			return false;
		}
		try {
			printQueue.put(code);
			if (state.debug)
				cons.appendTextNoCR("Queued -> " + code.getCodeline());
		} catch (InterruptedException e) {
			// Command add has been interrupted
			// cons.log(serial,"printQueue.put has been interruped:"+e);
			if (state.debug)
				cons.appendText("printQueue.put has been interruped");
			return false;
		}

		return true;
	}

	public boolean connect(PrinterConnection type, int baud) {
		state.connecting=true;
		cons.updateState(States.CONNECTING,States.CONNECTINGMSG,0);
		state.baud=baud;
		mConn=type;
		boolean succ = mConn.enumerate();
		if(!succ){ 
			state.connecting=false;		
			state.connected=false;
			cons.updateState(States.DISCONNECTED,States.CONNECTMSG,0);
		}
		return succ;
	}

	public void connectTo(String device) {
		mConn.requestDevice(device);
	}
	
	public void startRunnerThread(){
		if (runner == null || !runner.isAlive() || recover) {
			runner = new Thread(this);
			runner.start();
		}
	}

	public void disconnect() {
		if (isConnected()) {
			cons.appendText("Closing connection !");
			if (state.printing) {
				state.printing = false;
			}
			try {
				state.connected = false;
				state.connecting = false;
				state.reset = false;
				mConn.closeDevice();
				if (runner != null && runner != Thread.currentThread())
					runner.interrupt();
			} catch (Exception e) {
				e.printStackTrace();
				if (runner != null && runner != Thread.currentThread())
					runner.interrupt();
			}
			runner = null;
		}
		cons.updateState(States.DISCONNECTED,States.CONNECTMSG,0);
	}

	
	private void doInit() {
		if (state.printing)	setPrintMode(false);
		printQueue.clear();
		state.lastE = 0;
		state.lastpos = new float[3];
		try{
			if(mConn.init(resetoninit)){
				state.connected=true;
				state.connecting=false;
				cons.updateState(States.CONNECTED,States.CONNECTEDMSG,0);
			}
		} catch (Exception e) {
			if(state.debug) cons.appendText("Init failed:" + e.getMessage());
			e.printStackTrace();
			disconnect();
		}		
	}
	
	private void doReset() {
		if (state.connected) {
			try {
				if (state.printing) setPrintMode(false);
				printQueue.clear();
				mConn.reset();
				ReceiveBuffer recv = readResponse(10000);
				if (recv.isEmpty()) {
					cons.appendText("No printer response after reset ! Your hardware might not support reset over serial.");
				} else {
					cons.appendTextNoCR(recv);
				}
				state.lastE = 0;
				state.lastpos = new float[3];
				state.swallows=0;
				state.unexpected=0;
				state.timeouts=0;
			} catch (Exception e) {
				if (state.debug)
					cons.appendText("Reset failed or interrupted:" + e);
				e.printStackTrace();
			}
			state.reset = false;
		}
	}

	private void doTestRun() throws InterruptedException {
	//	printQueue.putAuto(GCodeFactory.getGCode("G90", 0)); // absolute
		Model mod = new Model("testrun");
		ArrayList<GCode> arr = mod.getGcodes();
		arr.add(GCodeFactory.getGCode("G90", 0));
		for (int i = 0; i < 5000; i++) {
			GCode gco = GCodeFactory.getGCode("G1 X10 Y10", i);
			//printQueue.putAuto(gco);
			arr.add(gco);
		}
		arr.add(GCodeFactory.getGCode("M114", 5002));
		mod.analyze();
		printQueue.addModel(mod);
		
		//printQueue.putAuto(GCodeFactory.getGCode("M114", 5002));
		setPrintMode(true);
	}

	public GCode getCurrentGCode() {
		return state.lastgcode;
	}
	
	public CharSequence getRemainingtime() {
		int	len = Constants.formatTimetoHHMMSS(printQueue.getRemainingtime(),state.timestring.getBytes());
		state.timestring.setlength(len-3); //Cut off seconds
		return state.timestring;
	}

	/**
	 * Home axis. IF axis is null, home all
	 * @param move
	 */
	public void homeAxis(Axis move) {
		String code;
		if(move == null){
			cons.appendText("Home all axis");
			code = "G28";
		}else{
			cons.appendText("Home " + move.toString() + " axis");
			code = "G28 " + move.toString();
		}
		addToPrintQueue(GCodeFactory.getGCode(code, 1), true);
	}

	public boolean isPause() {
		return state.pause;
	}
	
	public boolean isDebug() {
		return state.debug;
	}

	/**
	 * Is printing 
	 * @return boolean true if printing
	 */
	public boolean isPrinting() {
		return state.printing;
	}
	
	/**
	 * Is connected or about to connect (connecting)
	 * @return boolean connected || connecting
	 */
	public boolean isConnected() {
		return state.connected || state.connecting;
	}

	public void moveStep(Axis move, int sign) {
		moveStep(move, sign, movespeed);
	}
	
	public void listSDCard() {
		sdfiles.setLength(0);
		addToPrintQueue(GCodeFactory.getGCode("M21", -21), true);
		addToPrintQueue(M20, true);
	}

	public void moveStep(Axis move, int sign, int speed) {
		cons.appendText("Move " + move);
		String code = "G1 " + move.toString() + (state.distance * sign) + " F" + speed;

		boolean res = addToPrintQueue(GCodeFactory.getGCode("G91", 0), true); // relative
		if (res)
			res = addToPrintQueue(GCodeFactory.getGCode(code, 0), true);
		if (res)
			res = addToPrintQueue(GCodeFactory.getGCode("G90", 0), true); // absolute
		if (res)
			addToPrintQueue(GCodeFactory.getGCode("M114", 0), true);

	}

	public void moveToPos(int x, int y, int speed) {
		if(speed==0)speed=movespeed;
		String code = "G1 X" + x + " Y" + y + " F" + speed;

		boolean res = addToPrintQueue(GCodeFactory.getGCode("G90", 0), true); // relative
		if (res)
			addToPrintQueue(GCodeFactory.getGCode(code, 1), true);

	}


	public void moveToPos(int x, int y, int speed, float extrude) {

		String code = "G1 X" + x + " Y" + y + " F" + speed;
		if (x > 200 || x < 0 || y > 200 || y < 0) {
			cons.log("GCODE", "Invalid gcode " + code);
		}

		float xmove = Math.abs(state.lastpos[0] - x);
		float ymove = Math.abs(state.lastpos[1] - y);
		if (xmove + ymove == 0)
			return;
		state.lastpos[0] = x;
		state.lastpos[1] = y;
		float move = (float) Math.sqrt((xmove * xmove) + (ymove * ymove));

		if (state.lastE == 0) {
			addToPrintQueue(GCodeFactory.getGCode("G90", 0), true); // relative
			addToPrintQueue(GCodeFactory.getGCode("M82", 0), true); // relative
			addToPrintQueue(GCodeFactory.getGCode("G92 E0", 0), true); // relative
		}
		state.lastE = (state.lastE + (extrude * move));
		code = code + " E" + state.lastE;

		addToPrintQueue(GCodeFactory.getGCode(code, 1), true);

	}

	/**
	 * Poll from two queues 1) Auto when printing a full model, 2) manual for
	 * manual operations Send to serialIO and wait for response
	 * 
	 * @throws InterruptedException
	 */
	private void printAndWaitQueue() throws InterruptedException {
		GCode code = null;
		// do temp watch every 10 sec if busy
		if (System.currentTimeMillis() - lastTempWatch < tempwatchintervall || (!state.printing && !printQueue.isManualEmpty()) || state.streaming) {
			if (state.printing && !state.pause && printQueue.isManualEmpty()) {
				code = printQueue.pollAuto(); // poll for auto ops
				if (code == null){
					state.lastgcode = G0;
					if(state.percentCompleted >= 100){
						state.sdprint=false; //end printing
					}
					if(state.sdprint){
						code=M27; //get SD status
					}else{
						setPrintMode(false); // Finish printing
					}
				}else{
					state.lastgcode = code;// remember last code to sync with UI
				}
					
				if(logerrors && Constants.lastGarbage-garbagetime > 0){
					garbagetime=Constants.lastGarbage;
					cons.appendText("Garbage collector run during print !");
					cons.log("GC", String.valueOf(garbagetime));
				}
			}else{
				code = printQueue.pollManual(1); // poll for manual ops
			}
		} else {
			code = M105; // retrieve temp
			lastTempWatch = System.currentTimeMillis();
		}
		if (state.reset || code == null)
			return;

		starttime = System.currentTimeMillis();
		if (state.debug) {
			cons.appendTextNoCR(String.valueOf(code.getLineindex()),": ", code.getCodeline());
			if (code.isBuffered() && code.getTimeAccel() > 0.2) {
				cons.appendText("(Est.Time:", String.valueOf(Constants.round3digits(code.getTimeAccel())) , "s)");
			}
		}

		/*
		 * Write the gcode to the printer Measure time and lof
		 */
		if(state.debug){
			cons.log(serial, "write gcode line:"+code.getLineindex());
			cons.log(serial, code.getGcode().toString());
		}
		int len = code.getCodeline(ioBuffer.array); //Get codeline into buffer
		ioBuffer.setlength(len);					//adjust buffer len to what we got
		mConn.writeBuffer(ioBuffer);				//write buffer
		cons.log(io, null, ioBuffer); 				//log buffer to logfile
		sendtime = System.currentTimeMillis();
		state.readcalls=0; //reset readcalls var debug only

		/*
		 * Wait for the response
		 */
		while (state.connected && !state.reset) {
			if(mConn.getType() == PrinterConnection.BLUETOOTH)  Thread.sleep(5); //Response will take at least 5 ms, safe CPU cylces

			// receive updates wait 10min or 10sec
			ReceiveBuffer recv = readResponse(code.isLongRunning()?60000:timeout);
			cons.log(io, null, recv); 						//log buffer to logfile
			if (state.reset)
				break;

			if (state.debug && code.isBuffered() && (System.currentTimeMillis() - sendtime) > 400) {
				float resptime = Constants.round3digits(((float) (System.currentTimeMillis() - sendtime)) / 1000);
				cons.appendText("Wait for printer response: ", String.valueOf(resptime) , "s");
			}
			if (recv.isEmpty() || recv.isTimedout()) {
				state.timeouts++;
				state.timeoutline=code.getLineindex();
				if(state.debug || logerrors){
					//This can even happen in normal cases e.g. when the move is very long (1st in the buffer)
					String logm = "Timeout waiting for printer response at line #"+state.timeoutline+"("+ String.valueOf((System.currentTimeMillis() - starttime))+ "ms)";
					cons.appendText(logm);
					cons.log("ERROR", logm);
				}
				break; // timeout
			}

			if (state.debug || (!recv.isPlainOK() && code != M105) ) {
				// Suppress plain ok and temp when printing
				cons.appendTextNoCR(recv);
			}

			if(state.streaming && recv.containsFail()){
				//e.g. open failed when filename is wrong
				cons.appendText("Error during streaming, abort");
				setPrintMode(false);
			}
			/*
			 * Check if printer command is committed with "ok" make sure to
			 * update temperature in case of M105 command print out debug info
			 * in case of testruns
			 */
			if (recv.containsOK() || recv.containsWait()) {
				//cons.log(serial, "OK");
				if (code == M105 && recv.containsTx()) { // Parse temperature
					int idx1 = recv.indexOf('T');
					int idx = recv.indexOf('@');
					if(idx == -1 || idx1 ==-1){
						state.tempstring = recv.subSequence(3, recv.length(),state.tempstring);
					}else{
						state.tempstring = recv.subSequence(idx1, idx,state.tempstring);
					}
					cons.setTemp(state.tempstring);
				}else if (code == M105 && recv.isPlainOK()){
						state.swallows++;
					if(state.debug || logerrors){						
						cons.appendText("Swallow OK");
						cons.log("ERROR", "Swallow OK");
					}
					continue;
				}else if(code == M20){
					sdfiles.append(recv.toString());
					String[] fout = sdfiles.toString().toLowerCase().split("\n");
					if(fout.length > 3){
						//remove begin & end & ok
						String[] fnew = new String[fout.length-3];
						System.arraycopy(fout, 1, fnew, 0, fout.length-3);
						cons.chooseDialog(fnew,fnew,2);
					}else{
						cons.chooseDialog(null,null,2);
					}
						
					
				}else if(code == M27){
					state.percentCompleted=recv.parseSDStatus();
				}

				// if(state.debug){
				float diff = (System.currentTimeMillis() - starttime);
				if (state.testrun) {
					testrunavg = testrunavg + diff;
					if (diff > 50) {
						cons.log("GCodeLongTime", String.valueOf(code.getLineindex())); 
						cons.log("GCodeLongTime", String.valueOf(diff));
						cons.appendText("Took (overall ms): ", String.valueOf(diff));
					}
				}
				break;
			}
			//Check if temperature field needs to be updated (M109,m116,..)
			if (recv.containsTx()) { // Parse temperature
				state.tempstring = recv.subSequence(0, recv.length(), state.tempstring);
				cons.setTemp(state.tempstring);
			}
			
			if(code.isBuffered() && !recv.startsWithEcho() && !recv.startsWithGO()){ //For buffered commands we only expect ok
				state.unexpected++;
				cons.appendText("Unexpected response from printer: "+recv.toString());
				cons.log("ERROR", "Unexpected response from printer");
				//break; Do not break because e.g. makibox always returns with unexpected responses
			}
			
			if(code == M20){
				cons.log("serial", "Add file output:"+recv.toString());
				sdfiles.append(recv.toString());
			}
		}

//		int dtime = (int)(System.currentTimeMillis() - starttime);
//		bufferedGCodetime = Math.max(0,(bufferedGCodetime-dtime));
//		cons.log("TIME","BGTTime:"+bufferedGCodetime);
		//Update progress bar percentage. Only update if percent changes.
		if(isPrinting()){
			if(state.sdprint){
				cons.updateState(States.STREAMING, "unknown" ,state.percentCompleted );
			}else if(state.percentCompleted != printQueue.getPercentCompleted()){
				state.percentCompleted = printQueue.getPercentCompleted();
				if(state.streaming){
					cons.updateState(States.STREAMING, "unknown" ,state.percentCompleted );
				}else{
					cons.updateState(States.PRINTING, getRemainingtime() ,state.percentCompleted );
				}
			}
		}
		
		
	}

	/**
	 * Print 
	 * if sdfilename == null then print model
	 * if sdfilename is not null and model is not null, stream model to sd card on printer
	 * if sdfilename is not null but model is not, start sd print		
	 * @param pm Model 
	 * @param sdfilename  filename on sd card
	 * @throws InterruptedException
	 */
	public void printModel(Model pm,String sdfilename, boolean autostart) throws InterruptedException {
		if (state.connecting) {
			cons.appendText("Still connecting. Please wait until connecting is established.");
			return;
		}
		if (!state.connected) {
			cons.appendText("Not connected");
			return;
		}
		
		
		if(sdfilename == null){
			cons.appendText("Add model to print queue");
			printQueue.addModel(pm);	
			if (state.debug)
				cons.appendText("Filling print queue, Printing:" + state.printing);
		}else if (pm != null ){
			printQueue.putAuto(GCodeFactory.getGCode("M21", -21));
			printQueue.putAuto(GCodeFactory.getGCode("M28 "+sdfilename, -28));
			state.streaming=true;
			GCode finishstream = GCodeFactory.getGCode("M29 "+sdfilename, -29);
			if(autostart){
				GCode selectfile = GCodeFactory.getGCode("M23 "+sdfilename, -23);
				GCode printfile =  GCodeFactory.getGCode("M24", -24);
				printQueue.addModel(pm,finishstream,selectfile,printfile);
				state.sdprint=true;
			}else{
				printQueue.addModel(pm,finishstream);
			}
			if (state.debug)
				cons.appendText("Filling queue to stream, streaming:" + state.printing);
		}else{
			printQueue.putAuto(GCodeFactory.getGCode("M23 "+sdfilename, -23));
			printQueue.putAuto(GCodeFactory.getGCode("M24", -24));
			state.sdprint=true;
			if (state.debug)
				cons.appendText("SD print, Printing:" + state.printing);
           }
				
		setPrintMode(true);
		
	}
	
	/**
	 * Get the model which is currently printed
	 * @return
	 */
	public Model getModel(){
		return printQueue.getPrintModel();
	}

	/**
	 * Read from serial port for a printer response
	 * 
	 * @return ReceiveBuffer
	 */
	public ReceiveBuffer readResponse(int timeout) {
		ioBuffer.clear();// Make sure the buffer is cleared before
									// receiving new data
		long time = System.currentTimeMillis();
		while ((System.currentTimeMillis() - time) < timeout && isConnected()) {
			mConn.read(ioBuffer);
			if(state.debug){
				cons.log(serial, "Data Received:" + ioBuffer.toString().trim() );
			}
			
			// wait for full lines before notifying
			if (ioBuffer.endsWithNewLine()) {
				return ioBuffer;
			} else {
				if(state.debug){
					cons.log(serial, "Incomplete response, wait for more inBuffer="+ioBuffer.length());
				}
			}
		}
		if(isConnected()){
			ioBuffer.setTimedout(true);
		}
		return ioBuffer;
	}

	public void reset() {
		if(state.reset){
			//reset already 
			return;
		}
		if (state.connected) {
			cons.appendText("Reset connection");
			state.reset = true;
			//runner.interrupt(); // inform runner thread to do the reset
		} else {
			cons.appendText("Not connected");
		}
	}

	public void run() {
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		Thread.currentThread().setName("SerialRunner");
		if(!recover) doInit(); //initialize the connection
		while (state.connected) {
			if (state.reset) {
				doReset();
			} else {
				try {
					
					/*
					 * Main print processing
					 */
					printAndWaitQueue();
					
					/*
					 * Testrun to test IO communication latency
					 */
					if (state.testrun && !state.printing) {
						doTestRun();
					}
				} catch (InterruptedException e) {
					cons.log(serial, "PrintThread Interrupted"+ e);
				}
			}

		}
		cons.log(serial, "Runner Thread interrupted");
	}
	
	public void onRunError(Exception arg0, String arg1 ) {
		if (state.debug) cons.appendText(arg1+" Error:" + arg0.getMessage());
		cons.log("ERROR", arg1+" Error:" + arg0);
		try {
			disconnect();
		} catch (Exception e) {
		}
	}

	public boolean setBedTemp(float tmp) {
		cons.appendText("Set Bed Temperature to " + tmp + "°C");
		return addToPrintQueue(GCodeFactory.getGCode("M140 S" + tmp, 0), true);
	}

	public boolean setExtruderTemp(float tmp) {
		cons.appendText("Set Extruder Temperature to " + tmp + "°C");
		return addToPrintQueue(GCodeFactory.getGCode("M104 S" + tmp, 0), true);
	}
	
	public boolean setPrintSpeed(int percentage) {
		if(percentage <= 0){
			cons.appendText("Speed to low, please increase speed");
			return false;
		}
		if(percentage > state.printspeed){
			cons.appendText("Speed-up print to " + percentage + "%");
		}else{
			cons.appendText("Slow down print to " + percentage + "%");
		}
		state.printspeed=percentage;
		return addToPrintQueue(GCodeFactory.getGCode("M220 S" + percentage, 0), false);
	}
	
	public int getPrintSpeed() {
		return state.printspeed;
	}

	public void setPrintMode(boolean isprinting) {
		state.printing = isprinting;
		cons.appendText("Set printing " + state.printing);
		cons.setPrinting(isprinting);
		
		if (!isprinting) {
			String fin = "Print finished in " + ((System.currentTimeMillis() - printstart) / 1000) + "s";
			cons.log(serial, fin);
			cons.appendText(fin);
			printQueue.clear();
			if (state.testrun) {
				cons.appendText("Testrun completed, average response time (ms):" + testrunavg / 5002);
				testrunavg = 0;
				state.testrun = false;
			}
			state.lastgcode = GCodeFactory.getGCode("G0", 0);
			if(state.streaming) addToPrintQueue(GCodeFactory.getGCode("M29", -29), true); //Stop sd streaming
			cons.updateState(States.FINISHED,fin,-1);
			state.sdprint=false;
			state.streaming = false;
			if(state.debug) cons.appendText(showDebugData());
			cons.log("DEBUG",showDebugData());
		} else {
			System.gc(); //Force garbage collection to avoid gc during print
			printstart = System.currentTimeMillis();
			garbagetime =printstart+12000;
			state.percentCompleted=0;
			if(state.streaming || state.sdprint){
				cons.updateState(States.STREAMING,"unknown",0);
			}else{
				cons.updateState(States.PRINTING,getRemainingtime(),0);
			}
		}

	}

	public void setStepSize(float steps) {
		cons.appendText("Set move distance to " + steps + "mm");
		state.distance = steps;

	}
	
	public void setTempWatchIntervall(int ms) {
		tempwatchintervall=ms;
	}

	public void stopMotor() {
		cons.appendText("Stop all motors");
		addToPrintQueue(GCodeFactory.getGCode("M84", 0), true);
	}

	public void testrun() {
		if (state.connecting) {
			cons.appendText("Still connecting. Please wait until connecting is established.");
			return;
		}
		if (!state.connected) {
			cons.appendText("Not connected");
			return;
		}
		cons.appendText("Start communication test");
		state.testrun = true;
	}
	
	public void tryRecovery(){
		if(mConn == null || !isPrinting() || (System.currentTimeMillis() - starttime) < 15000){
			return ; //to early to recover
		}
		recover=true;
		startRunnerThread();
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		mConn.tryrecover();
		
	}

	public void toggleBaud() {
		if (state.connected || state.connecting) {
			cons.appendText("Please disconnect before changing the baud rate.");
			return;
		}
		if (state.baud == 115200) {
			state.baud = 250000;
		} else {
			state.baud = 115200;
		}
		cons.appendText("Set Baud " + state.baud);
	}

	public void setDebug(boolean db) {
		state.debug = db;
		cons.appendText(showDebugData());
		cons.appendText("Set debug " + state.debug);
	}

	public void toggleFan() {
		if (state.fan) {
			cons.appendText("Disable Fan");
			addToPrintQueue(GCodeFactory.getGCode("M107", 0), false);
		} else {
			cons.appendText("Enable Fan");
			addToPrintQueue(GCodeFactory.getGCode("M106", 0), false);
		}
		state.fan = !state.fan;
	}

	public void togglePause() {
		
		if(state.sdprint){
			//Execute commands to pause
			if(state.pause){
				state.pause=false; //Continue and resume
				addToPrintQueue(GCodeFactory.getGCode("M24",-24), false);
			}else{
				addToPrintQueue(GCodeFactory.getGCode("M25",-25), false);
				try {
					Thread.sleep(1000); //wait for printqueue to execute command before toggle pause
				} catch (InterruptedException e) {
				}
				state.pause=true;
			}
			
		}else{
			state.pause = !state.pause;
		}
		if (state.pause) {
			cons.appendText("Pause");
			if(state.debug){
				cons.appendText("Pause at GCode line number:" + state.lastgcode.getLineindex());
				cons.appendText(showDebugData());
			}
		} else {
			cons.appendText("Continue");
		}
		if(state.printing){
			 if(state.pause){
				 cons.updateState(States.PAUSED,null, -1);
			 }else{
				 cons.updateState(States.PRINTING,null, -1);
			 }
		}
	}

	public String showDebugData() {
			StringBuilder str = new StringBuilder();
			str.append("-----------------Debug Data---------------------------");
			str.append(Constants.newlinec);
			str.append("Connected/Connecting/reset:");
			str.append(state.connected);
			str.append("/");
			str.append(state.connecting);
			str.append("/");
			str.append(state.reset);
			str.append(Constants.newlinec);
			
			str.append("Baud:");
			str.append(state.baud);
			str.append(Constants.newlinec);
			
			str.append("Last GCode:");
			str.append(state.lastgcode.getCodeline().toString().trim());
			str.append(Constants.newlinec);
			
			str.append("Printing:");
			str.append(state.printing);
			str.append(Constants.newlinec);
			
			str.append("Streaming:");
			str.append(state.streaming);
			str.append(Constants.newlinec);
			
			str.append("Serial Port:");
			str.append(state.serialtype);
			str.append(Constants.newlinec);
			
			if(state.printing){
				str.append("Print Start:");
				str.append(new Date(printstart));
				str.append(Constants.newlinec);
				str.append("SD Card streaming/print:");
				str.append(state.streaming);
				str.append('/');
				str.append(state.sdprint);
				str.append(Constants.newlinec);
				if(getModel() != null){
					str.append("Filename:");
					str.append(getModel().getFilename());
					str.append(Constants.newlinec);
				}
			}
			str.append("Temperature:");
			str.append(state.tempstring.toString().trim());
			str.append(Constants.newlinec);
			
			str.append("TempWatch Intervall:");
			str.append(tempwatchintervall);
			str.append(Constants.newlinec);
			
			str.append("Communication Timeout (occurrences):");
			str.append(timeout);
			str.append("(");
			str.append(state.timeouts);
			str.append(")");
			str.append(Constants.newlinec);
			
			str.append("Last com. timeout linenr:");
			str.append(state.timeoutline);
			str.append(Constants.newlinec);
			
			str.append("Unexpected response:");
			str.append(state.unexpected);
			str.append(Constants.newlinec);
			
			str.append("Swallow OK:");
			str.append(state.swallows);
			str.append(Constants.newlinec);
			
			str.append("Read calls:");
			str.append(state.readcalls);
			str.append(Constants.newlinec);
			
			str.append("Wakelock:" );
			str.append(cons.hasWakeLock());
			str.append(Constants.newlinec);
			
			str.append("Time since last send:");
			str.append((System.currentTimeMillis() - starttime));
			str.append(Constants.newlinec);
			
			str.append("Manual Print Queue:");
			str.append(printQueue.getSizeManual());
			str.append(Constants.newlinec);
			
			str.append("Auto Print Queue:" );
			str.append(printQueue.getSizeAuto());
			str.append(Constants.newlinec);
			
			if(Constants.lastGarbage != 0){
				str.append("Last Garbage Collection:");
				str.append(new Date(Constants.lastGarbage));
				str.append(Constants.newlinec);
			}
			if (runner != null){
				str.append("RunnerThread Alive:");
				str.append(runner.isAlive());
				str.append(Constants.newlinec);
				StackTraceElement[] stack = runner.getStackTrace();
				str.append("RunnerThread Stack:");
				for (int i = 0; i < stack.length; i++) {
					str.append(stack[i].toString());
					str.append(Constants.newlinec);
				}
			}
			str.append("--------------------------------------------");
			str.append(Constants.newlinec);
			return str.toString();
	}

}

package de.dietzm.print;

import java.util.Date;

import de.dietzm.Constants;
import de.dietzm.Model;
import de.dietzm.Temperature;
import de.dietzm.Constants.GCDEF;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeFactory;
import de.dietzm.gcodes.GCodeStore;
import de.dietzm.gcodes.MemoryEfficientLenString;
import de.dietzm.gcodes.MemoryEfficientString;


public class SerialPrinter implements Runnable, Printer {

	public static final GCode G0 = GCodeFactory.getGCode("G0", 0);
	public static final GCode M105 = GCodeFactory.getGCode("M105", -105);
	public static final GCode M114 = GCodeFactory.getGCode("M114", -114);
	public static final GCode M20 = GCodeFactory.getGCode("M20", -20);
	public static final GCode M27 = GCodeFactory.getGCode("M27", -20);
	public static final GCode T0 = GCodeFactory.getGCode("T0", -1000);
	public static final GCode T1 = GCodeFactory.getGCode("T1", -1001);
	public static final String serial = "SERIAL"; //log tag
	public static final String io = "IO"; //log tag	

	private GCode recoverPoint = null; //Used to return to the old coords after pause (e.g. filament change)


	private ConsoleIf cons = null;
	private PrinterConnection mConn = null;
	private PrintQueue printQueue = null;
	private final ReceiveBuffer ioBuffer = new ReceiveBuffer(4096);
	private StringBuffer sdfiles = new StringBuffer();
	private int startline = 0;
	
	private Thread runner = null;
	private long printstart; //time when print started
	private long sendtime = 0;
	private long starttime = 0;
	private long garbagetime = 0;
	private boolean setRecoverPoint = false;
	private long lastTempWatch = 0; //time when last tempwatch happened
	private float testrunavg = 0;
	private int tempwatchintervall = 10000;
	private boolean resetoninit=true;
	private boolean logerrors=true;
	private boolean onconnect =false;
	private boolean homexyfinish = false; 
	private int extrnr = 1;
	private boolean strictmode = true;
	private int mintimout=50;
	
	/**
	 * Query firmware for current position and remember it so that we can return to this after pause
	 */
	public void setRecoverPoint() {
		cons.appendText("Set recovery point");
		setRecoverPoint=true;
		addToPrintQueue(M114, true);
	}
	
	protected boolean isHomeXYfinish() {
		return homexyfinish;
	}

	public void setHomeXYfinish(boolean homexyfinish) {
		this.homexyfinish = homexyfinish;
	}

	public boolean isResetoninit() {
		return resetoninit;
	}

	public void setResetoninit(boolean resetoninit) {
		this.resetoninit = resetoninit;
	}
	
	public void setOnConnect(boolean executeOnConnect) {
		this.onconnect = executeOnConnect;
	}

	private int movespeed = 3000;
	private int extspeed = 100;
	int gctimeout = 10000;
	
	
	public void setTimeout(int timeout) {
		this.gctimeout = timeout;
	}

	public void setMovespeed(int movespeed, int extspeed) {
		this.movespeed = movespeed;
		this.extspeed=extspeed;
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
		public static final String TOOLCHANGE="Tool Change";
		public static final String PAUSED="Printing Paused";
		public static final String RESET="Resetting Printer";
		public static final String CONNECTMSG = "Please connect";
		public static final String CONNECTINGMSG ="Establish connection";
		public static final String CONNECTEDMSG ="Ready to print";
		public static final String RESETMSG ="Resetting,please wait";
		public int baud = 115200;
		public float bedtemp = 0;
		public boolean connected = false;
		public boolean connecting = false;
		public boolean debug = false;
		public float distance = 1;
		public int activeExtr = 0;
		public boolean fan = false;
		public float lastE = 0;
		public GCode lastgcode = G0;
		public float[] lastpos = new float[] { 0f, 0f, 0f };
		public boolean pause = false;
		public boolean printing = false;
		public boolean streaming = false;
		public boolean sdlist = false;
		public boolean sdprint = false;
		public boolean reset = false;
		public boolean reseting = false;
		public int printspeed = 100;
		public int extrfactor = 100;
		public int timeouts=0;
		public int resends=0;
		public int resendskips=0;
		public int timeoutline=0;
		public int unexpected=0;
		public int swallows=0;
		public int readcalls=0;
		public int[] dynamicTimeout = new int[23];
		public int dynamicTimeoutPos = 0;
		public String serialtype="";
		public int percentCompleted=0;
		//public MemoryEfficientString tempstring = new MemoryEfficientString(new byte[64]);
		public Temperature temperature = new Temperature();
		public MemoryEfficientLenString timestring = new MemoryEfficientLenString(new byte[32]);
		public float[] exttemps = new float[Constants.MAX_EXTRUDER_NR];
		// public boolean absolute
		boolean testrun = false;
		int lineidx=0;
		String initresponse="";

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
		printQueue = new PrintQueue(2000);
		cons.appendText("Not connected. Press connect button to establish printer connection.");
	}
	
	public SerialPrinter( ConsoleIf console, int manual_queue_size) {
		this.cons = console;
		printQueue = new PrintQueue(manual_queue_size);
		cons.appendText("Not connected. Press connect button to establish printer connection.");
	}
	
	public void setConsole(ConsoleIf conso){
		this.cons=conso;
	}
	
	public ConsoleIf getConsole(){
		return this.cons;
	}

	/**
	 * Add a gcode to the print queue for async execution
	 * 
	 * @param code
	 * @param allow during manual operation only
	 * @return true if the command has been added to the queue successfully
	 */
	public boolean addToPrintQueue(GCode code, boolean manualOnly) {
		if (code == null || !code.isPrintable()) {
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
		if(mConn != null){
			mConn.requestDevice(device);
		}
	}
	
	public void startRunnerThread(){
		if (runner == null || !runner.isAlive()) {
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
				onConnect();
			}
		} catch (Exception e) {
			if(state.debug) cons.appendText("Init failed:" + e.getMessage());
			e.printStackTrace();
			disconnect();
		}		
	}
	
	private void doReset() {
		if (state.connected) {
			state.reseting=true;
			state.reset = false;
			try {
				if (state.printing) setPrintMode(false);
				state.lastE = 0;
				state.lastpos = new float[3];
				state.exttemps = new float[Constants.MAX_EXTRUDER_NR];
				state.swallows=0;
				state.unexpected=0;
				state.timeouts=0;
				state.printspeed=100;
				state.extrfactor=100;
				state.resends=0;
				state.resendskips=0;
				printQueue.clear();
				cons.updateState(States.RESET,States.RESETMSG,0);
				mConn.reset();
				ReceiveBuffer recv = readResponse(10000,1000);
				if (recv.isEmpty()) {
					cons.appendText("No printer response after reset ! Your hardware might not support reset over serial.");
				} else {
					cons.appendTextNoCR(recv);
				}			
			} catch (Exception e) {
				if (state.debug)
					cons.appendText("Reset failed or interrupted:" + e);
				e.printStackTrace();
			}
			state.reseting = false;
			cons.updateState(States.CONNECTED,States.CONNECTEDMSG,0);
		}
	}

	private void doTestRun() throws InterruptedException {
	//	printQueue.putAuto(GCodeFactory.getGCode("G90", 0)); // absolute
		Model mod = new Model("testrun");
		GCodeStore arr = mod.getGcodes();
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
	
	public int getCurrentLine() {
		return state.lineidx;
	}
	
	public CharSequence getRemainingtime() {
		float time = printQueue.getRemainingtime();
		if(state.printspeed != 100){ //Adjust time by speed
			time = time / state.printspeed * 100;
		}
		int	len = Constants.formatTimetoHHMMSS(time,state.timestring.getBytes());
		state.timestring.setlength(len-3); //Cut off seconds
		return state.timestring;
	}
	
	/**
	 * Get the size of the manual print queue
	 * @return
	 */
	public int getPrintQueueSize(){
		return printQueue.getSizeManual();
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
			code = "G28 " + move.toString()+"0";
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
		if(move == Axis.E){
			moveStep(move, sign, extspeed);
		}else{
			moveStep(move, sign, movespeed);
		}
	}
	
	public void listSDCard() {
		sdfiles.setLength(0);
		addToPrintQueue(GCodeFactory.getGCode("M21", -21), true);
		state.sdlist=true;
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
		if (res && state.debug)
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
		// do temp watch every 10 sec if busy. 
		//don't do tempwatch if not printing and manual gcode is in queue, if sd streaming or if listing sdcard files
		if (System.currentTimeMillis() - lastTempWatch < tempwatchintervall || (!state.printing && !printQueue.isManualEmpty()) || state.streaming || state.sdlist) {
			if (state.printing && !state.pause && printQueue.isManualEmpty()) {
				if(recoverPoint != null){
					cons.appendText("Starting from recovery point");
					code= recoverPoint;
					recoverPoint=null;
				}else{
					code = printQueue.pollAuto(); // poll for auto ops
				}
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
					state.lineidx++;
					if(state.lineidx < startline || !code.isPrintable()){
						return; //not printable or Resume (startline)
					}
				}
					
				if(logerrors && Constants.lastGarbage-garbagetime > 0){
					garbagetime=Constants.lastGarbage;
					cons.appendText("Garbage collector run during print !");
					cons.log("GC", String.valueOf(garbagetime));
				}
			}else{
				code = printQueue.pollManual(1); // poll for manual ops
				if(code != null){
					state.lastgcode = code;
				}
			}
		} else {
			code = M105; // retrieve temp
			state.lastgcode = code;
			lastTempWatch = System.currentTimeMillis();
		}
		if (state.reset || code == null)
			return;

		starttime = System.currentTimeMillis();
		if (state.debug) {
			cons.appendTextNoCR(String.valueOf(state.lineidx),": ", code.getCodeline());
			if (code.isBuffered() && code.getTimeAccel() > 0.2) {
				cons.appendText("(Est.Time:", String.valueOf(Constants.round3digits(code.getTimeAccel())) , "s)");
			}
		}

		/*
		 * Write the gcode to the printer Measure time and lof
		 */
		if(state.debug){
			cons.log(serial, "write gcode line:"+state.lineidx);
			cons.log(serial, code.getGcode().toString());
		}
		int len = code.getCodeline(ioBuffer.array); //Get codeline into buffer
		ioBuffer.setlength(len);					//adjust buffer len to what we got
		mConn.writeBuffer(ioBuffer);				//write buffer
		cons.log(io, null, ioBuffer); 				//log buffer to logfile
		sendtime = System.currentTimeMillis();
		state.readcalls=0; //reset readcalls var debug only
		int resendcnt=0;
		/*
		 * Wait for the response
		 */
		while (state.connected && !state.reset) {
			if(mConn.getType() == PrinterConnection.BLUETOOTH)  Thread.sleep(5); //Response will take at least 5 ms, safe CPU cylces

			// receive updates wait until timeout
			int readtimeout = gettimeout(code);
			ReceiveBuffer recv = readResponse(readtimeout);
			cons.log(io, null, recv); 						//log buffer to logfile
			if (state.reset)
				break;

			if (state.debug && code.isBuffered() && (System.currentTimeMillis() - sendtime) > 400) {
				float resptime = Constants.round3digits(((float) (System.currentTimeMillis() - sendtime)) / 1000);
				cons.appendText("Wait for printer response: ", String.valueOf(resptime) , "s");
			}
			if (recv.isEmpty() || recv.isTimedout() ) {
				if(!strictmode && readtimeout == mintimout){
					break;
				}
				state.timeouts++;
				state.timeoutline=state.lineidx;
				if(state.debug || logerrors){
					//This can even happen in normal cases e.g. when the move is very long (1st in the buffer)
					String logm = "Timeout waiting for printer response at line #"+state.timeoutline+"("+ String.valueOf((System.currentTimeMillis() - starttime))+ "ms)";
					cons.appendText(logm);
					cons.log("ERROR", logm +" Timeout="+readtimeout+ " pos"+state.dynamicTimeoutPos);
					if(state.debug){					
						for (int i = 0; i < state.dynamicTimeout.length; i++) {
						cons.log("ERROR", state.dynamicTimeout[i]+"["+i+"]");
						}
						cons.log("ERROR", code.getCodeline().toString());
					}
				}
				break; // timeout
			}

			if (state.debug || (!recv.isPlainOK() && code != M105 && code.getGcodeId() != GCDEF.T0.getId() && code.getGcodeId() != GCDEF.T1.getId()) ) {
				// Suppress plain ok and temp when printing
				cons.appendTextNoCR(recv);
			}

			if(state.streaming && recv.containsFail()){
				//e.g. open failed when filename is wrong
				cons.appendText("Error during streaming, abort");
				setPrintMode(false);
			}
			
			/*
			 * Resend handling
			 */
			if ( recv.containsResend()){
				resendcnt++;
				int linenr = recv.parseResend();
				if(resendcnt <= 1){
					state.resends++;
					if(state.debug){
						cons.appendText("Resend #"+linenr+"detected ... trying to resend last gcode");
					}
					String cmd = "N"+linenr+" "+code.getCodeline();
					ioBuffer.put(cmd.getBytes());
					ioBuffer.setlength(cmd.length());			
					mConn.writeBuffer(ioBuffer);				//write buffer
				}else{
					state.resendskips++;
					cons.appendText("Resend retry failed ... skipping");
					String cmd = "N"+linenr+" M105\n";
					ioBuffer.put(cmd.getBytes());
					ioBuffer.setlength(cmd.length());			
					mConn.writeBuffer(ioBuffer);				//write buffer
					break;
				}				
			}
			
			/*
			 * Check if printer command is committed with "ok" make sure to
			 * update temperature in case of M105 command print out debug info
			 * in case of testruns
			 */
			if (recv.containsOK() || (!strictmode && code == M20 && recv.containsEnd())) {
				//cons.log(serial, "OK");
				if (code == M105 && recv.containsTx()) { // Parse temperature
					try {
						state.temperature.setTempstring(recv);
						state.temperature.setActiveExtruder(state.activeExtr);
						cons.setTemp(state.temperature);
					} catch (Exception e) {
						//Ignore parsing errors
						break;
					}
				}else if (code == M105 && recv.isPlainOK()){
						state.swallows++;
					if(state.debug || logerrors){						
						cons.appendText("Swallow OK");
						cons.log("ERROR", "Swallow OK");
					}
					continue;
				}else if (code == M105){
					cons.log(serial, "Wait for temp");
					continue;
				}else if(code == M20){
					state.sdlist=false;
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
				}else if(code.getGcodeId() == GCDEF.T0.getId() && state.activeExtr != 0){
					state.activeExtr=0;
					cons.updateState(States.TOOLCHANGE,null, -1);
				}else if(code.getGcodeId() == GCDEF.T1.getId() && state.activeExtr != 1){
					state.activeExtr=1;
					cons.updateState(States.TOOLCHANGE,null, -1);
				}else if(setRecoverPoint && code == M114){
					try{
					CharSequence coord = recv.parseCoord();
					cons.appendText("Recovery Coords:"+coord);
					setRecoverPoint=false;
					recoverPoint = GCodeFactory.getGCode("G1 "+coord, -1);
					}catch(Exception e){
						cons.appendText("Could not set recovery point");
					}
				}

				// if(state.debug){
				float diff = (System.currentTimeMillis() - starttime);
				if (state.testrun) {
					testrunavg = testrunavg + diff;
					if (diff > 50) {
						cons.log("GCodeLongTime", String.valueOf(state.lineidx)); 
						cons.log("GCodeLongTime", String.valueOf(diff));
						cons.appendText("Took (overall ms): ", String.valueOf(diff));
					}
				}
				break;
			}
			//Check if temperature field needs to be updated (M109,m116,..)
			if (recv.containsTx()) { // Parse temperature
				try {
					state.temperature.setTempstring(recv);
					state.temperature.setActiveExtruder(state.activeExtr);
					cons.setTemp(state.temperature);
				} catch (Exception e) {
					// handle parsing errors 
					break;
				}
				if(code == M105) break;
				continue; //Smoothieware might send T: response even if we are already executing a buffered cmd
			}
			if( recv.containsWait()){
				cons.log(serial, "Wait received");
				/** From Repetier FW: #define WAITING_IDENTIFIER "wait" : Communication errors can swollow part of the ok, which tells the host software to send
				the next command. Not receiving it will cause your printer to stop. Sending this string every
				second, if our queue is empty should prevent this. Comment it, if you don't wan't this feature. */
				break; //we likely missed the "ok" 
			}
			if(code.isBuffered() && !recv.startsWithEcho() && !recv.startsWithGO() && !recv.startsWithComment()){ //For buffered commands we only expect ok
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
	 * @param line line number in gcode file to start with (resume)
	 * @throws InterruptedException
	 */
	public void printModel(Model pm,String sdfilename, boolean autostart, int line) throws InterruptedException {
		if (state.connecting) {
			cons.appendText("Still connecting. Please wait until connecting is established.");
			return;
		}
		if (!state.connected) {
			cons.appendText("Not connected");
			return;
		}
		startline=0; //reset startline (resume)
		
		if(sdfilename == null){
			//Direct printing
			cons.appendText("Add model to print queue");
			printQueue.addModel(pm);	
			startline=line;
			if (state.debug)
				cons.appendText("Filling print queue, Printing:" + state.printing);
		}else if (pm != null ){
			//Stream to SD Card
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
			//Print from SD Card
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
	 * @param timeout in milliseconds
	 * @return ReceiveBuffer
	 */
	public ReceiveBuffer readResponse(int timeout) {		
		return readResponse(timeout,0);
	}
	
	
	/**
	 * Read from serial port for a printer response
	 * @param timeout , read until timeout is reached or \n is received
	 * @param minwait time , even if \n is received , should always be less then timeout 
	 * @return ReceiveBuffer
	 */
	public ReceiveBuffer readResponse(int timeout, int minwait) {
		ioBuffer.clear();// Make sure the buffer is cleared before
									// receiving new data
		long time = System.currentTimeMillis();
		while ((System.currentTimeMillis() - time) < timeout && isConnected() && !state.reset) {
			mConn.read(ioBuffer,minwait!=0?minwait:timeout);
			if(state.debug){
				cons.log(serial, "Data Received:" + ioBuffer.toString().trim() );
			}
			
			//Check if minimum wait time is reached (used for init/reset)
			boolean ismin = (System.currentTimeMillis() - time) >= minwait ;
			// wait for full lines before notifying
			if ( ismin && ioBuffer.endsWithNewLine()) {
				return ioBuffer;
			} else {
				if(state.debug){
					cons.log(serial, "Incomplete response, wait for more inBuffer="+ioBuffer.length()+(ismin?" mintime":""));
				}
			}
		}
		if(isConnected()){
			if(timeout == 0){
				cons.log(serial, "Dynamic Timeout hit:"+timeout);
			}
			ioBuffer.setTimedout(true);
		}
		return ioBuffer;
	}

	public void reset() {
		if(state.reset || state.reseting){
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
		doInit(); //initialize the connection
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
		return addToPrintQueue(GCodeFactory.getGCode("M140 S" + tmp, 0), false);
	}

	public boolean setExtruderTemp(float tmp) {
		cons.appendText("Set Extruder Temperature to " + tmp + "°C");
		boolean ret = addToPrintQueue(GCodeFactory.getGCode("M104 S" + tmp, 0), false);
		if(ret){
			state.exttemps[state.activeExtr]=tmp;
		}
		return ret;
	}
	
	public float getExtruderTemp(){
		return state.exttemps[state.activeExtr]; //TODO Could be done with gcode
	}
	
	public int getActiveExtruder(){
		return state.activeExtr;
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
		return addToPrintQueue(GCodeFactory.getGCode("M220 S" + percentage, -220), false);
	}
	
	public boolean setExtrusionFactor(int percentage) {
		if(percentage <= 0){
			cons.appendText("Factor to low, please increase extrusion");
			return false;
		}
		if(percentage > state.printspeed){
			cons.appendText("Increase extrusion to " + percentage + "%");
		}else{
			cons.appendText("Reduce extrusion to " + percentage + "%");
		}
		state.extrfactor=percentage;
		return addToPrintQueue(GCodeFactory.getGCode("M221 S" + percentage, -221), false);
	}
	
	public int getPrintSpeed() {
		return state.printspeed;
	}
	
	public int getExtrusionFactor() {
		return state.extrfactor;
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
			state.lineidx=0;
			if (state.testrun) {
				cons.appendText("Testrun completed, average response time (ms):" + testrunavg / 5002);
				testrunavg = 0;
				state.testrun = false;
			}
			state.lastgcode = GCodeFactory.getGCode("G0", 0);
			onFinish();
			state.printspeed=100;
			state.extrfactor=100;
			cons.updateState(States.FINISHED,fin,-1);
			state.sdprint=false;
			state.streaming = false;
			if(state.debug) cons.appendText(showDebugData());
			cons.log("DEBUG",showDebugData());
		} else {
			printstart = System.currentTimeMillis();
			garbagetime =printstart+12000;
			System.gc(); //Force garbage collection to avoid gc during print
			state.lineidx=0;
			state.percentCompleted=0;
			if(state.streaming || state.sdprint){
				cons.updateState(States.STREAMING,"unknown",0);
			}else{
				cons.updateState(States.PRINTING,getRemainingtime(),0);
			}
		}

	}
	/**
	 * GCodes will be executed when the print is finished/stopped
	 */
	private void onFinish() {
		if(state.streaming) addToPrintQueue(GCodeFactory.getGCode("M29", -29), true); //Stop sd streaming
		//Turn off temperature and move to X/Y 0
		if(extrnr == 1){
			//single extr.
			addToPrintQueue(GCodeFactory.getGCode("M104 S0", -104), true);
		}else{
			for (int i = 0; i < extrnr; i++) {
				addToPrintQueue(GCodeFactory.getGCode("T"+i, -1002), true);
				addToPrintQueue(GCodeFactory.getGCode("M104 S0", -104), true);
			}
			addToPrintQueue(GCodeFactory.getGCode("T0", -1002), true);
		}	
		addToPrintQueue(GCodeFactory.getGCode("M140 S0", -140), true); //bed
		if(homexyfinish)addToPrintQueue(GCodeFactory.getGCode("G28 X0 Y0", -128), true);
		if(state.printspeed!=100)addToPrintQueue(GCodeFactory.getGCode("M220 100", -220), true); //set speed back to 100%
		if(state.extrfactor!=100)addToPrintQueue(GCodeFactory.getGCode("M221 100", -221), true); //set extr back to 100%
	}
	
	/**
	 * Get the timeout for the gcode execution.
	 * If dynamic timeouts are configured calculated the timeout based on the gcode duration
	 * @return int timeout
	 */
	int gettimeout(GCode code){
		//Default values for init
		if(code == null){
			if(gctimeout == 0) return 6000;
			return gctimeout;
		}
		//Witbox special handlin ... supresses ok when doing G28
		if( !strictmode 
				&& !GCDEF.G1.equals(code.getGcodeId()) 
				&& !GCDEF.G2.equals(code.getGcodeId()) 
				&& !GCDEF.G0.equals(code.getGcodeId())
				&& !GCDEF.M105.equals(code.getGcodeId())
				&& !GCDEF.M20.equals(code.getGcodeId())
				){
			cons.log("serial","Witbox low timeout workaround");
			return mintimout;
		}
		
		if(code.isLongRunning()) return 60000; //long running gcodes timeout after 60sec
		
		if(gctimeout == 0){
			state.dynamicTimeout[state.dynamicTimeoutPos%state.dynamicTimeout.length] = (int)(code.getTimeAccel()*1000);
			//Dynamic
			int min_timeout = 3000; //minimal timeout to add
			int maxtime = 0;
			for (int i = 0; i < state.dynamicTimeout.length; i++) {
				maxtime = Math.max(maxtime, state.dynamicTimeout[i]);
			}
			if(state.printspeed != 100){ //Adjust timeout by speed
				maxtime = maxtime / state.printspeed * 100;
			}
//			System.out.println("Time["+state.dynamicTimeoutPos%16+"]:"+state.dynamicTimeout[state.dynamicTimeoutPos%16]);
//			System.out.println("MAX:                             ----> "+maxtime);
			state.dynamicTimeoutPos++;
			return maxtime+min_timeout;
		}else{
			//Static
			return gctimeout;
		}
	}
	
	/**
	 * GCodes will be executed when the connection has been established
	 */
	private void onConnect() {
		if(onconnect){
			addToPrintQueue(GCodeFactory.getGCode("M42 P6 S255", -42), true);
			addToPrintQueue(GCodeFactory.getGCode("M42 P7 S255", -42), true);
		//	addToPrintQueue(GCodeFactory.getGCode("M115", -115), true);
		}
	}

	public void setStepSize(float steps) {
		cons.appendText("Set move distance to " + steps + "mm");
		state.distance = steps;

	}
	
	/**
	 * Allow to disable strict mode.
	 * Strict mode means that an "ok" acknowledgement is needed for all gcodes
	 * Non-Strict mode will only require it for G0/1/2/3+M105
	 * @param strict
	 */
	public void setStrictMode(boolean strict){
		this.strictmode=strict;
	}
	
	/**
	 * Set the active extruder
	 * @param i , if -1 then toggle between dual 0:1
	 */
	public boolean setActiveExtruder(int tool){
		if(tool == -1){
			if(state.activeExtr == 0){
				tool=1;
			}else{
				tool=0;
			}
		}
		if(tool > Constants.MAX_EXTRUDER_NR-1){
			cons.appendText("Max extruder number is "+(Constants.MAX_EXTRUDER_NR-1));
			return false;
		}
		boolean ret = addToPrintQueue(GCodeFactory.getGCode("T"+tool, -1002), true);
		if(ret){
//			state.exttemps[state.activeExtr]=state.exttemp; //Store and retrieve temp for extruder
			state.activeExtr=tool;			
//			state.exttemp=state.exttemps[state.activeExtr];
			cons.appendText("Set Active extruder:"+state.activeExtr+ " Temp:"+state.exttemps[state.activeExtr]);
		}
		return ret;
	}
	
	/**
	 * Set Number of extruders
	 */
	public boolean setExtruderNumber(int nr){
		if(nr == 0 || nr > Constants.MAX_EXTRUDER_NR){
			return false;
		}
		extrnr=nr;
		return true;
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
				cons.appendText("Pause at GCode line number:" + state.lineidx);
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
			str.append("Connected/Connecting/reset/resetting:");
			str.append(state.connected);
			str.append("/");
			str.append(state.connecting);
			str.append("/");
			str.append(state.reset);
			str.append("/");
			str.append(state.reseting);
			str.append(Constants.newlinec);
			
			str.append("Baud:");
			str.append(state.baud);
			str.append(Constants.newlinec);
			
			if(state.lastgcode != null){
			str.append("Last GCode:");
			str.append(state.lastgcode.getCodeline().toString().trim());
			str.append(Constants.newlinec);
			}
			
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
			str.append(state.temperature.tempstring.toString().trim());
			str.append(Constants.newlinec);
			
			str.append("TempWatch Intervall:");
			str.append(tempwatchintervall);
			str.append(Constants.newlinec);
			
			str.append("Communication Timeout (occurrences):");
			str.append(gctimeout);
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
			
			str.append("Swallow OK/Resend/Resendskip");
			str.append(state.swallows);
			str.append('/');
			str.append(state.resends);
			str.append('/');
			str.append(state.resendskips);
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
			
			str.append("Init response:" );
			str.append(state.initresponse);
			str.append(Constants.newlinec);

			str.append("--------------------------------------------");
			str.append(Constants.newlinec);
			
			return str.toString();
	}

}


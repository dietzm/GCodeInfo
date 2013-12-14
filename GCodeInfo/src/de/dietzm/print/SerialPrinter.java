package de.dietzm.print;


import java.util.Date;

import de.dietzm.Constants;
import de.dietzm.Model;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeFactory;
import de.dietzm.gcodes.MemoryEfficientString;


public class SerialPrinter implements Runnable, Printer {

	public static final GCode G0 = GCodeFactory.getGCode("G0", 0);
	public static final GCode M105 = GCodeFactory.getGCode("M105", 0);
	public static final String serial = "SERIAL"; //log tag	
	
	private ConsoleIf cons = null;
	private PrinterConnection mConn = null;
	private final PrintQueue printQueue = new PrintQueue();;
	private final ReceiveBuffer ioBuffer = new ReceiveBuffer(4096);
	
	private Thread runner = null;
	private long printstart; //time when print started
	private long sendtime = 0;
	private long starttime = 0;
	private long lastTempWatch = 0; //time when last tempwatch happened
	private float testrunavg = 0;
	private int tempwatchintervall = 10000;
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
		public boolean reset = false;
		public int timeouts=0;
		public int timeoutline=0;
		public int unexpected=0;
		public int swallows=0;
		public String serialtype="";
		public MemoryEfficientString tempstring = new MemoryEfficientString(new byte[64]);
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
	 * @return true if the command has been added to the queue successfully
	 */
	public boolean addToPrintQueue(GCode code, boolean manual) {
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
		if (manual && state.printing && !state.pause) {
			cons.appendText("Stop or Pause printing first");
			return false;
		}
		if (!manual && !state.printing) {
			cons.appendText("Printing stopped");
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
		state.baud=baud;
		mConn=type;
		boolean succ = mConn.enumerate();
		if(!succ) state.connecting=false;
		return succ;
	}

	public void connectTo(String device) {
		mConn.requestDevice(device);
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
				state.pause = true;
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
			}
			runner = null;
		}
	}

	
	private void doInit() {
		if (state.printing)	setPrintMode(false);
		printQueue.clear();
		state.lastE = 0;
		state.lastpos = new float[3];
		try{
			if(mConn.init()){
				state.connected=true;
				state.connecting=false;
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
		printQueue.putAuto(GCodeFactory.getGCode("G90", 0)); // absolute
		for (int i = 0; i < 5000; i++) {
			GCode gco = GCodeFactory.getGCode("G1 X10 Y10", 0);
			printQueue.putAuto(gco);
		}
		printQueue.putAuto(GCodeFactory.getGCode("M114", 5002));
		setPrintMode(true);
	}

	public GCode getCurrentGCode() {
		return state.lastgcode;
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
		if (System.currentTimeMillis() - lastTempWatch < tempwatchintervall || (!state.printing && !printQueue.isManualEmpty())) {
			if (state.printing && !state.pause) {
				code = printQueue.pollAuto(); // poll for auto ops
				state.lastgcode = code;// remember last code to sync with UI
				if (code == null)
					setPrintMode(false); // Finish printing
			}
			if (!state.printing || state.pause) {
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
		sendtime = System.currentTimeMillis();

		/*
		 * Wait for the response
		 */
		while (state.connected && !state.reset) {
			if(mConn.getType() == PrinterConnection.BLUETOOTH)  Thread.sleep(5); //Response will take at least 5 ms, safe CPU cylces

			// receive updates wait 10min or 10sec
			ReceiveBuffer recv = readResponse(code.isLongRunning()?60000:timeout);

			if (state.reset)
				break;

			if (state.debug && code.isBuffered() && (System.currentTimeMillis() - sendtime) > 400) {
				float resptime = Constants.round3digits(((float) (System.currentTimeMillis() - sendtime)) / 1000);
				cons.appendText("Wait for printer response: ", String.valueOf(resptime) , "s");
			}
			if (recv.isEmpty()) {
				state.timeouts++;
				state.timeoutline=code.getLineindex();
				if(state.debug){
					//This can even happen in normal cases e.g. when the move is very long (1st in the buffer) 
					cons.appendText("Timeout waiting for printer response (", String.valueOf((System.currentTimeMillis() - starttime)), "ms)");
				}
				break; // timeout
			}

			if (state.debug || (!recv.isPlainOK() && code != M105) ) {
				// Suppress plain ok and temp when printing
				cons.appendTextNoCR(recv);
			}

			/*
			 * Check if printer command is committed with "ok" make sure to
			 * update temperature in case of M105 command print out debug info
			 * in case of testruns
			 */
			if (recv.containsOK() || recv.containsWait()) {
				//cons.log(serial, "OK");
				if (code == M105 && recv.containsTx()) { // Parse temperature
					int idx = recv.indexOf('@');
					if(idx == -1){
						state.tempstring = recv.subSequence(3, recv.length(),state.tempstring);
					}else{
						state.tempstring = recv.subSequence(3, idx,state.tempstring);
					}
					cons.setTemp(state.tempstring);
				}else if (code == M105 && recv.isPlainOK()){
					if(state.debug){
						state.swallows++;
						cons.appendText("Swallow OK");
					}
					continue;
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
			
			if(code.isBuffered() && !recv.startsWithEcho()){ //For buffered commands we only expect ok
				state.unexpected++;
				cons.appendText("Unexpected response from printer: "+recv.toString());
				break;
			}
		}
		// Wait longer for the final result
	}

	public void printModel(Model pm) throws InterruptedException {
		if (state.connecting) {
			cons.appendText("Still connecting. Please wait until connecting is established.");
			return;
		}
		if (!state.connected) {
			cons.appendText("Not connected");
			return;
		}
		printQueue.addModel(pm);
		setPrintMode(true);
		if (state.debug)
			cons.appendText("Model added, Printing:" + state.printing);
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
				}else{
					cons.log(serial, "Incomplete response, wait for more");
				}
			}
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
			if (state.printing)
				setPrintMode(false);
			state.reset = true;
			runner.interrupt(); // inform runner thread to do the reset
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
		if (state.debug)
			cons.appendText(arg1+" Error:" + arg0.getMessage());
			cons.log(serial, arg1+" Error:" + arg0);
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

	public void setPrintMode(boolean isprinting) {
		state.printing = isprinting;
		cons.appendText("Set printing " + state.printing);

		cons.setPrinting(isprinting);
		
		if (!isprinting) {
			cons.log(serial, "Print Finished Time:" + (System.currentTimeMillis() - printstart));
			cons.appendText("Print finished in " + ((System.currentTimeMillis() - printstart) / 1000) + "s");
			printQueue.clear();
			if (state.testrun) {
				cons.appendText("Testrun completed, average response time (ms):" + testrunavg / 5002);
				testrunavg = 0;
				state.testrun = false;
			}
			state.lastgcode = GCodeFactory.getGCode("G0", 0);
		} else {
			System.gc(); //Force garbage collection to avoid gc during print
			printstart = System.currentTimeMillis();
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
		cons.appendText("Set debug " + state.debug);
		showDebugData();
	}

	public void toggleFan() {
		if (state.fan) {
			cons.appendText("Disable Fan");
			addToPrintQueue(GCodeFactory.getGCode("M107", 0), true);
		} else {
			cons.appendText("Enable Fan");
			addToPrintQueue(GCodeFactory.getGCode("M106", 0), true);
		}
		state.fan = !state.fan;
	}

	public void togglePause() {

		state.pause = !state.pause;
		if (state.pause) {
			cons.appendText("Pause");
			if(state.debug){
				cons.appendText("Pause at GCode line number:" + state.lastgcode.getLineindex());
				showDebugData();
			}
		} else {
			cons.appendText("Continue");
		}
	}

	public void showDebugData() {
			cons.appendText("-----------------Debug Data---------------------------");
			cons.appendText("Connected/Connecting/reset:"+state.connected+"/"+state.connecting+"/"+state.reset);
			cons.appendText("Baud:"+state.baud);
			cons.appendText("Last GCode:"+state.lastgcode.getCodeline().toString().trim());
			cons.appendText("Printing:"+state.printing);
			cons.appendText("Serial Port:"+state.serialtype);
			if(state.printing) cons.appendText("Print Start:"+new Date(printstart));
			cons.appendText("Temperature:"+state.tempstring.toString().trim());
			cons.appendText("TempWatch Intervall:"+tempwatchintervall);
			cons.appendText("Communication Timeout (occurrences):"+timeout+"("+state.timeouts+")");
			cons.appendText("Last com. timeout linenr:"+state.timeoutline);
			cons.appendText("Unexpected response:"+state.unexpected);
			cons.appendText("Swallow OK:"+state.swallows);
			cons.appendText("Wakelock:" +cons.hasWakeLock());
			cons.appendText("Time since last send:" +(System.currentTimeMillis() - starttime));
			cons.appendText("Manual Print Queue:" +printQueue.getSizeManual());
			cons.appendText("Auto Print Queue:" +printQueue.getSizeAuto());
			if(Constants.lastGarbage != 0){
				cons.appendText("Last Garbage Collection:" +new Date(Constants.lastGarbage));
			}
			if (runner != null){
				cons.appendText("RunnerThread Alive:" + runner.isAlive());
				StackTraceElement[] stack = runner.getStackTrace();
				cons.appendText("RunnerThread Stack:");
				for (int i = 0; i < stack.length; i++) {
					cons.appendText(stack[i].toString());
				}
			}
			cons.appendText("--------------------------------------------");
	}

}

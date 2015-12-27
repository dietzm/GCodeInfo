package de.dietzm.gcodesim;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import de.dietzm.Constants;
import de.dietzm.Constants.GCDEF;
import de.dietzm.Layer;
import de.dietzm.Model;
import de.dietzm.Position;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeStore;
import de.dietzm.gcodes.MemoryEfficientLenString;
import de.dietzm.print.Printer;

/**
 * Gcodepaint Class starts a thread and iterates through the gcodes and paint the corresponding lines. 
 * 
 * @author mdietz
 *
 */
public class GcodePainter implements Runnable {
	public static enum Commands {STEPBACK,STEP50X,STEP50XBACK,RESTART,NEXTLAYER,NEXTLAYER10,REPAINTLABEL,DEBUG,EXIT,OPENFILE,NOOP,PRINT,REPAINTLAYERS,PREVIOUSLAYER,HELP,REANALYSE};
	public static enum Travel {NOT_PAINTED,DOTTED,FULL}
	public int bedSquare = 200;
	//public int bedsizeSquare = 200;
	public int bedsizeDiff=0;
	public int colNR = 7;
	private Travel painttravel = Travel.DOTTED;
	private int defbuffersize = 15;
	private int header = 40; 
	public static float boxheightfactor = 10f;
	public boolean roundbed=false;
	boolean snapshot=false; 
	boolean norepaint=false;
	public float extrazoom = 1f;
	long ffstarttime = 0;
	float[] sideList = new float[6];
	
	
	public Position[] extruderOffset = {null,null,null,null}; //TODO make it configureable
	public boolean applyOffset = true; //if offset between T0 & T1 is visable in gcode and must be calculated out
	private int activeExtruder = 0;
	public Travel isPainttravel() {
		return painttravel;
	}

	public void jumptoLayer(int lnr){
		if(model.getLayercount(true) <= lnr){
			fftoLayer=model.getLayercount(true);
		}else{
			fftoLayer=lnr;				
		}		
	}

	public void setPainttravel(Travel painttravel) {
		this.painttravel = painttravel;
	}

	public boolean paintWhilePrint = true;
	public boolean oneLayerAtATime =false;

	protected boolean isOneLayerAtATime() {
		return oneLayerAtATime;
	}


	public void setOneLayerAtATime(boolean oneLayerAtATime) {
		this.oneLayerAtATime = oneLayerAtATime;
	}
	
	public void setBufferSize(int paintahead){
		defbuffersize=paintahead;
	}
	
	public void setExtruderOffset(int nr, Position pos){
		extruderOffset[nr] = pos;
	}

	private float fanspeed = 0;
	//Private vars
	private boolean ffLayer=false; //Fast Forward Layer
	private int fftoGcode=0 , fftoLayer=0; //Fast forward to gcode linenumber # / to layer
	private float zoom = 3.5f;
	private Layer currentlayer=null;
	private float speedup = 5;
	public final static float defaultzoommod=2.74f;
	private float zoommod=defaultzoommod; //0.1f = 0.025ratio 
	float zoomsmall= zoom/zoommod;
	private float bedSquareZoomed = bedSquare*zoom;
	private int pause = 0; 
	private boolean inpause=false;
	private int Xoffset=0,Yoffset=0;
	private Thread gcodepainter;
	private String errormsg=null;
	private String[] modelspeed=null,modelcomments=null,modellaysum=null;
	private String[] modeldetails =null;
	private String speeduplabel=speedup+"x";
	private MemoryEfficientLenString tempbuf = new MemoryEfficientLenString(new byte[10]);
	private float mtime;
	private Printer printer=null;
	private ArrayList<Layer> layers;
	private GraphicRenderer g2;
	private Commands cmd = Commands.NOOP;
	private boolean print=false;
	private int inbuffer = 0;
	private int bufferemptyindex = 0;
	//private StringBuilder details = new StringBuilder(2000);
	
	//Public vars, might be accessed from other classes.
	// Model & Layer Info
	public Model model = null;
	public final static int defaultgap=10;
	private int gap=10;

	public void setPaintWhilePrint(boolean paintWhilePrint) {
		this.paintWhilePrint = paintWhilePrint;
	}

	
	/**
	 * Get Zoom factor
	 * @return float zoom factor
	 */
	public float getZoom() {
		return zoom;
	}
	
	/**
	 * Get Current Layer
	 * @return Layer
	 */
	public Layer getCurrentLayer() {
		return currentlayer;
	}

	/**
	 * Set a new Zoom factor and repaint
	 * @param zoom float
	 */
	public void setZoom(float zoom) {
		this.zoom = zoom;
		zoomsmall= zoom/zoommod;
		bedSquareZoomed = bedSquare*zoom;
		setCmd(Commands.REPAINTLAYERS);
	}

	
	/**
	 * Will increase/decrease the speed , can increase multiple steps at once (ff,rev)
	 * @param boolean faster, true=increase speed, false=decrease
	 */
	public synchronized void toggleSpeed(boolean faster, int steps){

			if(faster ){
				if(speedup+steps > 99) {
					speedup=99;
				}else{
					speedup=Math.round(speedup+steps);
				}
			}else {
				if(speedup-steps <= 0.1) {
					speedup=0.1f;
				}else{
					speedup=speedup-steps;
				}
			}
			updateSpeedupLabel();
		setCmd(Commands.REPAINTLABEL);
	}
	
	/**
	 * Will increase/decrease the speed 
	 * @param boolean faster, true=increase speed, false=decrease
	 */
	public synchronized void toggleSpeed(boolean faster){
			if(faster && speedup >= 1){
				if(speedup >= 99) return;
				speedup++;
			}else if(faster && speedup < 1){
				speedup+=0.1;
			}else if(speedup > 1){
				speedup--;
			}else if(speedup > 0.10f){
				speedup-=0.10f;
			}
			updateSpeedupLabel();
		setCmd(Commands.REPAINTLABEL);
	}

	/**
	 * To avoid creating many temp String object , create label only when speedup is changed
	 */
	private void updateSpeedupLabel() {
		//update label
		if(speedup > 1) {
			speeduplabel=speedup+"x";
		}else{
			speeduplabel = Constants.round2digits(speedup)+"x";
		}
	}



	public synchronized void togglePause() {
		if (pause == 0) {
			pause =999999;
		} else {
			pause =0;
			//Prevent interrupting other calls than pause (thread.sleep) 
			if(inpause){
				gcodepainter.interrupt();
			}
		}		
	}
	
	public boolean isPause(){
		return pause!=0;
	}
	
	public synchronized void doStep(boolean forward) {
		if(forward){
			if(pause != 0 && inpause){
				//1 step
				gcodepainter.interrupt();
			}else if(pause == 0 ){
				//10 steps
				setCmd(Commands.STEP50X);
			}
		}else{
			if(pause != 0 && inpause){
				setCmd(Commands.STEPBACK);
			}else if(pause == 0 ){
				//10 steps
				setCmd(Commands.STEP50XBACK);
			}
		}
	}
	
	public synchronized void togglePrint(){
		pause=0; //disable pause
		if(printer!=null && !print){
		//	printer.setPrintMode(true);
			setCmd(Commands.PRINT);
		}else{
			//printer.setPrintMode(false);
			print=false;
			setCmd(Commands.RESTART);
		}
	}
	
	public synchronized void showHelp(){
		setCmd(Commands.HELP);
	}



	/**
	 * handover commands to the main thread. 
	 * Interrupt the main thread to interrupt sleeps and make sure commands are handled
	 * @param cmd to trigger in main thread
	 */
	public synchronized void setCmd(Commands cmd) {
		if(print) {
			//Only allow to toggle details
			if(cmd==Commands.REPAINTLABEL) this.cmd = cmd;		
			return ;
		}
		if(gcodepainter != null){
			gcodepainter.interrupt();
			this.cmd = cmd;		
		}
	}
	
	public int[] getSize(boolean details){
		return new int[]{ 
				(int) (bedSquare * getZoom() + gap + (details ? bedSquareZoomed/zoommod*2 : 0) ),
				(int) (bedSquare * getZoom() + (bedSquare * getZoom()/boxheightfactor))		
		};
	}

	public int getGap(){
		return gap;
	}

	/**
	 * Restart with new model from thread
	 * @param filename
	 */
	private void restart(final String filename){	
		//free memory
		model.getGcodes().clear();
		model.getLayer().clear();
		model=null;
		layers=null;
		System.gc();
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				start(filename,null,null);
			}
		}).start();
		
	}
	
	public synchronized void toggleType(){
		if(detailstype<7){
			detailstype++;
		}else {
			detailstype=0;
		}
		setCmd(Commands.REPAINTLABEL);
	}
	
	public void setPrintercon(Printer printercon) {
		this.printer = printercon;
	}
	
	/**
	 * Snapshot mode is for creating an image of the gcode file 
	 */
	public void setSnapshotMode(){
		snapshot=true;
	}
	
	/**
	 * Adjust ratio
	 * @param mod
	 */
	public void setZoomMod(float mod){
		zoommod=mod;
		zoomsmall= zoom/zoommod;
		setCmd(Commands.REPAINTLAYERS);
	}
	
	public void setBedIsRound(boolean round){
		roundbed=round;
		if(roundbed) bedsizeDiff=0; //no non-square round bed
	}

	int detailstype=0;
	
	public GcodePainter(GraphicRenderer g){
		this.g2=g;	
		colNR=g.getColorNr()-2; //-1 size vs index , -2 travel + border colors
	}
	
	public GcodePainter(GraphicRenderer g, boolean modeldetails, float zoomlevel,int bedx, int bedy, float zoomaspect){
		this(g);
		this.zoom=zoomlevel;
		gap=(int)(gap*zoom);
		bedSquare=Math.max(bedx, bedy);
		//bedsizeSquare=bedsizeSquare;
		bedsizeDiff= bedx-bedy; //ratio between bedsizex & bedsizey
		System.out.println("Diff:"+bedsizeDiff);
		
		zoommod=zoomaspect;		
		zoomsmall= zoom/zoommod;
		bedSquareZoomed = bedSquare*zoom;
		
	}

	private void calculateOffset() {
		//System.out.println("Bound:"+model.getBoundaries()[0]+"/"+model.getBoundaries()[1]);
		//Calculate offset
		if(model.getBoundaries()[0] >= bedSquare || model.getBoundaries()[1] <= 0 || extrazoom != 1){
			//67,139/2  =  0
			//250-150=200
			float midpoint = model.getBoundaries()[0]-(model.getDimension()[0]/2);
			Xoffset = (int)(bedSquare/2-midpoint);
		}else{
			Xoffset=0;
		}
		if(model.getBoundaries()[2] >= bedSquare || model.getBoundaries()[3] <= 0 || extrazoom != 1){
			//67,139/2  =  0
			//250-150=200
			float midpoint = model.getBoundaries()[2]-(model.getDimension()[1]/2);
			Yoffset = (int)(bedSquare/2-midpoint);
		}else{
			Yoffset=0;
		}
		

		
		if(roundbed){
			//zero is always in the center of the round bed
			Xoffset=bedSquare/2;
			Yoffset=bedSquare/2;
		}
		
		
		if(bedsizeDiff != 0){
			if(bedsizeDiff < 0){
				Xoffset=(bedSquare-(bedSquare+bedsizeDiff))/2;
				Yoffset=0;
			}else{
				Yoffset=(bedSquare-(bedSquare-bedsizeDiff))/2;
				Xoffset=0;
			}
		}
		
		//Experimental extra zoom
		if(extrazoom != 1){
			float midpoint = model.getBoundaries()[0]-(model.getDimension()[0]/2);
			System.out.println("Mid:"+midpoint);
			Xoffset = (int)(bedSquare/extrazoom /2-midpoint);
			
			float midpoint2 = model.getBoundaries()[2]-(model.getDimension()[1]/2);
			System.out.println(bedSquare/extrazoom/2+"sq  Mid2:"+midpoint2);
			Yoffset = (int)((bedSquare/extrazoom/2-midpoint2));
		}
		
		//System.out.println("X/Y Offset:"+Xoffset+"/"+Yoffset);
	}

	private void paintLabel(GraphicRenderer g2, Layer lay,GCode gc) {
		g2.setStroke(3);
		//On Layer change, skip for individual gcodes to save cycles
		if(gc == null){
			// Paint boxes with infos about current layer
			printLabelBox(g2, 0,12, Constants.inttoChar(lay.getNumber(),tempbuf), GraphicRenderer.LAYER,lay.getNumber());
			printLabelBox(g2, 12,20,Constants.floattoChar(lay.getZPosition(),tempbuf,2), GraphicRenderer.ZPOS,lay.getNumber());
			printLabelBox(g2, 32,12,Constants.ZERO_LABEL, GraphicRenderer.XYSPEED,lay.getNumber());
			printLabelBox(g2, 44,14,Constants.ZERO_LABEL, GraphicRenderer.ESPEED,lay.getNumber());
			printLabelBox(g2, 95,5,getFanSpeed(lay.getFanspeed()), GraphicRenderer.FAN,lay.getNumber());	
			
		}else{
			if(snapshot){
				printLabelBox(g2, 32,12,Constants.floattoChar(model.getAvgLayerHeight(),tempbuf,2), GraphicRenderer.LHEIGHT,lay.getNumber());
				printLabelBox(g2, 44,14,Constants.inttoChar(Math.round(model.getSpeed(Layer.Speed.SPEED_ALL_AVG)),tempbuf), GraphicRenderer.AVGSPEED,lay.getNumber());
				CharSequence cs = Constants.floattoChar(model.getDimension()[2],tempbuf,2);
				printLabelBox(g2, 12,20,cs, GraphicRenderer.ZHEIGHT,lay.getNumber());
			}else{
				printLabelBox(g2, 32,12,Constants.inttoChar(Math.round(gc.getSpeed()),tempbuf), GraphicRenderer.XYSPEED,lay.getNumber());
				printLabelBox(g2, 44,14,getExtrSpeed(gc), GraphicRenderer.ESPEED,lay.getNumber());
		
				//Z-Lift
				if(gc.isInitialized(Constants.Z_MASK)){
					CharSequence cs = Constants.floattoChar(gc.getZ(),tempbuf,2);
					printLabelBox(g2, 12,20,cs, GraphicRenderer.ZPOS,lay.getNumber());
				}
			}
			printLabelBox(g2, 95,5,getFanSpeed(gc.getFanspeed()), GraphicRenderer.FAN,lay.getNumber());
		}
		
		if(snapshot){
			//paint always the full print time
			int nr = Constants.formatTimetoHHMMSS(model.getTimeaccel(),tempbuf.getBytes());
			tempbuf.setlength(nr);
			printLabelBox(g2, 58,24, tempbuf, GraphicRenderer.PRINTTIME,lay.getNumber());
		}else{
			//paint the remaining time
			float rmtime = mtime;
			if(printer != null && printer.isPrinting() && printer.getPrintSpeed() != 100){
				rmtime = rmtime/printer.getPrintSpeed() *100;
			}
			int nr = Constants.formatTimetoHHMMSS(rmtime,tempbuf.getBytes());
			tempbuf.setlength(nr);
			printLabelBox(g2, 58,24, tempbuf, GraphicRenderer.RTIME,lay.getNumber());
		}

		if(print){
			if(gc!=null){
				//printLabelBox(g2, 82,12,gc.getLineindex()%2==0?"#":"##", "Print",lay.getNumber());
				printLabelBox(g2, 81,14,gc.getGcode().toString(), GraphicRenderer.PRINT,lay.getNumber());
			}else{
				printLabelBox(g2, 81,14,Constants.PRINTSTART_LABEL, GraphicRenderer.PRINT,lay.getNumber());
			}
		}else{
			if(snapshot){
				//Paint price				
				printLabelBox(g2, 81,14,Constants.floattoChar(model.getPrice(),tempbuf,1)+"€", GraphicRenderer.COST,lay.getNumber());
			}else{
				//Paint speedup
				printLabelBox(g2, 81,14,speeduplabel, GraphicRenderer.SPEEDUP,lay.getNumber());	
			}						
		}
		
		g2.setStroke(1);
//		if(pause == 0){

//		}else{
//			printLabelBox(g2, 78,13,"P", "Speedup",lay.getNumber());
//		}
		//printLabelBox(g2, 54,24, GCode.formatTimetoHHMMSS((useAccelTime?lay.getTimeAccel():lay.getTime())), "Layer Time",lay);
		//printLabelBox(g2, 32,22, String.valueOf(lay.getSpeed(Speed.SPEED_PRINT_AVG)), "Avg. Speed",lay);
	}

	private String getFanSpeed(float fanf) {
		if(fanf== Short.MAX_VALUE){
			fanf=fanspeed;
		}else{
			fanspeed=fanf;
		}
		if(fanf==0){
			return Constants.FANS0_LABEL;
		}else if (fanf<100){
			return Constants.FANS1_LABEL;
		}else if (fanf<200){
			return Constants.FANS2_LABEL;
		}
		return Constants.FANS3_LABEL;
	}

	private void printDetails(GraphicRenderer g2, Layer lay) {
		//g2.setColor(lay.getNumber() % colNR);
		g2.setColor(colNR);
		float boxheight=(bedSquareZoomed)/boxheightfactor;
		//int linegap=(int)((4*zoom)+3.2f);
		float size=2.5f+(3.10f*zoom)/200*bedSquare;
		int linegap=(int)size;
		g2.setFontSize(size);
		
		// bed
		//details.setLength(0);
		String[] det = null;
		int start=0;
		switch (detailstype) {
		case 0:
			det=modeldetails;
			break;
		case 1:
			StringBuilder tmpbuf = new StringBuilder();
			tmpbuf.append("--------------- Layer details ------------------\n");
			tmpbuf.append(lay.getLayerDetailReport());
			det=tmpbuf.toString().split("\n");
			break;
		case 2:
		case 3:
		case 4:
			det=modellaysum;
			String[] lines = modellaysum;
			if(lines.length*linegap+30 >= bedSquareZoomed+boxheight -(bedSquareZoomed/zoommod) ){
				start=(int) (((bedSquareZoomed+boxheight -linegap -(bedSquareZoomed/zoommod) )/linegap)+1)*(detailstype-2); //TODO calculate number
				//System.out.println("START:"+start);
				break;
			}
			detailstype=5; //skip
			det=null;
		case 5:
			det=modelspeed;
			break;
		case 6:
			det=lay.getLayerSpeedReport().split("\n");
			break;
		case 7:
			det=modelcomments;
			if(det.length != 0) break;
		default:
			det=modeldetails;
			break;
		}
		g2.clearrect(bedSquareZoomed+gap+5,(bedSquareZoomed/zoommod)+2+header, (bedSquareZoomed/zoommod*2)-7, bedSquareZoomed + boxheight -(bedSquareZoomed/zoommod)-82 ,print?1:0);
		
		
		int c=0;
		for (int i = start; i < det.length; i++) {
			int y = (int)(bedSquareZoomed/zoommod)+header+3+linegap+c*linegap; //avoid painting across the border
			c++;
			if(y+30 >= bedSquareZoomed+boxheight) break;
			
			g2.drawtext(det[i], bedSquareZoomed+gap+8,y );
			
		}
	}

	private void printLabelBox(GraphicRenderer g2, int boxposp,int bsizepercent, CharSequence value, int labl,int laynr) {
		float boxsize=((bedSquareZoomed+gap)/100)*bsizepercent;
		float boxpos=((bedSquareZoomed+gap)/100)*boxposp;
		float boxheight=(bedSquareZoomed)/boxheightfactor;
		float gapz=zoom;
		float size=10.15f*(zoom)/200*bedSquare;
		g2.clearrect(boxpos,bedSquareZoomed, boxsize,boxheight+2,2);
		//g2.clearrect(boxpos+2,bedsizeSquare*zoom+2, boxsize-3,boxheight-1,print?1:0);
		g2.setColor(colNR+1);//white
		g2.drawrect(boxpos,bedSquareZoomed, boxsize,boxheight+2);
		//g2.setColor(laynr % colNR);
		g2.setColor(colNR);//white
		g2.setFontSize(size);
		g2.drawtext(value, boxpos, bedSquareZoomed+size-gapz,boxsize);
		g2.setFontSize(size/3);
		g2.drawtext(labl, boxpos, bedSquareZoomed+boxheight-2-size/3,boxsize);
	}

	private void paintLoading(GraphicRenderer g) {
		g.setColor(colNR);
		g.setFontSize(40);
		
		g.drawtext("Please Wait",100,200);
		if(model != null){
			g.drawtext("Loading Model "+model.getFilenameShort(),100,270);
			g.clearrect(200,285,500,75,print?1:0);
			if(errormsg!=null){
				g.setFontSize(20);
				String[] errlines =  errormsg.split("\n");
				for (int i = 0; i < errlines.length; i++) {
					g.drawtext(errlines[i],100,340+i*30);
				}
				
			}else{
				if(model.getFilesize() >0){
					g.drawtext(Constants.round2digits((model.getReadbytes()/(model.getFilesize()/100f)))+"%  ("+model.getReadLines()+")",220,340);
				}else{
					g.drawtext(model.getReadLines()+"",220,340);
				}
			}
				
		}
		g.setFontSize(11.5f);
		}
	
	private void paintHelp(GraphicRenderer g) {
		g.setColor(0);
		
		float boxsize = (bedSquareZoomed)/2.5f;
		float xbox=(bedSquareZoomed)/2-boxsize/2;
		float ybox=(bedSquareZoomed)/2-boxsize/2;
		g.fillrect(xbox,ybox , boxsize+30, boxsize+109);
		g.drawrect(xbox-3,ybox-3 , boxsize+36, boxsize+115);
		float size=5.5f+(3.10f*zoom)/200*bedSquare;
		float gap =(int)size;
		g.setColor(colNR);
		
		g2.drawtext("Gcode Simulator "+GcodeSimulator.VERSION,xbox+3 , ybox+gap);
		g2.drawtext("________________________",xbox+3 , ybox+gap+1);
		g2.drawtext("Author: Mathias Dietz (gcode@dietzm.de)",xbox+3 , ybox+gap*2);
		g2.drawtext("Homepage: http://gcodesim.dietzm.de",xbox+3 , ybox+gap*3);
		
		g2.drawtext("Key Shortcuts Help: ",xbox+3 , ybox+gap*4+3);
		g2.drawtext("________________________",xbox+3 , ybox+gap*4+4);
		g2.drawtext("+/- = Speed up/down",xbox+3 , ybox+gap*5);
		g2.drawtext("//* = 10x Speed up/down",xbox+3 , ybox+gap*6);
		g2.drawtext("i/o = Zoom in/out",xbox+3 , ybox+gap*7);
		g2.drawtext("n/b = Layer next/back",xbox+3 , ybox+gap*8);
		g2.drawtext("m   = Show Model Details",xbox+3 , ybox+gap*9);
		g2.drawtext("t   = Toggle Model Details",xbox+3 , ybox+gap*10);		
		g2.drawtext("f   = Load Gcode File",xbox+3 , ybox+gap*11);
		g2.drawtext("p/r/q = Pause / Restart / Quit",xbox+3 , ybox+gap*12);
		g2.drawtext("space/backspace = Fast forward/back",xbox+3 , ybox+gap*13);
		g2.drawtext("space/backspace = Step forward/back (Pause)",xbox+3 , ybox+gap*14);
		
		
		
		g2.drawtext("Mouse Shortcuts Help: ",xbox+3 , ybox+gap*15+3);
		g2.drawtext("________________________",xbox+3 , ybox+gap*15+4);
		g2.drawtext("Mousewheel = Speed up/down",xbox+3 , ybox+gap*16);
		g2.drawtext("ALT+Mousewheel = Zoom in/out",xbox+3 , ybox+gap*17);
		g2.drawtext("Left Button on Bed = Next Layer",xbox+3 , ybox+gap*18);
		g2.drawtext("ALT+Left Button on Bed = Previous Layer",xbox+3 , ybox+gap*19);
		g2.drawtext("Right Button = Show Model Details",xbox+3 , ybox+gap*20);
		g2.drawtext("Left Button on Details = Toggle Model Details",xbox+3 , ybox+gap*21);
		g2.drawtext("Middle Button = Show Help",xbox+3 , ybox+gap*22);
		
		
		
		}

	private void printBed(GraphicRenderer g2) {
		float rightofBed=bedSquareZoomed + gap;
		float bedsquarezoom=bedSquareZoomed;
		float zoomsmall= zoom/zoommod;
		float boxsize=bedSquare*zoomsmall; //size of front view or side view box
		
		int rectback =  print?1:0;
		int roundback = rectback;
		if(roundbed || bedsizeDiff != 0){
			rectback = print?0:1; //invert the background color
		}
		//bed and side view
		g2.clearrect(0, 0, g2.getWidth(),g2.getHeight(),roundback);
		//Bed only
		g2.clearrect(0, 0, bedsquarezoom ,bedsquarezoom ,rectback);
		
		g2.setColor(colNR+1);
		g2.setStroke(3);
		g2.drawrect(0, 0, bedsquarezoom, bedsquarezoom); // Draw print bed
		
		
		g2.clearrect(bedsquarezoom, 0,  gap, bedsquarezoom,2); //level bar Color 
		g2.drawrect(bedsquarezoom , 0, gap, bedsquarezoom); // Draw level bar border
		
		
		//Draw box for modeldetails , front view and side view
		//Front & side view boxes
	
		g2.clearrect(rightofBed, 0, (boxsize)*2, header,2); //Label background side&font
		g2.drawrect(rightofBed, 0, boxsize, header);//Label background border
		g2.drawrect(rightofBed +boxsize , 0, boxsize, header);//Label background border
		
		g2.drawrect(rightofBed, 0, boxsize, boxsize);
		g2.drawrect(rightofBed +boxsize , 0, boxsize, boxsize);

		//full modeldetails box
		float boxheight=(bedsquarezoom)/boxheightfactor;
		g2.clearrect(rightofBed, boxsize, (boxsize)*2, header,2); //Label background modeldetails
		g2.drawrect(rightofBed, boxsize, boxsize*2, header);//Label background border
		g2.drawrect(rightofBed, 0,  boxsize*2, bedsquarezoom+boxheight+2); // Draw print		
		
		
		//Title for front view / side view and model details		
		float fsize=2+5f*(zoom)/200*bedSquare;
		g2.setFontSize(fsize);
		g2.setColor(colNR);
		g2.drawtext(GraphicRenderer.FRONTVIEW, rightofBed+boxsize,fsize+3,boxsize);
		g2.drawtext(GraphicRenderer.SIDEVIEW, rightofBed,fsize+3,boxsize);
		g2.drawtext(GraphicRenderer.DETAILS, rightofBed,boxsize+fsize+5,boxsize*2);

		//Unten rechts
//		g2.setFontSize(fsize*2);
//		g2.drawtext("3D", bedsquarezoom*0.9f+fsize,bedsquarezoom-fsize);
//		g2.setStroke(2);
		//Oben zwischen side/front
//		g2.setFontSize(fsize*2.5f);
//		g2.clearrect(rightofBed+boxsize-fsize*1.5f,2,fsize*3f+2,fsize*2f,1);
//		g2.drawtext("3D", rightofBed+boxsize-fsize*1.5f,fsize*2);
//		g2.drawrect(rightofBed+boxsize-fsize*1.5f,2,fsize*3f+2,fsize*2f);
//		g2.setStroke(2);
		
	//	g2.drawrect(bedsquarezoom-fsize*2.5f,2,fsize*2.5f,fsize*2.5f);
//		g2.setStroke(1);
//		g2.clearrect(bedSquareZoomed+gap+1,bedSquareZoomed + (bedSquareZoomed)/24f, 140, (bedSquareZoomed)/16f,2);
//		g2.drawrect(bedSquareZoomed+gap+4,bedSquareZoomed + (bedSquareZoomed)/24f +2, 138, (bedSquareZoomed)/16f -5);
//		g2.drawtext("Open 3D View", bedSquareZoomed+gap+10, bedSquareZoomed +(bedSquareZoomed)/24f +  (bedSquareZoomed)/48f +12 );
		
		//Draw circle for round bed
		if(roundbed && extrazoom == 1){
			g2.setStroke(0);
			g2.fillArc(0, 0, (int)(bedsquarezoom), (int)(bedsquarezoom), 0,360,roundback);
			g2.setColor(colNR);
			g2.drawArc(0, 0, (int)(bedsquarezoom), (int)(bedsquarezoom), 0,360);
		}

		//non square bed sizes
		float xBorder = 0;
		float yBorder =0;
		if(bedsizeDiff != 0 && extrazoom == 1){
			//Bed only
			g2.setStroke(1);
			if(bedsizeDiff < 0){
				//Larger Y 
				xBorder = (bedSquare-(bedSquare+bedsizeDiff))/2* zoom;
				g2.clearrect(xBorder, 0 ,(bedSquare+bedsizeDiff) * zoom,bedsquarezoom ,roundback);
				g2.drawrect(xBorder, 0 ,(bedSquare+bedsizeDiff) * zoom,bedsquarezoom );
			}else{
				//Larger X
				yBorder = (bedSquare-(bedSquare-bedsizeDiff))/2 * zoom;
				g2.clearrect(0, yBorder, bedsquarezoom ,(bedSquare-bedsizeDiff) * zoom ,roundback);
				g2.drawrect(0, yBorder, bedsquarezoom ,(bedSquare-bedsizeDiff) * zoom );
			}
			
		}
		
		//Draw grid
		g2.setStroke(2);
		g2.setColor(colNR);
		int stepsize = (int)(10*extrazoom);
		if(bedSquare >= 1000) stepsize=50;
		for (int i = stepsize; i < bedSquare; i=i+stepsize) {
			if((i*zoom) < (bedsquarezoom-xBorder*2)) g2.drawline(xBorder+i*zoom,yBorder+0,xBorder+i*zoom,bedsquarezoom-yBorder);
			if((i*zoom) < (bedsquarezoom-yBorder*2)) g2.drawline(xBorder+0,yBorder+i*zoom,bedsquarezoom-xBorder,yBorder+i*zoom);	
		}
		
		
		//draw front & side grid
		g2.drawline(rightofBed, boxsize, rightofBed +60, boxsize-60);
		g2.drawline(rightofBed+boxsize, boxsize, rightofBed +boxsize -60, boxsize-60);
		

	
		//Draw zero position on bed
		if(Xoffset != 0 || Yoffset != 0){
			g2.setStroke(0);
			g2.setColor(colNR);
			g2.drawline(Xoffset*zoom-10, Yoffset*zoom, Xoffset*zoom+100, Yoffset*zoom);
			g2.drawline(Xoffset*zoom, Yoffset*zoom+10, Xoffset*zoom, Yoffset*zoom-100);
			g2.setStroke(1);
		}
	
	
	}

	private void paintLevelBar(GraphicRenderer g2, Layer lay) {
		//Print level bar
		g2.setStroke(1);
		g2.setColor(lay.getNumber() % colNR);
		float factor = (bedSquareZoomed - 2) / (model.getDimension()[2] * 10);
		g2.fillrect(bedSquareZoomed, bedSquareZoomed - (int) ((lay.getZPosition() * 10) * factor), gap,
				(int) (lay.getLayerheight() * 10 * factor)); // Draw level indicator
	}

	private float printLine(GraphicRenderer g2, Position lastpos, Position pos, float zpos, float time,long starttime,boolean travel) {

		float x1 = Math.min((lastpos.x + Xoffset) * zoom,bedSquareZoomed);
		float y1 = (bedSquare/extrazoom * zoom) - ((lastpos.y + Yoffset) * zoom);
		float x2 = Math.min((pos.x + Xoffset) * zoom, bedSquareZoomed);
		float y2 = (bedSquare/extrazoom * zoom) - ((pos.y + Yoffset) * zoom);

		if (!ffLayer && fftoGcode == 0 && fftoLayer == 0 && speedup < 10 ) {
			// Instead of painting one long line and wait , we split the line
			// into segments to have a smoother painting
			float distx = x2 - x1;
			float disty = y2 - y1;
			int maxdist = (int) Math.max(Math.abs(distx), Math.abs(disty)) / 5; // paint
																				// each
																				// 5mm
			if (maxdist > 5) {
				float stepx = distx / maxdist;
				float stepy = disty / maxdist;
				for (int i = 0; i < maxdist; i++) {
					float nx=(x1 + ((i + 1) * stepx));
					float lx=x1 + (i * stepx);
					float ly=y1 + (i * stepy);
					float ny=( y1+ ((i + 1) * stepy));
					
					g2.setPos((int)(nx*extrazoom),(int)(ny*extrazoom));
					g2.drawline(lx*extrazoom, ly*extrazoom, nx*extrazoom, ny*extrazoom);
					printLineHorizontal2(g2,lx*extrazoom,ly*extrazoom,nx*extrazoom,ny*extrazoom, zpos*extrazoom,travel);
					try {
						g2.repaint();
						long paintdur=System.currentTimeMillis()-starttime;
						long sleepremain = Math.max(0,((long)time)-paintdur);
						long dosleep = Math.min(sleepremain, (long)(time / maxdist));
						Thread.sleep(dosleep ); // pause not done
					} catch (InterruptedException e) {
						time = 0;
					}
				}
				return 0; // already slept enough , no sleep in main loop
			}
		}

		g2.drawline(x1*extrazoom, y1*extrazoom, x2*extrazoom, y2*extrazoom);
		printLineHorizontal2(g2,x1*extrazoom,y1*extrazoom,x2*extrazoom,y2*extrazoom, zpos*extrazoom,travel);
		return time; // not slept, sleep in the main loop
	}
	
	private float printArc(GraphicRenderer g2,Position lastpos,Position pos, Layer lay,GCode gc){
		
		//center I&J relative to x&y
		float cx = (lastpos.x+gc.getIx());
		float cy = (lastpos.y+gc.getJy());
	
		//triangle
		float bx=(pos.x-cx);
		float by=(pos.y-cy);
		float ax=(lastpos.x-cx);
		float ay=(lastpos.y-cy);
		//Java drawarc is based on a bonding box
		//Left upper edge of the bounding box
		float xmove = Math.abs(cx-lastpos.x);
		float ymove = Math.abs(cy-lastpos.y);
		//assume a circle (no oval)
		float radius = ((float) Math.sqrt((xmove * xmove) + (ymove * ymove)));
		double angle1 ,angle2 ;
		//Calculate right angle
		if(gc.getGcode() == Constants.GCDEF.G2){
			angle1 = Math.atan2(by,bx) * (180/Math.PI);
			angle2 = Math.atan2(ay,ax) * (180/Math.PI);
		}else{
			angle2 = Math.atan2(by,bx) * (180/Math.PI);
			angle1 = Math.atan2(ay,ax) * (180/Math.PI);
		}
		double angle=(int) (angle2-angle1);
		
		//Bogenlaenge
		//double length = Math.PI * radius * angle / 180;
		
		//Upper top bounding box edge needed for drawArc()
		float utx = cx - radius;
		float uty = cy + radius;
		
		//Debug
//		System.out.println("aX:"+ax);
//		System.out.println("aY:"+ay);
//		System.out.println("bX:"+bx);
//		System.out.println("bY:"+by);
//		System.out.println("STARTPOS: X"+lastpos[0]+" Y"+lastpos[1]+" Gcode:"+gc.getCodeline());
//		System.out.println("ENDPOS: X"+pos[0]+" Y"+pos[1]);
//		System.out.println("Zoom:"+zoom);
//		System.out.println("Center CX:"+cx+" CY:"+cy);
//		System.out.println("Upper-top UTX:"+utx+" UTY:"+uty);
//		System.out.println("RAD:"+radius);
//		
//		System.out.println("A1:"+angle1+" A2:"+angle2);
//		System.out.println("Offset:"+Xoffset+" "+Yoffset);
//		System.out.println("Length:"+length);
		
	//	g2.setColor(2);	
	//	g2.drawrect(((cx+Xoffset)*zoom), (bedsizeSquare * zoom) -((cy+Yoffset)*zoom), (int)1, (int)1);
	//	g2.setColor(3);
	//	g2.drawrect(((utx+Xoffset)*zoom),(bedsizeSquare * zoom) - ((uty+Yoffset)*zoom), (int)radius*2*zoom, (int)radius*2*zoom);
		g2.drawArc((int)((utx+Xoffset)*zoom *extrazoom), (int)((bedSquareZoomed) -((uty+Yoffset)*zoom*extrazoom)), (int)(radius*2*zoom*extrazoom), (int)(radius*2*zoom*extrazoom), (int)angle1,(int)angle);
		//TODO: split arc in multiple sections to have a smoother paint
		return 0;
	}
	
	
	
	
	private void printLineHorizontal(GraphicRenderer g2, Position lastpos, Position pos, float zpos) {
		//TODO save some CPU cycles by remembering what has been drawn already on this layer and avoid redraw
		float boxsize = bedSquare *zoomsmall;
		float rightofBed=bedSquareZoomed + gap;
//		System.out.println("--STARTPOS: X"+lastpos.x+" Y"+lastpos.y+" Gcode:"+gc.getCodeline());
//		System.out.println("--ENDPOS: X"+pos.x+" Y"+pos.y);
//		System.out.println("Offset:"+Xoffset+" "+Yoffset+" Zoom:"+zoom +" small:"+zoomsmall);
		float z =  (boxsize) - (zpos*zoomsmall) - 20 ;		
		/*
		 * Print side view
		 */
		float y1 = (boxsize) - ((lastpos.y + Yoffset) * zoomsmall)+ (rightofBed);		
		float y2 = (boxsize) - ((pos.y + Yoffset) * zoomsmall)+ (rightofBed);
		g2.drawline(y1, z, y2, z);
		
		/*
		 * Print front view
		 */
		float x1 = ((lastpos.x + Xoffset) * zoomsmall + (rightofBed))+(boxsize) ;
		float x2 = ((pos.x + Xoffset) * zoomsmall+ (rightofBed))+(boxsize);
		g2.drawline(x1, z, x2,z);
		
		g2.setPos((int)y1, (int)x1, (int)z);
		//Print Extrusion		
//		if(gc.getExtrusion() > 0){		
//			float xex = gc.getExtrusion()/gc.getDistance()*10000;
//			System.out.println("extru:"+xex);
//		g2.drawline((bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall)), 
//				(bedsizeSquare *zoomsmall)-22-xex, 
//				(bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall))
//				,(bedsizeSquare * zoomsmall)-20-xex);
//		}else if(gc.getExtrusion() < 0){
//			float xex = gc.getExtrusion()*10;
//			g2.setColor(1);
//			System.out.println("restract:"+xex);
//			g2.drawline((bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall)), 
//					(bedsizeSquare *zoomsmall)-22-xex, 
//					(bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall))
//					,(bedsizeSquare * zoomsmall)-20-(xex));
//		}
//		
		//Print speed
		//g2.drawline((bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall)), 
//				(bedsizeSquare *zoomsmall)-22-gc.getSpeed(), 
//				(bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall))
//				,(bedsizeSquare * zoomsmall)-20-gc.getSpeed());
			
	}
	
	private void printLineHorizontal2(GraphicRenderer g2,float lx,float ly,float x, float y, float zpos,boolean travel) {
		//TODO save some CPU cycles by remembering what has been drawn already on this layer and avoid redraw
		float boxsize = bedSquare *zoomsmall;
		float rightofBed=bedSquareZoomed + gap;
		float z =  (boxsize) - (zpos*zoomsmall) - 20 ;		
		/*
		 * Print side view
		 */
		float y1 = (boxsize) - ((ly / zoom) * zoomsmall)+ (rightofBed);		
		float y2 = (boxsize) - ((y / zoom) * zoomsmall)+ (rightofBed);
		if(!travel){
			g2.drawline(y1, z, y2, z);
		}
		/*
		 * Print front view
		 */
		float x1 = ((lx / zoom) * zoomsmall + (rightofBed))+(boxsize) ;
		float x2 = ((x / zoom) * zoomsmall+ (rightofBed))+(boxsize);

		if(!travel){ //do not paint travel
			g2.drawline(x1, z, x2,z);
		}
		g2.setPos((int)y1, (int)x1, (int)z);
	}


	
	
	@Override
	/**
	 * Paint the gcodes to a offline image (offbuf) and trigger repaint
	 */
	public void run() {
		Thread.currentThread().setName("GcodePainter");
		g2.clearrect(0, 0, g2.getWidth(), g2.getHeight(),print?1:0);
		g2.repaint();
		try{
		A: while (true) {
			if (layers != null) {
				if ( model.getGcodecount() < 50) pause = 999999; //Activate pause when small file is loaded to prevent fast loops
				Position pos = new Position(0,0);
				Position lastpos = new Position(0,0);
				fanspeed=0;
				updateDetailLabels();
				updateSpeedupLabel();
				
				if(model.getExtruderCount() > 1){
					//only paint multiple extruder if model makes use of them
					g2.setExtruderOffset(extruderOffset,zoom);
				}else{
					g2.setExtruderOffset(null,zoom);
				}
				calculateOffset();
				mtime = model.getTimeaccel(); //remaining time
				printBed(g2);
				if(print){
					g2.setTitle("Print Mode");
				}else{
					g2.setTitle("Simulation");
				}
				 if(layers == null) return;
				 for (Layer lay : layers) {
					//System.out.println("Layer #"+lay.getNumber()+"  low:"+lay.lowidx+" high:"+lay.highidx);
					if(oneLayerAtATime){
						g2.clearrect(2, 2, bedSquareZoomed-2, bedSquareZoomed,print?1:0);
						printBed(g2);
					}else{
						//Fading the previous layer
						if(fftoLayer == 0 || fftoLayer <= lay.getNumber()+10){
							g2.faintRect(2, 2, bedSquareZoomed+gap, bedSquareZoomed);
						}
					}
						
					currentlayer=lay;
					ffLayer=false;	
					sideList=new float[6];
					printDetails(g2, lay);
					paintLevelBar(g2, lay);
					paintLabel(g2, lay,null);
					
										
					// Print & Paint all Gcodes
					if(model == null) return;
					//Android guidelines say foreach loops are slower for arraylist
					GCodeStore gcarr = model.getGcodes();					
					//int gcnum = gcarr.size();
					float zpos=lay.getZPosition();
					GCDEF gcdef;
					for(int ig = lay.lowidx ; ig <= lay.highidx; ig++ ){
					//	System.out.println("IDX:"+ig+"  ->"+lay.highidx);
						if(ig == -1) continue A;
						GCode gCode = gcarr.get(ig);
						gcdef=gCode.getGcode();
						/*
						 * Painting starts here
						 */
						if(gcdef == Constants.GCDEF.UNKNOWN || gcdef == Constants.GCDEF.COMMENT){
							if(fftoGcode != 0 && fftoGcode == ig){
								fftoGcode=0;
								System.out.println("Jump to Gcode:"+ig+" took "+ (System.currentTimeMillis()-ffstarttime));
							}//make sure to reset fftogcode even if it is a comment (loop hang)
							continue;
						}
						
						if(gcdef == Constants.GCDEF.T0){
							activeExtruder=0;
							g2.setActiveExtruder(activeExtruder);
						}else if(gcdef == Constants.GCDEF.T1){
							activeExtruder=1;
							g2.setActiveExtruder(activeExtruder);
						}else if(gcdef == Constants.GCDEF.T2){
							activeExtruder=2;
							g2.setActiveExtruder(activeExtruder);
						}else if(gcdef == Constants.GCDEF.T3){
							activeExtruder=3;
							g2.setActiveExtruder(activeExtruder);
						}
						
						//System.out.println(gCode);
						long starttime=System.currentTimeMillis();
						float sleeptime =0;
						//Paint label and calculate set gcode time as sleeptime (simulation)
						//has to be synchronized to avoid flickering
						synchronized(g2){ 
							if(!ffLayer && fftoGcode==0 && fftoLayer==0){
								paintLabel(g2, lay, gCode);
								sleeptime = (gCode.getTimeAccel()* 1000) / speedup;
							}
						}
						/*
						 * Printing
						 * If printing is enabled synchronize rendering with the print controller
						 */
						alignWithPrint(lay, gCode,ig);
						
						//Print the lines from last position to current position
						if(gCode.getCurrentPosition(pos) != null) {
							if(applyOffset && extruderOffset[activeExtruder] != null){
								pos.applyOffset(extruderOffset[activeExtruder]);
							}
							//System.out.println("XXXXX"+gCode);
			
						g2.setPos((int)((pos.x+Xoffset)*zoom*extrazoom), (int)((bedSquareZoomed) - ((pos.y+Yoffset)*zoom*extrazoom )));
						if (lastpos != null) {
							if (gCode.isExtruding()) { //TODO Add all_extrude for CNC
								g2.setColor(lay.getNumber() % colNR);		
								if(gcdef == Constants.GCDEF.G2 ||gcdef == Constants.GCDEF.G3 ){
									printArc(g2, lastpos, pos, lay, gCode);
									printLineHorizontal(g2,lastpos,pos,zpos);
								}else{
									printLine(g2, lastpos, pos,zpos,sleeptime,starttime,false);
								}
							} else if (painttravel != Travel.NOT_PAINTED) {
								if(painttravel == Travel.DOTTED || gcdef== Constants.GCDEF.G0){
									g2.setColor(colNR+1);
									g2.setStroke(0);
								}else{//Paint travel full (CNC)
									g2.setColor(lay.getNumber() % colNR);	
								}
								if(gcdef == Constants.GCDEF.G2 ||gcdef == Constants.GCDEF.G3 ){
									printArc(g2, lastpos, pos, lay, gCode);
								}else{
									printLine(g2, lastpos, pos,zpos,sleeptime,starttime,true);
								}
								g2.setStroke(1);
							}
						}
						}
						mtime=mtime-gCode.getTimeAccel();
						lastpos.updatePos(pos);			
						
						/*
						 * POST PROCESSING STARTS HERE
						 */						
						// Sleep for the remaining time up to the gcode time & handle commands
						try {
							/**
							 * Handle a user command
							 */
							int skip = handleCommand(gCode, lay,ig);
							if(skip==1) continue A;
							if(skip==2) return;
					
							
							if(!ffLayer && fftoGcode==0 && fftoLayer==0 ){
								g2.repaint();
								long paintdur=System.currentTimeMillis()-starttime;
								long sleep = Math.max(0,((long)sleeptime)-paintdur);
								Thread.sleep((int)sleep);

								if(pause != 0){
									doPause(lay, gCode,ig);
								}
								
							}else{
								if(fftoGcode != 0 && fftoGcode == ig){
									fftoGcode=0;
									System.out.println("Jump to Gcode:"+ig+" took "+ (System.currentTimeMillis()-ffstarttime));
								}
								if(fftoLayer != 0 && fftoLayer == lay.getNumber()){
									fftoLayer=0;
									norepaint=false;
								}
							}
							
						} catch (InterruptedException e) {
							inpause=false;
							g2.clearrect(bedSquareZoomed+gap+1,bedSquareZoomed + (bedSquareZoomed)/24f, (bedSquareZoomed/zoommod*2)-2, (bedSquareZoomed)/24f,print?1:0);
						}
							
					}//ForGCodes

					if(fftoLayer != 0 && !norepaint){
						//Repaint once a layer is done
						g2.repaint();
					}
				}//ForLayers
			}else{ //If layer==null
				g2.setTitle(null);
				g2.setPos(0, 0);
				paintLoading(g2);
				if(cmd==Commands.EXIT){
					cmd = Commands.NOOP;
					return;
				}
				g2.repaint();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
		//	System.out.println("FINAL");
			if(print){
				System.out.println("Print finished.");
				try {
					pause=999999;
					g2.setFontSize(40);
					g2.drawtext("Print Completed",100,200);
					inpause=true;
					togglePrint();
					g2.repaint();					
					Thread.sleep(pause); //allow pause also if printing			
				} catch (InterruptedException e) {}
				inpause=false;
				
			}
		
		}
		}catch(NullPointerException npe){
			System.err.println("NPE in GCodePrintr:"+npe);
			return;
		}
	}

	private void updateDetailLabels() {
		
		StringBuilder tmpbuf = new StringBuilder();
		
		//tmpbuf.append("--------------- Model details ------------------\n");
		tmpbuf.append(model.getModelDetailReport());
		tmpbuf.append(model.getFilamentReport());
		modeldetails=tmpbuf.toString().split("\n");
		modelspeed= model.getModelSpeedReport().split("\n");
		modelcomments = model.getModelComments().split("\n");
		modellaysum=model.getModelLayerSummaryReport().split("\n");
		
	}

	/**
	 * Render gcodes as in normal simulation mode, set speedup to 1
	 * Synchronization is done based on line index numbers.
	 * 
	 * Rendering should lag ~6 G-gcodes behind because of the printer buffer
	 * M-gcodes will not be buffered.
	 * 
	 * when render.index is >= print.index-buffer (current gcode = G) wait +
	 * reset speedup when render.index is >= print.index (current gcode = M)
	 * wait + reset speedup when render.index < print.index-10 increase speedup
	 * +1 when render.index < print.index-100 fftogcode-buffer
	 * 
	 * @param lay
	 * @param gCode
	 */
	private void alignWithPrint(Layer lay, GCode renderCode, int lineidx) {
		if (printer == null || (!printer.isPrinting() && !print) )
			return;
		if(pause == 0 && printer.isPause()){ //inconsistency 
			togglePause();
		}
		if (!print && printer.isPrinting()) {
			togglePrint(); // enable printing
			speedup=1;
		}

		
		//int defbuffersize = 15;
		int maxbehind = 35;
		int behind = defbuffersize+10;
		

		while (print && printer.isPrinting()) {
			try {
				//option to supress rendering while printing to save resources
				if(!paintWhilePrint){
					//paint second layer and wait
					if(model.getLayercount(false) > 2 && lay.getNumber() < 2){
						fftoLayer=2;
						return;
					}
					printLabelBox(g2, 82,12,"W", GraphicRenderer.WAIT,lay.getNumber());
					g2.repaint();
					while(printer.isPrinting()){
						Thread.sleep(2500); //check every 2.5 sec if still printing
					}
				}
				boolean wait = false;
				GCode printCode = printer.getCurrentGCode(); //get what has been send to the printer last
				int printlineidx = printer.getCurrentLine();
				if(printCode == null) continue;
				//TODO: when end of file is reached
				inbuffer=Math.min(defbuffersize, printlineidx-bufferemptyindex); //Slowly fill buffer buffer
				if (printCode.isBuffered()	&& lineidx >= printlineidx - inbuffer) {
					wait = true;
					speedup=1;
				} else if (!printCode.isBuffered() && lineidx >= printlineidx) {
					wait = true;
					bufferemptyindex=printlineidx;
					speedup=1;
				}

				if (lineidx < printlineidx - maxbehind) {
					if (printCode.isBuffered()) {
						fftoGcode = printlineidx - inbuffer;
					} else {
						fftoGcode =printlineidx;
					}
					//System.out.println("AlignGCode: ff:"+fftoGcode);
					wait = false;
				} else if (lineidx < printlineidx - behind) {
					speedup++;
					//System.out.println("AlignGCode:Speedup:"+speedup);
					wait = false;
				}

				if (pause != 0) {
					bufferemptyindex=printlineidx;
					doPause(lay, renderCode,lineidx);
					printLabelBox(g2, 82,12,"W", GraphicRenderer.WAIT,lay.getNumber());
					g2.repaint();
				} else if (wait) {
					//System.out.println("AlignGCode: WAIT "+printCode.isBuffered()+"  PR:"+(printCode.getLineindex()-renderCode.getLineindex())+" BF:"+inbuffer);
					g2.repaint();
					Thread.sleep(100);
				} else {
					break; //no wait 
				}
			} catch (InterruptedException e) {
			//	e.printStackTrace();
				printLabelBox(g2, 82,12,"W", GraphicRenderer.WAIT,lay.getNumber());
				g2.repaint();
			}

		}
		// Turn off print mode
		if (print && !printer.isPrinting()) {
			togglePrint();
		}
	}

	private void doPause(Layer lay, GCode gCode, int lineidx) throws InterruptedException {
		inpause=true;
		if(!snapshot){
			printLabelBox(g2, 82,12,"P", GraphicRenderer.PAUSE,lay.getNumber());
			g2.clearrect(bedSquareZoomed+gap+1,bedSquareZoomed + (bedSquareZoomed)/24f, (bedSquareZoomed/zoommod*2)-2, (bedSquareZoomed)/24f,print?1:0);
			g2.drawrect(bedSquareZoomed+gap+4,bedSquareZoomed + (bedSquareZoomed)/24f +2, (bedSquareZoomed/zoommod*2)-7, (bedSquareZoomed)/24f -5);
			g2.drawtext("L"+lineidx+": "+ gCode.getCodeline().toString().trim(), bedSquareZoomed+gap+10, bedSquareZoomed +(bedSquareZoomed)/24f +  (bedSquareZoomed)/48f +1 );
		}
		g2.repaint();
		Thread.sleep(pause); //allow pause also if printing			
		inpause=false;
	}

	private CharSequence getExtrSpeed(GCode gCode) {
		int exspeed = Math.round(gCode.getExtrusionSpeed());
		CharSequence exvar = exspeed >=0 ? Constants.inttoChar(exspeed,tempbuf) : Constants.RETRACT_LABEL;
		return exvar;
	}
	
	/**
	 * Handle a command
	 * return true if jump back to layer loop
	 * @param gCode
	 * @param lay
	 * @return return true if jump back to layer loop
	 */
	private synchronized int handleCommand(GCode gCode, Layer lay, int lineidx){
		if(cmd==Commands.NOOP) return 0;
		
		Thread.interrupted(); //reset interrupted state to avoid problems with file dialog
		switch (cmd) {
		case EXIT:
			cmd = Commands.NOOP;
			layers = null;
			return 2;
		case OPENFILE:
			cmd = Commands.NOOP;
			String file = g2.browseFileDialog();
			if(file != null) {
				lay=null;
				gCode=null;
				layers = null;
				restart(file);				
				return 2;
			}	
			break;
		case NEXTLAYER:
			cmd = Commands.NOOP;
			ffLayer=true;
			break;
		case NEXTLAYER10:
			cmd = Commands.NOOP;
			if(model.getLayercount(true) <= lay.getNumber()+10){
				fftoLayer=model.getLayercount(true);
			}else{
				fftoLayer=lay.getNumber()+10;				
			}
			
			break;
		case PREVIOUSLAYER:
			cmd = Commands.NOOP;
			//System.out.println("PREV");
			if(fftoLayer==0){
				fftoLayer=lay.getNumber()-1;
				if(fftoLayer<=0) fftoLayer=model.getLayercount(true); //jump to last layer
				norepaint=true;
			}else if(fftoLayer > (lay.getNumber()+1)){
				fftoLayer--;
				norepaint=true;
			}
			g2.setFontSize(40);
			g2.drawtext("Please wait", bedSquareZoomed/2-50, bedSquareZoomed/2,100);
			try {
			Thread.sleep(100); //wait to ensure that repaints are finished
			} catch (Exception e) {
			}
			return 1;
		case DEBUG:
			cmd = Commands.NOOP;
			System.out.println(gCode);
			break;
		case PRINT:
			print=true;
		case RESTART:
			cmd = Commands.NOOP;
			g2.clearrect(0, 0, g2.getWidth(), g2.getHeight(),print?1:0);
			fftoLayer=0;
			fftoGcode=0;
			g2.repaint();
			return 1;
		case REANALYSE:
			cmd = Commands.NOOP;
			model = new Model(model.getFilename(),model.getGcodes());
			model.analyze();		
			fftoLayer=lay.getNumber();
			g2.clearrect(0, 0, g2.getWidth(), g2.getHeight(),print?1:0);
			g2.repaint();
			return 1;
		case REPAINTLABEL:
			cmd = Commands.NOOP;
			paintLabel(g2, lay,gCode);
			printDetails(g2,lay);
			break;
		case HELP:
			cmd = Commands.NOOP;
			paintHelp(g2);
			g2.repaint();
			try {
				wait(99999);
			} catch (Exception e) {
			}
		case REPAINTLAYERS:
			cmd = Commands.NOOP;
			if(fftoGcode==0){
				ffstarttime = System.currentTimeMillis();
				fftoGcode=lineidx;
				g2.setFontSize(40);
				g2.drawtext("Please wait", bedSquareZoomed/2-50, bedSquareZoomed/2,100);
			}
			return 1;
		case STEPBACK:
			cmd = Commands.NOOP;
			if(fftoGcode==0 && lineidx > 3){
				ffstarttime = System.currentTimeMillis();
				fftoGcode=lineidx-3; //why 3 ?
			}
			return 1;
		case STEP50X:
			cmd = Commands.NOOP;
			if(fftoGcode==0 && lineidx < model.getGcodecount()-50){
				ffstarttime = System.currentTimeMillis();
				fftoGcode=lineidx+50; 
			}
			return 1;
		case STEP50XBACK:
			cmd = Commands.NOOP;
			if(fftoGcode==0 && lineidx > 50){
				ffstarttime = System.currentTimeMillis();
				fftoGcode=lineidx-50; 
			}
			return 1;
		default:
			break;
		}
		return 0;
	}

	/**
	 * Start the painter thread. Another invocation will stop the thread and
	 * start a new one
	 * 
	 * @param filename
	 * @param in
	 * @return time to analyse the model in ms
	 * @throws IOException
	 */
	public long start(String filename, InputStream in, Model modelin) {
		long time=-1;
		// Cleanup if thread already exists
		if (gcodepainter != null) {
			errormsg = null;
			setCmd(Commands.EXIT); // Stop thread
			if(pause != 0) togglePause();
			try {
				gcodepainter.join(10000);
				gcodepainter.interrupt();
			} catch (InterruptedException e) {
			}
			setCmd(Commands.NOOP);
			layers = null;
			model = null;
			g2.repaint();
		}
		System.gc();
		gcodepainter = new Thread(this);
		gcodepainter.start();
		extrazoom=1f;
		boolean ret;
		if (modelin == null) {
			try {
				model = new Model(filename);
				ret = false;
				if (in == null) {
					ret = model.loadModel();
				} else {
					ret = model.loadModel(in,in.available());
				}
			} catch (Exception e) {
				e.printStackTrace();
				errormsg = "Failed to load model. Is this a valid gcode file ?\n" + e.getMessage();
				g2.repaint();
				return time;
			}

			if (!ret) {
				errormsg = "Failed to load model.Check errors on command line.";
				g2.repaint();
				return time;
			}
			time = System.currentTimeMillis();
			model.analyze();
			time=(System.currentTimeMillis()-time);
			System.out.println("Load Model Analyse finished in ms:"+time);
		} else {
			model = modelin;
		}
		//overwrite manual extruder offset if defined by m218
		if(model.getExtruderOffset() != null){
			//extruderOffset = model.getExtruderOffset();
			//if m218 is used the offset in the gcode file is usually zero
			applyOffset=false;
		}
		layers = new ArrayList<Layer>(model.getLayer());
		return time;
	}

}

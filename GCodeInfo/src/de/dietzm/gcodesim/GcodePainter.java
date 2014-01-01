package de.dietzm.gcodesim;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import de.dietzm.Constants;
import de.dietzm.Layer;
import de.dietzm.Model;
import de.dietzm.Position;
import de.dietzm.Constants.GCDEF;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.MemoryEfficientLenString;
import de.dietzm.gcodes.MemoryEfficientString;
import de.dietzm.print.Printer;

/**
 * Gcodepaint Class starts a thread and iterates through the gcodes and paint the corresponding lines. 
 * 
 * @author mdietz
 *
 */
public class GcodePainter implements Runnable {
	public static enum Commands {STEPBACK,STEP50X,STEP50XBACK,RESTART,NEXTLAYER,NEXTLAYER10,REPAINTLABEL,DEBUG,EXIT,OPENFILE,NOOP,PRINT,REPAINTLAYERS,PREVIOUSLAYER,HELP,REANALYSE}; 
	public static final int bedsizeX = 200;
	public static final int bedsizeY = 200;
	public int colNR = 7;
	public boolean painttravel = true;
	public boolean isPainttravel() {
		return painttravel;
	}


	public void setPainttravel(boolean painttravel) {
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

	private float fanspeed = 0;
	//Private vars
	private boolean useAccelTime=true;
	private boolean ffLayer=false; //Fast Forward Layer
	private int fftoGcode=0 , fftoLayer=0; //Fast forward to gcode linenumber # / to layer
	private float zoom = 3.5f;
	private Layer currentlayer=null;
	private float speedup = 5;
	public static final float zoommod=2.7f;
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
	public static final int gap=20;

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
			}else{
				//10 steps
				setCmd(Commands.STEP50X);
			}
		}else{
			if(pause != 0 && inpause){
				setCmd(Commands.STEPBACK);
			}else{
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
				(int) (bedsizeX * getZoom() + gap + (details ? bedsizeX*zoom/zoommod*2 : 0) +13),
				(int) (bedsizeY * getZoom() + (bedsizeY * getZoom()/12)+65)		
		};
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

	int detailstype=0;
	
	public GcodePainter(GraphicRenderer g){
		this.g2=g;	
		colNR=g.getColorNr()-2; //-1 size vs index , -2 travel + border colors
	}
	
	public GcodePainter(GraphicRenderer g, boolean modeldetails, float zoomlevel){
		this(g);
		this.zoom=zoomlevel;
		
	}

	private void calculateOffset() {
		//System.out.println("Bound:"+model.getBoundaries()[0]+"/"+model.getBoundaries()[1]);
		//Calculate offset
		if(model.getBoundaries()[0] >= bedsizeX || model.getBoundaries()[1] <= 0){
			//67,139/2  =  0
			//250-150=200
			float midpoint = model.getBoundaries()[0]-(model.getDimension()[0]/2);
			Xoffset = (int)(bedsizeX/2-midpoint);
		}else{
			Xoffset=0;
		}
		if(model.getBoundaries()[2] >= bedsizeY || model.getBoundaries()[3] <= 0){
			//67,139/2  =  0
			//250-150=200
			float midpoint = model.getBoundaries()[2]-(model.getDimension()[1]/2);
			Yoffset = (int)(bedsizeY/2-midpoint);
		}else{
			Yoffset=0;
		}
		//System.out.println("X/Y Offset:"+Xoffset+"/"+Yoffset);
	}

	private void paintLabel(GraphicRenderer g2, Layer lay,GCode gc) {
		
		//On Layer change, skip for individual gcodes to save cycles
		if(gc == null){
			// Paint boxes with infos about current layer
			printLabelBox(g2, 0,12, Constants.inttoChar(lay.getNumber(),tempbuf), Constants.LAYER_LABEL,lay.getNumber());
			printLabelBox(g2, 12,20,Constants.floattoChar(lay.getZPosition(),tempbuf,2), Constants.ZPOS_LABEL,lay.getNumber());
			printLabelBox(g2, 32,12,Constants.ZERO_LABEL, Constants.XYSPEED_LABEL,lay.getNumber());
			printLabelBox(g2, 44,14,Constants.ZERO_LABEL, Constants.ESPEED_LABEL,lay.getNumber());
			printLabelBox(g2, 95,5,getFanSpeed(lay.getFanspeed()), Constants.FAN_LABEL,lay.getNumber());	
			
		}else{
			printLabelBox(g2, 32,12,Constants.inttoChar(Math.round(gc.getSpeed()),tempbuf), Constants.XYSPEED_LABEL,lay.getNumber());
			printLabelBox(g2, 44,14,getExtrSpeed(gc), Constants.ESPEED_LABEL,lay.getNumber());
			//Z-Lift
			if(gc.isInitialized(Constants.Z_MASK)){
				CharSequence cs = Constants.floattoChar(gc.getZ(),tempbuf,2);
				printLabelBox(g2, 12,20,cs, Constants.ZPOS_LABEL,lay.getNumber());
			}
			printLabelBox(g2, 95,5,getFanSpeed(gc.getFanspeed()), Constants.FAN_LABEL,lay.getNumber());
		}
		
		int nr = Constants.formatTimetoHHMMSS(mtime,tempbuf.getBytes());
		tempbuf.setlength(nr);
		printLabelBox(g2, 58,24, tempbuf, Constants.REMTIME_LABEL,lay.getNumber());

		if(print){
			if(gc!=null){
				//printLabelBox(g2, 82,12,gc.getLineindex()%2==0?"#":"##", "Print",lay.getNumber());
				printLabelBox(g2, 81,14,gc.getGcode().toString(), Constants.PRINT_LABEL,lay.getNumber());
			}else{
				printLabelBox(g2, 81,14,Constants.PRINTSTART_LABEL, Constants.PRINT_LABEL,lay.getNumber());
			}
		}else{
			printLabelBox(g2, 81,14,speeduplabel, Constants.SPEEDUP_LABEL,lay.getNumber());			
		}
		
		
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
		g2.setColor(lay.getNumber() % colNR);
		float boxheight=(bedsizeX*zoom)/12f;
		int linegap=(int)((4*zoom)+3.2f);
		float size=2.5f+(3.10f*zoom);
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
			if(lines.length*linegap+30 >= bedsizeY*zoom+boxheight -(bedsizeY*zoom/zoommod) ){
				start=(int) (((bedsizeY*zoom+boxheight -linegap -(bedsizeY*zoom/zoommod) )/linegap)+1)*(detailstype-2); //TODO calculate number
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
		g2.clearrect(bedsizeX*zoom+gap+5,(bedsizeY*zoom/zoommod)+2, (bedsizeX * zoom/zoommod*2)-5, bedsizeY * zoom + boxheight -(bedsizeY*zoom/zoommod)-7 ,print?1:0);
		
		
		int c=0;
		for (int i = start; i < det.length; i++) {
			int y = (int)(bedsizeY*zoom/zoommod)+linegap+c*linegap; //avoid painting across the border
			c++;
			if(y+30 >= bedsizeY*zoom+boxheight) break;
			
			g2.drawtext(det[i], bedsizeX*zoom+gap+5,y );
			
		}
	}

	private void printLabelBox(GraphicRenderer g2, int boxposp,int bsizepercent, CharSequence value, CharSequence labl,int laynr) {
		float boxsize=((bedsizeX*zoom+gap)/100)*bsizepercent;
		float boxpos=((bedsizeX*zoom+gap)/100)*boxposp;
		float boxheight=(bedsizeX*zoom)/12f;
		float gapz=zoom;
		float size=2+9.6f*(zoom);
		g2.clearrect(boxpos+2,bedsizeY*zoom+2, boxsize-3,boxheight-2,print?1:0);
		g2.setColor(colNR);//white
		g2.drawrect(boxpos,bedsizeY*zoom, boxsize,boxheight+1);
		g2.setColor(laynr % colNR);
		g2.setFontSize(size);
		g2.drawtext(value, boxpos, bedsizeY*zoom+size-gapz,boxsize);
		g2.setFontSize(size/3);
		g2.drawtext(labl, boxpos, bedsizeY*zoom+boxheight-gapz*2,boxsize);
	}

	private void paintLoading(GraphicRenderer g) {
		g.setColor(colNR);
		g.setFontSize(40);
		
		g.drawtext("Please Wait",100,200);
		g.drawtext("Loading Model......",100,270);
		if(model != null){
			g.clearrect(200,285,500,75,print?1:0);
			if(errormsg!=null){
				g.setFontSize(20);
				String[] errlines =  errormsg.split("\n");
				for (int i = 0; i < errlines.length; i++) {
					g.drawtext(errlines[i],100,340+i*30);
				}
				
			}else{
				if(model.getFilesize() >0){
					g.drawtext(Constants.round2digits((model.getReadbytes()/(model.getFilesize()/100f)))+"%  ("+model.getGcodes().size()+")",220,340);
				}else{
					g.drawtext(model.getGcodes().size()+"",220,340);
				}
			}
				
		}
		g.setFontSize(11.5f);
		}
	
	private void paintHelp(GraphicRenderer g) {
		g.setColor(0);
		
		float boxsize = (bedsizeX*zoom)/2.5f;
		float xbox=(bedsizeX*zoom)/2-boxsize/2;
		float ybox=(bedsizeY*zoom)/2-boxsize/2;
		g.fillrect(xbox,ybox , boxsize+30, boxsize+109);
		g.drawrect(xbox-3,ybox-3 , boxsize+36, boxsize+115);
		float gap = 5*zoom;
		g.setColor(colNR);
		
		g2.drawtext("Gcode Simulator "+GcodeSimulator.VERSION,xbox+3 , ybox+gap);
		g2.drawtext("________________________",xbox+3 , ybox+gap+1);
		g2.drawtext("Author: Mathias Dietz ",xbox+3 , ybox+gap*2);
		g2.drawtext("Contact: gcode@dietzm.de ",xbox+3 , ybox+gap*3);
		
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
		g2.setColor(colNR);
		g2.drawrect(0, 0, bedsizeX * zoom, bedsizeY * zoom); // Draw print bed
		g2.drawrect(bedsizeX * zoom , 0, gap, bedsizeY * zoom); // Draw level bar border
		
		//Draw box for modeldetails , front view and side view
		//Front & side view boxes
		float zoomsmall= zoom/zoommod;
		g2.drawrect(bedsizeX * zoom + gap, 0, bedsizeX*zoomsmall, bedsizeY * zoomsmall);
		g2.drawrect(bedsizeX * zoom + gap +bedsizeX*zoomsmall , 0, bedsizeX*zoomsmall, bedsizeY * zoomsmall);
		//full modeldetails box
		float boxheight=(bedsizeX*zoom)/12f;
		g2.drawrect(bedsizeX * zoom + gap, 0,  bedsizeX*zoomsmall*2, bedsizeY * zoom+boxheight+1); // Draw print		
		
		float fsize=2+5f*(zoom);
		g2.setFontSize(fsize);
		
		g2.drawtext("Front View", bedsizeX * zoom + gap+bedsizeX*zoomsmall,fsize+2,bedsizeX*zoomsmall);
		g2.drawtext("Side View", bedsizeX * zoom + gap,fsize+2,bedsizeX*zoomsmall);
		
		//Draw grid
		g2.setStroke(2);
		g2.setColor(colNR+1);
		for (int i = 10; i < bedsizeX; i=i+10) {
			g2.drawline(i*zoom,0,i*zoom,bedsizeY*zoom);
			g2.drawline(0,i*zoom,bedsizeX*zoom,i*zoom);	
		}
		//draw front & side grid
		g2.drawline(bedsizeX * zoom + gap, bedsizeY*zoomsmall, bedsizeX * zoom + gap +60, bedsizeY * zoomsmall-60);
		g2.drawline(bedsizeX * zoom + gap+bedsizeX * zoomsmall, bedsizeY*zoomsmall, bedsizeX * zoom + gap +bedsizeX *zoomsmall -60, bedsizeY * zoomsmall-60);
																
	
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
		float factor = (bedsizeY * zoom - 2) / (model.getDimension()[2] * 10);
		g2.fillrect(bedsizeX * zoom, bedsizeY * zoom - (int) ((lay.getZPosition() * 10) * factor), gap,
				(int) (lay.getLayerheight() * 10 * factor)); // Draw level indicator
	}

	private float printLine(GraphicRenderer g2, Position lastpos, Position pos, float zpos, float time,long starttime,boolean travel) {
		float x1 = (lastpos.x + Xoffset) * zoom;
		float y1 = (bedsizeY * zoom) - ((lastpos.y + Yoffset) * zoom);
		float x2 = (pos.x + Xoffset) * zoom;
		float y2 = (bedsizeY * zoom) - ((pos.y + Yoffset) * zoom);

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
					
					g2.setPos((int)lx,(int)ny);
					g2.drawline(lx, ly, nx, ny);
					printLineHorizontal2(g2,lx,ly,nx,ny, zpos,travel);
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

		g2.drawline(x1, y1, x2, y2);
		printLineHorizontal2(g2,x1,y1,x2,y2, zpos,travel);
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
	//	g2.drawrect(((cx+Xoffset)*zoom), (bedsizeY * zoom) -((cy+Yoffset)*zoom), (int)1, (int)1);
	//	g2.setColor(3);
	//	g2.drawrect(((utx+Xoffset)*zoom),(bedsizeY * zoom) - ((uty+Yoffset)*zoom), (int)radius*2*zoom, (int)radius*2*zoom);
		g2.drawArc((int)((utx+Xoffset)*zoom), (int)((bedsizeY * zoom) -((uty+Yoffset)*zoom)), (int)(radius*2*zoom), (int)(radius*2*zoom), (int)angle1,(int)angle);
		//TODO: split arc in multiple sections to have a smoother paint
		return 0;
	}
	
	
	
	
	private void printLineHorizontal(GraphicRenderer g2, Position lastpos, Position pos, float zpos) {
		//TODO save some CPU cycles by remembering what has been drawn already on this layer and avoid redraw
		float zoomsmall= zoom/zoommod;
//		System.out.println("--STARTPOS: X"+lastpos.x+" Y"+lastpos.y+" Gcode:"+gc.getCodeline());
//		System.out.println("--ENDPOS: X"+pos.x+" Y"+pos.y);
//		System.out.println("Offset:"+Xoffset+" "+Yoffset+" Zoom:"+zoom +" small:"+zoomsmall);
		float z =  (bedsizeY *zoomsmall) - (zpos*zoomsmall) - 20 ;		
		/*
		 * Print side view
		 */
		float y1 = (bedsizeX * zoomsmall) - ((lastpos.y + Yoffset) * zoomsmall)+ (bedsizeX * zoom + gap);		
		float y2 = (bedsizeX * zoomsmall) - ((pos.y + Yoffset) * zoomsmall)+ (bedsizeX * zoom + gap);
		g2.drawline(y1, z, y2, z);
		
		/*
		 * Print front view
		 */
		float x1 = ((lastpos.x + Xoffset) * zoomsmall + (bedsizeX * zoom + gap))+(bedsizeX * zoomsmall) ;
		float x2 = ((pos.x + Xoffset) * zoomsmall+ (bedsizeX * zoom + gap))+(bedsizeX * zoomsmall);
		g2.drawline(x1, z, x2,z);
		
		g2.setPos((int)y1, (int)x1, (int)z);
		//Print Extrusion		
//		if(gc.getExtrusion() > 0){		
//			float xex = gc.getExtrusion()/gc.getDistance()*10000;
//			System.out.println("extru:"+xex);
//		g2.drawline((bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall)), 
//				(bedsizeY *zoomsmall)-22-xex, 
//				(bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall))
//				,(bedsizeY * zoomsmall)-20-xex);
//		}else if(gc.getExtrusion() < 0){
//			float xex = gc.getExtrusion()*10;
//			g2.setColor(1);
//			System.out.println("restract:"+xex);
//			g2.drawline((bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall)), 
//					(bedsizeY *zoomsmall)-22-xex, 
//					(bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall))
//					,(bedsizeY * zoomsmall)-20-(xex));
//		}
//		
		//Print speed
		//g2.drawline((bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall)), 
//				(bedsizeY *zoomsmall)-22-gc.getSpeed(), 
//				(bedsizeX * zoom + gap) + (bedsizeX * zoomsmall)+(gc.getLineindex()/10%(bedsizeX * zoomsmall))
//				,(bedsizeY * zoomsmall)-20-gc.getSpeed());
			
	}
	
	private void printLineHorizontal2(GraphicRenderer g2,float lx,float ly,float x, float y, float zpos,boolean travel) {
		//TODO save some CPU cycles by remembering what has been drawn already on this layer and avoid redraw
		float zoomsmall= zoom/zoommod;
		float z =  (bedsizeY *zoomsmall) - (zpos*zoomsmall) - 20 ;		
		/*
		 * Print side view
		 */
		float y1 = (bedsizeX * zoomsmall) - ((ly / zoom) * zoomsmall)+ (bedsizeX * zoom + gap);		
		float y2 = (bedsizeX * zoomsmall) - ((y / zoom) * zoomsmall)+ (bedsizeX * zoom + gap);
		/*
		 * Print front view
		 */
		float x1 = ((lx / zoom) * zoomsmall + (bedsizeX * zoom + gap))+(bedsizeX * zoomsmall) ;
		float x2 = ((x / zoom) * zoomsmall+ (bedsizeX * zoom + gap))+(bedsizeX * zoomsmall);

		if(!travel){ //do not paint travel
			g2.drawline(y1, z, y2, z);
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
		A: while (true) {
			if (layers != null) {
				Position pos = new Position(0,0);
				Position lastpos = new Position(0,0);
				fanspeed=0;
				updateDetailLabels();
				updateSpeedupLabel();
				g2.clearrect(0, 0, g2.getWidth(),g2.getHeight(),print?1:0);
				calculateOffset();
				mtime = model.getTimeaccel(); //remaining time
				printBed(g2);
				if(print){
					g2.setTitle("Print Mode");
				}else{
					g2.setTitle("Simulation");
				}
				 for (Layer lay : layers) {
					if(oneLayerAtATime){
						g2.clearrect(2, 2, bedsizeX*zoom-2, bedsizeY*zoom,print?1:0);
						printBed(g2);
					}else{
						//Fading the previous layer
						if(fftoLayer == 0 || fftoLayer <= lay.getNumber()+10){
							g2.faintRect(2, 2, bedsizeX*zoom+20, bedsizeY*zoom);
						}
					}
						
					currentlayer=lay;
					ffLayer=false;					
					printDetails(g2, lay);
					paintLevelBar(g2, lay);
					paintLabel(g2, lay,null);
										
					// Print & Paint all Gcodes
					
					//Android guidelines say foreach loops are slower for arraylist
					ArrayList<GCode> gcarr = lay.getGcodes();
					int gcnum = gcarr.size();
					float zpos=lay.getZPosition();
					GCDEF gcdef;
					for(int ig = 0 ; ig < gcnum; ig++ ){
						GCode gCode = gcarr.get(ig);
						gcdef=gCode.getGcode();
						/*
						 * Painting starts here
						 */
						if(gcdef == Constants.GCDEF.UNKNOWN || gcdef == Constants.GCDEF.COMMENT){
							continue;
						}
						//System.out.println(gCode);
						long starttime=System.currentTimeMillis();
						float sleeptime =0;
						//Paint label and calculate set gcode time as sleeptime (simulation)
						//has to be synchronized to avoid flickering
						synchronized(g2){ 
							if(!ffLayer && fftoGcode==0 && fftoLayer==0){
								paintLabel(g2, lay, gCode);
								sleeptime = ((useAccelTime?gCode.getTimeAccel():gCode.getTime())* 1000) / speedup;
							}
						}
						/*
						 * Printing
						 * If printing is enabled synchronize rendering with the print controller
						 */
						alignWithPrint(lay, gCode);
						
						//Print the lines from last position to current position
						if(gCode.getCurrentPosition(pos) != null) {
							//System.out.println("XXXXX"+gCode);
			
						g2.setPos((int)((pos.x+Xoffset)*zoom), (int)((bedsizeY * zoom) - (pos.y+Yoffset)*zoom));
						if (lastpos != null) {
							if (gCode.isExtruding()) { //TODO Add all_extrude for CNC
								g2.setColor(lay.getNumber() % colNR);		
								if(gcdef == Constants.GCDEF.G2 ||gcdef == Constants.GCDEF.G3 ){
									printArc(g2, lastpos, pos, lay, gCode);
									printLineHorizontal(g2,lastpos,pos,zpos);
								}else{
									printLine(g2, lastpos, pos,zpos,sleeptime,starttime,false);
								}
								//printLineHorizontal(g2, lastpos, pos,lay,gCode); //Side & front view
							} else if (painttravel) {
								g2.setColor(colNR+1);
								g2.setStroke(0);
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
							int skip = handleCommand(gCode, lay);
							if(skip==1) continue A;
							if(skip==2) return;
					
							
							if(!ffLayer && fftoGcode==0 && fftoLayer==0 ){
								g2.repaint();
								long paintdur=System.currentTimeMillis()-starttime;
								long sleep = Math.max(0,((long)sleeptime)-paintdur);
								Thread.sleep((int)sleep);

								if(pause != 0){
									doPause(lay, gCode);
								}
								
							}else{
								if(fftoGcode != 0 && fftoGcode == gCode.getLineindex()){
									fftoGcode=0;
								}
								if(fftoLayer != 0 && fftoLayer == lay.getNumber()){
									fftoLayer=0;
								}
							}
							
						} catch (InterruptedException e) {
							inpause=false;
							g2.clearrect(bedsizeX*zoom+gap+1,bedsizeY * zoom + (bedsizeX*zoom)/24f, (bedsizeX * zoom/zoommod*2)-2, (bedsizeX*zoom)/24f,print?1:0);
						}
							
					}//ForGCodes
	
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
	
		
	}

	private void updateDetailLabels() {
		
		StringBuilder tmpbuf = new StringBuilder();
		
		tmpbuf.append("--------------- Model details ------------------\n");
		tmpbuf.append(model.getModelDetailReport());
		tmpbuf.append(model.guessPrice(model.guessDiameter()));
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
	private void alignWithPrint(Layer lay, GCode renderCode) {
		if (printer == null || (!printer.isPrinting() && !print) )
			return;
		if(pause == 0 && printer.isPause()){ //inconsistency 
			togglePause();
		}
		if (!print && printer.isPrinting()) {
			togglePrint(); // enable printing
			speedup=1;
		}

		
		int defbuffersize = 15;
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
					printLabelBox(g2, 82,12,"W", "Wait",lay.getNumber());
					g2.repaint();
					while(printer.isPrinting()){
						Thread.sleep(2500); //check every 2.5 sec if still printing
					}
				}
				boolean wait = false;
				GCode printCode = printer.getCurrentGCode(); //get what has been send to the printer last
				if(printCode == null) continue;
				//TODO: when end of file is reached
				inbuffer=Math.min(defbuffersize, printCode.getLineindex()-bufferemptyindex); //Slowly fill buffer buffer
				if (printCode.isBuffered()	&& renderCode.getLineindex() >= printCode.getLineindex() - inbuffer) {
					wait = true;
					speedup=1;
				} else if (!printCode.isBuffered() && renderCode.getLineindex() >= printCode.getLineindex()) {
					wait = true;
					bufferemptyindex=printCode.getLineindex();
					speedup=1;
				}

				if (renderCode.getLineindex() < printCode.getLineindex() - maxbehind) {
					if (printCode.isBuffered()) {
						fftoGcode = printCode.getLineindex() - inbuffer;
					} else {
						fftoGcode = printCode.getLineindex();
					}
					//System.out.println("AlignGCode: ff:"+fftoGcode);
					wait = false;
				} else if (renderCode.getLineindex() < printCode.getLineindex() - behind) {
					speedup++;
					//System.out.println("AlignGCode:Speedup:"+speedup);
					wait = false;
				}

				if (pause != 0) {
					bufferemptyindex=printCode.getLineindex();
					doPause(lay, renderCode);
					printLabelBox(g2, 82,12,"W", "Wait",lay.getNumber());
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
				printLabelBox(g2, 82,12,"W", "Wait",lay.getNumber());
				g2.repaint();
			}

		}
		// Turn off print mode
		if (print && !printer.isPrinting()) {
			togglePrint();
		}
	}

	private void doPause(Layer lay, GCode gCode) throws InterruptedException {
		inpause=true;
		printLabelBox(g2, 82,12,"P", "Pause",lay.getNumber());
		g2.clearrect(bedsizeX*zoom+gap+1,bedsizeY * zoom + (bedsizeX*zoom)/24f, (bedsizeX * zoom/zoommod*2)-2, (bedsizeX*zoom)/24f,print?1:0);
		g2.drawrect(bedsizeX*zoom+gap+4,bedsizeY * zoom + (bedsizeX*zoom)/24f +2, (bedsizeX * zoom/zoommod*2)-7, (bedsizeX*zoom)/24f -5);
		g2.drawtext("L"+gCode.getLineindex()+": "+ gCode.getCodeline().toString().trim(), bedsizeX*zoom+gap+10, bedsizeY * zoom +(bedsizeX*zoom)/24f +  (bedsizeX*zoom)/48f +1 );
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
	private synchronized int handleCommand(GCode gCode, Layer lay){
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
			}
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
				fftoGcode=gCode.getLineindex();
			}
			return 1;
		case STEPBACK:
			cmd = Commands.NOOP;
			if(fftoGcode==0 && gCode.getLineindex() > 3){
				fftoGcode=gCode.getLineindex()-3; //why 3 ?
			}
			return 1;
		case STEP50X:
			cmd = Commands.NOOP;
			if(fftoGcode==0 && gCode.getLineindex() < model.getGcodecount()-50){
				fftoGcode=gCode.getLineindex()+50; 
			}
			return 1;
		case STEP50XBACK:
			cmd = Commands.NOOP;
			if(fftoGcode==0 && gCode.getLineindex() > 50){
				fftoGcode=gCode.getLineindex()-50; 
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
	 * @throws IOException
	 */
	public void start(String filename, InputStream in, Model modelin) {
		// Cleanup if thread already exists
		if (gcodepainter != null) {
			errormsg = null;
			setCmd(Commands.EXIT); // Stop thread
			try {
				gcodepainter.join(10000);
			} catch (InterruptedException e) {
			}
			setCmd(Commands.NOOP);
			layers = null;
			model = null;
			g2.repaint();
		}
		gcodepainter = new Thread(this);
		gcodepainter.start();

		boolean ret;
		if (modelin == null) {
			try {
				model = new Model(filename);
				ret = false;
				if (in == null) {
					ret = model.loadModel();
				} else {
					ret = model.loadModel(in);
				}
			} catch (Exception e) {
				e.printStackTrace();
				errormsg = "Failed to load model. Is this a valid gcode file ?\n" + e.getMessage();
				g2.repaint();
				return;
			}

			if (!ret) {
				errormsg = "Failed to load model.Check errors on command line.";
				g2.repaint();
				return;
			}
			model.analyze();
		} else {
			model = modelin;
		}
		layers = new ArrayList<Layer>(model.getLayer());
	}

}

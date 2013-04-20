package de.dietzm.gcodesim;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import de.dietzm.GCode;
import de.dietzm.Layer;
import de.dietzm.Model;

public class GcodePainter implements Runnable {
	public static enum Commands {RESTART,NEXTLAYER,REPAINTLABEL,DEBUG,EXIT,OPENFILE,NOOP,PRINT,REPAINTLAYERS,PREVIOUSLAYER,HELP}; 
	
	private boolean useAccelTime=true;
	boolean ffLayer=false; //Fast Forward Layer
	int fftoGcode=0 , fftoLayer=0; //Fast forward to gcode linenumber # / to layer
	float zoom = 3.5f;
	
	float speedup = 5;
	float gap=20;
	float zoommod=2.7f;
	int pause = 0;
	float Xoffset=0,Yoffset=0;
	public static final int bedsizeX = 200;
	public static final int bedsizeY = 200;
	Thread gcodepainter;
	String errormsg=null;
	float mtime;

	private static final boolean PAINTTRAVEL = true;
	// Model & Layer Info
	Model model = null;
	ArrayList<Layer> layers;
	GraphicRenderer g2;
	Commands cmd = Commands.NOOP;
	boolean print=false;
	
	public float getZoom() {
		return zoom;
	}

	public void setZoom(float zoom) {
		this.zoom = zoom;
		setCmd(Commands.REPAINTLAYERS);
	}

	
	/**
	 * Will increase/decrease the speed 
	 * @param boolean faster, true=increase speed, false=decrease
	 */
	public synchronized void toggleSpeed(boolean faster){
		if(faster && speedup >= 1){
			speedup++;
		}else if(faster && speedup < 1){
			speedup+=0.1;
		}else if(speedup > 1){
			speedup--;
		}else if(speedup > 0.1){
			speedup-=0.1;
		}
		gcodepainter.interrupt();
		
	}



	public synchronized void togglePause() {
		if (pause == 0) {
			pause =999999;
		} else {
			pause =0;
			gcodepainter.interrupt();
		}		
	}
	
	public synchronized void togglePrint(){
		setCmd(Commands.PRINT);
	}
	
	public synchronized void showHelp(){
		setCmd(Commands.HELP);
	}



	public synchronized void setCmd(Commands cmd) {
		gcodepainter.interrupt();
		this.cmd = cmd;		
	}
	
	public int[] getSize(boolean details){
		return new int[]{ 
				(int) (bedsizeX * getZoom() + gap + (details ? bedsizeX*zoom/zoommod*2 : 0) +13),
				(int) (bedsizeY * getZoom() + (bedsizeY * getZoom()/12)+65)		
		};
	}


	/**
	 * Restart from thread
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
			try {
				start(filename,null);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
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

	int detailstype=0;
	
	public GcodePainter(GraphicRenderer g){
		this.g2=g;		
	}
	
	public GcodePainter(GraphicRenderer g, boolean modeldetails, float zoomlevel){
		this.g2=g;		
	//	this.modeldetails=modeldetails;
		this.zoom=zoomlevel;
	}

	private void calculateOffset() {
		//Calculate offset
		if(model.getBoundaries()[0] >= bedsizeX || model.getBoundaries()[1] <= 0){
			//67,139/2  =  0
			//250-150=200
			float midpoint = model.getBoundaries()[0]-(model.getDimension()[0]/2);
			Xoffset = bedsizeX/2-midpoint;
		}else{
			Xoffset=0;
		}
		if(model.getBoundaries()[2] >= bedsizeY || model.getBoundaries()[3] <= 0){
			//67,139/2  =  0
			//250-150=200
			float midpoint = model.getBoundaries()[2]-(model.getDimension()[1]/2);
			Yoffset = bedsizeY/2-midpoint;
		}else{
			Yoffset=0;
		}
		//System.out.println("X/Y Offset:"+Xoffset+"/"+Yoffset);
	}

	void paintLabel(GraphicRenderer g2, Layer lay,GCode gc) {
		//Clear box background
		g2.clearrect(0, bedsizeY * zoom + 5, bedsizeX * zoom+gap, bedsizeY * zoom);
		g2.setColor(lay.getNumber() % 7);

		// Paint boxes with infos about current layer
		printLabelBox(g2, 0,12, String.valueOf(lay.getNumber()), "Layer #",lay);
		printLabelBox(g2, 12,20, String.valueOf(lay.getZPosition()), "Z-Position",lay);
		//printLabelBox(g2, 32,22, String.valueOf(lay.getSpeed(Speed.SPEED_PRINT_AVG)), "Avg. Speed",lay);
		printLabelBox(g2, 32,22,String.valueOf(Math.round(gc.getSpeed())), "Current Speed",lay);
		//printLabelBox(g2, 54,24, GCode.formatTimetoHHMMSS((useAccelTime?lay.getTimeAccel():lay.getTime())), "Layer Time",lay);
		printLabelBox(g2, 54,24, GCode.formatTimetoHHMMSS(mtime), "Remaining Time",lay);
		printLabelBox(g2, 78,13,GCode.removeTrailingZeros(String.valueOf(GCode.round2digits(speedup)))+"x", "Speedup",lay);
		printLabelBox(g2, 91,9,(gc.getFanspeed()!=0?String.valueOf(Math.round(gc.getFanspeed()/25)):"-"), "Fan",lay);
	}

	private void printDetails(GraphicRenderer g2, Layer lay) {
		g2.setColor(lay.getNumber() % 7);
		float boxheight=(bedsizeX*zoom)/12f;
		int linegap=(int)(5*zoom);
		float size=2+10f*(zoom);
		g2.setFontSize(size/3);
		
		// bed
		String details;
		int start=0;
		switch (detailstype) {
		case 0:
			details = "--------------- Model details ------------------\n"+model.getModelDetailReport()+model.guessPrice(model.guessDiameter());
			//details="";
			break;
		case 1:
			 details = "--------------- Layer details ------------------\n"+lay.getLayerDetailReport();
			break;
		case 2:
		case 3:
		case 4:
			details = model.getModelLayerSummaryReport();
			String[] lines = details.split("\n");
			if(lines.length*linegap+30 >= bedsizeY*zoom+boxheight -(bedsizeY*zoom/zoommod) ){
				start=(int) (((bedsizeY*zoom+boxheight -linegap -(bedsizeY*zoom/zoommod) )/linegap)+1)*(detailstype-2); //TODO calculate number
				//System.out.println("START:"+start);
				break;
			}
			detailstype=5; //skip
		case 5:
			 details = model.getModelSpeedReport();
			break;
		case 6:
			details = lay.getLayerSpeedReport();
			break;
		case 7:
			details = model.getModelComments();
			if(details.length() != 0) break;
		default:
			 details = model.getModelDetailReport();
			break;
		}
		g2.clearrect(bedsizeX*zoom+gap+5,(bedsizeY*zoom/zoommod)+5, (bedsizeX * zoom/zoommod*2)-10, bedsizeY * zoom + boxheight -(bedsizeY*zoom/zoommod)-10 );
		
		String[] det = details.split("\n");
		int c=0;
		for (int i = start; i < det.length; i++) {
			int y = (int)(bedsizeY*zoom/zoommod)+linegap+c*linegap; //avoid painting across the border
			c++;
			if(y >= bedsizeY*zoom+boxheight) break;
			
			g2.drawtext(det[i], bedsizeX*zoom+gap+5,y );
			
		}
	}

	private void printLabelBox(GraphicRenderer g2, int boxposp,int bsizepercent, String value, String labl,Layer lay) {
		float boxsize=((bedsizeX*zoom+gap)/100)*bsizepercent;
		float boxpos=((bedsizeX*zoom+gap)/100)*boxposp;
		float boxheight=(bedsizeX*zoom)/12f;
		float gap=zoom;
		float size=2+10f*(zoom);
		g2.setColor(7);//white
		g2.drawrect(boxpos,bedsizeY*zoom, boxsize,boxheight+1);
		g2.setColor(lay.getNumber() % 7);
		g2.setFontSize(size);
		g2.drawtext(value, boxpos, bedsizeY*zoom+size-gap,boxsize);
		g2.setFontSize(size/3);
		g2.drawtext(labl, boxpos, bedsizeY*zoom+boxheight-gap*2,boxsize);
	}

	public void paintLoading(GraphicRenderer g) {
		g.setColor(0);
		//TODO
		g.setFontSize(50);
		
		g.drawtext("Please Wait",220,200);
		g.drawtext("Loading Model......",200,270);
		if(model != null){
			g.clearrect(200,285,500,75);
			if(errormsg!=null){
				g.drawtext(errormsg,200,340);
			}else{
				if(model.getFilesize() >0){
					g.drawtext(GCode.round2digits((model.getReadbytes()/(model.getFilesize()/100f)))+"%  ("+model.getGcodes().size()+")",220,340);
				}else{
					g.drawtext(model.getGcodes().size()+"",220,340);
				}
			}
				
		}
		g.setFontSize(11.5f);
		}
	
	public void paintHelp(GraphicRenderer g) {
		g.setColor(0);
		
		float boxsize = (bedsizeX*zoom)/2.5f;
		float xbox=(bedsizeX*zoom)/2-boxsize/2;
		float ybox=(bedsizeY*zoom)/2-boxsize/2;
		g.fillrect(xbox,ybox , boxsize+30, boxsize+80);
		g.drawrect(xbox-3,ybox-3 , boxsize+36, boxsize+86);
		float gap = 5*zoom;
		g.setColor(7);
		
		g2.drawtext("Gcode Simulator "+GcodeSimulator.VERSION,xbox+3 , ybox+gap);
		g2.drawtext("________________________",xbox+3 , ybox+gap+1);
		g2.drawtext("Author: Mathias Dietz ",xbox+3 , ybox+gap*2);
		g2.drawtext("Contact: gcode@dietzm.de ",xbox+3 , ybox+gap*3);
		
		g2.drawtext("Key Shortcuts Help: ",xbox+3 , ybox+gap*4+3);
		g2.drawtext("________________________",xbox+3 , ybox+gap*4+4);
		g2.drawtext("+/- = Speed up/down",xbox+3 , ybox+gap*5);
		g2.drawtext("i/o = Zoom in/out",xbox+3 , ybox+gap*6);
		g2.drawtext("n/b = Layer next/back",xbox+3 , ybox+gap*7);
		g2.drawtext("m   = Show Model Details",xbox+3 , ybox+gap*8);
		g2.drawtext("t   = Toggle Model Details",xbox+3 , ybox+gap*9);		
		g2.drawtext("f   = Load Gcode File",xbox+3 , ybox+gap*10);
		g2.drawtext("p/r/q = Pause / Restart / Quit",xbox+3 , ybox+gap*11);
		g2.drawtext("Mouse Shortcuts Help: ",xbox+3 , ybox+gap*12+3);
		g2.drawtext("________________________",xbox+3 , ybox+gap*12+4);
		g2.drawtext("Mousewheel = Speed up/down",xbox+3 , ybox+gap*13);
		g2.drawtext("ALT+Mousewheel = Zoom in/out",xbox+3 , ybox+gap*14);
		g2.drawtext("Left Button on Bed = Next Layer",xbox+3 , ybox+gap*15);
		g2.drawtext("ALT+Left Button on Bed = Previous Layer",xbox+3 , ybox+gap*16);
		g2.drawtext("Right Button = Show Model Details",xbox+3 , ybox+gap*17);
		g2.drawtext("Left Button on Details = Toggle Model Details",xbox+3 , ybox+gap*18);
		g2.drawtext("Middle Button = Show Help",xbox+3 , ybox+gap*19);
		
		
		
		}

	void printBed(GraphicRenderer g2, Layer lay) {
		g2.setColor(7);
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
		g2.setColor(8);
		for (int i = 10; i < bedsizeX; i=i+10) {
			g2.drawline(i*zoom,0,i*zoom,bedsizeY*zoom);
			g2.drawline(0,i*zoom,bedsizeX*zoom,i*zoom);	
		}
		//draw front & side grid
		g2.drawline(bedsizeX * zoom + gap, bedsizeY*zoomsmall, bedsizeX * zoom + gap +60, bedsizeY * zoomsmall-60);
		g2.drawline(bedsizeX * zoom + gap+bedsizeX * zoomsmall, bedsizeY*zoomsmall, bedsizeX * zoom + gap +bedsizeX *zoomsmall -60, bedsizeY * zoomsmall-60);
																
	
		//Print level bar
		g2.setStroke(1);
		g2.setColor(lay.getNumber() % 7);
		float factor = (bedsizeY * zoom - 2) / (model.getDimension()[2] * 10);
		g2.fillrect(bedsizeX * zoom, bedsizeY * zoom - (int) ((lay.getZPosition() * 10) * factor), gap,
				(int) (lay.getLayerheight() * 10 * factor)); // Draw level indicator
		
		//Draw zero position on bed
		if(Xoffset != 0 || Yoffset != 0){
			g2.setStroke(0);
			g2.setColor(7);
			g2.drawline(Xoffset*zoom-10, Yoffset*zoom, Xoffset*zoom+100, Yoffset*zoom);
			g2.drawline(Xoffset*zoom, Yoffset*zoom+10, Xoffset*zoom, Yoffset*zoom-100);
			g2.setStroke(1);
		}
		
	
	}

	float printLine(GraphicRenderer g2, float[] lastpos, float[] pos, float time,long starttime) {
		float x1 = (lastpos[0] + Xoffset) * zoom;
		float y1 = (bedsizeY * zoom) - ((lastpos[1] + Yoffset) * zoom);
		float x2 = (pos[0] + Xoffset) * zoom;
		float y2 = (bedsizeY * zoom) - ((pos[1] + Yoffset) * zoom);

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
					g2.drawline(x1 + (i * stepx), y1 + (i * stepy), x1 + ((i + 1) * stepx), y1
							+ ((i + 1) * stepy));
					try {
						g2.repaint();
						if(!print) {
							long paintdur=System.currentTimeMillis()-starttime;
							long sleepremain = Math.max(0,((long)time)-paintdur);
							long dosleep = Math.min(sleepremain, (long)(time / maxdist));
							Thread.sleep(dosleep ); // pause not done
						}
					} catch (InterruptedException e) {
						time = 0;
					}
				}
				return 0; // already slept enough , no sleep in main loop
			}
		}

		g2.drawline(x1, y1, x2, y2);
		return time; // not slept, sleep in the main loop
	}
	
	
	void printLineHorizontal(GraphicRenderer g2, float[] lastpos, float[] pos, Layer lay,GCode gc) {
		g2.setColor(lay.getNumber() % 7);
		g2.setStroke(1);
		float zoomsmall= zoom/zoommod;

		float x1 = ((lastpos[0] + Xoffset) * zoomsmall) + (bedsizeX * zoom + gap) ;
		float y1 = (bedsizeX * zoomsmall) - ((lastpos[1] + Yoffset) * zoomsmall)+ (bedsizeX * zoom + gap);
		float x2 = (pos[0] + Xoffset) * zoomsmall+ (bedsizeX * zoom + gap);
		float y2 = (bedsizeX * zoomsmall) - ((pos[1] + Yoffset) * zoomsmall)+ (bedsizeX * zoom + gap);
		float z =  (bedsizeY *zoomsmall) - (lastpos[2]*zoomsmall) - 20 ;
		
		//Print front view
		g2.drawline(y1, z, y2, z);	
		//Print side view
		g2.drawline(x1+(bedsizeX * zoomsmall), z, x2+(bedsizeX * zoomsmall),z);

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

	@Override
	/**
	 * Paint the gcodes to a offline image (offbuf) and trigger repaint
	 */
	public void run() {
		g2.clearrect(0, 0, g2.getWidth(), g2.getHeight());
		g2.repaint();
		while (true) {
			if (layers != null) {
				g2.clearrect(0, 0, g2.getWidth(),g2.getHeight());
				calculateOffset();
				mtime = model.getTimeaccel();
				A: for (Layer lay : layers) {
					float activespeedup = speedup;
					ffLayer=false;
					float[] lastpos = null;
					
					//paintLabel(g2, lay);
					printDetails(g2, lay);
					printBed(g2, lay);
					
						
					// Paint all Gcodes
					for (GCode gCode : lay.getGcodes()) {
						long starttime=System.currentTimeMillis();
						float sleeptime =0;
						synchronized(g2){ 
							if(!ffLayer && fftoGcode==0 && fftoLayer==0){
								paintLabel(g2, lay,gCode);
								sleeptime = ((useAccelTime?gCode.getTimeAccel():gCode.getTime())* 1000) / activespeedup;
							}
						}
						float[] pos = gCode.getCurrentPosition();
						if (lastpos != null) {
							if (gCode.isExtruding()) {
								//g2.setColor(lay.getNumber() % 7);								
								printLine(g2, lastpos, pos,sleeptime,starttime);
								printLineHorizontal(g2, lastpos, pos,lay,gCode);
							} else if (PAINTTRAVEL) {
								g2.setColor(8);
								g2.setStroke(0);
								printLine(g2, lastpos, pos,sleeptime,starttime);
								g2.setStroke(1);
							}
						}
						mtime=mtime-gCode.getTimeAccel();
						lastpos = pos;
						//PRINT
						if(print && gCode.isPrintable()){
							boolean success = g2.print(gCode);
							if(!success){
								print=false;
							}
						}
						//POST PROCESSING STARTS
						
						// Sleep for the remaining time up to the gcode time & handle commands
						try {
							/**
							 * Handle a user command
							 */
							int skip = handleCommand(gCode, lay);
							if(skip==1) break A;
							if(skip==2) return;
					
							
							if(!ffLayer && fftoGcode==0 && fftoLayer==0 ){
								g2.repaint();
								if(!print) {
									long paintdur=System.currentTimeMillis()-starttime;
									long sleep = Math.max(0,((long)sleeptime)-paintdur);
									//System.out.println(sleep+ " VS "+sleeptime);
									Thread.sleep((int)sleep + pause);
								}else{
									Thread.sleep(pause); //allow pause only if printing
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
						}
							
					}//ForGCodes
	
				}//ForLayers
			}else{ //If layer==null
				paintLoading(g2);
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
				if(cmd==Commands.EXIT){
					cmd = Commands.NOOP;
					return;
				}
				g2.repaint();
			}
		//	System.out.println("FINAL");
		
		}
	
		
	}
	
	/**
	 * Handle a command
	 * return true if jump back to layer loop
	 * @param gCode
	 * @param lay
	 * @return return true if jump back to layer loop
	 */
	private synchronized int handleCommand(GCode gCode, Layer lay){
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
		case PREVIOUSLAYER:
			cmd = Commands.NOOP;
			//System.out.println("PREV");
			if(fftoLayer==0){
				fftoLayer=lay.getNumber()-1;
				if(fftoLayer<=0) fftoLayer=model.getLayercount(true); //jump to last layer
			}
			try {
				Thread.sleep(150); //wait to ensure that repaints are finished
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
			g2.clearrect(0, 0, g2.getWidth(), g2.getHeight());
			g2.repaint();
			return 1;
		case REPAINTLABEL:
			cmd = Commands.NOOP;
			printBed(g2, lay);
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
		default:
			break;
		}
		return 0;
	}

	/**
	 * Start the painter thread. Another invocation will stop the thread and start a new one
	 * @param filename
	 * @param in
	 * @throws IOException
	 */
	public void start(String filename, InputStream in) throws IOException {
		//Cleanup if thread already exists
		if(gcodepainter!=null){
			errormsg=null;
			setCmd(Commands.EXIT); //Stop thread
			try {
				gcodepainter.join(0);
			} catch (InterruptedException e) {}
			setCmd(Commands.NOOP); 
			layers=null;
			model=null;			
			g2.repaint();
		}
		gcodepainter = new Thread(this);
		gcodepainter.start();

		boolean ret;
		try {
			model = new Model(filename);
			ret = false;
			if(in == null){			
				ret=model.loadModel();
			}else{
				ret=model.loadModel(in);
			}
		} catch (Exception e) {
			ret=false;
		}
		
		if(!ret) {
			errormsg="Failed to load model. Invalid gcode file."; 
			g2.repaint();
			return;
		}
			
		model.analyze();		
		layers = new ArrayList<Layer>(model.getLayer().values());
	}

}

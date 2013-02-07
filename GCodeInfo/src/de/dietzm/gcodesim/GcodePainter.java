package de.dietzm.gcodesim;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import de.dietzm.GCode;
import de.dietzm.Layer;
import de.dietzm.Layer.Speed;
import de.dietzm.Model;

public class GcodePainter implements Runnable {
	public enum Commands {RESTART,NEXTLAYER,REPAINTLABEL,DEBUG,EXIT,OPENFILE,NOOP,REPAINTLAYERS,PREVIOUSLAYER,HELP}; 
	
	private boolean useAccelTime=true;
	boolean ffLayer=false; //Fast Forward Layer
	int fftoGcode=0 , fftoLayer=0; //Fast forward to gcode linenumber # / to layer
	float zoom = 3.5f;
	public float getZoom() {
		return zoom;
	}

	public void setZoom(float zoom) {
		this.zoom = zoom;
		cmd=Commands.REPAINTLAYERS;
		gcodepainter.interrupt();
	}

	public float getSpeedup() {
		return speedup;
	}

//	public void setSpeedup(float speedup) {
//		this.speedup = speedup;
//		gcodepainter.interrupt();
//	}
	
	/**
	 * Will increase/decrease the speed 
	 * @param boolean faster, true=increase speed, false=decrease
	 */
	public void toggleSpeed(boolean faster){
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

	public int getPause() {
		return pause;
	}

	public void togglePause() {
		if (pause == 0) {
			pause =999999;
		} else {
			pause =0;
			gcodepainter.interrupt();
		}		
	}
	
	public void showHelp(){
		setCmd(Commands.HELP);
		gcodepainter.interrupt();
	}



	public void setCmd(Commands cmd) {
		this.cmd = cmd;
	}

	float speedup = 5;
	int pause = 0;
	float Xoffset=0,Yoffset=0;
	public static final int bedsizeX = 200;
	public static final int bedsizeY = 200;
	Thread gcodepainter;

	private static final boolean PAINTTRAVEL = true;
	// Model & Layer Info
	Model model = null;
	ArrayList<Layer> layers;
	GraphicRenderer g2;
	Commands cmd = Commands.NOOP;
	boolean modeldetails=false;
	public boolean isModeldetails() {
		return modeldetails;
	}

	public void toggleModeldetails() {
		this.modeldetails = !modeldetails;
		if(modeldetails){
			setCmd(Commands.REPAINTLABEL);
		}
		gcodepainter.interrupt();
	}
	
	public void toggleType(){
		if(detailstype<6){
			detailstype++;
		}else {
			detailstype=0;
		}
		if(modeldetails){
			setCmd(Commands.REPAINTLABEL);
			gcodepainter.interrupt();
		}
	}

	int detailstype=0;
	
	public GcodePainter(GraphicRenderer g){
		this.g2=g;		
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
		System.out.println("X/Y Offset:"+Xoffset+"/"+Yoffset);
	}

	void paintLabel(GraphicRenderer g2, Layer lay) {
		g2.clearrect(1, bedsizeY * zoom + 5, bedsizeX * zoom+20+(modeldetails?457:0), bedsizeY * zoom);

		// Paint boxes with infos about current layer
		printLabelBox(g2, 0,12, String.valueOf(lay.getNumber()), "Layer #",lay);
		printLabelBox(g2, 12,20, String.valueOf(lay.getZPosition()), "Z-Position",lay);
		printLabelBox(g2, 32,22, String.valueOf(lay.getSpeed(Speed.SPEED_PRINT_AVG)), "Avg. Speed",lay);
		printLabelBox(g2, 54,24, GCode.formatTimetoHHMMSS((useAccelTime?lay.getTimeAccel():lay.getTime())), "Layer Time",lay);
		printLabelBox(g2, 78,13,GCode.removeTrailingZeros(String.valueOf(GCode.round2digits(speedup)))+"x", "Speedup",lay);
		printLabelBox(g2, 91,9,(lay.getFanspeed()!=0?"On":"-"), "Fan",lay);
				
		g2.setColor(lay.getNumber() % 7);
			
		if (modeldetails){
			float boxheight=(bedsizeX*zoom)/12f;
			g2.clearrect(bedsizeX * zoom + 30, 1, 480, bedsizeY * zoom+boxheight); // Draw print
			g2.drawrect(bedsizeX * zoom + 30, 1, 480, bedsizeY * zoom+boxheight); // Draw print			
			// bed
			String details;
			int start=0;
			switch (detailstype) {
			case 0:
				 details = "--------------- Model details ------------------\n"+model.getModelDetailReport()+model.guessPrice(model.guessDiameter());
				break;
			case 1:
				 details = "--------------- Layer details ------------------\n"+lay.getLayerDetailReport();
				break;
			case 2:
				details = model.getModelLayerSummaryReport();
				break;
			case 3:
			case 4:
				details = model.getModelLayerSummaryReport();
				String[] lines = details.split("\n");
				if(lines.length*20+30 >= bedsizeY*zoom+boxheight){
					start=(int) (((bedsizeY*zoom+boxheight-30)/20)+1)*(detailstype-2); //TODO calculate number
					System.out.println("START:"+start);
					break;
				}
				detailstype=5; //skip
			case 5:
				 details = model.getModelSpeedReport();
				break;
			case 6:
				details = lay.getLayerSpeedReport();
				break;
			default:
				 details = model.getModelDetailReport();
				break;
			}
			
			
			String[] det = details.split("\n");
			int c=0;
			for (int i = start; i < det.length; i++) {
				int y = 20+c*20; //avoid painting across the border
				c++;
				if(y >= bedsizeY*zoom+boxheight) break;
				g2.drawtext(det[i], bedsizeX*zoom+35,y );
				
			}
			
		}
	}

	private void printLabelBox(GraphicRenderer g2, int boxposp,int bsizepercent, String value, String labl,Layer lay) {
		float boxsize=((bedsizeX*zoom+21)/100)*bsizepercent;
		float boxpos=((bedsizeX*zoom+21)/100)*boxposp;
		float boxheight=(bedsizeX*zoom)/12f;
		float gap=zoom;
		float size=2+10f*(zoom);
		g2.setColor(7);//white
		g2.drawrect(1+boxpos,bedsizeY*zoom+1, boxsize,boxheight);
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
			g.clearrect(200,285,300,75);
			g.drawtext(""+model.getGcodes().size(),220,340);
		}
		g.setFontSize(11.5f);
		}
	
	public void paintHelp(GraphicRenderer g) {
		g.setColor(0);
		
		float boxsize = (bedsizeX*zoom)/2.5f;
		float xbox=(bedsizeX*zoom)/2-boxsize/2;
		float ybox=(bedsizeY*zoom)/2-boxsize/2;
		g.fillrect(xbox,ybox , boxsize+30, boxsize);
		g.drawrect(xbox-3,ybox-3 , boxsize+36, boxsize+6);
		float gap = 5*zoom;
		g.setColor(7);
		
		g2.drawtext("Gcode Simulator "+GcodeSimulator.VERSION,xbox+3 , ybox+gap);
		g2.drawtext("________________________",xbox+3 , ybox+gap+1);
		g2.drawtext("Author: Mathias Dietz ",xbox+3 , ybox+gap*2);
		g2.drawtext("Contact: gcode@dietzm.de ",xbox+3 , ybox+gap*3);
		
		g2.drawtext("Key Shortcuts Help: ",xbox+3 , ybox+gap*5);
		g2.drawtext("________________________",xbox+3 , ybox+gap*5+1);
		g2.drawtext("i/o = Zoom in/out:",xbox+3 , ybox+gap*6);
		g2.drawtext("n/b = Layer next/back",xbox+3 , ybox+gap*7);
		g2.drawtext("m   = Show Model Details",xbox+3 , ybox+gap*8);
		g2.drawtext("t   = Toggle Model Details",xbox+3 , ybox+gap*9);		
		g2.drawtext("f   = Load Gcode File",xbox+3 , ybox+gap*10);
		g2.drawtext("p   = Pause",xbox+3 , ybox+gap*11);
		g2.drawtext("r   = Restart",xbox+3 , ybox+gap*12);
		g2.drawtext("q   = Quit",xbox+3 , ybox+gap*13);
		g2.drawtext("any = Show Help",xbox+3 , ybox+gap*14);
		
		}

	void printBed(GraphicRenderer g2, Layer lay) {
		g2.setColor(7);
		g2.drawrect(1, 1, bedsizeX * zoom, bedsizeY * zoom); // Draw print bed
		g2.drawrect(bedsizeX * zoom + 1, 1, 20, bedsizeY * zoom); // Draw print
		g2.setStroke(2);
		g2.setColor(8);
		for (int i = 10; i < bedsizeX; i=i+10) {
			g2.drawline(i*zoom,1,i*zoom,bedsizeY*zoom);
			g2.drawline(1,i*zoom,bedsizeX*zoom,i*zoom);	
		}
		g2.setStroke(1);															// bed
	
		g2.setColor(lay.getNumber() % 7);
		float factor = (bedsizeY * zoom - 2) / (model.getDimension()[2] * 10);
		g2.fillrect(bedsizeX * zoom + 1, bedsizeY * zoom - (int) ((lay.getZPosition() * 10) * factor), 20,
				(int) (lay.getLayerheight() * 10 * factor)); // Draw level indicator
		
		
		if(Xoffset != 0 || Yoffset != 0){
			g2.setStroke(0);
			g2.setColor(7);
			g2.drawline(Xoffset*zoom-10, Yoffset*zoom, Xoffset*zoom+100, Yoffset*zoom);
			g2.drawline(Xoffset*zoom, Yoffset*zoom+10, Xoffset*zoom, Yoffset*zoom-100);
			g2.setStroke(1);
		}
	}

	float printLine(GraphicRenderer g2, float[] lastpos, float[] pos,float time) {
		float x1 = (lastpos[0]+Xoffset) * zoom;
		float y1 =  (bedsizeY * zoom) - ((lastpos[1]+Yoffset) * zoom);
		float x2 = (pos[0]+Xoffset) * zoom;
		float y2 = (bedsizeY * zoom) - ((pos[1]+Yoffset) * zoom);
		
		//Instead of painting one long line and wait , we split the line into segments to have a smoother painting
		float distx=x2-x1;
		float disty=y2-y1;
		int maxdist = (int)Math.max(Math.abs(distx), Math.abs(disty))/5; //paint each 5mm 
		if( maxdist > 5){
			float stepx=distx/maxdist;
			float stepy=disty/maxdist;
			for (int i = 0; i < maxdist; i++) {
				g2.drawline(x1+(i*stepx),y1+(i*stepy),x1+((i+1)*stepx),y1+((i+1)*stepy));
				g2.repaint();
				try {
					if(!ffLayer && fftoGcode==0 && fftoLayer==0 ){
						Thread.sleep((long)(time/maxdist)); //pause not done here
					}
				} catch (InterruptedException e) {
					time=0;
				}
			}
			return 0; //already slept enough , no sleep in main loop
		}
		
		g2.drawline(x1,y1, x2,y2);
		return time; //not slept, sleep in the main loop
	}

	@Override
	/**
	 * Paint the gcodes to a offline image (offbuf) and trigger repaint
	 */
	public void run() {

		while (true) {
			if (layers != null) {
				g2.clearrect(0, 0, g2.getWidth(),g2.getHeight());
				calculateOffset();
				A: for (Layer lay : layers) {
					float activespeedup = speedup;
					ffLayer=false;
	
					g2.setColor(lay.getNumber() % 7);
					paintLabel(g2, lay);
					printBed(g2, lay);
	
					ArrayList<GCode> gc = lay.getGcodes();
					float[] lastpos = null;
	
					// Paint all Gcodes
					for (GCode gCode : gc) {
						float[] pos = gCode.getCurrentPosition();
						float sleeptime = ((useAccelTime?gCode.getTimeAccel():gCode.getTime())* 1000) / activespeedup;
						if (lastpos != null) {
							if (gCode.isExtruding()) {
								g2.setColor(lay.getNumber() % 7);								
								sleeptime = printLine(g2, lastpos, pos,sleeptime);
							} else if (PAINTTRAVEL) {
								g2.setColor(8);
								g2.setStroke(0);
								sleeptime = printLine(g2, lastpos, pos,sleeptime);
								g2.setStroke(1);
							}
							if (gCode.getFanspeed()!=0){
								//TODO draw a small fan 
							}
						}
						lastpos = pos;
	
						// Sleep for the time the gcode took
						try {
							if (activespeedup != speedup) {
								activespeedup = speedup;
								paintLabel(g2, lay);
							}
							switch (cmd) {
							case EXIT:
								return;
							case OPENFILE:
								cmd = Commands.NOOP;
								g2.clearrect(0, 0, g2.getWidth(), g2.getHeight());
								String file = g2.browseFileDialog();
								if(file != null) {									
									gcodepainter= null;
									model=null;
									layers=null;
									try {
										start(file,null);
									} catch (IOException e) {
										e.printStackTrace();
									}
									return;
								}	
							case NEXTLAYER:
								cmd = Commands.NOOP;
								ffLayer=true;
								break;
							case PREVIOUSLAYER:
								cmd = Commands.NOOP;
								if(fftoLayer==0){
									fftoLayer=lay.getNumber()-1;
								}
								break A;
							case DEBUG:
								cmd = Commands.NOOP;
								System.out.println(gCode);
								break;
							case RESTART:
								cmd = Commands.NOOP;
								g2.clearrect(0, 0, g2.getWidth(), g2.getHeight());
								break A;
							case REPAINTLABEL:
								cmd = Commands.NOOP;
								paintLabel(g2, lay);
								break;
							case HELP:
								cmd = Commands.NOOP;
								paintHelp(g2);
								g2.repaint();
								try {
									Thread.sleep(99999);
								} catch (Exception e) {
								}
							case REPAINTLAYERS:
								cmd = Commands.NOOP;
								if(fftoGcode==0){
									fftoGcode=gCode.getLineindex();
								}
								//System.out.println("Layernum"+fftoGcode);
								break A;
							default:
								break;
							}
						
							
							if(!ffLayer && fftoGcode==0 && fftoLayer==0 ){
								g2.repaint();
								Thread.sleep((int)sleeptime + pause);
							}else{
								if(fftoGcode != 0 && fftoGcode == gCode.getLineindex()){
									fftoGcode=0;
									g2.repaint();
								}
								if(fftoLayer != 0 && fftoLayer == lay.getNumber()){
									fftoLayer=0;
									g2.repaint();
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
			}
			g2.repaint();
		}
	
	}

	public void start(String filename, InputStream in) throws IOException {
		g2.repaint();
		gcodepainter = new Thread(this);
		gcodepainter.start();
		
		model = new Model(filename);
		if(in == null){
			model.loadModel();
		}else{
			model.loadModel(in);
		}
		model.analyze();
		
		layers = new ArrayList<Layer>(model.getLayer().values());
		//Collections.sort(layers);
	
	}

}

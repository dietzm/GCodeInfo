package de.dietzm.gcodesim;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

import de.dietzm.GCode;
import de.dietzm.Layer;
import de.dietzm.Model;

public class GcodePainter implements Runnable {
	public enum Commands {RESTART,NEXTLAYER,REPAINTLABEL,DEBUG,EXIT,OPENFILE,NOOP,REPAINTLAYERS,PREVIOUSLAYER}; 
	
	float zoom = 4;
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
		if(detailstype<4){
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
		g2.clearrect(1, bedsizeY * zoom + 5, bedsizeX * zoom+(modeldetails?457:0), bedsizeY * zoom);
		String label = lay.getLayerSummaryReport() + "   Speed:" + GCode.round2digits(speedup) + "x ("+GCode.round2digits(lay.getTime()/speedup)+")";
		g2.drawtext(label, 1, bedsizeY * zoom + 15);
		String help = "Key Help: i/o=Zoom +/-=Speed n/b=Next/Prev.Layer r=Restart p=Pause q=Quit m=Details t=toggleDetails f=OpenFile";
		g2.drawtext(help, 1, bedsizeY * zoom + 30);
		if (modeldetails){
			g2.clearrect(bedsizeX * zoom + 30, 1, 480, bedsizeY * zoom); // Draw print
			g2.drawrect(bedsizeX * zoom + 30, 1, 480, bedsizeY * zoom); // Draw print			
			// bed
			String details;
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
				 details = model.getModelSpeedReport();
				break;
			case 4:
				details = lay.getLayerSpeedReport();
				break;
			default:
				 details = model.getModelDetailReport();
				break;
			}
			
			
			String[] det = details.split("\n");
			for (int i = 0; i < det.length; i++) {
				int y = 30+i*25; //avoid painting across the border
				if(y >= bedsizeY*zoom) break;
				g2.drawtext(det[i], bedsizeX*zoom+35,y );
				
			}
			
		}
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

	void printLine(GraphicRenderer g2, float[] lastpos, float[] pos) {
		g2.drawline((lastpos[0]+Xoffset) * zoom, (bedsizeY * zoom) - ((lastpos[1]+Yoffset) * zoom), (pos[0]+Xoffset) * zoom,
				(bedsizeY * zoom) - ((pos[1]+Yoffset) * zoom));
	
	}

	@Override
	/**
	 * Paint the gcodes to a offline image (offbuf) and trigger repaint
	 */
	public void run() {
		boolean ffLayer=false; //Fast Forward Layer
		int fftoGcode=0 , fftoLayer=0; //Fast forward to gcode linenumber # / to layer
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
						if (lastpos != null) {
							if (gCode.isExtruding()) {
								g2.setColor(lay.getNumber() % 7);								
								printLine(g2, lastpos, pos);
							} else if (PAINTTRAVEL) {
								g2.setColor(8);
								g2.setStroke(0);
								printLine(g2, lastpos, pos);
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
							case REPAINTLAYERS:
								cmd = Commands.NOOP;
								if(fftoGcode==0){
									fftoGcode=gCode.getLineindex();
								}
								System.out.println("Layernum"+fftoGcode);
								break A;
							default:
								break;
							}
						
							
							if(!ffLayer && fftoGcode==0 && fftoLayer==0){
								g2.repaint();
								Thread.sleep((int) ((gCode.getTime() * 1000) / activespeedup) + pause);
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
				//g2.clearrect(0, 0, bedsizeX + 10, bedsizeY);
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
		Collections.sort(layers);
	
	}

}

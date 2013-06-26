package de.dietzm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import de.dietzm.GCode.GCDEF;
import de.dietzm.Layer.Speed;

public class Model {

	private static double PRICE_PER_G=30/1000d; //Euro per gram 
	private boolean isguessed=false; 
	private static boolean ACCELERATION = true;
	private float avgbedtemp=-1,avgextemp=-1;
	private float avgLayerHeight = -1;
	private float avgspeed=0,avgtravelspeed=0,maxprintspeed=0,minprintspeed=Float.MAX_VALUE;
	private float boundaries[] = { 0, 9999, 0, 9999, 0 }; // Xmax,Xmin,Ymax,Ymin,Zmax
	private float dimension[] = { 0, 0, 0 }; // X,y,z
	private float extrusion=0;
	private String filename;
	private ArrayList<GCode> gcodes = new ArrayList<GCode>();
	private SortedMap<Float, Layer> layer = new TreeMap<Float, Layer>();
	private int layercount = 0, notprintedLayers = 0;
	private SortedMap<Float, SpeedEntry> SpeedAnalysisT = new TreeMap<Float, SpeedEntry>();
	private float time, distance,traveldistance,timeaccel;
	private String unit = "mm"; //default is mm
	public enum Material {PLA,ABS,UNKNOWN};
	private long filesize=0, readbytes=0;
	


	public long getFilesize() {
		return filesize;
	}
	public long getReadbytes() {
		return readbytes;
	}
	public Model(String file) throws IOException {
		this.filename = file;
		//read env variables
		try {
			String kgprice = System.getenv("FILAMENT_PRICE_KG");
			if(kgprice != null){
				PRICE_PER_G=Float.parseFloat(kgprice)/1000;
			}
		} catch (NumberFormatException e) {
			//Use default price (30€)
		}
	}
	
	public Model(String file, ArrayList<GCode> gcall) throws Exception{
		this(file);
		gcodes=gcall;		
	}
	/**
	 * Main method to walk through the GCODES and analyze them. 
	 * @return
	 */
	public void analyze() {
		Layer currLayer = startLayer(0,null);
		Layer lastprinted =currLayer;
		//Current Positions & Speed
		float xpos=0;
		float ypos=0;
		float zpos=0;
		float epos=0;
		float f_old=1000;
		float f_new=f_old;
		float bedtemp=0,exttemp=0;
	
		
		for (GCode gc : getGcodes()) {
			//Initialize Axis  
			if(gc.getGcode() == GCDEF.G28 || gc.getGcode() == GCDEF.G92){
					if(gc.getE() != GCode.UNINITIALIZED) epos=gc.getE();
					if(gc.getX() != GCode.UNINITIALIZED) xpos=gc.getX();
					if(gc.getY() != GCode.UNINITIALIZED) ypos=gc.getY();
					if(gc.getZ() != GCode.UNINITIALIZED) zpos=gc.getZ();
			}
			
			//Update Speed if specified
			//TODO Clarify if default speed is the last used speed or not
			if(gc.getF() != GCode.UNINITIALIZED){
				if(gc.getX() == GCode.UNINITIALIZED && gc.getY()==GCode.UNINITIALIZED && gc.getZ()==GCode.UNINITIALIZED || !ACCELERATION){
					f_old=gc.getF(); //no movement no acceleration
					f_new=gc.getF(); //faccel is the same
				}else{
					f_new=gc.getF(); //acceleration
				}
			}
			
			//update Temperature if specified
			if(gc.getS_Ext() !=  GCode.UNINITIALIZED){
				exttemp=gc.getExtemp();
			//Update Bed Temperature if specified
			}else if(gc.getS_Bed() !=  GCode.UNINITIALIZED){ 
				bedtemp=gc.getBedtemp();
			}
			

			if (gc.getGcode() == GCDEF.G1 || gc.getGcode() == GCDEF.G0 || gc.getGcode() == GCDEF.G2 || gc.getGcode() == GCDEF.G3) {
				gc.setExtemp(exttemp); //Make sure all gcodes know the current temp
				gc.setBedtemp(bedtemp);
				//Detect Layer change and create new layers.
				if(gc.getZ() != GCode.UNINITIALIZED && gc.getZ() != currLayer.getZPosition()){
					//endLayer(currLayer);	//finish old layer
					if(currLayer.isPrinted()) lastprinted=currLayer;
					currLayer = startLayer(gc.getZ(),lastprinted);//Start new layer
				}	
				float move = 0;
				//Move G1 - X/Y at the same time
				if (gc.getGcode() == GCDEF.G2 || gc.getGcode() == GCDEF.G3){
					//center I&J relative to x&y
					float cx = (xpos+gc.getIx());
					float cy = (ypos+gc.getJy());
					float newxpos = gc.getX() != GCode.UNINITIALIZED ? gc.getX():xpos;
					float newypos = gc.getY() != GCode.UNINITIALIZED ? gc.getY():ypos;
					//triangle
					float bx=(newxpos-cx);
					float by=(newypos-cy);
					float ax=(xpos-cx);
					float ay=(ypos-cy);
					//Java drawarc is based on a bonding box
					//Left upper edge of the bounding box
					float xmove = Math.abs(cx-xpos);
					float ymove = Math.abs(cy-ypos);
					//assume a circle (no oval)
					float radius = ((float) Math.sqrt((xmove * xmove) + (ymove * ymove)));
					double angle1 ,angle2 ;
					//Calculate right angle
					if(gc.getGcode() == GCDEF.G2){
						angle1 = Math.atan2(by,bx) * (180/Math.PI);
						angle2 = Math.atan2(ay,ax) * (180/Math.PI);
					}else{
						angle2 = Math.atan2(by,bx) * (180/Math.PI);
						angle1 = Math.atan2(ay,ax) * (180/Math.PI);
					}
					double angle=(int) (angle2-angle1);
					
					xpos=newxpos;
					ypos=newypos;
					//Bogenlaenge
					move = (float) (Math.PI * radius * angle / 180);
					gc.setDistance(move);
				}else if (gc.getX() != GCode.UNINITIALIZED && gc.getY() != GCode.UNINITIALIZED) {
					float xmove = Math.abs(xpos - gc.getX());
					float ymove = Math.abs(ypos - gc.getY());
					if (xmove + ymove == 0)
						continue;
					xpos = gc.getX();
					ypos = gc.getY();
					move = (float) Math.sqrt((xmove * xmove) + (ymove * ymove));
					gc.setDistance(move);
				} else if (gc.getX() != GCode.UNINITIALIZED) {
					move = Math.abs(xpos - gc.getX());
					xpos = gc.getX();
					gc.setDistance(move);
				} else if (gc.getY() != GCode.UNINITIALIZED) {
					move = Math.abs(ypos - gc.getY());
					ypos = gc.getY();
					gc.setDistance(move);
				} else if (gc.getE() != GCode.UNINITIALIZED) {
					//Only E means we need to measure the time
					move = Math.abs(epos - gc.getE());
				} else	if (gc.getZ() != GCode.UNINITIALIZED) {
					//Only Z means we need to measure the time
					//TODO if Z + others move together, Z might take longest. Need to add time 
					move = Math.abs(zpos - gc.getZ());
					
				}	
				//update Z pos when Z changed 
				if (gc.getZ() != GCode.UNINITIALIZED) {
					zpos = gc.getZ();
				}
				//Update epos and extrusion, not add time because the actual move time is already added
				 if(gc.getE() != GCode.UNINITIALIZED ){
					    gc.setExtrusion(gc.getE()-epos);
						epos=gc.getE();
				 }
				 
				 gc.setTime(move / (f_new / 60)); //Set time w/o acceleration
				 if(f_new >= f_old){
					 //Assume sprinter _MAX_START_SPEED_UNITS_PER_SECOND {40.0,40.0,....}
					 gc.setTimeAccel(move / (((Math.min(40*60,f_old)+f_new)/2) / 60)); //set time with linear acceleration
					 //System.out.println("F"+f_old+"FA"+f_new+"time"+gc.getTime()+"ACCEL: "+(Math.abs(40-f_new)/gc.getTimeAccel()));
				 }else{
					 gc.setTimeAccel(move / ((f_old+f_new)/2 / 60)); //set time with linear acceleration
					 //System.out.println("F"+f_old+"FA"+f_new+"  DEACCEL: "+(Math.abs(f_old-f_new)/gc.getTimeAccel()));
				 }
				 f_old=f_new; //acceleration done. assign new speed
					
				 //Calculate print size
				 if(gc.getE() != GCode.UNINITIALIZED && gc.getE() > 0) { 
					currLayer.addPosition(xpos, ypos,zpos);					
				 }
			}
			
			//Assume that unit is only set once 
			if(gc.getGcode() == GCDEF.G20 || gc.getGcode() == GCDEF.G21){
				currLayer.setUnit(gc.getUnit());
			}
		
			if(gc.getFanspeed() != GCode.UNINITIALIZED){
				currLayer.setFanspeed((int)gc.getFanspeed());
			}
			
			gc.setCurrentPosition(new float[]{xpos,ypos,zpos});	
			//Add Gcode to Layer
			currLayer.addGcodes(gc);
		}
		//System.out.println("Summarize Layers");
		for (Layer closelayer : layer.values()) {
			endLayer(closelayer);	//finish old layer			
		}
		//End last layer
		//endLayer(currLayer);
		
	}

	void endLayer(Layer lay) {
		time += lay.getTime();
		timeaccel += lay.getTimeAccel();
		distance += lay.getDistance();
		traveldistance += lay.getTraveldistance();


		
		
		// Count layers which are visited only
		if (!lay.isPrinted()) {
			notprintedLayers++;
		//	lay.setLayerheight(0); // Layer not printed
		} else {
			// calculate dimensions
			boundaries[0] = Math.max(lay.getBoundaries()[0], boundaries[0]);
			boundaries[1] = Math.min(lay.getBoundaries()[1], boundaries[1]);
			boundaries[2] = Math.max(lay.getBoundaries()[2], boundaries[2]);
			boundaries[3] = Math.min(lay.getBoundaries()[3], boundaries[3]);
			boundaries[4] = Math.max(lay.getBoundaries()[4], boundaries[4]);
			dimension[0] = GCode.round2digits(boundaries[0] - boundaries[1]);
			dimension[1] = GCode.round2digits(boundaries[2] - boundaries[3]);
			dimension[2] = GCode.round2digits(boundaries[4]);
			
			
			extrusion=extrusion+lay.getExtrusion();
			
			if(avgLayerHeight == -1) avgLayerHeight=lay.getLayerheight(); //first printed layer
			if(avgextemp == -1) avgextemp=lay.getExttemp(); //first printed layer
			if(avgbedtemp == -1) avgbedtemp=lay.getBedtemp();//first printed layer
			avgbedtemp= GCode.round2digits((avgbedtemp + lay.getBedtemp()) / 2);
			avgextemp= GCode.round2digits((avgextemp + lay.getExttemp()) / 2);
			avgLayerHeight = GCode.round2digits((avgLayerHeight + lay
					.getLayerheight()) / 2);
		}

		//Summarize Speed values
		float sp = lay.getSpeed(Speed.SPEED_ALL_AVG);
		if(sp != Float.NaN && sp > 0){
			//average speed is relative to distance / divide by distance later
			avgspeed += (sp*lay.getDistance());
		}
		//Print/extrude only
		maxprintspeed=Math.max(maxprintspeed,lay.getSpeed(Speed.SPEED_PRINT_MAX));
		if(lay.getSpeed(Speed.SPEED_PRINT_MIN) != 0){
			minprintspeed=Math.min(minprintspeed,lay.getSpeed(Speed.SPEED_PRINT_MIN));
		}
		sp = lay.getSpeed(Speed.SPEED_TRAVEL_AVG);
		if(sp != Float.NaN && sp > 0){
			avgtravelspeed+= sp*lay.getTraveldistance();
		}
		
		// Update Speed Analysis for model ... combine layer data
		for (Iterator<Float> iterator = lay.getSpeedAnalysisT().keySet()
				.iterator(); iterator.hasNext();) {
			float speedkey = iterator.next();
			SpeedEntry timespeedlay = lay.getSpeedAnalysisT().get(speedkey);
			SpeedEntry speedsum = SpeedAnalysisT.get(speedkey);
			if (speedsum != null) {
				speedsum.addTime(timespeedlay.getTime());
				speedsum.addDistance(timespeedlay.getDistance());
				speedsum.setPrint(timespeedlay.getType());
				speedsum.addLayers(lay.getNumber());
			} else {
				SpeedEntry sped = new SpeedEntry(speedkey,timespeedlay.getTime(),lay.getNumber());
				sped.addDistance(timespeedlay.getDistance());
				sped.setPrint(timespeedlay.getType());
				SpeedAnalysisT.put(speedkey, sped);
			}
		}
		//Assume that unit is only set once
		unit=lay.getUnit();
	//	LayerOpen = false;
	}

	public float getAvgbedtemp() {
		return avgbedtemp;
	}

	public float getAvgextemp() {
		return avgextemp;
	}
	
	public Material guessMaterial(){
		if(avgextemp <= 205 && avgextemp > 140){
			return Material.PLA;
		}
		if(avgextemp < 290 && avgextemp > 205){
			return Material.ABS;
		}
		return Material.UNKNOWN;
	}
	
	public String guessPrice(float diameter){
		String var 	= "";
		if(diameter==0) {
			System.err.println("Unable to guess diameter, show results for 3mm. Set Environment var FILAMENT_DIAMETER to overwite.\n");
			diameter=3;
		}
		
		double mm3 = (diameter/2)*(diameter/2)*Math.PI*getExtrusion();
		double weigth=0; 
		
		switch (guessMaterial()) {
		case PLA:
			weigth= mm3*0.00125;
		case ABS:
			weigth= mm3*0.00105;
		default:
			break;
		}
		
		double priceg = weigth*PRICE_PER_G;
		
		var 	   +="Material"+(isguessed?"(guessed):":":")+guessMaterial()+" "+diameter+"mm\n";
		var		   +="Mass:   "+GCode.round2digits((float)mm3/1000)+"cm3\n";
		var		   +="Weight: "+GCode.round2digits((float)weigth)+"g\n";
		var		   +="Price:  "+GCode.round2digits((float)priceg)+"€\n";
		
		
		return var;
		
	}
	
	/**
	 * Guess diameter 
	 * 1) user defined environment variable
	 * 2) comments in GCODE file
	 * 3) Fallback to some very rough calculation
	 * @return diameter
	 */
	public float guessDiameter(){
		//Read user defined environment variable if exists
		try {
			String dia = System.getenv("FILAMENT_DIAMETER");
			if(dia != null){
				//System.out.println("Use env value FILAMENT_DIAMETER="+dia);
				return Float.parseFloat(dia);
			}
		} catch (NumberFormatException e1) {
		}
		
		//get diameter from comments in gcode file
		try {
			ArrayList<GCode> codes = getGcodes();
			for (GCode gCode : codes) {
				//Ignore comments behind gcodes
				if (gCode.isComment()){
					//System.out.println("COMMENT"+gCode.getComment());
					if(gCode.getComment().matches(".*FILAMENT_DIAMETER\\s=.*")){ //SLICER
						//System.out.println("MATCHES:"+gCode.getComment());
						String[] res =gCode.getComment().split("=");
						return Float.parseFloat(res[1]);
					}else if(gCode.getComment().matches(".*FILAMENT_DIAMETER_.*")){ //SKEINFORGE
						//System.out.println("MATCHES:"+gCode.getComment());
						String[] res =gCode.getComment().split("[:<]");
						return Float.parseFloat(res[2]);					
					}
				}
			}
		} catch (Exception e) {
			//Comment parsing failed
		}
	//	System.err.println("Failed to parse GCODE comments for filament diameter. Fallback to guessing.");
		//Fallback ... no comments found
		isguessed=true;
		//Tried many formulars but there is no good way to guess the diameter (too many unknowns)
		//
		float exRadius=getAvgLayerHeight()/2; 
		float WOT = 2.1f;  //Assume a wide over thickness value of ~2.1 (heavily depends on nozzel size)
		double extrArea =  exRadius*(exRadius*WOT) * Math.PI; //Fläche extruded mm2
		double menge = extrArea*getDistance();
		double sizeArea = menge/getExtrusion();
		double guessedDia = Math.sqrt(sizeArea/Math.PI)*2;
	//	System.out.println("Extr menge mm3:"+menge/1000+" Estimate dia:"+guessedDia);
		
		//Either take 1.75 or 3mm
		if(guessedDia > 2.45f){
			return 3;
		}else if(guessedDia < 2.05f){
			return 1.75f;
		}
		//show both
		return 0;
	}
	
	

	public float getAvgLayerHeight() {
		return avgLayerHeight;
	}
	
	public float[] getBoundaries() {
		return boundaries;
	}

	
	public float[] getDimension() {
		return dimension;
	}

	public float getDistance() {
		return GCode.round2digits(distance);
	}

	public float getExtrusion() {
		return GCode.round2digits(extrusion);
	}

	public String getFilename() {
		return filename;
	}
	public int getGcodecount() {
		return gcodes.size();
	}

	public ArrayList<GCode> getGcodes() {
		return gcodes;
	}

	public SortedMap<Float, Layer> getLayer() {
		return layer;
	}
	
	public Layer getLayer(int number) {
		//TODO: inefficient ! use quicksearch instead
		for (Layer lay : layer.values()) {
			if(lay.getNumber()==number){
				return lay;
			}
		}
		return null;
	}

	public int getLayercount(boolean printedonly) {
		if (printedonly)
			return layercount - notprintedLayers;
		return layercount;
	}

	/**
	 * Get the average x/y move speed (ignoring gcodes with zero speed)
	 * Specify a speed type (All incl travel, printing/extruding moves only, travel moves only)
	 * @return speed in mm/s
	 */
	public float getSpeed(Speed type) {
		switch (type) {
		case SPEED_ALL_AVG:
			return GCode.round2digits(avgspeed/distance);
		case SPEED_TRAVEL_AVG:
			return GCode.round2digits((avgtravelspeed/traveldistance));
		case SPEED_PRINT_AVG:
			return GCode.round2digits((avgspeed-avgtravelspeed)/(distance-traveldistance));
		case SPEED_PRINT_MAX:
			return maxprintspeed;
		case SPEED_PRINT_MIN:
			return minprintspeed;
		default:
			return 0;
		}
		
	}

	public SortedMap<Float, SpeedEntry> getSpeedAnalysisT() {
		return SpeedAnalysisT;
	}

	public float getTime() {
		return GCode.round2digits(time);
	}

	public String getUnit() {
		return unit;
	}
	public boolean loadModel()throws IOException{
		FileInputStream fread =  new FileInputStream(filename);
		File f = new File(filename);
		filesize= f.length();
		return loadModel(fread);
	}
	
	
	public boolean loadModel(InputStream in)throws IOException{
		InputStreamReader fread =  new InputStreamReader(in);
		BufferedReader gcread= new BufferedReader(fread);
		ArrayList<GCode> codes = getGcodes();
		String line;
		int idx=1;
		int errorcnt=0, success=0;
		while((line=gcread.readLine())!=null){
			GCode gc=new GCode(line,idx++);
			if(!gc.parseGcode()){
				errorcnt++;
				if(errorcnt-success > 5){
					System.err.println("Too many errors while reading GCODE file.");
					System.exit(2);
				}
			}else{
				success++;
			}
			codes.add(gc);
			readbytes+=line.getBytes().length;
		}
		gcread.close();
		if(errorcnt != 0){
			System.err.println("Detected "+errorcnt+" error(s) during parsing of Gcode file. Results might be wrong.");
			if(idx/errorcnt < 50){
				codes.clear();
				return false;
			}
		}
		return true;
	}
	public void saveModel(String newfilename)throws IOException{
		FileWriter fwr =  new FileWriter(newfilename);
		BufferedWriter gcwr= new BufferedWriter(fwr);
		
		for (GCode gc : gcodes) {
			gcwr.write(gc.getCodeline());
			gcwr.write("\n");
		}
		gcwr.close();
		fwr.close();
	}
	
	public String getModelComments(){
		String var ="--------- Slicer Comments------------\n";
		for (GCode gCode : gcodes) {
			//Ignore comments behind gcodes
			if (gCode.isComment()){
				//System.out.println(gCode.getComment());
				var+=gCode.getComment()+"\n";
			}
		}
		return var;
	}
	public String getModelDetailReport(){
		float time = getTime();
		float[] sizes = getDimension();
		float[] bound = getBoundaries();
		String mm_in = getUnit();
		String var =("Filename:  "+getFilename()+"\n");
		var+=("Layers visited:  "+getLayercount(false)+"\n");
		var+=("Layers printed:  "+getLayercount(true)+"\n");
		var+=("Avg.Layerheight: "+getAvgLayerHeight()+mm_in+"\n");
		var+=("Size:            "+sizes[0]+mm_in+" x "+sizes[1]+mm_in+" H"+sizes[2]+mm_in+"\n");
		var+=("Position on bed: "+GCode.round2digits(bound[1])+"/"+GCode.round2digits(bound[0])+mm_in+" x "+GCode.round2digits(bound[3])+"/"+GCode.round2digits(bound[2])+mm_in+" H"+GCode.round2digits(bound[4])+mm_in+"\n");
		var+=("XY Distance:     "+getDistance()+mm_in+"\n");
		var+=("Extrusion:       "+getExtrusion()+mm_in+"\n");
		var+=("Bed Temperatur:  "+getAvgbedtemp()+"°\n");
		var+=("Ext Temperatur:  "+getAvgextemp()+"°\n");
		var+=("Avg.Speed(All):    "+getSpeed(Speed.SPEED_ALL_AVG)+mm_in+"/s\n");
		var+=("Avg.Speed(Print):  "+getSpeed(Speed.SPEED_PRINT_AVG)+mm_in+"/s\n");
		var+=("Avg.Speed(Travel): "+getSpeed(Speed.SPEED_TRAVEL_AVG)+mm_in+"/s\n");
		var+=("Max.Speed(Print):  "+getSpeed(Speed.SPEED_PRINT_MAX)+mm_in+"/s\n");
		var+=("Min.Speed(Print):  "+getSpeed(Speed.SPEED_PRINT_MIN)+mm_in+"/s\n");
		var+=("Gcode Lines:     "+getGcodecount()+"\n");
		var+=("Overall Time (w/o Acceleration):   "+GCode.formatTimetoHHMMSS(time)+ " ("+time+"sec)\n");
		var+=("Overall Time (w/ Acceleration):    "+GCode.formatTimetoHHMMSS(timeaccel)+ " ("+timeaccel+"sec)\n");
		return var;
	}
	public String getModelSpeedReport(){
		String var="---------- Model Speed Distribution ------------";
		//TODO: use stringbuffer instead of string concatenation 
		ArrayList<Float> speeds = new ArrayList<Float>(getSpeedAnalysisT().keySet());
		for (Iterator<Float> iterator = speeds.iterator(); iterator.hasNext();) {
			float speedval =  iterator.next();
			SpeedEntry tim = getSpeedAnalysisT().get(speedval);
			var=var+"\n\tSpeed "+speedval+
					"  \t"+tim.getType()+
					"  \tTime:"+GCode.round2digits(tim.getTime())+"sec   \t\t"
					+GCode.round2digits(tim.getTime()/(time/100))+"%"+
					"  \t Layers:[";	
			int max=4;
			//print the layer nr but only max of 4 (too much of info)
			for (Iterator<Integer> layrs = tim.getLayers().iterator(); layrs.hasNext();) {
					var=var+" "+layrs.next();
					max--;
					if(max==0){
						var+=" ...";
						break;
					}
			}
			var+=" ]";
		}

		return var;
	}
	public String getModelLayerSummaryReport(){
		ArrayList<Layer> layers = new ArrayList<Layer>(getLayer().values());
		//Collections.sort(layers);
		//TODO: use stringbuffer instead of string concatenation 
		String var="---------- Printed Layer Summary ------------\n";
		for (Iterator<Layer> iterator = layers.iterator(); iterator.hasNext();) {
			Layer lay = iterator.next();
			if(!lay.isPrinted()) continue;
			float layperc =  GCode.round2digits(lay.getTime()/(getTime()/100));
			var=var+"\t"+lay.getLayerSummaryReport() + " \t "+layperc+ "%\n"; 
		}
		return var;
	}
	Layer startLayer(float z, Layer prevLayer) {
		//if (LayerOpen)
		//	return null;
		
		Layer lay = layer.get(z);
		if (lay == null) {
			int fanspeed=0;
			float lh = z;
			if (prevLayer != null && prevLayer.isPrinted()) {
				lh = (z - prevLayer.getZPosition());
				fanspeed=prevLayer.getFanspeed();
			} 
			lay = new Layer(z, layercount,GCode.round2digits(lh));
			lay.setUnit(unit); //remember last unit
			lay.setFanspeed(fanspeed);
			layer.put(z, lay);
			layercount++;
		}else{
			//System.out.println("Layer already exists:"+z);
		}
		// System.out.println("Add Layer:"+z);
		//LayerOpen = true;
		return lay;
	}
	public float getTimeaccel() {
		return timeaccel;
	}
	public static void deleteLayer(Collection<Layer> lays) {
		System.out.println("Delete Layer "+lays);
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
			for (GCode gCode : gcodes) {
				gCode.changeToComment();
				gCode.parseGcode();
			}
		}
	}
	public static void changeFan(Collection<Layer> lays, int value) {
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
			for (GCode gCode : gcodes) {
				gCode.changeFan(value);
				//todo... add fan value if not exits
			}
		}
	}
	public static void changeXOffset(Collection<Layer> lays, float value) {
		System.out.println("Add X Offset "+value);
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
			for (GCode gCode : gcodes) {
				gCode.changeXOffset(value);
			}
		}
	}
	public static void changeYOffset(Collection<Layer> lays, float value) {
		System.out.println("Add Y Offset "+value);
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
			for (GCode gCode : gcodes) {
				gCode.changeYOffset(value);
			}
		}
	}
	public static void changeZOffset(Collection<Layer> lays, float value) {
		System.out.println("Add Z Offset "+value);
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
			for (GCode gCode : gcodes) {
				gCode.changeZOffset(value);
			}
		}
	}
	public static void changeLayerHeight(Collection<Layer> lays, int value) {
		System.out.println("Change Layerheight by "+value+"%");
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
			for (GCode gCode : gcodes) {
				gCode.changeLayerHeight(value);
			}
		}
	}
	public static void changeBedTemp(Collection<Layer> lays, float value) {
		System.out.println("Set Bed temp to "+value);
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
		for (GCode gCode : gcodes) {
				gCode.changeBedTemp(value);
				//update temps, but always add a temp at the beginning of the layer
				//TODO: if a temp definitions exists before G1/G2/G3 , do not insert a new one
		}
		}
	}
	public static void changeExtTemp(Collection<Layer> lays, float value) {
		System.out.println("Set Extruder temp to "+value);
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
		for (GCode gCode : gcodes) {
			gCode.changeExtTemp(value);
				//update temps, but always add a temp at the beginning of the layer
				//TODO: if a temp definitions exists before G1/G2/G3 , do not insert a new one						
		}
		}
	}
	public static void changeExtrusion(Collection<Layer> lays, int value) {
		System.out.println("Change Extrusion by "+value+"%");
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
			for (GCode gCode : gcodes) {
				gCode.changeExtrusion(value);
			}
		}
	}
	public static void changeSpeed(Collection<Layer> lays, int value) {
		System.out.println("Change Speed by "+value+"%" );
		for (Layer layer : lays) {
			ArrayList<GCode> gcodes = layer.getGcodes();
			for (GCode gCode : gcodes) {
				gCode.changeSpeed(value,true);
			}
		}
	}

}

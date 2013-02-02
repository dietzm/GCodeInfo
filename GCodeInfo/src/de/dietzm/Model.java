package de.dietzm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

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
	private HashMap<Float, Layer> layer = new HashMap<Float, Layer>();
	private int layercount = 0, notprintedLayers = 0;
	private HashMap<Float, Float> SpeedAnalysisT = new HashMap<Float, Float>();
	private float time, distance,traveldistance;
	private String unit = "mm"; //default is mm
	public enum Material {PLA,ABS,UNKNOWN};
	


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
		float f=1000;
		float faccel=f;
		float bedtemp=0,exttemp=0;
	
		
		for (GCode gc : getGcodes()) {
			//Initialize Axis  
			if("G92".equals(gc.getGcode()) || "G28".equals(gc.getGcode())){
					if(gc.getE() != GCode.UNINITIALIZED) epos=gc.getE();
					if(gc.getX() != GCode.UNINITIALIZED) xpos=gc.getX();
					if(gc.getY() != GCode.UNINITIALIZED) ypos=gc.getY();
					if(gc.getZ() != GCode.UNINITIALIZED) zpos=gc.getZ();
			}
			
			//Update Speed if specified
			//TODO Clarify if default speed is the last used speed or not
			//TODO implement acceleration 
			if(gc.getF() != GCode.UNINITIALIZED){
				if(gc.getX() == GCode.UNINITIALIZED && gc.getY()==GCode.UNINITIALIZED && gc.getZ()==GCode.UNINITIALIZED || !ACCELERATION){
					f=gc.getF(); //no movement no acceleration
					faccel=gc.getF(); //faccel is the same
				}else{
					faccel=gc.getF(); //acceleration
				}
			}
			
			//update Temperature if specified
			if(gc.getExtemp() !=  GCode.UNINITIALIZED){
				exttemp=gc.getExtemp();
			//Update Bed Temperature if specified
			}else if(gc.getBedtemp() !=  GCode.UNINITIALIZED){ 
				bedtemp=gc.getBedtemp();
			}
			

			if ("G1".equals(gc.getGcode()) || "G0".equals(gc.getGcode())) {
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
				if (gc.getX() != GCode.UNINITIALIZED && gc.getY() != GCode.UNINITIALIZED) {
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
				 
				 gc.setTime(move / (((f+faccel)/2) / 60));
				 f=faccel; //acceleraction done. assign new speed
					
				 //Calculate print size
				 if(gc.getE() != GCode.UNINITIALIZED && gc.getE() > 0) { 
					currLayer.addPosition(xpos, ypos,zpos);					
				 }
			}
			
			//Assume that unit is only set once 
			if("G20".equals(gc.getGcode()) || "G21".equals(gc.getGcode())){
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
			Float timespeedlay = lay.getSpeedAnalysisT().get(speedkey);
			Float timeforspeed = SpeedAnalysisT.get(speedkey);
			//TODO: Introduce a speed object and remember which speeds are used by which layer
			if (timeforspeed != null) {
				float newtimesp = timeforspeed.floatValue() + timespeedlay;
				SpeedAnalysisT.put(speedkey, newtimesp);
			} else {
				SpeedAnalysisT.put(speedkey, timespeedlay);
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
				if (gCode.getGcode() == null && gCode.getComment() != null){
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

	public HashMap<Float, Layer> getLayer() {
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

	public HashMap<Float, Float> getSpeedAnalysisT() {
		return SpeedAnalysisT;
	}

	public float getTime() {
		return GCode.round2digits(time);
	}

	public String getUnit() {
		return unit;
	}
	public void loadModel()throws IOException{
		FileReader fread =  new FileReader(filename);
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
		}
		gcread.close();
		if(errorcnt != 0){
			System.err.println("Detected "+errorcnt+" error(s) during parsing of Gcode file. Results might be wrong.");
		}
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
		var+=("Overall Time:    "+GCode.formatTimetoHHMMSS(time)+ " ("+time+"sec)\n");
		return var;
	}
	public String getModelSpeedReport(){
		String var="---------- Model Speed Distribution ------------";
		//TODO: use stringbuffer instead of string concatenation 
		ArrayList<Float> speeds = new ArrayList<Float>(getSpeedAnalysisT().keySet());
		Collections.sort(speeds);
		for (Iterator<Float> iterator = speeds.iterator(); iterator.hasNext();) {
			float speedval =  iterator.next();
			float tim = getSpeedAnalysisT().get(speedval);
			var=var+"\n\tSpeed "+speedval+
					" \tTime:"+GCode.round2digits(tim)+"sec\t\t"+GCode.round2digits(tim/(time/100))+"%";				
		}
		return var;
	}
	public String getModelLayerSummaryReport(){
		ArrayList<Layer> layers = new ArrayList<Layer>(getLayer().values());
		Collections.sort(layers);
		//TODO: use stringbuffer instead of string concatenation 
		String var="---------- Printed Layer Summary ------------\n";
		for (Iterator<Layer> iterator = layers.iterator(); iterator.hasNext();) {
			Layer lay = iterator.next();
			if(!lay.isPrinted()) continue;
			float layperc =  GCode.round2digits(lay.getTime()/(getTime()/100));
			var=var+"\t"+lay.getLayerSummaryReport() + "\t "+layperc+ "%\n"; 
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

}

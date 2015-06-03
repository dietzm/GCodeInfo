package de.dietzm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import de.dietzm.SpeedEntry.Speedtype;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeStore;

public class Layer implements Comparable<Layer>{
	
	public static enum Speed {
		SPEED_ALL_AVG,SPEED_PRINT_AVG,SPEED_PRINT_MAX,SPEED_PRINT_MIN,SPEED_TRAVEL_AVG
	}

	private float avgspeed=0,avgtravelspeed=0,maxprintspeed=0,minprintspeed=Float.MAX_VALUE;
	private float bedtemp,exttemp;
	//boundary != dimension e.g.
	//dimension of layer#1=20x20, Layer#2=20x20 but dimesion of model is 40x40 
	private float boundaries[] = {0,9999,0,9999,0}; //Xmax,Xmin,Ymax,Ymin,Zmax

	private float dimension[] = new float[3]; //X,y,z

	private float distance=0;
	private float extrusion=0;
	private float fantime=0; //number of gcodes with fan turned on
	private int fanspeed=0; 

	private float Layerheight;
	private boolean isprinted =false;

	private int number;
	private SortedMap<Float,SpeedEntry> SpeedAnalysisT = new TreeMap<Float,SpeedEntry>();
//	private float time = 0;
	private float timeaccel=0;
	float traveldistance=0;
	private String unit = "mm"; //default is mm

	private float zPosition;
	 //index of the gcodes in the GCodeStore belonging to this layer
	public int lowidx = -1;
	public int highidx = -1;

	public Layer(float zPosition){
		this.zPosition=zPosition;
	}
	public Layer(float zPosition, int num, float lheight){
		this.zPosition=zPosition;
		this.number=num;
		this.Layerheight=lheight;
	}



	void addGcodes(GCode gcode, int idx) {
		if(lowidx==-1) lowidx=idx;
		highidx=idx;
		
	//	time=time+gcode.getTimeAccel();
		timeaccel=timeaccel+gcode.getTimeAccel();
		distance=distance+gcode.getDistance();
		if(!gcode.isExtruding()){
			traveldistance+= gcode.getDistance();
		}
		
		
			gcode.setFanspeed(fanspeed); //Update follow on gcodes
		if(fanspeed!=0) {
			fantime+=gcode.getTimeAccel();
		}
		
		float sp = gcode.getSpeed();
		if(sp != Float.NaN && sp > 0){
			//average speed is relative to distance / divide by distance later
			avgspeed = (avgspeed + (sp*gcode.getDistance()));
			
			categorizeSpeed(gcode, sp);
			
			if(gcode.isExtruding()){
				isprinted=true;
				//Print/extrude only
				maxprintspeed=Math.max(maxprintspeed,sp);
				minprintspeed=Math.min(minprintspeed,sp);

			}else{
				//Travel only
				avgtravelspeed+= sp*gcode.getDistance();
			}
		}
		
//		//Update temp only if extruding (temp changes at the end of the layer should not change layer temp)
//		if(gcode.isInitialized(Constants.SB_MASK)){
//			bedtemp=gcode.getBedtemp();
//		}
//		if(gcode.isInitialized(Constants.SE_MASK)){
//			exttemp=gcode.getExtemp();
//		}
		
		//
			
			extrusion=extrusion+gcode.getExtrusion();
	
	}



	public int getFanspeed() {
		return fanspeed;
	}
	public void setFanspeed(int fanspeed) {
		this.fanspeed = fanspeed;
	}
	void addPosition(float x, float y,float z){
		//System.out.println("BoundOLD:"+boundaries[0]+"/"+boundaries[1]);
		//System.out.println("X:"+x);
		boundaries[0]=Math.max(x,boundaries[0]);
		boundaries[1]=Math.min(x,boundaries[1]);
		boundaries[2]=Math.max(y,boundaries[2]);
		boundaries[3]=Math.min(y,boundaries[3]);
		boundaries[4]=Math.max(z,boundaries[4]);
		dimension[0]=boundaries[0]-boundaries[1];
		dimension[1]=boundaries[2]-boundaries[3];
		dimension[2]=Layerheight;
		//System.out.println("BoundNEW:"+boundaries[0]+"/"+boundaries[1]);
		
	}

	/**
	 * Summarize the required time for all movements with the same speed.
	 * This can help to understand which speed type (infill, perimeters) has the
	 * biggest impact on print time 
	 * @param gcode
	 * @param sp
	 */
	private void categorizeSpeed(GCode gcode, float sp) {
		sp = Math.round(sp);
		//Categorize movements by speed, to recognize which movement type takes longest.
		SpeedEntry timeforspeed = SpeedAnalysisT.get(sp);
		if (timeforspeed != null){
			timeforspeed.addTime(gcode.getTimeAccel());
			timeforspeed.addDistance(gcode.getDistance());
			if(gcode.isExtruding()){
				timeforspeed.setPrint(Speedtype.PRINT);
			}else{
				timeforspeed.setPrint(Speedtype.TRAVEL);
			}
		}else{
			SpeedEntry sped = new SpeedEntry(sp,gcode.getTimeAccel(),number);
			sped.addDistance(gcode.getDistance());
			if(gcode.isExtruding()){
				sped.setPrint(Speedtype.PRINT);
			}else{
				sped.setPrint(Speedtype.TRAVEL);
			}
			SpeedAnalysisT.put(sp, sped);
		}
	}
	@Override
	public int compareTo(Layer o) {		
		return Float.compare(zPosition,o.getZPosition());
	}
	public float getBedtemp() {
		return bedtemp;
	}
	public float[] getBoundaries() {
		return boundaries;
	}
	public float[] getDimension() {
		return dimension;
	}
	/**
	 * Moved X/Y Distance. (incl Travel)  
	 * @return
	 */
	public float getDistance() {
		return distance;
	}



	public float getExtrusion() {
		return extrusion;
	}

	public float getExttemp() {
		return exttemp;
	}
	
	protected void setExttemp(float ext) {
		exttemp=ext;
	}
	
	protected void setBedtemp(float bet) {
		bedtemp=bet;
	}
	

	public float getLayerheight() {
		return Layerheight;
	}
	
	
	public int getNumber() {
		return number;
	}



	/**
	 * Get the average x/y move speed (ignoring gcodes with zero speed)
	 * Specify a speed type (All incl travel, printing/extruding moves only, travel moves only)
	 * @return speed in mm/s
	 */
	public float getSpeed(Speed type) {
		switch (type) {
		case SPEED_ALL_AVG:
			return Constants.round2digits(avgspeed/distance);
		case SPEED_TRAVEL_AVG:
			return Constants.round2digits((avgtravelspeed/traveldistance));
		case SPEED_PRINT_AVG:
			return Constants.round2digits((avgspeed-avgtravelspeed)/(distance-traveldistance));
		case SPEED_PRINT_MAX:
			return maxprintspeed;
		case SPEED_PRINT_MIN:
			if (minprintspeed == Float.MAX_VALUE) return 0;
			return minprintspeed;
		default:
			return 0;
		}
		
	}
	
//	public SortedMap<Float, Float> getSpeedAnalysis() {
//		return SpeedAnalysis;
//	}

	public SortedMap<Float, SpeedEntry> getSpeedAnalysisT() {
		return SpeedAnalysisT;
	}

	
	/**
	 * Get time incl linear acceleration
	 * @return
	 */
	public float getTimeAccel() {
		return timeaccel;
	}


	
	public float getTraveldistance() {
		return traveldistance;
	}
	
	public String getUnit() {
		return unit;
	}

	public float getZPosition() {
		return zPosition;
	}
	
	



	public boolean isPrinted(){
		return isprinted;
	}









	void setLayerheight(float layerheight) {
		Layerheight = layerheight;
	}



	void setTraveldistance(float traveldistance) {
		this.traveldistance = traveldistance;
	}
	
	void setUnit(String unit) {
		this.unit = unit;
	}
	
	public String toString(){
		String var = getLayerDetailReport();
		String var2 = getLayerSpeedReport();
		return var+var2+"\n----------------------------------------------------------";
	}
	
	
	public String getLayerSpeedReport() {
		ArrayList<Float> speeds = new ArrayList<Float>(SpeedAnalysisT.keySet());
		StringBuilder var2 = new StringBuilder();
		var2.append("---------- Layer #");
		var2.append(number);
		var2.append(" Speed Distribution ------------");
		for (Iterator<Float> iterator = speeds.iterator(); iterator.hasNext();) {
			float speedval =  iterator.next();
			SpeedEntry tim = SpeedAnalysisT.get(speedval);
			var2.append("\n    Speed ");
			var2.append(speedval);
			var2.append("    ");
			var2.append(tim.getType());
			var2.append("     Distance:");
			var2.append(Constants.round2digits(tim.getDistance()/(distance/100)));
			var2.append('%');
			var2.append("      Time:");
			var2.append(Constants.round2digits(tim.getTime()));
			var2.append("sec/");
			var2.append(Constants.round2digits(tim.getTime()/(timeaccel/100)));
			var2.append('%');			
		}
		return var2.toString();
	}
	public String getLayerDetailReport() {
//		System.out.println("fan:"+fantime+" "+(gcodes.size()/100f));
		StringBuilder var = new StringBuilder(500); 
				var.append('#');
				var.append(number);
				var.append(" Height: ");
				var.append(zPosition);
				var.append(unit);
				var.append("\n LayerHeight: ");
				var.append(Layerheight);
				var.append(unit);
				var.append("\n Is Printed: ");
				var.append(isPrinted());
//				var.append("\n Print Time: ");
//				Constants.formatTimetoHHMMSS(time,var);
				var.append("\n Print Time (Accel): ");
				Constants.formatTimetoHHMMSS(timeaccel,var);
				var.append("\n Distance (All/travel): ");
				var.append(Constants.round2digits(distance));
				var.append('/');
				var.append(Constants.round2digits(traveldistance));
				var.append(unit);
				var.append("\n Extrusion: ");
				var.append(Constants.round2digits(extrusion));
				var.append(unit);
				var.append("\n Bed Temperatur:");
				var.append(bedtemp);
				var.append('°');
				var.append("\n Extruder Temperatur:");
				var.append(exttemp);
				var.append('°');
				var.append("\n Cooling Time (Fan): ");
				var.append(Constants.round2digits(fantime/(timeaccel/100f)));
				var.append('%');
				var.append("\n GCodes: ");
				var.append(lowidx-highidx); 
				var.append("\n GCode Linenr: ");
				var.append(lowidx); //TODO verify that lowidx == line number
				var.append("\n Dimension: ");
				var.append(Constants.round2digits(dimension[0]));
				var.append(unit);
				var.append(" x ");
				var.append(Constants.round2digits(dimension[1]));
				var.append(unit);
				var.append(" x");
				var.append(Constants.round2digits(dimension[2]));
				var.append(unit);
				var.append("\n Avg.Speed(All): ");
				var.append(getSpeed(Speed.SPEED_ALL_AVG));
				var.append(unit);
				var.append("/s\n Avg.Speed(Print): ");
				var.append(getSpeed(Speed.SPEED_PRINT_AVG));
				var.append(unit);
				var.append("/s\n Avg.Speed(Travel): ");
				var.append(getSpeed(Speed.SPEED_TRAVEL_AVG));
				var.append(unit);
				var.append("/s\n Max.Speed(Print): ");
				var.append(getSpeed(Speed.SPEED_PRINT_MAX));
				var.append(unit);
				var.append("/s\n Min.Speed(Print): ");
				var.append(getSpeed(Speed.SPEED_PRINT_MIN));
				var.append(unit);
				var.append("/s");
		return var.toString();
	}
	
	public String getLayerSummaryReport() {
		StringBuilder var = new StringBuilder(); 
		var.append('#');
		var.append(number);
		var.append("   H:");
		var.append(zPosition);
		var.append('/');
		var.append(Layerheight);
		var.append(unit);
		var.append("   T:");
		var.append(Constants.removeTrailingZeros(Float.toString(exttemp)));
		var.append("/");
		var.append(Constants.removeTrailingZeros(Float.toString(bedtemp)));
		var.append('°');
		var.append("  ");
		var.append(getSpeed(Speed.SPEED_PRINT_AVG));
		var.append(unit);
		var.append("/s");
		var.append("   Time: ");
		Constants.formatTimetoHHMMSS(timeaccel,var);
		return var.toString();
	}
	

	

}

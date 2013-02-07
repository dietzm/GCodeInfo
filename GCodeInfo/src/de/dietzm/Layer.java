package de.dietzm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import de.dietzm.SpeedEntry.Speedtype;

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
	private ArrayList<GCode> gcodes = new ArrayList<GCode>();

	private float Layerheight;
	private boolean isprinted =false;

	private int number;
	private SortedMap<Float,SpeedEntry> SpeedAnalysisT = new TreeMap<Float,SpeedEntry>();
	private float time = 0;
	private float timeaccel=0;
	float traveldistance=0;
	private String unit = "mm"; //default is mm

	private float zPosition;

	public Layer(float zPosition){
		this.zPosition=zPosition;
	}
	public Layer(float zPosition, int num, float lheight){
		this.zPosition=zPosition;
		this.number=num;
		this.Layerheight=lheight;
	}



	void addGcodes(GCode gcode) {
		this.gcodes.add(gcode);
		time=time+gcode.getTime();
		timeaccel=timeaccel+gcode.getTimeAccel();
		distance=distance+gcode.getDistance();
		if(!gcode.isExtruding()){
			traveldistance+= gcode.getDistance();
		}
		
		if(fanspeed!=0) {
			gcode.setFanspeed(fanspeed); //Update follow on gcodes
			fantime+=gcode.getTime();
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

				//Update temp only if extruding (temp changes at the end of the layer should not change layer temp)
				if(gcode.getBedtemp() != GCode.UNINITIALIZED){
					bedtemp=gcode.getBedtemp();
				}
				if(gcode.getExtemp() != GCode.UNINITIALIZED){
					exttemp=gcode.getExtemp();
				}
			}else{
				//Travel only
				avgtravelspeed+= sp*gcode.getDistance();
			}
		}
		
	
		
		//ignore retracts
		//if ( gcode.getExtrusion() > 0){
		extrusion=extrusion+gcode.getExtrusion();
		//}
	}



	public int getFanspeed() {
		return fanspeed;
	}
	public void setFanspeed(int fanspeed) {
		this.fanspeed = fanspeed;
	}
	void addPosition(float x, float y,float z){
		boundaries[0]=Math.max(x,boundaries[0]);
		boundaries[1]=Math.min(x,boundaries[1]);
		boundaries[2]=Math.max(y,boundaries[2]);
		boundaries[3]=Math.min(y,boundaries[3]);
		boundaries[4]=Math.max(z,boundaries[4]);
		dimension[0]=boundaries[0]-boundaries[1];
		dimension[1]=boundaries[2]-boundaries[3];
		dimension[2]=Layerheight;
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
			timeforspeed.addTime(gcode.getTime());
			timeforspeed.addDistance(gcode.getDistance());
			if(gcode.isExtruding()){
				timeforspeed.setPrint(Speedtype.PRINT);
			}else{
				timeforspeed.setPrint(Speedtype.TRAVEL);
			}
		}else{
			SpeedEntry sped = new SpeedEntry(sp,gcode.getTime(),number);
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
	
	public ArrayList<GCode> getGcodes() {
		return gcodes;
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
			return GCode.round2digits(avgspeed/distance);
		case SPEED_TRAVEL_AVG:
			return GCode.round2digits((avgtravelspeed/traveldistance));
		case SPEED_PRINT_AVG:
			return GCode.round2digits((avgspeed-avgtravelspeed)/(distance-traveldistance));
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

	public float getTime() {
		return time;
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
		//Collections.sort(speeds);
		//TODO: use stringbuffer instead of string concatenation 
		String var2="---------- Layer #"+number+" Speed Distribution ------------";
		for (Iterator<Float> iterator = speeds.iterator(); iterator.hasNext();) {
			float speedval =  iterator.next();
			SpeedEntry tim = SpeedAnalysisT.get(speedval);
			var2=var2+"\n\tSpeed "+speedval+
					" \t"+tim.getType()+
					" \tDistance:"+GCode.round2digits(tim.getDistance()/(distance/100))+"%"+ 
					"  \tTime:"+GCode.round2digits(tim.getTime())+"sec/"
					+GCode.round2digits(tim.getTime()/(time/100))+"%";
			
		}
		return var2;
	}
	public String getLayerDetailReport() {
//		System.out.println("fan:"+fantime+" "+(gcodes.size()/100f));
		//TODO: use stringbuffer instead of string concatination 
		String var = "#"+number+
				" Height: "+zPosition+unit+
				"\n LayerHeight: "+Layerheight+unit+
				"\n Is Printed: "+isPrinted()+
				"\n Print Time: "+GCode.formatTimetoHHMMSS(time)+
				"\n Print Time (Accel): "+GCode.formatTimetoHHMMSS(timeaccel)+
				"\n Distance (All/travel): "+GCode.round2digits(distance)+"/"+GCode.round2digits(traveldistance)+
				unit+"\n Extrusion: "+GCode.round2digits(extrusion)+
				unit+"\n Bed Temperatur:"+bedtemp+"°"+
				"\n Extruder Temperatur:"+exttemp+"°"+
				"\n Cooling Time (Fan): "+GCode.round2digits(fantime/(time/100f))+"%"+
				"\n GCodes: "+gcodes.size()+ 
				"\n GCode Linenr: "+gcodes.get(0).getLineindex()+
				"\n Dimension: "+GCode.round2digits(dimension[0])+unit+" x "+GCode.round2digits(dimension[1])+unit+" x"+GCode.round2digits(dimension[2])+unit+
				"\n Avg.Speed(All): "+getSpeed(Speed.SPEED_ALL_AVG)+
				unit+"/s\n Avg.Speed(Print): "+getSpeed(Speed.SPEED_PRINT_AVG)+
				unit+"/s\n Avg.Speed(Travel): "+getSpeed(Speed.SPEED_TRAVEL_AVG)+
				unit+"/s\n Max.Speed(Print): "+getSpeed(Speed.SPEED_PRINT_MAX)+
				unit+"/s\n Min.Speed(Print): "+getSpeed(Speed.SPEED_PRINT_MIN)+unit+"/s";
		return var;
	}
	
	public String getLayerSummaryReport() {
		//TODO: use stringbuffer instead of string concatenation 
		String var = "#"+number+
				"\t H:"+zPosition+"/"+Layerheight+unit+
				"\t T:"+GCode.removeTrailingZeros(Float.toString(exttemp))+"/"+GCode.removeTrailingZeros(Float.toString(bedtemp))+"°"+
				"\t Speed: "+getSpeed(Speed.SPEED_PRINT_AVG)+unit+"/s"+
				"\t Time: "+GCode.formatTimetoHHMMSS(time);
		return var;
	}
	

	

}

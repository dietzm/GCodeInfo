package de.dietzm;


public class GCode {
	
	public enum GCDEF {
		G0,G1,G2,G20,G21,G28,G4,G90,G91,G92,M0,M1,M101,M103,M104,M105,M106,M107,M108,M109,M113,M114,M140,M190,M82,M83,M84,UNKNOWN;
	}
	
	public static final float UNINITIALIZED = -99999.99f;
	
	private String codeline;

	
	//Parsed values, not changed by analyse 
	private float e=UNINITIALIZED; //Extruder 1
	private float f=UNINITIALIZED; //Speed
	private float x=UNINITIALIZED;
	private float y=UNINITIALIZED;
	private float z=UNINITIALIZED;
	private float s_ext=UNINITIALIZED;
	private float s_bed=UNINITIALIZED;
	private float s_fan=UNINITIALIZED;
	private String gcode;
	private int lineindex;
	private String params;
	private String comment;
	
	//Dynamic values updated by analyse	
	private float extrusion;	
	private float time;
	private float timeaccel; //track acceleration as extra time (+/-)
	private float extemp,bedtemp,fanspeed; //remember
	public float getFanspeed() {
		return fanspeed;
	}

	/**
	 * Set the fanspeed to remember how long the fan is turned on.
	 * This does NOT change the s_fan variable and will not written to gcode on save
	 * @param fanspeed
	 */
	public void setFanspeed(float fanspeed) {
		this.fanspeed = fanspeed;
	}

	public float getExtemp() {
		return extemp;
	}

	public float getBedtemp() {
		return bedtemp;
	}

	private String unit = null; //default is mm
	float[] currentPosition;
	float distance;
	
	public String getCodeline() {
		return codeline;
	}
	
	public static String formatTimetoHHMMSS(float secs)
	{		
		int secsIn = Math.round(secs);
		int hours =  secsIn / 3600,
		remainder =  secsIn % 3600,
		minutes = remainder / 60,
		seconds = remainder % 60;

	return ( (hours < 10 ? "" : "") + hours
	+ ":" + (minutes < 10 ? "0" : "") + minutes
	+ ":" + (seconds< 10 ? "0" : "") + seconds );

	}
	public static float round2digits(float num){
		return Math.round((num)*100f)/100f;
	}
	public static float round3digits(float num){
		return Math.round((num)*1000f)/1000f;
	}
	
	
	public GCode(String line,int linenr){
		codeline=line.trim();
		lineindex=linenr;
	}
	public float getS_Bed() {
		return s_bed;
	}
	public String getCode() {
		return codeline;
	}
	public String getComment() {
		return comment;
	}
	
	public float[] getCurrentPosition() {
		return currentPosition;
	}
	public float getDistance() {
		return distance;
	}
	
	public float getE() {
		return e;
	}
	
	public float getS_Ext() {
		return s_ext;
	}

	public float getExtrusion() {
		return extrusion;
	}

	public float getF() {
		return f;
	}
	public float getS_Fan() {
		return s_fan;
	}

	public String getGcode() {
		return gcode;
	}

	public int getLineindex() {
		return lineindex;
	}

	public String getParams() {
		return params;
	}


	
	
	
	public float[] getPosition(float[] reference){
		return new float[] {x==UNINITIALIZED?reference[0]:x,y==UNINITIALIZED?reference[1]:y,z==UNINITIALIZED?reference[2]:z};
	}
	/**
	 * Speed in mm/s based on distance/time
	 * @return
	 */
	public float getSpeed(){
		return round2digits((distance/time));
	}
	
	/**
	 * Update the speed values (and the corresponding extrusion rate)
	 * @param percent , negative value for slower
	 */
	public void changeSpeed(int percent){
		if(f!=UNINITIALIZED) f=f+(f/100*percent);
		if(e!=UNINITIALIZED) e=e+(e/100*percent);
		update();
	}
	
	/**
	 * Update the speed values (and the corresponding extrusion rate)
	 * @param percent , negative value for slower
	 */
	public void changeTemp(float ext,float bed){
		if(s_ext!=UNINITIALIZED) s_ext=ext;
		if(s_bed!=UNINITIALIZED) s_bed=bed;
		update();
	}
	
	private void update(){
		codeline=gcode+getIfInit("X",x,3)+getIfInit("Y",y,3)+
				getIfInit("Z",z,3)+
				getIfInit("F",f,3)+getIfInit("E",e,5)+
				getIfInit("S",s_bed,0)+getIfInit("S",s_ext,0)+getIfInit("S",s_fan,0)+
				(comment!=null?comment:"");
	}
	
	/**
	 * Do soem tricks to make sure that the saved output file is 100% equal to the input file (if nothing has been changed)
	 * @param prefix
	 * @param val
	 * @param digits
	 * @return
	 */
	private String getIfInit(String prefix,float val,int digits){
		if(val==UNINITIALIZED) return "";
		if(digits==0){
			String var = String.format(" "+prefix+"%.1f", val);
			return removeTrailingZeros(var); //remove trailing zero
		}
		if("E".equals(prefix) && val == 0) return " E0";  //replace E0.0000 with E0 
		return String.format(" "+prefix+"%."+digits+"f", val);		
	}

	public static String removeTrailingZeros(String var) {
		return var.replaceAll("[0]*$", "").replaceAll("\\.$", "");
	}
	
	public float getTime() {
		return time;
	}

	public String getUnit() {
		return unit;
	}

	public float getX() {
		return x;
	}

	public float getY() {
		return y;
	}

	/**
	 * Get Z position change. Rounded to 2 digits behind comma.
	 * @return zPosition
	 */
	public float getZ() {
		return z;
	}

	/**
	 * Is filament getting extruded or restract
	 * @return true is extruding
	 */
	public boolean isExtrudeOrRetract(){
		return ( e != UNINITIALIZED && e != 0 );
	}

	/**
	 * Is filament getting extruded (no incl. retract)
	 * @return true is extruding
	 */
	public boolean isExtruding(){
		return ( e != UNINITIALIZED && e > 0 );
	}

	/**
	 * Parse the GCode and initialize variables
	 * @return return false if something went wrong
	 */
	public boolean parseGcode() {
		String codelinevar=codeline;
		int idx;
		if((idx = codelinevar.indexOf(';')) != -1){
			//is a comment
			comment=codelinevar.substring(idx);
			codelinevar=codelinevar.substring(0, idx);
		}else if((idx = codelinevar.indexOf("(<")) != -1){
			//is a comment
			comment=codelinevar.substring(idx);
			codelinevar=codelinevar.substring(0, idx);
		}

		if(codelinevar.isEmpty()){
			return true;
		}
		codelinevar=codelinevar.toUpperCase();
		Character id;
		
		
		String[] segments = codelinevar.split(" ");
		gcode=segments[0].trim();
		
		GCDEF GCEnum;
		try {
			GCEnum = GCDEF.valueOf(gcode);
		} catch (Exception e1) {
			GCEnum=GCDEF.UNKNOWN;
		}
				
		switch (GCEnum) {
		case G0:
		case G1:
			//System.out.println("G1 ->"+code);
			for (int i = 1; i < segments.length; i++) {
				//System.out.println("segment:"+segments[i]);
				id = segments[i].charAt(0);
				switch (id) {
				case 'E':
					e = parseSegment(segments[i]);
					break;
				case 'X':
					x = parseSegment(segments[i]);
					break;
				case 'Y':
					y = parseSegment(segments[i]);
					break;
				case 'Z':
					z = parseSegment(segments[i]);	
					break;
				case 'F':
					f = parseSegment(segments[i]);
					break;
				default:
					break;
				}
			}
			break;
		case G4: //Dwell
			//TODO add to duration
			break;
		case G92:
			//System.out.println("G1 ->"+code);
			for (int i = 1; i < segments.length; i++) {
				//System.out.println("segment:"+segments[i]);
				id = segments[i].charAt(0);
				switch (id) {
				case 'E':
					e = parseSegment(segments[i]);
					break;
				case 'X':
					x = parseSegment(segments[i]);
					break;
				case 'Y':
					y = parseSegment(segments[i]);
					break;
				case 'Z':
					z = parseSegment(segments[i]);	
					break;
				case 'F':
					f = parseSegment(segments[i]);
					break;
				default:
					break;
				}
			}
			break;
		case M140: //set bed temp and not wait
		case M190: //set bed temp and wait
			id = segments[1].charAt(0);
			if (id=='S'){
				s_bed=parseSegment(segments[1]);
			}
			break;
		case M104: //set extruder temp and NOT wait
			id = segments[1].charAt(0);
			if (id=='S'){
				s_ext=parseSegment(segments[1]);
			}
			break;
		case G90: //Absolute positioning
			break;
		case M82: //Absolute positioning for extrusion
			break;	
		case M83:
		case G91: //Relative positioning
			System.err.println("G90/M83 Relative Positioning is NOT supported.");
			return false;
		case G20: //Unit = inch
			unit="in";
			break;
		case G21: //Unit = inch
			unit="mm";
			break;
		case M109: //set extruder temp and wait
			id = segments[1].charAt(0);
			if (id=='S'){
				s_ext=parseSegment(segments[1]);
			}
			break;
		case G28:
			if (segments.length == 1) { //no param means home all axis
				x = y = z = 0;
			} else {
				for (int i = 1; i < segments.length; i++) {
					id = segments[i].charAt(0);
					switch (id) {
					case 'X':
						x = 0;
						break;
					case 'Y':
						y = 0;
						break;
					case 'Z':
						z = 0;
						break;
					}
				}
			}
			break;
		case M107: //reset fan speed (off)
			s_fan=0;
			break;
		case M106: //reset fan speed (off)
			if (segments.length == 1) { //no param means turn on fan full ?
				s_fan=255;
			} else {
				id = segments[1].charAt(0);
				if (id=='S'){
					s_fan=parseSegment(segments[1]);
				}
			}
			break;
		case M84: //disable all motors
		case M105: //get extr temp
		case M114: //get current position
		case M0: //Stop
		case M1: //Sleep 
			break;
		case M103: //marlin turn all extr off
		case M101://marlin turn all extr on
		case M113: //set extruder speed / turn off
			System.err.println("Not supported Gcode:"+gcode);
			return false;
		case M108:
			System.err.println("Deprecated Gcode M108");
			return false;
		default:
			System.err.println("Unknown Gcode "+lineindex+": "+ codeline.substring(0,Math.min(15,codeline.length()))+"....");
			return false;
		}
		//update used values
		bedtemp=s_bed;
		extemp=s_ext;
		fanspeed=s_fan;
		
		
		return true;
	}

	private float parseSegment(String segment){
		String num = segment.substring(1);
		return Float.parseFloat(num);
	}

	/**
	 * Set the bedtemp to remember the configured temp.
	 * This does NOT change the s_bed variable and will not written to gcode on save
	 * @param fanspeed
	 */
	public void setBedtemp(float bedtemp) {
		this.bedtemp = bedtemp;
	}

	public void setCode(String code) {
		this.codeline = code;
	}

	public void setCurrentPosition(float[] currentPosition) {
		this.currentPosition = currentPosition;
	}

	public void setDistance(float distance) {
		this.distance = distance;
	}

	public void setE(float e) {
		this.e = e;
	}

	/**
	 * Set the exttemp to remember the configured temp.
	 * This does NOT change the s_ext variable and will not written to gcode on save
	 * @param fanspeed
	 */
	public void setExtemp(float extemp) {
		this.extemp = extemp;
	}
	
	public void setExtrusion(float extrusion) {
		this.extrusion = extrusion;
	}
	
	public void setF(float f) {
		this.f = f;
	}
	

	
	

	public void setGcode(String gcode) {
		this.gcode = gcode;
	}

	public void setLineindex(int lineindex) {
		this.lineindex = lineindex;
	}
	
	public void setParams(String params) {
		this.params = params;
	}

	public void setTime(float time) {
		this.time = time;
	}
	
	public void setTimeAccel(float time) {
		this.timeaccel = time;
	}
	
	
	public float getTimeAccel() {
		return this.timeaccel;
	}

	public void setX(float x) {
		this.x = x;
	}

	public void setY(float y) {
		this.y = y;
	}

	public void setZ(float z) {
		this.z = z;
	}

	@Override
	public String toString() {		
		String var = lineindex+":  "+codeline;
		var+="\tExtrusion:"+extrusion;
		var+="\tDistance:"+distance;
		var+="\tPosition:"+currentPosition[0]+"x"+currentPosition[1]+"x"+currentPosition[2];
		var+="\tTime:"+time;
		return var;
	}
	
	
	
	

}

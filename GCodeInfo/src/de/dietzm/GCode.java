package de.dietzm;


public class GCode {
	
	public static enum GCDEF {
		G0,G1,G2,G3,G20,G21,G28,G4,G90,G91,G92,M0,M1,M92,M101,M103,M104,M105,M106,M107,M108,M109,M113,M114,M117,M140,M190,M82,M83,M84,UNKNOWN;
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
	private GCDEF gcode;
	private int lineindex;
	private String comment;
	
	//Dynamic values updated by analyse	
	private float extrusion;	
	private float time;
	private float timeaccel; //track acceleration as extra time (+/-)
	private float extemp,bedtemp,fanspeed; //remember
	private String unit = null; //default is mm
	float[] currentPosition;
	float distance;
	
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

	public GCDEF getGcode() {
		return gcode;
	}

	public int getLineindex() {
		return lineindex;
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
	 * Update the speed values 
	 * @param percent , negative value for slower
	 */
	public void changeSpeed(int percent,boolean printonly){
		if(printonly && !isExtruding()) return;  //skip travel
		if(f!=UNINITIALIZED) f=f+(f/100*percent);
		//if(e!=UNINITIALIZED) e=e+(e/100*percent); 
		update();
	}
	
	/**
	 * Update the extrusion values 
	 * @param percent , negative value for slower
	 */
	public void changeExtrusion(int percent){
		if(e!=UNINITIALIZED) {
			e=e+(e/100*percent);
			update();
		}
		
	}
	
	/**
	 * Update the layerheight (and the corresponding extrusion rate)
	 * @param percent , negative value for slower
	 */
	public void changeLayerHeight(int percent){
		if(z!=UNINITIALIZED){
			z=z+(z/100*percent);
			update();
		}
		if(e!=UNINITIALIZED){ 
			e=e+(e/100*percent);
			update();
		}
		
	}

	/**
	 * Add offset to z (no extrusion rate change)
	 * @param absolute value 
	 */
	public void changeZOffset(float value){
		if(z!=UNINITIALIZED){
			z=z+value;
			update();
		}
		
	}
	
	/**
	 * Add offset to y (no extrusion rate change)
	 * @param absolute value 
	 */
	public void changeYOffset(float value){
		if(y!=UNINITIALIZED){
			y=y+value;
			update();
		}
		
	}
	
	/**
	 * Add offset to x (no extrusion rate change)
	 * @param absolute value 
	 */
	public void changeXOffset(float value){
		if(x!=UNINITIALIZED){
			x=x+value;
			update();
		}
		
	}
	
	public void changeToComment(){
		codeline=";"+codeline;
	}
	
	/**
	 * Update the fan speed 
	 * @param absolute value (0 = off, 255=full speed)
	 */
	public void changeFan(int value){
		if(s_fan!=UNINITIALIZED) {
			s_fan=value;
			update();
		}
		
	}
	
	/**
	 * Update the extruder temp values 
	 * @param absolute values 
	 */
	public void changeExtTemp(float ext){
		if(s_ext!=UNINITIALIZED) {
			s_ext=ext;
			update();
		}
		
	}
	
	/**
	 * Update the bed temp values 
	 * @param absolute values for bed 
	 */
	public void changeBedTemp(float bed){
		if(s_bed!=UNINITIALIZED){
			s_bed=bed;
			update();
		}
		
	}
	
	private void update(){
		codeline=(gcode!=GCDEF.UNKNOWN?gcode:"")+
				getIfInit("X",x,3)+
				getIfInit("Y",y,3)+
				getIfInit("Z",z,3)+
				getIfInit("F",f,3)+
				getIfInit("E",e,5)+
				getIfInit("S",s_bed,0)+
				getIfInit("S",s_ext,0)+
				getIfInit("S",s_fan,0)+
				(comment!=null?comment:"");
		parseGcode();
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
		return ( e != UNINITIALIZED && extrusion > 0 );
	}
	
	/**
	 * Is printable, returns false for comments only and unknown GCODES
	 * @return
	 */
	public boolean isPrintable(){
		 return gcode != GCDEF.UNKNOWN; 
	}
	
	/**
	 * Is printable, returns false for comments only and unknown GCODES
	 * @return
	 */
	public boolean isComment(){
		 return gcode == GCDEF.UNKNOWN && getComment() != null; 
	}

	/**
	 * Parse the GCode and initialize variables
	 * @return return false if something went wrong
	 */
	public boolean parseGcode() {
		String codelinevar=codeline;
		gcode=GCDEF.UNKNOWN;
		//codeline=null;
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
		String gcodestr=segments[0].trim();
		
		try {
			gcode = GCDEF.valueOf(gcodestr);
		} catch (Exception e1) {
			
		}
				
		switch (gcode) {
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
		case G3:
			System.err.println("Unsupported Gcode G3. Ignoring it.");
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
			System.err.println("G91/M83 Relative Positioning is NOT supported.");
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
		case M117: //get zero position
		case M92: //set axis steps per unit (just calibration) 
			break;
		case M103: //marlin turn all extr off
		case M101://marlin turn all extr on
		case M113: //set extruder speed / turn off
			System.err.println("Unsupported Gcode M101/M103/M113 found. Ignoring it.");
			break;
		case M108:
			System.err.println("Deprecated Gcode M108. Ignoring it.");
			break;
		default:
			System.err.println("Unknown Gcode "+lineindex+": "+ codelinevar.substring(0,Math.min(15,codelinevar.length()))+"....");
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
	

	
	

	public void setGcode(GCDEF gcode) {
		this.gcode = gcode;
	}

	public void setLineindex(int lineindex) {
		this.lineindex = lineindex;
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
	
	
	/**
	 * Get Extrusion speed (mm/min)
	 * @return
	 */
	public float getExtrusionSpeed(){
		return (extrusion/timeaccel)*60f;
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
	

	public String toCSV() {		
		String var = String.valueOf(getSpeed());
		var+=";"+extrusion;
		var+=";"+distance;
		var+=";"+time;
		var+=";"+fanspeed;
		return var;
	}
	
	
	
	

}

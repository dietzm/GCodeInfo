package de.dietzm;

import java.io.UnsupportedEncodingException;

import de.dietzm.Constants.GCDEF;


public class GCode {
	
	private static final String ENCODING = "ISO-8859-1";
    private byte[] data; //Store String in a more memory efficient way
	private short gcode;
	private int lineindex;
	private short commentidx=-1;
		
	//Parsed values, not changed by analyse 
	private float e=Float.MAX_VALUE;
	private float f=Float.MAX_VALUE; //Speed
	private float x=Float.MAX_VALUE;
	private float y=Float.MAX_VALUE;
	private float z=Float.MAX_VALUE;
	//Store additional values in separate class to avoid memory consumption if not needed
	private Extended ext =null;
	private class Extended{ 
		private float ix=Float.MAX_VALUE;
		private float jy=Float.MAX_VALUE;
		private float kz=Float.MAX_VALUE;
		private float r=Float.MAX_VALUE;	
		private float s_ext=Float.MAX_VALUE;
		private float s_bed=Float.MAX_VALUE;
		private float s_fan=Float.MAX_VALUE;
		private String unit = null; //default is mm
	}
	
	//Dynamic values updated by analyse	
	private float time;
	private float timeaccel; //track acceleration as extra time 
	private float distance;
	private float extrusion;
	private short fanspeed; //remember with less accuracy (just for display)
	private float curX,curY;
	
	//private float extemp,bedtemp;	
	public short getFanspeed() {
		return fanspeed;
	}

	/**
	 * Set the fanspeed to remember how long the fan is turned on.
	 * This does NOT change the s_fan variable and will not written to gcode on save
	 * @param fanspeed
	 */
	public void setFanspeed(float fanspeed) {
		this.fanspeed = (short)fanspeed;
	}

	public float getExtemp() {
		if(!isInitialized(Constants.SE_MASK)) return -255;
		return ext.s_ext;
	}

	public float getBedtemp() {
		if(!isInitialized(Constants.SB_MASK)) return -255;
		return ext.s_bed;
	}


	
	public MemoryEfficientString getCodeline() {
		return new MemoryEfficientString(data);
	}
	
	/**
	 * pass Stringbuffer to avoid allocation
	 * @param secs
	 * @param buf
	 * @return
	 */
	public static String formatTimetoHHMMSS(float secs, StringBuffer buf)
	{		
		int secsIn = Math.round(secs);
		int hours =  secsIn / 3600,
		remainder =  secsIn % 3600,
		minutes = remainder / 60,
		seconds = remainder % 60;

		if(buf==null){
			buf = new StringBuffer();
		}
		buf.append(hours);
		buf.append(":");
		buf.append((minutes < 10 ? "0" : ""));
		buf.append(minutes);
		buf.append(":");
		buf.append((seconds< 10 ? "0" : ""));
		buf.append(seconds);
		
	return buf.toString();
	}
	public static float round2digits(float num){
		return Math.round((num)*100f)/100f;
	}
	
	public MemoryEfficientString getCodelineToPrint(){
		if(commentidx != -1){
			return subSequence(0, commentidx);
		}
		return new MemoryEfficientString(data);
	}
	public static float round3digits(float num){
		return Math.round((num)*1000f)/1000f;
	}
	
	
	
	
	public GCode(String line,int linenr){
		lineindex=linenr;
		/**
		 * Store a memory efficient copy only
		 */

		 try {
		      data = line.getBytes(ENCODING);
		    } catch (UnsupportedEncodingException e) {
		      throw new RuntimeException("Unexpected: " + ENCODING + " not supported!");
		    }
		 
		parseGcode(line.trim());
		
	}

	public GCode(String line){
		this(line,0);
	}
	public float getS_Bed() {
		return ext.s_bed;
	}

	public String getComment() {
		return subSequence(commentidx,data.length).toString();
	}
	
	/**
	 * 
	 * @param pos pass a position object to avoid object creation
	 * @return
	 */
	public Position getCurrentPosition(Position pos) {
		pos.x=curX;
		pos.y=curY;
		return pos;
	}
	public float getDistance() {
		return distance;
	}
	
	public float getE() {
		return e;
	}
	
	public float getS_Ext() {
		return ext.s_ext;
	}

	public float getExtrusion() {
		return extrusion;
	}

	public float getF() {
		return f;
	}
	public float getS_Fan() {
		return ext.s_fan;
	}

	public Constants.GCDEF getGcode() {
		return GCDEF.getGCDEF(gcode);
	}

	public int getLineindex() {
		return lineindex;
	}




	
	
	
	public Position getPosition(Position reference){
		return new Position( isInitialized(Constants.X_MASK)?x:reference.x,isInitialized(Constants.Y_MASK)?y:reference.y);
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
		if(isInitialized(Constants.F_MASK)) f=f+(f/100*percent);
		//if(e!=UNINITIALIZED) e=e+(e/100*percent); 
		update();
	}
	
	/**
	 * Update the extrusion values 
	 * @param percent , negative value for slower
	 */
	public void changeExtrusion(int percent){
		if(isInitialized(Constants.E_MASK)) {
			e=e+(e/100*percent);
			update();
		}
		
	}
	
	/**
	 * Update the layerheight (and the corresponding extrusion rate)
	 * @param percent , negative value for slower
	 */
	public void changeLayerHeight(int percent){
		if(isInitialized(Constants.Z_MASK)){
			z=z+(z/100*percent);
			update();
		}
		if(isInitialized(Constants.E_MASK)){ 
			e=e+(e/100*percent);
			update();
		}
		
	}

	/**
	 * Add offset to z (no extrusion rate change)
	 * @param absolute value 
	 */
	public void changeZOffset(float value){
		if(isInitialized(Constants.Z_MASK)){
			z=z+value;
			update();
		}
		
	}
	
	/**
	 * Add offset to y (no extrusion rate change)
	 * @param absolute value 
	 */
	public void changeYOffset(float value){
		if(isInitialized(Constants.Y_MASK)){
			y=y+value;
			update();
		}
		
	}
	
	/**
	 * Add offset to x (no extrusion rate change)
	 * @param absolute value 
	 */
	public void changeXOffset(float value){
		if(isInitialized(Constants.X_MASK)){
			x=x+value;
			update();
		}
		
	}
	
	public void changeToComment(){
		MemoryEfficientString mes = getCodeline();
		data= new MemoryEfficientString(";"+mes).getBytes();
	}
	
	/**
	 * Update the fan speed 
	 * @param absolute value (0 = off, 255=full speed)
	 */
	public void changeFan(int value){
		if(isInitialized(Constants.SF_MASK)) {
			ext.s_fan=Float.valueOf(value);
			update();
		}
		
	}
	
	/**
	 * Update the extruder temp values 
	 * @param absolute values 
	 */
	public void changeExtTemp(float extr){
		if(isInitialized(Constants.SE_MASK)) {
			ext.s_ext=extr;
			update();
		}
		
	}
	
	/**
	 * Update the bed temp values 
	 * @param absolute values for bed 
	 */
	public void changeBedTemp(float bed){
		if(isInitialized(Constants.SB_MASK)){
			ext.s_bed=bed;
			update();
		}
		
	}
	
	private void update(){
		GCDEF gd = GCDEF.getGCDEF(gcode);
		String newgc = (gd!=Constants.GCDEF.UNKNOWN?gd:"")+
				getIfInit("X",x,3,Constants.X_MASK)+
				getIfInit("Y",y,3,Constants.Y_MASK)+
				getIfInit("Z",z,3,Constants.Z_MASK)+
				getIfInit("F",f,3,Constants.F_MASK)+
				getIfInit("E",e,5,Constants.E_MASK)+
				getIfInit("S",ext.s_bed,0,Constants.SB_MASK)+
				getIfInit("S",ext.s_ext,0,Constants.SE_MASK)+
				getIfInit("S",ext.s_fan,0,Constants.SF_MASK)+
				(commentidx!=-1?getComment():"");
		parseGcode(newgc);
	}
	
	/**
	 * Do soem tricks to make sure that the saved output file is 100% equal to the input file (if nothing has been changed)
	 * @param prefix
	 * @param val
	 * @param digits
	 * @return
	 */
	private String getIfInit(String prefix,float val,int digits,int mask){
		if(!isInitialized(mask)) return "";
		if(digits==0){
			String var = String.format(" "+prefix+"%.1f", val);
			return removeTrailingZeros(var); //remove trailing zero
		}
		if("E".equals(prefix) && val == 0) return " E0";  //replace E0.0000 with E0 
		return String.format(" "+prefix+"%."+digits+"f", val);		
	}

	/**
	 * Heavyweight
	 * @param var
	 * @return
	 */
	public static String removeTrailingZeros(String var) {
		return var.replaceAll("[0]*$", "").replaceAll("\\.$", "");
	}
	
	public float getTime() {
		return time;
	}

	public String getUnit() {
		return ext.unit;
	}

	public float getX() {
		return x;
	}
	public float getIx() {
		return ext.ix;
	}

	public float getJy() {
		return ext.jy;
	}

	public float getKz() {
		return ext.kz;
	}
	
	public float getR() {
		return ext.r;
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
		return ( isInitialized(Constants.E_MASK) && e != 0 );
	}

	/**
	 * Is filament getting extruded (no incl. retract)
	 * @return true is extruding
	 */
	public boolean isExtruding(){
		return ( isInitialized(Constants.E_MASK) && extrusion > 0 );
	}
	
	/**
	 * Is printable, returns false for comments only and unknown GCODES
	 * @return
	 */
	public boolean isPrintable(){
		 return !Constants.GCDEF.INVALID.equals(gcode) ; 
	}
	
	/**
	 * returns true if it starts with G or M (should be trimmed and uppercase already)
	 * @return boolean gcode
	 */
	private static boolean isValid(String codeline){
		 if(codeline == null || codeline.isEmpty()) return false;
		 if (codeline.startsWith("G")) return true;
		 if (codeline.startsWith("M")) return true;
		 if (codeline.startsWith("T")) return true;
		 //if (codeline.startsWith("g")) return true;
		 //if (codeline.startsWith("m")) return true;
		 return false;
	}
	
	/**
	 * Is printable, returns false for comments only and unknown GCODES
	 * @return
	 */
	public boolean isComment(){
		 return Constants.GCDEF.INVALID.equals(gcode) && commentidx != -1; 
	}

	/**
	 * Is it a gcode which is buffered by the firmware
	 * @return boolean true if buffered gcodes
	 */
	public boolean isBuffered(){
		//if(gcode==null) return false;
		if (Constants.GCDEF.G0.equals(gcode) || Constants.GCDEF.G1.equals(gcode) || Constants.GCDEF.G2.equals(gcode)  || Constants.GCDEF.M106.equals(gcode) || Constants.GCDEF.M107.equals(gcode)   ||Constants.GCDEF.G29.equals(gcode) || Constants.GCDEF.G30.equals(gcode) || Constants.GCDEF.G31.equals(gcode) || Constants.GCDEF.G32.equals(gcode)){
			return true;
		}
		return false;
	}

	/**
	 * Parse the GCode and initialize variables
	 * @return return false if something went wrong
	 */
	public boolean parseGcode(String codelinetmp) {
		String codelinevar=codelinetmp;
	
		GCDEF tmpgcode = Constants.GCDEF.UNKNOWN;

		//Find comments and strip them, init the comment filed 
		codelinevar = stripComment(codelinevar);
		codelinevar = codelinevar.toUpperCase();
		//Is Gcode valid ? plain Comments are marked as invalid as well
		if(!isValid(codelinevar)){
			gcode=Constants.GCDEF.INVALID.getId();
			return true;
		}
		
	
		Character id;		
		String[] segments = codelinevar.split(" ");
		String gcodestr=segments[0].trim();
		
		
		try {
			tmpgcode = Constants.GCDEF.valueOf(gcodestr);
		} catch (Exception e1) {
		}
		
		gcode = tmpgcode.getId();
	//	System.out.println("G1 ->"+gcode);	
		switch (tmpgcode) {
		case G0:
		case G1:
			
			for (int i = 1; i < segments.length; i++) {
				//System.out.println("segment:"+segments[i]);
				id = segments[i].charAt(0);
				switch (id) {
				case 'E':
					setInitialized(Constants.E_MASK);
					 e= parseSegment(segments[i]);
					break;
				case 'X':
					setInitialized(Constants.X_MASK);
					x = parseSegment(segments[i]);
					break;
				case 'Y':
					setInitialized(Constants.Y_MASK);
					y = parseSegment(segments[i]);
					break;
				case 'Z':
					setInitialized(Constants.Z_MASK);
					z = parseSegment(segments[i]);	
					break;
				case 'F':
					setInitialized(Constants.F_MASK);
					f = parseSegment(segments[i]);
					break;
				default:
					break;
				}
			}
			break;
		case G2:
		case G3:
			//System.out.println("G1 ->"+code);
			for (int i = 1; i < segments.length; i++) {
				//System.out.println("segment:"+segments[i]);
				id = segments[i].charAt(0);
				switch (id) {
				case 'E':
					setInitialized(Constants.E_MASK);
					e = parseSegment(segments[i]);
					break;
				case 'X':
					setInitialized(Constants.X_MASK);
					x = parseSegment(segments[i]);
					break;
				case 'Y':
					setInitialized(Constants.Y_MASK);
					y = parseSegment(segments[i]);
					break;
				case 'Z':
					
					setInitialized(Constants.Z_MASK);
					z = parseSegment(segments[i]);	
					break;
				case 'F':
					setInitialized(Constants.F_MASK);
					f = parseSegment(segments[i]);
					break;
				case 'I':
					
					setInitialized(Constants.IX_MASK);
					ext.ix = parseSegment(segments[i]);
					break;
				case 'J':
					
					setInitialized(Constants.JY_MASK);
					ext.jy = parseSegment(segments[i]);
					break;
				case 'K':
					
					setInitialized(Constants.KZ_MASK);
					ext.kz = parseSegment(segments[i]);
					break;
				case 'R':
					
					setInitialized(Constants.R_MASK);
					ext.r = parseSegment(segments[i]);
					break;
				default:
					break;
				}
			}
			System.err.println("Experimental support of Gcode G2/G3.");
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
					setInitialized(Constants.E_MASK);
					e = parseSegment(segments[i]);
					break;
				case 'X':
					setInitialized(Constants.X_MASK);
					x = parseSegment(segments[i]);
					break;
				case 'Y':
					setInitialized(Constants.Y_MASK);
					y = parseSegment(segments[i]);
					break;
				case 'Z':
					setInitialized(Constants.Z_MASK);
					z = parseSegment(segments[i]);	
					break;
				case 'F':
					setInitialized(Constants.F_MASK);
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
				setInitialized(Constants.SB_MASK);
				ext.s_bed=parseSegment(segments[1]);
			}
			break;
		case M104: //set extruder temp and NOT wait
			id = segments[1].charAt(0);
			if (id=='S'){
				setInitialized(Constants.SE_MASK);
				ext.s_ext=parseSegment(segments[1]);
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
			if(ext==null) ext=new Extended();
			ext.unit="in";
			break;
		case G21: //Unit = inch
			if(ext==null) ext=new Extended();
			ext.unit="mm";
			break;
		case M109: //set extruder temp and wait
			id = segments[1].charAt(0);
			if (id=='S'){
				setInitialized(Constants.SE_MASK);
				ext.s_ext=parseSegment(segments[1]);
			}
			break;
		case G161:
		case G162:
		case G28:
			if (segments.length == 1) { //no param means home all axis
				setInitialized(Constants.X_MASK);
				setInitialized(Constants.Y_MASK);
				setInitialized(Constants.Z_MASK);
				x = y = z = 0;
			} else {
				for (int i = 1; i < segments.length; i++) {
					
					id = segments[i].charAt(0);
					switch (id) {
					case 'X':
						setInitialized(Constants.X_MASK);
						x = 0;
						break;
					case 'Y':
						setInitialized(Constants.Y_MASK);
						y = 0;
						break;
					case 'Z':
						setInitialized(Constants.Z_MASK);
						z = 0;
						break;
					}
				}
			}
			break;
		case M107: //reset fan speed (off)
			setInitialized(Constants.SF_MASK);
			ext.s_fan=0;
			break;
		case M106: //reset fan speed (off)
			if (segments.length == 1) { //no param means turn on fan full ?
				setInitialized(Constants.SF_MASK);
				ext.s_fan=255;
			} else {
				id = segments[1].charAt(0);
				if (id=='S'){
					setInitialized(Constants.SF_MASK);
					ext.s_fan=parseSegment(segments[1]);
				}
			}
			break;
		case M84: //disable all motors
		case M18://Stop motor
		case M105: //get extr temp
		case M114: //get current position
		case M0: //Stop
		case M1: //Sleep
		case M117: //get zero position
		case M92: //set axis steps per unit (just calibration) 
		case M132: //set pid
		case M6: //replicatorG tool change
			break;
		case M103: //marlin turn all extr off
		case M101://marlin turn all extr on
		case M113: //set extruder speed / turn off
		//	System.err.println("Unsupported Gcode M101/M103/M113 found. Ignoring it.");
			break;
		case M70: //replicatorG
		case M72://replicatorG
		case M73: //replicatorG
			//System.err.println("Unsupported Gcode M70/M72/M73 found. Ignoring it.");
			break;
		case M108:
			System.err.println("Deprecated Gcode M108. Ignoring it.");
			break;
		case M204:
			System.err.println("M204 Acceleration control is ignored.");
			break;
		default:
			System.err.println("Unknown Gcode "+lineindex+": "+ codelinevar.substring(0,Math.min(15,codelinevar.length()))+"....");
			return false;
		}
		//update used values
	//	if(isInitialized(Constants.SB_MASK))		bedtemp=ext.s_bed;
	//	if(isInitialized(Constants.SE_MASK))		extemp=ext.s_ext;
		if(isInitialized(Constants.SF_MASK))		fanspeed=(short)ext.s_fan;
		
		
		return true;
	}

	/**
	 * Find comments and strip them, init the comment filed
	 * @param clv
	 * @return
	 */
	private String stripComment(String clv) {
		int idx;
		if((idx = clv.indexOf(';')) != -1){
			//is a comment
			commentidx=(short)idx;
			clv=clv.substring(0, idx);
		}else if((idx = clv.indexOf("(<")) != -1){
			//is a comment
			commentidx=(short)idx;
			clv=clv.substring(0, idx);
		}else if((idx = clv.indexOf("(")) != -1){
			//is a comment
			commentidx=(short)idx;
			clv=clv.substring(0, idx);
		}
		return clv.trim();
	}

	private float parseSegment(String segment){
		String num = segment.substring(1);
		return Float.parseFloat(num);
		//return new Float(num);
	}

//	/**
//	 * Set the bedtemp to remember the configured temp.
//	 * This does NOT change the s_bed variable and will not written to gcode on save
//	 * @param fanspeed
//	 */
//	public void setBedtemp(float bedtemp) {
//		this.bedtemp = bedtemp;
//	}



	public void setCurrentPosition(Position currentPosition) {
		curX=currentPosition.x;
		curY=currentPosition.y;
	}

	public void setDistance(float distance) {
		this.distance = distance;
	}

//	/**
//	 * Set the exttemp to remember the configured temp.
//	 * This does NOT change the s_ext variable and will not written to gcode on save
//	 * @param fanspeed
//	 */
//	public void setExtemp(float extemp) {
//		this.extemp = extemp;
//	}
	
	public void setExtrusion(float extrusion) {
		this.extrusion = extrusion;
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
	

	@Override
	public String toString() {		
		String var = lineindex+":  "+datatoString();
		var+="\tExtrusion:"+extrusion;
		var+="\tDistance:"+distance;
		var+="\tPosition:"+curX+"x"+curY;
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
	
	public void setInitialized(short mask){
		if(mask > Constants.Z_MASK && ext==null)ext=new Extended();
	}
	
	/**
	 * check if a particular field is initialized
	 * Only check one field at once
	 * @param mask
	 * @return boolean
	 */
	public boolean isInitialized(int mask){
		switch (mask) {
		case Constants.E_MASK:
			return e != Float.MAX_VALUE;
		case Constants.X_MASK:
			return x != Float.MAX_VALUE;
		case Constants.Y_MASK:
			return y != Float.MAX_VALUE;
		case Constants.Z_MASK:
			return z != Float.MAX_VALUE;
		case Constants.F_MASK:
			return f != Float.MAX_VALUE;
		case Constants.SE_MASK:
			return ext != null && ext.s_ext != Float.MAX_VALUE;
		case Constants.SB_MASK:
			return ext != null && ext.s_bed != Float.MAX_VALUE;
		case Constants.SF_MASK:
			return ext != null && ext.s_fan != Float.MAX_VALUE;		
		case Constants.IX_MASK:
			return ext != null && ext.ix != Float.MAX_VALUE;		
		case Constants.JY_MASK:
			return ext != null && ext.jy != Float.MAX_VALUE;		
		case Constants.KZ_MASK:
			return ext != null && ext.kz != Float.MAX_VALUE;		
		case Constants.R_MASK:
			return ext != null && ext.r != Float.MAX_VALUE;		
		default:
			break;
		}
		return false;
	}
	
	  private MemoryEfficientString subSequence(int start, int end) {
		  if (start < 0 || end > (data.length)) {
		    throw new IllegalArgumentException("Illegal range " +
		      start + "-" + end + " for sequence of length " + data.length);
		  }
		  byte[] newdata = new byte[end-start];
		  System.arraycopy(data,start,newdata,0,end-start);
		  return new MemoryEfficientString(newdata);
		}
	  
	  private String datatoString() {
		  try {
		    return new String(data, 0, data.length, ENCODING);
		  } catch (UnsupportedEncodingException e) {
		    throw new RuntimeException("Unexpected: " + ENCODING + " not supported");
		  }
		}

}

package de.dietzm.gcodes;


import de.dietzm.Constants;
import de.dietzm.Constants.GCDEF;


public class GCodeFactory {

	static GCodeFactory factory = new GCodeFactory();
	
	public GCodeFactory() {}
	
	public static void setCustomFactory(GCodeFactory custom){
		factory = custom;
	}
	
	public static GCode getGCode(String line, int linenr){
		return factory.parseGcode(line, linenr);
	}

	
	public static void main(String[] args) {
//		Pattern temppattern = Pattern.compile( "[ZF]" ); 
//		Matcher matcher = temppattern.matcher();
//		StringBuffer newtemp = new StringBuffer();
//		while ( matcher.find() ){
//		  System.out.println(matcher.group());
//		  }
		 System.out.println("G1 E23 X32 Y43 Z30".matches(".*[FZJK].*"));
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#parseGcode(java.lang.String)
	 */
	private GCode parseGcode(String arg0,int linenr) {
		String codelinevar=arg0;
		GCDEF tmpgcode = Constants.GCDEF.UNKNOWN;

		//Find comments and strip them, init the comment filed 
		codelinevar = stripComment(codelinevar);
		codelinevar = codelinevar.toUpperCase();

		//Is Gcode valid ? plain Comments are marked as invalid as well
		if(!Constants.isValidGCode(codelinevar)){
			GCode min = createDefaultGCode(arg0,linenr,Constants.GCDEF.INVALID);
			return min;
		}
		
		Character id;		
		String[] segments = codelinevar.split(" ");
		String gcodestr=segments[0].trim();
		codelinevar=codelinevar.substring(segments[0].length()).trim(); //Cut GX to save string memory
		
		try {
			tmpgcode = Constants.GCDEF.valueOf(gcodestr);
		} catch (Exception e1) {
		}
		GCode gcd = null;
		
		switch (tmpgcode) {
		case G0:
		case G1:
			gcd=findMatches(segments, codelinevar,linenr,tmpgcode);
			break;
		case G2:
		case G3:
			gcd=findMatches(segments, codelinevar,linenr,tmpgcode);
			System.err.println("Experimental support of Gcode G2/G3.");
			break;
		case G4: //Dwell
			//TODO add to duration
			break;
		case G92:
			gcd=findMatches(segments, codelinevar,linenr,tmpgcode);
			break;
		case M140: //set bed temp and not wait
		case M190: //set bed temp and wait
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			id = segments[1].charAt(0);
			if (id=='S'){
				gcd.setInitialized(Constants.SB_MASK,parseSegment(segments[1]));
			}
			break;
		case M104: //set extruder temp and NOT wait
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			id = segments[1].charAt(0);
			if (id=='S'){
				gcd.setInitialized(Constants.SE_MASK,parseSegment(segments[1]));
			}
			break;
		case G90: //Absolute positioning
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			break;
		case M82: //Absolute positioning for extrusion
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			break;	
		case M83:
		case G91: //Relative positioning
			System.err.println("G91/M83 Relative Positioning is NOT supported.");
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			break;
		case G20: //Unit = inch
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			gcd.setUnit("in");
			break;
		case G21: //Unit = inch
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			gcd.setUnit("mm");
			break;
		case M109: //set extruder temp and wait
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			id = segments[1].charAt(0);
			if (id=='S'){
				gcd.setInitialized(Constants.SE_MASK,parseSegment(segments[1]));
			}
			break;
		case G161:
		case G162:
		case G28:
			gcd = createOptimizedGCode(codelinevar, linenr,tmpgcode);
			if (segments.length == 1) { //no param means home all axis
				gcd.setInitialized(Constants.X_MASK,0);
				gcd.setInitialized(Constants.Y_MASK,0);
				gcd.setInitialized(Constants.Z_MASK,0);
			} else {
				for (int i = 1; i < segments.length; i++) {
					
					id = segments[i].charAt(0);
					switch (id) {
					case 'X':
						gcd.setInitialized(Constants.X_MASK,0);
						break;
					case 'Y':
						gcd.setInitialized(Constants.Y_MASK,0);
						break;
					case 'Z':
						gcd.setInitialized(Constants.Z_MASK,0);
						break;
					}
				}
			}
			break;
		case M107: //reset fan speed (off)
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			gcd.setInitialized(Constants.SF_MASK,0);
			break;
		case M106: //reset fan speed (off)
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			if (segments.length == 1) { //no param means turn on fan full ?
				gcd.setInitialized(Constants.SF_MASK,255);
			} else {
				id = segments[1].charAt(0);
				if (id=='S'){
					gcd.setInitialized(Constants.SF_MASK,parseSegment(segments[1]));
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
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			break;
		case M103: //marlin turn all extr off
		case M101://marlin turn all extr on
		case M113: //set extruder speed / turn off
		//	System.err.println("Unsupported Gcode M101/M103/M113 found. Ignoring it.");
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			break;
		case M70: //replicatorG
		case M72://replicatorG
		case M73: //replicatorG
			//System.err.println("Unsupported Gcode M70/M72/M73 found. Ignoring it.");
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			break;
		case M108:
			System.err.println("Deprecated Gcode M108. Ignoring it.");
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			break;
		case M204:
			System.err.println("M204 Acceleration control is ignored.");
			gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
			break;
		default:
			System.err.println("Unknown Gcode "+linenr+": "+ codelinevar.substring(0,Math.min(15,codelinevar.length()))+"....");
			return createDefaultGCode(codelinevar,linenr,tmpgcode);
		}
		//update used values
	//	if(isInitialized(Constants.SB_MASK))		bedtemp=ext.s_bed;
	//	if(isInitialized(Constants.SE_MASK))		extemp=ext.s_ext;
		if(gcd.isInitialized(Constants.SF_MASK))	{
			gcd.setFanspeed((short)gcd.getS_Fan());
		}
		
		
		return gcd;
	}

	protected GCode findMatches(String[] segments,String line, int linenr, GCDEF code) {
		GCode gcd = createOptimizedGCode(line, linenr, code);
		
		Character id;
		for (int i = 1; i < segments.length; i++) {
			//System.out.println("segment:"+segments[i]);
			id = segments[i].charAt(0);
			switch (id) {
			case 'A':		//makerwares A is the new E
			case 'B':		//makerwares A is the new E 
			case 'E':
				gcd.setInitialized(Constants.E_MASK,parseSegment(segments[i]));
				break;
			case 'X':
				gcd.setInitialized(Constants.X_MASK,parseSegment(segments[i]));
				break;
			case 'Y':
				gcd.setInitialized(Constants.Y_MASK,parseSegment(segments[i]));
				break;
			case 'Z':
				gcd.setInitialized(Constants.Z_MASK,parseSegment(segments[i]));
				break;
			case 'F':
				gcd.setInitialized(Constants.F_MASK,parseSegment(segments[i]));
				break;
			case 'I':
				gcd.setInitialized(Constants.IX_MASK,parseSegment(segments[i]));
				break;
			case 'J':
				gcd.setInitialized(Constants.JY_MASK,parseSegment(segments[i]));
				break;
			case 'K':
				gcd.setInitialized(Constants.KZ_MASK,parseSegment(segments[i]));
				break;
			case 'R':
				gcd.setInitialized(Constants.R_MASK,parseSegment(segments[i]));
				break;
			default:
				break;
			}
		}
		return gcd;
	}


	
	/**
	 * Called for the movement GCodes (G1,G2,G3,..) which allows the Factory to choose alternative implementations.
	 * @param line
	 * @param linenr
	 * @param code
	 * @return GCode
	 */
	protected GCode createOptimizedGCode(String line, int linenr, GCDEF code) {
		return createDefaultGCode(line, linenr, code);
	}
	
	/**
	 * Called when Gcode needs to be created.
	 * Every movement commands (G1,G2,G3) will call the optimized version of this method.
	 * @param line
	 * @param linenr
	 * @param code
	 * @return GCode
	 */
	protected GCode createDefaultGCode(String line, int linenr, GCDEF code) {
		GCode gcd;
		gcd = new GCodeMemSave(line, linenr, code);
		return gcd;
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
			clv=clv.substring(0, idx);
		}else if((idx = clv.indexOf("(<")) != -1){
			//is a comment
			clv=clv.substring(0, idx);
		}else if((idx = clv.indexOf("(")) != -1){
			//is a comment
			clv=clv.substring(0, idx);
		}
		return clv.trim();
	}

	private float parseSegment(String segment){
		String num = segment.substring(1);
		return Float.parseFloat(num);
		//return new Float(num);
	}
}

package de.dietzm.gcodes;


import java.io.IOException;
import java.io.InputStream;

import de.dietzm.Constants;
import de.dietzm.Constants.GCDEF;
import de.dietzm.gcodesim.GcodeSimulator;
import de.dietzm.print.ReceiveBuffer;


public class GCodeFactoryBuffer extends GCodeFactory {

	static GCodeFactoryBuffer factory = new GCodeFactoryBuffer();
	private long readbytes=0, readlines=0;
	
	byte[] modeldata;
	
	public GCodeFactoryBuffer() {}
	

	/**
	 * Parse the segments and fill the fields in the gcode instance
	 * @param segments
	 * @param line
	 * @param linenr
	 * @param code
	 * @return
	 */
	protected GCode fillGcodeFields(String[] segments,ReceiveBuffer line, int linenr, GCDEF code) {
		GCode gcd = createOptimizedGCode(segments,line, linenr, code);
		
		Character id;
		for (int i = 1; i < segments.length; i++) {
			//System.out.println("segment:"+segments[i]);
			if(segments[i].length() == 0 ) continue;
			id = segments[i].charAt(0);
			switch (id) {
			case 'A':		//makerwares A is the new E
			case 'B':		//makerwares A is the new E
			case 'b':
			case 'a':
			case 'E':
			case 'e':
				gcd.setInitialized(Constants.E_MASK,Constants.parseFloat(segments[i],1));
				break;
			case 'X':
			case 'x':
				gcd.setInitialized(Constants.X_MASK,Constants.parseFloat(segments[i],1));
				break;
			case 'Y':
			case 'y':
				gcd.setInitialized(Constants.Y_MASK,Constants.parseFloat(segments[i],1));
				break;
			case 'Z':
			case 'z':
				gcd.setInitialized(Constants.Z_MASK,Constants.parseFloat(segments[i],1));
				break;
			case 'F':
			case 'f':
				gcd.setInitialized(Constants.F_MASK,Constants.parseFloat(segments[i],1));
				break;
			case 'I':
			case 'i':
				gcd.setInitialized(Constants.IX_MASK,Constants.parseFloat(segments[i],1));
				break;
			case 'J':
			case 'j':
				gcd.setInitialized(Constants.JY_MASK,Constants.parseFloat(segments[i],1));
				break;
			case 'K':
			case 'k':
				gcd.setInitialized(Constants.KZ_MASK,Constants.parseFloat(segments[i],1));
				break;
			case 'R':
			case 'r':
			case 'T': //Use r for t as well (double used var)
			case 't':
				gcd.setInitialized(Constants.R_MASK,Constants.parseFloat(segments[i],1));
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
	protected GCode createOptimizedGCode(String[] segments,ReceiveBuffer line, int linenr, GCDEF code) {
		GCode gcd;
		gcd = new GCodeMemSave(line.array, linenr, code);
		return gcd;
	}
	
	/**
	 * Called when Gcode needs to be created.
	 * Every movement commands (G1,G2,G3) will call the optimized version of this method.
	 * @param line
	 * @param linenr
	 * @param code
	 * @return GCode
	 */
	protected GCode createDefaultGCode(ReceiveBuffer line, int linenr, GCDEF code) {
		GCode gcd;
		gcd = new GCodeMemSave(line.array, linenr, code);
		return gcd;
	}

	protected GCodeStore loadGcodeModel(InputStream in)throws IOException{
		readbytes=0;
		readlines=0;
//		InputStreamReader fread =  new InputStreamReader(in);
//		BufferedReader gcread= new BufferedReader(fread,32768);
		GCodeStore codes = getGcodeStore(0);
		ReceiveBuffer buf = new ReceiveBuffer(2048);
//		String line;
		String errors = "Error while parsing gcodes:\n";
		modeldata = new byte[40000000];
		int idx=1;
		int offset=0;
		int len;
		int errorcnt=0, success=0;
		long time = System.currentTimeMillis();
		try{
		while((len =in.read(modeldata,offset,32768)) != -1){
			offset=offset+len;
		}
		int lastnl=0;
		System.out.println("Load Model in ms:"+(System.currentTimeMillis()-time));
		for (int i = 0; i < offset; i++) {
			if(modeldata[i] != '\n') continue;
			
			GCode gc = null;
			try {
//					String cd = "G1 X132.222 Y34.222 E0.111";//new String(modeldata,lastnl,i-lastnl);
				buf.put(modeldata,lastnl,i-lastnl);
				gc = parseGcodeBuf(buf,idx++);
				lastnl=i+1;
			} catch (Exception e) {
				e.printStackTrace();
			//	System.err.println("Error while parsing gcode:"+line+" (line:"+idx+")");
			}
			if(gc == null || gc.getGcode() == GCDEF.UNKNOWN){
					errorcnt++;
					//errors = errors + ("line:"+idx+"     "+line+"\n");
					if(errorcnt-success > 10 || gc == null){
						throw new IOException(errors);
					}	
			}else{ 
				success++;
			}
			codes.add(gc);
			readbytes=i; //might be incorrect for multibyte chars, but getbytes is expensive
			readlines++;
			
		}
		}catch(OutOfMemoryError oom){
			throw new IOException("Out of Memory Error");
		}
		in.close();
		codes.commit();
		System.out.println("Load Model finished in ms:"+(System.currentTimeMillis()-time));
		if(errorcnt != 0){
			System.err.println("Detected "+errorcnt+" error(s) during parsing of Gcode file. Results might be wrong.");
		}
		return codes;
	}
	/**
	 * Find comments and strip them, init the comment filed
	 * @param clv
	 * @return
	 */
	private ReceiveBuffer stripComment(ReceiveBuffer clv) {
		int idx;
		if((idx = clv.indexOf(';')) != -1){
			//is a comment
			clv.setlength(idx);
		}else if((idx = clv.indexOf('(')) != -1){ //INCLUDES (<
			//is a comment
			clv.setlength(idx);
		}
		return clv; //TODO trim
	}

	/* (non-Javadoc)
		 * @see de.dietzm.GCodeIf#parseGcode(java.lang.String)
		 */
		private GCode parseGcodeBuf(ReceiveBuffer arg0,int linenr) {
			ReceiveBuffer codelinevar=arg0;
			GCDEF tmpgcode = Constants.GCDEF.UNKNOWN;
	
			//Find comments and strip them, init the comment filed 
			codelinevar = stripComment(codelinevar);
			if(codelinevar.length()==0) { // plain Comments 
				return createDefaultGCode(arg0.toString(),linenr,Constants.GCDEF.COMMENT);
			}
	
			//codelinevar = codelinevar.toUpperCase(); 
			if(!Constants.isValidGCode(codelinevar)){
				GCode min = createDefaultGCode(arg0.toString(),linenr,Constants.GCDEF.UNKNOWN);
				return min;
			}
			
			Character id;		
			//String[] segments = codelinevar.split(" ");
			String[] segments = Constants.splitbyLetter2(codelinevar);
//			if(codelinevar.length() > segments[0].length() && codelinevar.charAt(segments[0].length())== ' '){
//				codelinevar=codelinevar.substring(Math.min(segments[0].length()+1,codelinevar.length())); //Cut GX to save string memory
//			}else{
//				codelinevar=codelinevar.substring(Math.min(segments[0].length(),codelinevar.length())); //Cut GX to save string memory
//			}
			
			try {
				tmpgcode = Constants.GCDEF.getGCDEF(segments[0]);
			} catch (Exception e1) {
				System.err.println("Parse GCODE GCDEF Exception:"+e1);
			}
			GCode gcd = null;
			
			switch (tmpgcode) {
			case G0:
			case G1:
				gcd=fillGcodeFields(segments, codelinevar,linenr,tmpgcode);
				break;
			case G2:
			case G3:
				gcd=fillGcodeFields(segments, codelinevar,linenr,tmpgcode);
				System.err.println("Experimental support of Gcode G2/G3.");
				break;
			case G4: //Dwell
				//TODO add to duration
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				break;
			case G92:
				gcd=fillGcodeFields(segments, codelinevar,linenr,tmpgcode);
				break;
			case M140: //set bed temp and not wait
			case M190: //set bed temp and wait
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				id = segments[1].charAt(0);
				if (id=='S' || id == 's'){
					gcd.setInitialized(Constants.SB_MASK,Constants.parseFloat(segments[1],1));
				}
				break;
			case M104: //set extruder temp and NOT wait
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				id = segments[1].charAt(0);
				if (id=='S'|| id == 's'){
					gcd.setInitialized(Constants.SE_MASK,Constants.parseFloat(segments[1],1));
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
				if (id=='S'|| id == 's'){
					gcd.setInitialized(Constants.SE_MASK,Constants.parseFloat(segments[1],1));
				}
				break;
			case G161:
			case G162:
			case G28:
				gcd = createOptimizedGCode(segments,codelinevar, linenr,tmpgcode);
				if (segments.length == 1) { //no param means home all axis
					gcd.setInitialized(Constants.X_MASK,0);
					gcd.setInitialized(Constants.Y_MASK,0);
					gcd.setInitialized(Constants.Z_MASK,0);
				} else {
					for (int i = 1; i < segments.length; i++) {
						
						id = segments[i].charAt(0);
						switch (id) {
						case 'X':
						case 'x':
							gcd.setInitialized(Constants.X_MASK,0);
							break;
						case 'Y':
						case 'y':
							gcd.setInitialized(Constants.Y_MASK,0);
							break;
						case 'Z':
						case 'z':
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
					if (id=='S' || id=='s'){
						gcd.setInitialized(Constants.SF_MASK,Constants.parseFloat(segments[1],1));
					}
				}
				break;
			case M84: //disable all motors
			case M18://Stop motor
			case M105: //get extr temp
			case M114: //get current position
			case M0: //Stop
			case M1: //Sleep
			case M116:
			case M117: //get zero position
			case M92: //set axis steps per unit (just calibration) 
			case M132: //set pid
			case G130:
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
			case M108: //Bfb gcodes
				//System.err.println("Deprecated Gcode M108. Ignoring it.");
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				id = segments[1].charAt(0);
				if (id=='S' || id=='s'){
					gcd.setInitialized(Constants.E_MASK,Constants.parseFloat(segments[1],1));
				}
				break;
			case M204:
				//System.err.println("M204 Acceleration control is ignored.");
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				break;
			case M218:
				//dual extrusion offset marlin
				gcd=fillGcodeFields(segments, codelinevar,linenr,tmpgcode);
				break;
			case UNKNOWN:
				System.err.println("Unknown Gcode "+linenr+": "+ tmpgcode+" "+segments[0]+" "+codelinevar.subSequence(0,Math.min(15,codelinevar.length()))+"....");
				gcd=createDefaultGCode(segments[0]+" "+codelinevar,linenr,tmpgcode);
				break;
			default:
				return createDefaultGCode(codelinevar,linenr,tmpgcode);
			}
			//update used values
	//		if(gcd.isInitialized(Constants.SF_MASK))	{
	//			gcd.setFanspeed((short)gcd.getS_Fan());
	//		}
			if(gcd==null){
				return createDefaultGCode(codelinevar,linenr,tmpgcode);
			}
			
			return gcd;
		}
	
	
	

}

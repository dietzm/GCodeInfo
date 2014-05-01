package de.dietzm.gcodes;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.dietzm.Constants;
import de.dietzm.Constants.GCDEF;



public class GCodeFactory  {

	static GCodeFactoryImpl defaultfactory = new GCodeFactory.DefaultGCodeFactoryImpl();
	static GCodeFactoryImpl factory = defaultfactory;

	
	public GCodeFactory() {}
	
	public static class DefaultGCodeFactoryImpl implements GCodeFactoryImpl{
		protected long readbytes=0, readlines=0;
		byte[] modeldata;

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
			gcd = new GCodeMemSave(line,  code);
			return gcd;
		}

		/**
		 * Called for the movement GCodes (G1,G2,G3,..) which allows the Factory to choose alternative implementations.
		 * @param line
		 * @param linenr
		 * @param code
		 * @return GCode
		 */
		protected GCode createOptimizedGCode(String[] segments,String line, int linenr, GCDEF code) {
			return createDefaultGCode(line, linenr, code);
		}

		/**
		 * Called to create a new GCodeStore instance
		 * @param size
		 * @return GCodeStore
		 */
		@Override
		public GCodeStore createStore(int size){
			return new GCodeStore(size);
		}

		/**
		 * Parse the segments and fill the fields in the gcode instance
		 * @param segments
		 * @param line
		 * @param linenr
		 * @param code
		 * @return
		 */
		protected GCode fillGcodeFields(String[] segments,String line, int linenr, GCDEF code) {
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

		public long getReadBytes(){
			return readbytes;
		}
		
		public long getReadLines(){
			return readlines;
		}
		
		
		/**
		 * Load the model from input stream
		 * @param in
		 * @param fsize might be 0
		 * @return
		 * @throws IOException
		 */
		@Override
		public GCodeStore loadGcodeModel(InputStream in, long fsize)throws IOException{
			GCodeStore codes = createStore(100000);
			readbytes=0;
			readlines=0;
			InputStreamReader fread =  new InputStreamReader(in);
			BufferedReader gcread= new BufferedReader(fread,65536);
		
			String line;
			String errors = "Error while parsing gcodes:\n";
			int idx=1;
			int errorcnt=0, success=0;
			long time = System.currentTimeMillis();
			
			try{
			while((line=gcread.readLine())!=null){
				GCode gc = null;
				try {
					gc = parseGcode(line, idx++);
				} catch (Exception e) {
					e.printStackTrace();
					System.err.println("Error while parsing gcode:"+line+" (line:"+idx+")");
				}
				if(gc == null || gc.getGcode() == GCDEF.UNKNOWN){
						errorcnt++;
						errors = errors + ("Error #"+errorcnt+" at line:"+idx+" Content:'"+line+"'\n");
						if(errorcnt-success > 10 || gc == null){
							gcread.close();
							throw new IOException(errors);
						}	
				}else{ 
					success++;
				}
				codes.add(gc);
				readbytes+=line.length(); //might be incorrect for multibyte chars, but getbytes is expensive
				readlines++;
				
			}
			}catch(OutOfMemoryError oom){
				throw new IOException("Out of Memory Error");
			}
			if(success == 0){
				throw new IOException("No valid Gcode line found");
			}
			gcread.close();
			codes.commit();
			System.out.println("Load Model finished in ms:"+(System.currentTimeMillis()-time));
			if(errorcnt != 0){
				System.err.println("Detected "+errorcnt+" error(s) during parsing of Gcode file. Results might be wrong.");
			}
			return codes;
		}

		/* (non-Javadoc)
			 * @see de.dietzm.GCodeIf#parseGcode(java.lang.String)
			 */
			public GCode parseGcode(String arg0,int linenr) {
				String codelinevar=arg0;
				GCDEF tmpgcode = Constants.GCDEF.UNKNOWN;
		
				//Find comments and strip them, init the comment filed 
				codelinevar = Constants.stripComment(codelinevar);
				if(codelinevar.length()==0) { // plain Comments 
					return createDefaultGCode(arg0,linenr,Constants.GCDEF.COMMENT);
				}
		
				//codelinevar = codelinevar.toUpperCase(); 
				if(!Constants.isValidGCode(codelinevar)){
					GCode min = createDefaultGCode(arg0,linenr,Constants.GCDEF.UNKNOWN);
					return min;
				}
				
				Character id;		
				//String[] segments = codelinevar.split(" ");
				String[] segments = Constants.splitbyLetter2(codelinevar);
				if(codelinevar.length() > segments[0].length() && codelinevar.charAt(segments[0].length())== ' '){
					codelinevar=codelinevar.substring(Math.min(segments[0].length()+1,codelinevar.length())); //Cut GX to save string memory
				}else{
					codelinevar=codelinevar.substring(Math.min(segments[0].length(),codelinevar.length())); //Cut GX to save string memory
				}
				
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
					System.err.println("Unknown Gcode "+linenr+": "+ tmpgcode+" "+segments[0]+" "+codelinevar.substring(0,Math.min(15,codelinevar.length()))+"....");
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
	
	public static void setCustomFactory(GCodeFactoryImpl custom){
		factory = custom;
	}
	
	public static GCode getGCode(String line, int linenr){
		try{
			return factory.parseGcode(line, linenr);
		}catch(Exception e){
			//not implemented
			try{
				return defaultfactory.parseGcode(line, linenr);
			}catch(Exception ue){
				return null;
			}
		}
	}
	
	public static GCodeStore loadModel(InputStream in, long fsize)throws IOException{
		System.out.println("Load Model started ("+factory.getClass().getName()+")");
		return factory.loadGcodeModel(in,fsize);
	}
	
	public static long getReadBytes(){
		return factory.getReadBytes();
	}
	public static long getReadLines(){
		return factory.getReadLines();
	}
	
	


	public static GCodeStore getGcodeStore(int size){
		return factory.createStore(size);
	}
	
	public static void main(String[] args) {
//		Pattern temppattern = Pattern.compile( "[ZF]" ); 
//		Matcher matcher = temppattern.matcher();
//		StringBuffer newtemp = new StringBuffer();
//		while ( matcher.find() ){
//		  System.out.println(matcher.group());
//		  }
		 System.out.println("Split");
//		 String s = " G1X323  Y323.32 F2 22 ";
		 String s =" G1 Z5 F5000\n";
		 long time = System.nanoTime();
		 for (int i = 0; i < 100; i++) {
			 s.split(" ");	
		 }
		 System.out.println("Split:"+(System.nanoTime()-time));
		 
		
		 time = System.nanoTime();
		 for (int i = 0; i < 100; i++) {
			 Constants.splitbyLetter(s);	
		 }
		 System.out.println("SplitBy:"+(System.nanoTime()-time));
		
		 time = System.nanoTime();
		 for (int i = 0; i < 100; i++) {
			 Constants.splitbyLetter2(s);	
		 }
		 System.out.println("SplitBy:"+(System.nanoTime()-time));
		 
		 String[] str = null;//Constants.splitbyLetter2(s);
		 for (int i = 0; i < str.length; i++) {
			System.out.println("-"+str[i]+"-");
			System.out.println("Len:"+str[i].length());
		}
	}
	
	
	

}

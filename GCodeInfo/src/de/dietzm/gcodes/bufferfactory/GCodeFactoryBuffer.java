package de.dietzm.gcodes.bufferfactory;


import java.io.IOException;
import java.io.InputStream;

import de.dietzm.Constants;
import de.dietzm.Constants.CharSeqBufView;
import de.dietzm.Constants.GCDEF;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeFactoryImpl;
import de.dietzm.gcodes.GCodeMemSave;
import de.dietzm.gcodes.GCodeStore;
import de.dietzm.gcodesim.GcodeSimulator;
import de.dietzm.print.ReceiveBuffer;


/**
 * GCodeFactory which uses Buffers instead of temporary Strings to avoid extra memory allocations
 * @author mdietz
 *
 */
public class GCodeFactoryBuffer implements GCodeFactoryImpl {

	//static GCodeFactoryBuffer factory = new GCodeFactoryBuffer();
	protected long readbytes=0, readlines=0, filesize=0;
	byte[] modeldata;
	ReceiveBuffer tmpbuf = new ReceiveBuffer(1024);
	int defaultgc=0;
	int optigc=0;
	boolean m101=false;
	int[] gcodetypes = new int[11];
	CharSeqBufView[] preallocSegment = {new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView()};
	
	public GCodeFactoryBuffer() {}
	

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
	public GCode fillGcodeFields(CharSequence[] segments,int seg_len, ReceiveBuffer line, int linenr, GCDEF code) {
		GCode gcd = createOptimizedGCode(segments,seg_len,line, linenr, code);
		
		Character id;
		for (int i = 1; i < seg_len; i++) {
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
			case 'P':
			case 'p':
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
	protected GCode createOptimizedGCode(CharSequence[] segments,int seg_len,ReceiveBuffer line2, int linenr, GCDEF code) {
		optigc++;
		GCode gcd;
		String line ="";
		//System.out.println("OptimizedGCode: "+line);
		try{
			char ch0 = '-';
			char ch1 = '-';
			char ch2 = '-';
			char ch3 = '-';
			
			if(seg_len >1) ch0 = segments[1].charAt(0);
			if(seg_len >2) ch1 = segments[2].charAt(0);
			if(seg_len >3) ch2 = segments[3].charAt(0);
			if(seg_len >4) ch3 = segments[4].charAt(0);
			
			if(seg_len==4 && ch0=='X' && ch1=='Y' && ch2=='E'){
				gcd = new GCodeXYEMin(line,  code);
				gcodetypes[0]++;
			}else if(seg_len==4 && ch0=='Y' && ch1=='X' && ch2=='E'){
				gcd = new GCodeXYEMin(line,  code);
				gcodetypes[0]++;
			}else if(seg_len==3 && ch0=='X' && ch1=='Y'){
				if(m101){//bfb style
					gcd = new GCodeXYEFMinBFB(line,  code);
					gcodetypes[0]++;
				}else{
					gcd = new GCodeXYMin(line,  code);
					gcodetypes[1]++;
				}		
			}else if(seg_len==3 && ch0=='Y' && ch1=='X'){
				if(m101){//bfb style
					gcd = new GCodeXYEFMinBFB(line,  code);
					gcodetypes[0]++;
				}else{
					gcd = new GCodeXYMin(line,  code);
					gcodetypes[1]++;
				}				
			}else if(seg_len==4 && ch0=='X' && ch1=='Y' && ch2=='F'){
				if(m101){//bfb style
					gcd = new GCodeXYEFMinBFB(line,  code);
					gcodetypes[6]++;
				}else{
					gcd = new GCodeXYFMin(line,  code);
					gcodetypes[2]++;
				}
			}else if(seg_len==4 && ch0=='F' && ch1=='X' && ch2=='Y'){
				if(m101){//bfb style
					gcd = new GCodeXYEFMinBFB(line,  code);
					gcodetypes[6]++;
				}else{
					gcd = new GCodeXYFMin(line,  code);
					gcodetypes[2]++;
				}
			}else if(seg_len==5 && ch0=='F' && ch1=='X' && ch2=='Y' && ch3=='E'){
				gcd = new GCodeXYEFMin(line,  code);
				gcodetypes[6]++;
			}else if(seg_len==5 && ch0=='X' && ch1=='Y' && ch2=='F' && ch3=='E'){
				gcd = new GCodeXYEFMin(line,  code);
				gcodetypes[6]++;
			}else if(seg_len==5 && ch0=='X' && ch1=='Y' && ch2=='E' && ch3=='F'){
				gcd = new GCodeXYEFMin(line,  code);
				gcodetypes[6]++;	
			}else if(seg_len==3 && ch0=='F' && ch1=='E'){
				gcd = new GCodeFEMin(line,  code);
				gcodetypes[3]++;
			}else if(seg_len==3 && ch0=='E' && ch1=='F'){
				gcd = new GCodeFEMin(line,  code);
				gcodetypes[3]++;
			}else if(seg_len==3 && ch0=='Z' && ch1=='F'){
				gcd = new GCodeZFMin(line,  code);
				gcodetypes[8]++;
			}else if(seg_len==3 && ch0=='F' && ch1=='Z'){
				gcd = new GCodeZFMin(line,  code);
				gcodetypes[8]++;
			}else if(seg_len==5 && ch0=='X' && ch1=='Y' && ch2=='F' && ch3=='Z'){
				//TODO fix for bfb
				gcd = new GCodeXYFZMin(line,  code);
				gcodetypes[7]++;	
			}else if(seg_len==5 && ch0=='X' && ch1=='Y' && ch2=='Z' && ch3=='F'){
				gcd = new GCodeXYFZMin(line,  code);
				gcodetypes[7]++;	
			}else if(seg_len==5 && ch0=='F' && ch1=='X' && ch2=='Y' && ch3=='Z'){
				gcd = new GCodeXYFZMin(line,  code);
				gcodetypes[7]++;
			}else if(seg_len==2 && ch0=='Z'){ //Z only
				gcd = new GCodeZMin(line,  code);
				gcodetypes[4]++;
			}else if(seg_len==2 && ch0=='E'){	//E only
				gcd = new GCodeEMin(line,  code);
				gcodetypes[5]++;
			}else if(seg_len==2 && ch0=='F'){	//E only
				gcd = new GCodeFMin(line,  code);
				gcodetypes[9]++;
			}else{
//				String DBG = "";
//				for (int i = 1; i < seg_len; i++) {
//					 DBG = DBG +segments[i].charAt(0);
//				}
//				System.out.println("Load: "+DBG);
				gcd = createDefaultGCode(line2, linenr, code);
				gcodetypes[10]++;
			}
		}catch(Exception e){
			//Handle parsing exceptions e.g. if indexoutofbounds happen because of multiple whitespaces
			e.printStackTrace();
			gcd = createDefaultGCode(line2, linenr, code);
		}
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
	protected GCode createDefaultGCode(ReceiveBuffer line2, int linenr, GCDEF code) {
		
		GCode gcd;
		int gcodelen=0;
		  if(code.equals(GCDEF.COMMENT)){
//			  if(false){//suppress comments
//				  gcd = new GCodeE("", code);
//				  return gcd;
//			  }
		  }else if(code.equals(GCDEF.UNKNOWN)){
			  //keep full string
		  }else{
			  //strip of gcode e.g. G1
			  gcodelen = code.toString().length();
			  if(line2.length() > gcodelen && line2.array[gcodelen]==32){
				  gcodelen++;
			  }
		  }
		  defaultgc++;
		  byte[] newdata = new byte[line2.length()-gcodelen];
		  System.arraycopy(line2.array,gcodelen,newdata,0,line2.length()-gcodelen);
		  gcd = new GCodeMemSave(newdata,  code);
		//  System.out.println(gcd.getCodeline());
		//gcd = new GCodeMemSaveMin(line.array,  code);
		return gcd;
	}

	public GCodeStore loadGcodeModel(InputStream in,long fsize)throws IOException{
		readbytes=0;
		readlines=0;
		defaultgc=0;
		filesize=fsize;
		optigc=0;
		gcodetypes = new int[11];

		GCodeStore codes = createStore(0);
		ReceiveBuffer buf = new ReceiveBuffer(65536);
		String errors = "Error while parsing gcodes:\n";
		modeldata = new byte[65536];
		int idx=1;
		int offset=0;
		int targetOffset=0;
		int len;
		int errorcnt=0, success=0;
		long time = System.currentTimeMillis();
		try{
		while((len =in.read(modeldata,0,65536)) != -1){
			offset=offset+len;
			int lastnl=0;
			//System.out.println("Load Model in ms:"+(System.currentTimeMillis()-time) );
			for (int i = 0; i < len; i++) {
			if(modeldata[i] != '\n') continue;
			GCode gc = null;
			try {
//					String cd = "G1 X132.222 Y34.222 E0.111";//new String(modeldata,lastnl,i-lastnl);
				
				buf.put(modeldata,lastnl,targetOffset, i-lastnl);
			//	System.out.println(idx+"put: lastnl:"+lastnl+" off:"+targetOffset+" len:"+(i-lastnl));
				targetOffset=0;
			//	System.out.println("New:"+new String(modeldata,lastnl,i-lastnl));
			//	System.out.println(buf.toString());
				gc = parseGcodeBuf(buf,idx++);
				lastnl=i+1;
			} catch (Exception e) {
				errors = errors + e.getMessage();
			//	System.err.println("Error while parsing gcode:"+line+" (line:"+idx+")");
			}
			if(gc == null || gc.getGcode() == GCDEF.UNKNOWN){
					errorcnt++;
					if(gc== null){
						errors = errors + ("Error #"+errorcnt+" at line:"+(idx-1)+" Content:'"+buf.toString()+"'\n");
					}else{
						errors = errors + ("Error #"+errorcnt+" Unknown GCODE at line:"+(idx-1)+" Content:'"+buf.toString()+"'\n");
					}
					if(errorcnt-success > 10 || gc == null){
						throw new IOException(errors);
					}	
			}else{ 
				success++;
			}
			codes.add(gc);
			readbytes=offset; //might be incorrect for multibyte chars, but getbytes is expensive
			readlines=idx;
			}
			if(lastnl<=len){
				buf.put(modeldata,lastnl,len-lastnl);
			//	System.out.println("LASTLEN:"+lastnl+" len:"+len+" "+buf.toString());
				targetOffset=len-lastnl;
			}
		}
		}catch(OutOfMemoryError oom){
			throw new IOException("Out of Memory Error");
		}
		in.close();
		if(success == 0){
			throw new IOException("No valid Gcode line found");
		}
		codes.commit();
		System.out.println("Load Model finished in ms:"+(System.currentTimeMillis()-time));
		System.out.println("Load: Default:"+defaultgc+ " Opti:"+optigc);
		System.out.println("Load: Opti:"+optigc);
		System.out.println("Load:  XYE:"+gcodetypes[0]);
		System.out.println("Load:  XY:"+gcodetypes[1]);
		System.out.println("Load:  XYF:"+gcodetypes[2]);
		System.out.println("Load:  FE:"+gcodetypes[3]);
		System.out.println("Load:  Z:"+gcodetypes[4]);
		System.out.println("Load:  E:"+gcodetypes[5]);
		System.out.println("Load:  XYFE:"+gcodetypes[6]);
		System.out.println("Load:  XYFZ:"+gcodetypes[7]);
		System.out.println("Load:  ZF:"+gcodetypes[8]);
		System.out.println("Load:  F"+gcodetypes[9]);
		System.out.println("Load:  OptiFallback:"+gcodetypes[10]);
		System.out.println("Load:  Size: "+readbytes+"/"+readlines);
		System.out.println("Load Used Memory:" 
				+ ( Runtime.getRuntime().totalMemory() -  Runtime.getRuntime().freeMemory()) / (1024*1024));
		if(errorcnt != 0){
			System.err.println("Detected "+errorcnt+" error(s) during parsing of Gcode file. Results might be wrong.");
		}
		m101=false;
		return codes;
	}
	
	/**
	 * Alternative load, load everything into on large buffer
	 * @param in
	 * @param filesize
	 * @return
	 * @throws IOException
	 */
	private GCodeStore loadGcodeModel1(InputStream in,long filesize)throws IOException{
		readbytes=0;
		readlines=0;
		defaultgc=0;
		optigc=0;
//		InputStreamReader fread =  new InputStreamReader(in);
//		BufferedReader gcread= new BufferedReader(fread,32768);
		GCodeStore codes = createStore(0);
		ReceiveBuffer buf = new ReceiveBuffer(2048);
//		String line;
		String errors = "Error while parsing gcodes:\n";
		if(filesize >0){
			modeldata = new byte[(int) filesize+32768];
		}else{
			modeldata = new byte[40000000];
		}
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
		System.out.println("Load Model in ms:"+(System.currentTimeMillis()-time) );
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
			readlines=idx;
			
		}
		}catch(OutOfMemoryError oom){
			throw new IOException("Out of Memory Error");
		}
		in.close();
		codes.commit();
		System.out.println("Load Model finished in ms:"+(System.currentTimeMillis()-time));
		System.out.println("Load: default="+defaultgc+ " Opti:"+optigc);
		System.out.println("Load Used Memory:" 
				+ ( Runtime.getRuntime().totalMemory() -  Runtime.getRuntime().freeMemory()) / (1024*1024));
		if(errorcnt != 0){
			System.err.println("Detected "+errorcnt+" error(s) during parsing of Gcode file. Results might be wrong.");
		}
		return codes;
	}
	

	/* (non-Javadoc)
		 * @see de.dietzm.GCodeIf#parseGcode(java.lang.String)
		 */
		private GCode parseGcodeBuf(ReceiveBuffer arg0,int linenr) {
			ReceiveBuffer codelinevar=arg0;
			GCDEF tmpgcode = Constants.GCDEF.UNKNOWN;
	
			//Find comments and strip them, init the comment filed 
			int oldlen = codelinevar.length();
			codelinevar = Constants.stripComment(codelinevar);
			codelinevar.trimRight();
			if(codelinevar.length()==0) { // plain Comments 
				codelinevar.setlength(oldlen); //recover comments
				return createDefaultGCode(arg0,linenr,Constants.GCDEF.COMMENT);
			}
	
			//codelinevar = codelinevar.toUpperCase(); 
			if(!Constants.isValidGCode(codelinevar)){
				GCode min = createDefaultGCode(arg0,linenr,Constants.GCDEF.UNKNOWN);
				return min;
			}
			
			Character id;
			int seg_len=0;
			try {
			//String[] segments = codelinevar.split(" ");
			seg_len= Constants.splitbyLetter3(codelinevar,preallocSegment);
//			if(codelinevar.length() > segments[0].length() && codelinevar.charAt(segments[0].length())== ' '){
//				codelinevar=codelinevar.substring(Math.min(segments[0].length()+1,codelinevar.length())); //Cut GX to save string memory
//			}else{
//				codelinevar=codelinevar.substring(Math.min(segments[0].length(),codelinevar.length())); //Cut GX to save string memory
//			}
			
			
				tmpgcode = Constants.GCDEF.getGCDEF(preallocSegment[0]);
			} catch (Exception e1) {
				System.err.println("Parse GCODE GCDEF Exception:"+e1+" STR:'"+preallocSegment[0]+"'");
			}
			GCode gcd = null;
			
			switch (tmpgcode) {
			case G0:
			case G1:
				gcd=fillGcodeFields(preallocSegment,seg_len, codelinevar,linenr,tmpgcode);
				break;
			case G2:
			case G3:
				gcd=fillGcodeFields(preallocSegment,seg_len, codelinevar,linenr,tmpgcode);
				System.err.println("Experimental support of Gcode G2/G3.");
				break;
			case G4: //Dwell
				//TODO add to duration
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				break;
			case G92:
				gcd=fillGcodeFields(preallocSegment, seg_len,codelinevar,linenr,tmpgcode);
				break;
			case M140: //set bed temp and not wait
			case M190: //set bed temp and wait
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				id = preallocSegment[1].charAt(0);
				if (id=='S' || id == 's'){
					gcd.setInitialized(Constants.SB_MASK,Constants.parseFloat(preallocSegment[1],1));
				}
				break;
			case M104: //set extruder temp and NOT wait
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				id = preallocSegment[1].charAt(0);
				if (id=='S'|| id == 's'){
					gcd.setInitialized(Constants.SE_MASK,Constants.parseFloat(preallocSegment[1],1));
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
				id = preallocSegment[1].charAt(0);
				if (id=='S'|| id == 's'){
					gcd.setInitialized(Constants.SE_MASK,Constants.parseFloat(preallocSegment[1],1));
				}else if (seg_len == 3) { 
					//M109 T0 S200
					id = preallocSegment[2].charAt(0);
					if (id=='S'|| id == 's'){
						gcd.setInitialized(Constants.SE_MASK,Constants.parseFloat(preallocSegment[2],1));
					}
				}
				break;
			case G161:
			case G162:
			case G28:
				gcd = createOptimizedGCode(preallocSegment,seg_len,codelinevar, linenr,tmpgcode);
				if (seg_len == 1) { //no param means home all axis
					gcd.setInitialized(Constants.X_MASK,0);
					gcd.setInitialized(Constants.Y_MASK,0);
					gcd.setInitialized(Constants.Z_MASK,0);
				} else {
					for (int i = 1; i < seg_len; i++) {
						
						id = preallocSegment[i].charAt(0);
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
				if (seg_len == 1) { //no param means turn on fan full ?
					gcd.setInitialized(Constants.SF_MASK,255);
				} else {
					id = preallocSegment[1].charAt(0);
					if (id=='S' || id=='s'){
						gcd.setInitialized(Constants.SF_MASK,Constants.parseFloat(preallocSegment[1],1));
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
				m101=false;
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				break;
			case M101://marlin turn all extr on
				//bfb style ... make sure to have E values in XY
				m101=true;
				System.out.println("M101 BFB style detected");
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				break;
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
				id = preallocSegment[1].charAt(0);
				if (id=='S' || id=='s' || id=='R' || id=='r'){
					gcd.setInitialized(Constants.E_MASK,Constants.parseFloat(preallocSegment[1],1));
				}
				break;
			case M204:
				//System.err.println("M204 Acceleration control is ignored.");
				gcd=createDefaultGCode(codelinevar, linenr, tmpgcode);
				break;
			case G10:
			case M218:
				//dual extrusion offset marlin
				gcd=fillGcodeFields(preallocSegment,seg_len, codelinevar,linenr,tmpgcode);
				break;
			case UNKNOWN:
				System.err.println("Unknown Gcode "+linenr+": "+ tmpgcode+" "+preallocSegment[0]+" "+codelinevar.subSequence(0,Math.min(15,codelinevar.length()))+"....");
				//gcd=createDefaultGCode(preallocSegment[0]+" "+codelinevar,linenr,tmpgcode);
				gcd=createDefaultGCode(codelinevar,linenr,tmpgcode);
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


		@Override
		public long getReadBytes() {
			return readbytes;
		}


		@Override
		public long getReadLines() {
			return readlines;
		}
		
		public long getFilesize(){
			return filesize;
		}


		@Override
		public GCode parseGcode(String arg0, int linenr) {
			tmpbuf.put(arg0.getBytes());
			return parseGcodeBuf(tmpbuf, linenr);
		}
	
	
	

}

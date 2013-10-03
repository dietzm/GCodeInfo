package de.dietzm.gcodes;

import de.dietzm.Constants.GCDEF;

public class GCodeFactoryLowMem extends GCodeFactory {

	@Override
	protected GCode createOptimizedGCode(String line, int linenr, GCDEF code) {
		GCode gcd;
		if(!line.matches(".*[ZXYFIJKR].*")){
			//E only
			gcd = new GCodeE(line, linenr, code);
		}else if(!line.matches(".*[XYEFIJKR].*")){
			gcd = new GCodeZ(line, linenr, code);
		}else if(!line.matches(".*[ZEFIJKR].*")){
			gcd = new GCodeXY(line, linenr, code);
		}else if(!line.matches(".*[XYZIJKR].*")){
			gcd = new GCodeFE(line, linenr, code);
		}else if(!line.matches(".*[ZEIJKR].*")){
			gcd = new GCodeXYF(line, linenr, code);
		}else if(!line.matches(".*[ZFIJKR].*")){
			//if(segments.length<4) System.out.println("GCXYE: "+line);
			gcd = new GCodeXYE(line, linenr, code);
		}else{
		//	System.out.println("GCM: "+line);
			gcd = new GCodeMemSave(line, linenr, code);
		}
		return gcd;
	}
	
}

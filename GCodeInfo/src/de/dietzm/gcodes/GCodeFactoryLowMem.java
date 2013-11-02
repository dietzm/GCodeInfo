package de.dietzm.gcodes;

import de.dietzm.Constants.GCDEF;

public class GCodeFactoryLowMem extends GCodeFactory {

	@Override
	protected GCode createOptimizedGCode(String[] segments, String line, int linenr, GCDEF code) {
		GCode gcd;
		//System.out.println("OptimizedGCode: "+line);
		try{
			if(segments.length==2 && segments[1].charAt(0)=='E'){	//E only
				gcd = new GCodeE(line, linenr, code);
			}else if(segments.length==2 && segments[1].charAt(0)=='Z'){ //Z only
				gcd = new GCodeZ(line, linenr, code);
			}else if(segments.length==3 && segments[1].charAt(0)=='X' && segments[2].charAt(0)=='Y'){
				gcd = new GCodeXY(line, linenr, code);
			}else if(segments.length==3 && segments[1].charAt(0)=='Y' && segments[2].charAt(0)=='X'){
				gcd = new GCodeXY(line, linenr, code);
			}else if(segments.length==3 && segments[1].charAt(0)=='F' && segments[2].charAt(0)=='E'){
				gcd = new GCodeFE(line, linenr, code);
			}else if(segments.length==3 && segments[1].charAt(0)=='E' && segments[2].charAt(0)=='F'){
				gcd = new GCodeFE(line, linenr, code);
			}else if(segments.length==4 && segments[1].charAt(0)=='X' && segments[2].charAt(0)=='Y' && segments[3].charAt(0)=='E'){
				gcd = new GCodeXYE(line, linenr, code);
			}else if(segments.length==4 && segments[1].charAt(0)=='Y' && segments[2].charAt(0)=='X' && segments[3].charAt(0)=='E'){
				gcd = new GCodeXYE(line, linenr, code);
				//TODO handle all combinations
			}else if(segments.length==4 && segments[1].charAt(0)=='X' && segments[2].charAt(0)=='Y' && segments[3].charAt(0)=='F'){
				gcd = new GCodeXYF(line, linenr, code);
			}else{
				gcd = new GCodeMemSave(line, linenr, code);
			}
		}catch(Exception e){
			//Handle parsing exceptions e.g. if indexoutofbounds happen because of multiple whitespaces
			e.printStackTrace();
			gcd = new GCodeMemSave(line, linenr, code);
		}
		return gcd;
	}
	
}

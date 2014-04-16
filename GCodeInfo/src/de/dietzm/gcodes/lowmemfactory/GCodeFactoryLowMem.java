package de.dietzm.gcodes.lowmemfactory;

import de.dietzm.Constants.GCDEF;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeMemSave;
import de.dietzm.gcodes.GCodeFactory.DefaultGCodeFactoryImpl;
import de.dietzm.print.ReceiveBuffer;

public class GCodeFactoryLowMem extends DefaultGCodeFactoryImpl {

	@Override
	protected GCode createOptimizedGCode(String[] segments, String line,int linenr,  GCDEF code) {
		GCode gcd;
		//System.out.println("OptimizedGCode: "+line);
		try{
			char ch0 = '-';
			char ch1 = '-';
			char ch2 = '-';
			
			if(segments.length >1) ch0 = segments[1].charAt(0);
			if(segments.length >2) ch1 = segments[2].charAt(0);
			if(segments.length >3) ch2 = segments[3].charAt(0);
			
			if(segments.length==4 && ch0=='X' && ch1=='Y' && ch2=='E'){
				gcd = new GCodeXYE(line,  code);
			}else if(segments.length==4 && ch0=='Y' && ch1=='X' && ch2=='E'){
				gcd = new GCodeXYE(line,  code);
			}else if(segments.length==3 && ch0=='X' && ch1=='Y'){
				gcd = new GCodeXY(line,  code);
			}else if(segments.length==3 && ch0=='Y' && ch1=='X'){
				gcd = new GCodeXY(line,  code);			
				//TODO handle all combinations
			}else if(segments.length==4 && ch0=='X' && ch1=='Y' && ch2=='F'){
				gcd = new GCodeXYF(line,  code);
			}else if(segments.length==3 && ch0=='F' && ch1=='E'){
				gcd = new GCodeFE(line,  code);
			}else if(segments.length==3 && ch0=='E' && ch1=='F'){
				gcd = new GCodeFE(line,  code);
			}else if(segments.length==2 && ch0=='Z'){ //Z only
				gcd = new GCodeZ(line,  code);
			}else if(segments.length==2 && ch0=='E'){	//E only
				gcd = new GCodeE(line,  code);
			}else{
				gcd = new GCodeMemSave(line,  code);
			}
		}catch(Exception e){
			//Handle parsing exceptions e.g. if indexoutofbounds happen because of multiple whitespaces
			e.printStackTrace();
			gcd = new GCodeMemSave(line,  code);
		}
		return gcd;
	}
	

	protected GCode createOptimizedGCode(String[] segments, ReceiveBuffer line2, int linenr, GCDEF code) {
		GCode gcd;
		String line ="";
		//System.out.println("OptimizedGCode: "+line);
		try{
			char ch0 = '-';
			char ch1 = '-';
			char ch2 = '-';
			
			if(segments.length >1) ch0 = segments[1].charAt(0);
			if(segments.length >2) ch1 = segments[2].charAt(0);
			if(segments.length >3) ch2 = segments[3].charAt(0);
			
			if(segments.length==4 && ch0=='X' && ch1=='Y' && ch2=='E'){
				gcd = new GCodeXYE(line,  code);
			}else if(segments.length==4 && ch0=='Y' && ch1=='X' && ch2=='E'){
				gcd = new GCodeXYE(line,  code);
			}else if(segments.length==3 && ch0=='X' && ch1=='Y'){
				gcd = new GCodeXY(line,  code);
			}else if(segments.length==3 && ch0=='Y' && ch1=='X'){
				gcd = new GCodeXY(line,  code);			
				//TODO handle all combinations
			}else if(segments.length==4 && ch0=='X' && ch1=='Y' && ch2=='F'){
				gcd = new GCodeXYF(line,  code);
			}else if(segments.length==3 && ch0=='F' && ch1=='E'){
				gcd = new GCodeFE(line,  code);
			}else if(segments.length==3 && ch0=='E' && ch1=='F'){
				gcd = new GCodeFE(line,  code);
			}else if(segments.length==2 && ch0=='Z'){ //Z only
				gcd = new GCodeZ(line,  code);
			}else if(segments.length==2 && ch0=='E'){	//E only
				gcd = new GCodeE(line,  code);
			}else{
				gcd = new GCodeMemSave(line,  code);
			}
		}catch(Exception e){
			//Handle parsing exceptions e.g. if indexoutofbounds happen because of multiple whitespaces
			e.printStackTrace();
			gcd = new GCodeMemSave(line,  code);
		}
		return gcd;
	}
	
	
	

	
}


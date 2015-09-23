package de.dietzm;

public class ModelUtil {

	public ModelUtil() {
		// TODO Auto-generated constructor stub
	}

	public static String getFileSizeString(Model mod) {
		long fs = mod.getFilesize();
		String sz; 
		if(fs >= 1000000){
			float fs1 = fs / 1000000f;
			sz=Constants.floatToString2(fs1)+"MB";
		}else if(fs >=1000){
			float fs1 = fs / 1000f;
			sz=Constants.floatToString2(fs1)+"KB";	        			
		}else{
			sz=fs+" B";
		}
		return sz;
	}
	
	public static String getPrintTimeString(Model mod){
		return Constants.formatTimetoHHMMSS(mod.getTimeaccel(), new StringBuilder());
	}
	
	public static String getPrintTimeString(Layer mod){
		return Constants.formatTimetoHHMMSS(mod.getTimeAccel(), new StringBuilder());
	}
	
	public static String getPrintTimeString(float tm){
		return Constants.formatTimetoHHMMSS(tm, new StringBuilder());
	}
	
	public static String getLayerHeightString(Model mod){
		return Constants.floatToString2(mod.getAvgLayerHeight())+" mm";
	}
	
	public static String getExtrusionString(float extr){
		
		if(extr >= 1000){
			extr = extr / 1000f;
			return Constants.floatToString2(extr)+" m";
		}else{
			return Constants.floatToString2(extr)+" mm";
		}	  
	}

}

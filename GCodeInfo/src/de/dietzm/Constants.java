package de.dietzm;

import java.io.UnsupportedEncodingException;

public class Constants {

	public static enum GCDEF {
		G0(0),G1(1),G2(2),G3(3),G4(4),G20(20),G21(21),G28(28),G29(29),G30(30),G31(31),G32(32),G90(90),G91(91),G92(92),G161(161),G162(162),M0(1000),M1(1001),M6(1006),M18(1018),M70(1070),M72(1072),M73(1073),M92(1092),M101(1101),M103(1103),M104(1104),M105(1105),M106(1106),M107(1107),M108(1108),M109(1109),M113(1113),M114(1114),M117(1115),M132(1132),M140(1140),M190(1190),M204(1204),M82(1082),M83(1083),M84(1084),UNKNOWN(Short.MAX_VALUE),INVALID(Short.MIN_VALUE);
		
		 private short idx;
		 private byte[] bytes;

		   private GCDEF(int lidx) { 
			   this.idx = (short)lidx;
			   try{
				   bytes = toString().getBytes(Constants.ENCODING);
			   } catch (UnsupportedEncodingException e) {
			      throw new RuntimeException("GCDEF Unexpected: " + Constants.ENCODING + " not supported!");
			   }
		   }

		   /**
		    * Get a space efficient identifier
		    * @return
		    */
		   public short getId(){
			   return idx;
		   }
		   
		   public byte[] getBytes(){
			   return bytes;
		   }

		   /**
		    * Gets the enum object from a short identifier
		    * @param index
		    * @return
		    */
		   public static GCDEF getGCDEF(short index) {
			   //Shortcuts for the most common GCODES
			   if(index==0){
				   return G0;
			   }
			   if(index==1){
				   return G1;
			   }
			   if(index==2){
				   return G2;
			   }
			   if(index==3){
				   return G3;
			   }
			   if(index==92){
				   return G92;
			   }
			   if(index==Short.MAX_VALUE){
				   return UNKNOWN;
			   }
			   if(index==Short.MIN_VALUE){
				   return INVALID;
			   }
			   
			   
		      for (GCDEF l : GCDEF.values()) {
		          if (l.idx == index) return l;
		      }
		      throw new IllegalArgumentException("GCDEF not found. ");
		   }
		   
		   public boolean equals(short id){
			   return idx==id;
		   }
	}

	//Masks
	public final  static short E_MASK=1;
	public final  static short F_MASK=2;
	public final  static short X_MASK=4;
	public final  static short Y_MASK=8;
	public final  static short Z_MASK=16;
	public final  static short SF_MASK=32;
	public final  static short SE_MASK=64;
	public final  static short SB_MASK=128;
	public final  static short R_MASK=256;
	public final  static short IX_MASK=512;
	public final  static short JY_MASK=1024;
	public final  static short KZ_MASK=2048;
	
	public static final String ENCODING = "ISO-8859-1";
	
	public final static byte newlineb = 10;
	public final static byte spaceb = 32;
	
	public final static byte[] newline = new byte[]{newlineb};
	public final static char newlinec = '\n';
	
	
	public final static String RETRACT_LABEL ="R";
	public final static String XYSPEED_LABEL ="XY Speed";
	public final static String ESPEED_LABEL ="E Speed";
	public final static String ZPOS_LABEL ="Z Position";
	public final static String FAN_LABEL ="Fan";
	public final static String PRINT_LABEL ="Print";
	public final static String PRINTSTART_LABEL ="#";
	public final static String SPEEDUP_LABEL ="Speedup";
	public final static String REMTIME_LABEL ="Remaining Time";
	public final static String LAYER_LABEL ="Layer #";
	public final static String ZERO_LABEL ="0";
	public final static String FANS0_LABEL ="";
	public final static String FANS1_LABEL ="I";
	public final static String FANS2_LABEL ="II";
	public final static String FANS3_LABEL ="III";
	
	
	/**
	 * pass Stringbuffer to avoid allocation
	 * @param secs
	 * @param buf
	 * @return
	 */
	public static String formatTimetoHHMMSS(float secs, StringBuilder buf)
	{		
		int secsIn = Math.round(secs);
		int hours =  secsIn / 3600,
		remainder =  secsIn % 3600,
		minutes = remainder / 60,
		seconds = remainder % 60;

		if(buf==null){
			buf = new StringBuilder();
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
	
	/**
	 * returns true if it starts with G or M (should be trimmed and uppercase already)
	 * @return boolean gcode
	 */
	public static boolean isValidGCode(String codeline){
		 if(codeline == null || codeline.isEmpty()) return false;
		 if (codeline.startsWith("G")) return true;
		 if (codeline.startsWith("M")) return true;
		 if (codeline.startsWith("T")) return true;
		 //if (codeline.startsWith("g")) return true;
		 //if (codeline.startsWith("m")) return true;
		 return false;
	}
	
	public static float round3digits(float num){
		return Math.round((num)*1000f)/1000f;
	}
	
	/**
	 * Heavyweight
	 * @param var
	 * @return
	 */
	public static String removeTrailingZeros(String var) {
		return var.replaceAll("[0]*$", "").replaceAll("\\.$", "");
	}
}

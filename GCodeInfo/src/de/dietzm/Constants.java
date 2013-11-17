package de.dietzm;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import de.dietzm.gcodes.MemoryEfficientString;

public class Constants {

	static char[] buffer = new char[1024];
	static List<String> list = new ArrayList<String>();
	public static long lastGarbage=0;
	
	public static enum GCDEF {
		G0(0),G1(1),G2(2),G3(3),G4(4),G20(20),G21(21),G28(28),G29(29),G30(30),G31(31),G32(32),G90(90),G91(91),G92(92),G130(130),G161(161),G162(162),M0(1000),M1(1001),M6(1006),M18(1018),M70(1070),M72(1072),M73(1073),M92(1092),M101(1101),M103(1103),M104(1104),M105(1105),M106(1106),M107(1107),M108(1108),M109(1109),M113(1113),M114(1114),M116(1116),M117(1115),M130(1130),M132(1132),M133(1133),M134(1134),M135(1135),M136(1136),M137(1137),M140(1140),M190(1190),M220(1220),M204(1204),M82(1082),M83(1083),M84(1084),T0(5000),UNKNOWN(Short.MAX_VALUE),COMMENT(Short.MIN_VALUE);
		
		 private short idx;
		 private byte[] bytes;
		 private static GCDEF[] myvals = values(); //call to values always allocates new memory. avoid that by storing a copy
		 
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
		   
		   
		   public static GCDEF getGCDEF(String val) {
			   if(val.equalsIgnoreCase("G1")){
				    return G1;
			   }
			   if(val.equalsIgnoreCase("G2")){
				    return G2;
			   }
			   if(val.equalsIgnoreCase("G3")){
				    return G3;
			   }
			   if(val.equalsIgnoreCase("G92")){
				    return G92;
			   }
			   if(val.charAt(0) > 97){ //Check if lowercase
				   return GCDEF.valueOf(val.toUpperCase());   
			   }else{
				   return GCDEF.valueOf(val);
			   }
			   
			   
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
				   return COMMENT;
			   }
			   
			   
		      for (GCDEF l : myvals) {
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
	
	/**
	 * pass byte[] to avoid allocation
	 * @param secs
	 * @param buf - must be 10 bytes large
	 * @return
	 */
	public static byte[] formatTimetoHHMMSS(float secs, byte[] buf)
	{		
		int secsIn = Math.round(secs);
		int hours =  secsIn / 3600,
		remainder =  secsIn % 3600,
		minutes = remainder / 60,
		seconds = remainder % 60;

		if(buf==null){
			buf = new byte[10]; //Accounts for 9999 hours 
		}
		int cnt = 0;
		if(hours > 999){
			buf[cnt++]=(byte)Character.forDigit(hours/1000%10, 10);
		}
		if(hours > 99){
			buf[cnt++]=(byte)Character.forDigit(hours/100%10, 10);
		}
		if(hours > 9){
			buf[cnt++]=(byte)Character.forDigit(hours/10%10, 10);
		}
		buf[cnt++]=(byte)Character.forDigit(hours%10, 10);
		buf[cnt++]=':';
		buf[cnt++]=(byte)Character.forDigit(minutes/10, 10);
		buf[cnt++]=(byte)Character.forDigit(minutes%10, 10);
		buf[cnt++]=':';
		buf[cnt++]=(byte)Character.forDigit(seconds/10, 10);
		buf[cnt++]=(byte)Character.forDigit(seconds%10, 10);
		while(cnt < buf.length){
			buf[cnt++]=0;
		}
	return buf;
	}
	
	/**
	 * Memory efficient convert of int to char 
	 * @param value
	 * @param result - should be 12 bytes large
	 * @return
	 */
	public static MemoryEfficientString inttoChar(int value, MemoryEfficientString result){
		inttoChar(value, result.getBytes());
		return result;
	}
	
	/**
	 * Memory efficient convert of float to char
	 * WARNING: does not support the full range 
	 * @param value
	 * @param result - should be 12 bytes large
	 * @return
	 */
	public static MemoryEfficientString floattoChar(float value, MemoryEfficientString result, int digits){
		floattoChar(value, result.getBytes(), digits);
		return result;
	}
	
	/**
	 * Memory efficient convert of int to char 
	 * @param value
	 * @param result - should be 12 bytes large
	 * @return
	 */
	public static byte[] inttoChar(int value, byte[] result){
			int cnt = 0;
	       
           if (value == 0)
           {
                   result[cnt++] = '0';
           }else{
        	   if (value < 0)
               {
                       result[cnt++] = '-';
                       value = -value;
               }
        	   if( value == Integer.MIN_VALUE){
        		   //min value cannot be converted into a positive number because max value is 2^31-1
            	   result[cnt++] = '2';
            	   value=147483648; //remaining part
               } 
           int t = 1000000000;
           while (value < t) t /= 10;
           while (t > 0)
           {
                   int d = value / t;
                   result[cnt++] = (byte)('0' + d);
                   value -= d * t;
                   t /= 10;
           }
           }
           //Clear the remaining bytes
           while(cnt < result.length){
   			result[cnt++]=0;
   			}
           return result;
	}
	
	/**
	 * Memory efficient convert of float to char
	 * WARNING Does not support the full float range !! 
	 * @param value
	 * @param result - should be 12 bytes large
	 * @return
	 */
	public static byte[] floattoChar(float val, byte[] result, int digits){
			int cnt = 0;
			int multip=(int)Math.pow(10,digits);
	       int value = (int)(val * multip); //3 digits 
           if (value == 0)
           {
                   result[cnt++] = '0';
           }else{
        	   if (value < 0)
               {
                       result[cnt++] = '-';
                       value = -value;
               }
        	   if( value == Integer.MIN_VALUE){
        		   //min value cannot be converted into a positive number because max value is 2^31-1
            	   result[cnt++] = '2';
            	   value=147483648; //remaining part
               } 
           int t = 1000000000;
           while (value < t) t /= 10;
           if(Math.abs(val) < 1) { //special case to print leading zero
        	   result[cnt++] =(byte)'0';
        	   result[cnt++] =(byte)'.';
           }
           while (t > 0)
           {
                   int d = value / t;
                   result[cnt++] = (byte)('0' + d);
                   if(t==multip) result[cnt++] =(byte)'.';
                   value -= d * t;
                   t /= 10;
           }
           }
           //Clear the remaining bytes
           while(cnt < result.length){
   			result[cnt++]=0;
   			}
           return result;
	}
	
	public static void main(String[] args) {
		MemoryEfficientString me = new MemoryEfficientString(new byte[12]);
		formatTimetoHHMMSS(393699, me.getBytes());
		System.out.println(me.toString());
		floattoChar(1.1f, me.getBytes(),2);
		System.out.println(me.toString());
		
		floattoChar(-0.1f, me.getBytes(),2);
		System.out.println(me.toString());	
		
		floattoChar(0.99f, me.getBytes(),2);
		System.out.println(me.toString());	
		inttoChar(Integer.MIN_VALUE, me.getBytes());
		
		//Failure case
		float f = 0.0054454f;
		System.out.println(parseFloat("0.0054454545", 0));
		//System.out.println(Float.MIN_VALUE);
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
		 if (codeline.charAt(0) == 'G' || codeline.charAt(0) == 'g') return true;
		 if (codeline.charAt(0) == 'M' || codeline.charAt(0) == 'm') return true;
		 if (codeline.charAt(0) == 'T' || codeline.charAt(0) == 't') return true;
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

	/**
	 * Parse a float much quicker than Float.parseFloat().
	 * But has less precision, gives wrong results for high precision floats
	 * Seems to be ok for 4 digits beyond the comma which is fine for 3d print
	 * @param f
	 * @param startpos
	 * @return
	 */
	public static float parseFloat(String f,int startpos) {
		final int len   = f.length();
		float     ret   = 0f;         // return value
		int       pos   = startpos;          // read pointer position
		int       part  = 0;          // the current part (int, float and sci parts of the number)
		boolean   neg   = false;      // true if part is a negative number
	
		// find start
		while (pos < len && (f.charAt(pos) < '0' || f.charAt(pos) > '9') && f.charAt(pos) != '-' && f.charAt(pos) != '.')
			pos++;
	
		// sign
		if (f.charAt(pos) == '-') { 
			neg = true; 
			pos++; 
		}
	
		// integer part
		while (pos < len && !(f.charAt(pos) > '9' || f.charAt(pos) < '0'))
			part = part*10 + (f.charAt(pos++) - '0');
		ret = neg ? (float)(part*-1) : (float)part;
	
		// float part
		if (pos < len && f.charAt(pos) == '.') {
			pos++;
			int mul = 1;
			part = 0;
			while (pos < len && !(f.charAt(pos) > '9' || f.charAt(pos) < '0')) {
				part = part*10 + (f.charAt(pos) - '0'); 
				mul*=10; pos++;
			}
			ret = neg ? ret - (float)part / (float)mul : ret + (float)part / (float)mul;
		}
	
		// scientific part
		if (pos < len && (f.charAt(pos) == 'e' || f.charAt(pos) == 'E')) {
			pos++;
			neg = (f.charAt(pos) == '-'); pos++;
			part = 0;
			while (pos < len && !(f.charAt(pos) > '9' || f.charAt(pos) < '0')) {
				part = part*10 + (f.charAt(pos++) - '0'); 
			}
			if (neg)
				ret = ret / (float)Math.pow(10, part);
			else
				ret = ret * (float)Math.pow(10, part);
		}	
		return ret;
	}

	public static String[] splitbyLetter(String text){
	            List<String> list = new ArrayList<String>();
	            int pos = 0;
	            int trailws=0;
	            int len = text.length();
	            boolean first=true;
	            
	            
	            for (int i = 0; i < len; i++) {
	            	char c = text.charAt(i);
					if(c > 58){ //ASCII no number and no whitespace
						if(first){
							first=false;
							continue;
						}
						list.add(text.substring(pos, i-trailws));
						pos=i;
					}else if (i==pos && c == 32){
						pos++;
					}else if (c == 32){
						trailws++;
					}else{
						trailws=0;
					}
				}
	            list.add(text.substring(pos, len-trailws));
	            return list.toArray(new String[list.size()]);
	}
	
	public static String[] splitbyLetter2(String text){
        list.clear();
        int pos = 0;
        int len = text.length();      
        boolean first=true;
        
        
        for (int i = 0; i < len; i++) {
        	char c = text.charAt(i);
			if(c > 58){ //ASCII no number and no whitespace
				if(first){
					first=false;
					buffer[pos++]=c;
					continue;
				}
				list.add(new String(buffer,0,pos));
				pos=0;
				buffer[pos++]=c;
			}else if (c == 32 || c == 10){
				//ignore spaces and newlines
			}else{
				buffer[pos++]=c;
			}
		}
        list.add(new String(buffer,0,pos));
        return list.toArray(new String[list.size()]);
}


}

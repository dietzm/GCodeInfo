package de.dietzm;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import de.dietzm.gcodes.MemoryEfficientLenString;
import de.dietzm.gcodes.MemoryEfficientString;
import de.dietzm.print.ReceiveBuffer;

public class Constants {

	static char[] buffer = new char[1024];
	static List<String> list = new ArrayList<String>();
	public static long lastGarbage=0;
	static ReceiveBuffer tmpformatbuf = new ReceiveBuffer(256);
	static Formatter tmpformat = new Formatter(tmpformatbuf);
	
	
	public static class CharSeqBufView implements CharSequence{

		ReceiveBuffer srcbuf;
		int offset =0 ;
		int len = 0;
		
		public CharSeqBufView(){
			//init empty
		}
		
		public void setcontent(ReceiveBuffer rbuf,int off, int length){
			this.srcbuf=rbuf;
			this.offset=off;
			this.len=length;
		}
		
		@Override
		public int length() {
			return len;
		}

		@Override
		public char charAt(int index) {
			return srcbuf.charAt(offset+index);
		}

		@Override
		public CharSequence subSequence(int start, int end) {
			return srcbuf.subSequence(offset+start, offset+end);
		}
		
		@Override
		public String toString() {
			return new String(srcbuf.array,offset,len);
		}
		
	}
	public static enum GCDEF {
		G0(0),G00(0),G1(1),G01(1),G2(2),G02(2),G3(3),G03(3),G4(4),G04(4),G10(10),G20(20),G21(21),G28(28),G29(29),G30(30),G31(31),G32(32),G90(90),G91(91),G92(92),G130(130),G161(161),G162(162),M0(1000),M1(1001),M6(1006),M18(1018),M20(1020),M21(1021),M22(1022),M23(1023),M24(1024),M25(1025),M26(1026),M27(1027),M28(1028),M29(1029),M30(1030),M42(1042),M70(1070),M72(1072),M73(1073),M92(1092),M80(1080),M81(1081),M101(1101),M103(1103),M104(1104),M105(1105),M106(1106),M107(1107),M108(1108),M109(1109),M112(1112),M113(1113),M114(1114),M116(1116),M117(1117),M126(1126),M127(1127),M130(1130),M132(1132),M133(1133),M134(1134),M135(1135),M136(1136),M137(1137),M140(1140),M190(1190),M204(1204),M218(1218),M220(1220),M221(1221),M226(1226),M227(1227),M228(1228),M82(1082),M83(1083),M84(1084),T0(5000),T1(5001),T2(5002),T3(5003),T4(5004),GO(5010),UNKNOWN(Short.MAX_VALUE),COMMENT(Short.MIN_VALUE);
		
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
			   if(val.equals("G1")){
				    return G1;
			   }
			   if(val.equals("G0")){
				    return G0;
			   }
			   if(val.equals("G2")){
				    return G2;
			   }
			   if(val.equals("G3")){
				    return G3;
			   }
			   if(val.equals("G92")){
				    return G92;
			   }
			   if(val.charAt(0) > 97){ //Check if lowercase
				   return GCDEF.valueOf(val.toUpperCase());   
			   }else{
				   return GCDEF.valueOf(val);
			   }
			   
			   
		   }
		   
		   public static GCDEF getGCDEF(CharSequence val) {
			   if(val.length() == 2 && val.charAt(0) == 'G'){
				   if(val.charAt(1) == '1'){
					    return G1;
				   }
				   if(val.charAt(1) == '0'){
					    return G0;
				   }
				   if(val.charAt(1) == '2'){
					    return G2;
				   }
				   if(val.charAt(1) == '3'){
					    return G3;
				   }
				   if(val.charAt(1) == '4'){
					    return G4;
				   }
			   }
			   if(val.length() ==3 && val.charAt(1) == '9'  && val.charAt(2) == '2'){
				    return G92;
			   }
			   
			   if(val.charAt(0) > 97){ //Check if lowercase
				   return GCDEF.valueOf(val.toString().toUpperCase());   
			   }else{
				   return GCDEF.valueOf(val.toString());
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
	public final static byte Xb = 'X';
	public final static byte Yb = 'Y';
	public final static byte Zb = 'Z';
	public final static byte Eb = 'E';
	public final static byte Fb = 'F';
	
	public final static byte[] newline = new byte[]{newlineb};
	public final static char newlinec = '\n';
	
	
	public final static String RETRACT_LABEL ="R";
	public final static String XYSPEED_LABEL ="XY Speed";
	public final static String AVGXYSPEED_LABEL ="Average Speed";
	public final static String LAYERH_LABEL ="Layer Height";
	public final static String ESPEED_LABEL ="E Speed";
	public final static String ZH_LABEL ="Z Height";
	public final static String ZPOS_LABEL ="Z Position";
	public final static String FAN_LABEL ="Fan";
	public final static String PRINT_LABEL ="Print";
	public final static String PRINTSTART_LABEL ="#";
	public final static String PRICE_LABEL ="Price";
	public final static String SPEEDUP_LABEL ="Speedup";
	public final static String REMTIME_LABEL ="Remaining Time";
	public final static String PRINTTIME_LABEL ="Print Time";
	public final static String LAYER_LABEL ="Layer #";
	public final static String ZERO_LABEL ="0";
	public final static String FANS0_LABEL ="";
	public final static String FANS1_LABEL ="I";
	public final static String FANS2_LABEL ="II";
	public final static String FANS3_LABEL ="III";
	public static final int MAX_EXTRUDER_NR = 4;
	
	public static final int MAX_PROGESS_SLIDER = 1000;
	public static final float MIN_HEATBED_DEGREE=25;
	public static final float MIN_EXTRUDER_DEGREE=45;
	
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
	 * @return number of bytes written to tempbuf
	 */
	public static int formatTimetoHHMMSS(float secs, byte[] buf)
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
		
		int cntnew = cnt;
		while(cntnew < buf.length){
			buf[cntnew++]=0;
		}
	return cnt;
	}
	
	/**
	 * Memory efficient convert of int to char 
	 * @param value
	 * @param result - should be 12 bytes large
	 * @return
	 */
	public static MemoryEfficientString inttoChar(int value, MemoryEfficientLenString result){
		int nr = inttoChar(value, result.getBytes());
		result.setlength(nr); //set length of string		
		return result;
	}
	
	public static void copyTemp(CharSequence src, char[] dest){
		for (int i = 0; i < dest.length; i++) {
			if(i<src.length()){
				dest[i]=src.charAt(i);
			}else{
				dest[i]=0;
			}
			
		}
		dest[src.length()]=176;
		dest[src.length()+1]=67;
	}
	
	/**
	 * Memory efficient convert of float to char
	 * WARNING: does not support the full range 
	 * @param value
	 * @param result - should be 12 bytes large
	 * @return
	 */
	public static MemoryEfficientString floattoChar(float value, MemoryEfficientLenString result, int digits){
		int nr = floattoChar(value, result.getBytes(), digits);
		result.setlength(nr);
		return result;
	}
	
	/**
	 * Memory efficient convert of int to char 
	 * @param value
	 * @param result - should be 12 bytes large
	 * @return length of bytes written to byte[]
	 */
	public static int inttoChar(int value, byte[] result){
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
//           //Clear the remaining bytes
           int cntnew = cnt;
	   		while(cntnew < result.length){
	   			result[cntnew++]=0;
	   		}
           return cnt;
	}
	
	/**
	 * Memory efficient convert of float to char
	 * WARNING Does not support the full float range !! 
	 * @param value
	 * @param result - should be 12 bytes large
	 * @return number of bytes written to byte[]
	 */
	public static int floattoChar(float val, byte[] result, int digits){
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
//         //Clear the remaining bytes
         int cntnew = cnt;
	   		while(cntnew < result.length){
	   			result[cntnew++]=0;
	   		}
           return cnt;
	}
	
	public static void main(String[] args) {
//		MemoryEfficientString me = new MemoryEfficientString(new byte[12]);
//		formatTimetoHHMMSS(393699, me.getBytes());
//		System.out.println(me.toString());
//		floattoChar(1.1f, me.getBytes(),2);
//		System.out.println(me.toString());
//		
//		floattoChar(-0.1f, me.getBytes(),2);
//		System.out.println(me.toString());	
//		
//		floattoChar(0.99f, me.getBytes(),2);
//		System.out.println(me.toString());	
//		inttoChar(Integer.MIN_VALUE, me.getBytes());
//		
//		//Failure case
//		float f = 0.0054454f;
//		System.out.println(parseFloat("0.0054454545", 0));
		//System.out.println(Float.MIN_VALUE);
		ReceiveBuffer rb = new ReceiveBuffer(80);
		rb.put("M107\n".getBytes());
//		rb.put("M107 X\n".getBytes());
		CharSeqBufView[] preallocSegment = {new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView(),new CharSeqBufView()};
		splitbyLetter3(rb,preallocSegment);
		System.out.println("'"+preallocSegment[0].toString()+"'");
		
		Temperature temp = new Temperature();
		rb.put("T:20.00 B:-1.00 @:0".getBytes());
		temp.setTempstring(rb);
		System.out.println(temp.toString());
		System.out.println("FL:"+temp.getBedTempTargetFloat());
		
		rb.put("T:50.1 /0.0 B:16.9 /0.0 T0:205.0 /205.0 T1:50.1 /0.0".getBytes());
		temp.setTempstring(rb);
		System.out.println(temp.toString());
		System.out.println("FL:"+temp.getBedTempTargetFloat());
		
		rb.put("T0: 23.4/-273.1 T1: 21.6/-273.1 T2: 21.6/-273.1 B:23.7 /-273.1".getBytes());
		temp.setTempstring(rb);
		System.out.println(temp.toString());
		System.out.println("FL:"+temp.getBedTempTargetFloat());

		rb.put("ok T:181.2 /0.0 B:48.6 /0.0 T0:10.9 /0.0 @:0 B@:0".getBytes());
		temp.setTempstring(rb);
		System.out.println(temp.toString());
	}
	

	

	public static float round2digits(float num){
		return Math.round((num)*100f)/100f;
	}
	
	public static float round1digits(float num){
		return Math.round((num)*10f)/10f;
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
	
	/**
	 * returns true if it starts with G or M (should be trimmed and uppercase already)
	 * @return boolean gcode
	 */
	public static boolean isValidGCode(ReceiveBuffer codeline){
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
	public static float parseFloat(CharSequence f,int startpos) {
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

	/**
	 * User for splitting the GCodes into segments
	 * @param text
	 * @return
	 */
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
	
	/**
	 * User for splitting the GCodes into segments
	 * NOT THREAD SAVE !!
	 * @param text
	 * @return
	 */
	public static String[] splitbyLetter2(CharSequence text){
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
			}else if (c == 32 || c == 10 || c ==47 || c ==13 || c==9){
				//ignore spaces and newlines
			}else{
				buffer[pos++]=c;
			}
		}
        list.add(new String(buffer,0,pos));
        return list.toArray(new String[list.size()]);
}
	
	/**
	 * User for splitting the GCodes into segments
	 * NOT THREAD SAVE !!
	 * @param text
	 * @return
	 */
	public static int splitbyLetter3(ReceiveBuffer text, CharSeqBufView[] fillArr){
        //list.clear();
		int arrnumber=0;
        int pos = 0;
        int pos2=0;
        int len = text.length();      
        boolean first=true;
        
        
        for (int i = 0; i < len; i++) {
        	char c = text.charAt(i);
			if(c > 58){ //ASCII no number and no whitespace
				if(first){
					first=false;
					pos=i;
					continue;
				}
				//list.add(new String(buffer,0,pos));
				fillArr[arrnumber++].setcontent(text, pos, pos2-pos);
				pos=i;
			}else if (c == 32 || c == 10 || c ==47 || c == 13 || c == 9){
				//ignore spaces and newlines
			}else{
				pos2=i+1;
			}
		}
        fillArr[arrnumber++].setcontent(text, pos, pos2-pos);
        
//        CharSequence[] out = new CharSequence[arrnumber];
//        System.arraycopy(preallocSegment, 0, out, 0, arrnumber);
        return arrnumber;
}
	/**
	 * Find comments and strip them, init the comment filed
	 * @param clv
	 * @return
	 */
	public static String stripComment(String clv) {
		int idx;
		if((idx = clv.indexOf(';')) != -1){
			//is a comment
			clv=clv.substring(0, idx);
		}else if((idx = clv.indexOf("(")) != -1){ //INCLUDES (<
			//is a comment
			clv=clv.substring(0, idx);
		}
		return clv.trim();
	}
	
	/**
	 * Find comments and strip them, init the comment filed
	 * @param clv
	 * @return
	 */
	public static ReceiveBuffer stripComment(ReceiveBuffer clv) {
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
	
	public static Position parseOffset(String text) {
		try{
		String[] item = text.split(":");
		float x = Float.parseFloat(item[0]);
		float y = Float.parseFloat(item[1]);
		return new Position(x,y);
		}catch(Exception e){
		}
		return null;
	}

	/**
	 * Do some tricks to make sure that the saved output file is 100% equal to the input file (if nothing has been changed)
	 * @param prefix
	 * @param val
	 * @param digits
	 * @return
	 */
	public static String getIfInit2(String prefix,float val,int digits){
		if(val==Float.MAX_VALUE) return "";
		if(digits==0){
			String var = String.format(Locale.US," "+prefix+"%.1f", val);
			return removeTrailingZeros(var); //remove trailing zero
		}
		if("E".equals(prefix) && val == 0) return " E0";  //replace E0.0000 with E0 
		return String.format(Locale.US," "+prefix+"%."+digits+"f", val);		
	}
	
	public static int bytetoInt(byte[] b){
    int MASK = 0xFF;
    int result = 0;   
        result = b[0] & MASK;
        result = result + ((b[1] & MASK) << 8);
        result = result + ((b[2] & MASK) << 16);
        result = result + ((b[3] & MASK) << 24);            
    return result;
	}
	/**
	 * Float to string with 3 digits
	 * @param val
	 * @param buffer
	 * @param offset
	 * @return new length of the byte[] 
	 */
	public static int floatToString3(float val, byte[] buffer, int offset){
		tmpformatbuf.clear();		
		tmpformat.format(Locale.US,"%.3f", val);
		//TODO formatter allocate string and char[] ...fix it
		System.arraycopy(tmpformatbuf.array,0,buffer,offset,tmpformatbuf.length());
				
		return offset+tmpformatbuf.length();		
	}
	
	/**
	 * Simple format of float to 2 digit string
	 * @param val
	 * @return
	 */
	public static String floatToString2(float val){
			Formatter form = new Formatter();
			String ret = form.format("%.2f", val).out().toString();
			form.close();
			return ret;
	}
	/**
	 * Float to string with 5 digits
	 * @param prefix
	 * @param val
	 * @param digits
	 * @return
	 */
	public static int floatToString5(float val,byte[] buffer, int offset){
		if(val != 0){
			tmpformatbuf.clear();		
			tmpformat.format(Locale.US,"%.5f", val);
			System.arraycopy(tmpformatbuf.array,0,buffer,offset,tmpformatbuf.length());
			return offset+tmpformatbuf.length();
		}else{ 
			buffer[offset]='0';
			return offset+1;
		}
				
					
	}
	/**
	 * Return true is search string is contained in Array . case sensitive
	 * @param array
	 * @param search
	 * @return
	 */
	public static boolean containsString(String[] array,String search){
		for (int i = 0; i < array.length; i++) {
			if(array[i] != null && array[i].equals(search)) return true;
		}
		return false;
	}
	/**
	 * Float to string with 0 digits
	 * @param prefix
	 * @param val
	 * @param digits
	 * @return
	 */
	public static int floatToString0(float val,byte[] buffer, int offset){
		tmpformatbuf.clear();		
		tmpformat.format(Locale.US,"%d", (int)val);
		System.arraycopy(tmpformatbuf.array,0,buffer,offset,tmpformatbuf.length());
				
		return offset+tmpformatbuf.length();		
	}
	



}

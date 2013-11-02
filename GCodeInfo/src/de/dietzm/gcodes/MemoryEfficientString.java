package de.dietzm.gcodes;

import java.io.UnsupportedEncodingException;

public class MemoryEfficientString implements CharSequence {

		private static final String ENCODING = "ISO-8859-1";
	    private final byte[] data;

	  public MemoryEfficientString(String str) {
	    try {
	      data = str.getBytes(ENCODING);
	    } catch (UnsupportedEncodingException e) {
	      throw new RuntimeException("Unexpected: " + ENCODING + " not supported!");
	    }
	  }
	  
	  public MemoryEfficientString(byte[] data) {
		  this.data = data;
	}
	  
	  public MemoryEfficientString(byte[] data1,byte[] data2) {
		   data = new byte[data1.length+data2.length];
		  System.arraycopy(data1,0,data,0,data1.length);
		  System.arraycopy(data2,0,data,data1.length,data2.length);
		}

	  public char charAt(int index) {
	    if (index >= data.length) {
	      throw new StringIndexOutOfBoundsException("Invalid index " +
	        index + " length " + length());
	    }
	    return (char) (data[index] & 0xff);
	  }

	  public int length() {
	    return data.length;
	  }
	  
	  public MemoryEfficientString subSequence(int start, int end) {
		  if (start < 0 || end > (data.length)) {
		    throw new IllegalArgumentException("Illegal range " +
		      start + "-" + end + " for sequence of length " + length());
		  }
		  byte[] newdata = new byte[end-start];
		  System.arraycopy(data,start,newdata,0,end-start);
		  return new MemoryEfficientString(newdata);
		}
	  
	  /**
	   * Set all bytes to null, starting from idx
	   */
	  public void clear(int idx){
		  int cnt = data.length;
		  for (int i = idx; i < cnt; i++) {
			  data[i]=0;
		}
	  }
	  
	  public MemoryEfficientString subSequence(int start) {
		 return this.subSequence(start, data.length);
		}
	  
	  public byte[] getBytes(){
		  return data;
	  }
	  
	  public String toString() {
		  try {
		    return new String(data, 0, data.length, ENCODING);
		  } catch (UnsupportedEncodingException e) {
		    throw new RuntimeException("Unexpected: " + ENCODING + " not supported");
		  }
		}
	  
	  
	  public static String toString(byte[] data) {
		  try {
		    return new String(data, 0, data.length, ENCODING);
		  } catch (UnsupportedEncodingException e) {
		    throw new RuntimeException("Unexpected: " + ENCODING + " not supported");
		  }
		}
	  	  
}

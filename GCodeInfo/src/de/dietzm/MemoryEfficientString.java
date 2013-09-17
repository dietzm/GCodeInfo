package de.dietzm;

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
	  
	  protected MemoryEfficientString(byte[] data) {
		  this.data = data;
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
	  
	  
}

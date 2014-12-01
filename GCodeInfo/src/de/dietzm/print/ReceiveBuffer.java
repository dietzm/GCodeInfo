package de.dietzm.print;

import java.io.IOException;
import java.nio.ByteBuffer;

import de.dietzm.Constants;
import de.dietzm.gcodes.MemoryEfficientString;

/**
 * ReceiveBuffer 
 * A flexible buffer which provides functions to parse the byte[] content.
 * Avoid creating Strings to save memory
 * @author mdietz
 *
 */
public class ReceiveBuffer implements CharSequence,Appendable {

	@Override
	public Appendable append(char c) throws IOException {
		if(len+1 > array.length) throw new IndexOutOfBoundsException("ReceiveBuffer size exceeded");
		array[len++]=(byte)c;
		return this;
	}

	@Override
	public Appendable append(CharSequence csq) throws IOException {
		if(len+csq.length() > array.length) throw new IndexOutOfBoundsException("ReceiveBuffer size exceeded");
		for (int i = 0; i < csq.length(); i++) {
			array[len++]=(byte)csq.charAt(i);
		}
		return this;
	}

	@Override
	public Appendable append(CharSequence csq, int start, int end) throws IOException {
		if(len+(end-start) > array.length) throw new IndexOutOfBoundsException("ReceiveBuffer size exceeded");
		for (int i = start; i < end; i++) {
			array[len++]=(byte)csq.charAt(i);
		}
		return this;
	}

	public byte[] array ;
	int offset, len;
	boolean timedout =false;
	
	protected boolean isTimedout() {
		return timedout;
	}

	protected void setTimedout(boolean timedout) {
		this.timedout = timedout;
	}

	public ReceiveBuffer(int size){
		array = new byte[size];
		offset=0;
		len=0;
	}
	
	/**
	 * copy the content of the byte buffer up to the current position to offset 0
	 * @param buf
	 */
	public void put(ByteBuffer buf){
		if(buf.position() > array.length) throw new IndexOutOfBoundsException("ReceiveBuffer size exceeded");
		System.arraycopy(buf.array(), 0, array, 0, buf.position());
		len=buf.position();
	}
	
	public void put(byte[] buf){
		if(buf.length > array.length) throw new IndexOutOfBoundsException("ReceiveBuffer size exceeded");
		System.arraycopy(buf, 0, array, 0, buf.length);
		len=buf.length;
	}
	
	public void put(byte[] buf, int offset , int blen){
		if(blen > array.length) throw new IndexOutOfBoundsException("ReceiveBuffer size exceeded, len:"+blen+" off:"+offset);
		System.arraycopy(buf, offset, array, 0, blen);
		len=blen;
	}
	
	public void put(byte[] buf, int offset , int targetoff, int blen){
		if((targetoff+blen) > array.length) throw new IndexOutOfBoundsException("ReceiveBuffer size exceeded, len:"+blen+" off:"+offset);
		System.arraycopy(buf, offset, array, targetoff, blen);
		len=targetoff+blen;
	}
	
	/**
	 * copy the content of the byte buffer from offset 0 up to the current position to the end of this buffer
	 * @param buf
	 */
	public void append(ByteBuffer buf){
		//Log.d("SERIAL","AppendBuf len:"+len+" pos:"+buf.position());
		if(len+buf.position() > array.length) throw new IndexOutOfBoundsException("ReceiveBuffer size exceeded");
		System.arraycopy(buf.array(), 0, array, len, buf.position());
		len=len+buf.position();
	}
	
	/**
	 * clear buffer (set length to 0) 
	 */
	public void clear(){
		len=0;
		timedout=false;
	}
	
	public byte[] array() {
		return array;
	}

	public boolean isEmpty(){
		return len==0;
	}
	
	public boolean endsWithNewLine(){
		if(len==0) return false;
		return array[len-1]==Constants.newlineb;
	}
	
	public boolean startsWithOK(){
		if(len<2) return false;
		return array[0]==111 && array[1]==107; //ASCII
	}
	
	/**
	 * Makibox sends "go xxx" answers 
	 * @return
	 */
	public boolean startsWithGO(){
		if(len<2) return false;
		return array[0]==103 && array[1]==111; //ASCII
	}
	
	
	/**
	 * Smootieware might send "unexpected" responses
	 * e.g.  // action:pause commands.
	 * @return 
	 */
	public boolean startsWithComment(){
		if(len<2) return false;
		return array[0]==47 && array[1]==47; //ASCII
	}
	
	public boolean containsOK(){
		if(len<2) return false;
		for (int i = 0; i < len-1; i++) {
			if(array[i]==111 && array[i+1]==107) return true;
		}
		return false; //ASCII
	}
	
	public int indexOf(char ch){
		if(len<1) return -1;
		for (int i = 0; i < len-1; i++) {
			if(array[i]==ch) return i;
		}
		return -1; //ASCII
	}
	public int indexOf(char ch, int start){
		if(len<1) return -1;
		for (int i = start; i < len-1; i++) {
			if(array[i]==ch) return i;
		}
		return -1; //ASCII
	}
	
	//Repetier firmware sends when send delay was too large ?! 
	public boolean containsWait(){
		if(len<4) return false;
		for (int i = 0; i < len-3; i++) {
			if(array[i]==119 && array[i+1]==97 && array[i+2]==105 && array[i+3]==116) return true;
		}
		return false; //ASCII
	}
	
	//Repetier firmware sends Resend:x when command was invalid
	public boolean containsResend(){
		if(len<6) return false;
		for (int i = 0; i < len-3; i++) {
			if(array[i]=='R' && array[i+1]=='e' && array[i+2]=='s' && array[i+3]=='e' && array[i+4]=='n' && array[i+5]=='d') return true;
		}
		return false; //ASCII
	}
	
	public boolean containsFail(){
		if(len<4) return false;
		for (int i = 0; i < len-3; i++) {
			if(array[i]==102 && array[i+1]==97 && array[i+2]==105 && array[i+3]==108) return true;
		}
		return false; //ASCII
	}
	
	public boolean startsWithString(String start){
		if(len<start.length()) return false;
		for (int i = 0; i < start.length(); i++) {
			if(array[i] != start.charAt(i)){
				return false;
			}
		}
		return true;
	}
	
	
	public boolean startsWithSD(){
		if(len<2) return false;
		return array[0]==83 && array[1]==68;
	}
	
	/**
	 * parse output:
	 * sd printing byte 0/0
	 * 
	 * @return percent complete
	 */
	public int parseSDStatus(){
		if(len<17) return 0;
		//Starts with SD
		int sdtext=17;
		if(startsWithSD()){
			int idx = indexOf('/');
			if(idx == -1) return 0;
			int done;
			int total;
			try {
				done = Integer.parseInt(subSequence(sdtext, idx).toString());
				int idx2 = indexOf('\n',idx+1);
				if(idx2 == -1){
					return 0;
				}
				String remain = subSequence(idx+1,idx2).toString();
				total = Integer.parseInt(remain);
			} catch (NumberFormatException e) {
				return 0; //todo
			}
			if(total == 0 || (total/100) == 0) return 100;
			if(done == 0) return 0;
			return done/(total/100);
		}
		return 0; //ASCII
	}
	
	/**
	 * parse output:
	 * Resend:2
	 * 
	 * @return percent complete
	 */
	public int parseResend(){
		if(len<6) return 0;
		//Starts with resend:
		int ridx = -1;
		for (int i = 0; i < len-3; i++) {
			if(array[i]=='R' && array[i+1]=='e' && array[i+2]=='s' && array[i+3]=='e' && array[i+4]=='n' && array[i+5]=='d'){
				ridx=i;
				break;
			}
		}
		
		if(ridx != -1){
			int idx = indexOf(':',ridx);
			if(idx == -1) return 0;
			int linenr;
			try {
				int idx2 = indexOf('\n',idx+1);
				if(idx2 == -1){
					return 0;
				}
				String remain = subSequence(idx+1,idx2).toString().trim();
				linenr = Integer.parseInt(remain);
			} catch (NumberFormatException e) {
				return 0; //todo
			}
			return linenr;
		}
		return 0; //ASCII
	}
	
	
	public boolean startsWithEcho(){
		if(len<4) return false;
		if(array[0]==101 && array[1]==99 && array[2]==104 && array[3]==111) return true;
		return false; //ASCII
	}
	
	
	/**
	 * Check if response is a plain "ok"
	 * @return
	 */
	public boolean isPlainOK(){
		//Log.d("SERIAL", "IS plainok:"+len+" "+startsWithOK()+endsWithNewLine());
		return len==3 && startsWithOK() && endsWithNewLine();
	}
	
	public String toString(){
		return new String(array,0,len);
	}
	

	/**
	 * Looks for T:
	 * @return
	 */
	public boolean containsTx(){
		if(len<2) return false;
		for (int i = 0; i < len-1; i++) {
			if(array[i]==84 && array[i+1]==58) return true;
		}
		//look for T0:
		if(len<3) return false;
		for (int i = 0; i < len-1; i++) {
			if(array[i]==84 && array[i+1]==48 && array[i+2]==58) return true;
		}
		return false; //ASCII
	}

	@Override
	public char charAt(int index) {
		return (char)array[index];
	}

	@Override
	public int length() {
		return len;
	}
	
	
	/**
	 * Remove space,newline, cr, tab from the end of the buffer
	 * @return length of the buffer after trimRight
	 */
	public int trimRight(){
		while(len != 0){
			char c = (char)array[len-1];
			//remove spaces, newlines, cr, tab 
			if (c == 32 || c == 10 || c == 13 || c == 9){
				len--;
			}else{
				return len;
			}
		}
		return len;
	}
	
	/**
	 * Call setlength if you manipulate the array directly
	 * @param newlen
	 */
	public void setlength(int newlen) {
		len=newlen;
	}


	@Override
	public CharSequence subSequence(int start, int end) {
		  if (start < 0 || end > (len)) {
			    throw new IllegalArgumentException("Illegal range " +
			      start + "-" + end + " for sequence of length " + length());
			  }
			  byte[] newdata = new byte[end-start];
			  System.arraycopy(array,start,newdata,0,end-start);
			  return new MemoryEfficientString(newdata);
	}
	
	public MemoryEfficientString subSequence(int start, int end, MemoryEfficientString str) {
		  if (start < 0 || end > (len)) {
			    throw new IllegalArgumentException("Illegal range " +
			      start + "-" + end + " for sequence of length " + length());
			  }
			  byte[] newdata = str.getBytes();
			  int len = Math.min(end-start, str.length());
			  System.arraycopy(array,start,newdata,0, len);
			  str.clear(len); 
			  return str;
	}
	
	public MemoryEfficientString copyInto(MemoryEfficientString str) {
		  if (len > str.length()) {
			    throw new IllegalArgumentException("Illegal range for sequence of length " + length() +"/"+str.length());
			  }
			  byte[] newdata = str.getBytes();
			  int tgtlen = Math.min(len, str.length());
			  System.arraycopy(array,0,newdata,0, tgtlen);
			  str.clear(tgtlen); 
			  return str;
	}
}

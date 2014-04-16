package de.dietzm.gcodes;

/**
 * Memory efficient string with dedicated length field
 * This allows to use a memory string as a buffer and modify the length
 * 
 * @author mdietz
 *
 */
public class MemoryEfficientLenString extends MemoryEfficientString {
	
	int len = 0;

	public MemoryEfficientLenString(String str) {
		super(str);
		len=data.length;
	}

	public MemoryEfficientLenString(byte[] data) {
		super(data);
		len=data.length;
	}
	
	public MemoryEfficientLenString(byte[] data, int leng) {
		super(data);
		len=leng;
	}

	public MemoryEfficientLenString(byte[] data1, byte[] data2) {
		super(data1, data2);
		len=data.length;
	}
	
	public void setlength(int leng){
		len=leng;
	}
	
	@Override
	public int length() {
		return len;
	}
	
	

}

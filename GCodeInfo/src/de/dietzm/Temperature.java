package de.dietzm;

import de.dietzm.gcodes.MemoryEfficientLenString;
import de.dietzm.gcodes.MemoryEfficientString;
import de.dietzm.print.ReceiveBuffer;

/**
 * Temperature class Hold the printer temperature response and parse it. Avoid
 * any new memory allocation (no regex or string operations allowed)
 * 
 * @author mdietz
 * 
 */
public class Temperature {

	int activeExtruder = 0;
	TempVal bedtmp;
	TempVal[] temps;
	public MemoryEfficientString tempstring = new MemoryEfficientString(new byte[100]);
	public static final float UNINITIALIZED=-99999;

	/**
	 * TempVal internal for holing the temperatures as chars.
	 * @author mdietz
	 */
	class TempVal {
		// Target Temperature 
		MemoryEfficientLenString targettmp = new MemoryEfficientLenString(new byte[10]);
		//Current temperature
		MemoryEfficientLenString tmp = new MemoryEfficientLenString(new byte[10]);
		
		public String toString() {
			return tmp + " |" + targettmp;
		}
		
		protected void setTemp(Temperature temperature, byte[] rb, int start, int stop, int extruder) {
			
			byte[] memstr = tmp.getBytes();
			for (int i = 0; i < memstr.length; i++) {
				if (start + i < stop) {
					memstr[i] = rb[start + i];
				} else {
					memstr[i] = 0;
				}
			}
			tmp.setlength(stop - start);
			// System.out.println("SSSSSS:"+tval.toString());
		}

		protected void setTempTarget(Temperature temperature, byte[] rb, int start, int stop, int extruder) {
			byte[] memstr = targettmp.getBytes();
			for (int i = 0; i < memstr.length; i++) {
				if (start + i < stop) {
					memstr[i] = rb[start + i];
				} else {
					memstr[i] = 0;
				}
			}
			targettmp.setlength(stop - start);
			// System.out.println("SSSSSS:"+tval.toString());
		}
	}

	public Temperature() {
		temps = new TempVal[Constants.MAX_EXTRUDER_NR];
		for (int i = 0; i < temps.length; i++) {
			temps[i] = new TempVal();
		}
		bedtmp = new TempVal();
	}
	
	/**
	 * Set all temps to unititialized
	 */
	private void clear(){
		for (int i = 0; i < temps.length; i++) {
			temps[i].tmp.setlength(0);
			temps[i].targettmp.setlength(0);
		}
		bedtmp.tmp.setlength(0);
		bedtmp.targettmp.setlength(0);
	}

	public int getActiveExtruder() {
		return activeExtruder;
	}

	public CharSequence getBedTemp() {
		return bedtmp.tmp;
	}
	public CharSequence getBedTempTarget() {
		return bedtmp.targettmp;
	}
	
	
	public float getBedTempTargetFloat(){
		if(bedtmp.targettmp.length() == 0) return UNINITIALIZED;
		return Constants.parseFloat(bedtmp.targettmp, 0);
	}
	
	public float getBedTempFloat(){
		if(bedtmp.tmp.length() == 0) return UNINITIALIZED;
		return Constants.parseFloat(bedtmp.tmp, 0);
	}
	
	public float getActiveExtruderTempTargetFloat(){
		if(temps[activeExtruder].targettmp.length() == 0) return UNINITIALIZED;
		return Constants.parseFloat(temps[activeExtruder].targettmp, 0);
	}
	public CharSequence getExtruderTemp(int ext) {
		return temps[ext].tmp;
	}

	public CharSequence getExtruderTempTarget(int ext) {
		return temps[ext].targettmp;
	}

	public MemoryEfficientString getTempstring() {
		return tempstring;
	}

	/**
	 * tricolor : T0: 23.4/-273.1 T1: 21.6/-273.1 T2: 21.6/-273.1 B:23.7 /-273.1
	 * @:0 multec : T:50.1 /0.0 B:16.9 /0.0 T0:205.0 /205.0 T1:50.1 /0.0
	 * smoothie : T:128.9 /190.0 @255 B:32.0 /50.0 @255 brahma3 : T:36.3 /0.0
	 * B:31.7 /0.0 T0:36.3 /0.0 @:0.00W B@:0 repetier : T:20.00 B:-1.00 @:0
	 * dummy : ok T:181.2 /0.0 B:48.6 /0.0 T0:10.9 /0.0 @:0 B@:0
	 * 
	 * heating up: T:197.0 /205.0 @0 heating repetier: T:24.44 B:-1.00 @:0
	 * 
	 * regex: (B|T(\d*)):\s*(%s)(\s*\/?\s*(%s))? cannot use regex because of
	 * garbage collection
	 * 
	 * @param recv
	 */
	private void parseTemperature() {
		try {
			byte[] res = tempstring.getBytes();
			int i = 0;
			int extruderNr = activeExtruder;
			int startidx, stopidx;
			while (i < res.length - 1) {
				if (res[i] == 'T' || res[i] == 'B') {
					if (res[i] == 'T') {
						i++;
						for (int j = 0; j < Constants.MAX_EXTRUDER_NR; j++) {
							if (res[i] == (char) j + 48) {
								extruderNr = j;
								i++;
								break;
							}
						}
					} else {
						i++;
						extruderNr = -1;
					}

					if (res[i] == ':') {
						i++;
						while (res[i] == 32) { // skip spaces
							i++;
						}
						startidx = i;
						while ( i < (tempstring.length()-1) && res[i] != 0 && res[i] != 32 && res[i] != 47 && res[i]!= 64 && res[i]!=45 && res[i]!=67 ) { // next
							// space or / or @ or - or C
							i++;
						}
						stopidx = i;
						if (res[i] == 32)
							i++;
						if (extruderNr == -1) {
							bedtmp.setTemp(this,res, startidx, stopidx, extruderNr);
						} else {
							temps[extruderNr].setTemp(this,res, startidx, stopidx, extruderNr);
						}

						if (res[i] == 47) {
							i++;
							startidx = i;
							while (i < (tempstring.length()-1) && res[i] != 0 && res[i] != 32 && res[i] != 47 && res[i] != 0  && res[i]!= 64 && res[i]!=67 ) { // next
								// space or /
								i++;
							}
							stopidx = i;
							
							if (extruderNr== -1) {
								bedtmp.setTempTarget(this,res, startidx, stopidx, extruderNr);
							} else {
								temps[extruderNr].setTempTarget(this,res, startidx, stopidx, extruderNr);
							}
						}

						continue;
					} else {
						//System.out.println("WRONG FORMAT");
						// Wrong format , no colon after Tx
					}
				}
				i++;
			}
		} catch (Exception e) {
			// cons.appendText("Error parsing temperature: "+recv.toString());
			e.printStackTrace();
		}

	}

	public void setActiveExtruder(int active) {
		activeExtruder = active;
	}

	public void setTempstring(ReceiveBuffer recv) {
		clear();
		recv.copyInto(tempstring); // Copy buffer, no new allocation		
		parseTemperature();
	}

	public String toString() {
		String out = "";

		for (int i = 0; i < temps.length; i++) {
			out += "T"+i+": "+temps[i].toString();
			out += "\n";
		}
		out += "B: "+bedtmp.toString();
		return out;
	}

	public static void main(String[] args) {
		float min = 25;
		float max = 90;
		float maxprogress =1000;
		float heat = 25f;
		float bheat = 0;
			
		float gradperstep = (max-min)/(maxprogress-5);		
		float progress = (heat-min)/gradperstep+5f;	
		System.out.println("Progress="+progress+ "   "+(int)progress);
				
		bheat = (((int)progress-5)*gradperstep+min);
		System.out.println("Heat:"+bheat+"   "+Constants.round2digits(bheat));
		
		
		System.out.println("------------------------ H=60");
		
		progress = (((heat-min)/((max-min)/max))+5f);
		System.out.println("Progress="+progress+ "   "+(int)progress);
		
		bheat = ((progress-5)*((max-min)/max)+min);
		System.out.println("Heat:"+bheat);
		
				
		bheat = (((int)progress-5)*((max-min)/max)+min);
		System.out.println("Rounded Heat:"+bheat+"   "+(int)bheat);
		ReceiveBuffer rb = new ReceiveBuffer(200);
		Temperature temp = new Temperature();
		//rb.put("T:22.0 /0.0 B:0.5 /0.0 @0 B@:0".getBytes());
		//rb.put("T:199.9 /200.0 @19 B:53.0 /50.0 @0 HC:27.0 /0.0 @0 ".getBytes());
		rb.put("ok T:21.3 /0.0 B:82.1 /0.0 T0:21.3 /0.0 @:0 B@:0    ADC B:82.1C->697  T0:21.3C->983".getBytes());
		
		//rb.put("T:20.00 E:00 B:40.01@".getBytes());
		
		temp.setTempstring(rb);
		System.out.println(temp.toString());
		System.out.println("FL:"+temp.getBedTempTargetFloat());
		
	}
	
}

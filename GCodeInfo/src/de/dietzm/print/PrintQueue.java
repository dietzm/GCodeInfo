package de.dietzm.print;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import de.dietzm.Model;
import de.dietzm.gcodes.GCode;

/**
 * Print Queue
 * Blocking for automated printing to ensure that only one at a time is send and non-blocking for manual operations 
 * @author mdietz
 *
 */
public class PrintQueue  {
	
	//public static final int MAX_AUTO_CONCURRENT=1;
	public static final int MAX_MANUAL_CONCURRENT=2000;
	public Model printModel = null;
	private float remainingtime=0;

	//private boolean clear =false;
	private LinkedBlockingQueue<GCode> aprintQ = new LinkedBlockingQueue<GCode>();
	private LinkedBlockingQueue<GCode> mprintQ = new LinkedBlockingQueue<GCode>(MAX_MANUAL_CONCURRENT);

	public Model getPrintModel() {
		return printModel;
	}

	public void put(GCode code)throws InterruptedException{
		mprintQ.put(code);			
	}
	
	public void putAuto(GCode code)throws InterruptedException{
		aprintQ.put(code);	
		remainingtime+=code.getTimeAccel();
	}
	
	
	public void addModel(Model code) throws InterruptedException{
		printModel=code;
		for (GCode gc : code.getGcodes()) {
			if(gc.isPrintable()){
				aprintQ.add(gc);
			}
		}
		remainingtime=printModel.getTimeaccel();	
	}
	
	
	public GCode pollManual(int timeout_sec) throws InterruptedException {
		GCode gc = mprintQ.poll(timeout_sec,TimeUnit.SECONDS);
		return gc;
	}
	
	public int getSizeAuto(){
		return aprintQ.size();
	}
	
	public int getSizeManual(){
		return mprintQ.size();
	}
	
	/**
	 * is the manual queue empty ?
	 * @return
	 */
	public boolean isManualEmpty(){
		return mprintQ.isEmpty();
	}

	public GCode pollAuto() throws InterruptedException {
		if(!mprintQ.isEmpty()) return pollManual(1000);
		GCode gc = aprintQ.poll(2,TimeUnit.SECONDS);
		if(gc!=null){
			remainingtime-=gc.getTimeAccel();
			}
		return gc;
	}


	public void clear(){
		//clear=true;
		//notify(); //notify to ensure the addAUtoOPs is interrupted
		aprintQ.clear();
		mprintQ.clear();
		printModel=null;
		remainingtime=0;
	}

	public float getRemainingtime() {
		return remainingtime;
	}
	
	public int getPercentCompleted() {
		if ((printModel.getGcodecount()/100) == 0) return 100;
		if (aprintQ.size() == 0) return 100;
		//System.out.println("Gcode count:"+printModel.getGcodecount());
	//	int perc = (int) (100 -( remainingtime / (printModel.getTimeaccel() /100)));
		int perc = (int) (100 -( aprintQ.size() / (printModel.getGcodecount() /100)));
		return perc;
	}
}

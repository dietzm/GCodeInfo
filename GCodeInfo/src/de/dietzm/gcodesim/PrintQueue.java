package de.dietzm.gcodesim;

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
	//private boolean clear =false;
	private LinkedBlockingQueue<GCode> aprintQ = new LinkedBlockingQueue<GCode>();
	private LinkedBlockingQueue<GCode> mprintQ = new LinkedBlockingQueue<GCode>(MAX_MANUAL_CONCURRENT);
	
	public void put(GCode code)throws InterruptedException{
		mprintQ.put(code);			
	}
	
	public void putAuto(GCode code)throws InterruptedException{
		aprintQ.put(code);			
	}
	
	
	public void addModel(Model code) throws InterruptedException{
		for (GCode gc : code.getGcodes()) {
			if(gc.isPrintable()){
				aprintQ.add(gc);
			}
		}
	
	}
	
	
	public GCode pollManual(int timeout_sec) throws InterruptedException {
		GCode gc = mprintQ.poll(timeout_sec,TimeUnit.SECONDS);
		synchronized(this){
			notify();
		}
		return gc;
	}
	
	/**
	 * is the manual queue empty ?
	 * @return
	 */
	public boolean isManualEmpty(){
		return mprintQ.isEmpty();
	}

	public GCode pollAuto() throws InterruptedException {
		GCode gc = aprintQ.poll();
		return gc;
	}


	public void clear(){
		//clear=true;
		//notify(); //notify to ensure the addAUtoOPs is interrupted
		aprintQ.clear();
		mprintQ.clear();
	}
}

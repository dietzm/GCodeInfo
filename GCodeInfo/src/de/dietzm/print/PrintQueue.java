package de.dietzm.print;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import de.dietzm.Model;
import de.dietzm.gcodes.GCode;
import de.dietzm.gcodes.GCodeStore;

/**
 * Print Queue
 * Blocking for automated printing to ensure that only one at a time is send and non-blocking for manual operations 
 * @author mdietz
 *
 */
public class PrintQueue implements Runnable {
	
	public Model printModel = null;
	private float remainingtime=0;
	Thread addmodelth = null;

	//private boolean clear =false;
	private LinkedBlockingQueue<GCode> aprintQ = new LinkedBlockingQueue<GCode>();
	private LinkedBlockingQueue<GCode> mprintQ = null;
	
	//recover queue - Used to return to the old coords after pause (e.g. filament change)
	private LinkedBlockingQueue<GCode> rprintQ = new LinkedBlockingQueue<GCode>(5);;
	GCode[] postgc = null;
	
	public PrintQueue(int max_manual){
		 mprintQ = new LinkedBlockingQueue<GCode>(max_manual);
	}
	
	public Model getPrintModel() {
		return printModel;
	}

	public void put(GCode code)throws InterruptedException{
		mprintQ.put(code);			
	}
	
	public void putRecover(GCode code)throws InterruptedException{
		rprintQ.put(code);			
	}
	
	public void putAuto(GCode code)throws InterruptedException{
		aprintQ.put(code);	
		remainingtime+=code.getTimeAccel();
	}
	
	public void addModel(Model code) throws InterruptedException{
		printModel=code;
		addmodelth = new Thread(this);
		addmodelth.start();
	}
	

	
	public void addModel(Model code,GCode ... postcodes  ) throws InterruptedException{
		printModel=code;
		postgc=postcodes;
		addmodelth = new Thread(this);
		addmodelth.start();
	}
	
	
	
	private synchronized void addModeltoQueue() {	
		GCodeStore gcstore = printModel.getGcodes();
		int size = gcstore.size();
		for (int ig = 0; ig < size; ig++) {
			GCode gc = gcstore.get(ig);
			if(Thread.currentThread().isInterrupted()) return;
			//if(gc.isPrintable()){
			//Add all gcodes (even if not printable) to keep line numbers correct
				aprintQ.add(gc);
			//}
		}
		if(postgc!= null){
			for (int i = 0; i < postgc.length; i++) {
				aprintQ.add(postgc[i]);
			}
		}
		remainingtime=printModel.getTimeaccel();	
	}
	
	
	public GCode pollManual(int timeout_sec) throws InterruptedException {
		GCode gc = mprintQ.poll(timeout_sec,TimeUnit.SECONDS);
		return gc;
	}
	
	public GCode pollRecover(int timeout_sec) throws InterruptedException {
		GCode gc = rprintQ.poll(timeout_sec,TimeUnit.SECONDS);
		return gc;
	}
	
	public int getSizeAuto(){
		return aprintQ.size();
	}
	
	public int getSizeManual(){
		return mprintQ.size();
	}
	
	public int getSizeRecover(){
		return rprintQ.size();
	}
	
	/**
	 * is the manual queue empty ?
	 * @return
	 */
	public boolean isManualEmpty(){
		return mprintQ.isEmpty();
	}
	
	/**
	 * is the recover queue empty ?
	 * @return
	 */
	public boolean isRecoverEmpty(){
		return rprintQ.isEmpty();
	}

	public GCode pollAuto() throws InterruptedException {
		if(!mprintQ.isEmpty()) return pollManual(1000);
		GCode gc = null;
		int i = 0;
		do{
			gc = aprintQ.poll(2,TimeUnit.SECONDS);
			if(gc!=null){
				remainingtime-=gc.getTimeAccel();
				return gc;
			}
			i++;
			System.out.println("Still filling printqueue, retry");
		}while(isFillingQueue() && i < 5); //repeat 5x if still filling the queue
		return gc;
	}


	public void clear(){
		//clear=true;
		//notify(); //notify to ensure the addAUtoOPs is interrupted
		if(isFillingQueue()){
			addmodelth.interrupt();
		}
		aprintQ.clear();
		//Do not clear manual print queue because it is needed for onFinish gcodes
		//mprintQ.clear();
		rprintQ.clear();
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
	
	/**
	 * Filling thread, put model into queue
	 */
	public void run(){
		try {
			addModeltoQueue();
			System.out.println("Model added. Finish filling thread. Intr:"+addmodelth.isInterrupted());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//finish thread
	}
	
	/**
	 * Get the status of the filling thread
	 * @return true if model is right now added to the queue 
	 */
	public boolean isFillingQueue(){
		if(addmodelth != null && addmodelth.isAlive()) return true;
		return false;
	}
}

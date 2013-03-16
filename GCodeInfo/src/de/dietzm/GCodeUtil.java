package de.dietzm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class GCodeUtil {

	/**
	 * 0.91 Acceleration added
	 * 0.92 fixed zlift
	 * 0.93 printbed position added
	 * 0.94 Java 1.6 compatible 
	 * 0.95 Fixed average values (height,temp) , support skeinforge comments , guess diameter, show weight and price
	 * 0.96 use sorted map instead of sorting each time, Nice looking labels for current infos,  pageing for layer details, about/help dialog
	 * 0.97 Use SpeedEntry, show speedtype travel/print, show layers for speed
	 * 
	 */
	public static final String VERSION ="0.97";

	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		if(args.length < 2 || !new File(args[1]).exists()){
			printUsageandExit();
		}
		String mode = args[0];
		try {
			long start = System.currentTimeMillis();
			Model model = new Model(args[1]);
			model.loadModel();
			long load = System.currentTimeMillis();
			model.analyze();
			
			if(mode.contains("e")){ //edit
				/**
				 * Allow Modifications:
				 * Change layer height (recalculate E) 
				 * Change Speeds (by percent)
				 * Change Temp
				 * Remove Layers (if above layer is similar)
				 * Clone Layers 
				 * Insert a pause / replace filament
				 * Add/remove Offset X/Y/Z ?
				 * Scale   
				 */

//				//Change speed
//				ArrayList<GCode> gcodes = model.getLayer(7).getGcodes();
//				for (GCode gCode : gcodes) {
//					gCode.changeSpeed(0);
//				}
//				//Change temp
//				for (GCode gCode : gcodes) {
//					if(gCode.getS_Ext() != GCode.UNINITIALIZED){
//						gCode.changeTemp(200f, 65f);
//						//update temps, but always add a temp at the beginning of the layer
//						//TODO: if a temp definitions exists before G1/G2/G3 , do not insert a new one
//					}
//					
//				}
//				int idx = gcodes.get(0).getLineindex();
//				System.out.println(gcodes.get(0)+" "+idx+"  "+model.getGcodes().get(idx-1).getLineindex());
//				model.getGcodes().add(idx,new GCode("M104 S200", 15751) );
//				
//				model.saveModel("testfile.gcode");
			}
			if(mode.contains("m")){
				printModelDetails(model);
			}
			if(mode.contains("l")){
				printLayerSummary(model);
			}
			if (mode.contains("s")){
				printModelSpeedDetails(model);
			}			
			if(mode.matches(".*[pn].*")){
				printLayerDetails(model,mode);
			}
			if(mode.contains("c")){
				printComments(model);
			}
			//DEBUG
			if(mode.contains("g")){
				printGCodeDetails(model, mode);
			}
		

			long end = System.currentTimeMillis();
			System.out.println("Gcode Analyse Time: "+ GCode.formatTimetoHHMMSS((end-start)/1000f) +" Load time:"+GCode.formatTimetoHHMMSS((load-start)/1000f));
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static void printUsageandExit() {
		String name ="GcodeUtil";
		System.err.println(name+" "+VERSION+"\nWrong Parameters or gcode file does not exist.");
		System.err.println("Usage: "+name+" [mode m|p|n] gcodefile [");
		System.err.println("Modes:");
		System.err.println("\tm = Show Model Info");
		System.err.println("\tl = Show Layer Summary");
		System.err.println("\tp = Show Printed Layer Detail Info");
		System.err.println("\tn = Show Non-Printed Layer Detail Info");
		System.err.println("\ts = Show Printing Speed Details Info");
		System.err.println("\tc = Show embedded comments (e.g. from slicer)");
		
		System.err.println("\nExample: \nShow Model Info and Printed Layers");
		System.err.println("\t "+name+" mp /tmp/object.gcode ");
		System.err.println("\nShow All Info");
		System.err.println("\t "+name+" mlpnsc /tmp/object.gcode ");
		
		System.exit(1);
	}
	
	private static void printComments(Model model){
		System.out.println("***************************************************************************");
		System.out.println("****************************** Embedded Comments **************************");
		System.out.println("***************************************************************************");
		
		System.out.println(model.getModelComments());
		
	}

	private static void printModelDetails(Model model) {
		System.out.println("***************************************************************************");
		System.out.println("****************************** Model Details ******************************");
		System.out.println("***************************************************************************");
		
		System.out.println(model.getModelDetailReport()+model.guessPrice(model.guessDiameter()));
		

	}
	
	private static void printModelSpeedDetails(Model model){
			System.out.println(model.getModelSpeedReport());
	}
	
	private static void printLayerSummary(Model model){
		/**
		 * Print Layer details
		 */
		System.out.println(model.getModelLayerSummaryReport());
	}

	private static void printLayerDetails(Model model, String mode) {

		System.out.println("***************************************************************************");
		System.out.println("****************************** Layer Details ******************************");
		System.out.println("***************************************************************************");
		
		/**
		 * Print Layer details
		 */
		ArrayList<Layer> layers = new ArrayList<Layer>(model.getLayer().values());
		//Collections.sort(layers);
		
		for (Iterator<Layer> iterator = layers.iterator(); iterator.hasNext();) {
			Layer lay = iterator.next();
			float layperc =  GCode.round2digits(lay.getTime()/(model.getTime()/100));
			if(lay.isPrinted() && mode.contains("p")){
				System.out.println("--------------------------------------------------");
					System.out.println(lay.getLayerDetailReport()+"\n Percent of time:"+layperc+"%");					
				if(mode.contains("s")){
					System.out.println(lay.getLayerSpeedReport());
				}
			}else if(!lay.isPrinted() && mode.contains("n")){
				System.out.println("--------------------------------------------------");
				System.out.println(lay.getLayerDetailReport()+"\n Percent of time:"+layperc+"%");
				if(mode.contains("s")){
					System.out.println(lay.getLayerSpeedReport());
				}
			}
		}
	}
	
	private static void printGCodeDetails(Model model, String mode) {

		System.out.println("***************************************************************************");
		System.out.println("****************************** GCODE Details ******************************");
		System.out.println("***************************************************************************");
		
		/**
		 * Print Layer details
		 */
		ArrayList<Layer> layers = new ArrayList<Layer>(model.getLayer().values());
		//Collections.sort(layers);		
		for (Iterator<Layer> iterator = layers.iterator(); iterator.hasNext();) {
			Layer lay = iterator.next();
			float layperc =  GCode.round2digits(lay.getTime()/(model.getTime()/100));
			if(lay.isPrinted()){
				System.out.println("--------------------------------------------------");
					System.out.println(lay.getLayerDetailReport()+"\n Percent of time:"+layperc+"%");
					for (GCode gc : lay.getGcodes()) {
						System.out.println("\t"+gc.toString());
					}
				
			}
		}
	}

}

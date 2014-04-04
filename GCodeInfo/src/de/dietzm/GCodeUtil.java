package de.dietzm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import de.dietzm.gcodes.GCode;

public class GCodeUtil {

	/**
	 * 0.91 Acceleration added
	 * 0.92 fixed zlift
	 * 0.93 printbed position added
	 * 0.94 Java 1.6 compatible 
	 * 0.95 Fixed average values (height,temp) , support skeinforge comments , guess diameter, show weight and price
	 * 0.96 use sorted map instead of sorting each time, Nice looking labels for current infos,  pageing for layer details, about/help dialog
	 * 0.97 Use SpeedEntry, show speedtype travel/print, show layers for speed
	 * 0.98 Add edit function to modify gcodes (experimental)
	 * 0.99 CSV export 
	 */
	public static final String VERSION ="0.99";

	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		if(args.length < 2 ){
			printUsageandExit();
		}
		String mode = args[0];
		String filename = args[1];
		if(mode.startsWith("e")){
			//edit mode
			if(args.length < 4 ){
				printUsageandExit();
			}
			filename = args[3];
		}
		
		if ( !new File(filename).exists()){
			printUsageandExit();
		}
			
		try {
			long start = System.currentTimeMillis();
			Model model = new Model(filename);
			model.loadModel();
			long load = System.currentTimeMillis();
			model.analyze();
			
			if(mode.startsWith("e")){ //edit
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
				String option = args[1];
				String layersarg = args[2];	
				Collection<Layer> lays = parseLayerArgument(model, layersarg);
				
				if(option.contains("offset=") && !layersarg.equalsIgnoreCase("all")){
					System.err.println("Offset change can only be done if all layers are choosen");
					System.exit(1);
				}
				editLayer(model, option, lays);

//				int idx = gcodes.get(0).getLineindex();
//				System.out.println(gcodes.get(0)+" "+idx+"  "+model.getGcodes().get(idx-1).getLineindex());
//				model.getGcodes().add(idx,new GCode("M104 S200", 15751) );
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
		
			//DEBUG
			if(mode.contains("x")){
				printGCodeDetailsCSV(model, mode);
			}

			long end = System.currentTimeMillis();
			System.out.println("Gcode Analyse Time: "+ Constants.formatTimetoHHMMSS((end-start)/1000f,(StringBuilder)null) +" Load time:"+Constants.formatTimetoHHMMSS((load-start)/1000f,(StringBuilder)null));
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static void editLayer(Model model, String option, Collection<Layer> lays)	throws IOException {
		
		if(option.startsWith("speed=")){
			int value = Integer.parseInt(option.substring(6));
			model.changeSpeed(lays, value);

		}else if(option.startsWith("extr=")){
			int value = Integer.parseInt(option.substring(5));
			model.changeExtrusion(lays, value);
		}else if(option.startsWith("exttemp=")){
			float value = Float.parseFloat(option.substring(8));
			model.changeExtTemp(lays, value);
		}else if(option.startsWith("bedtemp=")){
			float value = Float.parseFloat(option.substring(8));
			model.changeBedTemp(lays, value);
		}else if(option.startsWith("layerh=")){
			int value = Integer.parseInt(option.substring(7));
			model.changeLayerHeight(lays, value);
		}else if(option.startsWith("zoffset=")){
			float value = Float.parseFloat(option.substring(8));
			model.changeZOffset(lays, value);
		}else if(option.startsWith("yoffset=")){
			float value = Float.parseFloat(option.substring(8));
			model.changeYOffset(lays, value);
		}else if(option.startsWith("xoffset=")){
			float value = Float.parseFloat(option.substring(8));
			model.changeXOffset(lays, value);
		}else if(option.startsWith("fan=")){
			int value = Integer.parseInt(option.substring(4));
			System.out.println("Change Fan to "+value);
			model.changeFan(lays, value);
		}else if(option.startsWith("delete")){
			System.out.println("Delete Layers ");
			model.deleteLayer(lays);
		}else{
			printUsageandExit();
		}
		System.out.println("Saving to file "+model.getFilename()+"-new");
		model.saveModel(model.getFilename()+"-new");
		System.exit(0);
	}

	private static Collection<Layer> parseLayerArgument(Model model, String layersarg) {
		Collection<Layer> lays;
		if(layersarg.equalsIgnoreCase("all")){
			lays=model.getLayer();
		}else{
			String[] layersarg1 = layersarg.split(",");
			lays= new ArrayList<Layer>();
			for (Layer lay1 : model.getLayer()) {
				for (String lan : layersarg1) {
					if(lan.equals(String.valueOf(lay1.getNumber()))){
						lays.add(lay1);
					}
				}				
		
			}
		}
		
		if(lays.isEmpty()){
			System.err.println("No matching layers found.");
			System.exit(1);
		}
		return lays;
	}

	private static void printUsageandExit() {
		String name ="GcodeUtil";
		System.err.println(name+" "+VERSION+"\nWrong Parameters or gcode file does not exist.");
		System.err.println("Usage: "+name+" [mode m|l|p|n] gcodefile ");
		System.err.println("Modes:");
		System.err.println("\tm = Show Model Info");
		System.err.println("\tl = Show Layer Summary");
		System.err.println("\tp = Show Printed Layer Detail Info");
		System.err.println("\tn = Show Non-Printed Layer Detail Info");
		System.err.println("\ts = Show Printing Speed Details Info");
		System.err.println("\tc = Show embedded comments (e.g. from slicer)");
		System.err.println("\tx = Print Gcode details as CSV output");
		
		System.err.println("Edit Mode Usage: "+name+" [editmode e] [option] [layers]  gcodefile ");
		System.err.println("\te speed=-10 = Reduce Speed by 10 percent");
		System.err.println("\te extr=10 = Increase extrusion rate by 10 percent");
		System.err.println("\te layerh=10 = Increase Layerheight by 10 percent (+increase extrusion)");
		System.err.println("\te exttemp=170.3 = Set extruder temperatur to 170.3 (only update existing gcodes)");
		System.err.println("\te bedtemp=50.3 = Set bed temperatur to 50.3 (only update existing gcodes)");
		System.err.println("\te zoffset=0.1 = Add Offset to Z position (requires layer option 'all'");
		System.err.println("\te xoffset=0.1 = Add Offset to X position (requires layer option 'all'");
		System.err.println("\te yoffset=0.1 = Add Offset to Y position (requires layer option 'all'");
		System.err.println("\te fan=255 = Set Fan 0=off, 255=full (only update existing gcodes)");
		System.err.println("\te delete = Delete the specified layers");
		System.err.println("[layers] = comma separated list of layers or 'all' for all ");
		
		
		System.err.println("\nExample: \nShow Model Info and Printed Layers");
		System.err.println("\t "+name+" mp /tmp/object.gcode ");
		System.err.println("\nShow All Info");
		System.err.println("\t "+name+" mlpnsc /tmp/object.gcode ");
		
		System.err.println("\nEdit Model, increase 1-3 layer speed by 25%");
		System.err.println("\t "+name+" e speed=25 1,2,3 /tmp/object.gcode ");
		System.err.println("\nEdit Model, disable fan for all layers");
		System.err.println("\t "+name+" e fan=0 all /tmp/object.gcode ");
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
		ArrayList<Layer> layers = new ArrayList<Layer>(model.getLayer());
		//Collections.sort(layers);
		
		for (Iterator<Layer> iterator = layers.iterator(); iterator.hasNext();) {
			Layer lay = iterator.next();
			float layperc =  Constants.round2digits(lay.getTime()/(model.getTime()/100));
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
		ArrayList<Layer> layers = new ArrayList<Layer>(model.getLayer());
		//Collections.sort(layers);		
		for (Iterator<Layer> iterator = layers.iterator(); iterator.hasNext();) {
			Layer lay = iterator.next();
			float layperc =  Constants.round2digits(lay.getTime()/(model.getTime()/100));
			if(lay.isPrinted()){
				System.out.println("--------------------------------------------------");
					System.out.println(lay.getLayerDetailReport()+"\n Percent of time:"+layperc+"%");
					for (GCode gc : model.getGcodes(lay)) {
						System.out.println("\t"+gc.toString());
					}
				
			}
		}
	}
	
	private static void printGCodeDetailsCSV(Model model, String mode) {

		System.out.println("***************************************************************************");
		System.out.println("****************************** GCODE Details ******************************");
		System.out.println("***************************************************************************");
		
		/**
		 * Print Layer details
		 */
		ArrayList<Layer> layers = new ArrayList<Layer>(model.getLayer());
		//Collections.sort(layers);		
		System.out.println("Layer;Speed;Extrusion;Distance;Time;Fanspeed");
		for (Iterator<Layer> iterator = layers.iterator(); iterator.hasNext();) {
			Layer lay = iterator.next();
			//float layperc =  GCode.round2digits(lay.getTime()/(model.getTime()/100));
				//System.out.println("--------------------------------------------------");
					//System.out.println(lay.getLayerDetailReport()+"\n Percent of time:"+layperc+"%");
					for (GCode gc : model.getGcodes(lay)) {
						System.out.println(lay.getNumber()+";"+gc.toCSV());
					}
				
			
		}
	}

}

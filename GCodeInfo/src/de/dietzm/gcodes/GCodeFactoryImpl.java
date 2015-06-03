package de.dietzm.gcodes;

import java.io.IOException;
import java.io.InputStream;


public interface GCodeFactoryImpl {

	public abstract GCodeStore loadGcodeModel(InputStream in, long fsize) throws IOException;

	public abstract GCodeStore createStore(int size);
	
	public abstract long getReadBytes();
	public abstract long getReadLines();	
	public abstract long getFilesize();
	
	public GCode parseGcode(String arg0,int linenr) throws Exception;

}
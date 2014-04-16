package de.dietzm.gcodes;

import java.util.ArrayList;
import java.util.Iterator;

public class GCodeStore implements Iterable<GCode> {

	private final ArrayList<GCode> list;
	
	public GCodeStore(int size) {
		 list = new ArrayList<GCode>(size);
	}
	public GCodeStore() {
		 list = new ArrayList<GCode>();
	}

	public boolean add(GCode gc){
		return list.add(gc);
	}
	
	public int size(){
		return list.size(); 
	}
	
	public GCode get(int idx){
		return list.get(idx);
	}
	
	public void clear(){
		list.clear();
	}

	@Override
	public Iterator<GCode> iterator() {
		return list.iterator();
	}
	
	public void commit(){
	
	}
}

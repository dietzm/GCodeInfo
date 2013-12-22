package de.dietzm.print;

public interface ConsoleIf {
	public void appendText(CharSequence ... txt);
	public void appendTextNoCR(CharSequence ... txt);
	public void setTemp(CharSequence temp);
	public void clearConsole();	
	public int chooseDialog(final String[] items,final String[] values);
	public void setWakeLock(boolean active);
	public void setPrinting(boolean printing);
	public void log(String tag, String value);
	public boolean hasWakeLock();
	public void updateState(String statemsg,int progressPercent);
}

package de.dietzm.gcodes;
import de.dietzm.Constants;
import de.dietzm.Position;
import de.dietzm.Constants.GCDEF;

@Deprecated
/**
 * deprecated full featured gcode class. 
 * Use GCodeMemSave instead
 * @author mdietz
 *
 */
public class GCodeFull extends GCodeAbstract {
	
    private byte[] data; //Store String in a more memory efficient way
	private short gcode;
	private int lineindex;
	private short commentidx=-1;
	
		
	//Parsed values, not changed by analyse 
	private float e=Float.MAX_VALUE;
	private float f=Float.MAX_VALUE; //Speed
	private float x=Float.MAX_VALUE;
	private float y=Float.MAX_VALUE;
	private float z=Float.MAX_VALUE;
	//Store additional values in separate class to avoid memory consumption if not needed
	private Extended ext =null;
	private class Extended{ 
		private float ix=Float.MAX_VALUE;
		private float jy=Float.MAX_VALUE;
		private float kz=Float.MAX_VALUE;
		private float r=Float.MAX_VALUE;	
		private float s_ext=Float.MAX_VALUE;
		private float s_bed=Float.MAX_VALUE;
		private float s_fan=Float.MAX_VALUE;
		private String unit = null; //default is mm
	}
	
	//Dynamic values updated by analyse	
	private float time;
	private float timeaccel; //track acceleration as extra time 
	private float distance;
	private float extrusion;
	private short fanspeed; //remember with less accuracy (just for display)
	private float curX,curY;
	
	//private float extemp,bedtemp;	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getFanspeed()
	 */
	@Override
	public short getFanspeed() {
		return fanspeed;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#setFanspeed(float)
	 */
	@Override
	public void setFanspeed(float fanspeed) {
		this.fanspeed = (short)fanspeed;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getExtemp()
	 */
	@Override
	public float getExtemp() {
		if(!isInitialized(Constants.SE_MASK)) return -255;
		return ext.s_ext;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getBedtemp()
	 */
	@Override
	public float getBedtemp() {
		if(!isInitialized(Constants.SB_MASK)) return -255;
		return ext.s_bed;
	}


	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getCodeline()
	 */
	@Override
	public MemoryEfficientString getCodeline() {
		return new MemoryEfficientString(data);
	}
	

	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getCodelineToPrint()
	 */
	public MemoryEfficientString getCodelineToPrint(){
		if(commentidx != -1){
			return subSequence(0, commentidx);
		}
		return new MemoryEfficientString(data);
	}

	
	

	
	public GCodeFull(String line,int linenr,GCDEF gc){
		super(line,linenr,gc);
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getS_Bed()
	 */
	@Override
	public float getS_Bed() {
		return ext.s_bed;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getComment()
	 */
	@Override
	public String getComment() {
		return subSequence(commentidx,data.length).toString();
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getCurrentPosition(de.dietzm.Position)
	 */
	@Override
	public Position getCurrentPosition(Position pos) {
		pos.x=curX;
		pos.y=curY;
		return pos;
	}
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getDistance()
	 */
	@Override
	public float getDistance() {
		return distance;
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getE()
	 */
	@Override
	public float getE() {
		return e;
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getS_Ext()
	 */
	@Override
	public float getS_Ext() {
		return ext.s_ext;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getExtrusion()
	 */
	@Override
	public float getExtrusion() {
		return extrusion;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getF()
	 */
	@Override
	public float getF() {
		return f;
	}
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getS_Fan()
	 */
	@Override
	public float getS_Fan() {
		return ext.s_fan;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getGcode()
	 */
	@Override
	public Constants.GCDEF getGcode() {
		return GCDEF.getGCDEF(gcode);
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getLineindex()
	 */
	@Override
	public int getLineindex() {
		return lineindex;
	}




	
	
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getPosition(de.dietzm.Position)
	 */
	@Override
	public Position getPosition(Position reference){
		return new Position( isInitialized(Constants.X_MASK)?x:reference.x,isInitialized(Constants.Y_MASK)?y:reference.y);
	}
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getSpeed()
	 */
	@Override
	public float getSpeed(){
		return Constants.round2digits((distance/time));
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeSpeed(int, boolean)
	 */
	public void changeSpeed(int percent,boolean printonly){
		if(printonly && !isExtruding()) return;  //skip travel
		if(isInitialized(Constants.F_MASK)) f=f+(f/100*percent);
		//if(e!=UNINITIALIZED) e=e+(e/100*percent); 
		update();
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeExtrusion(int)
	 */
	public void changeExtrusion(int percent){
		if(isInitialized(Constants.E_MASK)) {
			e=e+(e/100*percent);
			update();
		}
		
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeLayerHeight(int)
	 */

	public void changeLayerHeight(int percent){
		if(isInitialized(Constants.Z_MASK)){
			z=z+(z/100*percent);
			update();
		}
		if(isInitialized(Constants.E_MASK)){ 
			e=e+(e/100*percent);
			update();
		}
		
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeZOffset(float)
	 */
	public void changeZOffset(float value){
		if(isInitialized(Constants.Z_MASK)){
			z=z+value;
			update();
		}
		
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeYOffset(float)
	 */
	public void changeYOffset(float value){
		if(isInitialized(Constants.Y_MASK)){
			y=y+value;
			update();
		}
		
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeXOffset(float)
	 */
	public void changeXOffset(float value){
		if(isInitialized(Constants.X_MASK)){
			x=x+value;
			update();
		}
		
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeToComment()
	 */
	public void changeToComment(){
		MemoryEfficientString mes = getCodeline();
		data= new MemoryEfficientString(";"+mes).getBytes();
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeFan(int)
	 */
	public void changeFan(int value){
		if(isInitialized(Constants.SF_MASK)) {
			ext.s_fan=Float.valueOf(value);
			update();
		}
		
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeExtTemp(float)
	 */
	public void changeExtTemp(float extr){
		if(isInitialized(Constants.SE_MASK)) {
			ext.s_ext=extr;
			update();
		}
		
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#changeBedTemp(float)
	 */
	public void changeBedTemp(float bed){
		if(isInitialized(Constants.SB_MASK)){
			ext.s_bed=bed;
			update();
		}
		
	}
	
	private void update(){
		GCDEF gd = GCDEF.getGCDEF(gcode);
//		String newgc = (gd!=Constants.GCDEF.UNKNOWN?gd:"")+
//				getIfInit("X",x,3,Constants.X_MASK)+
//				getIfInit("Y",y,3,Constants.Y_MASK)+
//				getIfInit("Z",z,3,Constants.Z_MASK)+
//				getIfInit("F",f,3,Constants.F_MASK)+
//				getIfInit("E",e,5,Constants.E_MASK)+
//				getIfInit("S",ext.s_bed,0,Constants.SB_MASK)+
//				getIfInit("S",ext.s_ext,0,Constants.SE_MASK)+
//				getIfInit("S",ext.s_fan,0,Constants.SF_MASK)+
//				(commentidx!=-1?getComment():"");
				//parseGcode(newgc);
	}
	



	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getTime()
	 */
	@Override
	public float getTime() {
		return time;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getUnit()
	 */
	@Override
	public String getUnit() {
		return ext.unit;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getX()
	 */
	@Override
	public float getX() {
		return x;
	}
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getIx()
	 */
	@Override
	public float getIx() {
		return ext.ix;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getJy()
	 */
	@Override
	public float getJy() {
		return ext.jy;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getKz()
	 */
	@Override
	public float getKz() {
		return ext.kz;
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getR()
	 */
	@Override
	public float getR() {
		return ext.r;
	}


	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getY()
	 */
	@Override
	public float getY() {
		return y;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getZ()
	 */
	@Override
	public float getZ() {
		return z;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#isExtrudeOrRetract()
	 */
	@Override
	public boolean isExtrudeOrRetract(){
		return ( isInitialized(Constants.E_MASK) && e != 0 );
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#isExtruding()
	 */
	@Override
	public boolean isExtruding(){
		return ( isInitialized(Constants.E_MASK) && extrusion > 0 );
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#isPrintable()
	 */
	@Override
	public boolean isPrintable(){
		 return !Constants.GCDEF.COMMENT.equals(gcode) ; 
	}
	

	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#isComment()
	 */
	@Override
	public boolean isComment(){
		 return Constants.GCDEF.COMMENT.equals(gcode) && commentidx != -1; 
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#isBuffered()
	 */
	@Override
	public boolean isBuffered(){
		//if(gcode==null) return false;
		if (Constants.GCDEF.G0.equals(gcode) || Constants.GCDEF.G1.equals(gcode) || Constants.GCDEF.G2.equals(gcode)  || Constants.GCDEF.M106.equals(gcode) || Constants.GCDEF.M107.equals(gcode)   ||Constants.GCDEF.G29.equals(gcode) || Constants.GCDEF.G30.equals(gcode) || Constants.GCDEF.G31.equals(gcode) || Constants.GCDEF.G32.equals(gcode)){
			return true;
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#isLongRunning()
	 */
	@Override
	public boolean isLongRunning(){
		if (Constants.GCDEF.M190.equals(gcode) || Constants.GCDEF.M109.equals(gcode) || Constants.GCDEF.G28.equals(gcode) ){
			return true;
		}
		return false;
	}

	
//	/**
//	 * Set the bedtemp to remember the configured temp.
//	 * This does NOT change the s_bed variable and will not written to gcode on save
//	 * @param fanspeed
//	 */
//	public void setBedtemp(float bedtemp) {
//		this.bedtemp = bedtemp;
//	}



	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#setCurrentPosition(de.dietzm.Position)
	 */
	@Override
	public void setCurrentPosition(Position currentPosition) {
		curX=currentPosition.x;
		curY=currentPosition.y;
	}

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#setDistance(float)
	 */
	@Override
	public void setDistance(float distance) {
		this.distance = distance;
	}

//	/**
//	 * Set the exttemp to remember the configured temp.
//	 * This does NOT change the s_ext variable and will not written to gcode on save
//	 * @param fanspeed
//	 */
//	public void setExtemp(float extemp) {
//		this.extemp = extemp;
//	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#setExtrusion(float)
	 */
	@Override
	public void setExtrusion(float extrusion) {
		this.extrusion = extrusion;
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#setLineindex(int)
	 */
	@Override
	public void setLineindex(int lineindex) {
		this.lineindex = lineindex;
	}
	


	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#setTime(float)
	 */
	@Override
	public void setTime(float time) {
		this.time = time;
	}
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#setTimeAccel(float)
	 */
	@Override
	public void setTimeAccel(float time) {
		this.timeaccel = time;
	}
	
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getTimeAccel()
	 */
	@Override
	public float getTimeAccel() {
		return this.timeaccel;
	}
	
	
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#getExtrusionSpeed()
	 */
	@Override
	public float getExtrusionSpeed(){
		return (extrusion/timeaccel)*60f;
	}
	

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#toString()
	 */
	public String toString() {		
		String var = lineindex+":  "+toStringRaw();
		var+="\tExtrusion:"+extrusion;
		var+="\tDistance:"+distance;
		var+="\tPosition:"+curX+"x"+curY;
		var+="\tTime:"+time;
		return var;
	}
	

	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#toCSV()
	 */
	@Override
	public String toCSV() {		
		String var = String.valueOf(getSpeed());
		var+=";"+extrusion;
		var+=";"+distance;
		var+=";"+time;
		var+=";"+fanspeed;
		return var;
	}
	
	public void setInitialized(short mask, float value){
		if(mask > Constants.Z_MASK && ext==null)ext=new Extended();
		
		switch (mask) {
		case Constants.E_MASK:
			e = value;
			break;
		case Constants.X_MASK:
			x  = value;
			break;
		case Constants.Y_MASK:
			y = value;
			break;
		case Constants.Z_MASK:
			z = value;
			break;
		case Constants.F_MASK:
			f = value;
			break;
		case Constants.SE_MASK:
			ext.s_ext = value;
			break;
		case Constants.SB_MASK:
			ext.s_bed  = value;
			break;
		case Constants.SF_MASK:
			ext.s_fan = value;	
			break;
		case Constants.IX_MASK:
			 ext.ix  = value;
			 break;
		case Constants.JY_MASK:
			 ext.jy  = value;
			 break;
		case Constants.KZ_MASK:
			ext.kz = value;
			break;
		case Constants.R_MASK:
			ext.r = value;
			break;
		default:
			break;
		}
		
	}
	/* (non-Javadoc)
	 * @see de.dietzm.GCodeIf#isInitialized(int)
	 */
	@Override
	public boolean isInitialized(int mask){
		switch (mask) {
		case Constants.E_MASK:
			return e != Float.MAX_VALUE;
		case Constants.X_MASK:
			return x != Float.MAX_VALUE;
		case Constants.Y_MASK:
			return y != Float.MAX_VALUE;
		case Constants.Z_MASK:
			return z != Float.MAX_VALUE;
		case Constants.F_MASK:
			return f != Float.MAX_VALUE;
		case Constants.SE_MASK:
			return ext != null && ext.s_ext != Float.MAX_VALUE;
		case Constants.SB_MASK:
			return ext != null && ext.s_bed != Float.MAX_VALUE;
		case Constants.SF_MASK:
			return ext != null && ext.s_fan != Float.MAX_VALUE;		
		case Constants.IX_MASK:
			return ext != null && ext.ix != Float.MAX_VALUE;		
		case Constants.JY_MASK:
			return ext != null && ext.jy != Float.MAX_VALUE;		
		case Constants.KZ_MASK:
			return ext != null && ext.kz != Float.MAX_VALUE;		
		case Constants.R_MASK:
			return ext != null && ext.r != Float.MAX_VALUE;		
		default:
			break;
		}
		return false;
	}

	public void setUnit(String unit){
		if(ext==null) ext = new Extended();
		ext.unit=unit;
	}

//	@Override
//	protected String getIfInit(String prefix, float val, int digits, int mask) {
//		// TODO Auto-generated method stub
//		return null;
//	}
	
	

}

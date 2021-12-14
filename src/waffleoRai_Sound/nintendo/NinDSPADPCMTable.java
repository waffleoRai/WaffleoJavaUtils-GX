package waffleoRai_Sound.nintendo;

import waffleoRai_Sound.ADPCMTable;
import waffleoRai_Utils.FileBuffer;

public class NinDSPADPCMTable extends ADPCMTable{
	/*--- Instance Variables ---*/
	
	//All values are sign extended from short
	//This is because Java refuses to do bit operations on anything smaller than 32 bits
	private int[] coeff;
	
	/*--- Construction/Parsing ---*/
	
	public NinDSPADPCMTable(){
		super(2);
		coeff = new int[16];
	}
	
	public static NinDSPADPCMTable readFromFile(FileBuffer src, long stpos){
		//System.out.println("Input start offset: 0x" + Long.toHexString(stpos));
		if(src == null) return null;
		long cpos = stpos;
		NinDSPADPCMTable tbl = new NinDSPADPCMTable();
		for(int i = 0; i < 16; i++){
			tbl.coeff[i] = (int)src.shortFromFile(cpos);
			cpos += 2;
		}
		tbl.gain = (int)src.shortFromFile(cpos); cpos += 2;
		int ps = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.backsamps_start[0] = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.backsamps_start[1] = (int)src.shortFromFile(cpos); cpos += 2;
		int lps = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.backsamps_loop[0] = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.backsamps_loop[1] = (int)src.shortFromFile(cpos); cpos += 2;
		
		tbl.cidx_start = (ps >>> 4) & 0xf;
		tbl.shamt_start = ps & 0xf;
		tbl.cidx_loop = (lps >>> 4) & 0xf;
		tbl.shamt_loop = lps & 0xf;
		
		return tbl;
	}
	
	/*--- Getters ---*/
	
	public int getCoefficient(int index){return coeff[index];}
	public int getGain(){return gain;}
	
	/*--- Setters ---*/
	
	public void reallocCoefficientTable(int alloc){
		coeff = new int[alloc];
	}
	
	public void setCoefficient(int index, int value){coeff[index] = value;}
	public void setGain(int value){gain = value;}

	/*--- Print Info ---*/
	
	public void printInfo(){
		System.out.println("Gain: 0x" + Integer.toHexString(gain));
		System.out.println("Predictor (Start): " + cidx_start);
		System.out.println("Shift (Start): " + shamt_start);
		System.out.println("S1: 0x" + Integer.toHexString(backsamps_start[0]));
		System.out.println("S2: 0x" + Integer.toHexString(backsamps_start[1]));
		System.out.println("Predictor (Loop): " + cidx_loop);
		System.out.println("Shift (Loop): " + shamt_loop);
		System.out.println("Loop S1: 0x" + Integer.toHexString(backsamps_loop[0]));
		System.out.println("Loop S2: 0x" + Integer.toHexString(backsamps_loop[1]));
		System.out.println("Coeff Table: ");
		for(int i = 0; i < coeff.length; i++)
		{
			System.out.println("\t0x" + Integer.toHexString(coeff[i]));
		}
	}
	
}

package waffleoRai_Sound.nintendo;

import waffleoRai_Utils.FileBuffer;

public class ADPCMTable 
{
	/*--- Instance Variables ---*/
	
	//All values are sign extended from short
	//This is because Java refuses to do bit operations on anything smaller than 32 bits
	private int[] coeff;
	private int gain;
	private int ps;
	private int s1;
	private int s2;
	private int lps;
	private int ls1;
	private int ls2;
	
	/*--- Construction/Parsing ---*/
	
	public ADPCMTable()
	{
		coeff = new int[16];
	}
	
	public static ADPCMTable readFromFile(FileBuffer src, long stpos)
	{
		//System.out.println("Input start offset: 0x" + Long.toHexString(stpos));
		if(src == null) return null;
		long cpos = stpos;
		ADPCMTable tbl = new ADPCMTable();
		for(int i = 0; i < 16; i++)
		{
			tbl.coeff[i] = (int)src.shortFromFile(cpos);
			cpos += 2;
		}
		tbl.gain = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.ps = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.s1 = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.s2 = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.lps = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.ls1 = (int)src.shortFromFile(cpos); cpos += 2;
		tbl.ls2 = (int)src.shortFromFile(cpos); cpos += 2;
		
		return tbl;
	}
	
	/*--- Getters ---*/
	
	public int getCoefficient(int index){return coeff[index];}
	public int getGain(){return gain;}
	public int getPS(){return ps;}
	public int getHistorySample1(){return s1;}
	public int getHistorySample2(){return s2;}
	public int getLoopPS(){return lps;}
	public int getLoopHistorySample1(){return ls1;}
	public int getLoopHistorySample2(){return ls2;}
	
	/*--- Setters ---*/
	
	public void reallocCoefficientTable(int alloc){
		coeff = new int[alloc];
	}
	
	public void setCoefficient(int index, int value){coeff[index] = value;}
	public void setGain(int value){gain = value;}
	public void setPS(int value){ps = value;}
	public void setHistorySample1(int value){s1 = value;}
	public void setHistorySample2(int value){s2 = value;}
	public void setLoopPS(int value){lps = value;}
	public void setLoopHistorySample1(int value){ls1 = value;}
	public void setLoopHistorySample2(int value){ls2 = value;}

	/*--- Print Info ---*/
	
	public void printInfo()
	{
		System.out.println("Gain: 0x" + Integer.toHexString(gain));
		System.out.println("PS: 0x" + Integer.toHexString(ps));
		System.out.println("S1: 0x" + Integer.toHexString(s1));
		System.out.println("S2: 0x" + Integer.toHexString(s2));
		System.out.println("Loop PS: 0x" + Integer.toHexString(lps));
		System.out.println("Loop S1: 0x" + Integer.toHexString(ls1));
		System.out.println("Loop S2: 0x" + Integer.toHexString(ls2));
		System.out.println("Coeff Table: ");
		for(int i = 0; i < coeff.length; i++)
		{
			System.out.println("\t0x" + Integer.toHexString(coeff[i]));
		}
	}
	
}

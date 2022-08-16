package waffleoRai_Sound.nintendo;

import waffleoRai_Sound.ADPCMTable;
import waffleoRai_Utils.FileBuffer;

public class NinDSPADPCMTable extends ADPCMTable{
	/*--- Instance Variables ---*/
	
	//All values are sign extended from short
	//This is because Java refuses to do bit operations on anything smaller than 32 bits
	private int[][] coeff;
	
	/*--- Construction/Parsing ---*/
	
	public NinDSPADPCMTable(int predictors){
		super(2);
		coeff = new int[predictors][2];
	}
	
	public static NinDSPADPCMTable readFromFile(FileBuffer src, long stpos){
		//System.out.println("Input start offset: 0x" + Long.toHexString(stpos));
		if(src == null) return null;
		long cpos = stpos;
		NinDSPADPCMTable tbl = new NinDSPADPCMTable(8);
		for(int p = 0; p < 8; p++){
			for(int o = 0; o < 2; o++){
				tbl.coeff[p][o] = (int)src.shortFromFile(cpos);
				cpos += 2;
			}
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
	
	public int getPredictorIndex(int index_1d){
		return index_1d%coeff.length;
	}
	
	public int getOrderIndex(int index_1d){
		return index_1d/coeff.length;
	}
	
	public int predictorCount(){return coeff.length;}
	public int getCoefficient(int index){return coeff[getPredictorIndex(index)][getOrderIndex(index)];}
	public int getCoefficient(int p, int o){return coeff[p][o];}
	public int getGain(){return gain;}
	
	/*--- Setters ---*/
	
	public void reallocCoefficientTable(int predictors){
		coeff = new int[predictors][2];
	}
	
	public void setCoefficient(int index, int value){coeff[getPredictorIndex(index)][getOrderIndex(index)] = value;}
	public void setCoefficient(int p, int o, int value){coeff[p][o] = value;}
	public void setGain(int value){gain = value;}
	
	public void setInitBacksample(int sample, int idx){backsamps_start[idx] = sample;}
	public void setLoopBacksample(int sample, int idx){backsamps_loop[idx] = sample;}
	public void setInitPredictor(int value){cidx_start = value;}
	public void setLoopPredictor(int value){cidx_loop = value;}
	public void setInitShift(int value){shamt_start = value;}
	public void setLoopShift(int value){shamt_loop = value;}

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
		for(int p = 0; p < coeff.length; p++){
			for(int o = 0; o < 2; o++){
				int val = coeff[p][o] & 0xffff;
				System.out.print(String.format("%04x ", val));
			}
			System.out.println();
		}
	}
	
}

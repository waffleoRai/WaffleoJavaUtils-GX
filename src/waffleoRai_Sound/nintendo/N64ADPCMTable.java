package waffleoRai_Sound.nintendo;

import waffleoRai_Sound.ADPCMTable;
import waffleoRai_Utils.BufferReference;

public class N64ADPCMTable extends ADPCMTable{
	
	private static final int[] DEFO_TBL24 = {0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
											0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
											0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
											0x0779, 0x06fc, 0x0687, 0x0619, 0x05b3, 0x0553, 0x04fa, 0x04a7,
											0xf880, 0xf1b4, 0xebc8, 0xe6db, 0xe307, 0xe058, 0xded1, 0xde6b,
											0x0f40, 0x1592, 0x1ad2, 0x1ee8, 0x21c5, 0x2366, 0x23d2, 0x2318,
											0xf980, 0xf452, 0xf04c, 0xed45, 0xeb1b, 0xe9ac, 0xe8db, 0xe88e,
											0x0e60, 0x1354, 0x170d, 0x19b8, 0x1b7b, 0x1c7c, 0x1cdb, 0x1cb5};

	private int[][][] coeff_table;
	
	public N64ADPCMTable(int order, int pcount) {
		super(order);
		coeff_table = new int[pcount][order][8];
	}
	
	public static N64ADPCMTable readTable(BufferReference ref){
		//Get the order and pred count
		int o = ref.nextInt();
		int pcount = ref.nextInt();
		
		N64ADPCMTable tbl = new N64ADPCMTable(o, pcount);
		for(int p = 0; p < pcount; p++){
			for(o = 0; o < tbl.order; o++){
				for(int i = 0; i < 8; i++){
					tbl.coeff_table[p][o][i] = (int)ref.nextShort();
				}
			}
		}
		
		return tbl;
	}
	
	public static N64ADPCMTable fromRaw(int order, int pcount, short[] table){
		int c = 0;
		N64ADPCMTable tbl = new N64ADPCMTable(order, pcount);
		for(int p = 0; p < pcount; p++){
			for(int o = 0; o < order; o++){
				for(int i = 0; i < 8; i++){
					tbl.coeff_table[p][o][i] = (int)table[c++];
				}
			}
		}
		return tbl;
	}
	
	public static N64ADPCMTable fromRaw(int order, int pcount, int[] table){
		int c = 0;
		N64ADPCMTable tbl = new N64ADPCMTable(order, pcount);
		for(int p = 0; p < pcount; p++){
			for(int o = 0; o < order; o++){
				for(int i = 0; i < 8; i++){
					tbl.coeff_table[p][o][i] = (int)((short)table[c++]);
				}
			}
		}
		return tbl;
	}
	
	public static N64ADPCMTable fromRaw(int order, int pcount, short[][] table){
		int c = 0;
		N64ADPCMTable tbl = new N64ADPCMTable(order, pcount);
		for(int p = 0; p < pcount; p++){
			c = 0;
			for(int o = 0; o < order; o++){
				for(int i = 0; i < 8; i++){
					tbl.coeff_table[p][o][i] = (int)table[p][c++];
				}
			}
		}
		return tbl;
	}

	public static N64ADPCMTable getDefaultTable(){
		return fromRaw(2,4,DEFO_TBL24);
	}
	
	public int getPredictorCount(){
		if(coeff_table == null) return 0;
		return coeff_table.length;
	}
	
	public int getOrder(){
		if(coeff_table == null) return 0;
		return coeff_table[0].length;
	}
	
	public int getCoefficient(int idx_1d) {
		int i = idx_1d & 0x7;
		idx_1d >>>= 3;
		int o = idx_1d % order;
		int p = idx_1d / order;
		
		return coeff_table[p][o][i];
	}
	
	public int getCoefficient(int p, int o) {
		return coeff_table[p][o][0];
	}
	
	public int getCoefficient(int p, int o, int i) {
		return coeff_table[p][o][i];
	}
	
	public void setCoefficient(int p, int o, int i, int val){
		coeff_table[p][o][i] = (int)((short)val);
	}

	public short[] getAsRaw(){
		int pcount = coeff_table.length;
		int total = order * pcount * 8;
		short[] tbl = new short[total];
		int j = 0;
		for(int p = 0; p < pcount; p++){
			for(int o = 0; o < order; o++){
				for(int i = 0; i < 8; i++){
					tbl[j++] = (short)coeff_table[p][o][i];
				}
			}
		}
		return tbl;
	}
	
	public boolean bookEquals(N64ADPCMTable other){
		if(other == null) return false;
		if(other == this) return true;
		
		if(this.getPredictorCount() != other.getPredictorCount()) return false;
		if(this.getOrder() != other.getOrder()) return false;
		
		int p = this.getPredictorCount();
		int o = this.getOrder();
		try{
			for(int i = 0; i < p; i++){
				for(int j = 0; j < o; j++){
					for(int k = 0; k < 8; k++){
						if(this.coeff_table[i][j][k] != other.coeff_table[i][j][k]) return false;
					}
				}
			}
		}
		catch(ArrayIndexOutOfBoundsException ex){
			return false;
		}
		
		return true;
	}
	
}

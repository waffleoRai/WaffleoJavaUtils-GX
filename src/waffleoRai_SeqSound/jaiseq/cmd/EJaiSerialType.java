package waffleoRai_SeqSound.jaiseq.cmd;

public enum EJaiSerialType {
	
	SERIALFMT_DUMMY(-1,-1),
	
	SERIALFMT_(1,0),
	SERIALFMT_L(1,1),
	SERIALFMT_1(2,1),
	SERIALFMT_2(3,1),
	SERIALFMT_3(4,1),
	SERIALFMT_11(3,2),
	SERIALFMT_12(4,2),
	SERIALFMT_13(5,2),
	SERIALFMT_111(4,3),
	SERIALFMT_112(5,3),
	SERIALFMT_121(5,3),
	SERIALFMT_122(6,3),
	
	SERIALFMT_V(0,1),
	SERIALFMT_NOTE(3,3),
	
	;
	
	private int ser_size;
	private int ser_args;
	
	private EJaiSerialType(int size, int args){
		ser_size = size;
		ser_args = args;
	}
	
	public int getArgCount(){return ser_args;}
	public int getSerSize(){return ser_size;}

}

package waffleoRai_SeqSound.n64al.cmd.linking;

/*
 * Commands for self-modification and indirect linking
 * 
 * [sts] - Store to Self (Seq, Channel)
 * [stps] - Store P to Self (Channel)
 * 
 * [lds] - Load from Self (Channel)
 * [ldptbl] - Load P from Table (Channel)
 * [ldpi] - Load P Immediate (Channel)
 * 		Can be either literal or 
 * 
 * [dyntbl] - Set DynTable (Channel)
 * 		Set channel dyntable pointer to immediate
 * [ptodyn] - P to DynTable (Channel)
 * 		Sets channel dyntable pointer to current p
 * [dyntop] - DynTable to P (Channel)
 * 		Sets p to DynTable[q]
 * [lddyn] - Load from DynTable (Channel)
 * 		Sets q to DynTable[q]
 * [dynlup]- DynTable Lookup (Channel)
 * 
 * [dyncall] - Dynamic Call (Seq, Channel)
 * [dynldlayer] - Dynamic Load Layer (Channel)
 * 
 */

public class NUSALSeqLinking {
	
	public static final int P_TYPE_UNK = -1;
	public static final int P_TYPE_LITERAL = 0;
	public static final int P_TYPE_QDATA = 1;
	public static final int P_TYPE_PDATA = 2;
	public static final int P_TYPE_GENERAL_DATA = 3;
	public static final int P_TYPE_SEQ_CODE = 4;
	public static final int P_TYPE_CH_CODE = 5;
	public static final int P_TYPE_LYR_CODE = 6;
	public static final int P_TYPE_STS_TARG = 7;
	
	//What are the contents of a PTable
	//public static final int PTBL_TYPE_UNK = -1;
	//public static final int PTBL_TYPE_SEQ_CODE_PTR = 1;
	//public static final int PTBL_TYPE_CH_CODE_PTR = 2;
	//public static final int PTBL_TYPE_LYR_CODE_PTR = 3;
	//public static final int PTBL_TYPE_MOD_ARGS = 4; //Used as literal arguments to modify commands w/stps (eg. long delays)
	//public static final int PTBL_TYPE_DATA_PTR = 5; //Pointers to data (filters, other tables, buffers, etc.)

}

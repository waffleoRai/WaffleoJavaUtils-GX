package waffleoRai_SeqSound.n64al;

public class NUSALSeqCommands {
	
	public static final int FLAG_REFOVERLAP = 	0x080000;
	public static final int FLAG_ADDRREF = 		0x040000;
	public static final int FLAG_OPENTRACK = 	0x020000;
	public static final int FLAG_STATEMOD = 	0x010000;
	public static final int FLAG_BRANCH = 		0x008000;
	public static final int FLAG_JUMP = 		0x004000;
	public static final int FLAG_CALL = 		0x002000;
	public static final int FLAG_STACKPOP = 	0x001000;
	public static final int FLAG_ISWAIT = 		0x000800;
	public static final int FLAG_TAKESTIME =	0x000400;
	public static final int FLAG_ONCEPERTICK =	0x000200;
	public static final int FLAG_PARAMSET =		0x000100;
	public static final int FLAG_DATAONLY =		0x000080;
	public static final int FLAG_SEQDATMOD =	0x000040;
	public static final int FLAG_HASVLQ =		0x000008;
	public static final int FLAG_SEQVALID =		0x000004;
	public static final int FLAG_CHVALID =		0x000002;
	public static final int FLAG_LYRVALID =		0x000001;
	
	public static final int MML_DATAPARAM_TYPE__DECSIGNED = 0;
	public static final int MML_DATAPARAM_TYPE__DECUNSIGNED = 1;
	public static final int MML_DATAPARAM_TYPE__HEXUNSIGNED = 2;
	public static final int MML_DATAPARAM_TYPE__BUFFER = 3;

}

package waffleoRai_SeqSound.ninseq;

import java.util.List;

/*
 * Sources:
 * 	- BrawlBox (https://github.com/libertyernie/brawltools) RSEQ.cs
 * 	- VGMTrans (https://github.com/vgmtrans/vgmtrans) NDSSeq.cpp
 * 	- loveemu sseq2mid (https://github.com/loveemu/loveemu-lab)
 * 	- kiwids sseq documentation (https://sites.google.com/site/kiwids/)
 *  - rseq2midi Source code
 *  - SSEQPlayer (https://github.com/kode54/SSEQPlayer)
 */

public interface NinSeq {
	
	/* ----- Constants ----- */
	
	public static final int TICKS_PER_QNOTE = 48;
	
	public static final int STACK_NODE_TYPE_RA = 0; //Return address
	public static final int STACK_NODE_TYPE_LS = 1; //Loop start
	
	public static final int PLAYER_MODE_DS = 0;
	public static final int PLAYER_MODE_WII = 1;
	public static final int PLAYER_MODE_3DS = 2;
	public static final int PLAYER_MODE_CAFE = 3;
	
	public static final int COMMAND_TYPE_ANY = 0x0F;
	public static final int COMMAND_TYPE_DS_ONLY = 0x01;
	public static final int COMMAND_TYPE_WII_ONLY = 0x02;
	public static final int COMMAND_TYPE_3DS_ONLY = 0x04;
	public static final int COMMAND_TYPE_CAFE_ONLY = 0x08;
	public static final int COMMAND_TYPE_RCF_ONLY = 0x0E;
	public static final int COMMAND_TYPE_CF_ONLY = 0x0C;
	
	public static final int NO_DEFAULT_VALUE = -1;
	
	/* ----- Methods ----- */
	
	public NinSeqDataSource getSequenceData();
	public boolean writeMIDI(String path, boolean verbose);
	public boolean writeMIDI(int lblidx, String path, boolean verbose);
	
	public short getVariable(int idx);
	public void setVariable(int idx, short val);
	public String getName();
	public void setName(String s);
	
	public int getLabelCount();
	public NinSeqLabel getLabel(int idx);
	public List<NinSeqLabel> getLabels();
	
}

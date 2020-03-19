package waffleoRai_SeqSound.ninseq;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public enum NSExtendedCommand 
{
	SET_VAR(0x80),
	ADD_VAR(0x81),
	SUB_VAR(0x82),
	MUL_VAR(0x83),
	DIV_VAR(0x84),
	SHIFT_VAR(0x85),
	RAND_VAR(0x86),
	AND_VAR(0x87),
	OR_VAR(0x88),
	XOR_VAR(0x89),
	NOT_VAR(0x8a),
	MOD_VAR(0x8b),
	
	CMP_EQ(0x90),
	CMP_GE(0x91),
	CMP_GT(0x92),
	CMP_LE(0x93),
	CMP_LT(0x94),
	CMP_NE(0x95),
	
	USER_PROC(0xe0),

	;
	
	private int value;
	
	private NSExtendedCommand(int cmd)
	{
		value = cmd;
	}
	
	public byte getCommandByte(){return (byte)value;}
	
	private static ConcurrentMap<Integer, NSExtendedCommand> cmdMap;
	
	public static NSExtendedCommand getSubCommand(byte value)
	{
		if(cmdMap == null)
		{
			cmdMap = new ConcurrentHashMap<Integer, NSExtendedCommand>();
			for(NSExtendedCommand c : NSExtendedCommand.values()) cmdMap.put(c.value, c);
		}
		return cmdMap.get(Byte.toUnsignedInt(value));
	}

}

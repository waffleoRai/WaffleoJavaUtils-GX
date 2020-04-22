package waffleoRai_SeqSound.ninseq;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_Utils.FileBuffer;

public class NinSeqParser {
	
	public static NSEvent parseEvent(FileBuffer src, long pos, int seqMode, boolean plimit)
	{
		byte cmdByte = src.getByte(pos);
		NSCommand cmd = NSCommand.getCommand(seqMode, cmdByte);
		if(cmd == NSCommand.PLAY_NOTE) return parseNoteEvent(cmd, src, pos, plimit);
		
		int cmdFam = (Byte.toUnsignedInt(cmdByte) >>> 4) & 0xF;
		try{
		switch(cmdFam)
		{
		case 0x8:
			if(cmd == NSCommand.WAIT || cmd == NSCommand.CHANGE_PRG) return parseStandardVLQEvent(cmd, src, pos, plimit);
			else return parseControlEvent(cmd, src, pos, plimit);
		case 0x9:
			return parseControlEvent(cmd, src, pos, plimit);
		case 0xa:
			return parsePrefixEvent(cmd, src, pos, seqMode, plimit);
		case 0xb:
			if (seqMode == NinSeq.PLAYER_MODE_DS) return parseStateEvent(cmd, src, pos, plimit);
			else return parseStandardOneEvent(cmd, src, pos, plimit);
		case 0xc:
		case 0xd:
			return parseStandardOneEvent(cmd, src, pos, plimit);
		case 0xe:
			return parseStandardTwoEvent(cmd, src, pos, plimit);
		case 0xf:
			if (cmd == NSCommand.EX_COMMAND) return parseStateEventWii(cmd, src, pos, plimit);
			else return parseControlEvent(cmd, src, pos, plimit);
		}
		}
		catch(Exception e)
		{
			return new UnknownCommandPlaceholder(cmdByte, e);
		}
		
		return null;
	}
	
	private static NoteEvent parseNoteEvent(NSCommand cmd, FileBuffer src, long pos, boolean plimit)
	{
		//TODO padding 0s at end might be deferred to this parser.
		
		long cpos = pos;
		int note = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		int vel = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		if(!plimit)
		{
			int[] vlq = MIDI.getVLQ(src, cpos);
			NoteEvent e = new NoteEvent(note, vel, vlq[0]);
			return e;
		}
		
		return new NoteEvent((int)cmd.getCommandByte(), vel, -1);
	}
	
	private static StandardEvent parseStandardOneEvent(NSCommand cmd, FileBuffer src, long pos, boolean plimit)
	{
		if(plimit) return new StandardEvent(cmd, -1);
		int param = Byte.toUnsignedInt(src.getByte(pos+1));
		StandardEvent ecd = new StandardEvent(cmd, param);
		return ecd;
	}
	
	private static StandardEvent parseStandardTwoEvent(NSCommand cmd, FileBuffer src, long pos, boolean plimit)
	{
		if(plimit) return new StandardEvent(cmd, -1);
		int param = Short.toUnsignedInt(src.shortFromFile(pos+1));
		StandardEvent e = new StandardEvent(cmd, param);
		return e;
	}
	
	private static StandardEvent parseStandardVLQEvent(NSCommand cmd, FileBuffer src, long pos, boolean plimit)
	{
		if(plimit) return new StandardEvent(cmd, -1);
		int[] vlq = MIDI.getVLQ(src, pos+1);
		StandardEvent e = new StandardEvent(cmd, vlq[0]);
		return e;
	}
	
	private static StateEvent parseStateEvent(NSCommand cmd, FileBuffer src, long pos, boolean plimit)
	{
		long cpos = pos + 1;
		int vind = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		if(!plimit)
		{
			int val = (int)src.shortFromFile(cpos);
			StateEvent e = new StateEvent(cmd, vind, val);
			return e;
		}
		return new StateEvent(cmd, vind, -1);
	}
	
	private static StateEvent parseStateEventWii(NSCommand cmd, FileBuffer src, long pos, boolean plimit)
	{
		long cpos = pos + 1;
		byte cmdbyte = src.getByte(cpos); cpos++;
		NSExtendedCommand excmd = NSExtendedCommand.getSubCommand(cmdbyte);
		int vind = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
		if(!plimit)
		{
			int val = (int)src.shortFromFile(cpos);
			StateEvent e = new StateEvent(cmd, excmd, vind, val);
			return e;
		}
		return new StateEvent(cmd, excmd, vind, -1);
	}
	
	private static ControlEvent parseControlEvent(NSCommand cmd, FileBuffer src, long pos, boolean plimit)
	{
		long cpos = pos+1;
		switch(cmd)
		{
		case OPEN_TRACK_WII:
		case OPEN_TRACK_DS:	
			int track = Byte.toUnsignedInt(src.getByte(cpos)); cpos++;
			int taddr = -1;
			if(!plimit) taddr = src.shortishFromFile(cpos);
			return new ControlEvent(cmd, track, taddr);
		case JUMP_WII:
		case JUMP_DS:
		case CALL_WII:
		case CALL_DS:
			int jaddr = -1;
			if(!plimit) jaddr = src.shortishFromFile(cpos);
			return new ControlEvent(cmd, jaddr);
		case ENV_RESET:
		case LOOP_END:
		case RETURN:
		case TRACK_END:
			return new ControlEvent(cmd);
		case ALLOC_TRACK:
			int tflags = -1;
			if(!plimit) tflags = Short.toUnsignedInt(src.shortFromFile(cpos));
			return new ControlEvent(cmd, tflags);
		default: return null;
		}
	}
	
	private static PrefixEvent parsePrefixEvent(NSCommand cmd, FileBuffer src, long pos, int seqMode, boolean plimit)
	{
		//TODO plimit?
		switch(cmd)
		{
		case PREFIX_RANDOM: return parsePrefixEvent_Random(cmd, src, pos, seqMode);
		case PREFIX_VARIABLE: return parsePrefixEvent_Variable(cmd, src, pos, seqMode);
		case PREFIX_IF: return parsePrefixEvent_If(cmd, src, pos, seqMode);
		case PREFIX_TIME: return parsePrefixEvent_Time(cmd, src, pos, seqMode);
		case PREFIX_TIME_RANDOM: return parsePrefixEvent_TimeRandom(cmd, src, pos, seqMode);
		case PREFIX_TIME_VARIABLE: return parsePrefixEvent_TimeVariable(cmd, src, pos, seqMode);
		default: return null;
		}
	}
	
	private static PrefixEvent parsePrefixEvent_Random(NSCommand cmd, FileBuffer src, long pos, int seqMode)
	{
		//Get sub-command
		long cpos = pos+1;
		byte cmdbyte = src.getByte(cpos);
		NSCommand subcmd = NSCommand.getCommand(seqMode, cmdbyte);
		NSEvent sube = parseEvent(src, cpos, seqMode, true); cpos++;
		if(subcmd == NSCommand.EX_COMMAND) cpos++;
		if(sube.hasSecondParameter()) cpos++;
		
		int min = (int)src.shortFromFile(cpos); cpos += 2;
		int max = (int)src.shortFromFile(cpos);
		PrefixEvent e = new PrefixEvent(cmd, sube, min, max);
		
		return e;
	}
	
	private static PrefixEvent parsePrefixEvent_Variable(NSCommand cmd, FileBuffer src, long pos, int seqMode)
	{
		long cpos = pos+1;
		byte cmdbyte = src.getByte(cpos);
		NSCommand subcmd = NSCommand.getCommand(seqMode, cmdbyte);
		NSEvent sube = parseEvent(src, cpos, seqMode, true); cpos++;
		if(subcmd == NSCommand.EX_COMMAND) cpos++;
		if(sube.hasSecondParameter()) cpos++;
		
		int val = Byte.toUnsignedInt(src.getByte(cpos));
		PrefixEvent e = new PrefixEvent(cmd, sube, val, -1);
		
		return e;
	}
	
	private static PrefixEvent parsePrefixEvent_If(NSCommand cmd, FileBuffer src, long pos, int seqMode)
	{
		NSEvent sube = parseEvent(src, pos+1, seqMode, false);
		PrefixEvent e = new PrefixEvent(cmd, sube, -1, -1);
		return e;
	}
	
	private static PrefixEvent parsePrefixEvent_Time(NSCommand cmd, FileBuffer src, long pos, int seqMode)
	{
		long cpos = pos+1;
		byte cmdbyte = src.getByte(cpos);
		NSCommand subcmd = NSCommand.getCommand(seqMode, cmdbyte);
		
		if(subcmd.getCommandHigherNybble() == 0xa)
		{
			NSEvent sube = parseEvent(src, pos+2, seqMode, false);
			cpos += sube.getSerialSize() + 1;
			
			PrefixEvent subp = new PrefixEvent(subcmd, sube, -1, -1);
			int param = (int)src.shortFromFile(cpos);
			PrefixEvent e = new PrefixEvent(cmd, subp, param, -1);
			return e;
		}
		else
		{
			NSEvent sube = parseEvent(src, pos+1, seqMode, false);
			cpos += sube.getSerialSize();
			int param = (int)src.shortFromFile(cpos);
			PrefixEvent e = new PrefixEvent(cmd, sube, param, -1);
			return e;
		}

	}
	
	private static PrefixEvent parsePrefixEvent_TimeRandom(NSCommand cmd, FileBuffer src, long pos, int seqMode)
	{
		long cpos = pos+1;
		byte cmdbyte = src.getByte(cpos);
		NSCommand subcmd = NSCommand.getCommand(seqMode, cmdbyte);
		
		if(subcmd.getCommandHigherNybble() == 0xa)
		{
			NSEvent sube = parseEvent(src, pos+2, seqMode, false);
			cpos += sube.getSerialSize() + 1;
			
			PrefixEvent subp = new PrefixEvent(subcmd, sube, -1, -1);
			int min = (int)src.shortFromFile(cpos); cpos += 2;
			int max = (int)src.shortFromFile(cpos);
			PrefixEvent e = new PrefixEvent(cmd, subp, min, max);
			return e;
		}
		else
		{
			NSEvent sube = parseEvent(src, pos+1, seqMode, false);
			cpos += sube.getSerialSize();
			int min = (int)src.shortFromFile(cpos); cpos += 2;
			int max = (int)src.shortFromFile(cpos);
			PrefixEvent e = new PrefixEvent(cmd, sube, min, max);
			return e;
		}
	}
	
	private static PrefixEvent parsePrefixEvent_TimeVariable(NSCommand cmd, FileBuffer src, long pos, int seqMode)
	{
		long cpos = pos+1;
		byte cmdbyte = src.getByte(cpos);
		NSCommand subcmd = NSCommand.getCommand(seqMode, cmdbyte);
		
		if(subcmd.getCommandHigherNybble() == 0xa)
		{
			NSEvent sube = parseEvent(src, pos+2, seqMode, false);
			cpos += sube.getSerialSize() + 1;
			
			PrefixEvent subp = new PrefixEvent(subcmd, sube, -1, -1);
			int param = Byte.toUnsignedInt(src.getByte(cpos));
			PrefixEvent e = new PrefixEvent(cmd, subp, param, -1);
			return e;
		}
		else
		{
			NSEvent sube = parseEvent(src, pos+1, seqMode, false);
			cpos += sube.getSerialSize();
			int param = Byte.toUnsignedInt(src.getByte(cpos));
			PrefixEvent e = new PrefixEvent(cmd, sube, param, -1);
			return e;
		}
	}
	
}

package waffleoRai_SeqSound.ninseq;

public class ControlEvent extends NSEvent{

	//Open track, jump, call, return, alloc track, loop end, track end, env reset
	
	public ControlEvent(NSCommand cmd)
	{
		this(cmd, -1);
	}
	
	public ControlEvent(NSCommand cmd, int p1)
	{
		this(cmd, p1, -1);
	}
	
	public ControlEvent(NSCommand cmd, int p1, int p2)
	{
		super.setCommand(cmd);
		super.setParam1(p1);
		super.setParam2(p2);
		//super.setAddress(addr);
		switch(cmd)
		{
		case ALLOC_TRACK: super.setSerialSize(3); break;

		case CALL_DS:
		case CALL_WII:
			super.setSerialSize(4); break;
			
		case ENV_RESET: super.setSerialSize(1); break;

		case JUMP_DS:
		case JUMP_WII:
			super.setSerialSize(4); 
			break;
			
		case LOOP_END: super.setSerialSize(1); break;
			
		case OPEN_TRACK_DS:
		case OPEN_TRACK_WII: 
			super.setSerialSize(5);
			break;
		
		case RETURN: super.setSerialSize(1); break;
		default: break;
		}
	}
	
	public boolean hasFirstParameter()
	{
		NSCommand cmd = super.getCommand();
		switch(cmd)
		{
		case ALLOC_TRACK: return true;
		case CALL_DS: return true;
		case CALL_WII: return true;	
		case ENV_RESET: return false;
		case JUMP_DS:  return true;	
		case JUMP_WII:  return true;	
		case LOOP_END: return false;
		case OPEN_TRACK_DS: return true;	
		case OPEN_TRACK_WII: return true;
		case RETURN: return false;
		default: return false;
		}
	}
	
	public boolean hasSecondParameter()
	{
		NSCommand cmd = super.getCommand();
		switch(cmd)
		{
		case ALLOC_TRACK: return false;
		case CALL_DS: return false;
		case CALL_WII: return false;	
		case ENV_RESET: return false;
		case JUMP_DS:  return false;	
		case JUMP_WII:  return false;	
		case LOOP_END: return false;
		case OPEN_TRACK_DS: return true;	
		case OPEN_TRACK_WII: return true;
		case RETURN: return false;
		default: return false;
		}
	}
	
	public void execute(NinTrack track)
	{
		NSCommand cmd = super.getCommand();
		switch(cmd)
		{
		case ALLOC_TRACK: return; //Does nothing
		case CALL_DS:
		case CALL_WII: 
			track.call(getParam1());
			return;	
		case ENV_RESET: 
			track.resetEnvelope();
			return;
		case JUMP_DS:
		case JUMP_WII:  
			track.jump(getParam1());
			return;	
		case LOOP_END: 
			track.executeLoopEnd();
			return;
		case OPEN_TRACK_DS: 	
		case OPEN_TRACK_WII: 
			track.openTrack(getParam1(), getParam2());
			return;
		case RETURN: 
			track.returnToCallAddr();
			return;
		case TRACK_END:
			track.signalTrackEnd();
			return;
		default: return;
		}
	}
	
	public byte[] serializeEvent(boolean endian)
	{
		byte[] event = null;
		if(!this.hasFirstParameter())
		{
			event = new byte[1];
			event[0] = getCommand().getCommandByte();
		}
		else
		{
			NSCommand cmd = getCommand();
			switch(cmd)
			{
			case ALLOC_TRACK:
				event = new byte[3];
				event[0] = cmd.getCommandByte();
				if(endian)
				{
					event[1] = (byte)(super.getParam1() >>> 8);
					event[2] = (byte)(super.getParam1() & 0xFF);
				}
				else
				{
					event[1] = (byte)(super.getParam1() & 0xFF);
					event[2] = (byte)(super.getParam1() >>> 8);
				}
				break;
			case CALL_DS: 
			case CALL_WII:	
			case JUMP_DS:
			case JUMP_WII:	
				event = new byte[4];
				event[0] = cmd.getCommandByte();
				if(endian)
				{
					event[1] = (byte)(super.getParam1() >>> 16);
					event[2] = (byte)((super.getParam1() >>> 8) & 0xFF);
					event[3] = (byte)(super.getParam1() & 0xFF);
				}
				else
				{
					event[1] = (byte)(super.getParam1() & 0xFF);
					event[2] = (byte)((super.getParam1() >>> 8) & 0xFF);
					event[3] = (byte)(super.getParam1() >>> 16);
				}
				break;
			case OPEN_TRACK_DS:	
			case OPEN_TRACK_WII:
				event = new byte[5];
				event[0] = cmd.getCommandByte();
				event[1] = (byte)(super.getParam1() & 0xFF);
				if(endian)
				{
					event[2] = (byte)(super.getParam2() >>> 16);
					event[3] = (byte)((super.getParam2() >>> 8) & 0xFF);
					event[4] = (byte)(super.getParam2() & 0xFF);
				}
				else
				{
					event[4] = (byte)(super.getParam2() >>> 16);
					event[3] = (byte)((super.getParam2() >>> 8) & 0xFF);
					event[2] = (byte)(super.getParam2() & 0xFF);
				}
			default: break;
			}
		}
		
		return event;
	}
	
	public String toString()
	{
		NSCommand cmd = super.getCommand();
		switch(cmd)
		{
		case ALLOC_TRACK: return "Alloc Tracks: " + String.format("0x%04x", super.getParam1());
		case CALL_DS: return "Call (SSEQ): 0x" + Integer.toHexString(super.getParam1());
		case CALL_WII: return "Call (RSEQ): 0x" + Integer.toHexString(super.getParam1());	
		case ENV_RESET: return "Envelope Reset";
		case JUMP_DS:  return "Jump (SSEQ): 0x" + Integer.toHexString(super.getParam1());
		case JUMP_WII:  return "Jump (RSEQ): 0x" + Integer.toHexString(super.getParam1());
		case LOOP_END: return "Loop End";
		case OPEN_TRACK_DS: return "SSEQ Open Track " + super.getParam1() + ": 0x" + Integer.toHexString(super.getParam2());
		case OPEN_TRACK_WII: return "RSEQ Open Track " + super.getParam1() + ": 0x" + Integer.toHexString(super.getParam2());
		case RETURN: return "Return to Call Address";
		case TRACK_END: return "Track End";
		default: return "INVALID CTRL COMMAND";
		}
	}
	

}

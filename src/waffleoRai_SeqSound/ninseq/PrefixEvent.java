package waffleoRai_SeqSound.ninseq;

public class PrefixEvent extends NSEvent{
	
	private NSEvent subEvent;
	
	public PrefixEvent(NSCommand cmd, NSEvent sub)
	{
		this(cmd, sub, -1, -1);
	}
	
	public PrefixEvent(NSCommand cmd, NSEvent sub, int p1)
	{
		this(cmd, sub, p1, -1);
	}
	
	public PrefixEvent(NSCommand cmd, NSEvent sub, int p1, int p2)
	{
		super.setAddress(-1);
		super.setCommand(cmd);
		subEvent = sub;
		super.setParam1(p1);
		super.setParam2(p2);
		subEvent.setParam2(-1);
		int sz = calculateSize();
		super.setSerialSize(sz);
	}
	
	private int calculateSize()
	{
		NSCommand cmd = super.getCommand();
		int sz = 1; //0xan
		if(cmd == NSCommand.PREFIX_RANDOM)
		{
			if(subEvent.getCommand() == NSCommand.EX_COMMAND) sz++;
			if(subEvent.hasSecondParameter()) sz++;
			sz += 5; //SubCmd, Param 1, Param 2
		}
		else if(cmd == NSCommand.PREFIX_VARIABLE)
		{
			if(subEvent.getCommand() == NSCommand.EX_COMMAND) sz++;
			if(subEvent.hasSecondParameter()) sz++;
			sz+=2; //SubCmd, Param 1
		}
		else if(cmd == NSCommand.PREFIX_IF)
		{
			sz += subEvent.getSerialSize();
		}
		else if(cmd == NSCommand.PREFIX_TIME)
		{
			//Check if sub is also a prefix...
			if(subEvent instanceof PrefixEvent)
			{
				NSEvent subsub = ((PrefixEvent)subEvent).subEvent; sz+=1;
				sz += subsub.getSerialSize();
			}
			else sz += subEvent.getSerialSize();
			sz+=2; //Param 1
		}
		else if(cmd == NSCommand.PREFIX_TIME_RANDOM)
		{
			if(subEvent instanceof PrefixEvent)
			{
				NSEvent subsub = ((PrefixEvent)subEvent).subEvent; sz+=1;
				sz += subsub.getSerialSize();
			}
			else sz += subEvent.getSerialSize();
			sz+=4; //Param 1, Param 2
		}
		else if (cmd == NSCommand.PREFIX_TIME_VARIABLE)
		{
			if(subEvent instanceof PrefixEvent)
			{
				NSEvent subsub = ((PrefixEvent)subEvent).subEvent; sz+=1;
				sz += subsub.getSerialSize();
			}
			else sz += subEvent.getSerialSize();
			sz+=1; //Param 1
		}
		return sz;
	}
	
	public NSEvent getSubEvent(){return subEvent;}
	
	public boolean hasFirstParameter(){return false;}
	public boolean hasSecondParameter(){return false;}
	
	public void execute(NinTrack track)
	{
		NSCommand cmd = super.getCommand();
		switch(cmd)
		{
		case PREFIX_RANDOM: 
			track.executeWithRandom(subEvent);
			return;
		case PREFIX_VARIABLE: 
			track.executeWithVariable(getParam1(), subEvent);
			return;
		case PREFIX_IF: 
			track.executeIf(subEvent);
			return;
		case PREFIX_TIME: 
			//TODO
			return;
		case PREFIX_TIME_RANDOM: 
			//TODO
			return;
		case PREFIX_TIME_VARIABLE: 
			//TODO
			return;
		default: break;
		}
	}
	
	public byte[] serializeEvent(boolean endian)
	{
		NSCommand cmd = getCommand();
		
		switch(cmd)
		{
		case PREFIX_RANDOM: return serialize_random(endian);
		case PREFIX_VARIABLE: return serialize_variable();
		case PREFIX_IF: return serialize_if(endian);
		case PREFIX_TIME: return serialize_time(endian);
		case PREFIX_TIME_RANDOM: return serialize_timerandom(endian);
		case PREFIX_TIME_VARIABLE: return serialize_timevariable(endian);
		default: break;
		}
		return null;
	}
	
	private byte[] serialize_random(boolean endian)
	{
		boolean extra = (subEvent instanceof StateEvent) && (subEvent.getCommand() == NSCommand.EX_COMMAND);
		int sz = 0;
		if(extra) sz++;
		if(subEvent.hasSecondParameter()) sz++;
		byte[] event = null;
		
		event = new byte[6 + sz];
		event[0] = this.getCommand().getCommandByte();
		event[1] = subEvent.getCommand().getCommandByte();
		int i = 2;
		if(extra)
		{
			event[i] = ((StateEvent)subEvent).getExCommand().getCommandByte(); i++;
		}
		if(subEvent.hasSecondParameter()){event[i] = (byte)subEvent.getParam1(); i++;}
		
		if(endian)
		{
			event[i] = (byte)(super.getParam1() >>> 8); i++;
			event[i] = (byte)(super.getParam1() & 0xFF); i++;
			event[i] = (byte)(super.getParam2() >>> 8); i++;
			event[i] = (byte)(super.getParam2() & 0xFF); i++;
		}
		else
		{
			event[i] = (byte)(super.getParam1() & 0xFF); i++;
			event[i] = (byte)(super.getParam1() >>> 8); i++;
			event[i] = (byte)(super.getParam2() & 0xFF); i++;
			event[i] = (byte)(super.getParam2() >>> 8); i++;
		}
		
		return event;
	}
	
	private byte[] serialize_variable()
	{
		boolean extra = (subEvent instanceof StateEvent) && (subEvent.getCommand() == NSCommand.EX_COMMAND);
		byte[] event = null;
		
		event = new byte[getSerialSize()];
		event[0] = getCommand().getCommandByte();
		event[1] = subEvent.getCommand().getCommandByte();
		int i = 2;
		if(extra)
		{
			event[i] = ((StateEvent)subEvent).getExCommand().getCommandByte(); i++;
		}
		if(subEvent.hasSecondParameter()){event[i] = (byte)subEvent.getParam1(); i++;}
		event[i] = (byte)(super.getParam1() & 0xFF); i++;
		
		return event;
	}
	
	private byte[] serialize_if(boolean endian)
	{
		byte[] event = new byte[getSerialSize()];
		byte[] subser = subEvent.serializeEvent(endian);
		event = new byte[1 + subser.length];
		event[0] = getCommand().getCommandByte();
		for(int k = 0; k < subser.length; k++) event[k+1] = subser[k];
		
		return event;
	}
	
	private byte[] serialize_time(boolean endian)
	{
		byte[] event = new byte[getSerialSize()];
		boolean inner = (subEvent instanceof PrefixEvent);
		NSEvent sub = subEvent;
		if(inner) sub = ((PrefixEvent)subEvent).subEvent;
		byte[] subser = sub.serializeEvent(endian);
		
		event[0] = getCommand().getCommandByte();
		int i = 1;
		if(inner){event[1] = subEvent.getCommand().getCommandByte(); i++;}
		for(int j = 0; j < subser.length; j++)
		{
			event[i] = subser[j];
			i++;
		}
		
		if(endian)
		{
			event[i] = (byte)((getParam1() >>> 8) & 0xFF); i++;
			event[i] = (byte)(getParam1() & 0xFF); i++;
		}
		else
		{
			event[i] = (byte)(getParam1() & 0xFF); i++;
			event[i] = (byte)((getParam1() >>> 8) & 0xFF); i++;
		}
		
		
		return event;
	}
	
	private byte[] serialize_timerandom(boolean endian)
	{
		byte[] event = new byte[getSerialSize()];
		boolean inner = (subEvent instanceof PrefixEvent);
		NSEvent sub = subEvent;
		if(inner) sub = ((PrefixEvent)subEvent).subEvent;
		byte[] subser = sub.serializeEvent(endian);
		
		event[0] = getCommand().getCommandByte();
		int i = 1;
		if(inner){event[1] = subEvent.getCommand().getCommandByte(); i++;}
		for(int j = 0; j < subser.length; j++)
		{
			event[i] = subser[j];
			i++;
		}
		
		if(endian)
		{
			event[i] = (byte)(super.getParam1() >>> 8); i++;
			event[i] = (byte)(super.getParam1() & 0xFF); i++;
			event[i] = (byte)(super.getParam2() >>> 8); i++;
			event[i] = (byte)(super.getParam2() & 0xFF); i++;
		}
		else
		{
			event[i] = (byte)(super.getParam1() & 0xFF); i++;
			event[i] = (byte)(super.getParam1() >>> 8); i++;
			event[i] = (byte)(super.getParam2() & 0xFF); i++;
			event[i] = (byte)(super.getParam2() >>> 8); i++;
		}
		
		return event;
	}
	
	private byte[] serialize_timevariable(boolean endian)
	{
		byte[] event = new byte[getSerialSize()];
		boolean inner = (subEvent instanceof PrefixEvent);
		NSEvent sub = subEvent;
		if(inner) sub = ((PrefixEvent)subEvent).subEvent;
		byte[] subser = sub.serializeEvent(endian);
		
		event[0] = getCommand().getCommandByte();
		int i = 1;
		if(inner){event[1] = subEvent.getCommand().getCommandByte(); i++;}
		for(int j = 0; j < subser.length; j++)
		{
			event[i] = subser[j];
			i++;
		}
		
		event[i] = (byte)(getParam1() & 0xFF); i++;
		
		return event;
	}
	
	public String toString()
	{
		NSCommand cmd = super.getCommand();
		if(cmd == NSCommand.PREFIX_RANDOM)
		{
			if(subEvent.hasSecondParameter())
			{
				String s = "Execute ";
				s += subEvent.getCommand().name();
				s += " w/ p1 set to 0x" + Integer.toHexString(subEvent.getParam1());
				s += " and p2 set to random between 0x";
				s += Integer.toHexString(super.getParam1());
				s += " and 0x";
				s += Integer.toHexString(super.getParam2());
				return s;
			}
			else
			{
				String s = "Execute ";
				s += subEvent.getCommand().name();
				s += " w/ param set to random between 0x";
				s += Integer.toHexString(super.getParam1());
				s += " and 0x";
				s += Integer.toHexString(super.getParam2());
				return s;
			}
		}
		else if(cmd == NSCommand.PREFIX_VARIABLE)
		{
			if(subEvent.hasSecondParameter())
			{
				String s = "Execute ";
				s += subEvent.getCommand().name();
				s += " w/ p1 set to 0x" + Integer.toHexString(subEvent.getParam1());
				s += " and p2 set to variable $";
				s += super.getParam1();
				return s;
			}
			else
			{
				String s = "Execute ";
				s += subEvent.getCommand().name();
				s += " w/ param set to variable $";
				s += super.getParam1();
				return s;
			}
		}
		else if(cmd == NSCommand.PREFIX_IF)
		{
			String s = "If track flag set -- ";
			s += subEvent.toString();
			return s;
		}
		return "INVALID PREFIX COMMAND";
	}
	

}

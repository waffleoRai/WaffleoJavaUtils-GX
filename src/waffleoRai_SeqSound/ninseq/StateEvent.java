package waffleoRai_SeqSound.ninseq;

public class StateEvent extends NSEvent{

	private NSExtendedCommand subCommand; //Wii Only
	
	public StateEvent(NSCommand cmd, int reg, int imm)
	{
		this(cmd, null, reg, imm);
		super.setSerialSize(4);
	}
	
	public StateEvent(NSCommand cmd, NSExtendedCommand ex, int reg, int imm)
	{
		super.setCommand(cmd);
		subCommand = ex;
		super.setParam1(reg);
		super.setParam2(imm);
		super.setSerialSize(5);
	}
	
	public NSExtendedCommand getExCommand(){return subCommand;}
	
	@Override
	public boolean hasFirstParameter() {return true;}

	@Override
	public boolean hasSecondParameter() {return true;}

	public void execute(NinTrack track)
	{
		NSCommand cmd = super.getCommand();
		switch(cmd)
		{
		case ADD_VAR_DS: track.addImmediate(getParam1(), (short)getParam2()); return;
		case CMP_EQ_DS: track.setIfEqual(getParam1(), (short)getParam2()); return;
		case CMP_GE_DS: track.setIfGreaterOrEqual(getParam1(), (short)getParam2()); return;
		case CMP_GT_DS: track.setIfGreaterThan(getParam1(), (short)getParam2()); return;
		case CMP_LE_DS: track.setIfLessOrEqual(getParam1(), (short)getParam2()); return;
		case CMP_LT_DS: track.setIfLessThan(getParam1(), (short)getParam2()); return;
		case CMP_NE_DS: track.setIfNotEqual(getParam1(), (short)getParam2()); return;
		case DIV_VAR_DS: track.divideImmediate(getParam1(), (short)getParam2()); return;
		case EX_COMMAND:
			switch(subCommand)
			{
			case ADD_VAR: track.addImmediate(getParam1(), (short)getParam2()); return;
			case AND_VAR: track.andImmediate(getParam1(), getParam2()); return;
			case CMP_EQ: track.setIfEqual(getParam1(), (short)getParam2()); return;
			case CMP_GE: track.setIfGreaterOrEqual(getParam1(), (short)getParam2()); return;
			case CMP_GT: track.setIfGreaterThan(getParam1(), (short)getParam2()); return;
			case CMP_LE: track.setIfLessOrEqual(getParam1(), (short)getParam2()); return;
			case CMP_LT: track.setIfLessThan(getParam1(), (short)getParam2()); return;
			case CMP_NE: track.setIfNotEqual(getParam1(), (short)getParam2()); return;
			case DIV_VAR: track.divideImmediate(getParam1(), (short)getParam2()); return;
			case MOD_VAR: track.modImmediate(getParam1(), (short)getParam2()); return;
			case MUL_VAR: track.multiplyImmediate(getParam1(), (short)getParam2()); return;
			case NOT_VAR: track.notImmediate(getParam1(), getParam2()); return;
			case OR_VAR: track.orImmediate(getParam1(), getParam2()); return;
			case RAND_VAR: track.setPlayerVariableToRandom(getParam1()); return;
			case SET_VAR: track.setPlayerVariable(getParam1(), (short)getParam2()); return;
			case SHIFT_VAR: track.shiftImmediate(getParam1(), getParam2()); return;
			case SUB_VAR: track.subtractImmediate(getParam1(), (short)getParam2()); return;
			case USER_PROC: return;
			case XOR_VAR: track.xorImmediate(getParam1(), getParam2()); return;
			default: return;
			}
		case MUL_VAR_DS: track.multiplyImmediate(getParam1(), (short)getParam2()); return;
		case RAND_VAR_DS: track.setPlayerVariableToRandom(getParam1()); return;
		case SET_VAR_DS: track.setPlayerVariable(getParam1(), (short)getParam2()); return;
		case SHIFT_VAR_DS: track.shiftImmediate(getParam1(), getParam2()); return;
		case SUB_VAR_DS: track.subtractImmediate(getParam1(), (short)getParam2()); return;
		default: return;
		}
	}
	
	public byte[] serializeEvent(boolean endian)
	{
		int sz = 4;
		if(subCommand != null) sz++;
		
		int i = 0;
		byte[] event = new byte[sz];
		event[i] = this.getCommand().getCommandByte(); i++;
		if(subCommand != null){event[i] = subCommand.getCommandByte(); i++;}
		event[i] = (byte)super.getParam1(); i++;
		if(endian)
		{
			event[i] = (byte)(getParam2() >>> 8); i++;
			event[i] = (byte)(getParam2() & 0xFF);
		}
		else
		{
			event[i] = (byte)(getParam2() & 0xFF); i++;
			event[i] = (byte)(getParam2() >>> 8); 
		}
		
		return event;
	}
	
	public String toString()
	{
		NSCommand cmd = super.getCommand();
		switch(cmd)
		{
		case ADD_VAR_DS: return "Add " + super.getParam2() + " to variable $" + super.getParam1();
		case CMP_EQ_DS: return "Set track flag if variable $" + getParam1() + " == " + getParam2();
		case CMP_GE_DS: return "Set track flag if variable $" + getParam1() + " >= " + getParam2();
		case CMP_GT_DS: return "Set track flag if variable $" + getParam1() + " > " + getParam2();
		case CMP_LE_DS: return "Set track flag if variable $" + getParam1() + " <= " + getParam2();
		case CMP_LT_DS: return "Set track flag if variable $" + getParam1() + " < " + getParam2();
		case CMP_NE_DS: return "Set track flag if variable $" + getParam1() + " != " + getParam2();
		case DIV_VAR_DS: return "Divide varible $" + getParam1() + " by " + getParam2();
		case EX_COMMAND:
			switch(subCommand)
			{
			case ADD_VAR: return "Add " + getParam2() + " to variable $" + getParam1();
			case AND_VAR: return getParam2() + " AND $" + getParam1();
			case CMP_EQ: return "Set track flag if variable $" + getParam1() + " == " + getParam2();
			case CMP_GE: return "Set track flag if variable $" + getParam1() + " >= " + getParam2();
			case CMP_GT: return "Set track flag if variable $" + getParam1() + " > " + getParam2();
			case CMP_LE: return "Set track flag if variable $" + getParam1() + " <= " + getParam2();
			case CMP_LT: return "Set track flag if variable $" + getParam1() + " < " + getParam2();
			case CMP_NE: return "Set track flag if variable $" + getParam1() + " != " + getParam2();
			case DIV_VAR: return "Divide varible $" + getParam1() + " by " + getParam2();
			case MOD_VAR: return "Modulus of variable $" + getParam1() + " and " + getParam2();
			case MUL_VAR: return "Multiply " + getParam2() + " with variable $" + getParam1();
			case NOT_VAR: return "$" + getParam1() + " = ~" + getParam2();
			case OR_VAR: return getParam2() + " OR $" + getParam1();
			case RAND_VAR: return "Set variable $" + getParam1() + " to random up to " + getParam2();
			case SET_VAR: return "Set variable $" + getParam1() + " to " + getParam2();
			case SHIFT_VAR: return "Shift variable $" + getParam1() + " " + getParam2() + " bits";
			case SUB_VAR: return "Subtract " + getParam2() + " from variable $" + getParam1();
			case USER_PROC: return "[User function] " + getParam2() + " with variable $" + getParam1();
			case XOR_VAR: return getParam2() + " XOR $" + getParam1();
			default: return "INVALID STATE COMMAND";
			}
		case MUL_VAR_DS: return "Multiply " + getParam2() + " with variable $" + getParam1();
		case RAND_VAR_DS: return "Set variable $" + getParam1() + " to random up to " + getParam2();
		case SET_VAR_DS: return "Set variable $" + getParam1() + " to " + getParam2();
		case SHIFT_VAR_DS: return "Shift variable $" + getParam1() + " " + getParam2() + " bits";
		case SUB_VAR_DS: return "Subtract " + getParam2() + " from variable $" + getParam1();
		default: return "INVALID STATE COMMAND";
		}

	}
	
}

package waffleoRai_SeqSound.jaiseq;

import waffleoRai_SeqSound.MIDI;
import waffleoRai_SeqSound.jaiseq.cmd.EJaiseqCmd;

public abstract class JaiseqCommand {
	
	/*----- Constants -----*/
	
	/*----- Instance Variables -----*/
	
	protected EJaiseqCmd cmd_enum;
	protected int[] args;
	
	protected int address;
	protected int track = -1;
	protected boolean flag = false;
	
	protected JaiseqCommand ref;
	protected JaiseqCommand next;
	protected String label;
	
	/*----- Init -----*/
	
	protected JaiseqCommand(EJaiseqCmd c_enum){
		if(c_enum == null) throw new IllegalArgumentException("JaiseqCommand.<init> || Need non-null command enum!");
		cmd_enum = c_enum;
		int acount = cmd_enum.getSerialType().getArgCount();
		if(acount > 0) args = new int[acount];
	}

	/*----- Getters -----*/
	
	public EJaiseqCmd getCommandEnum(){return cmd_enum;}
	
	public int getArg(int index){
		if(args == null || index < 0) return 0;
		if(index >= args.length) return 0;
		return args[index];
	}
	
	public int getSize(){
		int ssize = cmd_enum.getSerialType().getSerSize();
		if(ssize > 0) return ssize;
		if(ssize == 0){
			//VLQ
			return 1 + MIDI.VLQlength(args[0]);
		}
		return 0;
	}
	
	public int getAddress(){return address;}
	public JaiseqCommand getReferencedCommand(){return ref;}
	public JaiseqCommand getNextCommand(){return next;}
	public String getLabel(){return label;}
	public int getTrack(){return track;}
	
	/*----- Setters -----*/
	
	public void setArg(int index, int value){
		if(args == null || index < 0) return;
		if(index >= args.length) return;
		args[index] = value;
	}
	
	public void setAddress(int val){address = val;}
	public void setReferencedCommand(JaiseqCommand cmd){ref = cmd;}
	public void setNextCommand(JaiseqCommand cmd){next = cmd;}
	public void setLabel(String str){label = str;}
	public void setTrack(int val){track = val;}
	
	/*----- Abstract Methods -----*/
	
	public abstract boolean doAction(Jaiseq seq);
	public abstract boolean doAction(JaiseqTrack track);
	
	/*----- Serialize -----*/
	
	public byte[] serializeMe(){
		byte[] out = null;
		switch(cmd_enum.getSerialType()){
		case SERIALFMT_:
			out = new byte[]{cmd_enum.getStatusByte()};
			break;
		case SERIALFMT_1:
			out = new byte[]{cmd_enum.getStatusByte(), (byte)args[0]};
			break;
		case SERIALFMT_11:
			out = new byte[]{cmd_enum.getStatusByte(), 
					(byte)args[0],
					(byte)args[1]};
			break;
		case SERIALFMT_111:
			out = new byte[]{cmd_enum.getStatusByte(), 
					(byte)args[0],
					(byte)args[1],
					(byte)args[2]};
			break;
		case SERIALFMT_112:
			out = new byte[]{cmd_enum.getStatusByte(), 
					(byte)args[0],
					(byte)args[1],
					0, 0};
			out[3] = (byte)((args[2] >>> 8) & 0xff);
			out[4] = (byte)(args[2] & 0xff);
			break;
		case SERIALFMT_12:
			out = new byte[]{cmd_enum.getStatusByte(), 
					(byte)args[0],
					0, 0};
			out[2] = (byte)((args[1] >>> 8) & 0xff);
			out[3] = (byte)(args[1] & 0xff);
			break;
		case SERIALFMT_121:
			out = new byte[]{cmd_enum.getStatusByte(), 
					(byte)args[0],
					0, 0,
					(byte)args[2]};
			out[2] = (byte)((args[1] >>> 8) & 0xff);
			out[3] = (byte)(args[1] & 0xff);
			break;
		case SERIALFMT_122:
			out = new byte[]{cmd_enum.getStatusByte(), 
					(byte)args[0],
					0, 0, 0, 0};
			out[2] = (byte)((args[1] >>> 8) & 0xff);
			out[3] = (byte)(args[1] & 0xff);
			out[4] = (byte)((args[2] >>> 8) & 0xff);
			out[5] = (byte)(args[2] & 0xff);
			break;
		case SERIALFMT_13:
			out = new byte[]{cmd_enum.getStatusByte(), 
					(byte)args[0],
					0, 0, 0};
			out[2] = (byte)((args[1] >>> 16) & 0xff);
			out[3] = (byte)((args[1] >>> 8) & 0xff);
			out[4] = (byte)(args[1] & 0xff);
			break;
		case SERIALFMT_2:
			out = new byte[]{cmd_enum.getStatusByte(), 
					0,0};
			out[1] = (byte)((args[0] >>> 8) & 0xff);
			out[2] = (byte)(args[0] & 0xff);
			break;
		case SERIALFMT_3:
			out = new byte[]{cmd_enum.getStatusByte(), 
					0, 0, 0};
			out[1] = (byte)((args[0] >>> 16) & 0xff);
			out[2] = (byte)((args[0] >>> 8) & 0xff);
			out[3] = (byte)(args[0] & 0xff);
			break;
		case SERIALFMT_L:
			int stat = Byte.toUnsignedInt(cmd_enum.getStatusByte());
			stat &= 0xf0;
			stat |= (args[0] & 0x0f);
			out = new byte[]{(byte)stat};
			break;
		case SERIALFMT_NOTE:
			out = new byte[]{(byte)args[0], (byte)args[1], (byte)args[2]};
			break;
		case SERIALFMT_V:
			byte[] vlq = MIDI.makeVLQ(args[0]);
			out = new byte[vlq.length+1];
			out[0] = cmd_enum.getStatusByte();
			for(int i = 0; i < vlq.length; i++){
				out[i+1] = vlq[i];
			}
			break;
		case SERIALFMT_DUMMY:
		default: 
			break;
		}
		return out;
	}
	
	public String toMML(){
		return toString();
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder(256);
		sb.append(cmd_enum.toString());
		if(args != null){
			for(int i = 0; i < args.length; i++){
				if(i > 0) sb.append(",");
				sb.append(' ');
				//sb.append(Integer.toHexString(args[i]));
				sb.append(args[i]);
			}
		}
		return sb.toString();
	}
	
}

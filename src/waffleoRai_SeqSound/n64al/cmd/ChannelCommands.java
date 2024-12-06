package waffleoRai_SeqSound.n64al.cmd;

import waffleoRai_SeqSound.n64al.NUSALSeq;
import waffleoRai_SeqSound.n64al.NUSALSeqChannel;
import waffleoRai_SeqSound.n64al.NUSALSeqCmdType;
import waffleoRai_SeqSound.n64al.NUSALSeqCommand;
import waffleoRai_SeqSound.n64al.NUSALSeqCommandBook;
import waffleoRai_SeqSound.n64al.cmd.FCommands.*;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.StringUtils;

public class ChannelCommands {
	
	/*--- Parser ---*/
	
	public static NUSALSeqCommand parseChannelCommandOld(BufferReference dat){
		int cmdi = Byte.toUnsignedInt(dat.getByte());
		int cmdhi = (cmdi & 0xf0) >>> 4;
		int cmdlo = cmdi & 0xf;
		
		int i,j,k;
		switch(cmdhi){
		case 0x0: return new C_C_DeltaTime(cmdlo);
		case 0x1:
			if(cmdlo < 0x8) return new C_C_LoadSample(cmdlo);
			else{
				cmdlo -= 8;
				return new C_C_LoadSampleP(cmdlo);
			}
		case 0x2:
			i = Short.toUnsignedInt(dat.getShort(1));
			return new C_C_StartChannel(cmdlo, i);
		case 0x3:
			i = Byte.toUnsignedInt(dat.getByte(1));
			return new C_C_StoreChIO(cmdlo, i);
		case 0x4:
			i = Byte.toUnsignedInt(dat.getByte(1));
			return new C_C_LoadChIO(cmdlo, i);
		case 0x5: return new C_C_SubIO(cmdlo);
		case 0x6: return new C_C_LoadIO(cmdlo);
		case 0x7:
			if(cmdlo < 0x8) return new C_C_StoreIO(cmdlo);
			else{
				cmdlo -= 8;
				i = (int)dat.getShort(1);
				return new C_C_StartLayerRel(cmdlo, i);
			}
		case 0x8:
			if(cmdlo < 0x8) return new C_C_TestLayer(cmdlo);
			else{
				cmdlo -= 8;
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_C_StartLayer(cmdlo, i);
			}
		case 0x9:{
			if(cmdlo < 0x8) return new C_C_StopLayer(cmdlo);
			return new C_C_StartLayerTable(cmdlo-8);
		}
	/* 0xbn */		
		case 0xb:
			switch(cmdlo){
			case 0x0:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_C_SetFilter(i);
			case 0x1: return new C_C_ClearFilter();
			case 0x2:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_C_LoadPFromTable(i);
			case 0x3:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_CopyFilter(i);
			case 0x4: return new C_C_P2DynTable();
			case 0x5: return new C_C_DynTable2P();
			case 0x6: return new C_C_DynTable2Q();
			case 0x7:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_RandomP(i);
			case 0x8:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_RandomQ(i);
			case 0x9:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_SetVelocityRand(i);
			case 0xa:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_SetGateRand(i);
			case 0xb:
				i = (int)dat.getByte(1);
				j = (int)dat.getShort(2);
				final int fi = i; final int fj = j;
				return new CMD_IgnoredCommand(NUSALSeqCmdType.CHORUS){
					protected void onInit(){
						setParam(0, fi); setParam(1,fj);
					}
				};
			case 0xc:
				i = (int)dat.getShort(1);
				return new C_C_AddPImmediate(i);
			case 0xd:
				i = Byte.toUnsignedInt(dat.getByte(1));
				j = (int)dat.getShort(2);
				return new C_C_AddPRandom(i,j);
			}
			break;
	/* 0xcn */
		case 0xc:
			switch(cmdlo){
			case 0x0:
				i = (int)dat.getByte(1);
				final int fi = i;
				return new CMD_IgnoredCommand(NUSALSeqCmdType.C_UNK_C0){
					protected void onInit(){
						setParam(0, fi);;
					}
				};
			case 0x1:
				i = (int)dat.getByte(1);
				return new C_C_ChangeProgram(i);
			case 0x2:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_C_SetDynTable(i);
			case 0x3: return new C_C_SetShortNotesOn();
			case 0x4: return new C_C_SetShortNotesOff();
			case 0x5: return new C_C_ShiftDynTable();
			case 0x6:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_ChangeBank(i);
			case 0x7:
				i = (int)dat.getByte(1);
				j = Short.toUnsignedInt(dat.getShort(2));
				return new C_C_StoreToSelf(i,j);
			case 0x8:
				i = (int)dat.getByte(1);
				return new C_C_SubtractImm(i);
			case 0x9:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_AndImm(i);
			case 0xa:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_MuteBehavior(i);
			case 0xb:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_C_LoadFromSelf(i);
			case 0xc:
				i = (int)dat.getByte(1);
				return new C_C_LoadImm(i);
			case 0xd:
				i = (int)dat.getByte(1);
				return new C_C_StopChannel(i);
			case 0xe:
				i = Short.toUnsignedInt(dat.getShort(1));
				//return new C_C_LoadPImm(i);
			case 0xf:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_C_StorePToSelf(i);
			}
			break;
	/* 0xdn */
		case 0xd:
			switch(cmdlo){
			case 0x0:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_StereoEffects(i);
			case 0x1:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_NoteAllocPolicy(i);
			case 0x2:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_Sustain(i);
			case 0x3:
				i = (int)dat.getByte(1);
				return new C_C_PitchBend(i);
			case 0x4:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_Reverb(i);
			case 0x7:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_VibratoFreq(i);
			case 0x8:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_VibratoDepth(i);
			case 0x9:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_Release(i);
			case 0xa:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_C_Envelope(i);
			case 0xb:
				i = (int)dat.getByte(1);
				return new C_C_Transpose(i);
			case 0xc:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_PanMix(i);
			case 0xd:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_Pan(i);
			case 0xe:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_C_FreqScale(i);
			case 0xf:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_Volume(i);
			}
			break;
	/* 0xen */
		case 0xe:
			switch(cmdlo){
			case 0x0:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_Expression(i);
			case 0x1:
				i = Byte.toUnsignedInt(dat.getByte(1));
				j = Byte.toUnsignedInt(dat.getByte(2));
				k = Byte.toUnsignedInt(dat.getByte(3));
				return new C_C_VibFreqEnvelope(i,j,k);
			case 0x2:
				i = Byte.toUnsignedInt(dat.getByte(1));
				j = Byte.toUnsignedInt(dat.getByte(2));
				k = Byte.toUnsignedInt(dat.getByte(3));
				return new C_C_VibDepthEnvelope(i,j,k);
			case 0x3:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_VibratoDelay(i);
			case 0x4:
				return new C_C_DynCall();
			case 0x5:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_ReverbIndex(i);
			case 0x6:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_SampleVariation(i);
			case 0x7:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_C_LoadChannelParams(i);
			case 0x8:
				//This one takes 8 parameters...
				int[] arr = new int[8];
				for(int n = 0; n < 8; n++){
					arr[n] = (int)dat.getByte(n+1);
				}
				return new C_C_SetChannelParams(arr);
			case 0x9:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_Priority(i);
			case 0xa: return new C_C_Halt();
			case 0xb:
				i = Byte.toUnsignedInt(dat.getByte(1));
				j = Byte.toUnsignedInt(dat.getByte(2));
				return new C_C_SetBankProgram(i,j);
			case 0xc: return new C_C_ResetChannel();
			case 0xd:
				i = Byte.toUnsignedInt(dat.getByte(1));
				return new C_C_FilterGain(i);
			case 0xe:
				i = (int)dat.getByte(1);
				return new C_C_PitchBendAlt(i);
			}
			break;
	/* 0xfn */
		case 0xf:
			switch(cmdlo){
			case 0x0: return new C_UnreserveNotes();
			case 0x1:
				i = (int)dat.getByte(1);
				return new C_ReserveNotes(i);
			case 0x2:
				i = (int)dat.getByte(1);
				return new C_rbltz(i);
			case 0x3:
				i = (int)dat.getByte(1);
				return new C_rbeqz(i);
			case 0x4:
				i = (int)dat.getByte(1);
				return new C_rjump(i);
			case 0x5:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_bgez(i);
			case 0x6: return new C_Break();
			case 0x7: return new C_LoopEnd();
			case 0x8:
				i = (int)dat.getByte(1);
				return new C_LoopStart(i);
			case 0x9:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_bltz(i);
			case 0xa:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_beqz(i);
			case 0xb:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_Jump(i);
			case 0xc:
				i = Short.toUnsignedInt(dat.getShort(1));
				return new C_Call(i);
			case 0xd:
				dat.increment();
				i = NUSALSeqReader.readVLQ(dat);
				dat.decrement();
				return new C_Wait(i);
			case 0xe: return new C_Yield();
			case 0xf: return new C_EndRead();
			}
			break;
		}
		
		return null;
	}
	
	public static NUSALSeqCommand parseChannelCommandOld(String cmd, String[] args){
		NUSALSeqCommand command = FCommands.parseFCommandOld(cmd, args);
		if(command != null) return command;
		
		/*
		 * The following commands are not currently supported:
		 * 	dynstartlayer
		 * 	setfilter
		 * 	clearfilter
		 * 	ldptbl
		 * 	copyfilter
		 * 	p2dyntable
		 * 	dyntable2p
		 * 	lddyn
		 * 	unk_BB
		 * 	dyntable
		 * 	dynsetdyntable
		 * 	sts
		 * 	lds
		 * 	stps
		 * 	cenvelope
		 * 	dyncall
		 * 	ldcparams
		 */
		
		//References resolved by caller.
		cmd = cmd.toLowerCase();
		if(cmd.equals("cdelay")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_DeltaTime(n);
		}
		else if(cmd.equals("startlayer")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_StartLayer(n,-1);
		}
		else if(cmd.equals("rstartlayer")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_StartLayerRel(n,-1);
		}
		else if(cmd.equals("cpan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_Pan(n);
		}
		else if(cmd.equals("cvol")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_Volume(n);
		}
		else if(cmd.equals("instr")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_ChangeProgram(n);
		}
		else if(cmd.equals("pitchbend")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_PitchBend(n);
		}
		else if(cmd.equals("reverb")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_Reverb(n);
		}
		else if(cmd.equals("ctp")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_Transpose(n);
		}
		else if(cmd.equals("shortoff")){return new C_C_SetShortNotesOff();}
		else if(cmd.equals("shorton")){return new C_C_SetShortNotesOn();}
		else if(cmd.equals("mutebhv")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_MuteBehavior(n);
		}
		else if(cmd.equals("notepriority")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_Priority(n);
		}
		else if(cmd.equals("ldi")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_LoadImm(n);
		}
		else if(cmd.equals("sub")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_SubtractImm(n);
		}
		else if(cmd.equals("and")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_AndImm(n);
		}
		else if(cmd.equals("stio")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_StoreIO(n);
		}
		else if(cmd.equals("ldio")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_LoadIO(n);
		}
		else if(cmd.equals("subio")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_SubIO(n);
		}
		else if(cmd.equals("stcio")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_C_StoreChIO(n[0],n[1]);
		}
		else if(cmd.equals("ldcio")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_C_LoadChIO(n[0],n[1]);
		}
		else if(cmd.equals("bankinstr")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_C_SetBankProgram(n[0],n[1]);
		}
		else if(cmd.equals("bank")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_ChangeBank(n);
		}
		else if(cmd.equals("addp")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_AddPImmediate(n);
		}
		else if(cmd.equals("ldpi")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			//return new C_C_LoadPImm(n);
		}
		else if(cmd.equals("rand")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_RandomQ(n);
		}
		else if(cmd.equals("randp")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_RandomP(n);
		}
		else if(cmd.equals("randaddp")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_C_AddPRandom(n[0],n[1]);
		}
		else if(cmd.equals("vibfreq")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_VibratoFreq(n);
		}
		else if(cmd.equals("vibdepth")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_VibratoDepth(n);
		}
		else if(cmd.equals("vibdelay")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_VibratoDelay(n);
		}
		else if(cmd.equals("sustain")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_Sustain(n);
		}
		else if(cmd.equals("release")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_Release(n);
		}
		else if(cmd.equals("velrand")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_SetVelocityRand(n);
		}
		else if(cmd.equals("gaterand")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_SetGateRand(n);
		}
		else if(cmd.equals("panmix")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_PanMix(n);
		}
		else if(cmd.equals("cexp")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_Expression(n);
		}
		else if(cmd.equals("freqscale")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_FreqScale(n);
		}
		else if(cmd.equals("filgain")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_FilterGain(n);
		}
		else if(cmd.equals("reverbidx")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_ReverbIndex(n);
		}
		else if(cmd.equals("cbend2")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_PitchBendAlt(n);
		}
		else if(cmd.equals("testlayer")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_TestLayer(n);
		}
		else if(cmd.equals("stoplayer")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_StopLayer(n);
		}
		else if(cmd.equals("vibfreqenv")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_C_VibFreqEnvelope(n[0],n[1],n[2]);
		}
		else if(cmd.equals("vibdepthenv")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_C_VibDepthEnvelope(n[0],n[1],n[2]);
		}
		else if(cmd.equals("loadspl")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_LoadSample(n);
		}
		else if(cmd.equals("loadsplp")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_LoadSampleP(n);
		}
		else if(cmd.equals("startchan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_StartChannel(n, -1);
		}
		else if(cmd.equals("stopchan")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_StopChannel(n);
		}
		else if(cmd.equals("chanreset")){return new C_C_ResetChannel();}
		else if(cmd.equals("halt")){return new C_C_Halt();}
		else if(cmd.equals("splvari")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_SampleVariation(n);
		}
		else if(cmd.equals("stereoheadseteffects")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_StereoEffects(n);
		}
		else if(cmd.equals("noteallocpolicy")){
			Integer n = NUSALSeqReader.readNumber(args[0]);
			if(n == null) return null;
			return new C_C_NoteAllocPolicy(n);
		}
		else if(cmd.equals("cparams")){
			int[] n = NUSALSeqReader.readNumbers(args);
			if(n == null) return null;
			return new C_C_SetChannelParams(n);
		}
		
		return null;
	}
	
	public static NUSALSeqCommand parseChannelCommand(BufferReference dat, NUSALSeqCommandBook book){
		int[] params = new int[8];
		int bb = Byte.toUnsignedInt(dat.nextByte());
		NUSALSeqCommandDef def = book.getChannelCommand((byte)bb);
		if(def == null) return null;
		NUSALSeqCommand.readBinArgs(params, dat, def, bb);
		switch(def.getFunctionalType()) {
		case CH_DELTA_TIME: return new C_C_DeltaTime(def, params[0]);
		case LOAD_SAMPLE: return new C_C_LoadSample(def, params[0]);
		case LOAD_SAMPLE_P: return new C_C_LoadSampleP(def, params[0]);
		case CHANNEL_OFFSET_C: return new C_C_StartChannel(def, params[0], params[1]);
		case STORE_CHIO: return new C_C_StoreChIO(def, params[0], params[1]);
		case LOAD_CHIO: return new C_C_LoadChIO(def, params[0], params[1]);
		case SUBTRACT_IO_C: return new C_C_SubIO(def, params[0]);
		case LOAD_IO_C: return new C_C_LoadIO(def, params[0]);
		case STORE_IO_C: return new C_C_StoreIO(def, params[0]);
		//case VOICE_OFFSET_REL: return new C_C_StartLayerRel(def, params[0], (int)((short)params[1]));
		case VOICE_OFFSET_REL: return new C_C_StartLayerRel(def, params[0], params[1]);
		case TEST_VOICE: return new C_C_TestLayer(def, params[0]);
		case VOICE_OFFSET: return new C_C_StartLayer(def, params[0], params[1]);
		case STOP_VOICE: return new C_C_StopLayer(def, params[0]);
		case VOICE_OFFSET_TABLE: return new C_C_StartLayerTable(def, params[0]);
		case SET_CH_FILTER: return new C_C_SetFilter(def, params[0]);
		case CLEAR_CH_FILTER:  return new C_C_ClearFilter(def);
		case LOAD_P_TABLE: return new C_C_LoadPFromTable(def, params[0]);
		case COPY_CH_FILTER: return new C_C_CopyFilter(def, params[0], params[1]);
		case DYNTABLE_WRITE: return new C_C_P2DynTable(def);
		case DYNTABLE_READ: return new C_C_DynTable2P(def);
		case DYNTABLE_LOAD: return new C_C_DynTable2Q(def);
		case RANDP: return new C_C_RandomP(def, params[0]);
		case RAND_C: return new C_C_RandomQ(def, params[0]);
		case VELRAND: return new C_C_SetVelocityRand(def, params[0]);
		case GATERAND: return new C_C_SetGateRand(def, params[0]);
		case CHORUS: return new C_C_Chorus(def, params[0], params[1]);
		case ADD_IMM_P: return new C_C_AddPImmediate(def, params[0]);
		case ADD_RAND_IMM_P: return new C_C_AddPRandom(def, params[0], params[1]);
		case C_UNK_C0: return null;
		case SET_PROGRAM: return new C_C_ChangeProgram(def, params[0]);
		case SET_DYNTABLE: return new C_C_SetDynTable(def, params[0]);
		case SHORTNOTE_ON: return new C_C_SetShortNotesOn(def);
		case SHORTNOTE_OFF: return new C_C_SetShortNotesOff(def);
		case SHIFT_DYNTABLE: return new C_C_ShiftDynTable(def);
		case SET_BANK: return new C_C_ChangeBank(def, params[0]);
		case STORE_TO_SELF_C: return new C_C_StoreToSelf(def, params[0], params[1]);
		case SUBTRACT_IMM_C: return new C_C_SubtractImm(def, params[0]);
		case AND_IMM_C: return new C_C_AndImm(def, (params[0] & 0xff));
		case LOAD_IMM_C: return new C_C_LoadImm(def, params[0]);
		case MUTE_BEHAVIOR_C: return new C_C_MuteBehavior(def, params[0]);
		case LOAD_FROM_SELF: return new C_C_LoadFromSelf(def, params[0]);
		case STOP_CHANNEL_C: return new C_C_StopChannel(def, params[0]);
		case LOAD_IMM_P: return new C_C_LoadPImm(def, params[0]);
		case STORE_TO_SELF_P: return new C_C_StorePToSelf(def, params[0]);
		case CH_STEREO_EFF: return new C_C_StereoEffects(def, (params[0] & 0xff));
		case CH_NOTEALLOC_POLICY: return new C_C_NoteAllocPolicy(def, params[0]);
		case CH_SUSTAIN: return new C_C_Sustain(def, params[0]);
		case CH_PITCHBEND: return new C_C_PitchBend(def, params[0]);
		case CH_REVERB: return new C_C_Reverb(def, params[0]);
		case CH_VIBRATO_FREQ: return new C_C_VibratoFreq(def, (params[0] & 0xff));
		case CH_VIBRATO_DEPTH: return new C_C_VibratoDepth(def, params[0]);
		case CH_RELEASE: return new C_C_Release(def, (params[0] & 0xff));
		case CH_ENVELOPE: return new C_C_Envelope(def, params[0]);
		case CH_TRANSPOSE: return new C_C_Transpose(def, params[0]);
		case CH_PANMIX: return new C_C_PanMix(def, params[0]);
		case CH_PAN: return new C_C_Pan(def, params[0]);
		case CH_FREQSCALE: return new C_C_FreqScale(def, params[0]);
		case CH_VOLUME: return new C_C_Volume(def, params[0]);
		case CH_EXP: return new C_C_Expression(def, params[0]);
		case CH_VIBRATO_FREQENV: return new C_C_VibFreqEnvelope(def, (params[0] & 0xff), (params[1] & 0xff), (params[2] & 0xff));
		case CH_VIBRATO_DEPTHENV: return new C_C_VibDepthEnvelope(def, (params[0] & 0xff), (params[1] & 0xff), (params[2] & 0xff));
		case CH_VIBRATO_DELAY: return new C_C_VibratoDelay(def, (params[0] & 0xff));
		case CALL_DYNTABLE: return new C_C_DynCall(def);
		case CH_REVERB_IDX: return new C_C_ReverbIndex(def, params[0]);
		case CH_SAMPLE_VARIATION: return new C_C_SampleVariation(def, params[0]);
		case CH_LOAD_PARAMS: return new C_C_LoadChannelParams(def, params[0]);
		case CH_SET_PARAMS: return new C_C_SetChannelParams(def, params);
		case CH_PRIORITY: return new C_C_Priority(def, params[0]);
		case CH_HALT: return new C_C_Halt(def);
		case SET_BANK_AND_PROGRAM: return new C_C_SetBankProgram(def, params[0], params[1]);
		case CH_RESET: return new C_C_ResetChannel(def);
		case CH_FILTER_GAIN: return new C_C_FilterGain(def, params[0]);
		case CH_PITCHBEND_ALT: return new C_C_PitchBendAlt(def, params[0]);
			
		case UNRESERVE_NOTES: return new C_UnreserveNotes(def);
		case RESERVE_NOTES: return new C_ReserveNotes(def, params[0]);
		case BRANCH_IF_LTZ_REL: return new C_rbltz(params[0], def);
		case BRANCH_IF_EQZ_REL: return new C_rbeqz(params[0], def);
		case BRANCH_ALWAYS_REL: return new C_rjump(params[0], def);
		case BRANCH_IF_GTEZ: return new C_bgez(params[0], def);
		case BREAK: return new C_Break(def);
		case LOOP_END: return new C_LoopEnd(def);
		case LOOP_START: return new C_LoopStart(params[0], def);
		case BRANCH_IF_LTZ: return new C_bltz(params[0], def);
		case BRANCH_IF_EQZ: return new C_beqz(params[0], def);
		case BRANCH_ALWAYS: return new C_Jump(params[0], def);
		case CALL: return new C_Call(params[0], def);
		case WAIT: return new C_Wait(def, params[0]);
		case YIELD: return new C_Yield(def);
		case END_READ: return new C_EndRead(def);
		 default: return null;
		}
	}
	
	public static NUSALSeqCommand parseChannelCommand(NUSALSeqCommandBook book, String cmd, String[] args) {
		if(book == null || cmd == null) return null;
		NUSALSeqCommandDef def = book.getChannelCommand(cmd.toLowerCase());
		if(def == null) return null;
		
		int[][] iargs = def.parseMMLArgs(args);
		int arg0 = 0;
		if(iargs != null) arg0 = iargs[0][0];
		else arg0 = StringUtils.parseSignedInt(args[0]);
		int arg1 = 0, arg2 = 0;
		
		NUSALSeqCmdType ctype = def.getFunctionalType();
		switch(ctype) {
		case CH_DELTA_TIME: return new C_C_DeltaTime(def, arg0);
		case LOAD_SAMPLE: return new C_C_LoadSample(def, arg0);
		case LOAD_SAMPLE_P: return new C_C_LoadSampleP(def, arg0);
		case CHANNEL_OFFSET_C: return new C_C_StartChannel(def, arg0, -1);
		case STORE_CHIO: 
			if(iargs != null) arg1 = iargs[1][0];
			else arg1 = StringUtils.parseSignedInt(args[1]);
			return new C_C_StoreChIO(def, arg0, arg1);
		case LOAD_CHIO: 
			if(iargs != null) arg1 = iargs[1][0];
			else arg1 = StringUtils.parseSignedInt(args[1]);
			return new C_C_LoadChIO(def, arg0, arg1);
		case SUBTRACT_IO_C: return new C_C_SubIO(def, arg0);
		case LOAD_IO_C: return new C_C_LoadIO(def, arg0);
		case STORE_IO_C: return new C_C_StoreIO(def, arg0);
		case VOICE_OFFSET_REL: return new C_C_StartLayerRel(def, arg0, -1);
		case TEST_VOICE: return new C_C_TestLayer(def, arg0);
		case VOICE_OFFSET: return new C_C_StartLayer(def, arg0, -1);
		case STOP_VOICE: return new C_C_StopLayer(def, arg0);
		case VOICE_OFFSET_TABLE: return new C_C_StartLayerTable(def, arg0);
		case SET_CH_FILTER: return new C_C_SetFilter(def, -1);
		case CLEAR_CH_FILTER:  return new C_C_ClearFilter(def);
		case LOAD_P_TABLE: return new C_C_LoadPFromTable(def, -1);
		case COPY_CH_FILTER: 
			if(iargs != null) arg1 = iargs[1][0];
			else arg1 = StringUtils.parseSignedInt(args[1]);
			return new C_C_CopyFilter(def, arg0, arg1);
		case DYNTABLE_WRITE: return new C_C_P2DynTable(def);
		case DYNTABLE_READ: return new C_C_DynTable2P(def);
		case DYNTABLE_LOAD: return new C_C_DynTable2Q(def);
		case RANDP: return new C_C_RandomP(def, arg0);
		case RAND_C: return new C_C_RandomQ(def, arg0);
		case VELRAND: return new C_C_SetVelocityRand(def, arg0);
		case GATERAND: return new C_C_SetGateRand(def, arg0);
		case CHORUS: 
			if(iargs != null) arg1 = iargs[1][0];
			else arg1 = StringUtils.parseSignedInt(args[1]);
			return new C_C_Chorus(def, arg0, arg1);
		case ADD_IMM_P: return new C_C_AddPImmediate(def, arg0);
		case ADD_RAND_IMM_P: 
			if(iargs != null) arg1 = iargs[1][0];
			else arg1 = StringUtils.parseSignedInt(args[1]);
			return new C_C_AddPRandom(def, arg0, arg1);
		case C_UNK_C0: return null;
		case SET_PROGRAM: return new C_C_ChangeProgram(def, arg0);
		case SET_DYNTABLE: return new C_C_SetDynTable(def, -1);
		case SHORTNOTE_ON: return new C_C_SetShortNotesOn(def);
		case SHORTNOTE_OFF: return new C_C_SetShortNotesOff(def);
		case SHIFT_DYNTABLE: return new C_C_ShiftDynTable(def);
		case SET_BANK: return new C_C_ChangeBank(def, arg0);
		case STORE_TO_SELF_C: return new C_C_StoreToSelf(def, arg0, -1);
		case SUBTRACT_IMM_C: return new C_C_SubtractImm(def, arg0);
		case AND_IMM_C: return new C_C_AndImm(def, arg0);
		case LOAD_IMM_C: return new C_C_LoadImm(def, arg0);
		case MUTE_BEHAVIOR_C: return new C_C_MuteBehavior(def, arg0);
		case LOAD_FROM_SELF: return new C_C_LoadFromSelf(def, -1);
		case STOP_CHANNEL_C: return new C_C_StopChannel(def, arg0);
		case LOAD_IMM_P: return new C_C_LoadPImm(def, arg0);
		case STORE_TO_SELF_P: return new C_C_StorePToSelf(def, -1);
		case CH_STEREO_EFF: return new C_C_StereoEffects(def, arg0);
		case CH_NOTEALLOC_POLICY: return new C_C_NoteAllocPolicy(def, arg0);
		case CH_SUSTAIN: return new C_C_Sustain(def, arg0);
		case CH_PITCHBEND: return new C_C_PitchBend(def, arg0);
		case CH_REVERB: return new C_C_Reverb(def, arg0);
		case CH_VIBRATO_FREQ: return new C_C_VibratoFreq(def, arg0);
		case CH_VIBRATO_DEPTH: return new C_C_VibratoDepth(def, arg0);
		case CH_RELEASE: return new C_C_Release(def, arg0);
		case CH_ENVELOPE: return new C_C_Envelope(def, -1);
		case CH_TRANSPOSE: return new C_C_Transpose(def, arg0);
		case CH_PANMIX: return new C_C_PanMix(def, arg0);
		case CH_PAN: return new C_C_Pan(def, arg0);
		case CH_FREQSCALE: return new C_C_FreqScale(def, arg0);
		case CH_VOLUME: return new C_C_Volume(def, arg0);
		case CH_EXP: return new C_C_Expression(def, arg0);
		case CH_VIBRATO_FREQENV: 
			if(iargs != null) {
				arg1 = iargs[1][0];
				arg2 = iargs[2][0];
			}
			else {
				arg1 = StringUtils.parseSignedInt(args[1]);
				arg2 = StringUtils.parseSignedInt(args[2]);
			}
			return new C_C_VibFreqEnvelope(def, arg0, arg1, arg2);
		case CH_VIBRATO_DEPTHENV: 
			if(iargs != null) {
				arg1 = iargs[1][0];
				arg2 = iargs[2][0];
			}
			else {
				arg1 = StringUtils.parseSignedInt(args[1]);
				arg2 = StringUtils.parseSignedInt(args[2]);
			}
			return new C_C_VibDepthEnvelope(def, arg0, arg1, arg2);
		case CH_VIBRATO_DELAY: return new C_C_VibratoDelay(def, arg0);
		case CALL_DYNTABLE: return new C_C_DynCall(def);
		case CH_REVERB_IDX: return new C_C_ReverbIndex(def, arg0);
		case CH_SAMPLE_VARIATION: return new C_C_SampleVariation(def, arg0);
		case CH_LOAD_PARAMS: return new C_C_LoadChannelParams(def, -1);
		case CH_SET_PARAMS: 
			if(iargs != null) {
				return new C_C_SetChannelParams(def, iargs[0]);
			}
			else {
				int[] cparams = new int[8];
				for(int i = 0; i < 8; i++) {
					cparams[i] = StringUtils.parseSignedInt(args[i]);
				}
				return new C_C_SetChannelParams(def, cparams);
			}
		case CH_PRIORITY: return new C_C_Priority(def, arg0);
		case CH_HALT: return new C_C_Halt(def);
		case SET_BANK_AND_PROGRAM: 
			if(iargs != null) arg1 = iargs[1][0];
			else arg1 = StringUtils.parseSignedInt(args[1]);
			return new C_C_SetBankProgram(def, arg0, arg1);
		case CH_RESET: return new C_C_ResetChannel(def);
		case CH_FILTER_GAIN: return new C_C_FilterGain(def, arg0);
		case CH_PITCHBEND_ALT: return new C_C_PitchBendAlt(def, arg0);
		
		
		case UNRESERVE_NOTES: return new C_UnreserveNotes(def);
		case RESERVE_NOTES: return new C_ReserveNotes(def, arg0);
		case BRANCH_IF_LTZ_REL: return new C_rbltz(-1, def);
		case BRANCH_IF_EQZ_REL: return new C_rbeqz(-1, def);
		case BRANCH_ALWAYS_REL: return new C_rjump(-1, def);
		case BRANCH_IF_GTEZ: return new C_bgez(-1, def);
		case BREAK: return new C_Break(def);
		case LOOP_END: return new C_LoopEnd(def);
		case LOOP_START: return new C_LoopStart(arg0, def);
		case BRANCH_IF_LTZ: return new C_bltz(-1, def);
		case BRANCH_IF_EQZ: return new C_beqz(-1, def);
		case BRANCH_ALWAYS: return new C_Jump(-1, def);
		case CALL: return new C_Call(-1, def);
		case WAIT: return new C_Wait(def, arg0);
		case YIELD: return new C_Yield(def);
		case END_READ: return new C_EndRead(def);
		
		default: return null;
		}
	}
	
	/*--- 0x00:0x0f cdelay ---*/
	public static class C_C_DeltaTime extends NUSALSeqWaitCommand{
		public C_C_DeltaTime(int ticks) {
			super(NUSALSeqCmdType.CH_DELTA_TIME, ticks);
		}
		public C_C_DeltaTime(NUSALSeqCommandBook book, int ticks) {
			super(NUSALSeqCmdType.CH_DELTA_TIME, book, ticks);
		}
		public C_C_DeltaTime(NUSALSeqCommandDef def, int ticks) {
			super(def, ticks);
		}
	}
	
	/*--- 0x10:0x17 loadspl ---*/
	public static class C_C_LoadSample extends CMD_IgnoredCommand{
		public C_C_LoadSample(int io_idx) {
			super(NUSALSeqCmdType.LOAD_SAMPLE);
			super.setParam(0, io_idx);
		}
		public C_C_LoadSample(NUSALSeqCommandBook book, int io_idx) {
			super(NUSALSeqCmdType.LOAD_SAMPLE, book);
			super.setParam(0, io_idx);
		}
		public C_C_LoadSample(NUSALSeqCommandDef def, int io_idx) {
			super(def);
			super.setParam(0, io_idx);
		}
	}
	
	/*--- 0x18:0x1f loadsplp ---*/
	public static class C_C_LoadSampleP extends CMD_IgnoredCommand{
		public C_C_LoadSampleP(int io_idx) {
			super(NUSALSeqCmdType.LOAD_SAMPLE_P);
			super.setParam(0, io_idx);
		}
		public C_C_LoadSampleP(NUSALSeqCommandBook book, int io_idx) {
			super(NUSALSeqCmdType.LOAD_SAMPLE_P, book);
			super.setParam(0, io_idx);
		}
		public C_C_LoadSampleP(NUSALSeqCommandDef def, int io_idx) {
			super(def);
			super.setParam(0, io_idx);
		}
	}
	
	/*--- 0x20:0x2f startchan ---*/
	public static class C_C_StartChannel extends NUSALSeqReferenceCommand{
		public C_C_StartChannel(int channel, int addr) {
			super(NUSALSeqCmdType.CHANNEL_OFFSET_C, null, channel, addr, false);
		}
		public C_C_StartChannel(NUSALSeqCommandBook book, int channel, int addr) {
			super(NUSALSeqCmdType.CHANNEL_OFFSET_C, book, channel, addr, false);
		}
		public C_C_StartChannel(NUSALSeqCommandDef def, int channel, int addr) {
			super(def, channel, addr, false);
		}
		public NUSALSeqCmdType getRelativeCommand(){return null;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.CHANNEL_OFFSET_C;}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			return channel.pointChannelTo(getParam(0), getParam(1));
		}
	}
	
	/*--- 0x30:0x3f stcio ---*/
	public static class C_C_StoreChIO extends NUSALSeqGenericCommand{
		public C_C_StoreChIO(int ch, int ioidx) {
			super(NUSALSeqCmdType.STORE_CHIO);
			super.setParam(0, ch);
			super.setParam(1, ioidx);
		}
		public C_C_StoreChIO(NUSALSeqCommandBook book, int ch, int ioidx) {
			super(NUSALSeqCmdType.STORE_CHIO, book);
			super.setParam(0, ch);
			super.setParam(1, ioidx);
		}
		public C_C_StoreChIO(NUSALSeqCommandDef def, int ch, int ioidx) {
			super(def);
			super.setParam(0, ch);
			super.setParam(1, ioidx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			NUSALSeqChannel ch = seq.getChannel(getParam(0));
			if(ch == null) return false;
			ch.setSeqIOValue(getParam(1), channel.getVarQ());
			return true;
		}
	}
	
	/*--- 0x40:0x4f ldcio ---*/
	public static class C_C_LoadChIO extends NUSALSeqGenericCommand{
		public C_C_LoadChIO(int ch, int ioidx) {
			super(NUSALSeqCmdType.LOAD_CHIO);
			super.setParam(0, ch);
			super.setParam(1, ioidx);
		}
		public C_C_LoadChIO(NUSALSeqCommandBook book, int ch, int ioidx) {
			super(NUSALSeqCmdType.LOAD_CHIO, book);
			super.setParam(0, ch);
			super.setParam(1, ioidx);
		}
		public C_C_LoadChIO(NUSALSeqCommandDef def, int ch, int ioidx) {
			super(def);
			super.setParam(0, ch);
			super.setParam(1, ioidx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			NUSALSeqChannel ch = seq.getChannel(getParam(0));
			if(ch == null) return false;
			channel.setQ((byte)ch.getSeqIOValue(getParam(1)));
			return true;
		}
	}
	
	/*--- 0x50:0x5f subio ---*/
	public static class C_C_SubIO extends NUSALSeqGenericCommand{
		public C_C_SubIO(int ioidx) {
			super(NUSALSeqCmdType.SUBTRACT_IO_C);
			super.setParam(0, ioidx);
		}
		public C_C_SubIO(NUSALSeqCommandBook book, int ioidx) {
			super(NUSALSeqCmdType.SUBTRACT_IO_C, book);
			super.setParam(0, ioidx);
		}
		public C_C_SubIO(NUSALSeqCommandDef def, int ioidx) {
			super(def);
			super.setParam(0, ioidx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setQ((byte)(channel.getVarQ() - channel.getSeqIOValue(getParam(0))));
			return true;
		}
	}
	
	/*--- 0x60:0x6f ldio ---*/
	public static class C_C_LoadIO extends NUSALSeqGenericCommand{
		public C_C_LoadIO(int ioidx) {
			super(NUSALSeqCmdType.LOAD_IO_C);
			super.setParam(0, ioidx);
		}
		public C_C_LoadIO(NUSALSeqCommandBook book, int ioidx) {
			super(NUSALSeqCmdType.LOAD_IO_C, book);
			super.setParam(0, ioidx);
		}
		public C_C_LoadIO(NUSALSeqCommandDef def, int ioidx) {
			super(def);
			super.setParam(0, ioidx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setQ((byte)channel.getSeqIOValue(getParam(0)));
			return true;
		}
	}
	
	/*--- 0x70:0x77 stio ---*/
	public static class C_C_StoreIO extends NUSALSeqGenericCommand{
		public C_C_StoreIO(int ioidx) {
			super(NUSALSeqCmdType.STORE_IO_C);
			super.setParam(0, ioidx);
		}
		public C_C_StoreIO(NUSALSeqCommandBook book, int ioidx) {
			super(NUSALSeqCmdType.STORE_IO_C, book);
			super.setParam(0, ioidx);
		}
		public C_C_StoreIO(NUSALSeqCommandDef def, int ioidx) {
			super(def);
			super.setParam(0, ioidx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setSeqIOValue(getParam(0), channel.getVarQ());
			return true;
		}
	}
	
	/*--- 0x78:0x7b rstartlayer ---*/
	public static class C_C_StartLayerRel extends NUSALSeqReferenceCommand{
		public C_C_StartLayerRel(int layer, int offset) {
			super(NUSALSeqCmdType.VOICE_OFFSET_REL, null, layer, offset, true);
		}
		public C_C_StartLayerRel(NUSALSeqCommandBook book, int layer, int offset) {
			super(NUSALSeqCmdType.VOICE_OFFSET_REL, book, layer, offset, true);
		}
		public C_C_StartLayerRel(NUSALSeqCommandDef def, int layer, int offset) {
			super(def, layer, offset, true);
		}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.VOICE_OFFSET_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.VOICE_OFFSET;}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			return channel.pointLayerTo(getParam(0), getBranchAddress());
		}
	}
	
	/*--- 0x80:0x87 testlayer ---*/
	public static class C_C_TestLayer extends NUSALSeqGenericCommand{
		public C_C_TestLayer(int layer_idx) {
			super(NUSALSeqCmdType.TEST_VOICE);
			super.setParam(0, layer_idx);
		}
		public C_C_TestLayer(NUSALSeqCommandBook book, int layer_idx) {
			super(NUSALSeqCmdType.TEST_VOICE, book);
			super.setParam(0, layer_idx);
		}
		public C_C_TestLayer(NUSALSeqCommandDef def, int layer_idx) {
			super(def);
			super.setParam(0, layer_idx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			channel.setQ((byte)channel.getLayerStatus(getParam(0)));
			return true;
		}
	}
	
	/*--- 0x88:0x8b startlayer ---*/
	public static class C_C_StartLayer extends NUSALSeqReferenceCommand{
		public C_C_StartLayer(int layer, int addr) {
			super(NUSALSeqCmdType.VOICE_OFFSET, null, layer, addr, false);
		}
		public C_C_StartLayer(NUSALSeqCommandBook book, int layer, int addr) {
			super(NUSALSeqCmdType.VOICE_OFFSET, book, layer, addr, false);
		}
		public C_C_StartLayer(NUSALSeqCommandDef def, int layer, int addr) {
			super(def, layer, addr, false);
		}
		public NUSALSeqCmdType getRelativeCommand(){return NUSALSeqCmdType.VOICE_OFFSET_REL;}
		public NUSALSeqCmdType getAbsoluteCommand(){return NUSALSeqCmdType.VOICE_OFFSET;}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			return channel.pointLayerTo(getParam(0), getBranchAddress());
		}
	}
	
	/*--- 0x90:0x97 stoplayer ---*/
	public static class C_C_StopLayer extends NUSALSeqGenericCommand{
		public C_C_StopLayer(int layer_idx) {
			super(NUSALSeqCmdType.STOP_VOICE);
			super.setParam(0, layer_idx);
		}
		public C_C_StopLayer(NUSALSeqCommandBook book, int layer_idx) {
			super(NUSALSeqCmdType.STOP_VOICE, book);
			super.setParam(0, layer_idx);
		}
		public C_C_StopLayer(NUSALSeqCommandDef def, int layer_idx) {
			super(def);
			super.setParam(0, layer_idx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			return channel.stopLayer(getParam(0));
		}
	}
	
	/*--- 0x98:0x9f dynstartlayer ---*/
	public static class C_C_StartLayerTable extends NUSALSeqGenericCommand{
		public C_C_StartLayerTable(int layer_idx) {
			super(NUSALSeqCmdType.VOICE_OFFSET_TABLE);
			super.setParam(0, layer_idx);
		}
		public C_C_StartLayerTable(NUSALSeqCommandBook book, int layer_idx) {
			super(NUSALSeqCmdType.VOICE_OFFSET_TABLE, book);
			super.setParam(0, layer_idx);
		}
		public C_C_StartLayerTable(NUSALSeqCommandDef def, int layer_idx) {
			super(def);
			super.setParam(0, layer_idx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			int off = channel.getVarQ() << 1;
			BufferReference ref = seq.getSeqDataReference(channel.getDynTableAddress()+ off);
			return channel.pointLayerTo(getParam(0), Short.toUnsignedInt(ref.getShort()));
		}
	}
	
	/*--- 0xb0 setfilter ---*/
	public static class C_C_SetFilter extends NUSALSeqDataRefCommand{
		public C_C_SetFilter(int address) {
			super(NUSALSeqCmdType.SET_CH_FILTER, null, address, 16);
			super.setLabelPrefix("filter");
		}
		public C_C_SetFilter(NUSALSeqCommandBook book, int address) {
			super(NUSALSeqCmdType.SET_CH_FILTER, book, address, 16);
			super.setLabelPrefix("filter");
		}
		public C_C_SetFilter(NUSALSeqCommandDef def, int address) {
			super(def, address, 16);
			super.setLabelPrefix("filter");
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setFilterAddress(getBranchAddress());
			return true;
		}
	}
	
	/*--- 0xb1 clearfilter ---*/
	public static class C_C_ClearFilter extends NUSALSeqGenericCommand{
		public C_C_ClearFilter() {
			super(NUSALSeqCmdType.CLEAR_CH_FILTER);
		}
		public C_C_ClearFilter(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.CLEAR_CH_FILTER, book);
		}
		public C_C_ClearFilter(NUSALSeqCommandDef def) {
			super(def);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.clearFilter();
			return true;
		}
	}
	
	/*--- 0xb2 ldptbl ---*/
	public static class C_C_LoadPFromTable extends NUSALSeqDataRefCommand{
		public C_C_LoadPFromTable(int address) {
			super(NUSALSeqCmdType.LOAD_P_TABLE, null, address, -1);
			super.setLabelPrefix("ptrtbl");
		}
		public C_C_LoadPFromTable(NUSALSeqCommandBook book, int address) {
			super(NUSALSeqCmdType.LOAD_P_TABLE, book, address, -1);
			super.setLabelPrefix("ptrtbl");
		}
		public C_C_LoadPFromTable(NUSALSeqCommandDef def, int address) {
			super(def, address, -1);
			super.setLabelPrefix("ptrtbl");
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			int off = channel.getVarQ() << 1;
			BufferReference ref = seq.getSeqDataReference(this.getBranchAddress() + off);
			channel.setP(ref.getShort());
			return true;
		}
	}
	
	/*--- 0xb3 copyfilter ---*/
	public static class C_C_CopyFilter extends CMD_IgnoredCommand{
		public C_C_CopyFilter(int param) {
			super(NUSALSeqCmdType.COPY_CH_FILTER);
			super.setParam(0, (param & 0xf0) >>> 4);
			super.setParam(1, param & 0xf);
		}
		public C_C_CopyFilter(NUSALSeqCommandBook book, int param) {
			super(NUSALSeqCmdType.COPY_CH_FILTER, book);
			super.setParam(0, (param & 0xf0) >>> 4);
			super.setParam(1, param & 0xf);
		}
		public C_C_CopyFilter(NUSALSeqCommandDef def, int param) {
			super(def);
			super.setParam(0, (param & 0xf0) >>> 4);
			super.setParam(1, param & 0xf);
		}
		public C_C_CopyFilter(NUSALSeqCommandDef def, int param1, int param2) {
			super(def);
			super.setParam(0, param1);
			super.setParam(1, param2);
		}
	}
	
	/*--- 0xb4 p2dyntable ---*/
	public static class C_C_P2DynTable extends NUSALSeqGenericCommand{
		public C_C_P2DynTable() {
			super(NUSALSeqCmdType.DYNTABLE_WRITE);
		}
		public C_C_P2DynTable(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.DYNTABLE_WRITE, book);
		}
		public C_C_P2DynTable(NUSALSeqCommandDef def) {
			super(def);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			BufferReference ref = seq.getSeqDataReference(channel.getVarP());
			channel.setDynTable(Short.toUnsignedInt(ref.getShort()));
			return true;
		}
	}
	
	/*--- 0xb5 dyntable2p ---*/
	public static class C_C_DynTable2P extends NUSALSeqGenericCommand{
		public C_C_DynTable2P() {
			super(NUSALSeqCmdType.DYNTABLE_READ);
		}
		public C_C_DynTable2P(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.DYNTABLE_READ, book);
		}
		public C_C_DynTable2P(NUSALSeqCommandDef def) {
			super(def);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			int ptr = channel.getDynTableAddress();
			ptr += channel.getVarQ() << 1;
			BufferReference ref = seq.getSeqDataReference(ptr);
			channel.setP(ref.getShort());
			return true;
		}
	}
	
	/*--- 0xb6 lddyn ---*/
	public static class C_C_DynTable2Q extends NUSALSeqGenericCommand{
		public C_C_DynTable2Q() {
			super(NUSALSeqCmdType.DYNTABLE_LOAD);
		}
		public C_C_DynTable2Q(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.DYNTABLE_LOAD, book);
		}
		public C_C_DynTable2Q(NUSALSeqCommandDef def) {
			super(def);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			int ptr = channel.getDynTableAddress();
			ptr += channel.getVarQ() << 1;
			BufferReference ref = seq.getSeqDataReference(ptr);
			seq.setVarQ(ref.getByte(1)); //dyntable should be a u16[], so not sure if read both bytes, first or second?
			return true;
		}
	}
	
	/*--- 0xb7 randp ---*/
	public static class C_C_RandomP extends NUSALSeqGenericCommand{
		public C_C_RandomP(int max) {
			super(NUSALSeqCmdType.RANDP);
			super.setParam(0, max);
		}
		public C_C_RandomP(NUSALSeqCommandBook book, int max) {
			super(NUSALSeqCmdType.RANDP, book);
			super.setParam(0, max);
		}
		public C_C_RandomP(NUSALSeqCommandDef def, int max) {
			super(def);
			super.setParam(0, max);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			int max = getParam(0);
			if(max < 1) max = 0xFFFF;
			int val = seq.getRNG().nextInt(max);
			channel.setP((short)val);
			return true;
		}
	}
	
	/*--- 0xb8 rand ---*/
	public static class C_C_RandomQ extends NUSALSeqGenericCommand{
		public C_C_RandomQ(int max) {
			super(NUSALSeqCmdType.RAND_C);
			super.setParam(0, max);
		}
		public C_C_RandomQ(NUSALSeqCommandBook book, int max) {
			super(NUSALSeqCmdType.RAND_C, book);
			super.setParam(0, max);
		}
		public C_C_RandomQ(NUSALSeqCommandDef def, int max) {
			super(def);
			super.setParam(0, max);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			int max = getParam(0);
			if(max < 1) max = 0xFF;
			int val = seq.getRNG().nextInt(max);
			channel.setQ((byte)val);
			return true;
		}
	}
	
	/*--- 0xb9 velrand ---*/
	public static class C_C_SetVelocityRand extends NUSALSeqGenericCommand{
		public C_C_SetVelocityRand(int value) {
			super(NUSALSeqCmdType.VELRAND);
			super.setParam(0, value);
		}
		public C_C_SetVelocityRand(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.VELRAND, book);
			super.setParam(0, value);
		}
		public C_C_SetVelocityRand(NUSALSeqCommandDef def, int value) {
			super(def);
			super.setParam(0, value);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setVelocityVariance(getParam(0));
			return true;
		}
	}
	
	/*--- 0xba gaterand ---*/
	public static class C_C_SetGateRand extends NUSALSeqGenericCommand{
		public C_C_SetGateRand(int value) {
			super(NUSALSeqCmdType.GATERAND);
			super.setParam(0, value);
		}
		public C_C_SetGateRand(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.GATERAND, book);
			super.setParam(0, value);
		}
		public C_C_SetGateRand(NUSALSeqCommandDef def, int value) {
			super(def);
			super.setParam(0, value);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setGateVariance(getParam(0));
			return true;
		}
	}
	
	/*--- 0xbb chorus ---*/
	//TODO Implement
	public static class C_C_Chorus extends NUSALSeqGenericCommand{
		public C_C_Chorus() {
			super(NUSALSeqCmdType.CHORUS);
		}
		public C_C_Chorus(NUSALSeqCommandBook book, int p1, int p2) {
			super(NUSALSeqCmdType.CHORUS, book);
			super.setParam(0, p1);
			super.setParam(1, p2);
		}
		public C_C_Chorus(NUSALSeqCommandDef def, int p1, int p2) {
			super(def);
			super.setParam(0, p1);
			super.setParam(1, p2);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			//TODO
			flagChannelUsed(channel.getIndex());
			return true;
		}
	}
	
	/*--- 0xbc addp ---*/
	public static class C_C_AddPImmediate extends NUSALSeqGenericCommand{
		public C_C_AddPImmediate(int imm) {
			super(NUSALSeqCmdType.ADD_IMM_P);
			super.setParam(0, imm);
		}
		public C_C_AddPImmediate(NUSALSeqCommandBook book, int imm) {
			super(NUSALSeqCmdType.ADD_IMM_P, book);
			super.setParam(0, imm);
		}
		public C_C_AddPImmediate(NUSALSeqCommandDef def, int imm) {
			super(def);
			super.setParam(0, imm);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setP((short)(channel.getVarP() + getParam(0)));
			return true;
		}
	}
	
	/*--- 0xbd randaddp ---*/
	public static class C_C_AddPRandom extends NUSALSeqGenericCommand{
		public C_C_AddPRandom(int max, int imm) {
			super(NUSALSeqCmdType.ADD_RAND_IMM_P);
			super.setParam(0, max);
			super.setParam(1, imm);
		}
		public C_C_AddPRandom(NUSALSeqCommandBook book, int max, int imm) {
			super(NUSALSeqCmdType.ADD_RAND_IMM_P, book);
			super.setParam(0, max);
			super.setParam(1, imm);
		}
		public C_C_AddPRandom(NUSALSeqCommandDef def, int max, int imm) {
			super(def);
			super.setParam(0, max);
			super.setParam(1, imm);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			int max = getParam(0);
			if(max < 1) max = 0xFFFF;
			int val = seq.getRNG().nextInt(max);
			val = (val + getParam(1)) | 0x8000;
			channel.setP((short)val);
			return true;
		}
	}
		
	/*--- 0xc1 instr ---*/
	public static class C_C_ChangeProgram extends NUSALSeqGenericCommand{
		public C_C_ChangeProgram(int idx) {
			super(NUSALSeqCmdType.SET_PROGRAM);
			super.setParam(0, idx);
		}
		public C_C_ChangeProgram(NUSALSeqCommandBook book, int idx) {
			super(NUSALSeqCmdType.SET_PROGRAM, book);
			super.setParam(0, idx);
		}
		public C_C_ChangeProgram(NUSALSeqCommandDef def, int idx) {
			super(def);
			super.setParam(0, idx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.changeProgram(getParam(0));
			return true;
		}
	}
	
	/*--- 0xc2 dyntable ---*/
	public static class C_C_SetDynTable extends NUSALSeqDataRefCommand{
		public C_C_SetDynTable(int addr) {
			super(NUSALSeqCmdType.SET_DYNTABLE, null, addr, -1);
			super.setLabelPrefix("cdyntbl");
		}
		public C_C_SetDynTable(NUSALSeqCommandBook book, int addr) {
			super(NUSALSeqCmdType.SET_DYNTABLE, book, addr, -1);
			super.setLabelPrefix("cdyntbl");
		}
		public C_C_SetDynTable(NUSALSeqCommandDef def, int addr) {
			super(def, addr, -1);
			super.setLabelPrefix("cdyntbl");
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setDynTable(getParam(0));
			return true;
		}
	}
	
	/*--- 0xc3 shorton ---*/
	public static class C_C_SetShortNotesOn extends NUSALSeqGenericCommand{
		public C_C_SetShortNotesOn() {
			super(NUSALSeqCmdType.SHORTNOTE_ON);
		}
		public C_C_SetShortNotesOn(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.SHORTNOTE_ON, book);
		}
		public C_C_SetShortNotesOn(NUSALSeqCommandDef def) {
			super(def);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setShortNotesMode(true);
			return true;
		}
	}
	
	/*--- 0xc4 shortoff ---*/
	public static class C_C_SetShortNotesOff extends NUSALSeqGenericCommand{
		public C_C_SetShortNotesOff() {
			super(NUSALSeqCmdType.SHORTNOTE_OFF);
		}
		public C_C_SetShortNotesOff(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.SHORTNOTE_OFF, book);
		}
		public C_C_SetShortNotesOff(NUSALSeqCommandDef def) {
			super(def);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setShortNotesMode(false);
			return true;
		}
	}
	
	/*--- 0xc5 dynsetdyntable ---*/
	public static class C_C_ShiftDynTable extends NUSALSeqGenericCommand{
		public C_C_ShiftDynTable() {
			super(NUSALSeqCmdType.SHIFT_DYNTABLE);
		}
		public C_C_ShiftDynTable(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.SHIFT_DYNTABLE, book);
		}
		public C_C_ShiftDynTable(NUSALSeqCommandDef def) {
			super(def);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			int shamt = channel.getVarQ();
			if(shamt < 0) return true;
			shamt <<= 1;
			channel.setDynTable(channel.getDynTableAddress() + shamt);
			return true;
		}
	}
	
	/*--- 0xc6 bank ---*/
	public static class C_C_ChangeBank extends NUSALSeqGenericCommand{
		public C_C_ChangeBank(int idx) {
			super(NUSALSeqCmdType.SET_BANK);
			super.setParam(0, idx);
		}
		public C_C_ChangeBank(NUSALSeqCommandBook book, int idx) {
			super(NUSALSeqCmdType.SET_BANK, book);
			super.setParam(0, idx);
		}
		public C_C_ChangeBank(NUSALSeqCommandDef def, int idx) {
			super(def);
			super.setParam(0, idx);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.changeBank(getParam(0));
			return true;
		}
	}
	
	/*--- 0xc7 sts ---*/ 
	public static class C_C_StoreToSelf extends NUSALSeqDataRefCommand{
		public C_C_StoreToSelf(int imm, int addr) {
			super(NUSALSeqCmdType.STORE_TO_SELF_C, null, imm, addr, -1);
			//super.setParam(0, imm);
			//super.setParam(1, addr);
		}
		public C_C_StoreToSelf(NUSALSeqCommandBook book, int imm, int addr) {
			super(NUSALSeqCmdType.STORE_TO_SELF_C, book, imm, addr, -1);
			//super.setParam(0, imm);
			//super.setParam(1, addr);
		}
		public C_C_StoreToSelf(NUSALSeqCommandDef def, int imm, int addr) {
			super(def, imm, addr, -1);
			//super.setParam(0, imm);
			//super.setParam(1, addr);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			int val = channel.getVarQ() + getParam(0);
			STSResult res = channel.storeToSelf(getBranchAddress(), (byte)val);
			return res == STSResult.OKAY;
		}
	}
	
	/*--- 0xc8 sub ---*/
	public static class C_C_SubtractImm extends NUSALSeqGenericCommand{
		public C_C_SubtractImm(int imm) {
			super(NUSALSeqCmdType.SUBTRACT_IMM_C);
			super.setParam(0, imm);
		}
		public C_C_SubtractImm(NUSALSeqCommandBook book, int imm) {
			super(NUSALSeqCmdType.SUBTRACT_IMM_C, book);
			super.setParam(0, imm);
		}
		public C_C_SubtractImm(NUSALSeqCommandDef def, int imm) {
			super(def);
			super.setParam(0, imm);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			int val = channel.getVarQ() - getParam(0);
			channel.setQ((byte)val);
			return true;
		}
	}
	
	/*--- 0xc9 and ---*/
	public static class C_C_AndImm extends NUSALSeqGenericCommand{
		public C_C_AndImm(int imm) {
			super(NUSALSeqCmdType.AND_IMM_C);
			super.setParam(0, imm);
		}
		public C_C_AndImm(NUSALSeqCommandBook book, int imm) {
			super(NUSALSeqCmdType.AND_IMM_C, book);
			super.setParam(0, imm);
		}
		public C_C_AndImm(NUSALSeqCommandDef def, int imm) {
			super(def);
			super.setParam(0, imm);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			int val = channel.getVarQ() & getParam(0);
			channel.setQ((byte)val);
			return true;
		}
		
		protected String paramsToString(int syntax){
			return String.format("0x%02x", super.getParam(0));
		}
	}
	
	/*--- 0xca mutebhv ---*/
	public static class C_C_MuteBehavior extends CMD_IgnoredCommand{
		public C_C_MuteBehavior(int bitmask) {
			super(NUSALSeqCmdType.MUTE_BEHAVIOR_C);
			super.setParam(0, bitmask);
		}
		public C_C_MuteBehavior(NUSALSeqCommandBook book, int bitmask) {
			super(NUSALSeqCmdType.MUTE_BEHAVIOR_C, book);
			super.setParam(0, bitmask);
		}
		public C_C_MuteBehavior(NUSALSeqCommandDef def, int bitmask) {
			super(def);
			super.setParam(0, bitmask);
		}
	}
	
	/*--- 0xcb lds ---*/ 
	public static class C_C_LoadFromSelf extends NUSALSeqDataRefCommand{
		public C_C_LoadFromSelf(int addr) {
			super(NUSALSeqCmdType.LOAD_FROM_SELF, null, addr, -1);
		}
		public C_C_LoadFromSelf(NUSALSeqCommandBook book, int addr) {
			super(NUSALSeqCmdType.LOAD_FROM_SELF, book, addr, -1);
		}
		public C_C_LoadFromSelf(NUSALSeqCommandDef def, int addr) {
			super(def, addr, -1);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			BufferReference ref = seq.getSeqDataReference(getParam(0) + channel.getVarQ());
			channel.setQ(ref.getByte());
			return true;
		}
	}
	
	/*--- 0xcc ldi ---*/
	public static class C_C_LoadImm extends NUSALSeqGenericCommand{
		public C_C_LoadImm(int imm) {
			super(NUSALSeqCmdType.LOAD_IMM_C);
			super.setParam(0, imm);
		}
		public C_C_LoadImm(NUSALSeqCommandBook book, int imm) {
			super(NUSALSeqCmdType.LOAD_IMM_C, book);
			super.setParam(0, imm);
		}
		public C_C_LoadImm(NUSALSeqCommandDef def, int imm) {
			super(def);
			super.setParam(0, imm);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setQ((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xcd stopchan ---*/
	public static class C_C_StopChannel extends NUSALSeqGenericCommand{
		public C_C_StopChannel(int ch) {
			super(NUSALSeqCmdType.STOP_CHANNEL_C);
			super.setParam(0, ch);
		}
		public C_C_StopChannel(NUSALSeqCommandBook book, int ch) {
			super(NUSALSeqCmdType.STOP_CHANNEL_C, book);
			super.setParam(0, ch);
		}
		public C_C_StopChannel(NUSALSeqCommandDef def, int ch) {
			super(def);
			super.setParam(0, ch);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			return channel.stopChannel(getParam(0));
		}
	}
	
	/*--- 0xce ldpi ---*/
	public static class C_C_LoadPImm extends NUSALSeqDataRefCommand{
		public C_C_LoadPImm(NUSALSeqCommandBook book, int imm) {
			super(NUSALSeqCmdType.LOAD_IMM_P, book, imm, -1);
		}
		public C_C_LoadPImm(NUSALSeqCommandDef def, int imm) {
			super(def, imm, -1);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setP((short)this.getBranchAddress());
			return true;
		}
	}
	
	/*--- 0xcf stps ---*/ 
	public static class C_C_StorePToSelf extends NUSALSeqDataRefCommand{
		public C_C_StorePToSelf(int addr) {
			super(NUSALSeqCmdType.STORE_TO_SELF_P, null, addr, -1);
		}
		public C_C_StorePToSelf(NUSALSeqCommandBook book, int addr) {
			super(NUSALSeqCmdType.STORE_TO_SELF_P, book, addr, -1);
		}
		public C_C_StorePToSelf(NUSALSeqCommandDef def, int addr) {
			super(def, addr, -1);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			int val = channel.getVarP();
			STSResult res = channel.storePToSelf(getBranchAddress(), (short)val);
			return res == STSResult.OKAY;
		}
	}
	
	/*--- 0xd0 stereoheadseteffects ---*/
	public static class C_C_StereoEffects extends CMD_IgnoredCommand{
		public C_C_StereoEffects(int bitmask) {
			super(NUSALSeqCmdType.CH_STEREO_EFF);
			super.setParam(0, bitmask);
		}
		public C_C_StereoEffects(NUSALSeqCommandBook book, int bitmask) {
			super(NUSALSeqCmdType.CH_STEREO_EFF, book);
			super.setParam(0, bitmask);
		}
		public C_C_StereoEffects(NUSALSeqCommandDef def, int bitmask) {
			super(def);
			super.setParam(0, bitmask);
		}
	}
	
	/*--- 0xd1 noteallocpolicy ---*/
	public static class C_C_NoteAllocPolicy extends CMD_IgnoredCommand{
		public C_C_NoteAllocPolicy(int bitmask) {
			super(NUSALSeqCmdType.CH_NOTEALLOC_POLICY);
			super.setParam(0, bitmask);
		}
		public C_C_NoteAllocPolicy(NUSALSeqCommandBook book, int bitmask) {
			super(NUSALSeqCmdType.CH_NOTEALLOC_POLICY, book);
			super.setParam(0, bitmask);
		}
		public C_C_NoteAllocPolicy(NUSALSeqCommandDef def, int bitmask) {
			super(def);
			super.setParam(0, bitmask);
		}
	}
	
	/*--- 0xd2 sustain ---*/
	public static class C_C_Sustain extends NUSALSeqGenericCommand{
		public C_C_Sustain(int val) {
			super(NUSALSeqCmdType.CH_SUSTAIN);
			super.setParam(0, val);
		}
		public C_C_Sustain(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_SUSTAIN, book);
			super.setParam(0, val);
		}
		public C_C_Sustain(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setSustain(getParam(0));
			return true;
		}
	}
	
	/*--- 0xd3 pitchbend ---*/
	public static class C_C_PitchBend extends NUSALSeqGenericCommand{
		public C_C_PitchBend(int val) {
			super(NUSALSeqCmdType.CH_PITCHBEND);
			super.setParam(0, val);
		}
		public C_C_PitchBend(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_PITCHBEND, book);
			super.setParam(0, val);
		}
		public C_C_PitchBend(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setPitchbend(getParam(0));
			return true;
		}
	}
	
	/*--- 0xd4 reverb ---*/
	public static class C_C_Reverb extends NUSALSeqGenericCommand{
		public C_C_Reverb(int val) {
			super(NUSALSeqCmdType.CH_REVERB);
			super.setParam(0, val);
		}
		public C_C_Reverb(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_REVERB, book);
			super.setParam(0, val);
		}
		public C_C_Reverb(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setEffectsLevel((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xd7 vibfreq ---*/
	public static class C_C_VibratoFreq extends NUSALSeqGenericCommand{
		public C_C_VibratoFreq(int val) {
			super(NUSALSeqCmdType.CH_VIBRATO_FREQ);
			super.setParam(0, val);
		}
		public C_C_VibratoFreq(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_VIBRATO_FREQ, book);
			super.setParam(0, val);
		}
		public C_C_VibratoFreq(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setVibratoFrequency(getParam(0));
			return true;
		}
	}
	
	/*--- 0xd8 vibdepth ---*/
	public static class C_C_VibratoDepth extends NUSALSeqGenericCommand{
		public C_C_VibratoDepth(int val) {
			super(NUSALSeqCmdType.CH_VIBRATO_DEPTH);
			super.setParam(0, val);
		}
		public C_C_VibratoDepth(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_VIBRATO_DEPTH, book);
			super.setParam(0, val);
		}
		public C_C_VibratoDepth(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setVibratoDepth(getParam(0));
			return true;
		}
	}
	
	/*--- 0xd9 release ---*/
	public static class C_C_Release extends NUSALSeqGenericCommand{
		public C_C_Release(int val) {
			super(NUSALSeqCmdType.CH_RELEASE);
			super.setParam(0, val);
		}
		public C_C_Release(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_RELEASE, book);
			super.setParam(0, val);
		}
		public C_C_Release(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setRelease(getParam(0));
			return true;
		}
	}
	
	/*--- 0xda cenvelope ---*/
	public static class C_C_Envelope extends NUSALSeqDataRefCommand{
		public C_C_Envelope(int addr) {
			super(NUSALSeqCmdType.CH_ENVELOPE, null, addr, 16); //I THINK it's 16 bytes
			super.setLabelPrefix("env");
		}
		public C_C_Envelope(NUSALSeqCommandBook book, int addr) {
			super(NUSALSeqCmdType.CH_ENVELOPE, book, addr, 16);
			super.setLabelPrefix("env");
		}
		public C_C_Envelope(NUSALSeqCommandDef def, int addr) {
			super(def, addr, 16);
			super.setLabelPrefix("env");
		}

		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setEnvelopeAddress(getParam(0));
			return true;
		}
	}
	
	/*--- 0xdb ctp ---*/
	public static class C_C_Transpose extends NUSALSeqGenericCommand{
		public C_C_Transpose(int val) {
			super(NUSALSeqCmdType.CH_TRANSPOSE);
			super.setParam(0, val);
		}
		public C_C_Transpose(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_TRANSPOSE, book);
			super.setParam(0, val);
		}
		public C_C_Transpose(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setTranspose(getParam(0));
			return true;
		}
	}
	
	/*--- 0xdc panmix ---*/
	public static class C_C_PanMix extends NUSALSeqGenericCommand{
		public C_C_PanMix(int val) {
			super(NUSALSeqCmdType.CH_PANMIX);
			super.setParam(0, val);
		}
		public C_C_PanMix(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_PANMIX, book);
			super.setParam(0, val);
		}
		public C_C_PanMix(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setPanMix(getParam(0));
			return true;
		}
	}
	
	/*--- 0xdd cpan ---*/
	public static class C_C_Pan extends NUSALSeqGenericCommand{
		public C_C_Pan(int val) {
			super(NUSALSeqCmdType.CH_PAN);
			super.setParam(0, val);
		}
		public C_C_Pan(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_PAN, book);
			super.setParam(0, val);
		}
		public C_C_Pan(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setPan((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xde freqscale ---*/
	public static class C_C_FreqScale extends NUSALSeqGenericCommand{
		public C_C_FreqScale(int val) {
			super(NUSALSeqCmdType.CH_FREQSCALE);
			super.setParam(0, val);
		}
		public C_C_FreqScale(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_FREQSCALE, book);
			super.setParam(0, val);
		}
		public C_C_FreqScale(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setFreqScale(getParam(0));
			return true;
		}
	}
	
	/*--- 0xdf cvol ---*/
	public static class C_C_Volume extends NUSALSeqGenericCommand{
		public C_C_Volume(int val) {
			super(NUSALSeqCmdType.CH_VOLUME);
			super.setParam(0, val);
		}
		public C_C_Volume(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_VOLUME, book);
			super.setParam(0, val);
		}
		public C_C_Volume(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setVolume((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xe0 cexp ---*/
	public static class C_C_Expression extends NUSALSeqGenericCommand{
		public C_C_Expression(int val) {
			super(NUSALSeqCmdType.CH_EXP);
			super.setParam(0, val);
		}
		public C_C_Expression(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_EXP, book);
			super.setParam(0, val);
		}
		public C_C_Expression(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setExpression((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xe1 vibfreqenv ---*/
	public static class C_C_VibFreqEnvelope extends NUSALSeqGenericCommand{
		public C_C_VibFreqEnvelope(int start, int target, int time) {
			super(NUSALSeqCmdType.CH_VIBRATO_FREQENV);
			super.setParam(0, start);
			super.setParam(1, target);
			super.setParam(2, time);
		}
		public C_C_VibFreqEnvelope(NUSALSeqCommandBook book, int start, int target, int time) {
			super(NUSALSeqCmdType.CH_VIBRATO_FREQENV, book);
			super.setParam(0, start);
			super.setParam(1, target);
			super.setParam(2, time);
		}
		public C_C_VibFreqEnvelope(NUSALSeqCommandDef def, int start, int target, int time) {
			super(def);
			super.setParam(0, start);
			super.setParam(1, target);
			super.setParam(2, time);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setVibratoFrequencyEnvelope(getParam(0), getParam(1), getParam(2));
			return true;
		}
	}
	
	/*--- 0xe2 vibdepthenv ---*/
	public static class C_C_VibDepthEnvelope extends NUSALSeqGenericCommand{
		public C_C_VibDepthEnvelope(int start, int target, int time) {
			super(NUSALSeqCmdType.CH_VIBRATO_DEPTHENV);
			super.setParam(0, start);
			super.setParam(1, target);
			super.setParam(2, time);
		}
		public C_C_VibDepthEnvelope(NUSALSeqCommandBook book, int start, int target, int time) {
			super(NUSALSeqCmdType.CH_VIBRATO_DEPTHENV, book);
			super.setParam(0, start);
			super.setParam(1, target);
			super.setParam(2, time);
		}
		public C_C_VibDepthEnvelope(NUSALSeqCommandDef def, int start, int target, int time) {
			super(def);
			super.setParam(0, start);
			super.setParam(1, target);
			super.setParam(2, time);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setVibratoDepthEnvelope(getParam(0), getParam(1), getParam(2));
			return true;
		}
	}
	
	/*--- 0xe3 vibdelay ---*/
	public static class C_C_VibratoDelay extends NUSALSeqGenericCommand{
		public C_C_VibratoDelay(int val) {
			super(NUSALSeqCmdType.CH_VIBRATO_DELAY);
			super.setParam(0, val);
		}
		public C_C_VibratoDelay(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_VIBRATO_DELAY, book);
			super.setParam(0, val);
		}
		public C_C_VibratoDelay(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setVibratoDelay(getParam(0));
			return true;
		}
	}
	
	/*--- 0xe4 dyncall ---*/
	public static class C_C_DynCall extends NUSALSeqGenericCommand{
		public C_C_DynCall() {
			super(NUSALSeqCmdType.CALL_DYNTABLE);
		}
		public C_C_DynCall(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.CALL_DYNTABLE, book);
		}
		public C_C_DynCall(NUSALSeqCommandDef def) {
			super(def);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			int q = channel.getVarQ();
			if(q < 0) return true;
			q <<= 1;
			BufferReference ref = seq.getSeqDataReference(channel.getDynTableAddress() + q);
			int calladdr = Short.toUnsignedInt(ref.getShort());
			return channel.jumpTo(calladdr, true);
		}
	}
	
	/*--- 0xe5 reverbidx ---*/
	public static class C_C_ReverbIndex extends CMD_IgnoredCommand{
		public C_C_ReverbIndex(int value) {
			super(NUSALSeqCmdType.CH_REVERB_IDX);
			super.setParam(0, value);
		}
		public C_C_ReverbIndex(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.CH_REVERB_IDX, book);
			super.setParam(0, value);
		}
		public C_C_ReverbIndex(NUSALSeqCommandDef def, int value) {
			super(def);
			super.setParam(0, value);
		}
	}
	
	/*--- 0xe6 splvari ---*/
	public static class C_C_SampleVariation extends CMD_IgnoredCommand{
		public C_C_SampleVariation(int value) {
			super(NUSALSeqCmdType.CH_SAMPLE_VARIATION);
			super.setParam(0, value);
		}
		public C_C_SampleVariation(NUSALSeqCommandBook book, int value) {
			super(NUSALSeqCmdType.CH_SAMPLE_VARIATION, book);
			super.setParam(0, value);
		}
		public C_C_SampleVariation(NUSALSeqCommandDef def, int value) {
			super(def);
			super.setParam(0, value);
		}
	}
	
	/*--- 0xe7 ldcparams ---*/
	public static class C_C_LoadChannelParams extends NUSALSeqDataRefCommand{
		public C_C_LoadChannelParams(int addr) {
			super(NUSALSeqCmdType.CH_LOAD_PARAMS, null, addr, 8);
			super.setLabelPrefix("cparam");
		}
		public C_C_LoadChannelParams(NUSALSeqCommandBook book, int addr) {
			super(NUSALSeqCmdType.CH_LOAD_PARAMS, book, addr, 8);
			super.setLabelPrefix("cparam");
		}
		public C_C_LoadChannelParams(NUSALSeqCommandDef def, int addr) {
			super(def, addr, 8);
			super.setLabelPrefix("cparam");
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			NUSALSeq seq = channel.getParent();
			if(seq == null) return false;
			BufferReference ref = seq.getSeqDataReference(getParam(0));
			//0: Mute behavior
			//1: NoteAlloc
			//2: Priority
			channel.setPriority(ref.getByte(2));
			//3: Transpose
			channel.setTranspose((int)ref.getByte(3));
			//4: Pan
			channel.setPan(ref.getByte(4));
			//5: Pan Weight
			channel.setPanMix(ref.getByte(5));
			//6: Reverb
			channel.setEffectsLevel(ref.getByte(6));
			//7: Reverb Index
			return true;
		}

	}
	
	/*--- 0xe8 cparams ---*/
	public static class C_C_SetChannelParams extends NUSALSeqGenericCommand{
		public C_C_SetChannelParams(int[] args) {
			super(NUSALSeqCmdType.CH_SET_PARAMS);
			for(int i = 0; i < 8; i++){
				if(args == null || args.length <= i) break;
				super.setParam(i, args[i]);
			}
		}
		public C_C_SetChannelParams(NUSALSeqCommandBook book, int[] args) {
			super(NUSALSeqCmdType.CH_SET_PARAMS, book);
			for(int i = 0; i < 8; i++){
				if(args == null || args.length <= i) break;
				super.setParam(i, args[i]);
			}
		}
		public C_C_SetChannelParams(NUSALSeqCommandDef def, int[] args) {
			super(def);
			for(int i = 0; i < 8; i++){
				if(args == null || args.length <= i) break;
				super.setParam(i, args[i]);
			}
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			//0: Mute behavior
			//1: NoteAlloc
			//2: Priority
			channel.setPriority((byte)getParam(2));
			//3: Transpose
			channel.setTranspose(getParam(3));
			//4: Pan
			channel.setPan((byte)getParam(4));
			//5: Pan Weight
			channel.setPanMix((byte)getParam(5));
			//6: Reverb
			channel.setEffectsLevel((byte)getParam(6));
			//7: Reverb Index
			return true;
		}
	}
	
	/*--- 0xe9 notepriority ---*/
	public static class C_C_Priority extends NUSALSeqGenericCommand{
		public C_C_Priority(int val) {
			super(NUSALSeqCmdType.CH_PRIORITY);
			super.setParam(0, val);
		}
		public C_C_Priority(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_PRIORITY, book);
			super.setParam(0, val);
		}
		public C_C_Priority(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setPriority((byte)getParam(0));
			return true;
		}
	}
	
	/*--- 0xea halt ---*/
	public static class C_C_Halt extends CMD_IgnoredCommand{
		public C_C_Halt() {
			super(NUSALSeqCmdType.CH_HALT);
		}
		public C_C_Halt(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.CH_HALT, book);
		}
		public C_C_Halt(NUSALSeqCommandDef def) {
			super(def);
		}
	}
	
	/*--- 0xeb bankinstr ---*/
	public static class C_C_SetBankProgram extends NUSALSeqGenericCommand{
		public C_C_SetBankProgram(int bank, int prog) {
			super(NUSALSeqCmdType.SET_BANK_AND_PROGRAM);
			super.setParam(0, bank);
			super.setParam(1, prog);
		}
		public C_C_SetBankProgram(NUSALSeqCommandBook book, int bank, int prog) {
			super(NUSALSeqCmdType.SET_BANK_AND_PROGRAM, book);
			super.setParam(0, bank);
			super.setParam(1, prog);
		}
		public C_C_SetBankProgram(NUSALSeqCommandDef def, int bank, int prog) {
			super(def);
			super.setParam(0, bank);
			super.setParam(1, prog);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.changeProgram(getParam(0), getParam(1));
			return true;
		}
	}
	
	/*--- 0xec chanreset ---*/
	public static class C_C_ResetChannel extends NUSALSeqGenericCommand{
		public C_C_ResetChannel() {
			super(NUSALSeqCmdType.CH_RESET);
		}
		public C_C_ResetChannel(NUSALSeqCommandBook book) {
			super(NUSALSeqCmdType.CH_RESET, book);
		}
		public C_C_ResetChannel(NUSALSeqCommandDef def) {
			super(def);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.resetChannel();
			return true;
		}
	}
	
	/*--- 0xed filgain ---*/
	public static class C_C_FilterGain extends NUSALSeqGenericCommand{
		public C_C_FilterGain(int val) {
			super(NUSALSeqCmdType.CH_FILTER_GAIN);
			super.setParam(0, val);
		}
		public C_C_FilterGain(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_FILTER_GAIN, book);
			super.setParam(0, val);
		}
		public C_C_FilterGain(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setFilterGain(getParam(0));
			return true;
		}
	}
	
	/*--- 0xee cbend2 ---*/
	public static class C_C_PitchBendAlt extends NUSALSeqGenericCommand{
		public C_C_PitchBendAlt(int val) {
			super(NUSALSeqCmdType.CH_PITCHBEND_ALT);
			super.setParam(0, val);
		}
		public C_C_PitchBendAlt(NUSALSeqCommandBook book, int val) {
			super(NUSALSeqCmdType.CH_PITCHBEND_ALT, book);
			super.setParam(0, val);
		}
		public C_C_PitchBendAlt(NUSALSeqCommandDef def, int val) {
			super(def);
			super.setParam(0, val);
		}
		public boolean doCommand(NUSALSeqChannel channel){
			flagChannelUsed(channel.getIndex());
			channel.setPitchbendAlt(getParam(0));
			return true;
		}
	}
	
}

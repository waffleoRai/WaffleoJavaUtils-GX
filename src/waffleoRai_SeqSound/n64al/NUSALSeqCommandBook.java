package waffleoRai_SeqSound.n64al;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import waffleoRai_SeqSound.n64al.cmd.NUSALSeqCommandDef;

public class NUSALSeqCommandBook {
	
	private Map<Byte, NUSALSeqCommandDef> seqBinCmds;
	private Map<Byte, NUSALSeqCommandDef> chBinCmds;
	private Map<Byte, NUSALSeqCommandDef> lyrBinCmds;
	private Map<Byte, NUSALSeqCommandDef> lyrShBinCmds;
	
	private Map<String, NUSALSeqCommandDef> seqStrCmds;
	private Map<String, NUSALSeqCommandDef> chStrCmds;
	private Map<String, NUSALSeqCommandDef> lyrStrCmds;
	
	private Map<NUSALSeqCmdType, NUSALSeqCommandDef> seqEnumCmds;
	private Map<NUSALSeqCmdType, NUSALSeqCommandDef> chEnumCmds;
	private Map<NUSALSeqCmdType, NUSALSeqCommandDef> lyrEnumCmds;
	
	public NUSALSeqCommandBook() {
		seqBinCmds = new HashMap<Byte, NUSALSeqCommandDef>();
		chBinCmds = new HashMap<Byte, NUSALSeqCommandDef>();
		lyrBinCmds = new HashMap<Byte, NUSALSeqCommandDef>();
		lyrShBinCmds = new HashMap<Byte, NUSALSeqCommandDef>();
		seqStrCmds = new HashMap<String, NUSALSeqCommandDef>();
		chStrCmds = new HashMap<String, NUSALSeqCommandDef>();
		lyrStrCmds = new HashMap<String, NUSALSeqCommandDef>();
	}
	
	/*----- Getters -----*/
	
	public NUSALSeqCommandDef getSeqCommand(byte cmd) {
		return seqBinCmds.get(cmd);
	}
	
	public NUSALSeqCommandDef getChannelCommand(byte cmd) {
		return chBinCmds.get(cmd);
	}
	
	public NUSALSeqCommandDef getLayerCommand(byte cmd, boolean shortMode) {
		NUSALSeqCommandDef def = null;
		if(shortMode) {
			def = lyrShBinCmds.get(cmd);
			if(def != null) return def;
		}
		return lyrBinCmds.get(cmd);
	}
	
	public NUSALSeqCommandDef getSeqCommand(String cmd) {
		if(cmd != null) return null;
		return seqStrCmds.get(cmd);
	}
	
	public NUSALSeqCommandDef getChannelCommand(String cmd) {
		if(cmd != null) return null;
		return chStrCmds.get(cmd);
	}
	
	public NUSALSeqCommandDef getLayerCommand(String cmd) {
		if(cmd != null) return null;
		return lyrStrCmds.get(cmd);
	}
	
	public NUSALSeqCommandDef getSeqCommand(NUSALSeqCmdType cmd) {
		if(cmd != null) return null;
		return seqEnumCmds.get(cmd);
	}
	
	public NUSALSeqCommandDef getChannelCommand(NUSALSeqCmdType cmd) {
		if(cmd != null) return null;
		return chEnumCmds.get(cmd);
	}
	
	public NUSALSeqCommandDef getLayerCommand(NUSALSeqCmdType cmd) {
		if(cmd != null) return null;
		return lyrEnumCmds.get(cmd);
	}
	
	public NUSALSeqCommandDef getCommand(NUSALSeqCmdType cmd) {
		if(cmd != null) return null;
		//TODO
		return null;
	}
	
	/*----- Setters -----*/
	
	public void addCommandDef(NUSALSeqCommandDef def) {
		if(def == null) return;
		int ctx = def.getContext();
		int min = Byte.toUnsignedInt(def.getMinCommand());
		int max = Byte.toUnsignedInt(def.getMaxCommand());
		for(int i = min; i <= max; i++) {
			String key1 = def.getMnemonicZeqer();
			String key2 = def.getMnemonicSeq64();
			String key3 = def.getMnemonicZeldaRet();
			NUSALSeqCmdType ctype = def.getFunctionalType();
			
			switch(ctx) {
			case NUSALSeqCommands.CMDCTX_ANY:
				seqBinCmds.put((byte)i, def);
				chBinCmds.put((byte)i, def);
				lyrBinCmds.put((byte)i, def);
				if(key1 != null) {
					seqStrCmds.put(key1, def);
					chStrCmds.put(key1, def);
					lyrStrCmds.put(key1, def);
				}
				if(key2 != null) {
					seqStrCmds.put(key2, def);
					chStrCmds.put(key2, def);
					lyrStrCmds.put(key2, def);
				}
				if(key3 != null) {
					seqStrCmds.put(key3, def);
					chStrCmds.put(key3, def);
					lyrStrCmds.put(key3, def);
				}
				if(ctype != null) {
					seqEnumCmds.put(ctype, def);
					chEnumCmds.put(ctype, def);
					lyrEnumCmds.put(ctype, def);
				}
				break;
			case NUSALSeqCommands.CMDCTX_SEQ:
				seqBinCmds.put((byte)i, def);
				if(key1 != null) seqStrCmds.put(key1, def);
				if(key2 != null) seqStrCmds.put(key2, def);
				if(key3 != null) seqStrCmds.put(key3, def);
				seqEnumCmds.put(ctype, def);
				break;
			case NUSALSeqCommands.CMDCTX_CHANNEL:
				chBinCmds.put((byte)i, def);
				if(key1 != null) chStrCmds.put(key1, def);
				if(key2 != null) chStrCmds.put(key2, def);
				if(key3 != null) chStrCmds.put(key3, def);
				chEnumCmds.put(ctype, def);
				break;
			case NUSALSeqCommands.CMDCTX_LAYER:
				lyrBinCmds.put((byte)i, def);
				if(key1 != null) lyrStrCmds.put(key1, def);
				if(key2 != null) lyrStrCmds.put(key2, def);
				if(key3 != null) lyrStrCmds.put(key3, def);
				lyrEnumCmds.put(ctype, def);
				break;
			case NUSALSeqCommands.CMDCTX_SHORTNOTES:
				lyrShBinCmds.put((byte)i, def);
				if(key1 != null) lyrStrCmds.put(key1, def);
				if(key2 != null) lyrStrCmds.put(key2, def);
				if(key3 != null) lyrStrCmds.put(key3, def);
				lyrEnumCmds.put(ctype, def);
				break;
			}
		}
	}
	
	/*----- Read -----*/
	
	public static NUSALSeqCommandBook readTSV(BufferedReader reader) throws IOException {
		if(reader == null) return null;
		Map<String, Integer> cols = new HashMap<String, Integer>();
		String line = null;
		
		NUSALSeqCommandBook book = new NUSALSeqCommandBook();
		while((line = reader.readLine()) != null) {
			if(line.isEmpty()) continue;
			if(line.startsWith("#")) {
				//Columns
				String[] split = line.split("\t");
				for(int i = 0; i < split.length; i++) {
					String key = split[i].replace("#", "");
					cols.put(key, i);
				}
				continue;
			}
			
			String[] fields = line.split("\t");
			NUSALSeqCommandDef def = new NUSALSeqCommandDef();
			
			boolean okay = true;
			Integer val = cols.get("CONTEXT");
			if(val != null) {
				if(fields[val].equals("A")) {
					def.setContext(NUSALSeqCommands.CMDCTX_ANY);
				}
				else if(fields[val].equals("S")) {
					def.setContext(NUSALSeqCommands.CMDCTX_SEQ);
				}
				else if(fields[val].equals("C")) {
					def.setContext(NUSALSeqCommands.CMDCTX_CHANNEL);
				}
				else if(fields[val].equals("L")) {
					def.setContext(NUSALSeqCommands.CMDCTX_LAYER);
				}
				else if(fields[val].equals("LS")) {
					def.setContext(NUSALSeqCommands.CMDCTX_SHORTNOTES);
				}
				else okay = false;
			}
			else okay = false;
			
			int ival = 0;
			val = cols.get("START");
			if(val != null) {
				try {
					if(fields[val].startsWith("0x")) {
						ival = Integer.parseInt(fields[val].substring(2), 16);
					}
					else {
						ival = Integer.parseInt(fields[val]);
					}
				}
				catch(NumberFormatException ex) {
					ex.printStackTrace();
					okay = false;
				}
				def.setMinCommand((byte)ival);
			}
			else okay = false;
			
			val = cols.get("END");
			if(val != null) {
				try {
					if(fields[val].startsWith("0x")) {
						ival = Integer.parseInt(fields[val].substring(2), 16);
					}
					else {
						ival = Integer.parseInt(fields[val]);
					}
				}
				catch(NumberFormatException ex) {
					ex.printStackTrace();
					okay = false;
				}
				def.setMaxCommand((byte)ival);
			}
			else okay = false;
			
			val = cols.get("MNEMONIC_ZEQER");
			if(val != null) {
				def.setMnemonicZeqer(fields[val]);
			}
			else okay = false;
			
			val = cols.get("MNEMONIC_SEQ64");
			if(val != null) {
				def.setMnemonicSeq64(fields[val]);
			}
			else okay = false;
			
			val = cols.get("MNEMONIC_ZELDARET");
			if(val != null) {
				def.setMnemonicZeldaRet(fields[val]);
			}
			else okay = false;
			
			val = cols.get("ENUM");
			if(val != null) {
				//This links the functionality
				NUSALSeqCmdType cmd = NUSALSeqCmdType.fromEnumName(fields[val]);
				if(cmd != null) def.setFunctionalType(cmd);
			}
			else okay = false;
			
			val = cols.get("SIZE");
			if(val != null) {
				try {
					if(fields[val].startsWith("0x")) {
						ival = Integer.parseInt(fields[val].substring(2), 16);
					}
					else {
						ival = Integer.parseInt(fields[val]);
					}
				}
				catch(NumberFormatException ex) {
					ex.printStackTrace();
					okay = false;
				}
				def.setMinSize(ival);
			}
			else okay = false;
			
			val = cols.get("PARAM_COUNT");
			if(val != null) {
				try {
					if(fields[val].startsWith("0x")) {
						ival = Integer.parseInt(fields[val].substring(2), 16);
					}
					else {
						ival = Integer.parseInt(fields[val]);
					}
				}
				catch(NumberFormatException ex) {
					ex.printStackTrace();
					okay = false;
				}
				def.setParamCount(ival);
			}
			else okay = false;
			
			val = cols.get("FLAGS");
			if(val != null) {
				try {
					if(fields[val].startsWith("0x")) {
						ival = Integer.parseInt(fields[val].substring(2), 16);
					}
					else {
						ival = Integer.parseInt(fields[val]);
					}
				}
				catch(NumberFormatException ex) {
					ex.printStackTrace();
					okay = false;
				}
				def.setFlags(ival);
			}
			else okay = false;
			
			val = cols.get("SERFMT");
			if(val != null) {
				String s = fields[val];
				if(s.equals("X")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_);
				}
				else if(s.equals("L")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_L);
				}
				else if(s.equals("V")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_V);
				}
				else if(s.equals("1")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_1);
				}
				else if(s.equals("2")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_2);
				}
				else if(s.equals("11")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_11);
				}
				else if(s.equals("12")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_12);
				}
				else if(s.equals("21")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_21);
				}
				else if(s.equals("111")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_111);
				}
				else if(s.equals("L11")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_L11);
				}
				else if(s.equals("L12")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_L12);
				}
				else if(s.equals("L1")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_L1);
				}
				else if(s.equals("L2")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_L2);
				}
				else if(s.equals("CPF")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_COPYFILTER);
				}
				else if(s.equals("CPR")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_CPARAMS);
				}
				else if(s.equals("N")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_NOTE);
				}
				else if(s.equals("SN")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_SNOTE);
				}
				else if(s.equals("LP8")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_Lp8);
				}
				else if(s.equals("LP81")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_Lp81);
				}
				else if(s.equals("LP82")) {
					def.setSerialType(NUSALSeqCommand.SERIALFMT_Lp82);
				}
			}
			else okay = false;
			
			if(okay) book.addCommandDef(def);
		}
		
		return book;
	}

}

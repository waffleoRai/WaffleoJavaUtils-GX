package waffleoRai_soundbank.procyon;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Files.Converter;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileNode;
import waffleoRai_soundbank.SimpleBank;
import waffleoRai_soundbank.SimpleInstrument;
import waffleoRai_soundbank.SimplePreset;
import waffleoRai_soundbank.SingleBank;
import waffleoRai_soundbank.SoundbankDef;
import waffleoRai_soundbank.sf2.SF2;

//Thank you extremely specific PMD2 modding community!
//https://projectpokemon.org/docs/mystery-dungeon-nds/dse-swdl-format-r14/

/*
 * Header Format (Little Endian)
 * 	Magic "swdl" [4]
 * 	(Unknown) [4]
 *  File Length [4]
 *  Version [2] (Always 0x0415 LE)
 *  (Unknown) [1]
 *  (Unknown) [1] (Always 0x7F or 0x64)
 *  (Zero) [8]
 *  File Creation Time [8]
 *  	Year [2]
 *  	Month [1]
 *  	Day [1]
 *  	Hour [1]
 *  	Minute [1]
 *  	Second [1]
 *  	Centisecond [1]
 *  Internal Name [16]
 *  	Padding after null character is 0xAA
 *  (Unknown) [16] (First dword is always 0x00AAAAAA)
 *  PCMD Length [4]
 *  	If there is no chunk, this is 0.
 *  	If the chunk is in another SWD file, this is 0xAAAA0000
 *  (Unknown) [2]
 *   WAVI Slots [2]
 *   PRGI Slots [2]
 *   (Unknown) [2]
 *   WAVI Length [4]
 */

/*
 * WAVI Chunk
 * 	Magic "wavi" [4]
 * 	(Reserved) [2]
 *  Version [2] (0x0415)
 *  Chunk Start [4]
 *  Chunk Length [4] (Length is all after this field)
 *  
 *  WavPtrTable (Offsets relative to WavTable start)
 *  	Pointer (n) [2]
 *  -> Padded to 16 bytes (with 0xAA)
 *  -> Entry count in file's main header
 *  -> An empty entry is just 0x0000 (Null placeholder)
 *  
 *  Sample Info Table
 *  	Entry (n) [64]
 *  		Marker? [2] (0x01AA)
 *  		WAVI Index [2]
 *  		Fine Tune [1] (Probably in cents)
 *  		Coarse Tune [1] (Semitones? Defaults to -7 for some reason)
 *  		Unity Key [1]
 *  		Key Transpose [1] (Difference between unity key and 60)
 *  		Volume [1]
 *  		Pan [1]
 *  		Keygroup? [1] (Usually 0)
 *  		(Unknown) [1] (Usually 0x02)
 *  		(Padding) [2]
 *  		Version [2]
 *  		(Unknown) [1]
 *  		Encoding [1]
 *  			0 - 8 bit PCM
 *  			1 - 16 bit PCM
 *  			2 - IMA ADPCM (Check if DS standard or IMA standard)
 *  		(Unknown) [1]
 *  		Loop Flag [1]
 *  		(Unknown) [6] (Probably important)
 *  		(Null) [4]
 *  		Sample Rate [4]
 *  		PCMD Offset [4]
 *  		Loop Start [4]
 *  		Loop Length [4]
 *  		Envelope Flag [1] (If off, envelope is not applied)
 *  		Envelope Multiplier [1]
 *  		(Unknown) [6]
 *  		Attack Volume [1] (Volume at which att phase begins. Weird)
 *  		Attack [1]
 *  		Decay [1]
 *  		Sustain [1]
 *  		Hold [1]
 *  		Decay2 [1]
 *  		Release [1]
 *  		(Unknown) [1]
 */

/*
 * PCMD Chunk
 * 	(Contains audio data) 
 * 
 *	Magic "pcmd" [4]
 * 	(Reserved) [2]
 *  Version [2] (0x0415)
 *  Chunk Start [4]
 *  Chunk Length [4] (Length is all after this field)
 * 	Audio Data [Variable]
 *
 */

/*
 * PRGI Chunk
 * 	(Program info)
 * 
 * 	Magic "prgi" [4]
 * 	(Reserved) [2]
 *  Version [2] (0x0415)
 *  Chunk Start [4]
 *  Chunk Length [4] (Length is all after this field)
 *  
 *  Entry Pointer Table [n*2]
 *  	Presumably same deal as with WAVI ptr table
 *  
 *  Program Table
 *  	Program Index [2]
 *  	Split Count [2]
 *  	Volume [1]
 *  	Pan [1]
 *  	(Unknown) [5] Probably multiple important fields
 *  	LFO Count [1]
 *  	(Padding) [1]
 *  	(Unknown) [3]
 *  	LFO Table [LFOCount * 16]
 *  		(Unknown) [2]
 *  		Dest [1]
 *  			0 - None
 *  			1 - Pitch
 *  			2 - Vol
 *  			3 - Pan
 *  			4 - LPF (Oh fuck that)
 *  		Shape [1]
 *  			1 - Square
 *  			2 - Triangle
 *  			3 - Sine
 *  			4 - ?
 *  			5 - Saw
 *  			6 - Noise
 *  			7 - Random
 *  		Rate [2] (Hz???)
 *  		(Unknown) [2]
 *  		Depth [2]
 *  		Delay [2] (in ms)
 *  		(Unknown) [4]
 *  	(Padding) [16]
 *  	Split Map [SplitCount * 48]
 *  		(Zero) [1]
 *  		Index [1]
 *  		Bend Range? [1]
 *  		(Unknown) [1]
 *  		Min Key [1]
 *  		Max Key [1]
 *  		(Unknown) [2]
 *  		Min Vel [1]
 *  		Max Vel [1]
 *  		(Unknown) [2]
 *  		(Padding) [6]
 *  		WAVI Index [2]
 *  		Fine Tune [1] (Cents)
 *  		Coarse Tune [1] (Semis, default -7)
 *  		Unity Key [1]
 *  		Key Transpose [1]
 *  		Volume [1]
 *  		Pan [1]
 *  		Keygroup ID [1]
 *  		(Unknown) [5]
 *  
 *  		Volume Envelope [16] (Same as WAVI)
 *  
 */

/*
 * KGRP Chunk
 * 	("Key Group")
 * 
 * 	Magic "kgrp" [4]
 * 	(Reserved) [2]
 *  Version [2] (0x0415)
 *  Chunk Start [4]
 *  Chunk Length [4] (Length is all after this field)
 * 
 *  Keygroup Entries [n*8]
 *  	Keygroup Index [2]
 *  	Polyphony [1] (#Max voices)
 *  	Priority [1]
 *  	Min Voice Channel [1]
 *  	Max Voice Channel [1]
 *  	(Unknown) [2]
 *  	
 * 
 */

/*
 * EOD magic number denotes end of file
 */

public class SWD implements SynthBank{
	
	/*----- Constants -----*/
	
	public static final int TYPE_ID = 0x7377646c;
	
	public static final String MAGIC = "swdl";
	public static final String MAGIC_WAVI = "wavi"; //Wave data
	public static final String MAGIC_PRGI = "prgi"; //Program data
	public static final String MAGIC_KGRP = "kgrp";
	public static final String MAGIC_PCMD = "pcmd";
	public static final String MAGIC_EOD = "eod ";
	
	public static final int OSC_TYPE_SQUARE = 1;
	public static final int OSC_TYPE_TRIANGLE = 2;
	public static final int OSC_TYPE_SINE = 3;
	public static final int OSC_TYPE_SAW = 5;
	public static final int OSC_TYPE_NOISE = 6;
	public static final int OSC_TYPE_RANDOM = 7;
	
	public static final int OSC_DEST_NONE = 0;
	public static final int OSC_DEST_PITCH = 1;
	public static final int OSC_DEST_VOLUME = 2;
	public static final int OSC_DEST_PAN = 3;
	public static final int OSC_DEST_LPF = 4;
	
	public static final int OSC_MAX_PITCH = 1200; //Max one octave?
	
	public static final String FNMETAKEY_SMDBANK = "SWDBANK";
	public static final String FNMETAKEY_SMDPATH = "SWDBANKPATH";
	public static final String FNMETAKEY_BANKUID = "SWDUID"; //Bank UID (in case moved)
	public static final String FNMETAKEY_PARTNER = "SWDPCMDPARTNER"; //Partner UID (in case partner is moved)
	public static final String FNMETAKEY_PARTNERPATH = "SWDPARTNERPATH"; //Relative path in ROM
	public static final String FNMETAKEY_PCMDSRC_PATH = "SWDPCMDSRCPATH"; //Direct source path
	public static final String FNMETAKEY_PCMDSRC_OFF = "SWDPCMDSRCOFF"; //Direct offset (in hex, no 0x prefix)
	public static final String FNMETAKEY_PCMDSRC_LEN = "SWDPCMDSRCLEN";//Direct length (in hex, no 0x prefix)
	
	public static final int SAMPLERATE_DS = 32768;
	public static final int SAMPLERATE_SCALE = 32000;
	
	/*----- Instance Variables -----*/
	
	//private FileNode pcmd_link; //Location of PCMD chunk, if this file doesn't have one.
	//References ONLY the sound data (start offset is after header)
	
	private OffsetDateTime lastModified;
	private String fname;
	
	private SWDWavi[] wavinfo;
	private SWDWave[] sounds;
	private SWDProgram[] programs;
	
	private SWDWaveMap smap; //Only for playback
	
	/*----- Construction -----*/
	
	private SWD(){}
	
	/*----- Parsing -----*/
	
	public static SWD readSWD(FileBuffer data, long offset) throws UnsupportedFileTypeException{
		if(data == null) return null;
		
		data.setEndian(false);
		
		//Header
		long mpos = data.findString(offset, offset+0x10, MAGIC);
		if(mpos != offset) throw new FileBuffer.UnsupportedFileTypeException("swdl Magic Number not found!");
		
		SWD swd = new SWD();
		data.setCurrentPosition(offset+24);
		int year = Short.toUnsignedInt(data.nextShort());
		int month = Byte.toUnsignedInt(data.nextByte()) + 1;
		int date = Byte.toUnsignedInt(data.nextByte()) + 1;
		int hour = Byte.toUnsignedInt(data.nextByte());
		int minute = Byte.toUnsignedInt(data.nextByte());
		int sec = Byte.toUnsignedInt(data.nextByte());
		int cs = Byte.toUnsignedInt(data.nextByte());
		swd.lastModified = OffsetDateTime.of(year, month, date, hour, minute, sec, cs * 10000000, ZoneOffset.UTC);
		
		swd.fname = data.getASCII_string(data.getCurrentPosition(), '\0');
		data.skipBytes(32);
		//System.err.println("fname = " + swd.fname);
		
		//int pcmdlen = data.nextInt();
		//data.skipBytes(4);
		data.skipBytes(6);
		int wavi_ct = Short.toUnsignedInt(data.nextShort());
		int prgi_ct = Short.toUnsignedInt(data.nextShort());
		data.skipBytes(6);
		//data.skipBytes(2);
		//int wavilen = data.nextInt();
		
		//System.err.println("WAVI Count: " + wavi_ct);
		//System.err.println("PRGI Count: " + prgi_ct);
		
		//Read chunks...
		long cpos = data.getCurrentPosition();
		String chunk_magic = data.getASCII_string(cpos, 4); cpos+=12;
		while(!(chunk_magic.equals(MAGIC_EOD))){
			//System.err.println("Chunk Magic: " + chunk_magic);
			long chunklen = Integer.toUnsignedLong(data.intFromFile(cpos)); cpos+=4;
			long npos = cpos;
			//System.err.println("Chunk Length: 0x" + Long.toHexString(chunklen));
			if(chunk_magic.equals(MAGIC_WAVI)){
				//Pointer table
				swd.wavinfo = new SWDWavi[wavi_ct];
				for(int i = 0; i < wavi_ct; i++){
					int ptr = Short.toUnsignedInt(data.shortFromFile(npos)); npos += 2;
					if(ptr != 0){
						long roff = cpos + ptr;
						swd.wavinfo[i] = SWDWavi.readFromSWD(data, roff);
					}
				}
			}
			else if(chunk_magic.equals(MAGIC_PRGI)){
				//Pointer table
				swd.programs = new SWDProgram[prgi_ct];
				for(int i = 0; i < prgi_ct; i++){
					int ptr = Short.toUnsignedInt(data.shortFromFile(npos)); npos += 2;
					if(ptr != 0){
						long roff = cpos + ptr;
						swd.programs[i] = SWDProgram.readFromSWD(data, roff);
					}
				}
			}
			else if(chunk_magic.equals(MAGIC_KGRP)){
				//For now, ignore
			}
			else if(chunk_magic.equals(MAGIC_PCMD)){
				//Read in sound data (if already have wavi)
				//TODO how is offset calculated?
				if(swd.wavinfo != null){
					swd.sounds = new SWDWave[wavi_ct];
					for(int i = 0; i < wavi_ct; i++){
						SWDWavi wavi = swd.wavinfo[i];
						if(wavi != null){
							long soff = cpos + wavi.getPCMDOffset();
							swd.sounds[i] = SWDWave.readSWDWave(data, soff, wavi);
						}
					}
				}
			}
			else{
				//Chunk not recognized. Looks like sometimes the chunk sizes are messed up?
				//Look for a known chunk in the next kb...
				long spos = cpos;
				long search = data.findString(spos, spos + 1028, MAGIC_EOD);
				if(search >= 0){chunk_magic = MAGIC_EOD; cpos = search; continue;}
				search = data.findString(spos, spos + 1028, MAGIC_PRGI);
				if(search >= 0){chunk_magic = MAGIC_PRGI; cpos = search; continue;}
				search = data.findString(spos, spos + 1028, MAGIC_WAVI);
				if(search >= 0){chunk_magic = MAGIC_WAVI; cpos = search; continue;}
				search = data.findString(spos, spos + 1028, MAGIC_KGRP);
				if(search >= 0){chunk_magic = MAGIC_KGRP; cpos = search; continue;}
				search = data.findString(spos, spos + 1028, MAGIC_PCMD);
				if(search >= 0){chunk_magic = MAGIC_PCMD; cpos = search; continue;}
				break;
			}
			cpos += chunklen;
			chunk_magic = data.getASCII_string(cpos, 4); cpos+=12;
		}
		
		return swd;
	}
	
	/*----- Getters -----*/
	
	public String getName(){return fname;}
	public OffsetDateTime getLastModified(){return lastModified;}
	public boolean hasSoundData(){return sounds != null;}
	public boolean hasArticulationData(){return programs != null;}
	
	/*----- Setters -----*/
	
	public void setName(String s){fname = s;}
	
	/*----- Playback -----*/
	
	public boolean loadSoundDataFrom(SWD other_swd){
		if(other_swd == null) return false;
		if(!other_swd.hasSoundData()) return false;
		if(wavinfo == null) return false;
		
		sounds = new SWDWave[wavinfo.length];
		for(int i = 0; i < wavinfo.length; i++){
			if(wavinfo[i] != null){
				//Copy from the other swd
				sounds[i] = other_swd.sounds[i];
			}
		}
		
		return true;
	}
	
	private boolean prepareForPlayback(){
		if(!hasSoundData()) return false;
		if(!hasArticulationData()) return false;
		
		//Generate map
		smap = new SWDWaveMap(sounds.length);
		for(int i = 0; i < sounds.length; i++){
			smap.setWave(i, sounds[i]);
		}
		
		//Link map to programs
		for(int i = 0; i < programs.length; i++){
			if(programs[i] != null){
				programs[i].linkSoundMap(smap);
				//programs[i].scaleTuning(sounds);
			}
		}
		
		return true;
	}
	
	public SynthProgram getProgram(int bankIndex, int programIndex) {
		if(smap == null){
			if(!prepareForPlayback()){
				//throw new UnsupportedOperationException("Bank lacks sound or articulation data!");
				return null;
			}
		}
		
		if(programIndex < 0 || programIndex >= programs.length) return null;
		
		return programs[programIndex];
	}
	
	public void clearPlaybackCache(){
		//Delink and clear all programs
		if(programs != null){
			for(int i = 0; i < programs.length; i++){
				programs[i].clearSoundMapLink();
				programs[i].clearCache();
			}	
		}
		
		//Clear sound map
		if(smap != null){
			smap.free();
			smap = null;	
		}
	}
	
	/*----- Conversion -----*/
	
	public static String generateSoundKey(int idx){
		return "swd_wavi_" + String.format("%04d", idx);
	}
	
	public boolean writeSF2(String path){
		if(!hasSoundData()) return false;
		if(!hasArticulationData()) return false;
		
		//Instantiate Bank
		SimpleBank fullbank = new SimpleBank(fname, "VersionUnknown", "Procyon Studios", 1);
		
		//Build sound map
		for(int i = 0; i < sounds.length; i++){
			if(sounds[i] != null){
				fullbank.addSample(generateSoundKey(i), sounds[i]);
			}
		}
		
		//Build presets
		int idx = fullbank.newBank(0, fname+"_b0");
		SingleBank bank = fullbank.getBank(idx);
		
		for(int i = 0; i < programs.length; i++){
			if(programs[i] != null){
				//programs[i].scaleTuning(sounds);
				String pname = "prgi_" + String.format("%03d", i);
				idx = bank.newPreset(i, pname, 1);	
				SimplePreset p = bank.getPreset(idx);
				idx = p.newInstrument(pname, programs[i].countRegions());
				SimpleInstrument inst = p.getRegion(idx).getInstrument();
				programs[i].copyToInstrument(inst);
			}
		}
		
		//Write SF2
		try {
			SF2.writeSF2(fullbank, "waffleoJavaUtilsGX", false, path);
		} 
		catch (UnsupportedFileTypeException e) {
			e.printStackTrace();
			return false;
		} 
		catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public void dumpWavs(String dir){
		if(sounds == null) return;
		for(int i = 0; i < sounds.length; i++){
			String name = dir + File.separator + "swd_wavi_" + String.format("%04d", i) + ".wav";
			if(sounds[i] != null){
				//System.err.println("Dumping sound " + i);
				sounds[i].writeWAV(name);
			}
		}
	}
	
	/*----- Debug -----*/
	
	public void debugPrint(){
		System.err.println("======== SWD File ========");
		System.err.println("Internal Name: " + fname);
		System.err.println("Last Modified: " + lastModified.format(DateTimeFormatter.RFC_1123_DATE_TIME));
		System.err.println("Articulation Data: " + this.hasArticulationData());
		System.err.println("Sound Data: " + this.hasSoundData());
		System.err.println("-------- wavi --------");
		if(wavinfo != null){
			for(int i = 0; i < wavinfo.length; i++){
				System.err.println("-> WAVI " + i);
				if(wavinfo[i] == null){
					System.err.println("\t[Null]");
				}
				else{
					wavinfo[i].debugPrint(1);
				}
			}	
		}
		System.err.println("-------- prgi --------");
		if(programs != null){
			for(int i = 0; i < programs.length; i++){
				System.err.println("-> PRGI " + i);
				if(programs[i] == null){
					System.err.println("\t[Null]");
				}
				else{
					programs[i].debugPrint(1);
				}
			}
		}
	}

	public void dumpWaviTable(BufferedWriter out) throws IOException{
		out.write("Index\tEncoding\tSampleRate\tPCMDOffset\tLoops?\tLoopStart\tLoopLen\t");
		out.write("RootKey\tTune(Hex)\tTune(Dec)\tVolume\tPan\tKeyGroup\tEnvFlag\tEnvMult\t");
		out.write("AttLvl\tAtt\tHold\tDec\tSus\tRel\tArchdecay\n");
		
		for(int i = 0; i < wavinfo.length; i++){
			if(wavinfo[i] != null){
				wavinfo[i].debugPrintRecord(out);
				out.write("\n");
			}
		}
	}
	
	public void debugPrintWaviPositions(){
		System.err.println("Index\tStart(dec)\tStart\tEnd");
		for(int i = 0; i < wavinfo.length; i++){
			if(wavinfo[i] != null){
				wavinfo[i].printPCMDPos();
			}
		}
	}
	
	/*----- File Tree Partnering -----*/
	
	private static String searchDir(DirectoryNode dir, String fname, String bankuid, String path){
		
		List<DirectoryNode> cdirs = new LinkedList<DirectoryNode>();
		List<FileNode> children = dir.getChildren();
		
		//Search this level first
		for(FileNode child : children){
			if(child instanceof DirectoryNode){
				cdirs.add((DirectoryNode)child);
			}
			else{
				boolean matched = false;
				//If looking for a file name match, check that...
				if(fname != null){
					matched = (fname.equals(child.getFileName()));
				}
				//Otherwise if looking for a metadata match, check that...
				else if(bankuid != null){
					String uid = child.getMetadataValue(FNMETAKEY_BANKUID);
					if(uid != null){
						matched = (bankuid.equals(uid));
					}
				}
				
				if(matched) return path + child.getFileName();
			}
		}
		
		//Search dirs if nothing found
		for(DirectoryNode child : cdirs){
			String result = searchDir(child, fname, bankuid, path + child.getFileName() + "/");
			if(result != null) return result;
		}
		
		return null;
	}
	
	public static String findBankWithUID(String uid, FileNode start){
		
		String path = "";
		DirectoryNode dir = start.getParent();
		while(dir != null){
			String match = searchDir(dir, null, uid, path);
			if(match != null) return match;
			path = "../" + path;
			dir = dir.getParent();
		}
		
		return null;
	}
	
	public static String findSMDPartner(FileNode smd){
		//Looks for one with same file name, but .swd
		String swdname = smd.getFileName().replace(".smd", ".swd");
		
		String path = "";
		DirectoryNode dir = smd.getParent();
		while(dir != null){
			String match = searchDir(dir, swdname, null, path);
			if(match != null) return match;
			path = "../" + path;
			dir = dir.getParent();
		}
		
		return null;
	}
	
	public static void annotateBankUID(FileNode swd){
		int uid = swd.getFullPath().hashCode();
		swd.setMetadataValue(FNMETAKEY_BANKUID, Integer.toHexString(uid));
	}
	
	public static void partnerSMD(FileNode smd, String swd_path){
		FileNode swd = smd.getParent().getNodeAt(swd_path);
		if(swd.getMetadataValue(FNMETAKEY_BANKUID) == null) annotateBankUID(swd);
		smd.setMetadataValue(FNMETAKEY_SMDBANK, swd.getMetadataValue(FNMETAKEY_BANKUID));
		smd.setMetadataValue(FNMETAKEY_SMDPATH, swd_path);
	}
	
	public static void partnerSWD(FileNode swd, String pcmd_partner_path){
		FileNode pcmd_partner = swd.getParent().getNodeAt(pcmd_partner_path);
		if(pcmd_partner.getMetadataValue(FNMETAKEY_BANKUID) == null) annotateBankUID(pcmd_partner);
		swd.setMetadataValue(FNMETAKEY_PARTNER, pcmd_partner.getMetadataValue(FNMETAKEY_BANKUID));
		swd.setMetadataValue(FNMETAKEY_PARTNERPATH, pcmd_partner_path);
	}
	
	public boolean loadPartnerSWD(FileNode node) throws UnsupportedFileTypeException, IOException{
	
		//Check path first
		FileNode pnode = null;
		String ppath = node.getMetadataValue(FNMETAKEY_PARTNERPATH);
		if(ppath != null) pnode = node.getParent().getNodeAt(ppath);
		
		if(pnode == null){
			String puid = node.getMetadataValue(FNMETAKEY_PARTNER);
			if(puid == null) return false;
			ppath = findBankWithUID(puid, node);
			if(ppath == null) return false;
			partnerSWD(node, ppath);
			pnode = node.getParent().getNodeAt(ppath);
		}
			
		//Load the data
		return loadSoundDataFrom(SWD.readSWD(pnode.loadDecompressedData(), 0));
	}
	
	/*----- Definition -----*/
	
	private static SWDSoundbankDef static_def;
	
	public static SWDSoundbankDef getDefinition(){
		if(static_def == null) static_def = new SWDSoundbankDef();
		return static_def;
	}
	
	public static class SWDSoundbankDef extends SoundbankDef{

		private static final String DEFO_ENG_STR = "Procyon DS SoundBank";
		private static final String[] EXT_LIST = {"swd", "SWD", "swdl"};
		
		private String str;
		
		public SWDSoundbankDef(){
			str = DEFO_ENG_STR;
		}
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(EXT_LIST.length);
			for(String s : EXT_LIST)list.add(s);
			return list;
		}

		public String getDescription() {return str;}
		public int getTypeID() {return TYPE_ID;}
		public void setDescriptionString(String s) {str = s;}
		public String getDefaultExtension() {return "swd";}

		public SynthBank getPlayableBank(FileNode file) {
			try {
				FileBuffer dat = file.loadDecompressedData();
				SWD swd = SWD.readSWD(dat, 0);
				
				//Link sound partner, if known
				if(!swd.hasSoundData()){
					if(!swd.loadPartnerSWD(file)) return null;
				}
				
				return swd;
			} 
			catch (IOException e) {
				e.printStackTrace();
				return null;
			} 
			catch (UnsupportedFileTypeException e) {
				e.printStackTrace();
				return null;
			}
		}
		
		public String getBankIDKey(FileNode file){
			return file.getMetadataValue(FNMETAKEY_BANKUID);
		}
		
	}
	
	private static SWDConverter cdef;
	
	public static SWDConverter getDefaultConverter(){
		if(cdef == null) cdef = new SWDConverter();
		return cdef;
	}
	
	public static class SWDConverter implements Converter{

		public static final String DEFO_ENG_FROM = "Procyon SoundBank (.swd)";
		public static final String DEFO_ENG_TO = "SoundFont 2 (.sf2)";
		
		private String from_desc;
		private String to_desc;
		
		public SWDConverter(){
			from_desc = DEFO_ENG_FROM;
			to_desc = DEFO_ENG_TO;
		}
		
		public String getFromFormatDescription() {return from_desc;}
		public String getToFormatDescription() {return to_desc;}
		public void setFromFormatDescription(String s) {from_desc = s;}
		public void setToFormatDescription(String s) {to_desc = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			writeAsTargetFormat(FileBuffer.createBuffer(inpath), outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			try {
				SWD swd = SWD.readSWD(input, 0);
				if(!swd.hasArticulationData() || swd.hasSoundData()){
					throw new UnsupportedFileTypeException("SWD lacks sound or articulation data! (Cannot convert to sf2)");
				}
				swd.writeSF2(outpath);
			} 
			catch (Exception e) {
				e.printStackTrace();
				throw new UnsupportedFileTypeException("SWD/SF2 Conversion Error");
			}
		}
		
		public void writeAsTargetFormat(FileNode node, String outpath) 
				throws IOException, UnsupportedFileTypeException{
			try {
				SWD swd = SWD.readSWD(node.loadDecompressedData(), 0);
				if(!swd.hasArticulationData()) throw new UnsupportedFileTypeException("SWD lacks articulation data! (Cannot convert to sf2)");
				if(!swd.hasSoundData()){
					//Look for partner
					if(!swd.loadPartnerSWD(node)){
						throw new UnsupportedFileTypeException("SWD lacks sound data! (Cannot convert to sf2)");
					}
				}
				swd.writeSF2(outpath);
			} 
			catch (Exception e) {
				e.printStackTrace();
				throw new UnsupportedFileTypeException("SWD/SF2 Conversion Error");
			}
		}

		public String changeExtension(String path) {
			if(path == null) return null;
			if(path.isEmpty()) return path;
			
			int lastdot = path.lastIndexOf('.');
			if(lastdot < 0) return path + ".sf2";
			return path.substring(0, lastdot) + ".sf2";
		}
		
	}
	
}

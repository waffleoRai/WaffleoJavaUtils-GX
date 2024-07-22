package waffleoRai_soundbank.vab;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Files.Converter;
import waffleoRai_Files.FileClass;
import waffleoRai_Files.FileTypeDefinition;
import waffleoRai_Files.NodeMatchCallback;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileUtils;
import waffleoRai_Utils.MultiFileBuffer;
import waffleoRai_soundbank.SimpleBank;
import waffleoRai_soundbank.SingleBank;
import waffleoRai_soundbank.SoundbankDef;
import waffleoRai_soundbank.SoundbankNode;
import waffleoRai_soundbank.sf2.SF2;
import waffleoRai_soundbank.sf2.ADSR.ADSRC_RetainTime;

public class PSXVAB implements SynthBank{
	
	/* ----- Constants ----- */
	
	public static final String MAGIC_LE = "pBAV";
	
	public static final int MAX_PROGRAMS = 128;
	public static final int MAX_TONES_PER_PROGRAM = 16;
	
	public static final int HEADER_SIZE = 32;
	public static final int PTE_SIZE = 16;
	public static final int TTE_SIZE = 32;
	public static final int SPT_SIZE = 512;
	
	public static final int REVERB_AMOUNT = 1000; //Reverb send in 1/100 of a percent
	
	public static final String FNMETAKEY_BANKUID = "VABP_UID";
	public static final String FNMETAKEY_WARUID = "VB_UID";
	public static final String FNMETAKEY_BODY_PATH = "VABP_BODY_PATH";
	public static final String FNMETAKEY_BODY_ID = "VABP_BODY_ID";
	
	public static final String[] NOTES = {"C", "C#", "D", "Eb", 
										  "E", "F", "F#", "G",
										  "Ab", "A", "Bb", "B"};
	
	/* ----- Instance Variables ----- */
	
	private int version;
	private int ID;
	//private int waveformSize;
	
	private int masterVolume;
	private int masterPan;
	
	private int att1;
	private int att2;
	
	private VABProgram[] programs;
	private List<PSXVAG> samples;
	
	private boolean i1;
	private VABSynthProgram[] playables;
	
	private String name;
	
	/* ----- Construction ----- */
	
	public PSXVAB(int bankID){
		resetDefaults();
		ID = bankID;
		programs = new VABProgram[MAX_PROGRAMS];
		samples = new ArrayList<PSXVAG>(255);
	}
	
	public PSXVAB(String path) throws IOException, UnsupportedFileTypeException{
		this(FileBuffer.createBuffer(path, false));
	}
	
	public PSXVAB(FileBuffer vab) throws UnsupportedFileTypeException, IOException{
		resetDefaults();
		programs = new VABProgram[MAX_PROGRAMS];
		samples = new ArrayList<PSXVAG>(255);
		parseVAB(vab);
	}
	
	public PSXVAB(FileBuffer vab_head, FileBuffer vab_body) throws UnsupportedFileTypeException, IOException{
		resetDefaults();
		programs = new VABProgram[MAX_PROGRAMS];
		samples = new ArrayList<PSXVAG>(255);
		parseVAB(vab_head, vab_body);
	}
	
	public void resetDefaults(){
		version = 7;
		ID = 0;
		masterVolume = 0x7F;
		masterPan = 0x40;
		att1 = 0;
		att2 = 0;
	}
	
	/* ----- Parsing ----- */
	
	private void parseVAB(FileBuffer vab) throws UnsupportedFileTypeException, IOException{
		//Need to know program count to know how big header is.
		//Offset of program count is 0x12 (field is 2 bytes)
		long cPos = vab.findString(0, 0x10, MAGIC_LE);
		if (cPos < 0) throw new FileBuffer.UnsupportedFileTypeException();
		int progcount = (int)vab.shortFromFile(cPos+0x12);
		
		//Calculate header size
		int headersize = 32 + 2048 + 512;
		headersize += (progcount * 16 * 32);
		
		FileBuffer header = vab.createReadOnlyCopy(0, headersize);
		FileBuffer body = vab.createReadOnlyCopy(headersize, vab.getFileSize());
		parseVAB(header, body);
	}
	
	private void parseVAB(FileBuffer vab_head, FileBuffer vab_body) throws UnsupportedFileTypeException, IOException{
		parseVAB(vab_head.getReferenceAt(0L), vab_body);
	}
	
	private void parseVAB(BufferReference vab_head, FileBuffer vab_body) throws UnsupportedFileTypeException, IOException {
		if(vab_head == null) return;
		
		String mcheck = vab_head.nextASCIIString(4);
		if(!MAGIC_LE.equals(mcheck)) throw new UnsupportedFileTypeException("PSXVAB.parseVAB || Magic number not found!");
		
		vab_head.setByteOrder(false);
		
		//VAB header
		version = vab_head.nextInt();
		ID = vab_head.nextInt();
		vab_head.add(6L); //Skip VAB size and reserved
		int pCount = (int)vab_head.nextShort();
		vab_head.add(2L); //Skip tone count
		int sCount = (int)vab_head.nextShort();
		masterVolume = (int)vab_head.nextByte();
		masterPan = (int)vab_head.nextByte();
		att1 = Byte.toUnsignedInt(vab_head.nextByte());
		att2 = Byte.toUnsignedInt(vab_head.nextByte());
		vab_head.add(4L); //Skip more system reserved
		
		//Program table
		for (int i = 0; i < MAX_PROGRAMS; i++){
			programs[i] = VABProgram.readProgram(vab_head);
			if(programs[i] != null) programs[i].setProgramIndex(i);
		}
		
		//Tone table
		for(int p = 0; p < MAX_PROGRAMS; p++) {
			if(programs[p] == null) {
				//vab_head.add(VABTone.TONE_RECORD_SIZE << 4);
				continue;
			}
			
			int pUsedCount = programs[p].getToneCount();
			int t = 0;
			while(t < pUsedCount) {
				programs[p].setTone(VABTone.readTone(vab_head, t), t);
				t++;
			}
			while(t < 16) {
				vab_head.add(VABTone.TONE_RECORD_SIZE);
				t++;
			}
		}
		
		//SPT - 256 slots
		int[] sptrs = new int[256];
		for (int i = 0; i < 256; i++){
			short ptr = vab_head.nextShort();
			sptrs[i] = Short.toUnsignedInt(ptr) << 3;
		}
			
		//VAB Body (if provided)
		if(vab_body == null) return; //Might want something safer...
		
		vab_body.setEndian(false);
		long spos = 0;
		for (int i = 0; i < sCount; i++){
			int vaglen = sptrs[i+1];
			FileBuffer rawsamp = vab_body.createCopy(spos, spos+vaglen);
			PSXVAG parsedsamp = new PSXVAG(rawsamp);
			samples.add(parsedsamp);
			spos += vaglen;
		}
	}
	
	/* ----- Serialization ----- */
	//TODO Update all of these methods to use better output buffers
	
	public int getSampleChunkSize(){
		int tot = 0;
		for (PSXVAG s : samples){
			tot += s.getDataSize();
		}
		return tot;
	}
	
	private FileBuffer serializeHeader(){
		int pcount = countPrograms();
		int tcount = countTones();
		int scount = countSamples();
		int vbSize = getSampleChunkSize();
		FileBuffer header = new FileBuffer(HEADER_SIZE, false);
		header.printASCIIToFile(MAGIC_LE);
		header.addToFile(version);
		header.addToFile(ID);
		
		int waveformsize = HEADER_SIZE;
		waveformsize += (MAX_PROGRAMS * PTE_SIZE);
		waveformsize += (pcount * MAX_TONES_PER_PROGRAM * TTE_SIZE);
		waveformsize += 0x200; //Sample lookup table size
		waveformsize += vbSize;
		
		header.addToFile(waveformsize);
		header.addToFile((short)0xEEEE);
		header.addToFile((short)pcount);
		header.addToFile((short)tcount);
		header.addToFile((short)scount);
		
		header.addToFile((byte)masterVolume);
		header.addToFile((byte)masterPan);
		header.addToFile((byte)att1);
		header.addToFile((byte)att2);
	
		header.addToFile(0xFFFFFFFF);
		
		return header;
	}
	
	private FileBuffer serializeProgramTable(){
		if (programs == null) throw new IllegalStateException();
		FileBuffer PAT = new MultiFileBuffer(MAX_PROGRAMS);
		for (int i = 0; i < MAX_PROGRAMS; i++){
			if (programs[i] != null) PAT.addToFile(programs[i].serializePAT_entry());
			else PAT.addToFile(VABProgram.generateEmptyPAT_entry());
		}
		
		return PAT;
	}
	
	private FileBuffer serializeToneTable(){
		if (programs == null) throw new IllegalStateException();
		FileBuffer TAT = new MultiFileBuffer(MAX_PROGRAMS);
		for (int i = 0; i < MAX_PROGRAMS; i++){
			if (programs[i] != null) {
				TAT.addToFile(programs[i].serializeTAT_entry());
			}
		}
		
		return TAT;
	}
	
	private FileBuffer serializeSamplePointerTable(){
		FileBuffer SPT = new FileBuffer(SPT_SIZE, false);
		SPT.addToFile((short)0x0000);
		
		int i = 0;
		for (PSXVAG s : samples){
			int ssz = s.getDataSize();
			ssz = ssz >>> 3;
			SPT.addToFile((short)ssz);
			i++;
		}
		
		while (i < 255){
			SPT.addToFile((short)0x0000);
			i++;
		}
		
		return SPT;
	}
	
	private FileBuffer serializeSampleChunk(){
		int scount = countSamples();
		FileBuffer schunk = new MultiFileBuffer(scount);
		
		for (PSXVAG s : samples){
			schunk.addToFile(s.serializeVAGData());
		}
		
		return schunk;
	}
	
	public FileBuffer serializeVabHead(){
		FileBuffer myVAB = new MultiFileBuffer(4);
		myVAB.addToFile(serializeHeader());
		myVAB.addToFile(serializeProgramTable());
		myVAB.addToFile(serializeToneTable());
		myVAB.addToFile(serializeSamplePointerTable());
		
		return myVAB;
	}
	
	public FileBuffer serializeVabBody(){
		return serializeSampleChunk();
	}
	
	public FileBuffer serializeMe(){
		FileBuffer myVAB = new MultiFileBuffer(5);
		myVAB.addToFile(serializeHeader());
		myVAB.addToFile(serializeProgramTable());
		myVAB.addToFile(serializeToneTable());
		myVAB.addToFile(serializeSamplePointerTable());
		myVAB.addToFile(serializeSampleChunk());
		
		return myVAB;
	}
	
	public void writeVAB(String outpath) throws IOException{
		FileBuffer me = serializeMe();
		me.writeFile(outpath);
	}
	
	/* ----- Getters ----- */
	
	public int getVersion(){return version;}
	public int getID(){return ID;}
	public int getVolume(){return masterVolume;}
	public int getPan(){return masterPan;}
	public int getAttribute1(){return att1;}
	public int getAttribute2(){return att2;}

	public VABProgram getProgram(int i){
		if (i < 0) return null;
		if (i >= 128) return null;
		return programs[i];
	}
	
	public PSXVAG getSample(int i){
		if (i < 0) return null;
		if (i >= samples.size()) return null;
		return samples.get(i);
	}
	
	public int countPrograms(){
		int tot = 0;
		if (programs == null) return 0;
		for (int i = 0; i < programs.length; i++){
			if (programs[i] != null) tot++;
		}
		return tot;
	}
	
	public int countTones(){
		int tot = 0;
		if (programs == null) return 0;
		for (int i = 0; i < programs.length; i++){
			if (programs[i] != null) {
				tot += programs[i].getToneCount();
			}
		}
		return tot;
	}
	
	public int countSamples(){
		return samples.size();
	}
	
	public String getName(){return name;}
	
	/* ----- Setters ----- */
	
	public void setName(String s){name = s;}
	
	public void setVersion(int val){version = val;}
	public void setMasterVolume(byte val){masterVolume = val;}
	public void setMasterPan(byte val){masterPan = val;}
	public void setBankAttr1(byte val){att1 = val;}
	public void setBankAttr2(byte val){att2 = val;}
	
	public VABProgram newProgram(int index){
		if(index < 0) return null;
		if(index >= 128) return null;
		programs[index] = new VABProgram();
		programs[index].setProgramIndex(index);
		return programs[index];
	}
	
	public void addSample(PSXVAG sample) {
		samples.add(sample);
	}
	
	/* ----- Playback ----- */
	
	public void setOneBasedIndexing(boolean b){
		i1 = b;
	}
	
	public VABSampleMap generateSampleMap(){
		return new VABSampleMap(samples);
	}
	
	public SynthProgram getProgram(int bankIndex, int programIndex){
		if(playables == null){
			playables = new VABSynthProgram[128];
			VABSampleMap sm = generateSampleMap();
			for(int i = 0; i < 128; i++){
				playables[i] = new VABSynthProgram(this, sm, i);
			}
		}
		
		if(i1) programIndex--;
		return playables[programIndex];
	}
	
	public Collection<Integer> getUsableBanks(){
		List<Integer> list = new ArrayList<Integer>(2);
		if(samples != null && !samples.isEmpty()) list.add(0);
		return list;
	}
	
	public Collection<Integer> getUsablePrograms(){
		if(samples == null && samples.isEmpty()){
			return new LinkedList<Integer>();
		}
		
		List<Integer> list = new ArrayList<Integer>(128);
		for(int i = 0; i < 128; i++){
			if(programs[i] != null) list.add(i);
		}
		
		return list;
	}
	
	/* ----- Conversion ----- */
	
	public static int scaleVibratoWidth(int vibWidth){return 0;}
	public static int scaleVibratoTime(int vibTime){return 0;}
	public static int scalePortamentoWidth(int pWidth){return 0;}
	public static int scalePortamentoTime(int pTime){return 0;}
	
	public static int scaleVolume(int toneVolume){
		double vol = (double)toneVolume / (double)0x7F;
		return (int)Math.round(vol * (double)0x7FFFFFFF);
	}
	
	public static short scalePan(int tonePan){
		tonePan -= 0x40;
		double ratio = (double)tonePan / (double)0x3F;
		return (short)Math.round(ratio * (double)0x7FFF);
	}
	
	public static String generateSampleKey(int number){
		return "PSX_VAG_" + Integer.toString(number);
	}
	
	public SimpleBank getSoundbank(){
		SimpleBank mybank = new SimpleBank("VABp_" + ID, Integer.toString(version), "Sony", 1);
		int idx = mybank.newBank(0, "VAB_Bank");
		
		SingleBank bank = mybank.getBank(idx);
		
		//Add samples
		int si = 0;
		for (PSXVAG s : samples){
			mybank.addSample(generateSampleKey(si), s);
			si++;
		}
		
		//Add programs
		for (int i = 0; i < MAX_PROGRAMS; i++){
			VABProgram p = programs[i];
			if (p != null){
				p.addToSoundbank(bank, i);
			}
		}
		
		return mybank;
	}

	public SoundbankNode getBankTree(String bankname){
		SoundbankNode root = new SoundbankNode(null, bankname, SoundbankNode.NODETYPE_BANK);
		
		root.addMetadataEntry("Volume", String.format("0x%02x", masterVolume));
		root.addMetadataEntry("Pan", String.format("0x%02x", masterPan));
		root.addMetadataEntry("Attribute 1", String.format("0x%04x", att1));
		root.addMetadataEntry("Attribute 2", String.format("0x%04x", att2));
		
		int pcount = 0;
		for(int i = 0; i < programs.length; i++){
			if(programs[i] != null){
				pcount++;
				programs[i].toSoundbankNode(root, String.format("Program %03d", i));
			}
		}
		
		root.addMetadataEntry("Program Count", Integer.toString(pcount));
		root.addMetadataEntry("Sound Sample Count", Integer.toString(samples.size()));
		
		return root;
	}
	
	public void dumpReport(String reportPath) throws IOException {
		if (reportPath != null && !reportPath.isEmpty()) {
			FileWriter fw = new FileWriter(reportPath);
			BufferedWriter bw = new BufferedWriter(fw);
			printInfoToBuffer(bw);
			bw.close();
			fw.close();
		}
	}
	
	public SF2 getSF2(String toolname) {
		//Make soundbank
		SimpleBank sb = getSoundbank();
		
		//Set ADSR Converter
		SF2.setADSRConverter(new ADSRC_VAB(120, 150, 120, true));
		
		//Dump to SF2
		SF2 mysf = SF2.createSF2(sb, toolname, false);
		
		//Reset ADSR Converter
		SF2.setADSRConverter(new ADSRC_RetainTime());
		
		return mysf;
	}
	
	public void writeSoundFont2(String sf2path, String reportPath, String toolname) throws IOException, UnsupportedFileTypeException {
		//If reportpath is valid, write that first
		if (reportPath != null && !reportPath.isEmpty()) {
			dumpReport(reportPath);
		}
		
		//Make soundbank
		SimpleBank sb = getSoundbank();
		
		//Set ADSR Converter
		SF2.setADSRConverter(new ADSRC_VAB(120, 150, 120, true));
		
		//Dump to SF2
		SF2.writeSF2(sb, toolname, false, sf2path);
		
		//Reset ADSR Converter
		SF2.setADSRConverter(new ADSRC_RetainTime());
	}
	
	public void printInfoToBuffer(BufferedWriter bw) throws IOException {
		if (bw == null) return;
		bw.write("=============================== PlayStation 1 VAB Soundbank ===============================\n");
		bw.write("Dump Date: " + OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\n");
		bw.write("VAB Version: " + version + "\n");
		bw.write("VAB ID: " + ID + "\n");
		bw.write("Master Volume: " + String.format("0x%02X", masterVolume) + "\n");
		bw.write("Master Pan: " + String.format("0x%02X", masterPan) + "\n");
		bw.write("Attribute 1: " + String.format("0x%02X", att1) + "\n");
		bw.write("Attribute 2: " + String.format("0x%02X", att2) + "\n");
		bw.write("Sound Sample Count: " + samples.size() + "\n");
		bw.write("\n");
		
		//Write the programs
		if (programs == null) {
			bw.write("[No Programs]\n");
			return;
		}
		for (int i = 0; i < programs.length; i++) {
			System.err.println("Writing program " + i);
			VABProgram p = programs[i];
			bw.write("--------- PROGRAM " + i + " ---------\n");
			if (p == null) bw.write("\t[Null]\n");
			else {
				p.printInfoToBuffer(1, bw);
			}
			bw.write("\n");
		}
	}
	
	/* ----- VH/VB Node Linking ----- */
	
	public static boolean linkVABNodes(FileNode vh, FileNode vb){
		if(vh == null) return false;
		if(vb == null) return false;
		
		//See if vb has an ID already
		String vbid = vb.getMetadataValue(FNMETAKEY_BODY_ID);
		if(vbid == null){
			//Generate one.
			String vbpath = vb.getFullPath();
			//Hash.
			byte[] phash = FileUtils.getSHA256Hash(vbpath.getBytes());
			//Take the first 8 bytes.
			StringBuilder sb = new StringBuilder(16);
			for(int i = 0; i < 8; i++) sb.append(String.format("%02x", phash[i]));
			vbid = sb.toString();
			vb.setMetadataValue(FNMETAKEY_BODY_ID, vbid);
		}
		
		//Get relative path of vb
		DirectoryNode parent = vh.getParent();
		String rpath = vb.getFullPath();
		if(parent != null){
			rpath = parent.findNodeThat(new NodeMatchCallback(){

				public boolean meetsCondition(FileNode n) {
					return n == vb;
				}
				
			});
			if(rpath == null || rpath.isEmpty()) rpath = vb.getFullPath();
		}
		
		//Set meta values
		vh.setMetadataValue(FNMETAKEY_BODY_ID, vbid);
		vh.setMetadataValue(FNMETAKEY_BODY_PATH, rpath);
		
		return true;
	}
	
	public static FileNode findVABBody(FileNode vh){
		if(vh == null) return null;
		
		//Find one already linked...
		FileNode vb = null;
		vb = FileUtils.findPartnerNode(vh, FNMETAKEY_BODY_PATH, FNMETAKEY_BODY_ID);

		if(vb != null){
			//System.err.println("VB node: " + vb.getFullPath());
			return vb;
		}
		
		//vb not linked. Look for a good candidate.
		DirectoryNode parent = vh.getParent();
		if(parent == null) return null;
		
		//Look for one with same name.
		String name = vh.getFileName();
		name.replace(".vh", ".vb");
		String vbpath = vh.findNodeThat(new NodeMatchCallback(){

			public boolean meetsCondition(FileNode n) {
				return n.getFileName().equalsIgnoreCase(name);
			}
			
		});
		if(vbpath != null && !vbpath.isEmpty()){
			vb = parent.getNodeAt(vbpath);
			if(vb != null){
				linkVABNodes(vh, vb);
				return vb;
			}
		}
		
		//If none, look for closest vh in same dir
		List<FileNode> sibs = parent.getChildren();
		Collections.sort(sibs);
		FileNode[] sibs_arr = new FileNode[sibs.size()];
		int i = 0;
		for(FileNode c : sibs) sibs_arr[i++] = c;
		
		//Look for vh
		int myidx = -1;
		for(i = 0; i < sibs_arr.length; i++){
			if(vh == sibs_arr[i]){
				myidx = i;
				break;
			}
		}
		
		//Search up and down for first node ending in .vb
		i = 1;
		boolean min = false;
		boolean max = false;
		while(!min || !max){
			
			int idx = myidx - i;
			if(idx >= 0){
				FileNode upnode = sibs_arr[idx];
				if(!upnode.isDirectory() && upnode.getFileName().endsWith(".vb")){
					vb = upnode;
					break;
				}
			}
			else min = true;
			
			idx = myidx + i;
			if(idx < sibs_arr.length){
				FileNode downnode = sibs_arr[idx];
				if(!downnode.isDirectory() && downnode.getFileName().endsWith(".vb")){
					vb = downnode;
					break;
				}
			}
			else max = true;
			
			i++;
		}
		
		if(vb != null){
			//Link and return
			linkVABNodes(vh, vb);
			return vb;
		}
		
		//No easy match found
		return null;
	}
	
	/* ----- Definitions ----- */
	
	public static final int DEF_ID = 0x70424156;
	private static final String DEFO_ENG_STR = "PlayStation 1 VAB Soundbank";
	
	public static final int DEF_ID_HEAD = 0x68424156;
	private static final String DEFO_ENG_STR_HEAD = "PlayStation 1 VAB Articulation Data";
	
	public static final int DEF_ID_BODY = 0x62424156;
	private static final String DEFO_ENG_STR_BODY = "PlayStation 1 VAB Wave Archive";
	
	private static VABPDef stat_def;
	private static VABPHeadDef stat_def_h;
	private static VABPBodyDef stat_def_b;
	private static VABP2SF2Conv stat_conv;
	
	public static class VABPDef extends SoundbankDef{
		
		private String desc = DEFO_ENG_STR;
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("vab");
			list.add("vabp");
			return list;
		}

		public String getDescription() {return desc;}

		public int getTypeID() {return DEF_ID;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "vab";}

		public SynthBank getPlayableBank(FileNode file) {
			
			try {
				FileBuffer dat = file.loadDecompressedData();
				PSXVAB vab = new PSXVAB(dat);
				return vab;
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

		public String getBankIDKey(FileNode file) {
			if(file == null) return null;
			return file.getMetadataValue(FNMETAKEY_BANKUID);
		}

		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class VABPHeadDef extends SoundbankDef{
		
		private String desc = DEFO_ENG_STR_HEAD;
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("vh");
			list.add("vab");
			return list;
		}

		public String getDescription() {return desc;}

		public int getTypeID() {return DEF_ID_HEAD;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "vh";}

		public SynthBank getPlayableBank(FileNode file) {
			
			try {
				//Look for matching VB
				FileNode vb = findVABBody(file);
				
				FileBuffer h_dat = file.loadDecompressedData();
				FileBuffer b_dat = vb.loadDecompressedData();
				PSXVAB vab = new PSXVAB(h_dat, b_dat);
				return vab;
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

		public String getBankIDKey(FileNode file) {
			if(file == null) return null;
			return file.getMetadataValue(FNMETAKEY_BANKUID);
		}

		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static class VABPBodyDef implements FileTypeDefinition{
		
		private String desc = DEFO_ENG_STR_BODY;
		
		public Collection<String> getExtensions() {
			List<String> list = new ArrayList<String>(2);
			list.add("vb");
			return list;
		}

		public String getDescription() {return desc;}

		public int getTypeID() {return DEF_ID_BODY;}
		public void setDescriptionString(String s) {desc = s;}
		public String getDefaultExtension() {return "vb";}

		public FileClass getFileClass() {return FileClass.SOUND_WAVEARC;}

		public String toString(){return FileTypeDefinition.stringMe(this);}
		
	}
	
	public static VABPDef getDefinition(){
		if(stat_def == null) stat_def = new VABPDef();
		return stat_def;
	}
	
	public static VABPHeadDef getHeadDefinition(){
		if(stat_def_h == null) stat_def_h = new VABPHeadDef();
		return stat_def_h;
	}
	
	public static VABPBodyDef getBodyDefinition(){
		if(stat_def_b == null) stat_def_b = new VABPBodyDef();
		return stat_def_b;
	}
	
	public static class VABP2SF2Conv implements Converter{
		
		private String desc_from = DEFO_ENG_STR;
		private String desc_to = "SoundFont 2 Bank (.sf2)";

		public String getFromFormatDescription() {return desc_from;}
		public String getToFormatDescription() {return desc_to;}
		public void setFromFormatDescription(String s) {desc_from = s;}
		public void setToFormatDescription(String s) {desc_to = s;}

		public void writeAsTargetFormat(String inpath, String outpath)
				throws IOException, UnsupportedFileTypeException {
			FileBuffer dat = FileBuffer.createBuffer(inpath, false);
			writeAsTargetFormat(dat, outpath);
		}

		public void writeAsTargetFormat(FileBuffer input, String outpath)
				throws IOException, UnsupportedFileTypeException {
			//Assumes full VAB
			PSXVAB vab = new PSXVAB(input);
			String rpath = generateReportPath(outpath);
			vab.writeSoundFont2(outpath, rpath, "WaffleoUtilsGX");
		}

		public void writeAsTargetFormat(FileNode node, String outpath)
				throws IOException, UnsupportedFileTypeException {
			
			//Main one (can link to vb if needed)
			if(node == null) return;
			PSXVAB vab = null;
			
			String name = node.getFileName();
			if(name.endsWith(".vh")){
				//Header only. Load body.
				FileNode vb = PSXVAB.findVABBody(node);
				FileBuffer vhdat = node.loadDecompressedData();
				FileBuffer vbdat = null;
				if(vb != null) vbdat = vb.loadDecompressedData();
				
				vab = new PSXVAB(vhdat, vbdat);
			}
			else{
				//Full VAB
				vab = new PSXVAB(node.loadDecompressedData());
			}
			
			String rpath = generateReportPath(outpath);
			vab.writeSoundFont2(outpath, rpath, "WaffleoUtilsGX");
		}
		
		public String generateReportPath(String outpath){
			if(outpath == null) return null;
			int lastdot = outpath.lastIndexOf('.');
			if(lastdot >= 0){
				return outpath.substring(0, lastdot) + "_report.txt";
			}
			else return outpath + "_report.txt";
		}

		public String changeExtension(String path) {
			if(path.endsWith(".vh")) return path.replace(".vh", ".sf2");
			return path.replace(".vab", ".sf2");
		}
		
	}
	
	public static VABP2SF2Conv getConverter(){
		if(stat_conv == null) stat_conv = new VABP2SF2Conv();
		return stat_conv;
	}
	
}

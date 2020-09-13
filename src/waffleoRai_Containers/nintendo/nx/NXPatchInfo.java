package waffleoRai_Containers.nintendo.nx;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import waffleoRai_Encryption.AES;
import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.FileCryptTable;
import waffleoRai_Encryption.nintendo.NinCTRCryptRecord;
import waffleoRai_Encryption.nintendo.NinCrypto;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Files.tree.PatchworkFileNode;
import waffleoRai_Utils.FileBuffer;

public class NXPatchInfo {

	/* --- Instance Variables --- */
	
	private long virtual_size;
	private long patch_size;
	
	private List<RelocBucket> reloc;
	private List<PatchSecBucket> patchsec;
	
	/* --- Structs --- */
	
	protected static class RelocBucket{
		public long v_addr;
		public long bucket_end;
		
		public List<RelocEntry> entries;
		
		public RelocBucket(){
			entries = new LinkedList<RelocEntry>();
		}
	}
	
	protected static class RelocEntry implements Comparable<RelocEntry>{
		public long v_address;
		public long src_address;
		public boolean from_patch; //If false, from base
		
		public long end_addr; //Scratch field for lookup!

		public int compareTo(RelocEntry o) {
			//Sorted by v_address...
			if(this.v_address > o.v_address) return 1;
			else if (this.v_address < o.v_address) return -1;
			return 0;
		}
	}
	
	protected static class PatchSecBucket{
		public long p_addr;
		public long bucket_end;
		
		public List<PatchSecEntry> entries;
		
		public PatchSecBucket(){
			entries = new LinkedList<PatchSecEntry>();
		}
	}
	
	protected static class PatchSecEntry implements Comparable<PatchSecEntry>{
		public long address;
		public int sec_ctr;
		
		public long entry_uid; //For crypt table, not in NX specification
		
		public long end_addr; //Scratch field for lookup!
		//public int idx; //Scratch field for lookup!

		public int compareTo(PatchSecEntry o) {
			//Sorted by address...
			if(this.address > o.address) return 1;
			else if (this.address < o.address) return -1;
			return 0;
		}
	}
	
	/* --- Construction --- */
	
	public NXPatchInfo(){
		virtual_size = 0L;
		patch_size = 0L;
		reloc = new LinkedList<RelocBucket>();
		patchsec = new LinkedList<PatchSecBucket>();
	}
	
	/* --- Parsing --- */
	
	public static NXPatchInfo readFromNCA(FileBuffer data, long bktr_off1, long bktr_off2){
		data.setEndian(false);
		data.setCurrentPosition(bktr_off1+4);
		NXPatchInfo patch = new NXPatchInfo();
		
		//BKTR 1 (Relocation)
		int bcount = data.nextInt();
		patch.virtual_size = data.nextLong();
		long[] boff = new long[bcount];
		for(int i = 0; i < bcount; i++){
			boff[i] = data.nextLong();
		}
		for(int i = 0; i < bcount; i++){
			data.setCurrentPosition(bktr_off1 + (0x4000 * (i+1)));
			data.skipBytes(4);
			int ecount = data.nextInt();
			long endoff = data.nextLong();
			
			RelocBucket bucket = new RelocBucket();
			bucket.v_addr = boff[i];
			bucket.bucket_end = endoff;
			
			for(int j = 0; j < ecount; j++){
				RelocEntry entry = new RelocEntry();
				entry.v_address = data.nextLong();
				entry.src_address = data.nextLong();
				if(data.nextInt() != 0) entry.from_patch = true;
				bucket.entries.add(entry);
			}
			
			patch.reloc.add(bucket);
		}
		
		//BKTR 2 (Patch Sections)
		data.setCurrentPosition(bktr_off2+4);
		bcount = data.nextInt();
		patch.patch_size = data.nextLong();
		boff = new long[bcount];
		for(int i = 0; i < bcount; i++){
			boff[i] = data.nextLong();
		}
		for(int i = 0; i < bcount; i++){
			data.setCurrentPosition(bktr_off2 + (0x4000 * (i+1)));
			data.skipBytes(4);
			int ecount = data.nextInt();
			long endoff = data.nextLong();
			
			PatchSecBucket bucket = new PatchSecBucket();
			bucket.p_addr = boff[i];
			bucket.bucket_end = endoff;
			
			for(int j = 0; j < ecount; j++){
				PatchSecEntry entry = new PatchSecEntry();
				entry.address = data.nextLong();
				data.skipBytes(4);
				entry.sec_ctr = data.nextInt();
				bucket.entries.add(entry);
			}
			patch.patchsec.add(bucket);
		}
		
		return patch;
	}
	
	/* --- Getters --- */
	
	public long getVirtualSize(){return virtual_size;}
	public long getPatchSize(){return patch_size;}
	
	public int countRelocationEntries(){
		int total = 0;
		for(RelocBucket b : reloc) total += b.entries.size();
		return total;
	}
	
	public int countSubsectionEntries(){
		int total = 0;
		for(PatchSecBucket b : patchsec) total += b.entries.size();
		return total;
	}
	
	/* --- Setters --- */
	
	/* --- Patching --- */
	
	private List<PatchSecEntry> getEntriesAt(long stoff, long len, int start, List<PatchSecEntry> list){

		List<PatchSecEntry> out = new LinkedList<PatchSecEntry>();
		
		int lsize = list.size();
		long edoff = stoff + len;
		
		//Find first entry (entry covering start position)
		//Better hope that list is an Array...
		PatchSecEntry e0 = null;
		int i = lsize/2;
		int imin = 0;
		int imax = lsize;
		while(e0 == null && (imax - imin) > 0){ //Search area is >0 entries
			PatchSecEntry e = list.get(i);
			if(e.address > stoff){
				//Comes after what we're looking for. Go left.
				imax = i;
				i = (imax-imin)/2 + imin;
				continue;
			}
			if(e.end_addr <= stoff){
				//Comes before what we're looking for. Go right.
				imin = i;
				i = (imax-imin)/2 + imin;
				continue;
			}
			
			//stoff may fall in this entry
			e0 = e;
		}
		
		if(e0 == null) return out; //Nothing found :(
		out.add(e0);
		
		//Determine if we need more entries...
		while(edoff > e0.end_addr){
			e0 = list.get(++i);
			out.add(e0);
		}
		
		Collections.sort(out);
		return out;
	}
	
 	private static long genBlockUID(byte[] partkey, PatchSecEntry e){
		byte[] dat1 = FileBuffer.numToByStr(e.address);
		byte[] dat2 = FileBuffer.numToByStr(e.sec_ctr);
		byte[] dat = new byte[16];
		for(int i = 0; i < 8; i++) dat[i] = dat1[i];
		for(int i = 0; i < 4; i++) dat[i+8] = dat2[i];
		
		AES aes = new AES(partkey);
		aes.genKeySchedule();
		byte[] ndat = aes.rijndael_enc(dat);
		
		long out = 0L;
		for(int i = 0; i < 8; i++){
			out |= Byte.toUnsignedLong(ndat[i]);
			out = out << 8;
		}
		
		e.entry_uid = out;
		
		return out;
	}
	
	public Collection<FileCryptRecord> addEncryptionInfo(byte[] partkey, byte[] basectr, FileCryptTable table){
		//TODO
		
		List<FileCryptRecord> rlist = new LinkedList<FileCryptRecord>();
		
		//Determine the key index...
		int kidx = table.getIndexOfKey(NinCrypto.KEYTYPE_128, partkey);
		if(kidx < 0) kidx = table.addKey(NinCrypto.KEYTYPE_128, partkey);
		
		for(PatchSecBucket bucket : patchsec){
			for(PatchSecEntry entry : bucket.entries){
				genBlockUID(partkey, entry);
				FileCryptRecord rec = new NinCTRCryptRecord(entry.entry_uid);
				rec.setKeyType(NinCrypto.KEYTYPE_128);
				rec.setKeyIndex(kidx);
				
				//Offset TODO
				//Should be relative to Patch IVFC start! (May need adding to!!)
				rec.setCryptOffset(entry.address);
				
				//CTR
				//Seems to replace the "generation" field, or the second highest word
				//TODO is the lower doubleword (offset part) relative to subsec or patch NCA?
				byte[] myctr = Arrays.copyOf(basectr, 16);
				int shamt = 24;
				for(int i = 0; i < 4; i++){
					int b = (entry.sec_ctr >>> shamt) & 0xFF;
					myctr[i+4] = (byte)b;
					shamt-=8;
				}
				myctr = NXCrypt.adjustCTR(myctr, entry.address);
				rec.setIV(myctr);
				
				//Add
				rlist.add(rec);
				table.addRecord(entry.entry_uid, rec);
			}
		}
		
		return rlist;
	}
	
	public PatchworkFileNode generatePatchedImage(FileNode base_img, FileNode patch_img){
		//base_img and patch_img node offsets should be relative to start of IVFC (the hashy crap, not RomFS header)
		
		//Should be in order, but sort just to make sure...
		List<RelocEntry> reloc_entries = new ArrayList<RelocEntry>(countRelocationEntries()+1);
		List<PatchSecEntry> sec_entries = new ArrayList<PatchSecEntry>(countSubsectionEntries()+1);
		
		for(RelocBucket b : reloc) reloc_entries.addAll(b.entries);
		Collections.sort(reloc_entries);
		
		for(PatchSecBucket b : patchsec) sec_entries.addAll(b.entries);
		Collections.sort(sec_entries);
		
		//Mark end positions (to make life a bit easier)
		int rentries = reloc_entries.size();
		for(int i = 0; i < rentries; i++){
			RelocEntry e0 = reloc_entries.get(i);
			if(i < (rentries - 1)){
				e0.end_addr = reloc_entries.get(i+1).v_address;
			}
			else e0.end_addr = virtual_size;
		}
		int sentries = sec_entries.size();
		for(int i = 0; i < sentries; i++){
			PatchSecEntry e0 = sec_entries.get(i);
			//e0.idx = i;
			if(i < (sentries - 1)){
				e0.end_addr = sec_entries.get(i+1).address;
			}
			else e0.end_addr = patch_size;
		}
		
		//Generate the output node
		Random r = new Random();
		PatchworkFileNode pfn = new PatchworkFileNode(null, "patchimg" + Long.toHexString(r.nextLong()), reloc_entries.size());
		pfn.generateGUID();
		pfn.setComplexMode(true);
		
		//Crypto stuff
		String cgroupid = base_img.getMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID);
		
		//Generate pieces
		int last_subsec_idx = sec_entries.size()/2; //Search start
		for(int i = 0; i < rentries; i++){
			//Get entries
			RelocEntry e0 = reloc_entries.get(i);
			RelocEntry e1 = null;
			if(i < (rentries-1)) e1 = reloc_entries.get(i+1);
			
			//Calculate chunk size
			long vend = virtual_size;
			if(e1 != null) vend = e1.v_address;
			long vsize = vend - e0.v_address;
			
			if(e0.from_patch){
				//Find subsection(s). Hopefully does not go across subsec boundaries
				//I would think it wouldn't, but I'm not 100% sure
				List<PatchSecEntry> pse = getEntriesAt(e0.src_address, vsize, last_subsec_idx, sec_entries);
				//System.err.println("Reloc chunk " + i + ": Subsec entries found: " + pse.size());
				//System.err.println("vsize = 0x" + Long.toHexString(vsize));
				long src_ed = e0.src_address + vsize;
				/*if(!pse.isEmpty()){
					//If it IS empty, we have a problem...
					last_subsec_idx = pse.get(0).idx; //Set next search start...
				}*/
				
				for(PatchSecEntry s : pse){
					//For each subsection, create a node for the Patchwork w/crypt table ID
					//System.err.println("Reloc src: 0x" + Long.toHexString(e0.src_address));
					//System.err.println("Patch Subsec Start: 0x" + Long.toHexString(s.address));
					
					long st = e0.src_address<s.address?s.address:e0.src_address; //Want the higher one.
					long ed = src_ed<s.end_addr?src_ed:s.end_addr; //Want the lower one.
					FileNode snode = patch_img.getSubFile(st, (ed-st));
					snode.setMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID, Long.toHexString(s.entry_uid));
					pfn.addBlock(snode);
				}
				
			}
			else{
				//Don't forget to copy the crypt table entry ID...
				FileNode pnode = base_img.getSubFile(e0.src_address, vsize);
				if(cgroupid != null) pnode.setMetadataValue(NinCrypto.METAKEY_CRYPTGROUPUID, cgroupid);
				pfn.addBlock(pnode);
			}
			//if(i >= 10) break; //DEBUG!!!
		}
		
		return pfn;
	}
	
	/* --- Debug --- */
	
	public void printMe(Writer out) throws IOException{
		out.write("NCA BKTR Partition - Patch Table\n");
		out.write("Virtual Image Size: 0x" + Long.toHexString(virtual_size) + "\n");
		out.write("Patch Image Size: 0x" + Long.toHexString(patch_size) + "\n");
		
		out.write("Relocation Info - (" + reloc.size() + " buckets) ---\n");
		int i = 0;
		for(RelocBucket b : reloc){
			out.write("-> Bucket " + (i++) + "\n");
			out.write("Base Address: 0x" + Long.toHexString(b.v_addr) + "\n");
			out.write("End: 0x" + Long.toHexString(b.bucket_end) + "\n");
			out.write("Entry Count: " + b.entries.size() + "\n");
			int j = 0;
			for(RelocEntry e : b.entries){
				out.write("\t->Entry " + (j++) + ": ");
				out.write("0x" + Long.toHexString(e.src_address) + " -> 0x" + Long.toHexString(e.v_address));
				out.write(" (");
				if(e.from_patch) out.write("patch)\n");
				else out.write("base)\n");
			}
		}
		
		out.write("\n");
		out.write("Patchsec Info - (" + patchsec.size() + " buckets) ---\n");
		i = 0;
		for(PatchSecBucket b : patchsec){
			out.write("-> Bucket " + (i++) + "\n");
			out.write("Base Address: 0x" + Long.toHexString(b.p_addr) + "\n");
			out.write("End: 0x" + Long.toHexString(b.bucket_end) + "\n");
			out.write("Entry Count: " + b.entries.size() + "\n");
			int j = 0;
			for(PatchSecEntry e : b.entries){
				out.write("\t->Entry " + (j++) + ": ");
				out.write("0x" + Long.toHexString(e.address) + ", CTRFactor = " + e.sec_ctr + "\n");
			}
		}
		
	}
	
}

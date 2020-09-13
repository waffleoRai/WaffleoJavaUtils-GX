package waffleoRai_Containers.nintendo.nx;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Encryption.AES;
import waffleoRai_Files.GenericSystemDef;
import waffleoRai_Utils.FileBuffer;

/*
 *Source: https://switchbrew.org/wiki/CNMT
 *It's a bit vague on the overarching structure.
 * So I'll note that here...
 * 
 * CNMT Header
 * Extended Header (If present)
 * Content Info (0x38 * #contents)
 * Extended Data (If present)
 * SHA-256 Hash (0x20)
 * 
 */

public class NXContentMeta {
	
	/*----- Constants -----*/
	
	public static final int CNMT_TYPE_UNK = 0x0;
	public static final int CNMT_TYPE_SYSPROGRAM = 0x1;
	public static final int CNMT_TYPE_SYSDATA = 0x2;
	public static final int CNMT_TYPE_SYSUPDATE = 0x3;
	public static final int CNMT_TYPE_BOOTIMG_PKG = 0x4;
	public static final int CNMT_TYPE_BOOTIMG_PKGSAFE = 0x5;
	public static final int CNMT_TYPE_APP = 0x80;
	public static final int CNMT_TYPE_PATCH = 0x81;
	public static final int CNMT_TYPE_ADDON = 0x82;
	public static final int CNMT_TYPE_DELTA = 0x83;
	
	public static final int CONTENT_TYPE_META = 0;
	public static final int CONTENT_TYPE_PROGRAM = 1;
	public static final int CONTENT_TYPE_DATA = 2;
	public static final int CONTENT_TYPE_CONTROL = 3;
	public static final int CONTENT_TYPE_HTML = 4;
	public static final int CONTENT_TYPE_LEGAL = 5;
	public static final int CONTENT_TYPE_DELTA = 6;
	
	/*----- Instance Variables -----*/
	
	private long id;
	private int version;
	private int meta_type;
	private int exheader_size;
	
	private int content_count;
	private int cnmt_count;
	private int cnmt_attr;
	
	private int req_dl_sys_version;
	
	//Various extended header fields...
	//These fields are used/not used for various meta types
	private long app_id;
	private long exdat_size;
	private int req_sys_ver;
	private int req_app_ver;
	
	//Content Info
	private ContentInfo[] contents;
	private ContentMetaInfo[] content_meta;
	
	//Extended Data
	//Only used for System Update, Patch, and Delta
	private ExdataSysUpdate exdata_sysup;
	private ExdataPatch exdata_patch;
	private ExdataDelta exdata_delta;
	
	/*----- Inner Structures -----*/
	
	public static class ContentInfo{
		
		public byte[] hash;
		public byte[] contentID;
		public long size;
		public int content_type;
		public int id_offset;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "Hash: " + AES.bytes2str(hash) + "\n");
			out.write(tabs + "Content ID: " + AES.bytes2str(contentID) + "\n");
			out.write(tabs + "Size: 0x" + Long.toHexString(size) + "\n");
			out.write(tabs + "Content Type: ");
			switch(content_type){
			case CONTENT_TYPE_META: out.write("Meta\n"); break;
			case CONTENT_TYPE_PROGRAM: out.write("Program\n"); break;
			case CONTENT_TYPE_DATA: out.write("Data\n"); break;
			case CONTENT_TYPE_CONTROL: out.write("Control\n"); break;
			case CONTENT_TYPE_HTML: out.write("HTML\n"); break;
			case CONTENT_TYPE_LEGAL: out.write("Legal\n"); break;
			case CONTENT_TYPE_DELTA: out.write("Delta\n"); break;
			}
			
			out.write(tabs + "ID Offset: 0x" + String.format("%02x", id_offset) + "\n");
		}
		
	}
	
	public static class ContentMetaInfo{
		public long id;
		public int version;
		public int type;
		public int attr;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "ID: " + Long.toHexString(id) + "\n");
			out.write(tabs + "Version: " + version + "\n");
			out.write(tabs + "Meta Type: ");
			switch(type){
			case CNMT_TYPE_UNK: out.write("<Invalid>\n"); break;
			case CNMT_TYPE_SYSPROGRAM: out.write("System Program\n"); break;
			case CNMT_TYPE_SYSUPDATE: out.write("System Update\n"); break;
			case CNMT_TYPE_BOOTIMG_PKG: out.write("Boot Image Package\n"); break;
			case CNMT_TYPE_BOOTIMG_PKGSAFE: out.write("Boot Image Package Safe\n"); break;
			case CNMT_TYPE_APP: out.write("Application\n"); break;
			case CNMT_TYPE_PATCH: out.write("Patch\n"); break;
			case CNMT_TYPE_ADDON: out.write("Add-on\n"); break;
			case CNMT_TYPE_DELTA: out.write("Delta\n"); break;
			}

			out.write(tabs + "Attributes: " + Integer.toHexString(attr) + "\n");
		}
		
	}
	
	public static class ExdataSysUpdate{
		
		public int version;
		public int var_count;
		public int[] firm_var_id;
		public boolean[] firm_var_base;
		public int[] firm_var_metacount;
		public ContentMetaInfo[] firm_var_metainfo;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "Version: " + version + "\n");
			out.write(tabs + "Variation Count: " + var_count + "\n");
			
			for(int i = 0; i < var_count; i++){
				out.write(tabs + "-> Variation " + i + "\n");
				out.write(tabs + "\tFirmware Variation ID: " + Integer.toHexString(firm_var_id[i]) + "\n");
				out.write(tabs + "\tRefer to Base: " + firm_var_base[i] + "\n");
				out.write(tabs + "\tMeta Count: " + Integer.toHexString(firm_var_metacount[i]) + "\n");
			}
			
			System.err.println(tabs + "--- Content Meta Info ---");
			for(int i = 0; i < firm_var_metainfo.length; i++){
				firm_var_metainfo[i].printMeTo(out, indents + 1);
			}
		}
		
	}
	
	public static class ExdataPatch{
		
		public int his_ct;
		public int delta_his_ct;
		public int delta_ct;
		public int frag_set_ct;
		public int his_content_ct;
		public int delta_content_ct;
		
		public HistoryHeader[] histories;
		public DeltaHistory[] delta_hists;
		public DeltaHeader[] delta_hdrs;
		public FragmentSet[] frag_sets;
		public ContentInfo[] hist_content_info;
		public ContentInfo[] delta_content_info;
		public FragmentIndicator[] frag_ind;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "History Count: " + his_ct + "\n");
			for(int i = 0; i < his_ct; i++){
				out.write(tabs + "-> History " + i + "\n");
				histories[i].printMeTo(out, indents+1);
			}
			
			out.write(tabs + "Delta History Count: " + delta_his_ct + "\n");
			for(int i = 0; i < delta_his_ct; i++){
				out.write(tabs + "-> Delta History " + i + "\n");
				delta_hists[i].printMeTo(out, indents+1);
			}
			
			out.write(tabs + "Delta Count: " + delta_ct + "\n");
			for(int i = 0; i < delta_ct; i++){
				out.write(tabs + "-> Delta " + i + "\n");
				delta_hdrs[i].printMeTo(out, indents+1);
			}
			
			out.write(tabs + "Fragment Set Count: " + frag_set_ct + "\n");
			for(int i = 0; i < frag_set_ct; i++){
				out.write(tabs + "-> Fragment Set " + i + "\n");
				frag_sets[i].printMeTo(out, indents+1);
			}
			
			out.write(tabs + "History Content Count: " + his_content_ct + "\n");
			for(int i = 0; i < his_content_ct; i++){
				out.write(tabs + "-> History Content " + i + "\n");
				hist_content_info[i].printMeTo(out, indents+1);
			}
			
			out.write(tabs + "Delta Content Count: " + delta_content_ct + "\n");
			for(int i = 0; i < delta_content_ct; i++){
				out.write(tabs + "-> Delta Content " + i + "\n");
				delta_content_info[i].printMeTo(out, indents+1);
			}
			
			out.write(tabs + "Fragment Indicator Count: " + frag_ind.length + "\n");
			for(int i = 0; i < frag_ind.length; i++){
				out.write(tabs + "-> Fragment Indicator " + i + "\n");
				frag_ind[i].printMeTo(out, indents+1);
			}
			
		}
		
	}
	
	public static class HistoryHeader{
		public byte[] content_meta_key;
		public byte[] hash;
		public int content_info_ct;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "Content Meta Key: " + AES.bytes2str(content_meta_key) + "\n");
			out.write(tabs + "Hash: " + AES.bytes2str(hash) + "\n");
			out.write(tabs + "Content Info Count: " + content_info_ct + "\n");

		}
	}
	
	public static class DeltaHistory{
		public long src_patch_id;
		public long dest_patch_id;
		public int src_ver;
		public int dest_ver;
		public long dl_size;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "Source Patch ID: " + Long.toHexString(src_patch_id) + "\n");
			out.write(tabs + "Destination Patch ID: " + Long.toHexString(dest_patch_id) + "\n");
			out.write(tabs + "Source Version: " + src_ver + "\n");
			out.write(tabs + "Destination Version: " + dest_ver + "\n");
			out.write(tabs + "Download Size: 0x" + Long.toHexString(dl_size) + "\n");

		}
	}
	
	public static class DeltaHeader{
		public long src_patch_id;
		public long dest_patch_id;
		public int src_ver;
		public int dest_ver;
		public int frag_set_ct;
		public int content_info_ct;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "Source Patch ID: " + Long.toHexString(src_patch_id) + "\n");
			out.write(tabs + "Destination Patch ID: " + Long.toHexString(dest_patch_id) + "\n");
			out.write(tabs + "Source Version: " + src_ver + "\n");
			out.write(tabs + "Destination Version: " + dest_ver + "\n");
			out.write(tabs + "Fragment Set Count: " + frag_set_ct + "\n");
			out.write(tabs + "Content Info Count: " + content_info_ct + "\n");
		}
		
	}
	
	public static class FragmentSet{
		public byte[] src_content_id;
		public byte[] dest_content_id;
		public long src_size;
		public long dest_size;
		public int frag_ind_count;
		public int frag_targ_content_type;
		public int update_type;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "Source Content ID: " + AES.bytes2str(src_content_id) + "\n");
			out.write(tabs + "Destination Content ID: " + AES.bytes2str(dest_content_id) + "\n");
			out.write(tabs + "Source Size: 0x" + Long.toHexString(src_size) + "\n");
			out.write(tabs + "Destination Size: 0x" + Long.toHexString(dest_size) + "\n");
			out.write(tabs + "Fragment Indicator Count: " + frag_ind_count + "\n");
			out.write(tabs + "Fragment Target Content Type: ");
			switch(frag_targ_content_type){
			case CONTENT_TYPE_META: out.write("Meta\n"); break;
			case CONTENT_TYPE_PROGRAM: out.write("Program\n"); break;
			case CONTENT_TYPE_DATA: out.write("Data\n"); break;
			case CONTENT_TYPE_CONTROL: out.write("Control\n"); break;
			case CONTENT_TYPE_HTML: out.write("HTML\n"); break;
			case CONTENT_TYPE_LEGAL: out.write("Legal\n"); break;
			case CONTENT_TYPE_DELTA: out.write("Delta\n"); break;
			}
			
			out.write(tabs + "Update Type: ");
			switch(update_type){
			case 0: out.write("Apply As Delta\n"); break;
			case 1: out.write("Overwrite\n"); break;
			case 2: out.write("Create\n"); break;
			}
		}
	}
	
	public static class FragmentIndicator{
		public int content_info_idx;
		public int frag_idx;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "Content Info Index: " + content_info_idx + "\n");
			out.write(tabs + "Fragment Index: " + frag_idx + "\n");
		}
	}
	
	public static class ExdataDelta{
		public long src_patch_id;
		public long dest_patch_id;
		public int src_ver;
		public int dest_ver;
		public int frag_set_ct;
		
		public FragmentSet[] frag_sets;
		public FragmentIndicator[] frag_ind;
		
		public void printMeTo(Writer out, int indents) throws IOException{
			StringBuilder sb = new StringBuilder(indents+1);
			for(int i = 0; i < indents; i++) sb.append("\t");
			String tabs = sb.toString();
			
			out.write(tabs + "Source Patch ID: " + Long.toHexString(src_patch_id) + "\n");
			out.write(tabs + "Destination Patch ID: " + Long.toHexString(dest_patch_id) + "\n");
			out.write(tabs + "Source Version: " + src_ver + "\n");
			out.write(tabs + "Destination Version: " + dest_ver + "\n");
			
			out.write(tabs + "Fragment Set Count: " + frag_set_ct + "\n");
			for(int i = 0; i < frag_set_ct; i++){
				out.write(tabs + "-> Fragment Set " + i + "\n");
				frag_sets[i].printMeTo(out, indents+1);
			}
			
			out.write(tabs + "Fragment Indicator Count: " + frag_ind.length + "\n");
			for(int i = 0; i < frag_ind.length; i++){
				out.write(tabs + "-> Fragment Indicator " + i + "\n");
				frag_ind[i].printMeTo(out, indents+1);
			}
			
		}
		
	}
	
	/*----- Construction/Parsing -----*/
	
	private NXContentMeta(){}
	
	public static NXContentMeta readCMNT(FileBuffer data, long stpos){
	
		data.setEndian(false);
		data.setCurrentPosition(stpos);
		NXContentMeta cnmt = new NXContentMeta();
		
		//Header
		cnmt.id = data.nextLong();
		cnmt.version = data.nextInt();
		cnmt.meta_type = Byte.toUnsignedInt(data.nextByte());
		data.skipBytes(1);
		cnmt.exheader_size = Short.toUnsignedInt(data.nextShort());
		cnmt.content_count = Short.toUnsignedInt(data.nextShort());
		cnmt.cnmt_count = Short.toUnsignedInt(data.nextShort());
		cnmt.cnmt_attr = Byte.toUnsignedInt(data.nextByte());
		data.skipBytes(3);
		cnmt.req_dl_sys_version = data.nextInt();
		data.skipBytes(4);
		
		//Extended header (if present)
		if(cnmt.exheader_size > 0){
			switch(cnmt.meta_type){
			case CNMT_TYPE_SYSUPDATE: 
				cnmt.exdat_size = Integer.toUnsignedLong(data.nextInt());
				break;
			case CNMT_TYPE_APP: 
				cnmt.app_id = data.nextLong();
				cnmt.req_sys_ver = data.nextInt();
				cnmt.req_app_ver = data.nextInt();
				break;
			case CNMT_TYPE_PATCH: 
				cnmt.app_id = data.nextLong();
				cnmt.req_sys_ver = data.nextInt();
				cnmt.exdat_size = Integer.toUnsignedLong(data.nextInt());
				data.skipBytes(8);
				break;
			case CNMT_TYPE_ADDON: 
				cnmt.app_id = data.nextLong();
				cnmt.req_app_ver = data.nextInt();
				data.skipBytes(4);
				break;
			case CNMT_TYPE_DELTA: 
				cnmt.app_id = data.nextLong();
				cnmt.exdat_size = Integer.toUnsignedLong(data.nextInt());
				data.skipBytes(4);
				break;
			default: break;
			}	
		}
		
		//Content Info
		int ccount = cnmt.content_count;
		if(ccount > 0){
			cnmt.contents = new ContentInfo[ccount];
			for(int i = 0; i < ccount; i++){
				ContentInfo ci = new ContentInfo();
				cnmt.contents[i] = ci;
				
				ci.hash = new byte[32];
				for(int j = 0; j < 32; j++) ci.hash[j] = data.nextByte();
				ci.contentID = new byte[16];
				for(int j = 0; j < 16; j++) ci.contentID[j] = data.nextByte();
				
				ci.size = Integer.toUnsignedLong(data.nextInt());
				ci.size |= Short.toUnsignedLong(data.nextShort()) << 32;
				ci.content_type = Byte.toUnsignedInt(data.nextByte());
				ci.id_offset = Byte.toUnsignedInt(data.nextByte());
			}	
		}
		
		//Content meta info
		ccount = cnmt.cnmt_count;
		if(ccount > 0){
			cnmt.content_meta = new ContentMetaInfo[ccount];
			for(int i = 0; i < ccount; i++){
				ContentMetaInfo ci = new ContentMetaInfo();
				cnmt.content_meta[i] = ci;
				
				ci.id = data.nextLong();
				ci.version = data.nextInt();
				ci.type = Byte.toUnsignedInt(data.nextByte());
				ci.attr = Byte.toUnsignedInt(data.nextByte());
				data.skipBytes(2);
			}
		}
		
		//Extended Data
		if(cnmt.exdat_size > 0){
			switch(cnmt.meta_type){
			case CNMT_TYPE_SYSUPDATE: 
				ExdataSysUpdate exdat0 = new ExdataSysUpdate();
				cnmt.exdata_sysup = exdat0;
				exdat0.version = data.nextInt();
				exdat0.var_count = data.nextInt();
				exdat0.firm_var_id = new int[exdat0.var_count];
				
				if(exdat0.version < 2){
					for(int i = 0; i < exdat0.var_count; i++){
						exdat0.firm_var_id[i] = data.nextInt();
						data.skipBytes(0x1C);
					}
				}
				else{
					for(int i = 0; i < exdat0.var_count; i++){
						exdat0.firm_var_id[i] = data.nextInt();
					}
					exdat0.firm_var_metacount = new int[exdat0.var_count];
					exdat0.firm_var_base = new boolean[exdat0.var_count];
					int mcount = 0;
					for(int i = 0; i < exdat0.var_count; i++){
						exdat0.firm_var_base[i] = (data.nextByte() != 0);
						data.skipBytes(3);
						exdat0.firm_var_metacount[i] = data.nextInt();
						data.skipBytes(0x18);
						if(!exdat0.firm_var_base[i]) mcount += exdat0.firm_var_metacount[i];
					}
					exdat0.firm_var_metainfo = new ContentMetaInfo[mcount];
					for(int i = 0; i < mcount; i++){
						ContentMetaInfo cmi = new ContentMetaInfo();
						exdat0.firm_var_metainfo[i] = cmi;
						
						cmi.id = data.nextLong();
						cmi.version = data.nextInt();
						cmi.type = Byte.toUnsignedInt(data.nextByte());
						cmi.attr = Byte.toUnsignedInt(data.nextByte());
						data.skipBytes(2);
					}
				}
				
				break;
			case CNMT_TYPE_PATCH: 
				ExdataPatch exdat1 = new ExdataPatch();
				cnmt.exdata_patch = exdat1;
				
				exdat1.his_ct = data.nextInt();
				exdat1.delta_his_ct = data.nextInt();
				exdat1.delta_ct = data.nextInt();
				exdat1.frag_set_ct = data.nextInt();
				exdat1.his_content_ct = data.nextInt();
				exdat1.delta_content_ct = data.nextInt();
				data.skipBytes(4);
				
				//System.err.println("History Count: " + exdat1.his_ct);
				exdat1.histories = new HistoryHeader[exdat1.his_ct];
				for(int i = 0; i < exdat1.his_ct; i++){
					HistoryHeader hh = new HistoryHeader();
					exdat1.histories[i] = hh;
					
					hh.content_meta_key = new byte[16];
					for(int j = 0; j < 16; j++) hh.content_meta_key[j] = data.nextByte();
					hh.hash = new byte[32];
					for(int j = 0; j < 32; j++) hh.hash[j] = data.nextByte();
					hh.content_info_ct = Short.toUnsignedInt(data.nextShort());
					data.skipBytes(6);
				}
				
				//System.err.println("Delta History Count: " + exdat1.delta_his_ct);
				exdat1.delta_hists = new DeltaHistory[exdat1.delta_his_ct];
				for(int i = 0; i < exdat1.delta_his_ct; i++){
					DeltaHistory dh = new DeltaHistory();
					exdat1.delta_hists[i] = dh;
					
					dh.src_patch_id = data.nextLong();
					dh.dest_patch_id = data.nextLong();
					dh.src_ver = data.nextInt();
					dh.dest_ver = data.nextInt();
					dh.dl_size = data.nextLong();
					data.skipBytes(8);
				}
				
				//System.err.println("Delta Count: " + exdat1.delta_ct);
				exdat1.delta_hdrs = new DeltaHeader[exdat1.delta_ct];
				for(int i = 0; i < exdat1.delta_ct; i++){
					DeltaHeader dh = new DeltaHeader();
					exdat1.delta_hdrs[i] = dh;
					
					dh.src_patch_id = data.nextLong();
					dh.dest_patch_id = data.nextLong();
					dh.src_ver = data.nextInt();
					dh.dest_ver = data.nextInt();
					dh.frag_set_ct = Short.toUnsignedInt(data.nextShort());
					data.skipBytes(6);
					dh.content_info_ct = Short.toUnsignedInt(data.nextShort());
					data.skipBytes(6);
				}
				
				//System.err.println("Fragment Set Count: " + exdat1.frag_set_ct);
				int ficount = 0;
				exdat1.frag_sets = new FragmentSet[exdat1.frag_set_ct];
				for(int i = 0; i < exdat1.frag_set_ct; i++){
					FragmentSet fs = new FragmentSet();
					exdat1.frag_sets[i] = fs;
					
					fs.src_content_id = new byte[16];
					for(int j = 0; j < 16; j++) fs.src_content_id[j] = data.nextByte();
					fs.dest_content_id = new byte[16];
					for(int j = 0; j < 16; j++) fs.dest_content_id[j] = data.nextByte();
					fs.src_size = Integer.toUnsignedLong(data.nextInt());
					fs.src_size |= Short.toUnsignedLong(data.nextShort()) << 32;
					fs.dest_size = Integer.toUnsignedLong(data.nextInt());
					fs.dest_size |= Short.toUnsignedLong(data.nextShort()) << 32;
					fs.frag_ind_count = Short.toUnsignedInt(data.nextShort());
					ficount += fs.frag_ind_count;
					fs.frag_targ_content_type = Byte.toUnsignedInt(data.nextByte());
					fs.update_type = Byte.toUnsignedInt(data.nextByte());
					data.skipBytes(4);
				}
				
				//System.err.println("History Content Count: " + exdat1.his_content_ct);
				exdat1.hist_content_info = new ContentInfo[exdat1.his_content_ct];
				for(int i = 0; i < exdat1.his_content_ct; i++){
					ContentInfo ci = new ContentInfo();
					exdat1.hist_content_info[i] = ci;
					
					ci.contentID = new byte[16];
					for(int j = 0; j < 16; j++) ci.contentID[j] = data.nextByte();
					ci.size = Integer.toUnsignedLong(data.nextInt());
					ci.size |= Short.toUnsignedLong(data.nextShort()) << 32;
					ci.content_type = Byte.toUnsignedInt(data.nextByte());
					ci.id_offset = Byte.toUnsignedInt(data.nextByte());
				}
				
				//System.err.println("Delta Content Count: " + exdat1.delta_content_ct);
				exdat1.delta_content_info = new ContentInfo[exdat1.delta_content_ct];
				for(int i = 0; i < exdat1.delta_content_ct; i++){
					ContentInfo ci = new ContentInfo();
					exdat1.delta_content_info[i] = ci;
					
					ci.hash = new byte[32];
					for(int j = 0; j < 32; j++) ci.hash[j] = data.nextByte();
					ci.contentID = new byte[16];
					for(int j = 0; j < 16; j++) ci.contentID[j] = data.nextByte();
					ci.size = Integer.toUnsignedLong(data.nextInt());
					ci.size |= Short.toUnsignedLong(data.nextShort()) << 32;
					ci.content_type = Byte.toUnsignedInt(data.nextByte());
					ci.id_offset = Byte.toUnsignedInt(data.nextByte());
				}
				
				//System.err.println("Fragment Indicator Count: " + ficount);
				exdat1.frag_ind = new FragmentIndicator[ficount];
				for(int i = 0; i < ficount; i++){
					FragmentIndicator fi = new FragmentIndicator();
					exdat1.frag_ind[i] = fi;
					
					fi.content_info_idx = Short.toUnsignedInt(data.nextShort());
					fi.frag_idx = Short.toUnsignedInt(data.nextShort());
				}
				
				break;
			case CNMT_TYPE_DELTA: 
				ExdataDelta exdat2 = new ExdataDelta();
				cnmt.exdata_delta = exdat2;
				
				exdat2.src_patch_id = data.nextLong();
				exdat2.dest_patch_id = data.nextLong();
				exdat2.src_ver = data.nextInt();
				exdat2.dest_ver = data.nextInt();
				exdat2.frag_set_ct = Short.toUnsignedInt(data.nextShort());
				data.skipBytes(6);
				
				int ficount1 = 0;
				exdat2.frag_sets = new FragmentSet[exdat2.frag_set_ct];
				for(int i = 0; i < exdat2.frag_set_ct; i++){
					FragmentSet fs = new FragmentSet();
					exdat2.frag_sets[i] = fs;
					
					fs.src_content_id = new byte[16];
					for(int j = 0; j < 16; j++) fs.src_content_id[j] = data.nextByte();
					fs.dest_content_id = new byte[16];
					for(int j = 0; j < 16; j++) fs.dest_content_id[j] = data.nextByte();
					fs.src_size = Integer.toUnsignedLong(data.nextInt());
					fs.src_size |= Short.toUnsignedLong(data.nextShort()) << 32;
					fs.dest_size = Integer.toUnsignedLong(data.nextInt());
					fs.dest_size |= Short.toUnsignedLong(data.nextShort()) << 32;
					fs.frag_ind_count = Short.toUnsignedInt(data.nextShort());
					ficount1 += fs.frag_ind_count;
					fs.frag_targ_content_type = Byte.toUnsignedInt(data.nextByte());
					fs.update_type = Byte.toUnsignedInt(data.nextByte());
					data.skipBytes(4);
				}
				
				exdat2.frag_ind = new FragmentIndicator[ficount1];
				for(int i = 0; i < ficount1; i++){
					FragmentIndicator fi = new FragmentIndicator();
					exdat2.frag_ind[i] = fi;
					
					fi.content_info_idx = Short.toUnsignedInt(data.nextShort());
					fi.frag_idx = Short.toUnsignedInt(data.nextShort());
				}
				
				break;
			default: break;
			}	
		}
		
		return cnmt;
	}
	
	/*----- Getters -----*/
	
	public long getID(){return this.id;}
	public int getVersion(){return this.version;}
	public int getMetaType(){return this.meta_type;}
	public int getExtendedHeaderSize(){return this.exheader_size;}
	
	public int getContentCount(){return this.content_count;}
	public int getContentMetaCount(){return this.cnmt_count;}
	public int getContentMetaAttributes(){return this.cnmt_attr;}
	public int getRequiredDownloadSystemVersion(){return this.req_dl_sys_version;}
	
	public long getAppID(){return this.app_id;}
	public long getExtendedDataSize(){return this.exdat_size;}
	public int getRequiredSystemVersion(){return this.req_sys_ver;}
	public int getRequiredApplicationVersion(){return this.req_app_ver;}
	
	public List<ContentInfo> getContents(){
		List<ContentInfo> list = new LinkedList<ContentInfo>();
		if(contents != null){
			for(int i = 0; i < contents.length; i++) list.add(contents[i]);
		}
		return list;
	}
	
	public List<FragmentSet> getFragmentSets(){
		List<FragmentSet> list = new LinkedList<FragmentSet>();
		if(exdata_patch != null){
			if(exdata_patch.frag_sets != null){
				for(int i = 0; i < exdata_patch.frag_sets.length; i++){
					list.add(exdata_patch.frag_sets[i]);
				}
			}
		}
		if(exdata_delta != null){
			if(exdata_delta.frag_sets != null){
				for(int i = 0; i < exdata_delta.frag_sets.length; i++){
					list.add(exdata_delta.frag_sets[i]);
				}
			}
		}
		return list;
	}
	
	/*----- Setters -----*/
	
	/*----- Util -----*/
	
	public long getPatchTargetOriginalSize(String patch_content_id){
		//TODO
		//This is honestly just a hint because the stupid IDs don't go all the way back.
		
		FragmentSet[] fragments = null;
		if(exdata_patch != null) fragments = exdata_patch.frag_sets;
		else if(exdata_delta != null) fragments = exdata_delta.frag_sets;
		
		if(fragments == null) return -1L;
		
		//Look for a fragment with the target destID
		FragmentSet fs = null;
		for(FragmentSet f : fragments){
			String idstr = AES.bytes2str(f.dest_content_id);
			if(idstr.equals(patch_content_id)){fs = f; break;}
		}
		if(fs == null) return -1L;
		
		//Look at the earliest version and try to match it to this entry.
		if(exdata_patch != null){
			HistoryHeader[] hists = exdata_patch.histories;
			ContentInfo[] hists_c = exdata_patch.hist_content_info;
			
			if(hists == null) return -1L;
			int maxidx = hists[0].content_info_ct;
			
			//Try to match by type
			ContentInfo ci = null;
			for(int j = 0; j < maxidx; j++){
				if(hists_c[j].content_type == fs.frag_targ_content_type){
					if(ci != null) return -1L; //More than one
					ci = hists_c[j];
				}
			}
			
			return ci.size;
		}
		
		return -1L;
	}
	
	/*----- Definition -----*/
	
	private static NXCNMTDef staticdef;
	
	public static NXCNMTDef getDefinition(){
		if(staticdef == null) staticdef = new NXCNMTDef();
		return staticdef;
	}
	
	public static class NXCNMTDef extends GenericSystemDef{
		
		private static String DEFO_ENG_DESC = "Nintendo Switch Content Metadata";
		public static int TYPE_ID = 0x636e6d74;
		
		public NXCNMTDef(){
			super(DEFO_ENG_DESC, TYPE_ID);
		}
		
		public Collection<String> getExtensions() {
			List<String> slist = new LinkedList<String>();
			slist.add("cnmt");
			return slist;
		}
		
		public String getDefaultExtension(){return "cnmt";}
		
	}
	
	/*----- Debug -----*/
	
	public void printMeTo(Writer out) throws IOException{

		out.write("ID: " + Long.toHexString(id) + "\n");
		out.write("Version: " + version + "\n");
		out.write("Meta Type: ");
		switch(meta_type){
		case CNMT_TYPE_UNK: out.write("<Invalid>\n"); break;
		case CNMT_TYPE_SYSPROGRAM: out.write("System Program\n"); break;
		case CNMT_TYPE_SYSUPDATE: out.write("System Update\n"); break;
		case CNMT_TYPE_BOOTIMG_PKG: out.write("Boot Image Package\n"); break;
		case CNMT_TYPE_BOOTIMG_PKGSAFE: out.write("Boot Image Package Safe\n"); break;
		case CNMT_TYPE_APP: out.write("Application\n"); break;
		case CNMT_TYPE_PATCH: out.write("Patch\n"); break;
		case CNMT_TYPE_ADDON: out.write("Add-on\n"); break;
		case CNMT_TYPE_DELTA: out.write("Delta\n"); break;
		}
		
		out.write("Extended Header Size: 0x" + Integer.toHexString(exheader_size) + "\n");
		out.write("Content Count: " + content_count + "\n");
		out.write("Content Meta Count: " + cnmt_count + "\n");
		out.write("Content Meta Attributes: 0x" + Integer.toHexString(cnmt_attr) + "\n");
		
		out.write("Required Download System Version: 0x" + Integer.toHexString(req_dl_sys_version) + "\n");
		
		switch(meta_type){
		case CNMT_TYPE_SYSUPDATE: 
			out.write("Extended Data Size: 0x" + Long.toHexString(exdat_size) + "\n");
			break;
		case CNMT_TYPE_APP: 
			out.write("Patch ID: " + Long.toHexString(app_id) + "\n");
			out.write("Required System Version: 0x" + Integer.toHexString(req_sys_ver) + "\n");
			out.write("Required Application Version: 0x" + Integer.toHexString(req_app_ver) + "\n");
			break;
		case CNMT_TYPE_PATCH: 
			out.write("Application ID: " + Long.toHexString(app_id) + "\n");
			out.write("Required System Version: 0x" + Integer.toHexString(req_sys_ver) + "\n");
			out.write("Extended Data Size: 0x" + Long.toHexString(exdat_size) + "\n");
			break;
		case CNMT_TYPE_ADDON: 
			out.write("Application ID: " + Long.toHexString(app_id) + "\n");
			out.write("Required Application Version: 0x" + Integer.toHexString(req_app_ver) + "\n");
			break;
		case CNMT_TYPE_DELTA: 
			out.write("Application ID: " + Long.toHexString(app_id) + "\n");
			out.write("Extended Data Size: 0x" + Long.toHexString(exdat_size) + "\n");
			break;
		default: break;
		}
		
		out.write("----- Contents -----\n");
		for(int i = 0; i < this.content_count; i++){
			out.write("->Content " + i + "\n");
			contents[i].printMeTo(out, 1);
		}
		
		out.write("----- Content Meta -----\n");
		for(int i = 0; i < this.cnmt_count; i++){
			out.write("->Content Meta " + i + "\n");
			content_meta[i].printMeTo(out, 1);
		}
		
		out.write("----- Extended Data -----\n");
		switch(meta_type){
		case CNMT_TYPE_SYSUPDATE: 
			if(exdata_sysup != null) exdata_sysup.printMeTo(out, 1);
			break;
		case CNMT_TYPE_PATCH: 
			if(exdata_patch != null) exdata_patch.printMeTo(out, 1);
			break;
		case CNMT_TYPE_DELTA: 
			if(exdata_delta != null) exdata_delta.printMeTo(out, 1);
			break;
		default: break;
		}
		
	}

}

package waffleoRai_Containers.nintendo.nus;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Encryption.DecryptorMethod;
import waffleoRai_Encryption.StaticDecryption;
import waffleoRai_Encryption.StaticDecryptor;
import waffleoRai_Files.EncryptionDefinition;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StreamWrapper;

public class NUSDescrambler {
	
	public static class NUS_Z64_ByteswapMethod implements DecryptorMethod{

		@Override
		public byte[] decrypt(byte[] input, long offval) {
			//I think I'll just assume it's word-aligned eh.
			if(input == null) return null;
			int words = input.length >>> 2;
			byte[] out = new byte[words << 2];
			int pos = 0;
			for(int w = 0; w < words; w++){
				out[pos + 0] = input[pos + 3];
				out[pos + 1] = input[pos + 2];
				out[pos + 2] = input[pos + 1];
				out[pos + 3] = input[pos + 0];
				pos += 4;
			}
			
			return out;
		}

		public void adjustOffsetBy(long value) {}
		public int getInputBlockSize() {return 4;}
		public int getOutputBlockSize() {return 4;}
		public int getPreferredBufferSizeBlocks() {return 0x100;}

		@Override
		public long getOutputBlockOffset(long inputBlockOffset) {
			return inputBlockOffset;
		}

		@Override
		public long getInputBlockOffset(long outputBlockOffset) {
			return outputBlockOffset;
		}

		@Override
		public long getOutputCoordinate(long inputCoord) {
			return inputCoord;
		}

		@Override
		public long getInputCoordinate(long outputCoord) {
			return outputCoord;
		}

		public int backbyteCount() {return 0;}
		public void putBackbytes(byte[] dat) {}

		@Override
		public DecryptorMethod createCopy() {
			return new NUS_Z64_ByteswapMethod();
		}
		
	}
	
	public static class NUS_N64_ByteswapMethod implements DecryptorMethod{

		@Override
		public byte[] decrypt(byte[] input, long offval) {
			//I think I'll just assume it's word-aligned eh.
			if(input == null) return null;
			int words = input.length >>> 2;
			byte[] out = new byte[words << 2];
			int pos = 0;
			for(int w = 0; w < words; w++){
				out[pos + 0] = input[pos + 2];
				out[pos + 1] = input[pos + 3];
				out[pos + 2] = input[pos + 0];
				out[pos + 3] = input[pos + 1];
				pos += 4;
			}
			
			return out;
		}

		public void adjustOffsetBy(long value) {}
		public int getInputBlockSize() {return 4;}
		public int getOutputBlockSize() {return 4;}
		public int getPreferredBufferSizeBlocks() {return 0x100;}

		@Override
		public long getOutputBlockOffset(long inputBlockOffset) {
			return inputBlockOffset;
		}

		@Override
		public long getInputBlockOffset(long outputBlockOffset) {
			return outputBlockOffset;
		}

		@Override
		public long getOutputCoordinate(long inputCoord) {
			return inputCoord;
		}

		@Override
		public long getInputCoordinate(long outputCoord) {
			return outputCoord;
		}

		public int backbyteCount() {return 0;}
		public void putBackbytes(byte[] dat) {}

		@Override
		public DecryptorMethod createCopy() {
			return new NUS_N64_ByteswapMethod();
		}
		
	}

	public static class NUS_Z64_Byteswapper implements StaticDecryptor{

		private List<String> tempfiles;
		
		@Override
		public FileNode decrypt(FileNode node) {
			if(node == null) return null;
			if(tempfiles == null) tempfiles = new LinkedList<String>();
			
			try{
				String tpath = FileBuffer.generateTemporaryPath("NUS_Z64_Byteswapper");
				FileBuffer dat = node.loadData();
				FileNode outnode = new FileNode(null, node.getFileName() + "_p64");
				outnode.setSourcePath(tpath); outnode.setOffset(0);
				long len = dat.getFileSize();
				outnode.setLength(len);
				
				int words = (int)(len >>> 2);
				if((len % 4) != 0) words++; //Shouldn't happen with a real ROM but eh
				
				tempfiles.add(tpath);
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tpath));
				byte[] buff = new byte[4];
				dat.setCurrentPosition(0);
				for(int w = 0; w < words; w++){
					for(int j = 0; j < 4; j++){
						if(dat.hasRemaining()) buff[j] = dat.nextByte();
						else buff[j] = (byte)0;
					}
					for(int j = 0; j < 4; j++){
						bos.write(Byte.toUnsignedInt(buff[3-j]));
					}
				}
				bos.close();
				return outnode;
			}
			catch(Exception e){
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public DecryptorMethod generateDecryptor(FileNode node) {
			return new NUS_Z64_ByteswapMethod();
		}

		@Override
		public void dispose() {
			if(tempfiles != null){
				try{
					for(String s : tempfiles){
						Files.deleteIfExists(Paths.get(s));
					}	
				}
				catch(Exception ex){
					ex.printStackTrace();
				}
			}
			tempfiles = null;
		}
		
	}
	
	public static class NUS_N64_Byteswapper implements StaticDecryptor{

		private List<String> tempfiles;
		
		@Override
		public FileNode decrypt(FileNode node) {
			if(node == null) return null;
			if(tempfiles == null) tempfiles = new LinkedList<String>();
			
			try{
				String tpath = FileBuffer.generateTemporaryPath("NUS_N64_Byteswapper");
				FileBuffer dat = node.loadData();
				FileNode outnode = new FileNode(null, node.getFileName() + "_p64");
				outnode.setSourcePath(tpath); outnode.setOffset(0);
				long len = dat.getFileSize();
				outnode.setLength(len);
				
				int words = (int)(len >>> 2);
				if((len % 4) != 0) words++;
				
				tempfiles.add(tpath);
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tpath));
				byte[] buff = new byte[4];
				dat.setCurrentPosition(0);
				for(int w = 0; w < words; w++){
					for(int j = 0; j < 4; j++){
						if(dat.hasRemaining()) buff[j] = dat.nextByte();
						else buff[j] = (byte)0;
					}
					bos.write(Byte.toUnsignedInt(buff[2]));
					bos.write(Byte.toUnsignedInt(buff[3]));
					bos.write(Byte.toUnsignedInt(buff[0]));
					bos.write(Byte.toUnsignedInt(buff[1]));
				}
				bos.close();
				return outnode;
			}
			catch(Exception e){
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public DecryptorMethod generateDecryptor(FileNode node) {
			return new NUS_N64_ByteswapMethod();
		}

		@Override
		public void dispose() {
			if(tempfiles != null){
				try{
					for(String s : tempfiles){
						Files.deleteIfExists(Paths.get(s));
					}	
				}
				catch(Exception ex){
					ex.printStackTrace();
				}
			}
			tempfiles = null;
		}
		
	}
	
	public static class NUS_Z64_ByteswapDef implements EncryptionDefinition{

		public static final int DEF_ID = 0xe4123782;
		public static final String DEFO_ENG_DESC = "Nintendo 64 z64 Byte Swapper";
		
		private String desc = DEFO_ENG_DESC;
		
		public int getID() {return DEF_ID;}
		public String getDescription() {return desc;}
		public void setDescription(String s) {desc = s;}

		public void setStateValue(int key, int value) {}
		public int getStateValue(int key) {return 0;}

		@Override
		public boolean decrypt(StreamWrapper input, OutputStream output, List<byte[]> keydata) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean encrypt(StreamWrapper input, OutputStream stream, List<byte[]> keydata) {
			// TODO Auto-generated method stub
			return false;
		}

		public int[] getExpectedKeydataSizes() {return null;}
		public boolean unevenIOBlocks() {return false;}
		
	}
	
	public static class NUS_N64_ByteswapDef implements EncryptionDefinition{

		public static final int DEF_ID = 0xe4123783;
		public static final String DEFO_ENG_DESC = "Nintendo 64 n64 Byte Swapper";
		
		private String desc = DEFO_ENG_DESC;
		
		public int getID() {return DEF_ID;}
		public String getDescription() {return desc;}
		public void setDescription(String s) {desc = s;}

		public void setStateValue(int key, int value) {}
		public int getStateValue(int key) {return 0;}

		@Override
		public boolean decrypt(StreamWrapper input, OutputStream output, List<byte[]> keydata) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean encrypt(StreamWrapper input, OutputStream stream, List<byte[]> keydata) {
			// TODO Auto-generated method stub
			return false;
		}

		public int[] getExpectedKeydataSizes() {return null;}
		public boolean unevenIOBlocks() {return false;}
		
	}
	
	public static void registerByteswapMethods(){
		if(StaticDecryption.getDecryptorState(NUS_Z64_ByteswapDef.DEF_ID) == null){
			StaticDecryption.setDecryptorState(NUS_Z64_ByteswapDef.DEF_ID, new NUS_Z64_Byteswapper());
		}
		if(StaticDecryption.getDecryptorState(NUS_N64_ByteswapDef.DEF_ID) == null){
			StaticDecryption.setDecryptorState(NUS_N64_ByteswapDef.DEF_ID, new NUS_N64_Byteswapper());
		}
	}
	
}

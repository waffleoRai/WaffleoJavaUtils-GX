package waffleoRai_Containers.nintendo.nx;

import java.util.Collection;

import waffleoRai_Encryption.FileCryptRecord;
import waffleoRai_Encryption.FileCryptTable;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_Utils.FileBuffer;

public interface NXContainer {

	public DirectoryNode getFileTree();
	public Collection<FileCryptRecord> addEncryptionInfo(FileCryptTable table);
	public boolean buildFileTree(FileBuffer data, long offset, int complexity_level);
	public boolean unlock(NXCrypt cryptstate);
	
}

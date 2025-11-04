package waffleoRai_Files.maxis;

import java.io.IOException;

import waffleoRai_Containers.maxis.MaxisResKey;
import waffleoRai_Utils.FileBuffer;

public interface MaxisResImporterExporter {

	public String exportTo(FileBuffer data, String exportPathStem, MaxisResKey resKey) throws IOException; //Returns path to importable master file
	public FileBuffer importFrom(String importPath, MaxisResKey resKey) throws IOException;
	public int[] getCoveredTypeIds();
	
}

package waffleoRai_Files.maxis;

import java.io.IOException;

import waffleoRai_Containers.maxis.MaxisResKey;
import waffleoRai_Utils.FileBuffer;

public class MxAsIsIO implements MaxisResImporterExporter{

	//This imports/exports files as-is
	//Is default.
	
	public String exportTo(FileBuffer data, String exportPathStem, MaxisResKey resKey) throws IOException {
		if(data == null) return null;
		String ext = "bin";
		if(resKey != null) {
			ext = getExtension(resKey.getTypeId());
		}
		String outpath = exportPathStem + "." + ext;
		data.writeFile(outpath);
		
		return outpath;
	}

	public FileBuffer importFrom(String importPath, MaxisResKey resKey) throws IOException {
		if(importPath == null) return null;
		return FileBuffer.createBuffer(importPath, false);
	}
	
	private String getExtension(int typeId) {
		switch(typeId) {
		case MaxisTypeIds._IMG:
		case MaxisTypeIds._ADS:
			return "dds";
		case MaxisTypeIds.IMAG_TGA:
			return "tga";
		case MaxisTypeIds.SNAP_FamL:
		case MaxisTypeIds.SNAP_FamM:
		case MaxisTypeIds.SNAP_FamS:
		case MaxisTypeIds.SNAP_SimL:
		case MaxisTypeIds.SNAP_SimM:
		case MaxisTypeIds.SNAP_SimS:
		case MaxisTypeIds.ICON_LotS:
		case MaxisTypeIds.ICON_LotM:
		case MaxisTypeIds.ICON_LotL:
		case MaxisTypeIds.ICON_ObjS:
		case MaxisTypeIds.ICON_ObjM:
		case MaxisTypeIds.ICON_ObjL:
		case MaxisTypeIds.ICON_ObjXL:
		case MaxisTypeIds.IMAG_PNG:
		case MaxisTypeIds.THUM_ClrSwatch:
			return "png";
		}
		return "bin";
	}

	public int[] getCoveredTypeIds() {
		return new int[]{MaxisTypeIds._IMG, MaxisTypeIds.SNAP_FamL, MaxisTypeIds.SNAP_FamM, MaxisTypeIds.SNAP_FamS,
				MaxisTypeIds.SNAP_SimL, MaxisTypeIds.SNAP_SimM, MaxisTypeIds.SNAP_SimS, 
				MaxisTypeIds.ICON_LotS, MaxisTypeIds.ICON_LotM, MaxisTypeIds.ICON_LotL,
				MaxisTypeIds.ICON_ObjS, MaxisTypeIds.ICON_ObjM, MaxisTypeIds.ICON_ObjL, MaxisTypeIds.ICON_ObjXL,
				MaxisTypeIds.IMAG_TGA, MaxisTypeIds.IMAG_PNG, MaxisTypeIds.THUM_ClrSwatch, MaxisTypeIds._ADS};
	}

}

package testing;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;

import waffleoRai_Containers.nintendo.NDKDSFile;
import waffleoRai_Encryption.nintendo.nitro.PokeDPRandom;
import waffleoRai_Image.ImageDataReader;
import waffleoRai_Image.Palette;
import waffleoRai_Image.nintendo.nitro.NCLR;
import waffleoRai_Utils.FileBuffer;

public class Test_NitroGraphics {
	
	private static final int BIT_DEPTH = 4;
	private static final int TILE_WIDTH = 0;
	private static final int TILE_HEIGHT = 32;
	private static final int TILE_COL_SUGGEST = 4;
	
	private static final int TILE_WIDTH_SUGGEST = 160;
	
	private static int[] getDefaultPalette4() {
		int[] plt = new int[16];
		for(int i = 0; i < 16; i++) {
			int val = 0;
			for(int j = 0; j < 6; j++) {
				val <<= 4;
				val |= (i & 0xf);
			}
			val |= 0xff000000;
			plt[i] = val;
		}
		
		return plt;
	}
	
	private static int[] loadNCLR(String path) throws IOException {
		FileBuffer data = FileBuffer.createBuffer(path, false);
		NCLR nclr = NCLR.readNCLR(data);
		Palette p = nclr.getPalette(0);
		int[] pltout = new int[16];
		for(int i = 0; i < 16; i++) {
			int val = p.getRGBA(i);
			pltout[i] = val >>> 8;
			pltout[i] |= (val & 0xff) << 24;
		}
		
		return pltout;
	}
	
	private static void readAllNCGRAlt(String indir, String outdir, int pltNo) throws IOException {
		List<String> sprlist = new LinkedList<String>();
		DirectoryStream<Path> dirstr = Files.newDirectoryStream(Paths.get(indir));
		String nostr = String.format("%04x", pltNo);
		int[] plt = null;
		for(Path p : dirstr) {
			if(Files.isDirectory(p)) continue;
			String pstr = p.toAbsolutePath().toString();
			if(pltNo >= 0 && pstr.endsWith(nostr + ".nclr")) {
				plt = loadNCLR(pstr);
			}
			else if(pstr.endsWith(".ncgr")) {
				sprlist.add(pstr);
			}
		}
		dirstr.close();
		
		//If no palette loaded, load default.
		if(plt == null) plt = getDefaultPalette4();
		
		//Go through sprlist
		for(String s : sprlist) {
			System.out.println("> Trying " + s + "...");
			try {
				String fname = s.substring(s.lastIndexOf(File.separatorChar)+1, s.lastIndexOf('.'));
				String outpath = outdir + File.separator + fname + ".png";
				readNCGRAlt(s, outpath, plt);
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}
		}
		
	}

	private static FileBuffer scramblies(FileBuffer bitmapData) {
		return PokeDPRandom.decryptSpriteData(bitmapData);
	}
	
	private static void readNCGRAlt(String inpath, String outpath, int[] plt) throws IOException {
		//Print all the values in the CHAR header, then spam a bunch of tile sizes to PNG and see what works.
		NDKDSFile ndkFile = NDKDSFile.readDSFile(inpath);
		FileBuffer rahc = ndkFile.getSectionData("RAHC");
		
		rahc.setCurrentPosition(8L);
		int h = Short.toUnsignedInt(rahc.nextShort());
		int w = Short.toUnsignedInt(rahc.nextShort());
		int flags = Byte.toUnsignedInt(rahc.nextByte());
		rahc.skipBytes(3L);
		
		int w0 = rahc.nextInt();
		int w1 = rahc.nextInt();
		
		int size = rahc.nextInt();
		int w2 = rahc.nextInt();
		
		System.out.println("---- CHAR FIELDS ----");
		System.out.println("\tWidth(?): " + String.format("0x%04x (%d)", w, w));
		System.out.println("\tHeight(?): " + String.format("0x%04x (%d)", h, h));
		System.out.println("\tFlags(?): " + String.format("0x%02x", flags));
		System.out.println("\tWord 0x8: " + String.format("0x%08x (%d)", w0, w0));
		System.out.println("\tWord 0xc: " + String.format("0x%08x (%d)", w1, w1));
		System.out.println("\tBitmap Size: " + String.format("0x%08x (%d)", size, size));
		System.out.println("\tWord 0x14: " + String.format("0x%08x (%d)", w2, w2));
		
		BufferedImage img = null;
		ImageDataReader rdr = new ImageDataReader();
		rdr.setBitDepth(BIT_DEPTH);
		rdr.setReadBitsMSBFirst(false);
		if(TILE_WIDTH > 0) {
			rdr.setTileSize(TILE_WIDTH, TILE_HEIGHT);
			rdr.setTileColumnSuggestion(TILE_COL_SUGGEST);
		}
		else {
			rdr.setWidthSuggestion(TILE_WIDTH_SUGGEST);
		}
		//rdr.setPalette(plt);
		long cpos = rahc.getCurrentPosition();
		FileBuffer justdata = rahc.createReadOnlyCopy(cpos, cpos+size);
		FileBuffer rearr = scramblies(justdata);
		justdata.dispose();
		
		img = rdr.parseImageData(rearr.getReferenceAt(0L), size);
		//img = rdr.parseImageData(justdata.getReferenceAt(0L), size);
		rearr.dispose();
		
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
		ImageIO.write(img, "png", bos);
		bos.close();
	}
	
	public static void main(String[] args) {
		String ncgr_path = "C:\\Users\\Blythe\\Documents\\out\\dstest\\ADAE\\ADAE\\poketool\\pokegra\\d_pokegra.narc";
		String out_dir = "C:\\Users\\Blythe\\Documents\\out\\dstest\\ngcr_test";
		
		final int usePlt = 0xa5b;
		
		try {
			readAllNCGRAlt(ncgr_path, out_dir, usePlt);
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}

	}

}

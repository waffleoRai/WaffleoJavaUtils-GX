package waffleoRai_Sound.psx;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import waffleoRai_Sound.WAV;

public class Test_EncVag {

	public static void main(String[] args) {
		String indir = args[0];
		
		try{
			//Just enPath left channel.
			DirectoryStream<Path> dirstr = Files.newDirectoryStream(Paths.get(indir));
			for(Path p : dirstr){
				if(!Files.isRegularFile(p)) continue;
				
				String pstr = p.toAbsolutePath().toString();
				if(pstr.endsWith(".wav") && !pstr.contains("_TEST.wav")){
					WAV wave = new WAV(pstr);
					
					System.err.println("Processing " + pstr + "...");
					int[] samples = wave.getSamples_16Signed(0);
					int framecount = samples.length;
					short[] s16 = new short[framecount];
					for(int i = 0; i < framecount; i++){
						s16[i] = (short)samples[i];
					}
					
					byte[] dat = PSXVAGCompressor.encode(s16, PSXVAG.ENCMODE_NORMAL);
					
					String outpath = pstr.replace(".wav", ".vag");
					BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
					PSXVAG.writeVAGFromRawData(bos, wave.getSampleRate(), dat);
					bos.close();
					
					//See if it converts back to something safe to listen to.
					String outcheckpath = pstr.replace(".wav", "_TEST.wav");
					PSXVAG vag = new PSXVAG(outpath);
					WAV nwav = vag.convertToWAV();
					nwav.writeFile(outcheckpath);
				}
			}
			dirstr.close();
			
			
		}
		catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

}

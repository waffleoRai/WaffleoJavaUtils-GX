package testing;

import waffleoRai_Containers.nintendo.sar.DSSoundArchive;
import waffleoRai_SeqSound.ninseq.DSSeq;
import waffleoRai_SeqSound.ninseq.NinSeqSynthPlayer;
import waffleoRai_Sound.nintendo.DSStream;
import waffleoRai_Sound.nintendo.DSWarc;
import waffleoRai_Sound.nintendo.NinWave;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.soundformats.WAVWriter;
import waffleoRai_Utils.DirectoryNode;
import waffleoRai_soundbank.SimpleBank;
import waffleoRai_soundbank.nintendo.DSBank;
import waffleoRai_soundbank.sf2.SF2;

public class DSSDatTest {

	public static void main(String[] args) 
	{
		//String inpath = "C:\\Users\\Blythe\\Documents\\Game Stuff\\DS\\Games\\filedumps\\IPGE__POKEMON_SS\\IPGE\\data\\sound\\gs_sound_data.sdat";
		String inpath = "C:\\Users\\Blythe\\Documents\\Desktop\\Notes\\spirit_tracks.sdat";
		
		
		try
		{
			DSSoundArchive arc = DSSoundArchive.readSDAT(inpath);
			arc.setSourceLocation(inpath, 0);
			//arc.printTypeViewToStdOut();
			//DirectoryNode dn = arc.getArchiveView();
			//dn.printMeToStdErr(0);
			
			//Try reading a SWAR/SWAV
			//Indices are for the Spirit Tracks overworld theme
			int swar_idx = 0;
			DSWarc swar = arc.getSWAR(swar_idx);
			System.err.println("SWAV count: " + swar.countSounds());
			/*for(int i = 0; i < swar.countSounds(); i++)
			{
				String outpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\swar\\BKIE_SDAT_SWAR000_SWAV" + String.format("%03d", i) + ".wav";
				NinWave wav = swar.getWave(i);
				AudioSampleStream str = wav.createSampleStream();
				System.err.println("Writing wav " + i);
				System.err.println("\tSampleRate: " + str.getSampleRate() + " | Encoding: " + wav.getEncodingType());
				WAVWriter ww = new WAVWriter(str, outpath);
				ww.write(wav.totalFrames());
				ww.complete();
			}*/
			
			//Try a stream
			//int strm_idx = 0;
			//DSStream strm = arc.getSTRM(strm_idx);
			/*String outpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\BKIE_SDAT_STRM000.wav";
			//strm.writeTrackAsWAV(outpath, 0);
			AudioSampleStream str = strm.createSampleStream();
			WAVWriter ww = new WAVWriter(str, outpath);
			ww.write(strm.totalFrames());
			ww.complete();*/
			
			int sbnk_idx = 5;
			//Let's read a soundbank...
			DSBank sbnk = arc.getSBNK(sbnk_idx);
			//sbnk.printInfo();
			//System.exit(2);
			//sbnk.printInfo();
			//String outpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\ds_test\\BKIE_SDAT_SBNK005.sf2";
			//No idea if this still works :P
			//SimpleBank bnk = sbnk.toSoundbank(swar, 0, "BKIE_SDAT_SBNK005");
			//SF2.writeSF2(bnk, "DSSDatTest", false, outpath);
			
			int sseq_idx = 18;
			DSSeq seq = arc.getSSEQ(sseq_idx);
			
			//Alright... I guess I'll try playing it?
			NinSeqSynthPlayer player = new NinSeqSynthPlayer(seq.getSequenceData(), sbnk.generatePlayableBank(swar, 0), 0);
			player.setVariableValue(0, (short)17);
			player.startAsyncPlaybackToDefaultOutputDevice();
			
			
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}

	}

}

package testing;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Timer;

import waffleoRai_SeqSound.psx.SEQP;
import waffleoRai_SeqSound.psx.SeqpPlayer;
import waffleoRai_SeqSound.psx.SystemSeqpPlayer;
import waffleoRai_Sound.WAV;
import waffleoRai_Sound.psx.PSXVAG;
import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_SoundSynth.SynthProgram;
import waffleoRai_SoundSynth.SynthSampleStream;
import waffleoRai_SoundSynth.soundformats.WAVWriter;
import waffleoRai_SoundSynth.soundformats.game.VABSynthSampleStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_soundbank.vab.PSXVAB;
import waffleoRai_soundbank.vab.VABAttack;
import waffleoRai_soundbank.vab.VABDecay;

@SuppressWarnings("unused")
public class TestSynth {
	
	public static void main(String[] args) {
		
		String seqstem = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176\\BGM\\BGM_";
		String inseq = seqstem + "050.seqp"; //SEQ
		String inbank_head = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176\\SE\\SE_000.bnkp\\SE_000_vab.vh"; //VAB
		String inbank_body = "C:\\Users\\Blythe\\Documents\\Game Stuff\\PSX\\GameData\\SLPM87176\\SE\\SE_000.bnkp\\SE_000_vab.vb";
		String outpref = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\mewseqsynthtest_seq50";
		
		try
		{
			/*for(int i = 0; i < 63; i++)
			{
				String n = String.format("%03d", i);
				String outpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\mewseqsynthtest_seq_" + n + ".mid";
				String inpath = seqstem + n + ".seqp";
				if(!FileBuffer.fileExists(inpath)) continue;
				SEQP s = new SEQP(inpath);
				s.writeMIDI(outpath);
			}
			System.exit(2);*/
			
			SEQP myseq = new SEQP(inseq);
			PSXVAB mybank = new PSXVAB(FileBuffer.createBuffer(inbank_head, false), FileBuffer.createBuffer(inbank_body, false));
			
			/*System.err.println("VAB Read!");
			String infoout = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\mewseqsynthtest_vabinfo.txt";
			BufferedWriter bw = new BufferedWriter(new FileWriter(infoout));
			mybank.printInfoToBuffer(bw);
			bw.close();*/
			
			//Try out some ADSRs to make sure they turn out something usable
			/*String midiout = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\mewseqsynthtest_seq50.mid";
			myseq.writeMIDI(midiout);*/
			
			//Alright, looks like we have issues with the VAG decomp.
			//Let's fix that...
			/*int scount = mybank.countSamples();
			for(int i = 0; i < scount; i++)
			{
				String outpath = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\vagtest\\vab000_s" + String.format("%03d", i) + ".wav";
				PSXVAG vag = mybank.getSample(i); //heh heh
				//WAV wav = vag.convertToWAV();
				//wav.writeFile(outpath);
				
				AudioSampleStream str = vag.createSampleStream();
				WAVWriter ww = new WAVWriter(str, outpath);
				ww.write(vag.countSamples());
				ww.complete();
			}*/
			
			//Let's see what program 63 gives us...
			/*SynthProgram prog = mybank.getProgram(0, 11);
			VABSynthSampleStream str = (VABSynthSampleStream)prog.getSampleStream((byte)64, (byte)127);
			//I'm going to make it play for 2 seconds (then release and wait) and watch the volume levels...
			String voltbl = "C:\\Users\\Blythe\\Documents\\Desktop\\out\\voltable_11.csv";
			BufferedWriter bw = new BufferedWriter(new FileWriter(voltbl));
			bw.write("Sample,Instrinsic,Velocity,Envelope,Channel,Net,ADSRPhase\n");
			for(int i = 0; i < 88200; i++)
			{
				bw.write(i + ",");
				double[] d = str.getVolumeValues();
				for(int j = 0; j < 5; j++)
				{
					bw.write(d[j] + ",");
				}
				bw.write(str.getADSRPhase() + "\n");
				str.nextSample();
			}
			
			//Release
			str.releaseMe();
			int i = 88200;
			while(str.releaseSamplesRemaining())
			{
				bw.write(i++ + ",");
				double[] d = str.getVolumeValues();
				for(int j = 0; j < 5; j++)
				{
					bw.write(d[j] + ",");
				}
				bw.write(str.getADSRPhase() + "\n");
				str.nextSample();
			}
			
			bw.close();*/
			
			//mybank.setOneBasedIndexing(true);
			SeqpPlayer player = new SeqpPlayer(myseq, mybank);
			//player.startPlaybackToDefaultOutputDevice();
			player.startAsyncPlaybackToDefaultOutputDevice();
			//player.writeChannelsTo(outpref, 1);
			//player.writeChannelTo(outpref, 1, 10);
			
			//SystemSeqpPlayer sysplayer = new SystemSeqpPlayer(myseq);
			//sysplayer.start();
			
			/*for(int i = 0; i < 120; i++)
			{
				Thread.sleep(1000);
				Timer t = sysplayer.debugGetTimer();
			}*/
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		
	}

}

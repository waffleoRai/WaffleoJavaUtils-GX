package waffleoRai_Sound.psx;

public class PSXVAGCompressor {
	
	//This comes from reverse engineering ENCVAG.DLL from the PS1 SDK version 4.4
	//	(Also using ENCVAG.H of course)
	
	//Debugging with help from https://github.com/eurotools/es-ps2-vag-tool/PS2VagTool/Vag%20Functions/SonyVagEncoder.cs
	
	/*Yeah also turns out Ghidra isn't that amazing at decomping x86 FPU code
	 so I had basically do it from assembly
		2/10 don't recommend
	There is no constructive purpose for this comment, I just wanted to complain*/
	
	
	/* ----- Constants ----- */
	
	private double COEFF_TABLE_FLOAT[][] = {{0.0, 0.0},
										    {-60.0 / 64.0, 0.0},
										    {-115.0 / 64.0, 52.0 / 64.0},
										    {-98.0 / 64.0, 55.0 / 64.0},
										    {-122.0 / 64.0, 60.0 / 64.0}};
		
	/* ----- Instance Variables ----- */
	
	private short lastIn1 = 0;
	private short lastIn2 = 0;
	private double lastInD1 = 0.0;
	private double lastInD2 = 0.0;
	private double lastOut1 = 0.0;
	private double lastOut2 = 0.0;
	
	private double lastAmp1 = 0.0;
	private double unk_1ff68 = 0.0;
	private double[] lastErrors;
	
	private double[] modeFactors;
	private double[] unk_1c158;
	
	private int tblIdx = -1;
	private int shamt = -1;
	
	public void init(int encmode){
		lastIn1 = 0; lastIn2 = 0;
		lastOut1 = 0.0; lastOut2 = 0.0;
		lastInD1 = 0.0; lastInD2 = 0.0;
		lastAmp1 = 0.0;
		unk_1ff68 = 0.0;
		lastErrors = new double[4];
		modeFactors = new double[5];
		unk_1c158 = new double[5];
		tblIdx = -1;
		shamt = -1;
		
		final double FACTOR_A = 1.875;
		final double FACTOR_B = 4.4765625;
		
		for(int i = 0; i < 5; i++) unk_1c158[i] = 1.0;
		for(int i = 0; i < 5; i++) modeFactors[i] = FACTOR_A;
		
		switch(encmode){
		case PSXVAG.ENCMODE_HIGH:
			modeFactors[2] = FACTOR_B;
			modeFactors[3] = FACTOR_B;
			break;
		case PSXVAG.ENCMODE_LOW:
			modeFactors[2] = FACTOR_B;
			modeFactors[4] = FACTOR_B;
			break;
		case PSXVAG.ENCMODE_4BIT:
			for(int i = 1; i < 5; i++) modeFactors[i] = FACTOR_B;
			break;
		}
	}
	
	private short[] doEncode(short[] samples, int off){
		//func_1001460
		double[] tempvec = applyPhase(samples, 1, off);
		tempvec = testCoeffs(tempvec);
		return encodeDiffs(tempvec);
	}
	
	private double[] applyPhase(short[] samples, int phase, int off){
		//10014d0
		double coeffIn0 = 1.0;
		double coeffIn1 = 0.0;
		double coeffIn2 = 0.0;
		double coeffOut1 = 0.0;
		double coeffOut2 = 0.0;
		
		if(phase >= 2){
			final double ROOT_2_NEG = -1.141421356;
			double tanres = Math.tan(Math.PI / (double)phase);
			double temp = ((tanres - ROOT_2_NEG) * tanres) - -1.0;
			double tansq = tanres * tanres;
			double denom = tanres * ROOT_2_NEG;
			
			temp = -1.0 - temp;
			temp = (1.0 - tansq) - temp;
			coeffOut2 = temp/denom;
			
			coeffIn2 = tansq/denom;
			coeffOut1 = (-2.0 * (1.0 - tansq))/denom;
			coeffIn1 = (2.0 * (1.0 - tansq))/denom;
			coeffIn0 = coeffIn2;
		}
		
		double[] vec = new double[28];
		for(int i = 0; i < 28; i++){
			vec[i] = (double)lastIn1 * coeffIn1;
			vec[i] += (double)samples[i + off] * coeffIn0;
			vec[i] += (double)lastIn2 * coeffIn2;
			vec[i] -= lastOut1 * coeffOut1;
			vec[i] -= lastOut2 * coeffOut2;
			
			//Clamp
			if(vec[i] > (double)0x77ff) vec[i] = 0x77ff;
			if(vec[i] < (double)-0x7800) vec[i] = -0x7800;
			
			lastOut2 = lastOut1;
			lastOut1 = vec[i];
			lastIn2 = lastIn1;
			lastIn1 = samples[i + off];
		}
		
		return vec;
	}
	
	private double[] testCoeffs(double[] vec_in){
		//func_10016c0

		int tempi = 0;
		
		double lastAbs = 0.0;
		double min = Double.MAX_VALUE;
		double[] maxAmp = new double[5];
		
		double[][] testSamples = new double[5][28];
		
		double s1 = 0.0;
		double s2 = 0.0;
		for(int i = 0; i < 5; i++){
			lastAbs = lastAmp1;
			maxAmp[i] = 0.0;
			
			s1 = lastInD1;
			s2 = lastInD2;
			
			for(int j = 0; j < 28; j++){
				double smpl = (double)vec_in[j];
				double val = smpl;
				if(val > (double)0x77ff) val = 0x77ff;
				if(val < (double)-0x7800) val = -0x7800;
				
				val += s1 * COEFF_TABLE_FLOAT[i][0];
				val += s2 * COEFF_TABLE_FLOAT[i][1];
				
				testSamples[i][j] = val;
			
				if(val < 0.0) val = -val;
				if(val > maxAmp[i]) maxAmp[i] = val;
				s2 = s1;
				s1 = smpl;
				
				/*double val = unk_1ff68 * COEFF_TABLE_FLOAT[i][0];
				val += lastAbs * COEFF_TABLE_FLOAT[i][1];
				val += vec_in[j];
				testSamples[i][j] = val;
				
				if(val < 0.0) val = -val;
				if(val > maxAmp[i]) maxAmp[i] = val;
				lastAbs = val;*/
			}
			
			double cmp = modeFactors[i] * maxAmp[i];
			unk_1ff68 = cmp;
			if(min > cmp){
				tblIdx = i;
				min = cmp;
			}
			
			if(min <= (7.0 * modeFactors[i])){
				tblIdx = 0;
				unk_1ff68 = maxAmp[i];
				break;
			}
		}
		lastInD1 = s1;
		lastInD2 = s2;
		
		//lastAmp1 = lastAbs;
		
		double[] vec_out = new double[28];
		for(int j = 0; j < 28; j++){
			vec_out[j] = testSamples[tblIdx][j];
		}
		
		tempi = (int)Math.round(maxAmp[tblIdx] * unk_1c158[tblIdx]);
		
		if(tempi > 0x7fff) tempi = 0x7fff;
		if(tempi < -0x8000) tempi = -0x8000;
		
		shamt = 0;
		int mask = 0x4000;
		do{
			if((mask & ((mask >> 3) + tempi)) != 0){
				break;
			}
			mask >>= 1;
		}while(++shamt < 12);
		
		return vec_out;
	}
	
	private short[] encodeDiffs(double[] vec_in){
		//func_1001890
		
		short[] vec_out = new short[28];
		double scale = (double)(1 << shamt);
		
		double temp0 = 0.0;
		double sampd = 0.0;
		int samp = 0;
		
		for(int i = 0; i < 28; i++){
			temp0 = lastErrors[1] * COEFF_TABLE_FLOAT[tblIdx][1];
			temp0 += lastErrors[0] * COEFF_TABLE_FLOAT[tblIdx][0];
			//temp0 *= vec_in[i];
			temp0 += vec_in[i];
			sampd = temp0 * scale;
			
			samp = (int)sampd;
			samp += 0x800;
			samp &= ~0xfff;
			if(samp > 0x7fff) samp = 0x7fff;
			if(samp < -0x8000) samp = -0x8000;
			
			vec_out[i] = (short)samp;
			
			lastErrors[3] = lastErrors[2];
			lastErrors[2] = lastErrors[1];
			lastErrors[1] = lastErrors[0];
			lastErrors[0] = ((double)(samp >> shamt)) - temp0;
		}
		
		return vec_out;
	}
	
	public byte[] encodeBlock(short[] samples, int blockAttr){
		if(samples == null) return null;
		return encodeBlock(samples, 0, blockAttr);
	}
	
	public byte[] encodeBlock(short[] samples, int pos, int blockAttr){
		if(samples == null) return null;
		
		int rem = samples.length - pos;
		if(rem < 28){
			//Copy to new block and zero fill.
			short[] temp = new short[28];
			int j = 0;
			for(int i = pos; i < samples.length; i++){
				temp[j++] = samples[i];
			}
			pos = 0;
		}
		
		short[] vec = doEncode(samples, pos);
		if(vec == null) return null;
		
		byte[] block = new byte[16];
		
		//Do param word.
		int mybyte = shamt & 0xf;
		mybyte |= (tblIdx & 0xf) << 4;
		block[0] = (byte)mybyte;
		
		mybyte = 0;
		switch(blockAttr){
		case PSXVAG.BLOCK_ATTR_1_SHOT: mybyte = 0; break;
		case PSXVAG.BLOCK_ATTR_1_SHOT_END: mybyte = 0x01; break;
		case PSXVAG.BLOCK_ATTR_START: mybyte = 0x06; break;
		case PSXVAG.BLOCK_ATTR_BODY: mybyte = 0x02; break;
		case PSXVAG.BLOCK_ATTR_END: mybyte = 0x03; break;
		}
		block[1] = (byte)mybyte;
		
		int j = 0;
		for(int i = 0; i < 14; i++){
			int s0 = (int)vec[j];
			int s1 = (int)vec[j+1];
			
			mybyte = (s0 >> 12) & 0xf;
			mybyte |= (s1 >> 8) & 0xf0;
			
			block[i+2] = (byte)mybyte;
			j += 2;
		}
		
		return block;
	}
	
	public static byte[] encode(short[] samples, int encmode){
		return encode(samples, encmode, -1, -1);
	}
	
	public static byte[] encode(short[] samples, int encmode, int loopSt, int loopEd){
		if(samples == null) return null;
		
		int pos = 0;
		boolean oneshot = loopSt < 0 || loopEd < 0;
		
		//Calculate output size...
		int blockCount = (samples.length + 27) / 28;
		//int framesSnapped = blockCount * 28;
		int outSize = blockCount << 4;
		outSize += 16; //Zero block at beginning
		if(oneshot) outSize += 16; //Oneshots have a garbage block at the end
		byte[] outdata = new byte[outSize];
		
		//Initialize encoder.
		int attr = -1;
		int outpos = 16; //Leave room for zero block at beginning
		PSXVAGCompressor compr = new PSXVAGCompressor();
		compr.init(encmode);
		for(int b = 0; b < blockCount; b++){
			//Determine attr
			if(oneshot){
				if(b == blockCount - 1){
					attr = PSXVAG.BLOCK_ATTR_1_SHOT_END;
				}
				else attr = PSXVAG.BLOCK_ATTR_1_SHOT;
			}
			else{
				if(pos > loopSt){
					if(pos >= loopEd){
						if(pos > loopEd){
							//Did loop end in last block?
							int lastst = pos - 16;
							if(loopEd > lastst) attr = PSXVAG.BLOCK_ATTR_END;
							else attr = PSXVAG.BLOCK_ATTR_BODY;
						}
						else attr = PSXVAG.BLOCK_ATTR_END;
					}
					else attr = PSXVAG.BLOCK_ATTR_BODY;
				}
				else{
					if(pos < loopSt){
						//Is loop start in this block?
						int blockend = pos + 16;
						if(loopSt < blockend) attr = PSXVAG.BLOCK_ATTR_START;
						else attr = PSXVAG.BLOCK_ATTR_BODY;
					}
					else attr = PSXVAG.BLOCK_ATTR_START;
				}
			}
			
			//Encode
			byte[] blockdat = compr.encodeBlock(samples, pos, attr);
			for(int j = 0; j < 16; j++){
				outdata[outpos++] = blockdat[j];
			}
			pos += 28;
		}
		
		if(oneshot){
			//Add garbage block at end
			//This is done by EncVagFin, 
			//	but the garbage block only seems to appear in one-shots?
			for(int j = 2; j < 16; j++){
				outdata[outpos + j] = (byte)0x77;
			}
			outdata[outpos] = 0x00;
			outdata[outpos + 1] = 0x07;
		}
		
		return outdata;
	}

	public static byte[] encodeXA(short[][] samples, int encmode) {
		if(samples == null) return null;
		int chCount = samples.length;
		byte[][] data = new byte[chCount][];
		for(int c = 0; c < chCount; c++) {
			data[c] = encode(samples[c], encmode, -1, -1);
		}
		
		return PSXXAAudio.bitReorder_smpl2str(data);
	}
	
}

package waffleoRai_PSXMDEC;

import java.util.concurrent.ConcurrentLinkedQueue;

import waffleoRai_Image.psx.PSXImages;
import waffleoRai_Utils.Arunnable;

public class PSXMDEC {
	
	//TODO needs to be able to read multiple macroblocks w/ one command (if enough words follow)
	
	/* ----- Constants ----- */
	
	public static final double PSX_KR = 0.0;
	public static final double PSX_KG = 0.0;
	public static final double PSX_KB = 0.0;
	
	public static final int PADDING_CODE = 0xFE00;
	
	public static final int OUTPUT_DEPTH_4BIT = 0;
	public static final int OUTPUT_DEPTH_8BIT = 1;
	public static final int OUTPUT_DEPTH_15BIT = 3;
	public static final int OUTPUT_DEPTH_24BIT = 2;
	
	public static final int[] DEFO_SCALE_TABLE_RAW = 
		{0x5A82, 0x5A82, 0x5A82, 0x5A82, 0x5A82, 0x5A82, 0x5A82, 0x5A82,
		 0x7D8A, 0x6A6D, 0x471C, 0x18F8, 0xE707, 0xB8E3, 0x9592, 0x8275,
		 0x7641, 0x30FB, 0xCF04, 0x89BE, 0x89BE, 0xCF04, 0x30FB, 0x7641,
		 0x6A6D, 0xE707, 0x8275, 0xB8E3, 0x471C, 0x7D8A, 0x18F8, 0x9592,
		 0x5A82, 0xA57D, 0xA57D, 0x5A82, 0x5A82, 0xA57D, 0xA57D, 0x5A82,
		 0x471C, 0x8275, 0x18F8, 0x6A6D, 0x9592, 0xE707, 0x7D8A, 0xB8E3,
		 0x30FB, 0x89BE, 0x7641, 0xCF04, 0xCF04, 0x7641, 0x89BE, 0x30FB,
		 0x18F8, 0xB8E3, 0x6A6D, 0x8275, 0x7D8A, 0x9592, 0x471C, 0xE707};
	
	public static final long EXE_SLEEP_MILLIS = 1000;
	
	/* ----- Instance Variables ----- */
	
	private volatile boolean resetRequest;
	//private volatile boolean exeInterruptRequest;
	private boolean async_mode;
	private boolean output_bitscale; //If off, outputs 32-bit ARGB pixels. If on, outputs ready for PSX memory
	/*
	 * Output from methods is 0BGR, buuuuttt each byte may be signed, so might need to be rescaled.
	 */
	private boolean output_yuv; //Outputs YUV data instead of RGB.
	//As 4-byte BE words (MSB is first byte, LSB is fourth in cluster)
	//Outputs blocks in MDEC order - 16 Cr words, 16 Cb, 16 Y1, 16 Y2, 16 Y3, 16 Y4
	
	private QuantMatrix qt_y;
	private QuantMatrix qt_c;
	//private double[] scaleTable;
	private short[] scaleTable;
	
	private ConcurrentLinkedQueue<MDECCommand> exeQueue;
	private Executor exe;
	
	private int hw_count; //Expected halfwords for current command.
	
	//private MDECIO mdecIO;
	private IMDECIO mdecIO;
	
	/* ----- Construction ----- */
	
	public PSXMDEC(IMDECIO io)
	{
		exeQueue = new ConcurrentLinkedQueue<MDECCommand>();
		qt_y = new QuantMatrix();
		qt_c = new QuantMatrix();
		
		//mdecIO = new MDECIO(this, mem);
		mdecIO = io;
		resetRequest = false;
		output_bitscale = true;
		//exeInterruptRequest = false;
		
		io.linkMDEC(this);
	}
	
	/* ----- Mode ----- */
	
	public void setAsync(boolean b){
		async_mode = b;
	}
	
	public boolean asyncMode(){return async_mode;}
	
	public void setRGBOutput(boolean b){
		output_bitscale = !b;
	}
	
	public boolean outputRGB(){return !output_bitscale && !output_yuv;}
	
	public void setYUVOutput(boolean b){output_yuv = b;}
	
	public boolean outputYUV(){return output_yuv;}
	
	/* ----- Execution ----- */
	
	public class Executor extends Arunnable
	{
		private PSXMDEC mdec;
		
		public Executor(PSXMDEC me)
		{
			super.setName("PSXMDEC_Executor");
			//super.sleeps = true;
			super.sleeps = false; //Try making it a waiter
			super.delay = 0;
			super.sleeptime = EXE_SLEEP_MILLIS;
			mdec = me;
			//clear = false;
		}
		
		@Override
		public void doSomething() 
		{	
			while(!exeQueue.isEmpty())
			{
				//mdecIO.setBusy(true); //Let the IO unit do it?
				MDECCommand cmd = exeQueue.poll();
				try 
				{
					cmd.execute(mdec);
				} 
				catch (InterruptedException e) 
				{
					interruptThreads();
					if (resetRequest) {
						return;
					}
				}
				//mdecIO.setBusy(false);
			}
		}
		
	}
	
	public static class CommandEndException extends RuntimeException{
		private static final long serialVersionUID = 5209229950997905539L;
	}
	
	public void queueCommand(MDECCommand cmd)
	{
		if (cmd != null) exeQueue.add(cmd);
		if(exe != null)exe.interruptThreads();
	}
	
	public void start()
	{		
		if(!async_mode) return; //No need to start any new threads
		stop();
		
		mdecIO.startDisassembler();
		exe = new Executor(this);
		Thread t = new Thread(exe);
		t.setName("PSXMDEC_Execution_Thread");
		t.start();
	}
	
	public void stop()
	{
		mdecIO.stopDisassembler();
		if(exe != null) {
			exe.requestTermination();
			exe = null;
		}
	}
	
	public boolean executorRunning()
	{
		return exe.anyThreadsAlive();
	}
	
	public void interruptExecutor()
	{
		if (exe != null) exe.interruptThreads();
	}
	
	/* ----- Instructions ----- */
	
	private int popOrBlock(HalfwordStream src) throws InterruptedException
	{
		if(!async_mode){
			int hw = src.popHalfword();
			return hw;
		}
		
		boolean popped = false;
		int hw = 0;
		while(!popped)
		{
			try {hw = src.popHalfwordBlocking(); popped = true;}
			catch(InterruptedException e) 
			{
				if (resetRequest) throw e;
				//Otherwise, go back to trying to pop.
			}	
		}
		
		return hw;
	}
	
	private void updateStatus(int outputDepthEnum, boolean outputSigned, boolean setBit15)
	{
		mdecIO.setDataOutputDepth(outputDepthEnum);
		mdecIO.setDataOutputSigned(outputSigned);
		mdecIO.setDataOutputSet15(setBit15);
	}

	public void decodeMacroblocks(int outputDepthEnum, boolean outputSigned, boolean setBit15, int datSize) throws InterruptedException
	{
		//System.err.println("PSXMDEC.decodeMacroblocks || Called! datSize = " + datSize);
		updateStatus(outputDepthEnum, outputSigned, setBit15);
		hw_count = datSize;
		int initblock = 0;
		if (outputDepthEnum < 2) initblock = 4;
		HalfwordStream hws = mdecIO.getInputStream(initblock);
		try{
			while(hws.getPopCount() < hw_count){
				decodeMacroblock(hws, outputDepthEnum, outputSigned, setBit15);
			}
		}
		catch(CommandEndException x){
			//System.err.println("PSXMDEC.decodeMacroblocks || Command end found!");
		}
		finally{hws.resetPopCount();}
		
		//System.err.println("PSXMDEC.decodeMacroblocks || Exiting...");
	}
	
	public void decodeMacroblock(HalfwordStream src, int outputDepthEnum, boolean outputSigned, boolean setBit15) throws InterruptedException, CommandEndException
	{
		//HalfwordStream hws = mdecIO.getInputStream();
		//System.err.println("PSXMDEC.decodeMacroblock || Called!");
		int[] outblock = null;
		
		if(outputDepthEnum == OUTPUT_DEPTH_4BIT || outputDepthEnum == OUTPUT_DEPTH_8BIT) outblock = decode_monochrome_macroblock(src, !outputSigned);
		else outblock = decode_colored_macroblock(src, !outputSigned);
		outputBlock(outblock, outputDepthEnum, outputSigned, setBit15);
	}
	
	private void loadQuantTable(QuantMatrix qt, HalfwordStream src) throws InterruptedException
	{
		int x = 0;
		int y = 0;
		for (int i = 0; i < 32; i++)
		{
			//lo byte first, hi second
			int hw = popOrBlock(src);
			int b1 = hw & 0xFF;
			int b2 = (hw >>> 8) & 0xFF;
			qt.set(x, y, b1);
			x++;
			qt.set(x, y, b2);
			x++;
			if (x >= 8)
			{
				x = 0;
				y++;
			}
		}
	}
	
	public void setQuantTable(boolean setColor) throws InterruptedException
	{
		HalfwordStream hws = mdecIO.getInputStream(-1);
		loadQuantTable(qt_y, hws);
		
		//If also expecting a qt for color, repeat
		if(setColor) loadQuantTable(qt_c, hws);
		hws.resetPopCount();
	}
	
	public void setScaleTable() throws InterruptedException
	{
		//Scans the halfwords as they come in to see if any different from defo
		//If there are any differences, the incoming table is saved as scaleTable
		//If not, scaleTable is set to null
		HalfwordStream hws = mdecIO.getInputStream(-1);
		boolean allmatch = true;
		int[] rawtable = new int[64];
		for(int i = 0; i < 64; i++){
			int hw = popOrBlock(hws);
			rawtable[i] = hw;
			if (hw != DEFO_SCALE_TABLE_RAW[i]) allmatch = false;
		}
		
		if (!allmatch){
			//Convert to doubles
			//scaleTable = new double[64];
			scaleTable = new short[64];
			for(int i = 0; i < 64; i++){
				//double val = (double)((rawtable[i] >>> 14) & 0x03);
				/*double val = (double)((rawtable[i] >>> 14) & 0x03);
				
				int decPlaces = 11;
				int mask = 0x2000;
				double add = 0.5;
				for (int j = 0; j < decPlaces; j++)
				{
					if ((mask & rawtable[i]) != 0)
					{
						val += add;
					}
					add = add/2.0;
					mask = mask >>> 1;
				}
				scaleTable[i] = val;*/
				scaleTable[i] = (short)rawtable[i];
			}
		}
		else scaleTable = null;
		hws.resetPopCount();
	}
	
	private void outputBlock(int[] block, int outputDepthEnum, boolean outputSigned, boolean setBit15)
	{
		//System.err.println("PSXMDEC.outputBlock || Called! Block is null? " + (block == null));
		
		//This has to neatly convert the data if <24 bits too.
		mdecIO.signalMacroblockOutputQueueStart(outputDepthEnum, outputSigned, setBit15);
		if(output_yuv){
			//Just copy to output as is
			for (int i = 0; i < block.length; i++) mdecIO.queueWordForOutput(block[i]);
			mdecIO.signalMacroblockOutputQueueEnd();
			return;
		}
		if(!output_bitscale){
			//Rescale to Java friendly ARGB
			//System.err.println("PSXMDEC.outputBlock || Java output");
			
			for (int i = 0; i < block.length; i++){
				
				int val = block[i];
				int r = val & 0xFF;
				int g = (val >>> 8) & 0xFF;
				int b = (val >>> 16) & 0xFF;
				
				if(outputSigned){
					r -= 128; g -= 128; b -= 128;
					r &= 0xFF; g &= 0xFF; b &= 0xFF;
				}
				
				int w = 0xFF000000;
				w |= b;
				w |= (g << 8);
				w |= (r << 16);
				
				mdecIO.queueWordForOutput(w);
			}
			
			mdecIO.signalMacroblockOutputQueueEnd();
			return;
		}
		
		switch(outputDepthEnum)
		{
		case OUTPUT_DEPTH_4BIT:
			//MSB		LSB
			//PX7 PX6 PX5 PX4 PX3 PX2 PX1 PX0
			//Trim to 4 LSB???
			int outword4 = 0;
			for (int i = 0; i < block.length; i++)
			{
				int w4 = block[i];
				int mod = i%8;
				
				int bshift = mod*4;
				outword4 |= (w4 & 0xF) << bshift;
				
				if (mod == 7)
				{
					mdecIO.queueWordForOutput(outword4);
					outword4 = 0;
				}
			}
			break;
		case OUTPUT_DEPTH_8BIT:
			//MSB		LSB
			//PX3 PX2 PX1 PX0
			//Trim to 8 LSB and dump as is?
			int outword8 = 0;
			for (int i = 0; i < block.length; i++)
			{
				int w8 = block[i];
				int mod = i%4;
				
				int bshift = mod*8;
				outword8 |= (w8 & 0xFF) << bshift;
				
				if (mod == 3)
				{
					mdecIO.queueWordForOutput(outword8);
					outword8 = 0;
				}
			}
			break;
		case OUTPUT_DEPTH_15BIT:
			//MSB	LSB
			//PX1	PX0
			//Grab a word
			int outword15 = 0;
			for (int i = 0; i < block.length; i++)
			{
				int w15 = block[i];
				//Split RGB
				int r = w15 & 0xFF;
				int g = (w15 >> 8) & 0xFF;
				int b = (w15 >> 16) & 0xFF;
				//The scaler only works with unsigned values...
				if(outputSigned)
				{
					//Scale to unsigned...
					r = ((int)((byte)r)) + 128;
					g = ((int)((byte)g)) + 128;
					b = ((int)((byte)b)) + 128;
				}
				//Scale to 5 bit!
				int r5 = PSXImages.scale8Bit_to_5Bit(r);
				int g5 = PSXImages.scale8Bit_to_5Bit(g);
				int b5 = PSXImages.scale8Bit_to_5Bit(b);
				//Add to out word
				if(i % 2 == 0)
				{
					//LSB side
					outword15 |= (r5 & 0x1F);
					outword15 |= (g5 & 0x1F) << 5;
					outword15 |= (b5 & 0x1F) << 10;
				}
				else
				{
					//MSB side
					outword15 |= (r5 & 0x1F) << 16;
					outword15 |= (g5 & 0x1F) << 21;
					outword15 |= (b5 & 0x1F) << 26;
					
					if(setBit15) outword15 |= 0x80008000;
					mdecIO.queueWordForOutput(outword15);
					outword15 = 0;
				}
			}
			break;
		case OUTPUT_DEPTH_24BIT:
			//Mercifully, can just dump as is
			for (int w24 : block) mdecIO.queueWordForOutput(w24);
			break;
		}
		
		mdecIO.signalMacroblockOutputQueueEnd();
	}
	
	/* ----- Decode Native - Zigzag ----- */
	
	private static final double[] SCALE_FACTOR = {1.000000000, 1.387039845, 1.306562965, 1.175875602,
												  1.000000000, 0.785694958, 0.541196100, 0.275899379};
	protected static final int[] ZIGZAG_ARR = { 0, 1, 5, 6,14,15,27,28,
											  2, 4, 7,13,16,26,29,42,
											  3, 8,12,17,25,30,41,43,
											  9,11,18,24,31,40,44,53,
											 10,19,23,32,39,45,52,54,
											 20,22,33,38,46,51,55,60,
											 21,34,37,47,50,56,59,61,
											 35,36,48,49,57,58,62,63};
	private static double[] scaleZag;
	private static int[] zagzig;
	
	public static int[] getZigzagMatrix_Array()
	{
		return ZIGZAG_ARR;
	}
	
	public static double[] getScaleZagMatrix_Array()
	{
		if (scaleZag != null) return scaleZag;
		scaleZag = new double[64];
		for (int y = 0; y < 8; y++)
		{
			for(int x = 0; x < 8; x++)
			{
				int idx = ZIGZAG_ARR[x + (y*8)];
				double sf = SCALE_FACTOR[x] * (SCALE_FACTOR[y] / 8.0);
				scaleZag[idx] = sf;
			}
		}
		return scaleZag;
	}
	
	public static int[] getZagzigMatrix_Array()
	{
		if (zagzig != null) return zagzig;
		zagzig = new int[64];
		for (int i = 0; i < 64; i++)
		{
			int idx = ZIGZAG_ARR[i];
			zagzig[idx] = i;
		}
		return zagzig;
	}
	
	/* ----- Decode Native ----- */
	
	private int signed10bit(int in)
	{
		int n1 = in & 0x3FF;
		boolean sign = (in & 0x200) != 00;
		if (!sign) return n1;
		return n1 | 0xFFFFFC00;
	}
	
	private int[] combineYBlocks(int[] Y1, int[] Y2, int[] Y3, int[] Y4){
		//System.err.println("PSXMDEC.combineYBlocks || Called!");
		int[] Yblk = new int[256];
		for (int y = 0; y < 16; y++){
			for (int x = 0; x < 16; x++){
				int didx = (y*16) + x;
				int sidx = ((y%8)*8) + (x%8);
				if (x < 8){
					if (y < 8) Yblk[didx] = Y1[sidx];
					else Yblk[didx] = Y3[sidx];
				}
				else{
					if (y < 8) Yblk[didx] = Y2[sidx];
					else Yblk[didx] = Y4[sidx];
				}
			}
		}
		//System.err.println("PSXMDEC.combineYBlocks || Returning...");
		return Yblk;
	}
	
	private int[] yuv_to_export(int[][] blocks){

		int[] dest = new int[96];
		int d = 0;
		for(int b = 0; b < 6; b++){
			int e = 0;
			for(int i = 0; i < 16; i++){
				int word = 0;
				for(int j = 0; j < 4; j++){
					word = word << 8;
					word |= blocks[b][e++] & 0xff;
				}
				dest[d++] = word;
			}
		}
		
		return dest;
	}
	
	public int[] decode_colored_macroblock(HalfwordStream src, boolean unsigned) throws InterruptedException, CommandEndException
	{
		//System.err.println("PSXMDEC.decode_colored_macroblock || Called!");
		
		int[] dest = new int[256];
		boolean usefast = (scaleTable == null);
		
		//Cr
		mdecIO.setCurrentInputBlock(4);
		int[] Crblk = rl_decode_block(src, usefast, qt_c);
		//Cb
		mdecIO.setCurrentInputBlock(5);
		int[] Cbblk = rl_decode_block(src, usefast, qt_c);
		//Y1
		mdecIO.setCurrentInputBlock(0);
		int[] Y1 = rl_decode_block(src, usefast, qt_y);
		//Y2
		mdecIO.setCurrentInputBlock(1);
		int[] Y2 = rl_decode_block(src, usefast, qt_y);
		//Y3
		mdecIO.setCurrentInputBlock(2);
		int[] Y3 = rl_decode_block(src, usefast, qt_y);
		//Y4
		mdecIO.setCurrentInputBlock(3);
		int[] Y4 = rl_decode_block(src, usefast, qt_y);
		//System.err.println("PSXMDEC.decode_colored_macroblock || All blocks decoded! Combining...");
		
		/*System.err.println("CR");
		printBlock(Crblk, 8);
		System.err.println("CB");
		printBlock(Cbblk, 8);
		System.err.println("Y1");
		printBlock(Y1, 8);
		System.err.println("Y2");
		printBlock(Y2, 8);
		System.err.println("Y3");
		printBlock(Y3, 8);
		System.err.println("Y4");
		printBlock(Y4, 8);*/
		
		if(output_yuv){
			dest = yuv_to_export(new int[][]{Crblk, Cbblk, Y1, Y2, Y3, Y4});
			return dest;
		}
		
		//Combine Y pieces
		//Convert to RGB
		int[] Yblk = combineYBlocks(Y1,Y2,Y3,Y4);
		yuv_to_rgb(0,0, Crblk, Cbblk, Yblk, dest, unsigned);
		yuv_to_rgb(0,8, Crblk, Cbblk, Yblk, dest, unsigned);
		yuv_to_rgb(8,0, Crblk, Cbblk, Yblk, dest, unsigned);
		yuv_to_rgb(8,8, Crblk, Cbblk, Yblk, dest, unsigned);
		
		//System.err.println("PSXMDEC.decode_colored_macroblock || Returning...");
		//System.exit(2);
		return dest;
	}

	public int[] decode_monochrome_macroblock(HalfwordStream src, boolean unsigned) throws InterruptedException, CommandEndException
	{
		int[] dest = new int[64];
		boolean usefast = (scaleTable == null);
		
		int[] Yblk = rl_decode_block(src, usefast, qt_y);
		y_to_mono(Yblk, dest, unsigned);
		
		return dest;
	}
	
	public int[] rl_decode_block(HalfwordStream src, boolean useFast, QuantMatrix qt) throws InterruptedException, CommandEndException
	{
		//System.err.println("PSXMDEC.rl_decode_block || Called!");
		
		int[] blk = new int[64]; //Java automatically zero-fills, isn't that lovely?
		//Skip any padding
		int hw = popOrBlock(src);
		//System.err.println("PSXMDEC.rl_decode_block || Halfword popped: 0x" + Integer.toHexString(hw));
		while(hw == PADDING_CODE){
			hw = popOrBlock(src);
			//System.err.println("PSXMDEC.rl_decode_block || Halfword popped: 0x" + Integer.toHexString(hw));
			if(src.getPopCount() >= hw_count) throw new CommandEndException();
		}
		//Get the scale value
		int k = 0;
		int q_scale = (hw >>> 10) & 0x3F;
		int val = signed10bit(hw);
		//System.err.println("PSXMDEC.rl_decode_block || DC: " + val);
		if(q_scale != 0) val *= qt.get(k);
		else val *= 2;
		//System.err.println("PSXMDEC.rl_decode_block || Quanted DC: " + val);
		//val appears to be the DC here.
		
		//useFast = false; //DEBUG
		double[] scalearr = getScaleZagMatrix_Array();
		int[] zagzig = getZagzigMatrix_Array();
		while(k < 64){
			//per nocash's method:
			//	val=minmax(val,-400h,+3FFh) ;saturate to signed 11bit range
			//	Not sure if I need this?
			
			//System.err.println("PSXMDEC.rl_decode_block || Scale Factor: " + scalearr[k]);
			if (useFast) val = (int)((double)val * scalearr[k]);
			/*A couple questions here...
			 * 1. The doc says "scalezag[i]", but I'm not sure what i should be?
			 * 2. Should I floor it?
			 */
			
			blk[zagzig[k]] = val;
			hw = popOrBlock(src);
			//System.err.println("PSXMDEC.rl_decode_block || Halfword popped: 0x" + Integer.toHexString(hw));
			//int knxt = k;
			if(hw == PADDING_CODE){
				//The remainder is 0 filled.
				//knxt = 64;
				k = 64;
				break;
			}
			/*else{
				knxt += ((hw >>> 10) & 0x3F) + 1;
			}*/
			
			//Put in all values we gonna skip
			/*while(++k < knxt){
				blk[zagzig[k]] = val;
			}*/
			k += ((hw >>> 10) & 0x3F) + 1;
			
			//Calculate new val
			if(k < 64){
				val = signed10bit(hw);
				if(q_scale != 0) val = (val * qt.get(k) * (q_scale) + 4)/8;
				else val *= 2;	
			}
		}
		
		//System.err.println("Initial Block");
		//printBlock(blk, 8);
		
		int[] newblk = null;
		if(useFast) newblk = fast_idct_core(blk);
		else newblk = real_idct_core(blk);
		
		//System.err.println("PSXMDEC.rl_decode_block || Returning...");
		return newblk;
	}
	
	public int[] fast_idct_core(int[] blk)
	{
		//System.err.println("PSXMDEC.fast_idct_core || Called!");
		
		int[] dest = new int[64];
		final double sqrt2 = Math.sqrt(2.0);
		final double sclMult = sqrt2 * SCALE_FACTOR[2];
		final double sclDbl = 2.0 * SCALE_FACTOR[2];
		final double sclDiv = sqrt2 / SCALE_FACTOR[2];
		
		for (int pass = 0; pass < 2; pass++){
			for (int i = 0; i < 8; i++){
				boolean tooManyZeros = true;
				for (int j = 1; j < 8; j++){
					if (blk[(j*8)+i] != 0)
					{
						tooManyZeros = false;
						break;
					}
				}
				
				if(tooManyZeros){
					for (int j = 0; j < 8; j++){
						dest[(i*8) + j] = blk[i]; //Says "src[0*8+i]". 
						//I see why, but I don't know if the compiler is smart enough to ignore the 0
					}
				}
				else{
					double z10 = (double)(blk[i] + blk[4*8+i]); //Again "0*8+i"
					double z11 = (double)(blk[i] - blk[4*8+i]);
					double z13 = (double)(blk[2*8+i] + blk[6*8+i]);
					double z12 = (double)(blk[2*8+i] - blk[6*8+i]);
					z12 = (sqrt2 * z12) - z13;
					double tmp0 = z10 + z13;
					double tmp1 = z11 + z12;
					double tmp2 = z11 - z12;
					double tmp3 = z10 - z13;
					z13 = (double)(blk[3*8+i] + blk[5*8+i]);
					z10 = (double)(blk[3*8+i] - blk[5*8+i]);
					z11 = (double)(blk[8+i] + blk[7*8+i]); // 1*8 + i
					z12 = (double)(blk[8+i] - blk[7*8+i]);
					double z5 = (sclMult * (z12 - z10));
					double tmp7 = z11 + z13;
					double tmp6 = (sclDbl * z10) + z5 - tmp7;
					double tmp5 = (sqrt2 * (z11 - z13)) - tmp6;
					double tmp4 = (sclDiv * z12) - z5 + tmp5;
					int eight = i*8;
					dest[eight] = (int)(tmp0 + tmp7); //These are all getting floored!
					dest[eight + 1] = (int)(tmp1 + tmp6);
					dest[eight + 2] = (int)(tmp2 + tmp5);
					dest[eight + 4] = (int)(tmp3 + tmp4);
					dest[eight + 3] = (int)(tmp3 - tmp4);
					dest[eight + 5] = (int)(tmp2 - tmp5);
					dest[eight + 6] = (int)(tmp1 - tmp6);
					dest[eight + 7] = (int)(tmp0 - tmp7);
				}
			}
			if(pass > 0) break;
			//Swap the arrays
			int[] temp = dest;
			dest = blk;
			blk = temp;
		}
		
		return dest;
	}
	
	public int[] real_idct_core(int[] blk)
	{
		int[] dest = new int[64];
		
		short[] tbl = scaleTable;
		if(tbl == null){
			tbl = new short[64];
			for(int i = 0; i < 64; i++) tbl[i] = (short)DEFO_SCALE_TABLE_RAW[i];
		}
		
		for (int pass = 0; pass < 2; pass++){
			for (int x = 0; x < 8; x++){
				for (int y = 0; y < 8; y++){
					//double sum = 0.0;
					int sum = 0;
					for (int z = 0; z < 8; z++){
						int z8 = z*8;
						int scaler = (int)tbl[x+z8];
						scaler = scaler >> 3;
						int val = blk[y+z8];
						sum += val * scaler;
					}
					//No rescaling since being treated as a double?
					//dest[x+y*8] = (int)Math.round(sum);
					dest[x+(y*8)] = (sum + 0xfff)/0x2000;
				}
			}
			
			if(pass > 0) break;
			//Swap the arrays
			int[] temp = dest;
			dest = blk;
			blk = temp;
		}
		
		return dest;
	}
	
	public void yuv_to_rgb(int xx, int yy, int[] Crblk, int[] Cbblk, int[] Yblk, int[] dest, boolean unsigned)
	{
		//System.err.println("PSXMDEC.yuv_to_rgb || Called!");
		for(int y = 0; y < 8; y++){
			for (int x = 0; x < 8; x++){
				int cx = (x+xx) >>> 1;
				int cy = (y+yy) >>> 1;
				int idx = ((cy << 3) + cx);
				
				//int R = 0;
				//int B = 0;
				int R = Crblk[idx];
				int B = Cbblk[idx];
				int G = (int)((-0.3437 * (double)B)+(-0.7143 * (double)R)); //Floored
				R = (int)(1.402*(double)R);
				B = (int)(1.772*(double)B);
				
				int yidx = (x+xx) + ((y+yy) << 4);
				int Y = Yblk[yidx];
				//System.err.println("PSXMDEC.yuv_to_rgb || (" + x + "," + y +") -> (" + (x+xx) + "," + (y+yy) + ") CLR: " + idx + " | Y: " + yidx);
				R += Y; //I don't know if the flooring will work properly!
				G += Y;
				B += Y;
				if (R > 127) R = 127; if (R < -128) R = -128;
				if (G > 127) G = 127; if (G < -128) G = -128;
				if (B > 127) B = 127; if (B < -128) B = -128;
				if (unsigned){
					R += 128;
					G += 128;
					B += 128;
				}
				
				int out = R;
				out |= G << 8;
				out |= B << 16;
				
				//idx = (x+xx)+(y+yy)*16;
				dest[yidx] = out;
			}
		}
		//System.exit(2);
		//System.err.println("PSXMDEC.yuv_to_rgb || Returning...");
	}
	
	public void y_to_mono(int[] Yblk, int[] dest, boolean unsigned)
	{
		for (int i = 0; i < 64; i++)
		{
			int Y = Yblk[i];
			Y &= 0x1FF;
			if ((Y & 0x100) != 0) Y |= 0xFFFFFF00;
			if (Y > 127) Y = 127;
			if (Y < -128) Y = -128;
			if (unsigned) Y += 128;
			dest[i] = Y;
		}
	}

	/* ----- Other Commands ----- */
	
	public void requestReset()
	{
		resetRequest = true;
		stop();
		exeQueue.clear();
	}
	
	protected void completeReset()
	{
		qt_y = new QuantMatrix();
		qt_c = new QuantMatrix();
		scaleTable = null;
	}
	
	/* ----- Debug ----- */
	
	public void printBlock(int[] block, int cols){
		
		int rows = block.length/cols;
		
		for(int r = 0; r < rows; r++){
			for(int l = 0; l < cols; l++){
				System.err.print(block[(r*cols) + l] + " ");
			}
			System.err.println();
		}
		
	}
	
}

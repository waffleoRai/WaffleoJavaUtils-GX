package waffleoRai_Sound.nintendo;

import java.util.Arrays;

import waffleoRai_SoundSynth.AudioSampleStream;
import waffleoRai_Utils.FileBuffer;

public class VADPCM {
	/*
	 * This is based off of simonlindholm's RE of some of the audio tools in the N64 SDK
	 * https://github.com/n64decomp/sdk-tools
	 * By "Based off of" I mean copied large parts and translated to Java :D
	 */
	
	/*----- Instance Variables -----*/
	
	private int samps_per_package = 16;
	//private int src_bits = 16; //Fixed
	private int trg_bits = 4;
	
	private int order = 2;
	
	private double thresh = 10.0;
	private int refine_itr = 2;
	
	private int[] last_block;
	private N64ADPCMTable codebook;
	
	/*----- Init -----*/
	
	public VADPCM(){}
	
	/*----- Getters -----*/
	
	public int getSamplesPerPackage(){return samps_per_package;}
	public int getTargetBitDepth(){return trg_bits;}
	public int getOrder(){return order;}
	public double getThreshold(){return thresh;}
	public int getRefineIterations(){return refine_itr;}
	
	/*----- Setters -----*/
	
	public void setSamplesPerPackage(int val){
		if(val <= 0) return;
		samps_per_package = val;
	}
	
	public void setTargetBitDepth(int bits){
		if(bits <= 0) return;
		if(bits >= 16) return;
		trg_bits = bits;
	}
	
	public void setOrder(int val){
		if(val <= 0) return;
		order = val;
	}
	
	public void setThreshold(double val){
		thresh = val;
	}
	
	public void setRefineIterations(int val){
		refine_itr = val;
	}
	
	/*----- Encoding -----*/
	//This stuff is mostly wholesale copied from sdk-tools

	private void acvect(int[] in, int n, int m, double[] out){
		for(int i = 0; i <= n; i++){
			out[i] = 0.0;
			for(int j = 0; j < m; j++){
				out[i] -= (double)in[j - i] * (double)in[j];
			}
		}
	}
	
	private void acmat(int[] in, int n, int m, double[][] out){
		for(int i = 1; i <= n; i++){
			for(int j = 1; j <= n; j++){
				out[i][j] = 0.0;
	            for (int k = 0; k < m; k++)
	            {
	                out[i][j] += (double)in[k - i] * (double)in[k - j];
	            }
			}
		}
	}
	
	private int lud(double[][] mat, int n, int[] indx, int[] d){
		double[] vec = new double[n+1];
		double big, dum, sum, temp;
	    int imax = 0;
		
		d[0] = 1;
		for (int i = 1; i <= n; i++) {
	        big = 0.0;
	        for (int j = 1; j <= n; j++){
	            if ((temp = Math.abs(mat[i][j])) > big) big = temp;
	        }
	        if (big == 0.0) return 1;
	        vec[i] = 1.0/big;
	    }
		for (int j = 1; j <= n; j++) {
			for (int i = 1; i < j; i++) {
	            sum = mat[i][j];
	            for (int k = 1; k < i; k++) sum -= mat[i][k] * mat[k][j];
	            mat[i][j] = sum;
	        }
	        big = 0.0;
	        for (int i = j; i <= n; i++) {
	            sum = mat[i][j];
	            for (int k = 1; k < j; k++){
	            	sum -= mat[i][k] * mat[k][j];
	            }
	            mat[i][j] = sum;
	            if ((dum = vec[i] * Math.abs(sum)) >= big) {
	                big = dum;
	                imax = i;
	            }
	        }
	        if (j != imax) {
	            for (int k = 1; k <= n; k++) {
	                dum = mat[imax][k];
	                mat[imax][k] = mat[j][k];
	                mat[j][k] = dum;
	            }
	            d[0] = -d[0];
	            vec[imax] = vec[j];
	        }
	        indx[j] = imax;
	        if (mat[j][j] == 0.0) return 1;
	        if (j != n) {
	            dum = 1.0/(mat[j][j]);
	            for (int i = j+1; i <= n; i++) mat[i][j] *= dum;
	        }
		}
		
		double min = 1e10;
	    double max = 0.0;
	    for (int i = 1; i <= n; i++)
	    {
	        temp = Math.abs(mat[i][i]);
	        if (temp < min) min = temp;
	        if (temp > max) max = temp;
	    }
	    return min / max < 1e-10 ? 1 : 0;
	}
	
	private void lubksb(double[][] mat, int n, int[] indx, double[] out){
		
		double sum = 0.0;
		
		for (int i = 1; i <= n; i++) {
			int imatch = 0;
	        int ip = indx[i];
	        sum = out[ip];
	        out[ip] = out[i];
	        if (imatch != 0){
	        	for (int j = imatch; j <= i-1; j++) sum -= mat[i][j] * out[j];
	        }
	        else if (sum != 0) imatch = i;
	        out[i]=sum;
	    }
		for (int i = n; i >= 1; i--) {
	        sum = out[i];
	        for (int j = i+1; j <= n; j++) sum -= mat[i][j] * out[j];
	        out[i] = sum/mat[i][i];
	    }
	}
	
	private int kfroma(double[] in, double[] out, int n){
		int c = 0;
		double temp, div;
		double[] nxt = new double[n+1];
		
		out[n] = in[n];
		for (int i = n - 1; i >= 1; i--){
			for (int j = 0; j <= i; j++){
				temp = out[i+1];
				div = 1.0 - (temp * temp);
				if(div == 0.0){
					return 1;
				}
				nxt[j] = (in[j] - in[i + 1 - j] * temp) / div;
			}
			for (int j = 0; j <= i; j++) in[j] = nxt[j];
			out[i] = nxt[i];
			if(Math.abs(out[i]) > 1.0) c++;
	    }
		
		return c;
	}
	
	private void afromk(double[] in, double[] out, int n){
		out[0] = 1.0;
		for(int i = 1; i <= n; i++){
			out[i] = in[i];
			for(int j = 1; j <= i - 1; j++){
				out[j] += out[i-j] * out[i];
			}
		}
	}
	
	private void rfroma(double[] in, int n, double[] out){
		double[][] mat = new double[n+1][];
		mat[n] = new double[n+1];
		mat[n][0] = 1.0;
		
		double div;
		
		for(int i = 1; i <= n; i++) mat[n][1] = -in[i];
		for(int i = n; i >= 1; i--){
			mat[i-1] = new double[i];
			div = 1.0 - mat[i][i] * mat[i][i];
			for(int j = 1; j <= i - 1; j++){
				mat[i - 1][j] = (mat[i][i - j] * mat[i][i] + mat[i][j]) / div;
			}
		}
		
		out[0] = 1.0;
		for(int i = 1; i <= n; i++){
			out[i] = 0.0;
			for(int j = 1; j <= i; j++) out[i] += mat[i][j] * out[i - j];
		}
	}
	
	private int durbin(double[] v0, int n, double[] v1, double[] v2, double[] v4){
		int c = 0;
		
		v2[0] = 1.0;
		double div = v0[0];
		double sum = 0.0;
		for(int i = 1; i <= n ; i++){
			sum = 0.0;
			for(int j = 1; j <= i - 1; j++){
				sum += v2[j] * v0[i - j];
			}
			
			v2[i] = (div > 0.0 ? -(v0[i] + sum) / div : 0.0);
			v1[i] = v2[i];
			
			if(Math.abs(v1[i]) > 1.0) c++;
			
			for(int j = 1; j < i; j++){
				v2[j] += v2[i-j] * v2[i];
			}
			
			div *= 1.0 - v2[i] * v2[i];
		}
		
		if(v4 != null) v4[0] = div;
		return c;
	}
	
	private double model_dist(double[] v0, double[] v1, int n){
		double[] d0 = new double[n+1];
		double[] d1 = new double[n+1];
		double c = 0.0;
		
		rfroma(v1, n, d0);
		for (int i = 0; i <= n; i++){
	        d1[i] = 0.0;
	        for (int j = 0; j <= n - i; j++) d1[i] += v0[j] * v0[i + j];
	    }
		
		c = d1[0] * d0[0];
	    for (int i = 1; i <= n; i++){
	        c += 2 * d0[i] * d1[i];
	    }
		return c;
	}
	
	private void split(double[][] table, double[] delta, int order, int npred, double scale){
		for(int i = 0; i < npred; i++){
			for(int j = 0; j <= order; j++){
				table[i + npred][j] = table[i][j] + delta[j] * scale;
			}
		}
	}
	
	private void refine(double[][] table, int order, int npred, double[][] data, int dataSize, int refineItr){
		double[][] rsums = new double[npred][order+1];
		int[] counts = new int[npred];
		double[] dbuff = new double[order+1];
		
		double bestValue = 1e30;
		int bestIndex = 0;
		double dist = 0;
		
		for(int itr = 0; itr < refineItr; itr++){
			for (int i = 0; i < npred; i++){
	            counts[i] = 0;
	            for (int j = 0; j <= order; j++){
	                rsums[i][j] = 0.0;
	            }
	        }
			
			for (int i = 0; i < dataSize; i++){
	            bestValue = 1e30;
	            bestIndex = 0;

	            for (int j = 0; j < npred; j++){
	                dist = model_dist(table[j], data[i], order);
	                if (dist < bestValue){
	                    bestValue = dist;
	                    bestIndex = j;
	                }
	            }

	            counts[bestIndex]++;
	            rfroma(data[i], order, dbuff);
	            for (int j = 0; j <= order; j++){
	                rsums[bestIndex][j] += dbuff[j];
	            }
	        }
			
			for (int i = 0; i < npred; i++){
	            if (counts[i] > 0){
	                for (int j = 0; j <= order; j++) rsums[i][j] /= counts[i];
	            }
	        }

	        for (int i = 0; i < npred; i++){
	            durbin(rsums[i], order, dbuff, table[i], null);

	            for (int j = 1; j <= order; j++){
	                if (dbuff[j] >=  1.0) dbuff[j] =  0.9999999999;
	                if (dbuff[j] <= -1.0) dbuff[j] = -0.9999999999;
	            }

	            afromk(dbuff, table[i], order);
	        }
		}
	}
	
	private int outputRow(short[] out, double[] in, int order){
		double[][] table = new double[8][order];
		int overflows = 0;
		
		for (int i = 0; i < order; i++){
	        for (int j = 0; j < i; j++) table[i][j] = 0.0;
	        for (int j = i; j < order; j++) table[i][j] = -in[order - j + i];
	    }
		
		for (int i = order; i < 8; i++){
	        for (int j = 0; j < order; j++) table[i][j] = 0.0;
	    }
		
		for (int i = 1; i < 8; i++){
	        for (int j = 1; j <= order; j++){
	            if (i - j >= 0){
	                for (int k = 0; k < order; k++){
	                    table[i][k] -= in[j] * table[i - j][k];
	                }
	            }
	        }
	    }
		
		double fval = 0.0;
		int ival = 0;
		int o = 0;
		for (int i = 0; i < order; i++){
	        for (int j = 0; j < 8; j++){
	            fval = table[j][i] * 2048.0;
	            if (fval < 0.0){
	                ival = (int) (fval - 0.5);
	                if (ival < -0x8000) overflows++;
	            }
	            else{
	                ival = (int) (fval + 0.5);
	                if (ival >= 0x8000) overflows++;
	            }
	            out[o++] = (short)ival;
	        }
	    }
		return overflows;
	}
	
	public N64ADPCMTable buildTable(AudioSampleStream input, int samples, int channel){
		int[] ibuff1 = new int[samps_per_package];
		int[] ibuff2 = new int[samps_per_package];
		double[] vec = new double[order+1];
		double[][] mat = new double[order+1][order+1];
		int[] perm = new int[order+1];
		int[] permDet = new int[1];
		double[] spF4 = new double[order+1];
		
		int pkgcount = samples/samps_per_package;
		int mod = samples%samps_per_package;
		if(mod != 0) pkgcount++;
		
		double[][] data = new double[pkgcount][];
		int didx = 0;
		
		int rem = samples;
		while(rem > 0){
			//Load in a package worth of samples (to ibuff1)
			//Zero-fill if not full package left.
			for(int i = 0; i < samps_per_package; i++){
				if(rem-- > 0){
					try{
						int[] s = input.nextSample();
						ibuff1[i] = s[channel];
					}
					catch(Exception ex){ex.printStackTrace(); return null;}
				}
				else ibuff1[i] = 0;
			}
			
			//Linear algebra magic
			acvect(ibuff1, order, samps_per_package, vec);
			if(Math.abs(vec[0]) > thresh){
				acmat(ibuff1, order, samps_per_package, mat);
				if(lud(mat, order, perm, permDet) == 0){
					lubksb(mat, order, perm, vec);
					vec[0] = 1.0;
					if(kfroma(vec, spF4, order) == 0){
						data[didx] = new double[order+1];
						data[didx][0] = 1.0;
						for (int i = 1; i <= order; i++){
	                        if (spF4[i] >=  1.0) spF4[i] =  0.9999999999;
	                        if (spF4[i] <= -1.0) spF4[i] = -0.9999999999;
	                    }

	                    afromk(spF4, data[didx], order);
	                    didx++;
					}
				}
			}
			for(int i = 0; i < samps_per_package; i++) ibuff2[i] = ibuff1[i];
		}
		
		vec[0] = 1.0;
		for(int j = 1; j <= order; j++) vec[j] = 0.0;
		
		double[][] dbuff0 = new double[1 << trg_bits][order+1];
		for(int i = 0; i < didx; i++){
			rfroma(data[i], order, dbuff0[0]);
			for(int j = 1; j <= order; j++) vec[j] += dbuff0[0][j];
		}
		for(int j = 1; j <= order; j++) vec[j] /= (double)didx;
		
		durbin(vec, order, spF4, dbuff0[0], null);
		for(int j = 1; j <= order; j++){
			if (spF4[j] >=  1.0) spF4[j] =  0.9999999999;
	        if (spF4[j] <= -1.0) spF4[j] = -0.9999999999;
		}
		
		afromk(spF4, dbuff0[0], order);
	    int curBits = 0;
	    double[] splitDelta = new double[order+1];
	    while(curBits < trg_bits){
	    	for (int i = 0; i <= order; i++) splitDelta[i] = 0.0;
	        splitDelta[order - 1] = -1.0;
	        split(dbuff0, splitDelta, order, 1 << curBits, 0.01);
	        curBits++;
	        refine(dbuff0, order, 1 << curBits, data, didx, refine_itr);
	    }
		
	    int npred = 1 << curBits;
	    int numOverflows = 0;
	    short[][] outtbl = new short[npred][order * 8];
	    for(int i = 0; i < npred; i++) numOverflows += outputRow(outtbl[i], dbuff0[i], order);
	    
	    if(numOverflows > 0){
	    	System.err.println("VADPCM.buildTable || Warning: coefficient overflows occured.");
	    }
	    
	    //Combine tables
		return N64ADPCMTable.fromRaw(order, npred, outtbl);
	}
	
	private int tblip(int p, int i, int[] invec){
		//inner_product(order + i, coefTable[p][i], invec);
		if(codebook == null) return 0;
		int n = order + i;
		int sum = 0;
		for(int j = 0; j < order; j++){
			sum += invec[j] * codebook.getCoefficient(p, j, i);
		}
		for(int j = order; j < n; j++){
			int ii = i - ((j-order) + 1);
			sum += invec[j] * codebook.getCoefficient(p, order-1, ii);
		}
		
		int dout = sum >> 11;
		int fiout = dout << 11;
		if(fiout > sum) dout--;
		
		return dout;
	}
	
	private void enc_clamp(int fs, float[] e, int[] ie, int bits){
	    float llevel = -(float) (1 << (bits - 1));
	    float ulevel = -llevel - 1.0f;
	    for (int i = 0; i < fs; i++){
	        if (e[i] > ulevel) e[i] = ulevel;
	        if (e[i] < llevel) e[i] = llevel;
	        if (e[i] > 0.0f)ie[i] = (int) (e[i] + 0.5);
	        else ie[i] = (int) (e[i] - 0.5);
	    }
	}
	
	private int qsample(float x, int scale){
		if (x > 0.0f){
	        return ((int)((x / (float)scale) + 0.4999999)) & 0xffff;
	    }
		return ((int)((x / (float)scale) - 0.4999999)) & 0xffff;
	}
	
	private int clamp_bits(int x, int bits){
		int lim = 1 << (bits - 1);
	    if (x < -lim) return -lim;
	    if (x > lim - 1) return lim - 1;
	    return x;
	}
	
	private int encodePackage(int[] samples, FileBuffer output){
		//This one's mostly copied from aifc_decode.c from oot decomp
		//Setup some values
		int npred = codebook.getPredictorCount();
		int groups = samps_per_package >>> 1;
	    int scale_factor = 12;
	    int op = 0;
	    int llevel = -(1 << (trg_bits - 1));
	    int ulevel = -llevel - 1;
	    float min = 1e30f;
		
	    //Allocate some workspace vectors
	    int[] sstate = new int[samps_per_package];
		int[] invec = new int[samps_per_package];
		int[] prediction = new int[samps_per_package];
		int[] ie = new int[samps_per_package];
		float[] e = new float[samps_per_package];
		int[] ix = new int[samps_per_package];
		if(last_block == null) last_block = new int[samps_per_package];
		
		//Determine the best predictor
		for (int k = 0; k < npred; k++) {
	        for (int j = 0; j < groups; j++) {
	            for (int i = 0; i < order; i++) {
	            	invec[i] = (j == 0 ? last_block[samps_per_package - order + i] : samples[8 - order + i]);
	            }

	            for (int i = 0; i < 8; i++) {
	                prediction[j * 8 + i] = tblip(k, i, invec);
	                invec[i + order] = samples[j * 8 + i] - prediction[j * 8 + i];
	                e[j * 8 + i] = (float)invec[i + order];
	            }
	        }

	        float se = 0.0f;
	        for (int j = 0; j < 16; j++) se += e[j] * e[j];

	        if (se < min) {
	            min = se;
	            op = k;
	        }
	    }
		
		for (int j = 0; j < groups; j++) {
	        for (int i = 0; i < order; i++) {
	        	invec[i] = (j == 0 ? last_block[16 - order + i] : samples[8 - order + i]);
	        }

	        for (int i = 0; i < 8; i++) {
	            prediction[j * 8 + i] = tblip(op, i, invec);
	            e[j * 8 + i] = invec[i + order] = samples[j * 8 + i] - prediction[j * 8 + i];
	        }
	    }
		
		enc_clamp(16, e, ie, 16);
		
		int max = 0;
	    for (int i = 0; i < samps_per_package; i++) {
	        if (Math.abs(ie[i]) > Math.abs(max)) {
	            max = ie[i];
	        }
	    }
	    
	    scale_factor = 16 - trg_bits;
	    int scale = 0;
	    for (scale = 0; scale <= scale_factor; scale++) {
	        if (max <= ulevel && max >= llevel) break;
	        max >>= 1;
	    }
	    
	    for (int i = 0; i < samps_per_package; i++) sstate[i] = last_block[i];
	    
	    boolean again = true;
	    for (int n = 0; n < 2 && again; n++) {
	        again = false;
	        if (n == 1) scale++;
	        if (scale > scale_factor) {
	            scale = scale_factor;
	        }

	        for (int j = 0; j < groups; j++) {
	            int base = j * 8;
	            for (int i = 0; i < order; i++) {
	                invec[i] = (j == 0 ?
	                		sstate[16 - order + i] : last_block[8 - order + i]);
	            }

	            for (int i = 0; i < 8; i++) {
	                prediction[base + i] = tblip(op, i, invec);
	                float se = (float)samples[base + i] - (float)prediction[base + i];
	                ix[base + i] = qsample(se, 1 << scale);
	                int cV = clamp_bits(ix[base + i], trg_bits) - ix[base + i];
	                if (cV > 1 || cV < -1) again = true;
	                ix[base + i] += cV;
	                invec[i + order] = ix[base + i] * (1 << scale);
	                last_block[base + i] = prediction[base + i] + invec[i + order];
	            }
	        }
	    }
	    
	    //Serialize
	    int hdr = (scale << 4) | (op & 0xf);
	    int sz = 1;
	    output.addToFile((byte)hdr);
	    int b = 0;
	    int bpos = 7;
	    for(int i = 0; i < samps_per_package; i++){
	    	//Tack bits on end until can't fit anymore
	    	for(int j = 0; j < trg_bits; j++){
	    		b |= (ix[i] >> (trg_bits-1-j)) & 0x1;
	    		if(bpos-- == 0){
	    			//That was that last bit for this byte
	    			output.addToFile((byte)b);
	    			sz++;
	    			b = 0; bpos = 7;
	    		}
	    		else b <<= 1;
	    	}
	    }
	    
	    //Zero pad and put last byte in if unfinished.
	    if(bpos < 7){
	    	b <<= bpos+1;
	    	output.addToFile((byte)b);
			sz++;
	    }
		
		return sz;
	}
	
	public FileBuffer encodeAudio(AudioSampleStream input, N64ADPCMTable codebook, int samples, int channel) throws InterruptedException{
		//Set encoder instance vars
		this.codebook = codebook;
		last_block = new int[samps_per_package];
		int p_order = order;
		order = codebook.getOrder();
		
		//Allocate output buffer.
		int p_count = samples/samps_per_package;
		if(samples % samps_per_package > 0) p_count++;
		int p_sz = 1;
		int pbits = trg_bits * samps_per_package;
		p_sz += (pbits >>> 3);
		if((pbits & 0x7) != 0) p_sz++;
		FileBuffer outbuff = new FileBuffer(p_count*p_sz, true);
		
		//Loop thru input and encode packages
		int rem = samples;
		int[] samps = new int[samps_per_package];
		while(rem >= samps_per_package){
			for(int i = 0; i < samps_per_package; i++){
				samps[i] = input.nextSample()[channel];
			}
			rem -= samps_per_package;
			
			encodePackage(samps, outbuff);
		}
		
		//Zero pad and encode final package, if applicable
		Arrays.fill(samps, 0);
		for(int i = 0; i < rem; i++){
			samps[i] = input.nextSample()[channel];
		}
		encodePackage(samps, outbuff);
		
		//Clear encoder instance vars
		this.codebook = null;
		last_block = null;
		order = p_order;
		
		return outbuff;
	}
	
}

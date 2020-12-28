package waffleoRai_PSXMDEC;

import java.awt.Point;

import waffleoRai_Image.Pixel;
import waffleoRai_Image.Pixel_RGBA;

public class MDECMacroblock {
	
	/* ----- Constants ----- */
	
	public static final int MATRIX_DIM = 8;
	public static final int RGB_DIM = 16;

	/* ----- Instance Variables ----- */
	
	private static Point[] zigzagOrder;
	
	private boolean bMonochrome;
	
	private int[][] Cr;
	private int[][] Cb;
	
	private int[][] Y1;
	private int[][] Y2;
	private int[][] Y3;
	private int[][] Y4;
	
	/* ----- Construction ----- */
	
	public MDECMacroblock(boolean monochrome)
	{
		Y1 = new int[MATRIX_DIM][MATRIX_DIM];
		if(monochrome) {
			bMonochrome = true;
			return;
		}
		bMonochrome = false;
		
		Y2 = new int[MATRIX_DIM][MATRIX_DIM];
		Y3 = new int[MATRIX_DIM][MATRIX_DIM];
		Y4 = new int[MATRIX_DIM][MATRIX_DIM];
		Cr = new int[MATRIX_DIM][MATRIX_DIM];
		Cb = new int[MATRIX_DIM][MATRIX_DIM];
	}
	
	private static void generateZigzagOrder()
	{
		int sz = MATRIX_DIM * MATRIX_DIM;
		zigzagOrder = new Point[sz];
		int x = 0;
		int y = 0;
		boolean dir = true; //Up
		
		//First 8 diags
		for (int i = 0; i < sz; i++)
		{
			zigzagOrder[i] = new Point(x,y);
			if (dir)
			{
				x++;
				y--;
				if (x >= MATRIX_DIM)
				{
					x = MATRIX_DIM - 1;
					y += 2;
					dir = !dir;
				}
				if (y < 0)
				{
					y = 0;
					dir = !dir;
				}
			}
			else
			{
				y++;
				x--;
				if (x < 0)
				{
					x = 0;
					dir = !dir;
				}
				if (y >= MATRIX_DIM)
				{
					y = MATRIX_DIM - 1;
					x += 2;
					dir = !dir;
				}
			}
			
		}
	}
	
	/* ----- Getters ----- */
	
	public boolean isMonochrome()
	{
		return this.bMonochrome;
	}
	
	public int getCrAt(int x, int y)
	{
		return Cr[y][x];
	}
	
	public int getCbAt(int x, int y)
	{
		return Cb[y][x];
	}
	
	public int getYAt(int x, int y)
	{
		if (x < MATRIX_DIM)
		{
			if (y < MATRIX_DIM) return Y1[y][x];
			else return Y3[y-MATRIX_DIM][x];
		}
		else
		{
			if (y < MATRIX_DIM) return Y2[y][x-MATRIX_DIM];
			else return Y4[y-MATRIX_DIM][x-MATRIX_DIM];
		}
	}
	
	/* ----- Setters ----- */
	
	public void setYAt(int x, int y, int val)
	{
		if (x < MATRIX_DIM)
		{
			if (y < MATRIX_DIM) Y1[y][x] = val;
			else Y3[y-MATRIX_DIM][x] = val;
		}
		else
		{
			if (y < MATRIX_DIM) Y2[y][x-MATRIX_DIM] = val;
			else Y4[y-MATRIX_DIM][x-MATRIX_DIM] = val;
		}
	}
	
	/* ----- Encode ----- */
	
	public void populate(Pixel[][] RGB, double kr, double kg, double kb)
	{
		//Wikipedia! https://en.wikipedia.org/wiki/YCbCr
		int[][] cr_temp = new int[RGB_DIM][RGB_DIM];
		int[][] cb_temp = new int[RGB_DIM][RGB_DIM];
		
		for (int r = 0; r < RGB_DIM; r++)
		{
			for (int l = 0; l < RGB_DIM; l++)
			{
				Pixel p = null;
				if (r < RGB.length && l < RGB[r].length) p = RGB[r][l];
				else p = new Pixel_RGBA(0,0,0,0);
				
				int r_raw = p.getRed();
				int g_raw = p.getGreen();
				int b_raw = p.getBlue();
				
				double r_float = (double)r_raw/255.0;
				double g_float = (double)g_raw/255.0;
				double b_float = (double)b_raw/255.0;
				
				double y_raw = (kr * r_float) + (kg * g_float) + (kb * b_float);
				double cb_raw = 0.5 * ((b_float - y_raw)/(1.0 - kb));
				double cr_raw = 0.5 * ((r_float - y_raw)/(1.0 - kr));
				
				//Scale back to 255 and round
				int y = (int)Math.round(y_raw * 255.0);
				int cb = (int)Math.round(cb_raw * 255.0);
				int cr = (int)Math.round(cr_raw * 255.0);
				
				//Store
				this.setYAt(l, r, y);
				cr_temp[r][l] = cr;
				cb_temp[r][l] = cb;
			}
		}
		
		//Downscale cb and cr and store
		Cr = downsampleMatrix(cr_temp);
		Cb = downsampleMatrix(cb_temp);
	}
	
	public static int[][] downsampleMatrix(int[][] in)
	{
		int[][] out = new int[MATRIX_DIM][MATRIX_DIM];
		int or = 0;
		int ol = 0;
		for (int r = 0; r < RGB_DIM; r+=2)
		{
			ol = 0;
			int[] row1 = null;
			int[] row2 = null;
			if (r >= in.length)
			{
				row1 = new int[RGB_DIM];
				row2 = new int[RGB_DIM];
			}
			else if (r + 1 >= in.length)
			{
				row1 = in[r];
				row2 = new int[RGB_DIM];
			}
			else
			{
				row1 = in[r];
				row2 = in[r+1];
			}
			
			for (int l = 0; l < RGB_DIM; l+=2)
			{
				int v1 = 0;
				int v2 = 0;
				int v3 = 0;
				int v4 = 0;
				
				if(l < row1.length) v1 = row1[l];
				if(l+1 < row1.length) v2 = row1[l+1];
				if(l < row2.length) v3 = row2[l];
				if(l+1 < row2.length) v4 = row2[l+1];
				
				int sum = v1 + v2 + v3 + v4;
				int avg = (int)Math.round((double)sum/4.0);
				
				out[or][ol] = avg;
				
				ol++;
			}
			or++;
		}
		
		return out;
	}
	
	public static double[][] discosTransform(int[][] data)
	{
		//Copy values into new array so can play with them
		int[][] tempmat = new int[MATRIX_DIM][MATRIX_DIM];
		for (int r = 0; r < MATRIX_DIM; r++)
		{
			int[] row = null;
			if (r < data.length) row = data[r];
			if (row == null) row = new int[MATRIX_DIM];
			for (int l = 0; l < MATRIX_DIM; l++)
			{
				int val = 0;
				if (l < row.length) val = row[l];
				tempmat[r][l] = val;
			}
		}
		
		//Shift to 0-centered (so signed)
		for (int r = 0; r < MATRIX_DIM; r++)
		{
			for (int l = 0; l < MATRIX_DIM; l++)
			{
				tempmat[r][l] -= 128;
			}
		}
		
		//Calculate and store coefficients
		double[] posCoeff = new double[MATRIX_DIM];
		for (int i = 0; i < MATRIX_DIM; i++)
		{
			int a = (2*i) + 1;
			double b = Math.PI * (double)a;
			double c = b/ 16.0;
			posCoeff[i] = c;
		}
		
		//Calculate from uv
		double[][] results = new double[MATRIX_DIM][MATRIX_DIM];
		for (int v = 0; v < MATRIX_DIM; v++)
		{
			for (int u = 0; u < MATRIX_DIM; u++)
			{
				double sum = 0;
				for (int x = 0; x < MATRIX_DIM; x++)
				{
					for (int y = 0; y < MATRIX_DIM; y++)
					{
						double val = (double)tempmat[y][x];
						double xco = posCoeff[x];
						double yco = posCoeff[y];
						double cosx = Math.cos(xco * u);
						double cosy = Math.cos(yco * v);
						sum += cosx * cosy * val;
					}
				}
				//Normalize
				double alpha_u = 1.0;
				double alpha_v = 1.0;
				if (u == 0) alpha_u = 1.0/(Math.sqrt(2.0));
				if (v == 0) alpha_v = 1.0/(Math.sqrt(2.0));
				double out = 0.25 * alpha_u * alpha_v * sum;
				results[v][u] = out;
			}
		}
		
		return results;
	}

	public static int[][] quantize(double[][] rawResults, QuantMatrix qTable)
	{
		int[][] out = new int[MATRIX_DIM][MATRIX_DIM];
		for (int v = 0; v < MATRIX_DIM; v++)
		{
			for (int u = 0; u < MATRIX_DIM; u++)
			{
				double val = rawResults[v][u];
				int q = qTable.get(u, v);
				out[v][u] = (int)Math.round(val/(double)q);
			}
		}
		
		return out;
	}
	
	public static int[] blockToZigzaggedStream(int[][] matrix)
	{
		if (zigzagOrder == null) generateZigzagOrder();
		int sz = MATRIX_DIM*MATRIX_DIM;
		int[] stream = new int[sz];
		for (int i = 0; i < sz; i++)
		{
			Point p = zigzagOrder[i];
			int val = matrix[p.y][p.x];
			stream[i] = val;
		}
		return stream;
	}
	
	/* ----- Decode ----- */
	
	public static int[][] zigzagStreamToBlock(int[] stream)
	{
		if (zigzagOrder == null) generateZigzagOrder();
		int sz = MATRIX_DIM*MATRIX_DIM;
		int[][] matrix = new int[MATRIX_DIM][MATRIX_DIM];
		for (int i = 0; i < sz; i++)
		{
			Point p = zigzagOrder[i];
			int val = stream[i];
			matrix[p.y][p.x] = val;
		}
		
		return matrix;
	}
	
	public Pixel[][] toRGB(double kr, double kg, double kb)
	{
		Pixel[][] out = new Pixel[RGB_DIM][RGB_DIM];
		//Upscale the chromatic matrices
		int[][] cr_temp = upsampleMatrix(Cr);
		int[][] cb_temp = upsampleMatrix(Cb);
		
		for (int r = 0; r < RGB_DIM; r++)
		{
			for (int l = 0; l < RGB_DIM; l++)
			{
				int y = this.getYAt(l, r);
				int cr = cr_temp[r][l];
				int cb = cb_temp[r][l];
				
				double y_float = (double)y / 255.0;
				double cr_float = (double)cr / 255.0;
				double cb_float = (double)cb / 255.0;
				
				double r_float = ((1.0 - kr) * 2.0 * cr_float) + y_float;
				double b_float = ((1.0 - kb) * 2.0 * cb_float) + y_float;
				double g_float = (y_float - (kr*r_float) - (kb*b_float))/ kg;
				
				//Scale back to 255
				int red = (int)Math.round(r_float * 255.0);
				int green = (int)Math.round(g_float * 255.0);
				int blue = (int)Math.round(b_float * 255.0);
				
				Pixel p = new Pixel_RGBA(red, blue, green, 255);
				out[r][l] = p;
			}
		}
		
		return out;
	}
	
	public static int[][] upsampleMatrix(int[][] in)
	{
		int[][] out = new int[RGB_DIM][RGB_DIM];
		int or = 0;
		int ol = 0;
		
		for (int r = 0; r < MATRIX_DIM; r++)
		{
			ol = 0;
			int[] row = null;
			if (r < in.length) row = in[r];
			else row = new int[MATRIX_DIM];
			for (int l = 0; l < MATRIX_DIM; l++)
			{
				int val = 0;
				if (l < row.length) val = row[l];
				
				out[or][ol] = val;
				out[or+1][ol] = val;
				out[or][ol+1] = val;
				out[or+1][ol+1] = val;
				
				ol+=2;
			}
			or += 2;
		}
		
		return out;
	}
	
	public static double[][] descaleQuant(int[][] data, QuantMatrix qTable)
	{
		double[][] out = new double[MATRIX_DIM][MATRIX_DIM];
		for(int r = 0; r < MATRIX_DIM; r++)
		{
			for(int l = 0; l < MATRIX_DIM; l++)
			{
				int rawval = data[r][l];
				out[r][l] = (double)rawval * (double)qTable.get(l, r);
			}
		}
		
		return out;
	}
	
	public static int[][] discosInvert(double[][] data)
	{
		//Calculate and store coefficients
		double[] mCoeff = new double[MATRIX_DIM];
		double[] aCoeff = new double[MATRIX_DIM];
		for (int i = 0; i < MATRIX_DIM; i++)
		{
			aCoeff[i] = ((double)i * Math.PI)/16.0;
			mCoeff[i] = ((double)i * Math.PI)/8.0;
		}
		
		//Run DCT
		double[][] rawoutput = new double[MATRIX_DIM][MATRIX_DIM];
		for(int y = 0; y < MATRIX_DIM; y++)
		{
			for(int x = 0; x < MATRIX_DIM; x++)
			{
				double sum = 0;
				for(int v = 0; v < MATRIX_DIM; v++)
				{
					for(int u = 0; u < MATRIX_DIM; u++)
					{
						double val = data[v][u];
						
						double alpha_u = 1.0;
						double alpha_v = 1.0;
						if (u == 0) alpha_u = 1.0/(Math.sqrt(2.0));
						if (v == 0) alpha_v = 1.0/(Math.sqrt(2.0));
						
						double temp = val * alpha_u * alpha_v;
						temp *= Math.cos((x * mCoeff[u]) + aCoeff[u]);
						temp *= Math.cos((y * mCoeff[v]) + aCoeff[v]);
						
						sum += temp;
					}
				}
				rawoutput[y][x] = sum * 0.25;
			}
		}
		
		//Round & scale up to 255
		int[][] out = new int[MATRIX_DIM][MATRIX_DIM];
		for (int r = 0; r < MATRIX_DIM; r++)
		{
			for (int l = 0; l < MATRIX_DIM; l++)
			{
				out[r][l] = (int)Math.round(rawoutput[r][l]) + 128;
			}
		}
		
		return out;
	}
	
	
	
}

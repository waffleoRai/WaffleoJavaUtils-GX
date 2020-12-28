package waffleoRai_PSXMDEC;

public class QuantMatrix {
	
	public static final int[] DEFO_QM = {2, 16, 19, 22, 26, 27, 29, 34,
										16, 16, 22, 24, 27, 29, 34, 37,
										19, 22, 26, 27, 29, 34, 34, 38,
										22, 22, 26, 27, 29, 34, 37, 40,
										22, 26, 27, 29, 32, 35, 40, 48,
										26, 27, 29, 32, 35, 40, 48, 58,
										26, 27, 29, 34, 38, 46, 56, 69,
										27, 29, 35, 38, 46, 56, 69, 83};
	
	private int[][] matrix;
	
	public QuantMatrix()
	{
		matrix = new int[MDECMacroblock.MATRIX_DIM][MDECMacroblock.MATRIX_DIM];
		loadDefault();
	}
	
	private void loadDefault(){
		for(int i = 0; i < 64; i++){
			int idx = PSXMDEC.ZIGZAG_ARR[i];
			set(idx, DEFO_QM[i]);
			//set(i, DEFO_QM[idx]);
		}
	}
	
	public int get(int x, int y)
	{
		return matrix[y][x];
	}
	
	public int get(int i)
	{
		int y = i/8;
		int x = i%8;
		return matrix[y][x];
	}
	
	public void set(int x, int y, int val)
	{
		matrix[y][x] = val;
	}

	public void set(int i, int val)
	{
		int y = i/8;
		int x = i%8;
		matrix[y][x] = val;
	}
	
}

package waffleoRai_Image.psx;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class CLUTCompare {
	
	public static class CLUTMatch{
		public double score;
		public int[] map;
	}
	
	public static class CLUTImgMatch{
		public double score;
		public Map<Integer, Integer> map;
		
		public CLUTImgMatch(){map = new HashMap<Integer, Integer>();}
	}
	
	public static double colorDistance(int argb1, int argb2){
		
		int r = (argb1 >>> 16) & 0xff;
		int g = (argb1 >>> 8) & 0xff;
		int b = argb1 & 0xff;
		float[] hsb1 = Color.RGBtoHSB(r, g, b, null);
		
		r = (argb2 >>> 16) & 0xff;
		g = (argb2 >>> 8) & 0xff;
		b = argb2 & 0xff;
		float[] hsb2 = Color.RGBtoHSB(r, g, b, null);
		
		double hdist = (double)hsb1[0] - (double)hsb2[0];
		double sdist = (double)hsb1[1] - (double)hsb2[1];
		double bdist = (double)hsb1[2] - (double)hsb2[2];
		
		hdist *= hdist;
		sdist *= sdist;
		bdist *= bdist;

		return Math.sqrt(hdist * sdist * bdist);
	}

	public static CLUTMatch checkCLUTMatch(int[] clutRef, int[] clutCmp, int[][] imgData){
		if(clutRef == null) return null;
		if(clutCmp == null) return null;
		if(imgData == null) return null;
		
		//Score all colors
		CLUTMatch match = new CLUTMatch();
		match.map = new int[clutCmp.length];
		double[] scoreMap = new double[clutCmp.length];
		for(int i = 0; i < clutCmp.length; i++){
			scoreMap[i] = Double.MAX_VALUE;
			for(int j = 0; j < clutRef.length; j++){
				double score = colorDistance(clutCmp[i], clutRef[j]);
				if(score < scoreMap[i]){
					scoreMap[i] = score;
					match.map[i] = j;
				}
			}
		}
		
		//Sum image distances
		match.score = 0.0;
		for(int i = 0; i < imgData.length; i++){
			for(int j = 0; j < imgData[i].length; j++){
				int pix = imgData[i][j];
				match.score += scoreMap[pix];
			}
		}
		
		return match;
	}
	
	public static CLUTImgMatch checkImgCLUTMatch(int[] clutRef, int[][] imgData){
		if(clutRef == null) return null;
		if(imgData == null) return null;
		
		CLUTImgMatch match = new CLUTImgMatch();
		Map<Integer, Double> scoreMap = new HashMap<Integer, Double>();
		for(int i = 0; i < imgData.length; i++){
			for(int j = 0; j < imgData[i].length; j++){
				int pix = imgData[i][j];
				if(scoreMap.containsKey(pix)){
					match.score += scoreMap.get(pix);
				}
				else{
					double bestScore = Double.MAX_VALUE;
					int bestIndex = -1;
					for(int c = 0; c < clutRef.length; c++){
						double score = colorDistance(pix, clutRef[c]);
						if(score < bestScore){
							bestScore = score;
							bestIndex = c;
						}
					}
					match.map.put(pix, bestIndex);
					scoreMap.put(pix, bestScore);
				}
			}
		}
		
		return match;
	}
	
	public static int[][] remapImageToCLUT(int[] clut, int[][] imgdata){
		CLUTImgMatch match = checkImgCLUTMatch(clut, imgdata);
		
		int I = imgdata.length;
		int J = imgdata[0].length;
		int[][] out = new int[I][J];
		for(int i = 0; i < imgdata.length; i++){
			for(int j = 0; j < imgdata[i].length; j++){
				if(match.map.containsKey(imgdata[i][j])){
					out[i][j] = match.map.get(imgdata[i][j]);
				}
			}
		}
		
		return out;
	}
	
	public static int[][] remapImageToCLUT(int[] trgCLUT, int[] srcCLUT, int[][] imgdata){
		CLUTMatch match = checkCLUTMatch(trgCLUT, srcCLUT, imgdata);
		
		int I = imgdata.length;
		int J = imgdata[0].length;
		int[][] out = new int[I][J];
		for(int i = 0; i < imgdata.length; i++){
			for(int j = 0; j < imgdata[i].length; j++){
				out[i][j] = match.map[imgdata[i][j]];
			}
		}
		
		return out;
	}
	
}

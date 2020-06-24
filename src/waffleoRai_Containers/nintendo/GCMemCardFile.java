package waffleoRai_Containers.nintendo;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import waffleoRai_Image.Animation;
import waffleoRai_Image.AnimationFrame;
import waffleoRai_Image.SimpleAnimation;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.MultiFileBuffer;

public class GCMemCardFile {

	/*----- Instance Variables -----*/
	
	private String filename;
	private String gamecode;
	private String makercode;
	
	private BufferedImage banner;
	private Animation icon;
	
	private String comment_1;
	private String comment_2;
	
	private String filePath;
	private int[] blocks;
	
	/*----- Construction -----*/
	
	public GCMemCardFile(String fname, String srcfile, int blockCount){
		filename = fname;
		filePath = srcfile;
		blocks = new int[blockCount];
		
		icon = new SimpleAnimation(8);
	}
	
	/*----- Getters -----*/
	
	public String getFileName(){return this.filename;}
	public String getGameCode(){return this.gamecode;}
	public String getMakerCode(){return this.makercode;}
	public String getGameMakerCode(){return this.gamecode + this.makercode;}
	public BufferedImage getBanner(){return banner;}
	public Animation getRawIcon(){return icon;}
	public String getComment1(){return comment_1;}
	public String getComment2(){return comment_2;}
	public int getNumberBlocks(){return blocks.length;}
	
	public Animation getCleanIcon(){
		//TODO
		//May also need to reverse them??
		if(icon == null) return null;
		//Remove empty frames
		List<AnimationFrame> frames = new ArrayList<AnimationFrame>(8);
		for(int i = 0; i < 8; i++){
			AnimationFrame f = icon.getFrame(i);
			if(f == null) continue;
			if(f.getImage() != null && f.getLengthInFrames() > 0) frames.add(f);
		}
		
		if(frames.isEmpty()) return null;
		
		int nfcount = frames.size();
		Animation a = new SimpleAnimation(nfcount);
		a.setAnimationMode(icon.getAnimationMode());
		int i = 0;
		for(AnimationFrame f : frames){
			AnimationFrame n = new AnimationFrame(f.getImage(), f.getLengthInFrames() >>> 2);
			a.setFrame(n, i++);
		}
		
		return a;
	}
	
	/*----- Setters -----*/
	
	public void setBlockMapping(int idx, int block){
		blocks[idx] = block;
	}
	
	public void setGamecode(String code){gamecode = code;}
	public void setMakercode(String code){makercode = code;}
	
	public void setBanner(BufferedImage img){banner = img;}
	
	public void setPingpongAnimation(boolean b){
		if(icon != null){
			if(b) icon.setAnimationMode(Animation.ANIM_MODE_PINGPONG);
			else icon.setAnimationMode(Animation.ANIM_MODE_NORMAL);
		}
	}
	
	public void setIconFrame(int idx, BufferedImage img, int fcount){
		icon.setFrame(new AnimationFrame(img, fcount), idx);
	}
	
	public void setComment1(String cmt){comment_1 = cmt;}
	public void setComment2(String cmt){comment_2 = cmt;}
	
	/*----- File Handling -----*/
	
	public FileBuffer loadFile() throws IOException{

		FileBuffer file = new MultiFileBuffer(blocks.length);
		
		for(int i = 0; i < blocks.length; i++){
			long offset = blocks[i] << 13;
			file.addToFile(FileBuffer.createBuffer(filePath, offset, offset+GCMemCard.BLOCK_SIZE, true));
		}
		
		return file;
	}
	
	/*----- Debug -----*/
	
	public void debugPrint(){
		System.err.println("----- " + filename + " -----");
		System.err.println("Game: " + gamecode + makercode);
		System.err.println(comment_1);
		System.err.println(comment_2);
		System.err.println("Total Blocks: " + blocks.length);
		for(int i = 0; i < blocks.length; i++){
			if(i % 16 == 15) System.err.println();
			System.err.print(String.format("%04d ", blocks[i]));
		}
	}
	
}

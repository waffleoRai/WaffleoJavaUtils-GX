package waffleoRai_Containers.nintendo;

import java.awt.image.BufferedImage;

import waffleoRai_Image.Animation;
import waffleoRai_Image.AnimationFrame;
import waffleoRai_Image.SimpleAnimation;
import waffleoRai_Image.nintendo.DolGraphics;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class WiiSaveBannerFile {

	/*----- Constants -----*/
	
	public static final String MAGIC = "WIBN";
	public static final String STRENCODE = "UnicodeBigUnmarked";
	
	public static final int BANNER_WIDTH = 192;
	public static final int BANNER_HEIGHT = 64;
	public static final int ICON_DIM = 48;
	
	public static final int ANIM_SPEED_CONST = 60;
	
	/*----- Instance Variables -----*/
	
	private boolean pingpong;
	//private int anim_speed;
	private int[] frame_lens;
	
	private String title;
	private String subtitle;
	
	private BufferedImage banner;
	private BufferedImage[] icon;
	
	/*----- Construction/Parsing -----*/
	
	private WiiSaveBannerFile(){}
	
	public static WiiSaveBannerFile readBannerBin(FileBuffer data) throws UnsupportedFileTypeException{
		if(data == null) return null;
		return readBannerBin(data, 0, data.getFileSize());
	}
	
	public static WiiSaveBannerFile readBannerBin(FileBuffer data, long stpos, long size) throws UnsupportedFileTypeException{
		//Check magic number
		long moff = data.findString(stpos, stpos+0x10, MAGIC);
		if(moff < 0) throw new FileBuffer.UnsupportedFileTypeException("Wii Save Banner Magic WIBN not found!");
		
		WiiSaveBannerFile mybnr = new WiiSaveBannerFile();
		data.setCurrentPosition(moff+4);
		int flags = data.nextInt();
		if((flags & 0x10) != 0) mybnr.pingpong = true;
		
		int speedraw = Short.toUnsignedInt(data.nextShort());
		data.skipBytes(22);
		
		long cpos = data.getCurrentPosition();
		mybnr.title = data.readEncoded_string(STRENCODE, cpos, cpos+64); cpos += 64;
		mybnr.subtitle = data.readEncoded_string(STRENCODE, cpos, cpos+64); cpos += 64;
		
		//Banner
		mybnr.banner = new BufferedImage(BANNER_WIDTH, BANNER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		//Assuming 4x4 tiles...
		final int TLEDIM = 4;
		int rows = BANNER_HEIGHT >>> 2;
		int cols = BANNER_WIDTH >>> 2;
		int x = 0; int y = 0;
		for(int r = 0; r < rows; r++){
			for(int l = 0; l < cols; l++){
				for(int rr = 0; rr < TLEDIM; rr++){
					for(int ll = 0; ll < TLEDIM; ll++){
						int pix = DolGraphics.RGB5A3_to_ARGB(data.shortFromFile(cpos)); cpos+=2;
						mybnr.banner.setRGB(x+ll, y+rr, pix);
					}
				}
				x+=TLEDIM;
			}
			x = 0; y+=TLEDIM;
		}
		
		//Icon
		//Determine # frames
		long ico_size = size - 0x60a0;
		int frames = (int)(ico_size/4608);
		mybnr.icon = new BufferedImage[frames];
		for(int f = 0; f < frames; f++){
			BufferedImage icoframe = new BufferedImage(ICON_DIM, ICON_DIM, BufferedImage.TYPE_INT_ARGB);
			mybnr.icon[f] = icoframe;
			rows = ICON_DIM >>> 2;
			cols = ICON_DIM >>> 2;
			x = 0; y = 0;
			for(int r = 0; r < rows; r++){
				for(int l = 0; l < cols; l++){
					for(int rr = 0; rr < TLEDIM; rr++){
						for(int ll = 0; ll < TLEDIM; ll++){
							int pix = DolGraphics.RGB5A3_to_ARGB(data.shortFromFile(cpos)); cpos+=2;
							icoframe.setRGB(x+ll, y+rr, pix);
						}
					}
					x+=TLEDIM;
				}
				x = 0; y+=TLEDIM;
			}
		}
		
		//Set animation speed...
		mybnr.frame_lens = new int[8];
		int shift = 14;
		for(int i = 0; i < frames; i++){
			mybnr.frame_lens[i] = (speedraw >>> shift) & 0x3;
			shift-=2;
		}
		//mybnr.anim_speed = speedraw;
		
		return mybnr;
	}
	
	/*----- Getters -----*/
	
	public String getTitle(){return title;}
	public String getSubtitle(){return subtitle;}
	public BufferedImage getBanner(){return banner;}
	
	public int getAnimationFrameCount(){
		if(icon == null) return 0;
		
		//See if first two frame lens are 0, in which case 1...
		if(frame_lens[0] == 0 && frame_lens[1] == 0) return 1;
		
		int count = 0;
		for(int i = 0; i < icon.length; i++){
			if(icon[i] != null) count++;
		}
		
		return count;
	}
	
	public Animation getIcon(){
		int frames = getAnimationFrameCount();
		SimpleAnimation anim = new SimpleAnimation(frames);
		
		int f = 0;
		for(int i = 0; i < frames; i++){
			if(icon[i] != null){
				AnimationFrame frame = new AnimationFrame(icon[i], frame_lens[i]);
				anim.setFrame(frame, f++);	
			}
		}
		
		if(pingpong) anim.setAnimationMode(Animation.ANIM_MODE_PINGPONG);
		
		return anim;
	}
	
	/*----- Setters -----*/
	
}

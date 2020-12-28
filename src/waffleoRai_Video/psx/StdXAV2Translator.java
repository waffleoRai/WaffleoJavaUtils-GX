package waffleoRai_Video.psx;

public class StdXAV2Translator extends StdXATranslator{
	
	public StdXAV2Translator(){
		super();
	}
	
	protected int[] readNextDC() throws EndOfFrameException {
		//Get the next 10 bits...
		try {
			int ten = nextBits(10, true);
			if(ten == StdXATranslator.EOF_V2_HI_TEN){
				//Peek at the last bit to see if EOF
				if(!peekBit()){
					nextBit();
					throw new EndOfFrameException();
				}
			}
			
			int[] dc = new int[2];
			dc[0] = ten;
			dc[1] = 10;
			return dc;
		} 
		catch (NoBitsAvailableException e) {return null;}
	}

}

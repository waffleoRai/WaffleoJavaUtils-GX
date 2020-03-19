package waffleoRai_Containers.nintendo.sar.rev;

import java.io.BufferedWriter;
import java.io.IOException;

public class PlayerInfoNode extends RevSoundNode{

	private int playableSoundCount;
	
	public int getPlayableSoundCount(){return this.playableSoundCount;}
	public void setPlayableSoundCount(int i){this.playableSoundCount = i;}
	
	protected void writeMyFields(BufferedWriter bw) throws IOException
	{
		//#Name,Type,PlayerID,Volume,PlayerPriority,RemoteFilter,Flags,PanMode,PanCurve,ActorPlayerID,Param1,Param2
		bw.write("PLA,0,0,0,0,0,0,0,0,0,0");
	}
	
}

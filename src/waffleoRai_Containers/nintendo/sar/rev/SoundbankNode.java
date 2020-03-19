package waffleoRai_Containers.nintendo.sar.rev;

import java.io.BufferedWriter;
import java.io.IOException;

public class SoundbankNode extends RevSoundNode{
	
	private int bankIndex;
	
	public int getBankIndex(){return bankIndex;}
	public void setBankIndex(int i){bankIndex = i;}
	
	protected void writeMyFields(BufferedWriter bw) throws IOException
	{
		//#Name,Type,PlayerID,Volume,PlayerPriority,RemoteFilter,Flags,PanMode,PanCurve,ActorPlayerID,Param1,Param2
		bw.write("BNK,0,0,0,0,0,0,0,0,0,0");
	}
	
}

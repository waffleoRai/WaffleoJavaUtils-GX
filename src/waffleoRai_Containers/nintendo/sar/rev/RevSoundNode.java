package waffleoRai_Containers.nintendo.sar.rev;

import java.io.BufferedWriter;
import java.io.IOException;

import waffleoRai_Containers.nintendo.sar.NinsarDataFile;
import waffleoRai_Containers.nintendo.sar.NinsarSound;

public abstract class RevSoundNode implements NinsarSound{
	
	protected RevColNode collection;
	protected String name;
	
	public String getName(){return name;}
	public void setName(String n){name = n;}
	
	public RevColNode getCollectionNode(){return this.collection;}
	public void linkCollection(RevColNode c){this.collection = c;}
	
	public NinsarDataFile getDataFile() 
	{
		return collection;
	}
	
	public void writeTableEntry(BufferedWriter bw) throws IOException
	{
		//#Name,Type,PlayerID,Volume,PlayerPriority,RemoteFilter,Flags,PanMode,PanCurve,ActorPlayerID,Param1,Param2
		bw.write(getName() + ",");
		writeMyFields(bw);
	}
	
	protected abstract void writeMyFields(BufferedWriter bw) throws IOException;

}

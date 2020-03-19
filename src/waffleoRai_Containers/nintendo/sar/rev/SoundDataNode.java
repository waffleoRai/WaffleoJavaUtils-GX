package waffleoRai_Containers.nintendo.sar.rev;

import java.io.BufferedWriter;
import java.io.IOException;

public class SoundDataNode extends RevSoundNode{
	
	private int playerID;
	private int volume;
	private int player_pri;
	private int remoteFilter;
	private int actorPlayerID;
	
	private SoundType soundType;
	private PanMode panMode;
	private PanCurve panCurve;
	
	private int user1;
	private int user2;
	
	private int flags;
	
	public int getPlayerID(){return playerID;}
	public void setPlayerID(int i){playerID = i;}
	public int getVolume(){return volume;}
	public void setVolume(int i){volume = i;}
	public int getPlayerPriority(){return player_pri;}
	public void setPlayerPriority(int i){player_pri = i;}
	public int getRemoteFilter(){return remoteFilter;}
	public void setRemoteFilter(int i){remoteFilter = i;}
	public int getActorPlayerID(){return actorPlayerID;}
	public void setActorPlayerID(int i){actorPlayerID = i;}
	
	public SoundType getSoundType(){return soundType;}
	public void setSoundType(SoundType t){soundType = t;}
	public PanMode getPanMode(){return panMode;}
	public void setPanMode(PanMode t){panMode = t;}
	public PanCurve getPanCurve(){return panCurve;}
	public void setPanCurve(PanCurve t){panCurve = t;}
	
	public int getUserParameter1(){return user1;}
	public void setUserParameter1(int i){user1 = i;}
	public int getUserParameter2(){return user2;}
	public void setUserParameter2(int i){user2 = i;}
	public int getFlags(){return flags;}
	public void setFlags(int f){flags = f;}
	
	protected void writeMyFields(BufferedWriter bw) throws IOException
	{
		//#Name,Type,PlayerID,Volume,PlayerPriority,RemoteFilter,Flags,PanMode,PanCurve,ActorPlayerID,Param1,Param2
		bw.write(soundType + ",");
		bw.write(playerID + ",");
		bw.write(volume + ",");
		bw.write(player_pri + ",");
		bw.write(remoteFilter + ",");
		bw.write("0x" + Integer.toHexString(flags) + ",");
		bw.write(panMode + ",");
		bw.write(panCurve + ",");
		bw.write(actorPlayerID + ",");
		writeMyParams(bw);
	}
	
	protected void writeMyParams(BufferedWriter bw) throws IOException
	{
		//Super class writes nothing
		bw.write("0,0");
	}
	
}

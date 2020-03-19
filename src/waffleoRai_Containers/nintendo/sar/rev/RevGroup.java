package waffleoRai_Containers.nintendo.sar.rev;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import waffleoRai_Containers.nintendo.sar.NinsarDataFile;
import waffleoRai_Containers.nintendo.sar.NinsarGroup;
import waffleoRai_Utils.FileTag;

public class RevGroup implements NinsarGroup{
	
	private String name;
	
	private FileTag dataLocation;
	private FileTag audioLocation;
	
	private List<RevGroupMember> members;
	private ConcurrentMap<Integer, RevGroupMember> accessionMap;
	
	public RevGroup(int memberCount)
	{
		members = new ArrayList<RevGroupMember>(memberCount + 1);
		accessionMap = new ConcurrentHashMap<Integer, RevGroupMember>();
	}
	
	public String getName(){return name;}
	public void setName(String s){name = s;}
	
	public FileTag getFileDataLocation()
	{
		return dataLocation;
	}
	
	public FileTag getAudioDataLocation()
	{
		return audioLocation;
	}
	
	public void setDataLocation(String path, long dataOff, long dataSz, long audioOff, long audioSz)
	{
		dataLocation = new FileTag(path, dataOff, dataSz);
		audioLocation = new FileTag(path, audioOff, audioSz);
	}
	
	public NinsarDataFile getFile(int index)
	{
		RevGroupMember member = members.get(index);
		if(member == null) return null;
		return member.getCollectionLink();
	}
	
	public List<NinsarDataFile> getAllFiles()
	{
		List<NinsarDataFile> list = new LinkedList<NinsarDataFile>();
		for(RevGroupMember m : members)
		{
			RevColNode col = m.getCollectionLink();
			if(col != null) list.add(col);
		}
		return list;
	}
	
	public RevGroupMember getMember(int index)
	{
		return members.get(index);
	}
	
	public List<RevGroupMember> getAllMembers()
	{
		List<RevGroupMember> list = new ArrayList<RevGroupMember>(members.size() + 1);
		list.addAll(members);
		return list;
	}
	
	public int countMembers()
	{
		return members.size();
	}
	
	public FileTag getFileDataLocation(int collectionAccession)
	{
		RevGroupMember mem = accessionMap.get(collectionAccession);
		if(mem == null) return null;
		return mem.getFileLocation();
	}
	
	public FileTag getAudioDataLocation(int collectionAccession)
	{
		RevGroupMember mem = accessionMap.get(collectionAccession);
		if(mem == null) return null;
		return mem.getAudioLocation();
	}
	
	protected boolean linkCollection(int index, RevColNode col)
	{
		if(col == null) return false;
		
		RevGroupMember mem = null;
		try{mem = members.get(index);}
		catch(Exception e){return false;}
		if(mem == null) return false;
		
		mem.setCollectionLink(col);
		accessionMap.put(col.getAccessionID(), mem);
		
		return true;
	}
	
	protected void addUnlinkedMemberNode(FileTag dataLoc, FileTag audLoc)
	{
		RevGroupMember mem = new RevGroupMember();
		mem.setAudioLocation(audLoc);
		mem.setFileLocation(dataLoc);
		members.add(mem);
	}
	
	public void addNewMember(RevColNode col)
	{
		RevGroupMember mem = new RevGroupMember(col);
		accessionMap.put(col.getAccessionID(), mem);
		members.add(mem);
	}

	public int countMembersWithAudio()
	{
		int tot = 0;
		for(RevGroupMember mem : members)
		{
			if (mem.hasAudio()) tot++;
		}
		return tot;
	}
	
}

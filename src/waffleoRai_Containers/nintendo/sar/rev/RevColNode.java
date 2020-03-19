package waffleoRai_Containers.nintendo.sar.rev;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import waffleoRai_Containers.nintendo.sar.NinsarDataFile;
import waffleoRai_Containers.nintendo.sar.NinsarSound;
import waffleoRai_Containers.nintendo.sar.RevSoundArchive;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileTag;

public class RevColNode implements NinsarDataFile{
	
	//private int collectionIndex;
	private String name;
	private int groupAccession;
	private long fileSize;
	private long audioSize;
	
	private List<RevSoundNode> members;
	private List<RevGroup> groupLinks;
	
	private boolean external; //In final packaged file
	private FileTag dataLocation; //If null, try to read from a group
	private FileTag audioLocation; //If null, try to read from a group
	
	public RevColNode(String name, int initSounds, int initGroups)
	{
		this.name = name;
		groupAccession = name.hashCode();
		
		fileSize = 0;
		members = new ArrayList<RevSoundNode>(initSounds+1);
		groupLinks = new ArrayList<RevGroup>(initSounds+1);
	}
	
	public RevColNode(String name)
	{
		this.name = name;
		groupAccession = name.hashCode();
		
		fileSize = 0;
		members = new ArrayList<RevSoundNode>();
		groupLinks = new ArrayList<RevGroup>();
	}
	
	public int getAccessionID()
	{
		return groupAccession;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String n)
	{
		name = n;
	}
	
	public long getFileSize()
	{
		return fileSize;
	}
	
	public void setFileSize(long sz)
	{
		fileSize = sz;
	}
	
	public long getAudioSize()
	{
		return audioSize;
	}
	
	public void setAudioSize(long sz)
	{
		audioSize = sz;
	}
	
	public boolean isExternal()
	{
		return external;
	}

	public void setExternal(boolean b)
	{
		external = b;
	}
	
	public void addSoundNode(RevSoundNode n)
	{
		members.add(n);
	}
	
	public NinsarSound getSound(int index)
	{
		return members.get(index);
	}
	
	public Collection<NinsarSound> getAllSounds()
	{
		List<NinsarSound> list = new ArrayList<NinsarSound>(members.size() + 1);
		list.addAll(members);
		return list;
	}
	
	public int countSounds()
	{
		return members.size();
	}
	
	public Collection<RevGroup> getAllAssociatedGroups()
	{
		List<RevGroup> list = new ArrayList<RevGroup>(groupLinks.size() + 1);
		list.addAll(groupLinks);
		return list;
	}
	
	public boolean writeToDisk(String directory)
	{
		FileTag data_src = getFileLocation();
		FileTag audio_src = getAudioLocation();
		
		String path_stem = directory + File.separator + name;
		
		try
		{
		//Do data
			if(data_src != null)
			{
				FileBuffer data = FileBuffer.createBuffer(data_src.getPath(), data_src.getOffset(), 
						data_src.getOffset() + data_src.getSize());
				String magic = data.getASCII_string(0, 4);
				String ext = RevSoundArchive.getFileExtension(magic);
				String data_path = path_stem + "." + ext;
				data.writeFile(data_path);
			}
		
		//Do audio
			if(audio_src != null && audio_src.getSize() > 0)
			{
				FileBuffer data = FileBuffer.createBuffer(audio_src.getPath(), audio_src.getOffset(), 
						audio_src.getOffset() + audio_src.getSize());
				String magic = data.getASCII_string(0, 4);
				String ext = RevSoundArchive.getFileExtension(magic);
				String audio_path = path_stem + "." + ext;
				data.writeFile(audio_path);
			}
		
		//Do table
			String tblPath = path_stem + ".csv";
			BufferedWriter bw = new BufferedWriter(new FileWriter(tblPath));
			writeCSVTable(bw);
			bw.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private void scanGroupsForFileLocation()
	{
		RevGroup bestGroup = null;
		int lowestcount = Integer.MAX_VALUE;
		for(RevGroup g : groupLinks)
		{
			int acount = g.countMembersWithAudio();
			if(acount < lowestcount)
			{
				lowestcount = acount;
				bestGroup = g;
			}
		}
		
		if(bestGroup != null)
		{
			dataLocation = bestGroup.getFileDataLocation(groupAccession);
			audioLocation = bestGroup.getAudioDataLocation(groupAccession);
		}
	}
	
	public void setFileLocation(String path, long stPos, long len)
	{
		dataLocation = new FileTag(path, stPos, len);
	}
	
	public FileTag getFileLocation()
	{
		if(dataLocation != null) return dataLocation;
		//If there is no data location, check the groups...
		scanGroupsForFileLocation();
		return dataLocation;
	}
	
	public FileTag getAudioLocation()
	{
		if(audioLocation == null) scanGroupsForFileLocation();
		return audioLocation;
	}
	
	public void writeCSVTable(BufferedWriter stream) throws IOException
	{
		stream.write("#Param1: SeqLabelEntry(RSEQ);SoundDataNode(RWSD);StartPosition(RSTM) \n");
		stream.write("#Param2: BankIndex(RSEQ);[NONE](RWSD);[NONE](RSTM) \n");
		stream.write("#Name,Type,PlayerID,Volume,PlayerPriority,RemoteFilter,Flags,PanMode,PanCurve,ActorPlayerID,Param1,Param2\n");
		for(RevSoundNode n : members)
		{
			n.writeTableEntry(stream);
			stream.write("\n");
		}
	}
	
	public boolean isEmpty()
	{
		return this.members.isEmpty();
	}

}

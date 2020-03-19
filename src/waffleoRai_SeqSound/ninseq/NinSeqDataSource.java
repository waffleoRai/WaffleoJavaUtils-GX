package waffleoRai_SeqSound.ninseq;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import waffleoRai_Utils.FileBuffer;

public class NinSeqDataSource {
	
	/* ----- Instance Variables ----- */
	
	private int player_mode;
	
	private long base_addr;
	private FileBuffer data_source;
	
	private ConcurrentMap<Long, NSEvent> events;
	
	/* ----- Construction ----- */
	
	public NinSeqDataSource(long baseAddress, FileBuffer data, int playerMode)
	{
		data_source = data;
		base_addr = baseAddress;
		player_mode = playerMode;
	}
	
	/* ----- Getters ----- */
	
	private void parseEvents()
	{
		events = new ConcurrentHashMap<Long, NSEvent>();
		
		//Right now, we look for an end that has two TRACK_END cmds in a row
		//	followed by at least one 0x00 byte
		boolean et1 = false;
		boolean et2 = false;
		
		long sz = data_source.getFileSize();
		long cpos = base_addr;
		while(cpos < sz)
		{
			long spos = cpos;
			if(et1 && et2)
			{
				byte b = data_source.getByte(cpos);
				if(b == 0x00) break;
			}
			NSEvent ev = NinSeqParser.parseEvent(data_source, cpos, player_mode, false);
			
			if(ev == null)
			{
				ev = new UnknownCommandPlaceholder(data_source.getByte(spos));
			}
			
			events.put(cpos, ev);
			ev.setAddress(cpos);
			cpos += ev.getSerialSize();
			if(ev.getCommand() == NSCommand.TRACK_END)
			{
				if(!et1) et1 = true;
				else et2 = true;
			}
			else{et1 = false; et2 = false;}
			
			if(ev instanceof UnknownCommandPlaceholder) break;
		}
	}
	
	public NSEvent getEventAt(long addr)
	{
		if(events == null) parseEvents();
		NSEvent e = events.get(addr);
		if(e == null) e = NinSeqParser.parseEvent(data_source, addr + base_addr, player_mode, false);
		return e;
	}

	public long getMaxAddress()
	{
		return data_source.getFileSize() - base_addr;
	}
	
	public Map<Long, NSEvent> getParsedEventMap()
	{
		if(events == null) parseEvents();
		Map<Long, NSEvent> emap = new HashMap<Long, NSEvent>();
		List<Long> keylist = new ArrayList<Long>(events.size()+1);
		keylist.addAll(events.keySet());
		for(Long k : keylist)
		{
			emap.put(k, events.get(k));
		}
		return emap;
	}
	
	public boolean isBigEndian()
	{
		return data_source.isBigEndian();
	}
	
	public int getPlayerMode()
	{
		return player_mode;
	}
	
	public List<Long> getEventAddresses()
	{
		if(events == null) parseEvents();
		List<Long> list = new ArrayList<Long>(events.size() + 1);
		list.addAll(events.keySet());
		Collections.sort(list);
		return list;
	}
	
	/* ----- Queries ----- */
	
	public boolean hasPrefixCommands()
	{
		if(events == null) parseEvents();
		List<Long> addrlist = new ArrayList<Long>(events.size() + 1);
		addrlist.addAll(events.keySet());
		Collections.sort(addrlist);
		
		for(Long a : addrlist)
		{
			NSEvent e = events.get(a);
			if(e != null)
			{
				if (e instanceof PrefixEvent) return true;
			}
		}
		return false;
	}
	
	public boolean hasPrefixCommandsBetween(long stPos, long edPos)
	{
		if(events == null) parseEvents();
		List<Long> addrlist = new ArrayList<Long>(events.size() + 1);
		addrlist.addAll(events.keySet());
		Collections.sort(addrlist);
		
		for(Long a : addrlist)
		{
			if(a < stPos) continue;
			if(a > edPos) continue;
			NSEvent e = events.get(a);
			if(e != null)
			{
				if (e instanceof PrefixEvent) return true;
			}
		}
		return false;
	}
	
	/* ----- Print ----- */
	
	public void printInfo(BufferedWriter outstream) throws IOException
	{
		//Prints the events
		outstream.write("--- SEQUENCE INFORMATION ---\n");
		outstream.write("Base Address: 0x" + Long.toHexString(base_addr) + "\n\n");
		
		if(events == null) parseEvents();
		List<Long> addrlist = new ArrayList<Long>(events.size() + 1);
		addrlist.addAll(events.keySet());
		Collections.sort(addrlist);
		
		for(Long a : addrlist)
		{
			outstream.write("0x" + Long.toHexString(a) + "\t");
			NSEvent e = events.get(a);
			if(e != null) outstream.write(e.toString() + "\n");
			else outstream.write("[NULL]\n");
		}
	}
	
	/* ----- Other ----- */
	
	public void freeSourceData()
	{
		if(events == null) parseEvents();
		data_source = null;
	}
	
}

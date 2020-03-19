package waffleoRai_soundbank.nintendo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Sound.nintendo.NinWave;
import waffleoRai_Sound.nintendo.WaveArchive;
import waffleoRai_SoundSynth.SynthBank;
import waffleoRai_soundbank.SimpleBank;
import waffleoRai_soundbank.SimpleInstrument;
import waffleoRai_soundbank.SimplePreset;
import waffleoRai_soundbank.SingleBank;
import waffleoRai_soundbank.SimpleInstrument.InstRegion;
import waffleoRai_soundbank.SimplePreset.PresetRegion;

public abstract class NinBank {
	
	/*--- Instance Variables ---*/
	
	protected ArrayList<NinBankNode> topNodes;
	protected Map<Long, NinBankNode> nodeMap;
	protected Set<NinBankNode> linkNodes;
	
	/*--- Construction/Parsing ---*/
	
	protected void resolveLinks()
	{
		for(NinBankNode ln : linkNodes)
		{
			long addr = ln.getLinkAddress();
			NinBankNode node = nodeMap.get(addr);
			if(node != null) ln.setLinkedNode(node);
		}
	}
	
	/*--- Getters ---*/
	
	public int countSurfacePatches()
	{
		return topNodes.size();
	}
	
	public NinBankNode getSurfaceNodeAt(int index)
	{
		return topNodes.get(index);
	}
	
	public Collection<NinBankNode> getLinksTo(long targetAddr)
	{
		Set<NinBankNode> linkset = new HashSet<NinBankNode>();
		for(NinBankNode n : linkNodes)
		{
			if(n.getLinkAddress() == targetAddr) linkset.add(n);
		}
		return linkset;
	}
	
	/*--- Conversion ---*/
	
	private void patchToPreset(NinBankNode node, SingleBank mybank, int pindex, Map<Long, SimpleInstrument> simap, Map<Integer, String> sndMap)
	{
		if(node == null || mybank == null || simap == null || sndMap == null) return;
		//String presetName = "PRESET_" + String.format("%03d", pindex);
		String presetName = "PRESET_" + Long.toHexString(node.getLocalAddress());
		
		//See if already in map (if so, just use that)
		SimpleInstrument inst = simap.get(node.getLocalAddress());
		if(inst != null)
		{
			int pidx = mybank.newPreset(pindex, presetName, 1);
			SimplePreset p = mybank.getPreset(pidx);
			p.newRegion(inst);
			return;
		}
		
		//See if any lv 1 children in map (if so, lv 1 children <- inst)
		if(node.getChildCount() > 0)
		{
			List<NinBankNode> children = node.getChildren();
			boolean has = false;
			for(NinBankNode child : children)
			{
				if(simap.containsKey(child.getLocalAddress())){has = true; break;}
			}
			if(has)
			{
				int pidx = mybank.newPreset(pindex, presetName, node.getChildCount());
				SimplePreset p = mybank.getPreset(pidx);
				for(NinBankNode child : children)
				{
					inst = simap.get(node.getLocalAddress());
					if(inst == null) 
					{
						inst = child.toFlatInstrument(sndMap);
						simap.put(child.getLocalAddress(), inst);
					}
					int ridx = p.newRegion(inst);
					PresetRegion preg = p.getRegion(ridx);
					preg.setMinKey(child.getMinNote());
					preg.setMaxKey(child.getMaxNote());
				}
				return;
			}
		}
		
		//If nothing already mapped, generate new inst(s)
		/*
		 * Inst per child if:
		 * 		-> Patch depth is > 1
		 * 		-> Patch depth is 1, but at least one lv 1 tone is link/linked to
		 * 			and link partner is at depth 0 or 1.
		 * 
		 * Otherwise, flat inst
		 */
		
		int pdepth = node.getDepth();
		boolean link1 = false;
		
		if(pdepth == 1)
		{
			//Check for links
			if(node.containsLinks())
			{
				//Is top level a link?
				if(node.isLink())
				{
					//if the link is level 1 or deeper, flatten inst
					//otherwise inst per child
					if(node.getLinkedNode() != null && node.getLinkedNode().getDepthLevel() < 1) link1 = true;
				}
				else link1 = true;
			}
			if(node.hasLinkingNodesRecursive())
			{
				//Is top level linked to?
				if(node.hasLinkingNodes())
				{
					//Same as with link other way around.
					if(node.getMinLinkingNodeDepth() < 1) link1 = true;
				}
				else link1 = true;
			}
		}
		
		if(pdepth > 1 || link1)
		{
			List<NinBankNode> children = node.getChildren();
			int pidx = mybank.newPreset(pindex, presetName, node.getChildCount());
			SimplePreset p = mybank.getPreset(pidx);
			for(NinBankNode child : children)
			{
				inst = simap.get(node.getLocalAddress());
				if(inst == null) 
				{
					inst = child.toFlatInstrument(sndMap);
					//Don't map if it's a level 0 and has a depth > 1
					if(child.isLink())
					{
						NinBankNode lnode = child.getLinkedNode();
						if(lnode != null)
						{
							if(!(lnode.getDepthLevel() == 0 && lnode.getDepth() > 1)) simap.put(child.getLocalAddress(), inst);
							else
							{
								//Rename the inst
								inst.setName("LINST_" + Long.toHexString(node.getLocalAddress()) + "-" + Long.toHexString(child.getLocalAddress()));
							}
						}
						else simap.put(child.getLocalAddress(), inst);
					}
					else simap.put(child.getLocalAddress(), inst);
				}
				int ridx = p.newRegion(inst);
				PresetRegion preg = p.getRegion(ridx);
				preg.setMinKey(child.getMinNote());
				preg.setMaxKey(child.getMaxNote());
			}
			return;
		}
		
		//Flat
		inst = node.toFlatInstrument(sndMap);
		simap.put(node.getLocalAddress(), inst);
		int pidx = mybank.newPreset(pindex, presetName, 1);
		SimplePreset p = mybank.getPreset(pidx);
		p.newRegion(inst);
	}
	
	public SimpleBank toSoundbank(WaveArchive soundarc, int bankIndex, String name)
	{
		int bCount = (this.countSurfacePatches()/ 0x7F) + 1;
		SimpleBank bank = new SimpleBank(name, "VersionUnknown", "Nintendo", bCount);
		int bidx = bank.newBank(0, name);
				
		//Do the sounds
		Set<Integer> siset = new HashSet<Integer>();
		Map<Integer, String> sndMap = new HashMap<Integer, String>();
		//for(RTone t : tones) siset.add(t.getWaveNumber());
		for(NinBankNode n : topNodes) siset.addAll(n.getAllLocalWaveNumbers());
		for(Integer i : siset)
		{
			if(i < 0) continue;
			//WiiBRWAV rwav = soundarc.getSoundAt(i);
			NinWave wav = soundarc.getWave(i);
			if(wav == null) continue;
			String soundKey = "RWAV_" + String.format("%04d", i);
			bank.addSample(soundKey, wav);
			sndMap.put(i, soundKey);
		}
		
		SingleBank mybank = bank.getBank(bidx);
		
		//Convert all instruments (by addr)
		Map<Long, SimpleInstrument> simap = new HashMap<Long, SimpleInstrument>();
		int i = 0; //Preset
		int j = 1; //Bank
		for(NinBankNode pnode : topNodes)
		{
			if(!pnode.isEmpty())
			{
				//Convert to preset
				//String presetName = "PRESET_" + String.format("%03d", i);
				String presetName = "PRESET_" + Long.toHexString(pnode.getLocalAddress());
				
				SimplePreset preset = null;
				SimpleInstrument inst = null;
				
				int pidx = -1;
				int iidx = -1;
				long addr = pnode.getLocalAddress();
				
				switch(pnode.getTypeFlag())
				{
				case NinBankNode.TYPE_TONE:
					//Just convert to instrument and wrap
					NinTone tone = pnode.getTone();
					if(tone == null) break;
					String skey = sndMap.get(tone.getWaveNumber());
					pidx = mybank.newPreset(i, presetName, 1);
					preset = mybank.getPreset(pidx);
					iidx = preset.newInstrument("TONE_" + Long.toHexString(addr), 1);
					inst = preset.getRegion(iidx).getInstrument();
					int ridx = inst.newRegion(skey);
					InstRegion r = inst.getRegion(ridx);
					tone.toInstRegion(r, false);
					simap.put(addr, inst);
					break;
				case NinBankNode.TYPE_INST:
					patchToPreset(pnode, mybank, i, simap, sndMap);
					break;
				case NinBankNode.TYPE_PERC:
					patchToPreset(pnode, mybank, i, simap, sndMap);
					break;
				case NinBankNode.TYPE_LINK:
					patchToPreset(pnode, mybank, i, simap, sndMap);
					break;
				}
			}
			i++;
			if(i > 0x7F)
			{
				//New bank
				bidx = bank.newBank(j, name + "_" + j);
				mybank = bank.getBank(bidx);
				i = 0; 
				j++;
			}
		}
		
		return bank;
	}
	
	public SynthBank generatePlayableBank(WaveArchive soundarc, int bankIndex){
		return new NinPlayableBank(this, soundarc);
	}
	
	public SynthBank generatePlayableBank(WaveArchive[] soundarc, int bankIndex){
		return new NinPlayableBank(this, soundarc);
	}
	
	/*--- Debug ---*/
	
	public void printInfo()
	{
		System.out.println("---== Binary NINTENDO Soundbank ==---");
		System.out.println("Surface Nodes: " + countSurfacePatches());
		int i = 0;
		for(NinBankNode node : topNodes)
		{
			System.out.println("\nSurface Patch " + i);
			if(node.isEmpty()) System.out.println("(Empty)");
			else node.printInfo(0);
			i++;
		}
	}


}

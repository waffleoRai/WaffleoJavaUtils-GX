package waffleoRai_soundbank.nintendo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.tree.TreeNode;

import waffleoRai_Utils.Treenumeration;
import waffleoRai_soundbank.SimpleInstrument;
import waffleoRai_soundbank.SoundbankNode;
import waffleoRai_soundbank.SimpleInstrument.InstRegion;

public class NinBankNode implements TreeNode{

	/* --- Constants --- */
	
	public static final int TYPE_NULL = 0;
	public static final int TYPE_TONE = 1;
	public static final int TYPE_INST = 2;
	public static final int TYPE_PERC = 3;
	public static final int TYPE_LINK = 4;
	
	/* --- Instance Variables --- */
	
	private NinBankNode parent;
	private ArrayList<NinBankNode> children;
	
	private int typeFlag;
	private long address;
	
	private NinTone tone; //If it's a leaf.
	
	private long linkAddress; //If it's a link
	private NinBankNode linkedNode; //If it's a link
	private Set<NinBankNode> backLinks;
	
	private byte minKey;
	private byte maxKey;
	
	private Map<Byte, NinBankNode> keymap;
	
	/* --- Construction --- */
	
	public NinBankNode(int typeflag, long rawAddress, NinBankNode parentNode)
	{
		typeFlag = typeflag;
		address = rawAddress;
		parent = parentNode;
		if(parent != null) parent.addChild(this);
		linkAddress = -1;
		initChildList();
		minKey = 0;
		maxKey = 0x7F;
		backLinks = new HashSet<NinBankNode>();
	}
	
	/* --- Parsing --- */
	
	private void initChildList()
	{
		int sz = 1;
		switch(typeFlag)
		{
		case TYPE_TONE: return; //leave null
		case TYPE_INST: sz = 16; break;
		case TYPE_PERC: sz = 128; break;
		case TYPE_LINK: return; //leave null
		}
		children = new ArrayList<NinBankNode>(sz);
	}
	
	public static NinBankNode generateEmptyNode(NinBankNode parentNode)
	{
		return new NinBankNode(TYPE_NULL, 0L, parentNode);
	}
		
	/* --- Getters --- */
	
	public int getTypeFlag(){return typeFlag;}
	public long getLocalAddress(){return address;}
	public NinTone getTone(){return tone;}
	public long getLinkAddress(){return linkAddress;}
	public boolean isLink(){return linkAddress != -1;}
	public NinBankNode getLinkedNode(){return linkedNode;}
	public boolean isEmpty(){return typeFlag == TYPE_NULL;}
	public byte getMinNote(){return minKey;}
	public byte getMaxNote(){return maxKey;}
	
	public List<NinBankNode> getChildren()
	{
		int sz = 0;
		if(children != null) sz = children.size();
		List<NinBankNode> list = new ArrayList<NinBankNode>(sz+1);
		if(children != null) list.addAll(children);
		return list;
	}

	public Collection<NinBankNode> getAllNonNullDescendants()
	{
		Set<NinBankNode> dcol = new HashSet<NinBankNode>();
		if(children != null)
		{
			for(NinBankNode n : children)
			{
				if(!n.isEmpty()) dcol.add(n);
			}
		}
		return dcol;
	}
	
	public int getDepth()
	{
		//A leaf returns a depth of 0
		if(this.isLink() && linkedNode != null) return linkedNode.getDepth();
		if(this.isLeaf()) return 0;
		int add = 1;

		for(NinBankNode n : children)
		{
			//null children should return true isLeaf
			if(!n.isLeaf())
			{
				int d = n.getDepth();
				d++;
				if(d > add) add = d;
			}
		}
		
		return add;
	}
	
	public int getDepthLevel()
	{
		if(parent == null) return 0;
		return parent.getDepthLevel() + 1;
	}
	
	public boolean containsLinks()
	{
		if(isLink()) return true;
		if(isLeaf()) return false;
		for(NinBankNode n : children)
		{
			if(n.containsLinks()) return true;
		}
		
		return false;
	}
	
	public Set<Integer> getAllLocalWaveNumbers()
	{
		Set<Integer> set = new HashSet<Integer>();
		this.addAllLocalWaveNumbers(set);
		return set;
	}
	
	private void addAllLocalWaveNumbers(Set<Integer> set)
	{
		if(isLink()) return;
		if(tone != null) set.add(tone.getWaveNumber());
		else
		{
			for(NinBankNode n : children)n.addAllLocalWaveNumbers(set);
		}
	}
	
	public boolean hasLinkingNodes(){return !backLinks.isEmpty();}
	
	public Collection<NinBankNode> getLinkingNodes()
	{
		Set<NinBankNode> set = new HashSet<NinBankNode>();
		set.addAll(backLinks);
		return set;
	}
	
	public int getMinLinkingNodeDepth()
	{
		if(backLinks.isEmpty()) return -1;
		int min = Integer.MAX_VALUE;
		for(NinBankNode l : backLinks)
		{
			int d = l.getDepthLevel();
			if(d < min) min = d;
		}
		return min;
	}
	
	public int getMaxLinkingNodeDepth()
	{
		if(backLinks.isEmpty()) return -1;
		int max = -1;
		for(NinBankNode l : backLinks)
		{
			int d = l.getDepthLevel();
			if(d > max) max = d;
		}
		return max;
	}
	
	public boolean hasLinkingNodesRecursive()
	{
		if(hasLinkingNodes()) return true;
		if(children != null)
		{
			for(NinBankNode c : children)
			{
				if(c.hasLinkingNodesRecursive()) return true;
			}
		}
		return false;
	}
	
	public Collection<NinBankNode> getLinkingNodesRecursive()
	{
		Set<NinBankNode> set = new HashSet<NinBankNode>();
		set.addAll(backLinks);
		if(children != null)
		{
			for(NinBankNode c : children)
			{
				set.addAll(c.getLinkingNodesRecursive());
			}
		}
		return set;
	}
	
	public int getMinLinkingNodeDepthRecursive()
	{
		if(backLinks.isEmpty()) return -1;
		int min = Integer.MAX_VALUE;
		for(NinBankNode l : backLinks)
		{
			int d = l.getDepthLevel();
			if(d < min) min = d;
		}
		if(children != null)
		{
			for(NinBankNode c : children)
			{
				int d = c.getMinLinkingNodeDepthRecursive();
				if(d >= 0 && d < min) min = d;
			}
		}
		return min;
	}
	
	public int getMaxLinkingNodeDepthRecursive()
	{
		if(backLinks.isEmpty()) return -1;
		int max = -1;
		for(NinBankNode l : backLinks)
		{
			int d = l.getDepthLevel();
			if(d > max) max = d;
		}
		if(children != null)
		{
			for(NinBankNode c : children)
			{
				int d = c.getMaxLinkingNodeDepthRecursive();
				if(d > max) max = d;
			}
		}
		return max;
	}
	
	public int getMinLinkedNodeDepthRecursive()
	{
		if(!this.containsLinks()) return -1;
		int min = Integer.MAX_VALUE;
		if(this.isLink() && this.linkedNode != null)
		{
			int d = linkedNode.getDepthLevel();
			if(d < min) min = d;
		}
		if(children != null)
		{
			for(NinBankNode child : children)
			{
				int d = child.getMinLinkedNodeDepthRecursive();
				if(d >= 0 && d < min) min = d;
			}
		}
		
		return min;
	}
	
	public int getMaxLinkedNodeDepthRecursive()
	{
		int max = -1;
		if(this.isLink() && this.linkedNode != null)
		{
			int d = linkedNode.getDepthLevel();
			if(d > max) max = d;
		}
		if(children != null)
		{
			for(NinBankNode child : children)
			{
				int d = child.getMaxLinkedNodeDepthRecursive();
				if(d > max) max = d;
			}
		}
		
		return max;
	}
	
	public NinTone getToneForKey(byte key){
		//System.err.println("NinBankNode.getToneForKey || Entered");
		if(linkedNode != null) return linkedNode.getToneForKey(key);
		if(isLeaf()) return tone;
		if(keymap == null) mapKeys();
		//System.err.println("NinBankNode.getToneForKey || Mapped");
		
		NinBankNode child = keymap.get(key);
		if(child == null) return null;

		return child.getToneForKey(key);
	}
	
	private void mapKeys(){
		keymap = new ConcurrentHashMap<Byte, NinBankNode>();
		//int n = 0;
		for(NinBankNode child : children)
		{
			//System.err.println("Child " + n++);
			byte min = child.getMinNote();
			byte max = child.getMaxNote();
			//System.err.println("Max note: " + max);
			for(int i = min; i <= max; i++){
				keymap.put((byte)i, child);
			}
		}
		//System.err.println("Mapping done");
	}
	
	public void clearKeyMapping(){
		if(keymap != null) keymap = null;
	}
	
	/* --- Setters --- */
	
	protected void addChild(NinBankNode child)
	{
		if(children == null) return;
		children.add(child);
	}

	protected void addBackLink(NinBankNode linker)
	{
		backLinks.add(linker);
	}
	
	protected void removeBackLink(NinBankNode linker)
	{
		backLinks.remove(linker);
	}
	
	public void setTone(NinTone rtone){tone = rtone;}
	
	public boolean setLinkAddress(long addr)
	{
		if(typeFlag != TYPE_LINK) return false;
		linkAddress = addr;
		return true;
	}
	
	public void setLinkedNode(NinBankNode node)
	{
		if(linkedNode != null)
		{
			linkedNode.removeBackLink(this);
		}
		if(typeFlag != TYPE_LINK) return;
		linkedNode = node;
		linkedNode.addBackLink(this);
	}
	
	public void setRange(byte min, byte max)
	{
		minKey = min;
		maxKey = max;
		if(tone != null) tone.setNoteRange(min, max);
	}
	
	/* --- Compare --- */
	
	public boolean equals(Object o)
	{
		/*if(this == o) return true;
		if(o == null) return false;
		if(!(o instanceof RBNKNode)) return false;
		RBNKNode n = (RBNKNode)o;

		if(this.typeFlag != n.typeFlag) return false;
		if(this.address != n.address) return false;
		
		return true;*/
		return this == o;
	}
	
	public int hashCode()
	{
		return (int)address;
	}
	
	/* --- Tree Node --- */
	
	@Override
	public TreeNode getChildAt(int childIndex) 
	{
		if(this.isEmpty()) return null;
		if(isLink() && linkedNode != null) return linkedNode.getChildAt(childIndex);
		if(children == null || children.isEmpty()) return null;
		if(childIndex < 0 || childIndex >= children.size()) return null;
		return children.get(childIndex);
	}

	@Override
	public int getChildCount() 
	{
		if(this.isEmpty()) return 0;
		if(isLink() && linkedNode != null) return linkedNode.getChildCount();
		if(children == null || children.isEmpty()) return 0;
		return children.size();
	}

	@Override
	public TreeNode getParent() 
	{
		return parent;
	}

	@Override
	public int getIndex(TreeNode node) 
	{
		if(node == null || this.isEmpty()) return -1;
		if(isLink() && linkedNode != null) return linkedNode.getIndex(node);
		if(children != null && !children.isEmpty())
		{
			int i = 0;
			for(NinBankNode n : children)
			{
				if(node == n) return i;
				i++;
			}
		}
		return -1;
	}

	@Override
	public boolean getAllowsChildren() 
	{
		switch(typeFlag)
		{
		case TYPE_NULL: return false;
		case TYPE_TONE: return false;
		case TYPE_INST: return true;
		case TYPE_PERC: return true;
		case TYPE_LINK:
			if(linkedNode != null) return linkedNode.getAllowsChildren();
			return false;
		}
		return false;
	}

	@Override
	public boolean isLeaf() 
	{
		if(!getAllowsChildren()) return true;
		if(isLink())
		{
			if(linkedNode != null) return linkedNode.isLeaf();
			else return true;
		}
		else
		{
			if(children == null) return true;
			return children.isEmpty();	
		}
	}

	@Override
	public Enumeration<TreeNode> children() 
	{
		if(isLink() && linkedNode != null) return linkedNode.children();
		int sz = 0;
		if(children != null) sz = children.size();
		List<TreeNode> list = new ArrayList<TreeNode>(sz+1);
		list.addAll(getChildren());
		return new Treenumeration(list);
	}
	
	/* --- Serialization --- */
	
	/* --- Conversion --- */
	
	private int estimateFlatInstRegions()
	{
		if(isLink())
		{
			if(linkedNode != null) return linkedNode.estimateFlatInstRegions();
		}
		if(isLeaf())
		{
			//Tone?
			return 1;
		}
		else
		{
			int tot = 0;
			for(NinBankNode child : children)
			{
				tot += child.estimateFlatInstRegions();
			}
			return tot;
		}
	}
	
	private void toInstRegions(SimpleInstrument inst, Map<Integer, String> sndKeys, byte minkey, byte maxkey)
	{
		//System.err.println("-DEBUG- Converting to regions inst " + Long.toHexString(this.getLocalAddress()));
		if(isLink())
		{
			//System.err.println("-DEBUG- Inst is link!");
			if(linkedNode != null) linkedNode.toInstRegions(inst, sndKeys, minKey, maxKey);
			return;
		}
		if(isLeaf())
		{
			//Tone?
			if(tone != null)
			{
				//System.err.println("-DEBUG- Node is leaf!");
				//System.err.println("-DEBUG- Clamping Note Range: " + String.format("%02x", minkey) + "-" + String.format("%02x", maxkey));
				//System.err.println("-DEBUG- Leaf Note Range: " + String.format("%02x", tone.getMinNote()) + "-" + String.format("%02x", tone.getMaxNote()));
				if(tone.getMinNote() > maxkey) return;
				if(tone.getMaxNote() < minkey) return;
				//System.err.println("-DEBUG- Note range passed!");
				
				int ridx = inst.newRegion(sndKeys.get(tone.getWaveNumber()));
				InstRegion reg = inst.getRegion(ridx);
				tone.toInstRegion(reg, true);
				//System.err.println("-DEBUG- Generated Region Range: " + String.format("%02x", reg.getMinKey()) + "-" + String.format("%02x", reg.getMaxKey()));
				if(reg.getMinKey() < minkey) reg.setMinKey(minkey);
				if(reg.getMaxKey() > maxkey) reg.setMaxKey(maxkey);
				//System.err.println("-DEBUG- Clamped Region Range: " + String.format("%02x", reg.getMinKey()) + "-" + String.format("%02x", reg.getMaxKey()));
			}
		}
		else
		{
			for(NinBankNode child : children)
			{
				//System.err.println("-DEBUG- Node is not a leaf!");
				byte newmin = minKey;
				byte newmax = maxKey;
				if(minkey > minKey) newmin = minkey;
				if(maxkey < maxKey) newmax = maxkey;
				child.toInstRegions(inst, sndKeys, newmin, newmax);
			}
		}
	}
	
	public SimpleInstrument toFlatInstrument(Map<Integer, String> sndKeys)
	{
		String pfix = "INST";
		if(tone != null) pfix = "TONE";
		String name = pfix + "_" + Long.toHexString(this.getLocalAddress());
		int rcount = this.estimateFlatInstRegions();
		SimpleInstrument inst = new SimpleInstrument(name, rcount);
		
		if(isLink() && linkedNode != null) return linkedNode.toFlatInstrument(sndKeys);
		
		if(isLeaf())
		{
			//Assumed tone
			if(tone != null)
			{
				int ridx = inst.newRegion(sndKeys.get(tone.getWaveNumber()));
				InstRegion reg = inst.getRegion(ridx);
				tone.toInstRegion(reg, false);
			}
		}
		else
		{
			//System.err.println("\n-DEBUG- Flattening Instrument " + Long.toHexString(this.getLocalAddress()));
			for(NinBankNode child : children)
			{
				child.toInstRegions(inst, sndKeys, (byte)0, (byte)0x7F);
			}
			//System.err.println("\n-DEBUG- Resulting Instrument --- ");
			//inst.printInfo();
		}
		
		return inst;
	}
	
	public SoundbankNode toSoundbankNode(SoundbankNode parentNode, int nodeidx){

		String myname = "[" + String.format("%03d", nodeidx) + "] 0x" + Long.toHexString(address);
		if(this.isLink()) myname += " (LINK)";
		int mytype = SoundbankNode.NODETYPE_PROGRAM;
		if(this.isLeaf()) mytype = SoundbankNode.NODETYPE_TONE;
		
		SoundbankNode node = new SoundbankNode(parentNode, myname, mytype);
		
		//Add metadata
		node.addMetadataEntry("Local Address", "0x" + Long.toHexString(address));
		node.addMetadataEntry("Key Range (Node)", minKey + " - " + maxKey);
		switch(typeFlag){
		case TYPE_TONE: node.addMetadataEntry("Node Type", "Tone"); break;
		case TYPE_INST: node.addMetadataEntry("Node Type", "Instrument"); break;
		case TYPE_PERC: node.addMetadataEntry("Node Type", "Percussion Set"); break;
		case TYPE_LINK: node.addMetadataEntry("Node Type", "Link"); break;
		}
		
		//Add link metadata (if applicable)
		if(this.isLink()){
			node.addMetadataEntry("Link Address", "0x" + Long.toHexString(linkAddress));
			
			int ltype = this.linkedNode.typeFlag;
			switch(ltype){
			case TYPE_TONE: node.addMetadataEntry("Linked Node Type", "Tone"); break;
			case TYPE_INST: node.addMetadataEntry("Linked Node Type", "Instrument"); break;
			case TYPE_PERC: node.addMetadataEntry("Linked Node Type", "Percussion Set"); break;
			case TYPE_LINK: node.addMetadataEntry("Linked Node Type", "Link"); break;
			}
			
		}
		
		//Add tone metadata (if applicable)
		if(tone != null){
			tone.addMetadataToNode(node);
		}
		if(linkedNode != null && linkedNode.tone != null){
			linkedNode.tone.addMetadataToNode(node);
		}
		
		//Do children (skip null placeholders)
		if(children != null){
			int i = 0;
			for(NinBankNode child : children){
				if(!child.isEmpty()) child.toSoundbankNode(node, i);
				i++;
			}
		}
		
		if(linkedNode != null && linkedNode.children != null){
			int i = 0;
			for(NinBankNode child : linkedNode.children){
				if(!child.isEmpty()) child.toSoundbankNode(node, i);
				i++;
			}
		}
		
		return node;
	}
	
	/*--- Debug ---*/
	
	public void printInfo(int tabs)
	{
		StringBuilder tabsb = new StringBuilder(16);
		for(int i = 0; i < tabs; i++) tabsb.append('\t');
		String tabstr = tabsb.toString();
		
		System.out.println(tabstr + "RBNK NODE ---");
		System.out.println(tabstr + "Depth: " + this.getDepth());
		System.out.println(tabstr + "Depth Level: " + this.getDepthLevel());
		if(children == null)
		{
			if(typeFlag == TYPE_TONE)
			{
				if(tone == null || tone.isEmpty()) System.out.println(tabstr + "(Empty Tone Leaf)");
				else tone.printInfo(tabs);
			}
			else if(typeFlag == TYPE_LINK)
			{
				System.out.println(tabstr + "Link: 0x" + Long.toHexString(getLinkAddress()));
			}
			else System.out.println(tabstr + "[Unknown Type]");
		}
		else
		{
			int i = 0;
			for(NinBankNode n : children)
			{
				if(n.isEmpty()){i++; continue;}
				System.out.println(tabstr + "-> REGION " + i);
				System.out.println(tabstr + "Depth: " + n.getDepth());
				System.out.println(tabstr + "Depth Level: " + n.getDepthLevel());
				switch(n.typeFlag)
				{
				case TYPE_TONE:
					if(n.tone == null || n.tone.isEmpty()) System.out.println(tabstr + "(Empty Tone Leaf)");
					else n.tone.printInfo(tabs);
					break;
				case TYPE_INST:
				case TYPE_PERC:
					n.printInfo(tabs+1);
					break;
				case TYPE_LINK:
					System.out.println(tabstr + "Link: 0x" + Long.toHexString(n.getLinkAddress()));
					break;
				}
				System.out.println(tabstr + "Node Min Note: " + String.format("0x%02x", n.minKey));
				System.out.println(tabstr + "Node Max Note: " + String.format("0x%02x", n.maxKey));
				i++;
			}
		}
		
	}
	
}

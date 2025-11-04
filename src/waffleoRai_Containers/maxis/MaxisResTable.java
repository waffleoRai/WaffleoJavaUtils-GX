package waffleoRai_Containers.maxis;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import waffleoRai_DataContainers.MultiValMap;
import waffleoRai_Files.XMLReader;
import waffleoRai_Files.maxis.MaxisResRef;
import waffleoRai_Files.maxis.MaxisTypeIds;

public class MaxisResTable {
	
	//Allows mapping between keys, names, and import/export paths
	
	private Map<MaxisResKey, MaxisResRef> table;
	private MultiValMap<String, MaxisResRef> nameMap;
	private Map<String, MaxisResRef> uniqueNameMap;
	
	public MaxisResTable() {
		table = new HashMap<MaxisResKey, MaxisResRef>();
		nameMap = new MultiValMap<String, MaxisResRef>();
		uniqueNameMap = new HashMap<String, MaxisResRef>();
	}
	
	/*----- Getters -----*/
	
	public MaxisResRef getEntry(MaxisResKey key) {
		return table.get(key);
	}
	
	public List<MaxisResRef> getEntryByName(String resName) {
		return nameMap.getValues(resName);
	}
	
	public MaxisResRef getEntryByUniqueName(String val) {
		return uniqueNameMap.get(val);
	}
	
	/*----- Setters -----*/
	
	public void clear() {
		table.clear();
		nameMap.clearValues();
		uniqueNameMap.clear();
	}
	
	private void updateUniqueName(MaxisResRef entry) {
		MaxisResKey key = entry.getKey();
		String u = entry.getName();
		u += "_t" + MaxisTypeIds.stringFromValue(key.getTypeId());
		u += String.format("_g%08X", key.getGroupId());
		if(uniqueNameMap.containsKey(u)) {
			u += String.format("_i%016X", key.getInstanceId());
		}
		
		//Replace bad characters
		u = u.replace("/", "");
		u = u.replace(" ", "_");
		u = u.replace("\\", "");
		u = u.replace(":", "");
		u = u.replace(";", "");
		
		entry.setUniqueName(u);
		uniqueNameMap.put(entry.getUniqueName(), entry);
	}
	
	public MaxisResRef newEntry(MaxisResKey key, String name) {
		MaxisResRef entry = new MaxisResRef(key);
		if(name != null) {
			entry.setName(name);
			List<MaxisResRef> sameNames = nameMap.getValues(name);
			if(sameNames != null && !sameNames.isEmpty()) {
				uniqueNameMap.remove(name);
				for(MaxisResRef other : sameNames) {
					String u = other.getUniqueName();
					if(u.equals(name)) {
						updateUniqueName(other);
					}
				}
				updateUniqueName(entry);
			}
			else {
				entry.setUniqueName(name);
				uniqueNameMap.put(entry.getUniqueName(), entry);
			}
		}
		
		table.put(key, entry);
		//nameMap.put(name, entry);
		nameMap.addValue(entry.getName(), entry);
		return entry;
	}
	
	/*----- XML -----*/
	
	public static MaxisResTable readXML(Element root) {
		String nn = root.getNodeName();
		if(!nn.equals("MaxisResTable")) return null;
		
		MaxisResTable tbl = new MaxisResTable();
		List<Element> children = XMLReader.getChildElements(root);
		for(Element child : children) {
			MaxisResRef entry = MaxisResRef.fromXMLNode(child);
			tbl.table.put(entry.getKey(), entry);
			//tbl.nameMap.put(entry.resName, entry);
			tbl.nameMap.addValue(entry.getName(), entry);
			tbl.uniqueNameMap.put(entry.getUniqueName(), entry);
		}
		
		return tbl;
	}
	
	public void writeXML(Writer out, String indent) throws IOException {
		if(indent == null) indent = "";
		out.write(indent + "<MaxisResTable>\n");
		
		List<MaxisResKey> keyset = new ArrayList<MaxisResKey>(table.size()+1);
		keyset.addAll(table.keySet());
		Collections.sort(keyset);
		
		for(MaxisResKey key : keyset) {
			MaxisResRef entry = table.get(key);
			entry.writeXMLNode(out, indent + "\t");
		}
		
		out.write(indent + "</MaxisResTable>\n");
	}

}

package waffleoRai_Files.maxis.xml2java;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.StringUtils;

public class Test_GenStructClassNoPS {

	public static final String[] DEF_TYPES = {"Struct", "TS3ResourceFileStruct", "PseudoUnion"};
	
	private static List<Element> getNodesToScan(Document xmlDoc){
		//Top node should be called "DefPackage"
		List<Element> list = new LinkedList<Element>();
		NodeList nl = xmlDoc.getElementsByTagName("DefPackage");
		if(nl == null) return list;
		
		Element pkgElement = null;
		int nodeCount = nl.getLength();
		for(int i = 0; i < nodeCount; i++) {
			Node node = nl.item(i);
			if(node.getNodeType() == Node.ELEMENT_NODE) {
				pkgElement = (Element)node;
				break;
			}
		}
		if(pkgElement == null) return list;
		
		nl = pkgElement.getChildNodes();
		nodeCount = nl.getLength();
		for(int i = 0; i < nodeCount; i++) {
			Node node = nl.item(i);
			String nodeName = node.getNodeName();
			boolean structType = false;
			for(int j = 0; j < DEF_TYPES.length; j++) {
				if(nodeName.equals(DEF_TYPES[j])) {
					structType = true;
					break;
				}
			}
			if(!structType) continue;
			
			if(node.getNodeType() == Node.ELEMENT_NODE) {
				list.add((Element)node);
			}
		}
		
		return list;
	}
	
	public static void main(String[] args) {
		String inPath = args[0]; //Input XML
		String outPath = args[1]; //Directory
		String templateFile = args[2];
		
		try {
			Document xmlDoc = XMLReader.readXMLStatic(inPath);
			
			if(!Files.isDirectory(Paths.get(outPath))) {
				Files.createDirectories(Paths.get(outPath));
			}
			
			List<Element> defList = getNodesToScan(xmlDoc);
			if(defList.isEmpty()) {
				System.err.println("Failed to find any struct def nodes!");
				System.exit(1);
			}
			
			for(Element e : defList) {
				String defName = e.getAttribute("Name");
				defName = StringUtils.capitalize(defName);
				String outpath = outPath + File.separator + defName + ".java";
				
				BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
				TS3XJ.writeClassTo(e, null, bw, templateFile, false, null);
				//doDefNode(e, outPath, templateFile, false);
				bw.close();
			}
			
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

}

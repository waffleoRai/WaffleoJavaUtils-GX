package waffleoRai_Files.maxis;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import waffleoRai_Reflection.ReflectionUtils;

public class MaxisResConv {

	private Map<Integer, MaxisResImporterExporter> typeHandlers;
	private MaxisResImporterExporter defoHandler;
	
	public MaxisResConv() {
		typeHandlers = new HashMap<Integer, MaxisResImporterExporter>();
		defoHandler = new MxAsIsIO();
	}
	
	public MaxisResImporterExporter getHandler(int typeId) {
		return typeHandlers.get(typeId);
	}
	
	public MaxisResImporterExporter getDefaultHandler() {
		return defoHandler;
	}
	
	public boolean scanAndLoadFromDir(String dir) {
		try {
			ReflectionUtils.loadClassesFromDir(dir, MaxisResConv.class);
			Collection<Class<?>> subclasses = ReflectionUtils.findSubclassesOf(MaxisResImporterExporter.class, false);
			if(subclasses != null) {
				for(Class<?> c : subclasses) {
					Object o = c.getConstructor().newInstance();
					if((o != null) && (o instanceof MaxisResImporterExporter)) {
						MaxisResImporterExporter io = (MaxisResImporterExporter)o;
						int[] typeIds = io.getCoveredTypeIds();
						if(typeIds != null) {
							for(int typeId : typeIds) if(typeId != 0) typeHandlers.put(typeId, io);
						}
					}
				}
			}
		} 
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean scanAndLoadPackaged() {
		try {
			ReflectionUtils.scanForAppClasses(MaxisResConv.class, 0);
			Collection<Class<?>> subclasses = ReflectionUtils.findSubclassesOf(MaxisResImporterExporter.class, false);
			if(subclasses != null) {
				for(Class<?> c : subclasses) {
					Object o = c.getConstructor().newInstance();
					if((o != null) && (o instanceof MaxisResImporterExporter)) {
						MaxisResImporterExporter io = (MaxisResImporterExporter)o;
						int[] typeIds = io.getCoveredTypeIds();
						if(typeIds != null) {
							for(int typeId : typeIds) {
								if(typeId != 0) typeHandlers.put(typeId, io);
							}
						}
					}
				}
			}
		} 
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
}

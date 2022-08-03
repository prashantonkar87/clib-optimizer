package org.clibopt.core.services.impl;

import java.util.HashMap;
import java.util.Map;

//@Component(service=OptimizerCacheModel.class)
public class OptimizerCacheModel {

//	@Reference
//	HtmlLibraryManager htmlLibManager;
//	
//	@Reference
//	ResourceResolverFactory resolverFactory;
	
	private Map<String, String> codeWiseCache;
	private Map<String, String> pathWiseCache;
	
	public OptimizerCacheModel() {
		codeWiseCache = new HashMap<String,String>();
		pathWiseCache = new HashMap<String,String>();
	}

	public String getClibWithCode(String clibCode) {
		return codeWiseCache.get(clibCode);
	}

	public String getClibWithPath(String clibPath) {
		return pathWiseCache.get(clibPath);
	}

	public void updateCache(String clibPath, String clibCode) {
		pathWiseCache.put(clibPath, clibCode);
		codeWiseCache.put(clibCode, clibPath);
	}
	
	

}

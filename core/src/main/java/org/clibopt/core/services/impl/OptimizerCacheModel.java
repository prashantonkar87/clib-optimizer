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
	
	private Map<String, CacheModel> codeWiseCache;
	private Map<String, CacheModel> pathWiseCache;
	
	public OptimizerCacheModel() {
		codeWiseCache = new HashMap<String,CacheModel>();
		pathWiseCache = new HashMap<String,CacheModel>();
	}

	public CacheModel getClibWithCode(String clibCode) {
		return codeWiseCache.get(clibCode);
	}

	public CacheModel getClibWithPath(String clibPath) {
		return pathWiseCache.get(clibPath);
	}

	public void updateCache(CacheModel model) {
		pathWiseCache.put(model.getClibPath(), model);
		codeWiseCache.put(model.getClibCode(), model);
	}
	
	

}

package org.clibopt.core.services.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.clibopt.core.services.OptimizerCacheService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;

@Component(service = OptimizerCacheService.class)
public class OptimizerCacheServiceImpl implements OptimizerCacheService {

	@Reference
	private ResourceResolverFactory resourceResolverFactory;

	@Reference
	private QueryBuilder queryBuilder;
	private ResourceResolver resourceResolver;
	private static final String CLIB_ROOT = "/var/clibopt/clib";
//	private Map<String,String> codeWiseCache = new HashMap<String,String>();
//	private Map<String,String> pathWiseCache = new HashMap<String,String>();
	private OptimizerCacheModel cache = new OptimizerCacheModel();

	private static final Logger LOG = LoggerFactory.getLogger(OptimizerCacheServiceImpl.class);

//	private String getPathFromCache(String clibCode) {
//		return codeWiseCache.get(clibCode);
//	}
//	
//	private String getClibCodeFromCache(String clibPath) {
//		return pathWiseCache.get(clibPath);
//	}

	private void updateCache(String clibPath, Long clibCode) {
		initializeResourceResolver();
//		String[] categories = resourceResolver.getResource(clibPath).getValueMap().get("categories", String[].class);
//		CacheModel model = new CacheModel(clibPath, convertIntToBase36String(clibCode));
		cache.updateCache(clibPath, convertIntToBase36String(clibCode));
	}

	@Override
	public String getClibCode(String clibPath) {

		if (cache.getClibWithPath(clibPath) != null) {
			LOG.debug("Found in cache ");
			return cache.getClibWithPath(clibPath);
		}

		initializeResourceResolver();
		Resource clibopt = resourceResolver.getResource(CLIB_ROOT +"/"+ getClibNodeName(clibPath));
		Long maxClibCodeCount;
		Long clibCode = null;
		if (clibopt != null) {
			LOG.debug("clibopt entry present for " + clibopt.getPath()+" "+getClibNodeName(clibPath));
			LOG.debug("hex " + clibopt.getValueMap().get("clibCode", String.class));
			clibCode = clibopt.getValueMap().get("clibCode", Long.class);
			if (clibCode == null) {
				LOG.debug("Creating new hex ");
				clibCode = generateClibCode();
				ModifiableValueMap map = clibopt.adaptTo(ModifiableValueMap.class);
				map.put(clibPath, map);
//				map.put("categories",
//						resourceResolver.getResource(clibPath).getValueMap().get("categories", String[].class));
				try {
					resourceResolver.commit();
				} catch (PersistenceException e) {
					LOG.error("Unable to create under " + CLIB_ROOT + " " + getClibNodeName(clibPath), e);
				}
			}
			updateCache(clibPath, clibCode);
			return convertIntToBase36String(clibCode);
		} else {
			LOG.debug("clibopt entry not present for " + clibPath+" "+getClibNodeName(clibPath));
			Map<String, Object> map = new HashMap<String, Object>();
			// get max hex count
			maxClibCodeCount = resourceResolver.getResource(CLIB_ROOT).getValueMap().get("maxClibCodeCount",
					Long.class);
			;
			LOG.debug("maxClibCodeCount " + maxClibCodeCount);
			if (maxClibCodeCount == null) {
				LOG.debug("initializing hexcount ");
				maxClibCodeCount = 0L;
				clibCode = 0L;
				updateMaxClibCodeCount(0L);
			} else if (maxClibCodeCount == 0L) {
				maxClibCodeCount = 1L;
				clibCode = 1L;
				updateMaxClibCodeCount(1L);
			} else {
				clibCode = maxClibCodeCount + 1L;
				updateMaxClibCodeCount(clibCode);
				LOG.debug("After incrementing hexcount : " + clibCode);
			}
			// create clibopt entry
			map.put("clibCode", clibCode);
			map.put("clibPath", clibPath);
//			map.put("categories",
//					resourceResolver.getResource(clibPath).getValueMap().get("categories", String[].class));
			map.put("jcr:primaryType", "nt:unstructured");
			try {
				ResourceUtil.getOrCreateResource(resourceResolver, CLIB_ROOT + "/" + getClibNodeName(clibPath), map,
						"nt:unstructured", true);
				updateCache(clibPath, clibCode);
//				resourceResolver.create(resourceResolver.getResource(CLIB_ROOT), getClibNodeName(clibPath), map);
//				resourceResolver.commit();
			} catch (PersistenceException e) {
				LOG.error("Unable to create under/var/clibopt" + getClibNodeName(clibPath), e);
			}
			return convertIntToBase36String(clibCode);
		}

	}

	private void initializeResourceResolver() {
		if (resourceResolver == null) {
			Map<String, Object> param = new HashMap<>();
			param.put(ResourceResolverFactory.SUBSERVICE, "readClientLibs");
			try {
				resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);
			} catch (LoginException e) {
				LOG.error("Unable to initialize service user " + e);
			}
		}
	}

	private String getClibNodeName(String clibPath) {
		LOG.debug("Node name " + clibPath.replaceAll("/", "-").substring(1, clibPath.length()));
		return clibPath.replaceAll("/", "-").substring(1, clibPath.length());
	}

	private Long generateClibCode() {
		Long maxClibCodeCount = resourceResolver.getResource(CLIB_ROOT).getValueMap().get("maxClibCodeCount",
				Long.class);
		return maxClibCodeCount + 1L;
	}

	private String convertIntToBase36String(Long i) {
		return Integer.toString(i.intValue(), 36);
	}

	private Long convertBase36StringToInt(String clibCode) {
		return Long.parseLong(clibCode, 36);
	}

	private void updateMaxClibCodeCount(Long maxClibCodeCount) {
		ModifiableValueMap valueMap = resourceResolver.getResource(CLIB_ROOT).adaptTo(ModifiableValueMap.class);
		valueMap.put("maxClibCodeCount", maxClibCodeCount);
		try {
			resourceResolver.commit();
		} catch (PersistenceException e) {
			LOG.error("Unable to initialize maxClibCodeCount in /var/clibopt ", e);
		}
	}

	@Override
	public String getClibPath(String clibCode) {

		if (cache.getClibWithCode(clibCode) != null) {
			return cache.getClibWithCode(clibCode);
		} else {
			initializeResourceResolver();
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("path", CLIB_ROOT);
			map.put("1_property", "clibCode");
			map.put("1_property.value", convertBase36StringToInt(clibCode));
			Query query = queryBuilder.createQuery(PredicateGroup.create(map), resourceResolver.adaptTo(Session.class));

			SearchResult result = query.getResult();
			Resource resource = null;
			try {
				for (Hit hit : result.getHits()) {
					resource = hit.getResource();
				}
			} catch (RepositoryException e) {
				LOG.error("Error while getting clib for " + clibCode, e);
			} 
			if (resource != null) {
				updateCache(resource.getValueMap().get("clibPath", String.class), convertBase36StringToInt(clibCode));
				return resource.getValueMap().get("clibPath", String.class);
			}
			LOG.error("No clientlib found for " + clibCode);
			return null;
		}
	}

}

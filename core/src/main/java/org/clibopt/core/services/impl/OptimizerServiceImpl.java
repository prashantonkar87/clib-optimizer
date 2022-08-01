package org.clibopt.core.services.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.clibopt.core.services.OptimizerConfig;
import org.clibopt.core.services.OptimizerService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.ui.clientlibs.HtmlLibraryManager;
import com.adobe.granite.ui.clientlibs.LibraryType;

@Component(service = OptimizerService.class)
@Designate(ocd = OptimizerConfig.class)
public class OptimizerServiceImpl implements OptimizerService {

	private static final Logger LOG = LoggerFactory.getLogger(OptimizerServiceImpl.class);
	private static final String CLIB_ROOT="/var/clibopt/clib";
	private List<String> paths;
	private List<String> ignoredClibs;
	private boolean isOptEnabled;
	private ResourceResolver resourceResolver;

	@Reference
	private HtmlLibraryManager htmlLibManager;

	@Reference
	private ResourceResolverFactory resourceResolverFactory;

	@Override
	public boolean isOptNeededForClib(String clibPath) {
		return !ignoredClibs.contains(clibPath);
	}

	@Override
	public boolean isOptNeededForPath(String contentPath) {
		return paths.contains(contentPath);
	}

	@Override
	public String optimizeClientLibs(String responseHTML, String pagePath) {
		if (this.isOptEnabled) {
			Document doc = Jsoup.parse(responseHTML);
			// optimize css
			processCSS(doc, pagePath);
			processJS(doc, pagePath);
			LOG.debug("returning");
			return doc.html();
		} else {
			LOG.debug("Optimization disabled. No Action taken");
			return responseHTML;
		}
	}

	private void processJS(Document doc, String pagePath) {
		Elements elements = doc.select("script");
		Iterator<Element> jsIter = elements.iterator();
		Set<String> jsClibSet = new LinkedHashSet<String>();
		StringBuilder clibIdentifier = new StringBuilder();
		while (jsIter.hasNext()) {
			Element element = jsIter.next();
			LOG.debug("Checking Attr : " + element.attr("src"));
			String clibPath = getClibPath(LibraryType.JS, element.attr("src"));
			if (clibPath != null) {
				String clibcode = getClibCode(clibPath);
				if (clibcode != null) {
					jsClibSet.add(clibcode);
					LOG.debug("hex for " + element.attr("src") + " is " + clibcode);
					// element.remove();
				} else {
					LOG.error("Null hex for : " + element.attr("src"));
				}
			} else {
				LOG.debug("Not processed " + element.attr("src"));
			}
		}
		LOG.debug("id " + jsClibSet.toString());
		int i = 0;
		for (String str : jsClibSet) {
			clibIdentifier.append(str);
			if (i < jsClibSet.size() - 1) {
				clibIdentifier.append("-");
			}
			i++;
		}
		doc.select("body").first().append(
				"<script type=\"text/javascript\" src=\"/clib." + clibIdentifier.toString() + ".js" + "\"></script>");
	}

	private String getClibPath(LibraryType type, String attr) {
		String clibPath = null;
		if (attr != null && (attr.startsWith("/etc.clientlibs") || attr.startsWith("/libs"))) {
			if (attr.startsWith("/etc.clientlibs")) {
				clibPath = attr.substring(15);
				if (clibPath.startsWith("/clientlibs") || (clibPath.startsWith("/foundation") || (clibPath.startsWith("/settings")))) {
					clibPath = "/libs" + clibPath;
				} else {
					clibPath = "/apps" + clibPath;
				}
			} else {
				clibPath = attr;
			}
			if (type.equals(LibraryType.CSS)) {
				clibPath = clibPath.substring(0, clibPath.indexOf(".css"));
			} else if (type.equals(LibraryType.JS)) {
				clibPath = clibPath.substring(0, clibPath.indexOf(".js"));
			}
			if (!this.ignoredClibs.contains(clibPath)) {
				if (htmlLibManager.getLibrary(type, clibPath) == null) {
					LOG.warn("No clientlib found under " + clibPath);
					return null;
				}
				LOG.debug("Client lib found: " + clibPath);
				return clibPath;
			} else {
				return null;
			}
		}
		return null;

	}

	private void processCSS(Document doc, String pagePath) {
		Elements elements = doc.getElementsByAttributeValueContaining("href", "clientlib");
		Iterator<Element> cssIter = elements.iterator();
		Set<String> cssClibSet = new LinkedHashSet<String>();
		StringBuilder clibIdentifier = new StringBuilder();
		while (cssIter.hasNext()) {
			Element element = cssIter.next();
			LOG.debug("Attr : " + element.attr("href"));
			String clibPath = getClibPath(LibraryType.CSS, element.attr("href"));
			if (clibPath != null) {
				String clibcode = getClibCode(clibPath);
				if (clibcode != null) {
					cssClibSet.add(clibcode);
					LOG.debug("hex for " + element.attr("href") + " is " + clibcode);
					// element.remove();
				} else {
					LOG.error("Null hex for : " + element.attr("href"));
				}
			} else {
				LOG.debug("Not processed " + element.attr("href"));
			}
		}
		LOG.debug("id " + cssClibSet.toString());
		int i = 0;
		for (String str : cssClibSet) {
			clibIdentifier.append(str);
			if (i < cssClibSet.size() - 1) {
				clibIdentifier.append("-");
			}
			i++;
		}
		doc.select("head").first().append(
				" <link rel=\"stylesheet\" href=\"/clib." + clibIdentifier.toString() + ".css\" type=\"text/css\">");
	}

	private String getClibCode(String clibPath) {
		if (resourceResolver == null) {
			Map<String, Object> param = new HashMap<>();
			param.put(ResourceResolverFactory.SUBSERVICE, "readClientLibs");
			try {
				resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);
			} catch (LoginException e) {
				LOG.error("Unable to initialize service user " + e);
			}
		}
		Resource clibopt = resourceResolver.getResource(CLIB_ROOT +  getClibNodeName(clibPath));
		Long maxClibCodeCount;
		if (clibopt != null) {
			LOG.debug("clibopt entry present for " + clibopt.getPath());
			LOG.debug("hex " + clibopt.getValueMap().get("clibCode", String.class));
			if (clibopt.getValueMap().get("clibCode", String.class) == null) {
				LOG.debug("Creating new hex ");
				clibopt.adaptTo(ModifiableValueMap.class).put("clibCode", generateClibCode());
				try {
					resourceResolver.commit();
				} catch (PersistenceException e) {
					LOG.error("Unable to create under "+CLIB_ROOT+" " +  getClibNodeName(clibPath), e);
				}
			}
			return clibopt.getValueMap().get("clibCode", String.class);
		} else {
			LOG.debug("clibopt entry not present for " + clibPath);
			Map<String, Object> map = new HashMap<String, Object>();
			Long clibCode = 0L;
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
			map.put("jcr:primaryType", "nt:unstructured");
			try {
				ResourceUtil.getOrCreateResource(resourceResolver, CLIB_ROOT + "/"+getClibNodeName(clibPath), map, "nt:unstructured",
						true);
//				resourceResolver.create(resourceResolver.getResource(CLIB_ROOT), getClibNodeName(clibPath), map);
//				resourceResolver.commit();
			} catch (PersistenceException e) {
				LOG.error("Unable to create under/var/clibopt" + getClibNodeName(clibPath), e);
			}
			return convertIntToBase36String(clibCode);
		}
	}

	private String getClibNodeName(String clibPath) {
		LOG.debug("Node name "+clibPath.replaceAll("/", "-").substring(1, clibPath.length()));
		return clibPath.replaceAll("/", "-").substring(1, clibPath.length());
	}

	private String generateClibCode() {
		Long maxClibCodeCount = resourceResolver.getResource(CLIB_ROOT).getValueMap().get("maxClibCodeCount",
				Long.class);
		return convertIntToBase36String(maxClibCodeCount + 1);
	}

	private String convertIntToBase36String(Long i) {
		return Integer.toString(i.intValue(), 36);
	}

	private int convertBase36StringToInt(String clibCode) {
		return Integer.parseInt(clibCode, 36);
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

	@Activate
	protected void activate(OptimizerConfig config) {
		this.isOptEnabled = config.isOptEnabled();
		this.paths = Arrays.asList(config.paths());
		this.ignoredClibs = Arrays.asList(config.ignoredClibs());
		LOG.info("Activated OptimizerServiceImpl with paths [ {} ]", String.join(", ", this.paths));

	}
}

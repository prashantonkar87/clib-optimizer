package org.clibopt.core.services.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
		Set<String> jsClibSet = new HashSet<String>();
		StringBuilder clibIdentifier = new StringBuilder();
		while (jsIter.hasNext()) {
			Element element = jsIter.next();
			LOG.debug("Attr : " + element.attr("src"));
			if (element.attr("src").startsWith("/etc.clientlibs") || element.attr("src").startsWith("/libs")) {
				jsClibSet.add(getHexCode(LibraryType.JS, element.attr("src")));
				LOG.debug("hex for "+element.attr("src") + getHexCode(LibraryType.JS, element.attr("src")));
				// element.remove();
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
		doc.select("body").first().append("<script type=\"text/javascript\" src=\"/bin/clibopt."
				+ clibIdentifier.toString() + ".js" + "\"></script>");
	}

	private void processCSS(Document doc, String pagePath) {
		Elements elements = doc.getElementsByAttributeValueContaining("href", "clientlib");
		Iterator<Element> cssIter = elements.iterator();
		Set<String> cssClibSet = new HashSet<String>();
		StringBuilder clibIdentifier = new StringBuilder();
		while (cssIter.hasNext()) {
			Element element = cssIter.next();
			LOG.debug("Attr : " + element.attr("href"));
			if (element.attr("href").startsWith("/etc.clientlibs") || element.attr("href").startsWith("/libs")) {
				cssClibSet.add(getHexCode(LibraryType.CSS, element.attr("href")));
				LOG.debug("Element : " + element.outerHtml());
				LOG.debug("href : " + element.attr("href"));
				// element.remove();
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
		doc.select("head").first().append(" <link rel=\"stylesheet\" href=\"/bin/clibopt." + clibIdentifier.toString()
				+ ".css\" type=\"text/css\">");
	}

	private String getHexCode(LibraryType type, String clibPath) {
		LOG.debug("processing clibpath " + clibPath);
		if (clibPath.startsWith("/etc.clientlibs")) {
			clibPath = clibPath.substring(15);
			if (clibPath.startsWith("/clientlibs") || (clibPath.startsWith("/foundation"))) {
				clibPath = "/libs" + clibPath;
			} else {
				clibPath = "/apps" + clibPath;
			}
		}
		LOG.debug("after substring " + clibPath);

		if (type.equals(LibraryType.CSS)) {
			clibPath = clibPath.substring(0, clibPath.indexOf(".css"));
		} else if (type.equals(LibraryType.JS)) {
			clibPath = clibPath.substring(0, clibPath.indexOf(".js"));
		}
		LOG.debug("processed clibpath " + clibPath);
		// LOG.debug("clib " + htmlLibManager.getLibrary(type, clibPath).getName());
		if (htmlLibManager.getLibrary(type, clibPath) == null) {
			LOG.warn("No clientlib found under " + clibPath);
			return null;
		}
		if (resourceResolver == null) {
			Map<String, Object> param = new HashMap<>();
			param.put(ResourceResolverFactory.SUBSERVICE, "readClientLibs");
			try {
				resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);
			} catch (LoginException e) {
				LOG.error("Unable to initialize service user " + e);
			}
		}
		Resource clibopt = resourceResolver.getResource("/var/clibopt" + clibPath);
		String hexCount;
		if (clibopt != null) {
			LOG.debug("clibopt entry present for " + clibopt.getPath());
			LOG.debug("hex " + clibopt.getValueMap().get("hexCode", String.class));
			if(clibopt.getValueMap().get("hexCode", String.class)==null) {
				clibopt.adaptTo(ModifiableValueMap.class).put("hexCode", generateHexCode());
				try {
					resourceResolver.commit();
				} catch (PersistenceException e) {
					LOG.error("Unable to create under/var/clibopt" + clibPath, e);
				}
			}
			return clibopt.getValueMap().get("hexCode", String.class);
		} else {
			LOG.debug("clibopt entry not present for " + clibPath);
			Map<String, Object> map = new HashMap<String, Object>();
			String hexCode = "";
			// get max hex count
			hexCount = resourceResolver.getResource("/var/clibopt").getValueMap().get("hexCount", String.class);;
			LOG.debug("hexcount " + hexCount);
			if (hexCount == null) {
				LOG.debug("initializing hexcount ");
				hexCount = "0";
				hexCode = "0";
				updateHexCount("0");
			} else if (hexCount.equals("0")) {
				hexCode = "1";
				updateHexCount("1");
			} else {
				hexCode = convertIntToBase36String(convertBase36StringToInt(hexCount) + 1);
				updateHexCount(hexCode);
				LOG.debug("After incrementing hexcount : " + hexCode);
			}
			// create clibopt entry
			map.put("hexCode", hexCode);
			map.put("jcr:primaryType", "nt:unstructured");
			try {
				ResourceUtil.getOrCreateResource(resourceResolver, "/var/clibopt" + clibPath, map, "nt:unstructured",
						true);
			} catch (PersistenceException e) {
				LOG.error("Unable to create under/var/clibopt" + clibPath, e);
			}

			return hexCode;
		}
	}

	private String generateHexCode() {
		String hexCount = resourceResolver.getResource("/var/clibopt").getValueMap().get("hexCount", String.class);
		return convertIntToBase36String(convertBase36StringToInt(hexCount) + 1);
	}

	private String convertIntToBase36String(int i) {
		return Integer.toString(i, 36);
	}

	private int convertBase36StringToInt(String hexCode) {
		return Integer.parseInt(hexCode, 36);
	}

	private void updateHexCount(String hexCount) {
		ModifiableValueMap valueMap = resourceResolver.getResource("/var/clibopt").adaptTo(ModifiableValueMap.class);
		valueMap.put("hexCount", hexCount);
		try {
			resourceResolver.commit();
		} catch (PersistenceException e) {
			LOG.error("Unable to initialize hexCount in /var/clibopt ", e);
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

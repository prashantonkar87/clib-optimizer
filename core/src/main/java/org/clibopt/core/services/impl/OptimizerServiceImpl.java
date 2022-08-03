package org.clibopt.core.services.impl;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.clibopt.core.services.OptimizerCacheService;
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

	@Reference
	private HtmlLibraryManager htmlLibManager;

	@Reference
	private OptimizerCacheService optimizerCache;

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
				String clibcode = optimizerCache.getClibCode(clibPath);
				if (clibcode != null) {
					jsClibSet.add(clibcode);
					LOG.debug("hex for " + element.attr("src") + " is " + clibcode);
					element.remove();
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
				"<script type=\"text/javascript\" src=\"/bin/clib." + clibIdentifier.toString() + ".js" + "\"></script>");
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
				String clibcode = optimizerCache.getClibCode(clibPath);
				if (clibcode != null) {
					cssClibSet.add(clibcode);
					LOG.debug("hex for " + element.attr("href") + " is " + clibcode);
					element.remove();
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
				" <link rel=\"stylesheet\" href=\"/bin/clib." + clibIdentifier.toString() + ".css\" type=\"text/css\">");
	}

	private String getClibPath(LibraryType type, String attr) {
		String clibPath = null;
		if (attr != null && (attr.startsWith("/etc.clientlibs") || attr.startsWith("/libs"))) {
			if (attr.startsWith("/etc.clientlibs")) {
				clibPath = attr.substring(15);
				if (clibPath.startsWith("/clientlibs")
						|| (clibPath.startsWith("/foundation") || (clibPath.startsWith("/settings")))) {
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
			if(clibPath.endsWith(".min")) {
				clibPath = clibPath.substring(0, clibPath.indexOf(".min"));
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

	

	@Activate
	protected void activate(OptimizerConfig config) {
		this.isOptEnabled = config.isOptEnabled();
		this.paths = Arrays.asList(config.paths());
		this.ignoredClibs = Arrays.asList(config.ignoredClibs());
		LOG.info("Activated OptimizerServiceImpl with paths [ {} ]", String.join(", ", this.paths));

	}
}

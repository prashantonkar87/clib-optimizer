package org.clibopt.core.services.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
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
			processCSS(doc,pagePath);
			processJS(doc,pagePath);
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
		//List<String> jsClibList = new ArrayList<String>();
		//Set<String> jsClibSet = new HashSet<String>();
		while (jsIter.hasNext()) {
			Element element = jsIter.next();
			LOG.debug("Attr : " + element.attr("src"));
			if (element.attr("src").startsWith("/etc.clientlibs")) {
				//jsClibSet.addAll(getLibrary(LibraryType.JS, element.attr("src")));
				// doc.select("body").first().append(element.outerHtml());
				LOG.debug("removing");
				//element.remove();
			}
		}
		doc.select("body").first().append("<script type=\"text/javascript\" src=\""+pagePath+".html.js"+"\"></script>");
	}

	private void processCSS(Document doc, String pagePath) {
		Elements elements = doc.getElementsByAttributeValueStarting("href", "/etc.clientlibs/");
		Iterator<Element> cssIter = elements.iterator();
		//List<String> cssClibList = new ArrayList<String>();
		while (cssIter.hasNext()) {
			Element element = cssIter.next();
			LOG.debug("Attr : " + element.attr("href"));
			if (element.attr("href").startsWith("/etc.clientlibs")) {
				//cssClibList.addAll(getLibrary(LibraryType.CSS, element.attr("href")));
				LOG.debug("Element : " + element.outerHtml());
				LOG.debug("href : " + element.attr("href"));
				// doc.select("head").first().append(element.outerHtml());
				//element.remove();
			}
		}
		doc.select("head").first().append(" <link rel=\"stylesheet\" href=\""+pagePath +".html.css\" type=\"text/css\">");
	}

	private List<String> getLibrary(LibraryType type, String clibPath) {
		clibPath = clibPath.substring(15);
		if (clibPath.startsWith("/clientlibs") || (clibPath.startsWith("/foundation"))) {
			clibPath = "/libs" + clibPath;
		} else {
			clibPath = "/apps" + clibPath;
		}
		if (type.equals(LibraryType.CSS)) {
			clibPath = clibPath.substring(0, clibPath.indexOf(".css"));
		} else if (type.equals(LibraryType.JS)) {
			clibPath = clibPath.substring(0, clibPath.indexOf(".js"));
		}
		LOG.debug("processed clibpath " + clibPath);
		LOG.debug("clib " + htmlLibManager.getLibrary(type, clibPath).getName());
		if (resourceResolver == null) {
			Map<String, Object> param = new HashMap<>();
			param.put(ResourceResolverFactory.SUBSERVICE, "readClientLibs");
			try {
				resourceResolver = resourceResolverFactory.getServiceResourceResolver(param);
			} catch (LoginException e) {
				LOG.error("Unable to initialize service user " + e);
			}
		}
		LOG.debug("clib categories "
				+ resourceResolver.getResource(clibPath).getValueMap().get("categories", String[].class));

		return Arrays.asList(resourceResolver.getResource(clibPath).getValueMap().get("categories", String[].class));
	}

	@Activate
	protected void activate(OptimizerConfig config) {
		this.isOptEnabled = config.isOptEnabled();
		this.paths = Arrays.asList(config.paths());
		this.ignoredClibs = Arrays.asList(config.ignoredClibs());
		LOG.info("Activated OptimizerServiceImpl with paths [ {} ]", String.join(", ", this.paths));

	}
}

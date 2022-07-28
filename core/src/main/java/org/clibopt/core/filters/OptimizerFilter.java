package org.clibopt.core.filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.engine.EngineConstants;
import org.clibopt.core.services.OptimizerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.component.propertytypes.ServiceVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = Filter.class, property = {
		EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_REQUEST,
		EngineConstants.SLING_FILTER_EXTENSIONS + "=" + "html",
		EngineConstants.SLING_FILTER_PATTERN + "=" + "/content/.*" })
@ServiceDescription("ClientLib Optimizer Filter")
@ServiceRanking(-700)
@ServiceVendor("Clib Optimizer")
public class OptimizerFilter implements Filter {

	private static final Logger LOG = LoggerFactory.getLogger(OptimizerFilter.class);

	@Reference
	private OptimizerService optimizerService;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {

	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		CharResponseWrapper wrapper = new CharResponseWrapper((HttpServletResponse) response);
		chain.doFilter(request, wrapper);
		SlingHttpServletRequest req = (SlingHttpServletRequest) request;
		LOG.debug("req : "+req.getRequestPathInfo().getResourcePath());
		String optimizedHTML = optimizerService.optimizeClientLibs(wrapper.toString(),req.getRequestPathInfo().getResourcePath());
		response.setContentLength(optimizedHTML.length());
		response.getOutputStream().write(optimizedHTML.getBytes());
	}

	@Override
	public void destroy() {

	}

}

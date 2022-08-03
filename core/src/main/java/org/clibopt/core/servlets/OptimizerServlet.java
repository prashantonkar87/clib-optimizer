/*
 *  Copyright 2015 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.clibopt.core.servlets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.zip.GZIPOutputStream;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletName;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.clibopt.core.services.OptimizerCacheService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.ui.clientlibs.HtmlLibrary;
import com.adobe.granite.ui.clientlibs.HtmlLibraryManager;
import com.adobe.granite.ui.clientlibs.LibraryType;

@Component(service = { Servlet.class })
@SlingServletPaths(value = { "/bin/clib" })
@SlingServletName(servletName = "Clib Optimier Servlet")
@ServiceDescription("Client Library Optimizer Servlet")
public class OptimizerServlet extends SlingSafeMethodsServlet {

	private static final Logger LOG = LoggerFactory.getLogger(OptimizerServlet.class);
	private static final long serialVersionUID = 1L;

	@Reference
	private HtmlLibraryManager htmlLibManager;

	@Reference
	private OptimizerCacheService optimizerService;

	@Override
	protected void doGet(final SlingHttpServletRequest req, final SlingHttpServletResponse resp)
			throws ServletException, IOException {
		LOG.debug("Selectors : " + req.getRequestPathInfo().getSelectorString());
		LOG.debug("Extension : " + req.getRequestPathInfo().getExtension());
		String responseString = "";
		if(req.getRequestPathInfo().getSelectorString()==null || req.getRequestPathInfo().getSelectorString().isEmpty()) {
			return;
		}
		if (req.getRequestPathInfo().getExtension().equalsIgnoreCase(LibraryType.CSS.toString())) {
			resp.setContentType("text/css;charset=utf-8");
			responseString = getResponseString(LibraryType.CSS, req.getRequestPathInfo().getSelectorString());
		} else if (req.getRequestPathInfo().getExtension().equalsIgnoreCase(LibraryType.JS.toString())) {
			resp.setContentType("application/javascript;charset=utf-8");
			responseString = getResponseString(LibraryType.JS, req.getRequestPathInfo().getSelectorString());
		}
		try (PrintWriter printWriter = new PrintWriter(new GZIPOutputStream(resp.getOutputStream()))) {
			resp.setHeader("Content-Encoding", "gzip");
			resp.setContentLength(responseString.length());
			printWriter.write(responseString);
		}
	}

	private String getResponseString(LibraryType type, String clibCodeSelector) {
		String[] clibCodes = clibCodeSelector.split("-");
		StringBuilder responseString = new StringBuilder();
		for (String clibCode : clibCodes) {
			String clibPath = optimizerService.getClibPath(clibCode);
			LOG.debug("Found clib " + clibPath + " for " + clibCode);
			HtmlLibrary lib = htmlLibManager.getLibrary(type, clibPath);
			if (lib != null) {
				try {
					responseString.append("\n/*clibopt start of " + clibPath + "*/\n");
					responseString.append(getResponseStringFromInputStream(lib.getInputStream(true)));
					responseString.append("\n/*clibopt end of " + clibPath + "*/\n");
				} catch (IOException e) {
					LOG.error("Error in reading input stream ", e);
				}
			} else {
				LOG.error("Library not found for " + clibPath);
			}
		}
		return responseString.toString();

	}

	private String getResponseStringFromInputStream(InputStream inputStream) {
		int nRead;
		byte[] data = new byte[4096];
		String response = null;
		try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()){
			while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, nRead);
			}
			buffer.flush();
			response = buffer.toString();
		} catch (IOException e) {
			LOG.error("Error in reading input stream ", e);
		}
		return response;
	}
}

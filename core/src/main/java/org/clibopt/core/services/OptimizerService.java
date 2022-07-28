package org.clibopt.core.services;

public interface OptimizerService {

	abstract boolean isOptNeededForPath(String contentPath);

	abstract boolean isOptNeededForClib(String clibPath);

	abstract String optimizeClientLibs(String responseHTML, String pagePath);

}

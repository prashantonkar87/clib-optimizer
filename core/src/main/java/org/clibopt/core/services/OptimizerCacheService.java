package org.clibopt.core.services;

public interface OptimizerCacheService {

	abstract String getClibCode(String clibPath);

	abstract String getClibPath(String clibCode);
}

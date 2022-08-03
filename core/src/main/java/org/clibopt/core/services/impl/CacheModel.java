package org.clibopt.core.services.impl;

import java.util.List;

class CacheModel {
	private String clibCode;
	private String clibPath;
//	private String[] categories;

	public CacheModel(String clibPath, String clibCode/*, String[] categories*/) {
		this.clibCode = clibCode;
		this.clibPath = clibPath;
//		this.categories = categories;
	}

	public String getClibCode() {
		return clibCode;
	}

	public void setClibCode(String clibCode) {
		this.clibCode = clibCode;
	}

	public String getClibPath() {
		return clibPath;
	}

	public void setClibPath(String clibPath) {
		this.clibPath = clibPath;
	}

//	public String[] getCategories() {
//		return categories;
//	}
//
//	public void setCategories(String[] categories) {
//		this.categories = categories;
//	}

}
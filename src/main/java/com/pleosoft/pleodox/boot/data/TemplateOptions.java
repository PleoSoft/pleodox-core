package com.pleosoft.pleodox.boot.data;

import java.util.HashMap;
import java.util.Map;

public class TemplateOptions {
	private final Map<String, Object> options = new HashMap<>();

	public TemplateOptions addOption(String key, Object value) {
		options.put(key, value);
		return this;
	}

	public Object getOption(String key) {
		return options.get(key);
	}
}

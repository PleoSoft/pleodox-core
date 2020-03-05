package com.pleosoft.pleodox.boot.service;

import java.io.InputStream;
import java.io.OutputStream;

import com.pleosoft.pleodox.boot.data.DataRoot;
import com.pleosoft.pleodox.boot.data.TemplateOptions;

public interface DocumentGenerator {

	public void generate(InputStream templateStream, OutputStream os, DataRoot dataroot, TemplateOptions options) throws Exception;
	public boolean isTransformable(String templateName, DataRoot dataroot, TemplateOptions options);
}

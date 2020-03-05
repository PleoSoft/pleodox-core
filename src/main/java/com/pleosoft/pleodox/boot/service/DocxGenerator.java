package com.pleosoft.pleodox.boot.service;

import java.io.InputStream;
import java.io.OutputStream;

import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.util.StringUtils;

import com.pleosoft.pleodox.boot.data.DataRoot;
import com.pleosoft.pleodox.boot.data.TemplateOptions;

public class DocxGenerator implements DocumentGenerator {

	private final DocumentGenerateService templatingService;

	public DocxGenerator(DocumentGenerateService templatingService) {
		this.templatingService = templatingService;
	}

	@Override
	public void generate(InputStream templateStream, OutputStream os, DataRoot dataroot, TemplateOptions options)
			throws Exception {
		WordprocessingMLPackage wordMLPackage = Docx4J.load(templateStream);

		templatingService.generateWord(wordMLPackage, dataroot, os,
				(Boolean) options.getOption("readOnly"), (String) options.getOption("protectionPass"));
	}
	
	@Override
	public boolean isTransformable(String templateName, DataRoot dataroot, TemplateOptions options) {
		String filenameExtension = StringUtils.getFilenameExtension(templateName).toUpperCase();
		return "DOCX".equals(filenameExtension) || "DOTX".equals(filenameExtension);
	}
}

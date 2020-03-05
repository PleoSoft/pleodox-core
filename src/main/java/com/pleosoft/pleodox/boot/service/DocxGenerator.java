/**
 * Copyright 2019 Pleo Soft d.o.o. (pleosoft.com)

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

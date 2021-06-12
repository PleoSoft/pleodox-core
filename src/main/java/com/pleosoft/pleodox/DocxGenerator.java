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

package com.pleosoft.pleodox;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.docx4j.Docx4J;
import org.docx4j.openpackaging.contenttype.ContentTypeManager;
import org.docx4j.openpackaging.contenttype.ContentTypes;
import org.docx4j.openpackaging.packages.ProtectDocument;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.STDocProtect;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.pleosoft.pleodox.data.DataRoot;
import com.pleosoft.pleodox.data.TemplateOptions;

public class DocxGenerator implements DocumentGenerator {

	private final XmlMapper xmlMapper;

	public DocxGenerator() {
		this.xmlMapper = new XmlMapper();
	}

	@Override
	public void generate(InputStream templateStream, OutputStream os, DataRoot dataroot, TemplateOptions options,
			int flags) throws Exception {
		WordprocessingMLPackage wordMLPackage = Docx4J.load(templateStream);

		try (InputStream xmlStreamTmp = new ByteArrayInputStream(getDataRootAsString(dataroot).getBytes())) {
			Docx4J.bind(wordMLPackage, xmlStreamTmp, flags);
		}

		if (Boolean.TRUE.equals((Boolean) options.getOption("readOnly"))) {
			final ProtectDocument pd = new ProtectDocument(wordMLPackage);
			pd.restrictEditing(STDocProtect.READ_ONLY, (String) options.getOption("protectionPass"));
		}

		try {
			ContentTypeManager ctm = wordMLPackage.getContentTypeManager();
			ctm.addOverrideContentType(new URI("/word/document.xml"), ContentTypes.WORDPROCESSINGML_DOCUMENT);

			Docx4J.save(wordMLPackage, os, Docx4J.FLAG_NONE);
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}

	}

	@Override
	public boolean isTransformable(String templateName, DataRoot dataroot, TemplateOptions options) {
		String filenameExtension = StringUtils.getFilenameExtension(templateName).toUpperCase();
		return "DOCX".equals(filenameExtension);
	}

	@Override
	public boolean isImageHandledAsBase64() {
		return true;
	}

	private String getDataRootAsString(DataRoot dataroot) throws JsonProcessingException {
		Map<String, Object> data = dataroot.getData();
		StringBuilder stringBuilder = new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><TestXMLNode xmlns=\"")
				.append(dataroot.getXmlns()).append("\">");

		if (!data.isEmpty()) {
			String val = xmlMapper.writer().writeValueAsString(data);
			int max = val.length() - 10;
			stringBuilder.append(val.substring(9, max));
		}

		stringBuilder.append("</TestXMLNode>");
		return stringBuilder.toString();
	}
}

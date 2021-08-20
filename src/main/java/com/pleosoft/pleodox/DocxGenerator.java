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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.docx4j.Docx4J;
import org.docx4j.model.datastorage.CustomXmlDataStorage;
import org.docx4j.openpackaging.contenttype.ContentTypeManager;
import org.docx4j.openpackaging.contenttype.ContentTypes;
import org.docx4j.openpackaging.packages.ProtectDocument;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.CustomXmlDataStoragePart;
import org.docx4j.openpackaging.parts.CustomXmlPart;
import org.docx4j.wml.STDocProtect;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.underscore.lodash.U;
import com.pleosoft.pleodox.data.PleodoxRoot;
import com.pleosoft.pleodox.data.PleodoxRoot.PleodoxRequest;
import com.pleosoft.pleodox.data.TemplateOptions;

public class DocxGenerator implements DocumentGenerator {

	private final ObjectMapper objectMapper;

	public DocxGenerator() {
		this.objectMapper = new ObjectMapper();
		this.objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
		this.objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
		this.objectMapper.enable(SerializationFeature.WRAP_ROOT_VALUE);
	}

	@Override
	public void generate(InputStream templateStream, OutputStream os, PleodoxRequest dataroot, TemplateOptions options)
			throws Exception {

//		int flags = TemplateOutputFormat.DOCX.equals(format)
//				? Docx4J.FLAG_BIND_INSERT_XML | Docx4J.FLAG_BIND_BIND_XML
//				: Docx4J.FLAG_BIND_INSERT_XML | Docx4J.FLAG_BIND_BIND_XML | Docx4J.FLAG_BIND_REMOVE_SDT;

		WordprocessingMLPackage wordMLPackage = Docx4J.load(templateStream);

		Map<String, Pair<String, PleodoxRoot>> collected = dataroot != null
				? dataroot.getPleodox().entrySet().stream()
						.collect(Collectors.toMap(e -> e.getValue().getXmlns(), e -> Pair.of(e.getKey(), e.getValue())))
				: Collections.emptyMap();

		HashMap<String, CustomXmlPart> customXmlDataStorageParts = wordMLPackage.getCustomXmlDataStorageParts();
		customXmlDataStorageParts.entrySet().forEach(entry -> {
			try {
				CustomXmlPart xmlPart = entry.getValue();
				if (xmlPart instanceof CustomXmlDataStoragePart) {
					CustomXmlDataStorage customXmlDataStorage = ((CustomXmlDataStoragePart) xmlPart).getData();
					
					String xpathGetString = customXmlDataStorage.cachedXPathGetString("/@xmlns", null);
					String xml = customXmlDataStorage.getXML();
					String json = U.xmlToJson(xml);

					PleodoxRoot currentPleodox = objectMapper.readValue(json, new TypeReference<PleodoxRoot>() {
					});

					if (currentPleodox != null) {
						PleodoxRoot pleodoxRoot = currentPleodox;
						String xmlns = pleodoxRoot.getXmlns();
						if (StringUtils.hasText(xmlns)) {
							Pair<String, PleodoxRoot> newPleodox = collected.get(xmlns);
							if (newPleodox != null) {

								String asString = objectMapper.writer().withRootName(newPleodox.getLeft())
										.writeValueAsString(newPleodox.getRight());
								String toXml = U.jsonToXml(asString);

								// TODO charsets might cause issues in multi lingual environments
//							InputStream is = new ByteArrayInputStream(toXml.getBytes());
//
//							CustomXmlDataStorage data = new CustomXmlDataStorageImpl();
//							data.setDocument(is);
//							((CustomXmlDataStoragePart) xmlPart).setData(data);

								Docx4J.bind(wordMLPackage, toXml,
										Docx4J.FLAG_BIND_INSERT_XML | Docx4J.FLAG_BIND_BIND_XML);
							}
						}
					}
				}
			} catch (Throwable e) {
				throw new IllegalStateException(e);
			}
		});

		if (Boolean.TRUE.equals((Boolean) options.getOption("readOnly"))) {
			final ProtectDocument pd = new ProtectDocument(wordMLPackage);
			pd.restrictEditing(STDocProtect.READ_ONLY, (String) options.getOption("protectionPass"));
		}

		ContentTypeManager ctm = wordMLPackage.getContentTypeManager();
		ctm.addOverrideContentType(URI.create("/word/document.xml"), ContentTypes.WORDPROCESSINGML_DOCUMENT);

		Docx4J.save(wordMLPackage, os, Docx4J.FLAG_NONE);
	}

	@Override
	public boolean isTransformable(String templateName, PleodoxRequest dataroot, TemplateOptions options) {
		String filenameExtension = StringUtils.getFilenameExtension(templateName).toUpperCase();
		return "DOCX".equals(filenameExtension);
	}

	@Override
	public boolean isImageHandledAsBase64() {
		return true;
	}
}

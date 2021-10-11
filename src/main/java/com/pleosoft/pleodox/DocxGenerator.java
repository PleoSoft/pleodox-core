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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.docx4j.Docx4J;
import org.docx4j.events.EventFinished;
import org.docx4j.events.StartEvent;
import org.docx4j.events.WellKnownJobTypes;
import org.docx4j.events.WellKnownProcessSteps;
import org.docx4j.model.datastorage.BindingHandler;
import org.docx4j.model.datastorage.CustomXmlDataStorage;
import org.docx4j.model.datastorage.CustomXmlDataStorageImpl;
import org.docx4j.model.datastorage.DomToXPathMap;
import org.docx4j.model.datastorage.OpenDoPEHandler;
import org.docx4j.model.datastorage.OpenDoPEIntegrity;
import org.docx4j.model.datastorage.OpenDoPEIntegrityAfterBinding;
import org.docx4j.openpackaging.contenttype.ContentTypeManager;
import org.docx4j.openpackaging.contenttype.ContentTypes;
import org.docx4j.openpackaging.io3.Load3;
import org.docx4j.openpackaging.io3.stores.ZipPartStore;
import org.docx4j.openpackaging.packages.ProtectDocument;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.CustomXmlDataStoragePart;
import org.docx4j.openpackaging.parts.CustomXmlPart;
import org.docx4j.wml.STDocProtect;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.underscore.lodash.U;
import com.pleosoft.pleodox.data.ObjectMerger;
import com.pleosoft.pleodox.data.PleodoxRoot;
import com.pleosoft.pleodox.data.PleodoxRoot.PleodoxRequest;
import com.pleosoft.pleodox.data.TemplateOptions;

public class DocxGenerator implements TemplateGenerator {

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

		if (customXmlDataStorageParts != null) {
			StartEvent bindJobStartEvent = new StartEvent(WellKnownJobTypes.BIND, wordMLPackage);
			bindJobStartEvent.publish();

			customXmlDataStorageParts.entrySet().forEach(entry -> {
				try {
					CustomXmlPart xmlPart = entry.getValue();
					if (xmlPart instanceof CustomXmlDataStoragePart) {
						CustomXmlDataStorage customXmlDataStorage = ((CustomXmlDataStoragePart) xmlPart).getData();

						String xmlns = customXmlDataStorage.getDocument().lookupNamespaceURI(null);
						String rootElement = customXmlDataStorage.cachedXPathGetString("name(/*[1])", null);

						if (StringUtils.hasText(xmlns)) {
							Pair<String, PleodoxRoot> newPleodox = collected.get(xmlns);
							if (newPleodox != null && newPleodox.getLeft().equals(rootElement)) {

								PleodoxRoot pleodox = newPleodox.getRight();
								if (Boolean.TRUE.equals((Boolean) options.getOption("mergeDefaults"))) {
									String xml = customXmlDataStorage.getXML();
									String defaults = U.xmlToJson(xml);
									PleodoxRoot defaultsPleodox = objectMapper.readValue(defaults,
											new TypeReference<PleodoxRoot>() {
											});

									JsonNode jsonDefaults = objectMapper.valueToTree(defaultsPleodox);
									JsonNode jsonRequest = objectMapper.valueToTree(newPleodox.getRight());

									ObjectMerger.merge(jsonRequest, jsonDefaults);
									pleodox = objectMapper.treeToValue(jsonRequest, PleodoxRoot.class);
								}

								String asString = objectMapper.writer().withRootName(newPleodox.getLeft())
										.writeValueAsString(pleodox);
								String toXml = U.jsonToXml(asString);

								StartEvent startEvent = new StartEvent(WellKnownJobTypes.BIND, wordMLPackage,
										WellKnownProcessSteps.BIND_INSERT_XML);
								startEvent.publish();

								// TODO charsets might cause issues in multi lingual environments
								try (InputStream is = new ByteArrayInputStream(toXml.getBytes())) {
									CustomXmlDataStorage data = new CustomXmlDataStorageImpl();
									data.setDocument(is);
									// ((CustomXmlDataStoragePart) xmlPart).setData(data);
									((CustomXmlDataStoragePart) xmlPart).setXML(data.getDocument());
								}

								new EventFinished(startEvent).publish();

//								Docx4J.bind(wordMLPackage, toXml,
//										Docx4J.FLAG_BIND_INSERT_XML | Docx4J.FLAG_BIND_BIND_XML);
							}
						}
					}
				} catch (Throwable e) {
					throw new IllegalStateException(e);
				}
			});

			StartEvent startEvent = new StartEvent(WellKnownJobTypes.BIND, wordMLPackage,
					WellKnownProcessSteps.BIND_BIND_XML_OpenDoPEHandler);
			startEvent.publish();

			WordprocessingMLPackage tmpMergeResult = wordMLPackage;

			OpenDoPEHandler openDoPEHandler = new OpenDoPEHandler(tmpMergeResult);
			tmpMergeResult = openDoPEHandler.preprocess();

			DomToXPathMap domToXPathMap = openDoPEHandler.getDomToXPathMap();

			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			Docx4J.save(tmpMergeResult, outStream);

			final ZipPartStore partLoader = new ZipPartStore(new ByteArrayInputStream(outStream.toByteArray()));
			final Load3 loader = new Load3(partLoader);
			loader.reuseExistingOpcPackage(wordMLPackage);
			loader.get();

			new EventFinished(startEvent).publish();

			startEvent = new StartEvent(WellKnownJobTypes.BIND, wordMLPackage,
					WellKnownProcessSteps.BIND_BIND_XML_OpenDoPEIntegrity);
			startEvent.publish();

			// since 3.3.2
			OpenDoPEIntegrity odi = new OpenDoPEIntegrity();
			odi.process(wordMLPackage);

			new EventFinished(startEvent).publish();

			startEvent = new StartEvent(WellKnownJobTypes.BIND, wordMLPackage,
					WellKnownProcessSteps.BIND_BIND_XML_BindingHandler);
			startEvent.publish();

			BindingHandler bh = new BindingHandler(wordMLPackage);
			bh.setStartingIdForNewBookmarks(openDoPEHandler.getNextBookmarkId());
			bh.setDomToXPathMap(domToXPathMap);
			bh.applyBindings();

			new EventFinished(startEvent).publish();

			startEvent = new StartEvent(WellKnownJobTypes.BIND, wordMLPackage,
					WellKnownProcessSteps.BIND_BIND_XML_OpenDoPEIntegrityAfterBinding);
			startEvent.publish();

			OpenDoPEIntegrityAfterBinding odiab = new OpenDoPEIntegrityAfterBinding();
			odiab.process(wordMLPackage);

			new EventFinished(startEvent).publish();

			new EventFinished(bindJobStartEvent).publish();
		}

		if (Boolean.TRUE.equals((Boolean) options.getOption("readOnly"))) {
			final ProtectDocument pd = new ProtectDocument(wordMLPackage);
			pd.restrictEditing(STDocProtect.READ_ONLY, (String) options.getOption("protectionPass"));
		}

		ContentTypeManager ctm = wordMLPackage.getContentTypeManager();
		ctm.addOverrideContentType(URI.create("/word/document.xml"), ContentTypes.WORDPROCESSINGML_DOCUMENT);

		Docx4J.save(wordMLPackage, os, Docx4J.FLAG_NONE);
	}

	@Override
	public boolean isTransformable(String templateName, TemplateOptions options) {
		String filenameExtension = StringUtils.getFilenameExtension(templateName).toUpperCase();
		return "DOCX".equals(filenameExtension);
	}

//	@Override
//	public List<PleodoxRoot> getPleodoxRoots(InputStream templateStream) throws Exception {
//
//		WordprocessingMLPackage wordMLPackage = Docx4J.load(templateStream);
//
//		HashMap<String, CustomXmlPart> customXmlDataStorageParts = wordMLPackage.getCustomXmlDataStorageParts();
//
//		List<PleodoxRoot> roots = new ArrayList<>();
//		if (customXmlDataStorageParts != null) {
//			StartEvent bindJobStartEvent = new StartEvent(WellKnownJobTypes.BIND, wordMLPackage);
//			bindJobStartEvent.publish();
//
//			customXmlDataStorageParts.entrySet().forEach(entry -> {
//				try {
//					CustomXmlPart xmlPart = entry.getValue();
//					if (xmlPart instanceof CustomXmlDataStoragePart) {
//						CustomXmlDataStorage customXmlDataStorage = ((CustomXmlDataStoragePart) xmlPart).getData();
//
//						String xmlns = customXmlDataStorage.getDocument().lookupNamespaceURI(null);
//						String rootElement = customXmlDataStorage.cachedXPathGetString("name(/*[1])", null);
//
//						if (StringUtils.hasText(xmlns)) {
//							String xml = customXmlDataStorage.getXML();
//							String defaults = U.xmlToJson(xml);
//
//							Map<String, Object> map = objectMapper.readValue(defaults,
//									new TypeReference<Map<String, Object>>() {
//									});
//
//							Object object = map.get(rootElement);
//							PleodoxRoot defaultsPleodox = objectMapper.convertValue(object,
//									new TypeReference<PleodoxRoot>() {
//									});
//
//							roots.add(defaultsPleodox);
//						}
//					}
//				} catch (Throwable e) {
//					throw new IllegalStateException(e);
//				}
//			});
//		}
//
//		return roots;
//	}
//
//	@Override
//	public void addPleodoxRoot(InputStream templateStream, PleodoxRoot pleodox, String rootName) throws Exception {
//		String asString = objectMapper.writer().withRootName(rootName).writeValueAsString(pleodox);
//		String toXml = U.jsonToXml(asString);
//
//		WordprocessingMLPackage wordMLPackage = Docx4J.load(templateStream);
//		HashMap<String, CustomXmlPart> customXmlDataStorageParts = wordMLPackage.getCustomXmlDataStorageParts();
//		Docx4J.bind(wordMLPackage, toXml, Docx4J.FLAG_BIND_INSERT_XML | Docx4J.FLAG_BIND_BIND_XML);
//	}
}

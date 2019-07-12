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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.docx4j.Docx4J;
import org.docx4j.model.datastorage.CustomXmlDataStorage;
import org.docx4j.model.datastorage.CustomXmlDataStoragePartSelector;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.ProtectDocument;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.CustomXmlDataStoragePart;
import org.docx4j.openpackaging.parts.CustomXmlPart;
import org.docx4j.wml.STDocProtect;
import org.springframework.integration.transformer.ObjectToMapTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.pleosoft.pleodox.boot.data.DataRoot;
import com.pleosoft.pleodox.boot.data.MultivalueJsonNodeDeserializer;
import com.pleosoft.pleodox.boot.data.PlaceholdersData;

public class DocumentGenerateService {

	private static final String UNKNOWN_STRING = "-";

	private final XmlMapper xmlMapper;
	private final ObjectToMapTransformer transformer;

	public DocumentGenerateService() {
		this.xmlMapper = new XmlMapper();

		xmlMapper.registerModule(
				new SimpleModule().addDeserializer(JsonNode.class, new MultivalueJsonNodeDeserializer()));

		transformer = new ObjectToMapTransformer();
		transformer.setShouldFlattenKeys(false);
	}

	public InputStream getDataRootInputStream(DataRoot dataroot) throws JsonProcessingException {
		return new ByteArrayInputStream(getDataRootAsString(dataroot).getBytes());
	}

	// TODO find a better way to use jackson (tried many things but it does
	// not work as we need in this case)
	// maybe use a regexp instead of substings?
	public String getDataRootAsString(DataRoot dataroot) throws JsonProcessingException {
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

	public Pair<JsonNode, CustomXmlDataStorage> getPleodoxCustomXmlPart(WordprocessingMLPackage wordMLPackage) {
		try {
			CustomXmlPart xmlPart = CustomXmlDataStoragePartSelector.getCustomXmlDataStoragePart(wordMLPackage);
			if (!(xmlPart instanceof CustomXmlDataStoragePart)) {
				throw new RuntimeException("No custom part defined");
			}

			CustomXmlDataStorage customXmlDataStorage = ((CustomXmlDataStoragePart) xmlPart).getData();
			String xml = customXmlDataStorage.getXML();
			JsonNode readTree = xmlMapper.readTree(xml);
			JsonNode rootname = readTree.get("rootname");
			JsonNode namespace = readTree.get("namespace");
			if (rootname != null && "TESTXMLNODE".equals(rootname.asText().toUpperCase()) && namespace != null
					&& "PLEODOX".equals(namespace.asText().toUpperCase())) {

				return Pair.of(readTree, customXmlDataStorage);
			}
		} catch (Exception e) {
			throw new RuntimeException("Issue while reading the template document!", e);
		}

		throw new RuntimeException("No PLEODOX custom xml part found!");
	}

	protected void generateWord(WordprocessingMLPackage wordMLPackage,
			Pair<JsonNode, CustomXmlDataStorage> customXmlPart, DataRoot dataroot, OutputStream os, Boolean readOnly,
			String protectionPassword) throws IOException, Docx4JException {

		dataroot.setXmlns("PLEODOX");

		PlaceholdersData holderData = jonNodeToPlaceholdersData(customXmlPart.getLeft());
		Set<String> templateKeys = holderData.getKeys();

		Map<String, Object> data = dataroot.getData();
		PlaceholdersData newData = mapToPlaceHoldersData(null, data);
		Set<String> newKeys = newData.getKeys();
		newKeys.retainAll(templateKeys);

		for (String templateKey : templateKeys) {
			Map<String, Object> tmpMap = data;
			String[] keys = StringUtils.delimitedListToStringArray(templateKey, ".");
			for (String k : keys) {
				Object object = tmpMap.get(k);
				if (object instanceof Map) {
					tmpMap = (Map<String, Object>) object;
				}
			}

			if (!newKeys.contains(templateKey)) {
				Set<String> tableColumns = holderData.getTableColumns(templateKey);
				if (tableColumns != null) {
					HashMap<String, String> tableEntry = new HashMap<>();
					for (String column : tableColumns) {
						tableEntry.put(column, UNKNOWN_STRING);
					}
					tmpMap.put(keys[keys.length - 1], tableEntry);
				} else {
					tmpMap.put(keys[keys.length - 1], UNKNOWN_STRING);
				}
			} else {
				Object value = tmpMap.get(keys[keys.length - 1]);
				if (ObjectUtils.isEmpty(value)) {
					Set<String> tableColumns = holderData.getTableColumns(templateKey);
					if (tableColumns != null) {
						HashMap<String, String> tableEntry = new HashMap<>();
						for (String column : tableColumns) {
							tableEntry.put(column, UNKNOWN_STRING);
						}
						tmpMap.put(keys[keys.length - 1], tableEntry);
					} else {
						tmpMap.put(keys[keys.length - 1], UNKNOWN_STRING);
					}
				}
			}
		}

		if (Boolean.TRUE.equals(readOnly)) {
			final ProtectDocument pd = new ProtectDocument(wordMLPackage);
			pd.restrictEditing(STDocProtect.READ_ONLY, protectionPassword);
		}

		try (InputStream xmlStreamTmp = getDataRootInputStream(dataroot)) {
			Docx4J.bind(wordMLPackage, xmlStreamTmp,
					Docx4J.FLAG_BIND_INSERT_XML | Docx4J.FLAG_BIND_BIND_XML | Docx4J.FLAG_BIND_REMOVE_SDT);
		}
		Docx4J.save(wordMLPackage, os, Docx4J.FLAG_NONE);
	}

	@SuppressWarnings("unchecked")
	public PlaceholdersData jonNodeToPlaceholdersData(JsonNode readTree) {

		Message<JsonNode> message = new GenericMessage<>(readTree.get("data"));
		Map<String, Object> payload = (Map<String, Object>) transformer.transform(message).getPayload();

		final PlaceholdersData placeholdersData = mapToPlaceHoldersData(null, payload);

		return placeholdersData;
	}

	private PlaceholdersData mapToPlaceHoldersData(String parentKey, Map<String, Object> map) {
		Set<String> fields = new HashSet<>();
		Map<String, Set<String>> tables = new HashMap<>();

		flattenMap(parentKey, map, fields, tables);

		final PlaceholdersData placeholdersData = new PlaceholdersData();
		placeholdersData.setFields(fields);
		placeholdersData.setTables(tables);

		return placeholdersData;
	}

	@SuppressWarnings("unchecked")
	private void flattenMap(String parentKey, Map<String, Object> map, Set<String> fields,
			Map<String, Set<String>> tables) {
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String key = entry.getKey();
			String compositeKey = parentKey != null ? parentKey + "." + key : key;

			Object value = entry.getValue();
			if (value instanceof Map) {
				flattenMap(compositeKey, (Map<String, Object>) value, fields, tables);
			} else if (value instanceof List<?>) {
				for (Object obj : (List<?>) value) {
					if (obj instanceof Map) {
						Set<String> keySet = ((Map<String, ?>) obj).keySet();
						tables.put(compositeKey, keySet);
					} else {
						fields.add(compositeKey);
					}
				}
			} else {
				fields.add(compositeKey);
			}
		}
	}
}

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

package com.pleosoft.pleodox.boot.data;

import java.io.IOException;

import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.JsonNodeDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;

public class MultivalueJsonNodeDeserializer extends JsonNodeDeserializer {

	@Override
	protected void _handleDuplicateField(JsonParser p, DeserializationContext ctxt, JsonNodeFactory nodeFactory,
			String fieldName, ObjectNode objectNode, JsonNode oldValue, JsonNode newValue)
			throws JsonProcessingException {
		ArrayNode node;
		if (oldValue instanceof ArrayNode) {
			node = (ArrayNode) oldValue;
			node.add(newValue);
		} else {
			node = nodeFactory.arrayNode();
			node.add(oldValue);
			node.add(newValue);
		}
		objectNode.set(fieldName, node);
	}

	@Override
	public JsonNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		XMLStreamReader reader = ((FromXmlParser) p).getStaxReader();
		String rootName = reader.getLocalName();
		String ns = reader.getNamespaceURI();
		ObjectNode objectNode = ctxt.getNodeFactory().objectNode();

		objectNode.set("rootname", new TextNode(rootName));
		objectNode.set("namespace", new TextNode(ns));
		return objectNode.set("data", super.deserialize(p, ctxt));
	}
}

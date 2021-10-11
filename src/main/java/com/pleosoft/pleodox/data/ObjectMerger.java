package com.pleosoft.pleodox.data;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;

public class ObjectMerger {

	/**
	 * Merge two JSON tree into one i.e mergedInTo.
	 *
	 * @param toBeMerged
	 * @param mergedInTo
	 */
	public static void merge(JsonNode toBeMerged, JsonNode mergedInTo) {
	    Iterator<Map.Entry<String, JsonNode>> incomingFieldsIterator = toBeMerged.fields();
	    Iterator<Map.Entry<String, JsonNode>> mergedIterator = mergedInTo.fields();

	    while (incomingFieldsIterator.hasNext()) {
	        Map.Entry<String, JsonNode> incomingEntry = incomingFieldsIterator.next();

	        JsonNode subNode = incomingEntry.getValue();

	        if (subNode.getNodeType().equals(JsonNodeType.OBJECT)) {
	            boolean isNewBlock = true;
	            mergedIterator = mergedInTo.fields();
	            while (mergedIterator.hasNext()) {
	                Map.Entry<String, JsonNode> entry = mergedIterator.next();
	                if (entry.getKey().equals(incomingEntry.getKey())) {
	                    merge(incomingEntry.getValue(), entry.getValue());
	                    isNewBlock = false;
	                }
	            }
	            if (isNewBlock) {
	                ((ObjectNode) mergedInTo).replace(incomingEntry.getKey(), incomingEntry.getValue());
	            }
	        } else if (subNode.getNodeType().equals(JsonNodeType.ARRAY)) {
	            boolean newEntry = true;
	            mergedIterator = mergedInTo.fields();
	            while (mergedIterator.hasNext()) {
	                Map.Entry<String, JsonNode> entry = mergedIterator.next();
	                if (entry.getKey().equals(incomingEntry.getKey())) {
	                    updateArray(incomingEntry.getValue(), entry);
	                    newEntry = false;
	                }
	            }
	            if (newEntry) {
	                ((ObjectNode) mergedInTo).replace(incomingEntry.getKey(), incomingEntry.getValue());
	            }
	        }
	        ValueNode valueNode = null;
	        JsonNode incomingValueNode = incomingEntry.getValue();
	        switch (subNode.getNodeType()) {
	            case STRING:
	                valueNode = new TextNode(incomingValueNode.textValue());
	                break;
	            case NUMBER:
	                valueNode = new IntNode(incomingValueNode.intValue());
	                break;
	            case BOOLEAN:
	                valueNode = BooleanNode.valueOf(incomingValueNode.booleanValue());
	        }
	        if (valueNode != null) {
	            updateObject(mergedInTo, valueNode, incomingEntry);
	        }
	    }
	}

	private static void updateArray(JsonNode valueToBePlaced, Map.Entry<String, JsonNode> toBeMerged) {
	    toBeMerged.setValue(valueToBePlaced);
	}

	private static void updateObject(JsonNode mergeInTo, ValueNode valueToBePlaced,
	                                 Map.Entry<String, JsonNode> toBeMerged) {
	    boolean newEntry = true;
	    Iterator<Map.Entry<String, JsonNode>> mergedIterator = mergeInTo.fields();
	    while (mergedIterator.hasNext()) {
	        Map.Entry<String, JsonNode> entry = mergedIterator.next();
	        if (entry.getKey().equals(toBeMerged.getKey())) {
	            newEntry = false;
	            entry.setValue(valueToBePlaced);
	        }
	    }
	    if (newEntry) {
	        ((ObjectNode) mergeInTo).replace(toBeMerged.getKey(), toBeMerged.getValue());
	    }
	}
}

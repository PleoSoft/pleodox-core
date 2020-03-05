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

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlaceholdersData implements Serializable {

	private final Set<String> fields = new HashSet<>();
	private final Map<String, Set<String>> tables = new HashMap<>();

	public Set<String> getFields() {
		return fields;
	}

	public void setFields(Set<String> fields) {
		this.fields.addAll(fields);
	}

	public Map<String, Set<String>> getTables() {
		return tables;
	}

	public void setTables(Map<String, Set<String>> tables) {
		this.tables.putAll(tables);
	}

	public boolean isEmpty() {
		return fields.isEmpty() && tables.isEmpty();
	}

	public boolean hasOnlyFields() {
		return !fields.isEmpty() && tables.isEmpty();
	}

	public boolean removeData(String fieldOrTable) {
		return fields.remove(fieldOrTable) || tables.remove(fieldOrTable) != null;
	}

	public Set<String> getKeys() {
		Set<String> keys = new HashSet<>(fields);
		keys.addAll(tables.keySet());
		return keys;
	}

	public boolean isField(String fieldName) {
		return fields.contains(fieldName);
	}

	public Set<String> getTableColumns(String fieldName) {
		return tables.get(fieldName);
	}
}

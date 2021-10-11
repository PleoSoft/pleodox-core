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

package com.pleosoft.pleodox.data;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PleodoxRoot implements Serializable {

	@JsonProperty("-xmlns")
	@JsonAlias("_xmlns")
	private final String xmlns;

	@JsonMerge
	final Map<String, Object> anyData = new HashMap<>();

	@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
	public PleodoxRoot(@JsonProperty(value = "-xmlns") String xmlns) {
		this.xmlns = xmlns;
	}

	private List<Barcode> barcodes;

	public void setBarcodes(List<Barcode> barcodes) {
		this.barcodes = barcodes;
	}

	public List<Barcode> getBarcodes() {
		return barcodes;
	}

	@JsonAnyGetter
	public Map<String, Object> getData() {
		return anyData;
	}

	@JsonAnySetter
	public void addData(String key, Object value) {
		anyData.put(key, value);
	}

	public String getXmlns() {
		return xmlns;
	}

	public static enum BarcodeType {
		PDF417(StandardCharsets.UTF_8, lookupCharset("Cp437")), QRCODE(StandardCharsets.UTF_8, StandardCharsets.UTF_8);

		private final Charset charsetIn;
		private final Charset charsetOut;

		private BarcodeType(Charset charsetIn, Charset charsetOut) {
			this.charsetIn = charsetIn;
			this.charsetOut = charsetOut;
		}

		public Charset getCharsetIn() {
			return charsetIn;
		}

		public Charset getCharsetOut() {
			return charsetOut;
		}
	}

	public static Charset lookupCharset(String csn) {
		if (Charset.isSupported(csn)) {
			try {
				return Charset.forName(csn);
			} catch (UnsupportedCharsetException x) {
				throw new Error(x);
			}
		}
		return null;
	}

	public static class Barcode {

		private final String id;
		private final BarcodeType type;
		private final String text;

		@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
		public Barcode(@JsonProperty(value = "id") String id,
				@JsonProperty(value = "type", required = false, defaultValue = "PDF417") BarcodeType type,
				@JsonProperty(value = "text") String text) {
			this.id = id;
			this.type = type != null ? type : BarcodeType.PDF417;
			this.text = text;
		}

		public String getId() {
			return id;
		}

		public BarcodeType getType() {
			return type;
		}

		public String getText() {
			return text;
		}
	}

	public static class PleodoxRequest {
		private Map<String, PleodoxRoot> pleodox;

		final Map<String, Object> anyData = new HashMap<>();

		@JsonAnyGetter
		public Map<String, Object> getAnyData() {
			return anyData;
		}

		@JsonAnySetter
		public void addAnyData(String key, Object value) {
			anyData.put(key, value);
		}

		public Map<String, PleodoxRoot> getPleodox() {
			return pleodox != null ? pleodox : Collections.emptyMap();
		}

		public void setPleodox(Map<String, PleodoxRoot> pleodox) {
			this.pleodox = pleodox;
		}
	}
}

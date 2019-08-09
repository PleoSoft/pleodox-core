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

import java.io.File;
import java.nio.file.Path;

import org.apache.commons.io.FilenameUtils;
import org.jodconverter.DocumentConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.pleosoft.pleodox.boot.data.TemplateOutputFormat;

public class TransformationService {

	private static final Logger LOG = LoggerFactory.getLogger(TransformationService.class);

	private final DocumentConverter converter;

	public TransformationService(final DocumentConverter converter) {
		this.converter = converter;
	}

	public Path transform(File sourceDocument, TemplateOutputFormat outputFormat) {
		Assert.notNull(sourceDocument, "sourceDocument parameter cannot be empty");
		Assert.notNull(outputFormat, "outputFormat parameter cannot be empty");

		try {
			File pdfFile = sourceDocument.toPath()
					.resolveSibling(FilenameUtils.getBaseName(sourceDocument.getName()) + outputFormat.getExtension())
					.toFile();

			converter.convert(sourceDocument).to(pdfFile).execute();

			if (!sourceDocument.delete() && LOG.isWarnEnabled()) {
				LOG.warn("Failed to delete File '" + sourceDocument + "'");
			}
			return pdfFile.toPath();

		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}

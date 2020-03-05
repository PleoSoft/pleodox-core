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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipException;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.PDFMergerUtility.DocumentMergeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.zip.transformer.ZipTransformer;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.pleosoft.pleodox.boot.data.DataRoot;
import com.pleosoft.pleodox.boot.data.TemplateOptions;
import com.pleosoft.pleodox.boot.data.TemplateOutputFormat;
import com.pleosoft.pleodox.boot.storage.StorageService;

public class TemplatesService {

	private static final Logger LOG = LoggerFactory.getLogger(TemplatesService.class);

	private final DocumentGenerateService templatingService;
	private final StorageService storageService;
	private final TransformationService transformationService;
	private final ZipTransformer zipTransformer;
	private final DocumentGenerationHandler documentGenerationHandler;
	private final List<DocumentGenerator> documentGenerators;

	public TemplatesService(DocumentGenerateService templatingService, StorageService storageService,
			TransformationService transformationService, ZipTransformer zipTransformer,
			DocumentGenerationHandler documentGenerationHandler, List<DocumentGenerator> documentGenerators) {
		this.templatingService = templatingService;
		this.storageService = storageService;
		this.transformationService = transformationService;
		this.zipTransformer = zipTransformer;
		this.documentGenerationHandler = documentGenerationHandler;
		this.documentGenerators = documentGenerators;
	}

	private final Path generateDocument(DataRoot request, String folderName, String templateName,
			TemplateOutputFormat format, Boolean readOnly, String protectionPass, String namePrefix)
			throws FileNotFoundException, IOException {

		Assert.notNull(request, "request parameter cnanot be empty");
		Assert.hasText(folderName, "folderName parameter cnanot be empty");
		Assert.hasText(templateName, "templateName parameter cnanot be empty");
		Assert.notNull(format, "format parameter cnanot be empty");

		Path tempResource = null;
		try {
			documentGenerationHandler.beforeDocumentGenerated();

			String cleanTemplatePath = StringUtils.cleanPath(templateName);
			final Path resource = storageService.loadExistingTemplate(cleanTemplatePath);
			String filename = cleanTemplatePath.replaceAll("/", "_");

			// find the first applicable generator
			DocumentGenerator applicableGenerator = null;
			TemplateOptions templateOptions = new TemplateOptions().addOption("readOnly", readOnly)
					.addOption("protectionPass", protectionPass).addOption("templatename", cleanTemplatePath);
			for (DocumentGenerator generator : documentGenerators) {
				if (generator.isTransformable(templateName, request, templateOptions)) {
					applicableGenerator = generator;
					break;
				}
			}

			if (applicableGenerator == null) {
				throw new TemplateFailedException(new Exception("There is no document generator applicable"));
			}

			if (!StringUtils.hasText(folderName)) {
				folderName = UUID.randomUUID().toString();
			}

			// TODO check if we can get rid of streams of the same file
			try (InputStream is = Files.newInputStream(resource)) {
				final String finalName = namePrefix == null ? folderName + File.separator + filename
						: folderName + File.separator + namePrefix + "-" + filename;
				tempResource = storageService.storeTemporary(is, finalName);

				try (InputStream tplStream = Files.newInputStream(tempResource)) {
					try (OutputStream os = Files.newOutputStream(tempResource)) {
						try (InputStream templateStream = Files.newInputStream(resource)) {
							applicableGenerator.generate(templateStream, os, request, templateOptions);
						}
					}

					if (!TemplateOutputFormat.DOCX.equals(format)) {
						tempResource = transformationService.transform(tempResource.toFile(), format);
					}

					documentGenerationHandler.afterDocumentGenerated();

					return tempResource;
				}
			}

		} catch (Throwable e) {

			if (tempResource != null) {
				try {
					Files.deleteIfExists(tempResource);
				} catch (Exception e1) {
					;
				}
			}
			throw new TemplateFailedException(e);
		}
	}

	public Path buildZip(String moveTo, List<File> resources) throws FileNotFoundException, IOException {
		return buildZip(moveTo, resources, null);
	}

	public Path buildZip(String moveTo, List<File> resources, String namePrefix)
			throws FileNotFoundException, IOException {
		Assert.notEmpty(resources, "resources parameter cnanot be empty");

		Message<List<File>> message = null;
		if (StringUtils.hasText(namePrefix)) {
			message = MessageBuilder.withPayload(resources)
					.setHeader(FileHeaders.FILENAME, namePrefix + "-templates.zip").build();
		} else {
			message = MessageBuilder.withPayload(resources).setHeader(FileHeaders.FILENAME, "templates.zip").build();
		}

		try {
			final Message<?> transform = zipTransformer.transform(message);
			final File payload = (File) transform.getPayload();

			try (InputStream is = new FileInputStream(payload)) {
				String filename = payload.getName();
				return storageService.storeTemporary(is,
						StringUtils.hasText(moveTo) ? moveTo + File.separator + filename : filename);
			}
		} catch (ZipException e) {
			for (File file : resources) {
				try {
					file.delete();
				} catch (Exception e1) {
					;
				}
			}
			throw e;
		}
	}

	public Path mergePdf(String moveTo, List<File> resources, String namePrefix)
			throws FileNotFoundException, IOException {
		Assert.notEmpty(resources, "resources parameter cnanot be empty");

		String finalName = null;
		if (StringUtils.hasText(namePrefix)) {
			finalName = namePrefix + ".pdf";
		} else {
			finalName = "template.pdf";
		}

		try {
			PDFMergerUtility ut = new PDFMergerUtility();
			ut.setDocumentMergeMode(DocumentMergeMode.OPTIMIZE_RESOURCES_MODE);
			for (File file : resources) {
				ut.addSource(file);
			}

			Path path = storageService.resolveTemporary(moveTo + "/" + finalName).toAbsolutePath();
			ut.setDestinationFileName(path.toString());
			ut.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());

			return path;
		} catch (Throwable e) {
			throw e;
		} finally {
			for (File file : resources) {
				try {
					file.delete();
				} catch (Exception e1) {
					;
				}
			}
		}
	}

	public Path generateDocument(DataRoot request, TemplateOutputFormat format, Boolean readOnly, String protectionPass,
			List<String> templates, String moveTo, String namePrefix, Boolean mergePdf)
			throws FileNotFoundException, IOException {
		Path resource = null;
		try {
			if (templates.size() > 1) {
				final List<File> templateResources = new ArrayList<>();

				try {
					for (final String template : templates) {
						Boolean ro = TemplateOutputFormat.DOCX.equals(format) ? readOnly : null;

						final Path templateResource = generateDocument(request, moveTo, template, format, ro,
								protectionPass, namePrefix);
						templateResources.add(templateResource.toFile());
					}
				} catch (Exception e) {
					for (File file : templateResources) {
						try {
							Files.deleteIfExists(file.toPath());
						} catch (Exception e1) {
							;
						}
					}
					throw e;
				}

				if (templateResources.isEmpty()) {
					throw new FileNotFoundException("templates could not be found or empty value");
				}

				if (TemplateOutputFormat.PDF.equals(format) && Boolean.TRUE.equals(mergePdf)) {
					resource = mergePdf(moveTo, templateResources, namePrefix);
				} else {
					resource = buildZip(moveTo, templateResources, namePrefix);
				}
			} else {
				resource = generateDocument(request, moveTo, templates.get(0), format, readOnly, protectionPass,
						namePrefix);
			}
		} catch (Exception e) {
			if (resource != null) {
				try {
					Files.deleteIfExists(resource);
				} catch (Exception e1) {
					;
				}
			}
			throw e;
		}

		return resource;
	}
}

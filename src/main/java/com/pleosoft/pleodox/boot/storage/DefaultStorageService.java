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

package com.pleosoft.pleodox.boot.storage;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.StringUtils;

public class DefaultStorageService implements StorageService {

	private final Path temporaryLocation;
	private final Path templateLocation;

	public DefaultStorageService(final Path temporaryLocation, final Path templateLocation) throws IOException {
		this.temporaryLocation = temporaryLocation;
		this.templateLocation = templateLocation;

		Files.createDirectories(temporaryLocation);
		Files.createDirectories(templateLocation);
	}

	public Path rsolveTemporary(String filename) {
		if (filename.contains("..")) {
			// This is a security check
			throw new StorageException("Cannot store file with relative path outside current directory " + filename);
		}

		return this.temporaryLocation.resolve(StringUtils.cleanPath(filename));
	}

	public Path storeTemporary(InputStream inputStream, String filename) {
		try {
			final Path newPath = rsolveTemporary(filename);
			File filePath = newPath.toFile();
			if (!filePath.exists()) {
				filePath.mkdirs();
			}

			// TODO move or copy?
			Files.copy(inputStream, newPath, StandardCopyOption.REPLACE_EXISTING);
			return newPath;
		} catch (final IOException e) {
			throw new StorageException("Failed to store file " + filename, e);
		} finally {
			// try {
			// inputStream.close();
			// } catch (final IOException e) {
			// ;
			// }
		}
	}

	public List<Path> loadAllTemplates() {
		try {

			final Path templatesPath = this.templateLocation;
			return Files.walk(templatesPath, 1).filter(Files::isRegularFile)
					.filter(path -> !path.equals(templatesPath)
							&& StringUtils.getFilenameExtension(path.getFileName().toString()).equals("docx"))
					.map(p -> p).collect(toList());
		} catch (final IOException e) {
			throw new StorageException("Failed to read stored files", e);
		}
	}

	public Path loadFromTemporary(String filename) {
		if (filename.contains("..")) {
			// This is a security check
			throw new StorageException("Cannot store file with relative path outside current directory " + filename);
		}
		return temporaryLocation.resolve(StringUtils.cleanPath(filename));
	}

	public Path loadExistingTemplate(final String filename) {
		if (filename.contains("..")) {
			// This is a security check
			throw new StorageException("Cannot load a file with relative path outside current directory " + filename);
		}
		return templateLocation.resolve(StringUtils.cleanPath(filename));
	}

	public Resource loadAsTemplateResource(final String filename) {
		Path file = loadExistingTemplate(filename);

		try {
			final Resource resource = new UrlResource(file.toUri());
			if (resource.exists() || resource.isReadable()) {
				return resource;
			} else {
				throw new StorageFileNotFoundException("Could not read file: " + file.getFileName());

			}
		} catch (final MalformedURLException e) {
			throw new StorageFileNotFoundException("Could not read file: " + file.getFileName(), e);
		}
	}

	public Resource loadAsTemporaryResource(final String filename) {
		Path file = loadFromTemporary(filename);

		try {
			final Resource resource = new UrlResource(file.toUri());
			if (resource.exists() || resource.isReadable()) {
				return resource;
			} else {
				throw new StorageFileNotFoundException("Could not read file: " + file.getFileName());

			}
		} catch (final MalformedURLException e) {
			throw new StorageFileNotFoundException("Could not read file: " + file.getFileName(), e);
		}
	}

}

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

package com.pleosoft.pleodoxstorage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.StringUtils;

public class FileSystemStorageService implements StorageService {

	private final Path temporaryLocation;
	private final Path templateLocation;

	public FileSystemStorageService(final Path temporaryLocation, final Path templateLocation) throws IOException {
		this.temporaryLocation = temporaryLocation;
		this.templateLocation = templateLocation;

		Files.createDirectories(temporaryLocation);
		Files.createDirectories(templateLocation);
	}

	public Path resolveTemporary(String filename) {
		if (filename.contains("..")) {
			// This is a security check
			throw new StorageException("Cannot store file with relative path outside current directory " + filename);
		}

		return this.temporaryLocation.resolve(StringUtils.cleanPath(filename));
	}

	public Path storeTemporary(InputStream inputStream, String filename) {
		try {
			final Path newPath = resolveTemporary(filename);
			File filePath = newPath.toFile();
			if (!filePath.exists()) {
				filePath.mkdirs();
			}

			// TODO move or copy?
			Files.copy(inputStream, newPath, StandardCopyOption.REPLACE_EXISTING);
			return newPath;
		} catch (final IOException e) {
			throw new StorageException("Failed to store file " + filename, e);
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

	public InputStream loadAsTemplateInputStream(final String filename) {
		try {
			return loadAsTemplateResource(filename).getInputStream();
		} catch (IOException e) {
			throw new StorageException("Cannot load a file with relative path outside current directory " + filename);
		}
	}

	@Override
	public InputStream loadAsTemporaryInputStream(String filename) {
		try {
			return loadAsTemporaryResource(filename).getInputStream();
		} catch (IOException e) {
			throw new StorageException("Cannot load a file with relative path outside current directory " + filename);
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

	public boolean exists(final String filename) {
		return loadAsTemplateResource(filename).exists();
	}

}

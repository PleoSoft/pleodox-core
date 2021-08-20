package com.pleosoft.pleodox.storage;

public class StoragePathExistsException extends RuntimeException {

	public StoragePathExistsException(String message) {
		super(message);
	}

	public StoragePathExistsException(String message, Throwable cause) {
		super(message, cause);
	}
}

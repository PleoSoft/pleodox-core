package com.pleosoft.pleodox.storage;

public class StorageActionNotAllowedException extends RuntimeException {

	public StorageActionNotAllowedException(String message) {
		super(message);
	}

	public StorageActionNotAllowedException(String message, Throwable cause) {
		super(message, cause);
	}
}

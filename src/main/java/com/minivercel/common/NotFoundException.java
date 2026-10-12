package com.minivercel.common;

/** Thrown when a resource doesn't exist, or exists but isn't the caller's. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}
}

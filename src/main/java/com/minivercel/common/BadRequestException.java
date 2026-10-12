package com.minivercel.common;

/** Thrown when a request is well-formed but the state or input makes it invalid. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}

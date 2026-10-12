package com.minivercel.deployment;

/** Lifecycle of a single deploy. Stored as the string name on the record. */
public enum DeploymentStatus {

	QUEUED("queued"),
	BUILDING("building"),
	SUCCESS("success"),
	FAILED("failed"),
	CANCELLED("cancelled"),
	REMOVED("removed");

	private final String value;

	DeploymentStatus(String value) {
		this.value = value;
	}

	public String value() {
		return this.value;
	}
}

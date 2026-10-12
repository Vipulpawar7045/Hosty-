package com.minivercel.config;

import org.springframework.stereotype.Component;

/**
 * Resolves physical table names from the configured prefix, so several environments can share
 * one AWS account without colliding.
 */
@Component
public class TableNames {

	private final String prefix;

	public TableNames(AppProperties props) {
		this.prefix = props.aws().tablePrefix();
	}

	public String users() {
		return this.prefix + "users";
	}

	public String projects() {
		return this.prefix + "projects";
	}

	public String deployments() {
		return this.prefix + "deployments";
	}

	public String emailClaims() {
		return this.prefix + "email-claims";
	}

	public String domains() {
		return this.prefix + "domains";
	}
}

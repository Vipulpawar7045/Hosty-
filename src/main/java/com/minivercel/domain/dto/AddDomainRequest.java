package com.minivercel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to attach a custom domain to a project. The pattern accepts a standard DNS hostname
 * (labels of letters/digits/hyphens separated by dots), e.g. {@code app.example.com}.
 */
public record AddDomainRequest(
		@NotBlank @Size(max = 253)
		@Pattern(
				regexp = "^(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$",
				message = "must be a valid domain name, e.g. app.example.com")
		String domainName) {
}

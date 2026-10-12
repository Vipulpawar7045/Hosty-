package com.minivercel.deployment.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelDeploymentRequest(
		@NotBlank(message = "Reason is required")
		String reason) {
}

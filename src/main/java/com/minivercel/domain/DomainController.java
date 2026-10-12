package com.minivercel.domain;

import java.util.List;

import com.minivercel.common.CurrentUser;
import com.minivercel.domain.dto.AddDomainRequest;
import com.minivercel.domain.dto.DomainResponse;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/domains")
public class DomainController {

	private final DomainService domainService;

	public DomainController(DomainService domainService) {
		this.domainService = domainService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DomainResponse add(@PathVariable String projectId,
			@Valid @RequestBody AddDomainRequest request,
			@CurrentUser String userId) {
		return domainService.addDomain(projectId, userId, request.domainName());
	}

	@GetMapping
	public List<DomainResponse> list(@PathVariable String projectId, @CurrentUser String userId) {
		return domainService.listDomains(projectId, userId);
	}

	@GetMapping("/{domainId}")
	public DomainResponse get(@PathVariable String projectId,
			@PathVariable String domainId,
			@CurrentUser String userId) {
		return domainService.getDomain(domainId, userId);
	}

	/** Re-checks the certificate with ACM and returns the updated status. */
	@PostMapping("/{domainId}/refresh")
	public DomainResponse refresh(@PathVariable String projectId,
			@PathVariable String domainId,
			@CurrentUser String userId) {
		return domainService.refreshStatus(domainId, userId);
	}

	@DeleteMapping("/{domainId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@PathVariable String projectId,
			@PathVariable String domainId,
			@CurrentUser String userId) {
		domainService.removeDomain(domainId, userId);
	}
}

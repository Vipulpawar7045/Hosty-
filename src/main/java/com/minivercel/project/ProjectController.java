package com.minivercel.project;

import java.util.List;

import com.minivercel.common.CurrentUser;
import com.minivercel.project.dto.CreateProjectRequest;
import com.minivercel.project.dto.ProjectResponse;
import com.minivercel.project.dto.UpdateProjectRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public List<ProjectResponse> list(@CurrentUser String userId) {
		return this.projectService.listForUser(userId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request,
			@CurrentUser String userId) {
		return this.projectService.create(request, userId);
	}

	@GetMapping("/{projectId}")
	public ProjectResponse get(@PathVariable String projectId, @CurrentUser String userId) {
		return this.projectService.get(projectId, userId);
	}

	@PutMapping("/{projectId}")
	public ProjectResponse update(@PathVariable String projectId,
			@Valid @RequestBody UpdateProjectRequest request, @CurrentUser String userId) {
		return this.projectService.update(projectId, request, userId);
	}

	@DeleteMapping("/{projectId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String projectId, @CurrentUser String userId) {
		this.projectService.delete(projectId, userId);
	}
}

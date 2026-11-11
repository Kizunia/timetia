package com.kibootia.backend.identity.web;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.identity.application.Invitation;
import com.kibootia.backend.identity.application.UserAdministration;
import com.kibootia.backend.identity.application.UserProfileStore.UserFilter;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/**
 * Other users' accounts (ADR 0041). Actions that are not plain create, read, update or delete are sub-resources
 * (ADR 0043). The controller only converts and delegates; the use cases decide who may do what, audit every change and
 * bring Keycloak in line.
 */
@RestController
@RequestMapping("/api/v1/users")
class UsersController {

	/** API field name to the name the store maps to a column, for every field a client may sort by. */
	private static final Map<String, String> SORTABLE = Map.of("name", "name", "email", "email", "username", "username",
			"status", "status", "createdAt", "createdAt");

	private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name");

	private final UserAdministration users;

	UsersController(UserAdministration users) {
		this.users = users;
	}

	/**
	 * @param q only users whose name, username or email contains this text
	 * @param state only users in this state; without it, every user that is not deleted
	 */
	@GetMapping
	PageResponse<UserResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) UserFilter.State state) {
		return PageResponse.of(this.users.list(new UserFilter(q, state), query.toPageable(SORTABLE, BY_NAME)),
				UserMapper::toResponse);
	}

	@GetMapping("/{id}")
	UserResponse get(@PathVariable UUID id) {
		return UserMapper.toResponse(this.users.get(id));
	}

	/** Creates the account and emails the invitation; the email is sent by Keycloak, shortly after this answers. */
	@PostMapping
	@ApiResponse(responseCode = "201", description = "The user was invited; `Location` is their URL.")
	ResponseEntity<UserResponse> invite(@Valid @RequestBody InviteUserRequest request) {
		UserResponse invited = UserMapper.toResponse(this.users.invite(UserMapper.toInvitation(request)));
		return ResponseEntity.created(URI.create("/api/v1/users/" + invited.id())).body(invited);
	}

	/** Sends the invitation again to a user who has not accepted it. */
	@PostMapping("/{id}/invitation")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void resendInvitation(@PathVariable UUID id) {
		this.users.resendInvitation(id);
	}

	@PostMapping("/{id}/disable")
	UserResponse disable(@PathVariable UUID id) {
		return UserMapper.toResponse(this.users.disable(id));
	}

	@PostMapping("/{id}/enable")
	UserResponse enable(@PathVariable UUID id) {
		return UserMapper.toResponse(this.users.enable(id));
	}

	/** Soft-deletes the user; an administrator can restore them until they are erased. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.users.delete(id);
	}

	@PostMapping("/{id}/restore")
	UserResponse restore(@PathVariable UUID id) {
		return UserMapper.toResponse(this.users.restore(id));
	}

	/** Begins erasing a deleted user for good. The removal runs afterwards, so the answer is 202 Accepted. */
	@PostMapping("/{id}/erase")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void erase(@PathVariable UUID id) {
		this.users.erase(id);
	}

}

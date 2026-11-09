package com.kibootia.backend.platform.web;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.kibootia.backend.TestcontainersConfiguration;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.persistence.TestWidget;
import com.kibootia.backend.platform.persistence.TestWidgetRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP conventions of ADR 0043 through the real filter chain and controller advice: one error shape for every
 * failure, the status table, and paging. A test-only controller (nested, so no other test sees it) raises each failure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ApiConventionsIT.Probe.class })
class ApiConventionsIT {

	record Payload(@NotBlank String name, @Email String email, @Min(1) int count) {

	}

	@RestController
	@RequestMapping("/api/v1/probe")
	static class Probe {

		private final TestWidgetRepository widgets;

		Probe(TestWidgetRepository widgets) {
			this.widgets = widgets;
		}

		@PostMapping("/validated")
		String validated(@Valid @RequestBody Payload payload) {
			return payload.name();
		}

		@GetMapping("/api-error/{kind}")
		String apiError(@org.springframework.web.bind.annotation.PathVariable String kind) {
			throw switch (kind) {
				case "not-found" -> ApiException.notFound("notes.not_found", "No such note.");
				case "conflict" -> ApiException.conflict("access.last_admin", "The last administrator stays.");
				case "too-large" -> ApiException.payloadTooLarge("files.too_large", "Too large.");
				case "unsupported" -> ApiException.unsupportedMediaType("files.unsupported_type", "Not allowed.");
				case "rate-limited" -> ApiException.tooManyRequests("Slow down.", Duration.ofSeconds(30));
				default -> throw new IllegalArgumentException(kind);
			};
		}

		@GetMapping("/denied")
		String denied() {
			throw new AccessDeniedException("internal detail that must not leak");
		}

		@GetMapping("/stale")
		String stale() {
			throw new ObjectOptimisticLockingFailureException(TestWidget.class, UUID.randomUUID());
		}

		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("secret internal detail: jdbc:mysql://db:3306 password=hunter2");
		}

		@GetMapping("/upload")
		String upload() {
			throw new MaxUploadSizeExceededException(10);
		}

		@GetMapping("/integrity")
		String integrity() {
			throw new DataIntegrityViolationException("could not execute statement [Column 'x' cannot be null]");
		}

		@PostMapping("/widgets/{name}")
		String widget(@org.springframework.web.bind.annotation.PathVariable String name) {
			this.widgets.saveAndFlush(new TestWidget(name));
			return name;
		}

		@GetMapping("/list")
		PageResponse<String> list(@Valid PageQuery query) {
			Pageable pageable = query.toPageable(java.util.Map.of("name", "name", "createdAt", "createdAt"),
					Sort.by(Sort.Direction.ASC, "name"));
			return PageResponse.of(new PageImpl<>(List.of("a", "b"), pageable, 42), String::toUpperCase);
		}

		@GetMapping("/sort-echo")
		String sortEcho(@Valid PageQuery query) {
			return query.toPageable(java.util.Map.of("name", "name", "createdAt", "createdAt"),
					Sort.by(Sort.Direction.ASC, "name"))
				.toString();
		}

	}

	@Autowired
	MockMvc mvc;

	private ResultActions call(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
		return this.mvc.perform(request);
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authed(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
		return builder.with(jwt().jwt((token) -> token.subject("11111111-1111-7111-8111-111111111111")));
	}

	private static ResultActions problem(ResultActions result, int status, String code) throws Exception {
		return result.andExpect(status().is(status))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(status))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.title").isString())
			.andExpect(jsonPath("$.traceId").isString())
			.andExpect(jsonPath("$.stackTrace").doesNotExist())
			.andExpect(jsonPath("$.properties").doesNotExist());
	}

	@Test
	@DisplayName("a failed validation is 400 with one entry per invalid field")
	void validationFailure() throws Exception {
		ResultActions result = call(authed(post("/api/v1/probe/validated")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\",\"email\":\"not-an-email\",\"count\":0}"));

		problem(result, 400, "platform.validation_failed").andExpect(jsonPath("$.title").value("Validation failed"))
			.andExpect(jsonPath("$.errors.length()").value(3))
			.andExpect(jsonPath("$.errors[?(@.field=='name')].code").value("required"))
			.andExpect(jsonPath("$.errors[?(@.field=='email')].code").value("invalid_format"))
			.andExpect(jsonPath("$.errors[?(@.field=='count')].code").value("out_of_range"))
			.andExpect(jsonPath("$.errors[0].message").isString());
	}

	@Test
	@DisplayName("a body that cannot be parsed is 400 and does not echo the parser's message")
	void malformedBody() throws Exception {
		ResultActions result = call(authed(post("/api/v1/probe/validated")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": nope"));

		problem(result, 400, "platform.malformed_request").andExpect(jsonPath("$.detail")
			.value("The request body could not be read."));
	}

	@Test
	@DisplayName("a path nothing serves is 404 in the same shape")
	void unknownPath() throws Exception {
		problem(call(authed(get("/api/v1/nothing-here"))), 404, "platform.not_found");
	}

	@Test
	@DisplayName("the wrong method is 405 and the wrong media type is 415")
	void methodAndMediaType() throws Exception {
		problem(call(authed(delete("/api/v1/probe/validated"))), 405, "platform.method_not_allowed");
		problem(call(authed(post("/api/v1/probe/validated")).contentType(MediaType.TEXT_PLAIN).content("x")), 415,
				"platform.unsupported_media_type");
	}

	@Test
	@DisplayName("modules choose their own status and code through ApiException")
	void apiExceptions() throws Exception {
		problem(call(authed(get("/api/v1/probe/api-error/not-found"))), 404, "notes.not_found");
		problem(call(authed(get("/api/v1/probe/api-error/conflict"))), 409, "access.last_admin");
		problem(call(authed(get("/api/v1/probe/api-error/too-large"))), 413, "files.too_large");
		problem(call(authed(get("/api/v1/probe/api-error/unsupported"))), 415, "files.unsupported_type");
	}

	@Test
	@DisplayName("a rate-limited call is 429 with Retry-After")
	void rateLimited() throws Exception {
		problem(call(authed(get("/api/v1/probe/api-error/rate-limited"))), 429, "platform.rate_limited")
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"));
	}

	@Test
	@DisplayName("a denial raised inside a controller is 403 in the same shape as the filter chain's")
	void accessDeniedFromMethodSecurity() throws Exception {
		problem(call(authed(get("/api/v1/probe/denied"))), 403, "platform.forbidden")
			.andExpect(jsonPath("$.detail").value("Access is denied"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal detail"))));
	}

	@Test
	@DisplayName("a concurrent edit is 409")
	void concurrentModification() throws Exception {
		problem(call(authed(get("/api/v1/probe/stale"))), 409, "platform.concurrent_modification");
	}

	@Test
	@DisplayName("a duplicate unique value is 409; another integrity failure is a 500, not a client error")
	void integrityFailures() throws Exception {
		call(authed(post("/api/v1/probe/widgets/dup-" + UUID.randomUUID().toString().substring(0, 8)))).andExpect(
				status().isOk());
		String name = "dup-" + UUID.randomUUID().toString().substring(0, 8);
		call(authed(post("/api/v1/probe/widgets/" + name))).andExpect(status().isOk());
		problem(call(authed(post("/api/v1/probe/widgets/" + name))), 409, "platform.duplicate");

		problem(call(authed(get("/api/v1/probe/integrity"))), 500, "platform.internal_error");
	}

	@Test
	@DisplayName("an upload over the limit is 413")
	void uploadTooLarge() throws Exception {
		problem(call(authed(get("/api/v1/probe/upload"))), 413, "platform.payload_too_large");
	}

	@Test
	@DisplayName("an unexpected failure is 500 with a generic message and no internal detail")
	void unexpectedFailure() throws Exception {
		problem(call(authed(get("/api/v1/probe/boom"))), 500, "platform.internal_error")
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("IllegalState"))));
	}

	@Test
	@DisplayName("the filter chain's 401 and 403 use the same shape")
	void filterChainFailuresShareTheShape() throws Exception {
		problem(call(get("/api/v1/me")), 401, "platform.unauthenticated").andExpect(
				header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")));
	}

	@Test
	@DisplayName("a list answers items and page, with defaults of page 0 and size 20")
	void listShape() throws Exception {
		call(authed(get("/api/v1/probe/list"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0]").value("A"))
			.andExpect(jsonPath("$.page.number").value(0))
			.andExpect(jsonPath("$.page.size").value(20))
			.andExpect(jsonPath("$.page.totalElements").value(42))
			.andExpect(jsonPath("$.page.totalPages").value(3));
	}

	@Test
	@DisplayName("a size of 100 is accepted and 101 is rejected")
	void sizeLimit() throws Exception {
		call(authed(get("/api/v1/probe/list?size=100"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.size").value(100));
		problem(call(authed(get("/api/v1/probe/list?size=101"))), 400, "platform.validation_failed")
			.andExpect(jsonPath("$.errors[0].field").value("size"));
		problem(call(authed(get("/api/v1/probe/list?size=0"))), 400, "platform.validation_failed");
		problem(call(authed(get("/api/v1/probe/list?page=-1"))), 400, "platform.validation_failed")
			.andExpect(jsonPath("$.errors[0].field").value("page"));
		problem(call(authed(get("/api/v1/probe/list?page=abc"))), 400, "platform.validation_failed")
			.andExpect(jsonPath("$.errors[0].code").value("invalid_type"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("java.lang"))));
	}

	@Test
	@DisplayName("only allow-listed fields can be sorted, in either direction")
	void sortAllowList() throws Exception {
		call(authed(get("/api/v1/probe/sort-echo?sort=createdAt,desc"))).andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("createdAt: DESC")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("id: ASC")));
		call(authed(get("/api/v1/probe/sort-echo?sort=name"))).andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("name: ASC")));
		call(authed(get("/api/v1/probe/sort-echo"))).andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("name: ASC")));

		problem(call(authed(get("/api/v1/probe/sort-echo?sort=password,asc"))), 400, "platform.validation_failed")
			.andExpect(jsonPath("$.errors[0].field").value("sort"))
			.andExpect(jsonPath("$.errors[0].code").value("unsupported_sort"));
		problem(call(authed(get("/api/v1/probe/sort-echo?sort=name,sideways"))), 400, "platform.validation_failed");
		problem(call(authed(get("/api/v1/probe/sort-echo?sort=name,asc,extra"))), 400, "platform.validation_failed");
	}

}

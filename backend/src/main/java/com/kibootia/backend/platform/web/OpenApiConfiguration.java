package com.kibootia.backend.platform.web;

import java.util.List;
import java.util.Locale;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/**
 * Shapes the generated OpenAPI description (ADR 0043). The description is committed as {@code backend/openapi.json} and
 * the frontend client is generated from it, so it must not depend on where or how the application ran: no server URL,
 * no build version, sorted output. Every operation gets the Problem Details responses that can happen to any call.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	static final String PROBLEM = "Problem";

	static final String BEARER = "bearer";

	@Bean
	OpenApiCustomizer kibootiaOpenApi() {
		return (OpenAPI openApi) -> {
			openApi.info(new Info().title("Kibootia API")
				.version("v1")
				.description("Everything lives under /api/v1. Every call needs a user's bearer token; browsers reach the "
						+ "API only through the Next.js server. Errors are RFC 9457 Problem Details with a stable "
						+ "`code`, the `traceId` of the request and, for validation failures, `errors`."));
			openApi.setServers(null);
			openApi.getComponents().addSecuritySchemes(BEARER,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
			openApi.addSecurityItem(new SecurityRequirement().addList(BEARER));
			openApi.getComponents().addSchemas(PROBLEM, problemSchema());
			if (openApi.getPaths() != null) {
				openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
					useJsonForSuccess(operation);
					addProblemResponses(path, method, operation);
				}));
			}
		};
	}

	/**
	 * A stable operation ID and tag per controller method ({@code notesList}, {@code failedEventsRetry}), so the
	 * generated client does not depend on method names being unique across controllers.
	 */
	@Bean
	OperationCustomizer kibootiaOperations() {
		return (Operation operation, HandlerMethod handler) -> {
			String controller = handler.getBeanType().getSimpleName().replaceAll("Controller$", "");
			String tag = controller.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
			String method = handler.getMethod().getName();
			operation.setOperationId(Character.toLowerCase(controller.charAt(0)) + controller.substring(1)
					+ Character.toUpperCase(method.charAt(0)) + method.substring(1));
			operation.setTags(List.of(tag));
			return operation;
		};
	}

	private static Schema<?> problemSchema() {
		Schema<?> fieldError = new ObjectSchema()
			.addProperty("field", new StringSchema().description("The request field, for example `title`."))
			.addProperty("code", new StringSchema().description("A stable code, for example `required`."))
			.addProperty("message", new StringSchema().description("A developer-facing explanation."))
			.required(List.of("field", "code", "message"));
		return new ObjectSchema().description("An RFC 9457 problem. Match on `code`, not on the text.")
			.addProperty("type", new StringSchema().example("about:blank"))
			.addProperty("title", new StringSchema())
			.addProperty("status", new IntegerSchema())
			.addProperty("detail", new StringSchema().description("A developer-facing default message."))
			.addProperty("instance", new StringSchema().description("The path of the request."))
			.addProperty("code", new StringSchema().description("`<module>.<reason>`, for example `notes.not_found`."))
			.addProperty("traceId", new StringSchema().description("The trace of the request, for support."))
			.addProperty("errors", new ArraySchema().items(fieldError)
				.description("Present for validation failures: one entry per invalid field."))
			.required(List.of("title", "status", "code"));
	}

	/** Spring leaves the media type of a successful body open ({@code *}/{@code *}); the API always answers JSON. */
	private static void useJsonForSuccess(Operation operation) {
		operation.getResponses().forEach((status, response) -> {
			if (status.startsWith("2") && response.getContent() != null && response.getContent().containsKey("*/*")) {
				MediaType body = response.getContent().remove("*/*");
				response.getContent().addMediaType("application/json", body);
			}
		});
	}

	/** Adds the failures that can happen to the call; 401, 403 and 500 can happen to every one. */
	private static void addProblemResponses(String path, PathItem.HttpMethod method, Operation operation) {
		boolean takesInput = operation.getRequestBody() != null
				|| (operation.getParameters() != null && !operation.getParameters().isEmpty());
		add(operation, "401", "No valid bearer token.");
		add(operation, "403", "The caller may not do this.");
		if (takesInput) {
			add(operation, "400", "The request is malformed or fails validation; see `errors`.");
		}
		if (path.contains("{")) {
			add(operation, "404", "There is no such record, or the caller may not see it.");
		}
		if (method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PUT) {
			add(operation, "409", "The record changed meanwhile, or the request clashes with its state.");
		}
		add(operation, "500", "An unexpected failure; quote the `traceId`.");
	}

	private static void add(Operation operation, String status, String description) {
		if (operation.getResponses().containsKey(status)) {
			return;
		}
		Content content = new Content().addMediaType("application/problem+json",
				new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM)));
		operation.getResponses().addApiResponse(status, new ApiResponse().description(description).content(content));
	}

}

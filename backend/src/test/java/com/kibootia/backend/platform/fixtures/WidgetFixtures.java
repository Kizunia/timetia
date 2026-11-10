package com.kibootia.backend.platform.fixtures;

import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;
import com.kibootia.backend.platform.authorization.RequiresPermission;

/**
 * A stand-in feature for the tests of the platform, access and identity modules: permissions with the same shape as a
 * real feature's (some every user holds, some that must be granted, {@code -any} variants that widen "mine" to
 * "everyone's") and one protected endpoint pair. Those tests need some declared permissions and some protected URL to
 * work against; naming a real feature there would stop the application's tests from passing once that feature is
 * removed. Imported by {@code AdminApiTest}, so every integration test built on it has it.
 *
 * <p>The widgets do not exist as data: the endpoints only check the permission and answer. They are not part of the
 * application and never appear in {@code openapi.json}, which is generated from a context that does not import this.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(WidgetFixtures.WidgetsApi.class)
public class WidgetFixtures {

	public static final String CREATE = "widgets.widgets.create";

	public static final String VIEW = "widgets.widgets.view";

	public static final String VIEW_ANY = "widgets.widgets.view-any";

	public static final String UPDATE = "widgets.widgets.update";

	public static final String UPDATE_ANY = "widgets.widgets.update-any";

	public static final String DELETE = "widgets.widgets.delete";

	public static final String DELETE_ANY = "widgets.widgets.delete-any";

	public static final String RESTORE = "widgets.widgets.restore";

	@Bean
	PermissionProvider widgetPermissions() {
		return () -> List.of(Permission.forEveryUser(CREATE, "Create widgets"),
				Permission.forEveryUser(VIEW, "View your own widgets"),
				Permission.restricted(VIEW_ANY, "View anyone's widgets"),
				Permission.forEveryUser(UPDATE, "Change your own widgets"),
				Permission.restricted(UPDATE_ANY, "Change anyone's widgets"),
				Permission.forEveryUser(DELETE, "Delete your own widgets"),
				Permission.restricted(DELETE_ANY, "Delete anyone's widgets"),
				Permission.restricted(RESTORE, "Restore deleted widgets"));
	}

	/** Two endpoints that need a permission and nothing else, for tests that need a protected URL. */
	@RestController
	@RequestMapping("/api/v1/widgets")
	public static class WidgetsApi {

		@PostMapping
		@ResponseStatus(HttpStatus.CREATED)
		@RequiresPermission(CREATE)
		public String create() {
			return "created";
		}

		@GetMapping
		@RequiresPermission(VIEW)
		public String list() {
			return "listed";
		}

	}

}

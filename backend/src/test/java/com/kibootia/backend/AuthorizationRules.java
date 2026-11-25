package com.kibootia.backend;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture rule 7 (ADR 0044): every public use-case method carries an authorization check or an explicit
 * {@code @NoAuthorization("reason")}. A use case is a Spring component in an {@code application} package. A method
 * counts as checked when it is annotated {@code @RequiresPermission}, or calls {@code Authorization} directly. A check
 * made inside a lambda or a helper class is not seen; put the call in the method itself, or mark it
 * {@code @NoAuthorization} with the reason.
 */
final class AuthorizationRules {

	private AuthorizationRules() {
	}

	static ArchRule useCaseMethodsAreChecked(String packageIdentifier) {
		return classes().that()
			.resideInAPackage(packageIdentifier)
			.and()
			.areMetaAnnotatedWith(Component.class)
			.should(haveEveryPublicMethodChecked())
			.allowEmptyShould(true)
			.because("a public use-case method without a check or an explicit @NoAuthorization is an unprotected operation");
	}

	private static ArchCondition<JavaClass> haveEveryPublicMethodChecked() {
		return new ArchCondition<>("have a permission check or @NoAuthorization on every public method") {
			@Override
			public void check(JavaClass useCase, ConditionEvents events) {
				for (JavaMethod method : useCase.getMethods()) {
					if (!method.getModifiers().contains(JavaModifier.PUBLIC)
							|| method.getModifiers().contains(JavaModifier.SYNTHETIC)
							|| method.getModifiers().contains(JavaModifier.BRIDGE)) {
						continue;
					}
					if (!isChecked(method)) {
						events.add(SimpleConditionEvent.violated(method, method.getFullName()
								+ " has no @RequiresPermission, no call to Authorization and no @NoAuthorization ("
								+ method.getSourceCodeLocation() + ")"));
					}
				}
			}
		};
	}

	private static boolean isChecked(JavaMethod method) {
		return method.isAnnotatedWith(RequiresPermission.class) || method.isAnnotatedWith(NoAuthorization.class)
				|| method.getMethodCallsFromSelf()
					.stream()
					.anyMatch((call) -> call.getTargetOwner().isEquivalentTo(Authorization.class));
	}

}

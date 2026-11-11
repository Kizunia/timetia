package com.kibootia.backend.academic;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.kibootia.backend.academic.domain.AcademicAccessPolicy;
import com.kibootia.backend.platform.authorization.ScopeKindProvider;
import com.kibootia.backend.platform.authorization.ScopedResourcePolicy;

/** Wires the academic rules into the decision point and declares the kinds of place Timetia roles apply to. */
@Configuration(proxyBeanMethods = false)
class AcademicConfiguration {

	@Bean
	ScopeKindProvider academicScopeKinds() {
		return () -> Set.of(AcademicScopes.DEPARTMENT, AcademicScopes.DIVISION, AcademicScopes.BATCH);
	}

	@Bean
	ScopedResourcePolicy academicAccessPolicy() {
		return new AcademicAccessPolicy();
	}

}

package com.kibootia.backend.platform.fixtures;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.kibootia.backend.platform.authorization.ScopeDirectory;

/**
 * A context in which nothing can say whether a department, division or batch exists. The application always has such a
 * provider now (the reference module), but the code that asks must still fail closed when there is none, so that nothing
 * is ever silently unvalidated: a role that needs a place is refused and a role that applies everywhere still works. A test
 * of that path imports this to take every {@link ScopeDirectory} out of the context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class WithoutScopeDirectory {

	@Bean
	static BeanFactoryPostProcessor removeEveryScopeDirectory() {
		return (factory) -> {
			if (factory instanceof BeanDefinitionRegistry registry) {
				for (String name : factory.getBeanNamesForType(ScopeDirectory.class, true, false)) {
					registry.removeBeanDefinition(name);
				}
			}
		};
	}

}

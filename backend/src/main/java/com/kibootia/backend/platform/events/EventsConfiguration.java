package com.kibootia.backend.platform.events;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventsProperties.class)
class EventsConfiguration {

	/** Applies {@link DurableEventInterceptor} to every method marked {@link DurableEventListener}. */
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static Advisor durableEventListenerAdvisor(BeanFactory beans) {
		return new DefaultPointcutAdvisor(new AnnotationMatchingPointcut(null, DurableEventListener.class, true),
				new DurableEventInterceptor(beans));
	}

}

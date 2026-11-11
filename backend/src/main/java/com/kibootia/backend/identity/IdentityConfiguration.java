package com.kibootia.backend.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.kibootia.backend.identity.application.IdentityProperties;

/** Binds the {@code kibootia.identity.*} settings. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {

}

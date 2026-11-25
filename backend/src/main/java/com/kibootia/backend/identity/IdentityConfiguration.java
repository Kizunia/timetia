package com.kibootia.backend.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.kibootia.backend.identity.application.IdentityProperties;
import com.kibootia.backend.identity.application.UserImportProperties;

/** Binds the {@code kibootia.identity.*} settings. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ IdentityProperties.class, UserImportProperties.class })
class IdentityConfiguration {

}

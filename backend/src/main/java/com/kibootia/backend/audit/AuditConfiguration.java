package com.kibootia.backend.audit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.kibootia.backend.audit.application.AuditProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditProperties.class)
class AuditConfiguration {

}

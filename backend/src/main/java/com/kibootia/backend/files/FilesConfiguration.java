package com.kibootia.backend.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.kibootia.backend.files.application.ConditionalOnFilesEnabled;
import com.kibootia.backend.files.application.FileInspector;
import com.kibootia.backend.files.application.FilesProperties;
import com.kibootia.backend.files.application.ObjectStorage;
import com.kibootia.backend.files.s3.S3ObjectStorage;

/**
 * Wires the files module (ADR 0024, ADR 0029): the S3 client, the default inspection hook and the creation of the
 * bucket. With {@code kibootia.files.enabled=false} none of this exists.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnFilesEnabled
@EnableConfigurationProperties(FilesProperties.class)
class FilesConfiguration {

	private static final Logger log = LoggerFactory.getLogger(FilesConfiguration.class);

	@Bean(destroyMethod = "close")
	S3ObjectStorage s3ObjectStorage(FilesProperties properties) {
		return new S3ObjectStorage(properties);
	}

	/** Accepts every upload; a deployment replaces it to scan for malware (a recipe, ADR 0029). */
	@Bean
	@ConditionalOnMissingBean(FileInspector.class)
	FileInspector fileInspector() {
		return (content, contentType) -> {
		};
	}

	/**
	 * Creates the bucket at startup when asked to. A storage server that is down must only affect file features
	 * (ADR 0047), so a failure is logged and the first upload tries again.
	 */
	@Bean
	SmartInitializingSingleton bucketInitializer(ObjectStorage storage, FilesProperties properties) {
		return () -> {
			if (!properties.createBucket()) {
				return;
			}
			try {
				storage.ensureBucket();
			}
			catch (ObjectStorage.Unavailable ex) {
				log.warn("The storage server is not available at startup; files will not work until it is: {}",
						ex.getMessage());
			}
		};
	}

}

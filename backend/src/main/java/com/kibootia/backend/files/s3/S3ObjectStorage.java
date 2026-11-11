package com.kibootia.backend.files.s3;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import com.kibootia.backend.files.application.FilesProperties;
import com.kibootia.backend.files.application.ObjectStorage;

/**
 * The {@link ObjectStorage} port on the standard AWS SDK for Java S3 client (ADR 0029). Any S3-compatible server works;
 * changing server is a change of configuration. Path-style addressing is used because a local server has no wildcard
 * DNS for bucket names.
 *
 * <p>The SDK's default is to add a trailing checksum to every upload and to validate one on every download. Servers that
 * do not implement that reject the request, so both are set to "when required", which sends and checks a checksum only
 * where the operation demands it.
 */
public class S3ObjectStorage implements ObjectStorage, AutoCloseable {

	private static final int MAX_KEYS_PER_DELETE = 1000;

	private final S3Client client;

	private final S3Presigner presigner;

	private final String bucket;

	public S3ObjectStorage(FilesProperties properties) {
		this.bucket = properties.bucket();
		AwsBasicCredentials credentials = AwsBasicCredentials.create(properties.accessKey(), properties.secretKey());
		URI endpoint = URI.create(properties.endpoint());
		S3Configuration configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build();
		this.client = S3Client.builder()
			.endpointOverride(endpoint)
			.region(Region.of(properties.region()))
			.credentialsProvider(StaticCredentialsProvider.create(credentials))
			.serviceConfiguration(configuration)
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.httpClientBuilder(UrlConnectionHttpClient.builder()
				.connectionTimeout(Duration.ofSeconds(3))
				.socketTimeout(Duration.ofSeconds(30)))
			.build();
		this.presigner = S3Presigner.builder()
			.endpointOverride(endpoint)
			.region(Region.of(properties.region()))
			.credentialsProvider(StaticCredentialsProvider.create(credentials))
			.serviceConfiguration(configuration)
			.build();
	}

	@Override
	public void ensureBucket() {
		try {
			try {
				this.client.headBucket(HeadBucketRequest.builder().bucket(this.bucket).build());
			}
			catch (NoSuchBucketException ex) {
				createBucket();
			}
			catch (software.amazon.awssdk.services.s3.model.S3Exception ex) {
				// Some servers answer a plain 404 without the error code the SDK maps to NoSuchBucketException.
				if (ex.statusCode() != 404) {
					throw ex;
				}
				createBucket();
			}
		}
		catch (SdkException ex) {
			throw unavailable("ensure the bucket", ex);
		}
	}

	private void createBucket() {
		try {
			this.client.createBucket((request) -> request.bucket(this.bucket));
		}
		catch (BucketAlreadyOwnedByYouException ex) {
			// Another instance created it first; that is the outcome we wanted.
		}
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		try {
			this.client.putObject(PutObjectRequest.builder()
				.bucket(this.bucket)
				.key(key)
				.contentType(contentType)
				.contentLength((long) content.length)
				.build(), RequestBody.fromBytes(content));
		}
		catch (SdkException ex) {
			throw unavailable("store an object", ex);
		}
	}

	@Override
	public Optional<StoredObject> get(String key) {
		try {
			ResponseInputStream<GetObjectResponse> response = this.client
				.getObject(GetObjectRequest.builder().bucket(this.bucket).key(key).build());
			GetObjectResponse head = response.response();
			return Optional.of(new StoredObject(response, head.contentLength(), head.contentType()));
		}
		catch (NoSuchKeyException ex) {
			return Optional.empty();
		}
		catch (software.amazon.awssdk.services.s3.model.S3Exception ex) {
			if (ex.statusCode() == 404) {
				return Optional.empty();
			}
			throw unavailable("read an object", ex);
		}
		catch (SdkException ex) {
			throw unavailable("read an object", ex);
		}
	}

	@Override
	public void delete(String key) {
		try {
			this.client.deleteObject((request) -> request.bucket(this.bucket).key(key));
		}
		catch (SdkException ex) {
			throw unavailable("delete an object", ex);
		}
	}

	@Override
	public void deleteAll(Collection<String> keys) {
		List<ObjectIdentifier> identifiers = keys.stream().map((key) -> ObjectIdentifier.builder().key(key).build()).toList();
		// One request deletes at most a thousand keys.
		for (int from = 0; from < identifiers.size(); from += MAX_KEYS_PER_DELETE) {
			List<ObjectIdentifier> chunk = identifiers.subList(from, Math.min(from + MAX_KEYS_PER_DELETE, identifiers.size()));
			try {
				this.client.deleteObjects(DeleteObjectsRequest.builder()
					.bucket(this.bucket)
					.delete(Delete.builder().objects(chunk).quiet(true).build())
					.build());
			}
			catch (SdkException ex) {
				throw unavailable("delete objects", ex);
			}
		}
	}

	@Override
	public Listing list(String prefix, String continuationToken, int maxKeys) {
		try {
			ListObjectsV2Response response = this.client.listObjectsV2(ListObjectsV2Request.builder()
				.bucket(this.bucket)
				.prefix(prefix)
				.continuationToken(continuationToken)
				.maxKeys(maxKeys)
				.build());
			List<ObjectInfo> objects = response.contents()
				.stream()
				.map((object) -> new ObjectInfo(object.key(), object.size(), object.lastModified()))
				.toList();
			return new Listing(objects, Boolean.TRUE.equals(response.isTruncated()) ? response.nextContinuationToken() : null);
		}
		catch (SdkException ex) {
			throw unavailable("list objects", ex);
		}
	}

	@Override
	public URL presignPut(String key, String contentType, Duration validFor) {
		try {
			return this.presigner.presignPutObject(PutObjectPresignRequest.builder()
				.signatureDuration(validFor)
				.putObjectRequest((request) -> request.bucket(this.bucket).key(key).contentType(contentType))
				.build()).url();
		}
		catch (SdkException ex) {
			throw unavailable("presign an upload", ex);
		}
	}

	@Override
	public URL presignGet(String key, Duration validFor) {
		try {
			return this.presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(validFor)
				.getObjectRequest((request) -> request.bucket(this.bucket).key(key))
				.build()).url();
		}
		catch (SdkException ex) {
			throw unavailable("presign a download", ex);
		}
	}

	@Override
	public void close() {
		this.client.close();
		this.presigner.close();
	}

	/** The SDK's message can name the endpoint and request IDs but never a secret; the credentials are not part of it. */
	private static Unavailable unavailable(String action, SdkException cause) {
		return new Unavailable("The storage server could not " + action + ": " + cause.getClass().getSimpleName(), cause);
	}

}

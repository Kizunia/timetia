package com.kibootia.backend;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.kibootia.backend.files.application.ObjectStorage;

/**
 * An in-memory object store for tests that are about the module and not about S3, which {@code S3ObjectStorageContractIT}
 * covers against a real server. It can be made to fail, to stand for a storage outage, and its objects can be made to
 * look old, so the reconciliation grace period can be tested without waiting.
 */
public class FakeObjectStorage implements ObjectStorage {

	private record Entry(byte[] content, String contentType, Instant lastModified) {

	}

	private final Map<String, Entry> objects = new ConcurrentHashMap<>();

	private volatile boolean unavailable;

	private final AtomicInteger bucketCalls = new AtomicInteger();

	/** Makes every call fail with {@link ObjectStorage.Unavailable} until switched back. */
	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	public int bucketCalls() {
		return this.bucketCalls.get();
	}

	public void clear() {
		this.objects.clear();
		this.unavailable = false;
	}

	public int count() {
		return this.objects.size();
	}

	public boolean contains(String key) {
		return this.objects.containsKey(key);
	}

	public List<String> keys() {
		return this.objects.keySet().stream().sorted().toList();
	}

	/** Stores bytes under a key as if they had been written at the given time. */
	public void putAt(String key, byte[] content, Instant lastModified) {
		this.objects.put(key, new Entry(content, "image/png", lastModified));
	}

	/** Makes an existing object look as if it had been written at the given time. */
	public void backdate(String key, Instant lastModified) {
		this.objects.computeIfPresent(key, (k, entry) -> new Entry(entry.content(), entry.contentType(), lastModified));
	}

	public byte[] bytesOf(String key) {
		return this.objects.get(key).content();
	}

	private void check() {
		if (this.unavailable) {
			throw new Unavailable("The fake storage server is switched off", null);
		}
	}

	@Override
	public void ensureBucket() {
		check();
		this.bucketCalls.incrementAndGet();
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		check();
		this.objects.put(key, new Entry(content.clone(), contentType, Instant.now()));
	}

	@Override
	public Optional<StoredObject> get(String key) {
		check();
		Entry entry = this.objects.get(key);
		return (entry == null) ? Optional.empty()
				: Optional.of(new StoredObject(new ByteArrayInputStream(entry.content()), entry.content().length,
						entry.contentType()));
	}

	@Override
	public void delete(String key) {
		check();
		this.objects.remove(key);
	}

	@Override
	public void deleteAll(Collection<String> keys) {
		check();
		keys.forEach(this.objects::remove);
	}

	@Override
	public Listing list(String prefix, String continuationToken, int maxKeys) {
		check();
		TreeMap<String, Entry> sorted = new TreeMap<>(this.objects);
		List<ObjectInfo> matching = sorted.entrySet()
			.stream()
			.filter((entry) -> entry.getKey().startsWith(prefix))
			.filter((entry) -> continuationToken == null || entry.getKey().compareTo(continuationToken) > 0)
			.limit(maxKeys + 1L)
			.map((entry) -> new ObjectInfo(entry.getKey(), entry.getValue().content().length, entry.getValue().lastModified()))
			.toList();
		if (matching.size() > maxKeys) {
			List<ObjectInfo> page = matching.subList(0, maxKeys);
			return new Listing(page, page.get(page.size() - 1).key());
		}
		return new Listing(matching, null);
	}

	@Override
	public URL presignPut(String key, String contentType, Duration validFor) {
		return address(key);
	}

	@Override
	public URL presignGet(String key, Duration validFor) {
		return address(key);
	}

	private static URL address(String key) {
		try {
			return URI.create("http://fake-storage.invalid/" + key).toURL();
		}
		catch (MalformedURLException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

package com.kibootia.backend.files;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.kibootia.backend.AdminApiTest;
import com.kibootia.backend.Await;
import com.kibootia.backend.files.application.ReconciliationJob;
import com.kibootia.backend.platform.jobs.RetentionPurgeJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The jobs and the event of the files module (ADR 0029, ADR 0034): the retention purge removes deleted files and their
 * objects once the period has passed, reconciliation removes objects nothing refers to once they are old enough to be
 * sure no upload is still being written, and erasing a user removes everything they stored at once.
 */
@SpringBootTest(properties = { "kibootia.identity.bootstrap-admin-email=" + AdminApiTest.ADMIN_EMAIL,
		"kibootia.identity.bootstrap-invite.enabled=false",
		"kibootia.jobs.soft-delete-retention-days=30", "kibootia.files.purge-batch-size=2",
		"kibootia.files.reconcile-grace=PT24H", "kibootia.rate-limit.limits.admin-actions.capacity=100000",
		"kibootia.rate-limit.limits.invitations.capacity=100000", "kibootia.events.initial-backoff=PT0S",
		"kibootia.events.max-backoff=PT0S", "kibootia.events.max-attempts=4", "kibootia.events.stale-after=PT1H" })
class FileJobsIT extends AdminApiTest {

	private static final String TENANT_HEX = "00000000000070008000000000000001";

	private static final UUID TENANT = UUID.fromString("00000000-0000-7000-8000-000000000001");

	@Autowired
	RetentionPurgeJob retention;

	@Autowired
	ReconciliationJob reconciliation;

	// --- fixtures ---------------------------------------------------------------------------------------------------

	private static String key(UUID file, String rendition) {
		return TENANT + "/avatar/" + file + "/" + rendition;
	}

	/** A file row with both objects present. {@code createdDaysAgo} ages the row; {@code deletedDaysAgo} null keeps it live. */
	private UUID file(UUID owner, int createdDaysAgo, Integer deletedDaysAgo) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				insert into stored_file (id, tenant_id, version, created_at, created_by, updated_at, updated_by, owner_id,
				  category, content_type, renditions)
				values (unhex(?), unhex(?), 0, utc_timestamp(6) - interval ? day, unhex(?), utc_timestamp(6), unhex(?),
				  unhex(?), 'avatar', 'image/png', 'small,large')
				""", hex(id), TENANT_HEX, createdDaysAgo, hex(owner), hex(owner), hex(owner));
		if (deletedDaysAgo != null) {
			this.jdbc.update("update stored_file set deleted_at = utc_timestamp(6) - interval ? day where id = unhex(?)",
					deletedDaysAgo, hex(id));
		}
		this.objectStorage.putAt(key(id, "small"), new byte[] { 1 }, Instant.now());
		this.objectStorage.putAt(key(id, "large"), new byte[] { 2 }, Instant.now());
		return id;
	}

	private boolean rowExists(UUID id) {
		return count("select count(*) from stored_file where id = unhex(?)", hex(id)) == 1;
	}

	private boolean hasObjects(UUID id) {
		return this.objectStorage.contains(key(id, "small")) || this.objectStorage.contains(key(id, "large"));
	}

	// --- retention --------------------------------------------------------------------------------------------------

	@Test
	@DisplayName("the retention purge removes the objects and then the rows of files deleted over 30 days ago, in batches, and no others")
	void retentionPurge() {
		UUID expiredA = file(ALICE, 60, 40);
		UUID expiredB = file(ALICE, 60, 35);
		UUID expiredC = file(BOB, 60, 31);
		UUID recentlyDeleted = file(ALICE, 5, 1);
		UUID live = file(ALICE, 60, null);

		this.retention.runOnce();

		for (UUID gone : new UUID[] { expiredA, expiredB, expiredC }) {
			assertThat(rowExists(gone)).as("row of %s", gone).isFalse();
			assertThat(hasObjects(gone)).as("objects of %s", gone).isFalse();
		}
		assertThat(rowExists(recentlyDeleted)).isTrue();
		assertThat(hasObjects(recentlyDeleted)).isTrue();
		assertThat(rowExists(live)).isTrue();
		assertThat(hasObjects(live)).isTrue();
	}

	@Test
	@DisplayName("when the storage server is down the purge removes no row, so the next run finishes the job")
	void retentionPurgeWithStorageDown() {
		UUID expired = file(ALICE, 60, 40);
		this.objectStorage.setUnavailable(true);

		this.retention.runOnce();

		assertThat(rowExists(expired)).isTrue();
		this.objectStorage.setUnavailable(false);
		this.retention.runOnce();
		assertThat(rowExists(expired)).isFalse();
		assertThat(hasObjects(expired)).isFalse();
	}

	// --- reconciliation ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("reconciliation removes old objects nothing refers to, keeps young ones, referenced ones and strangers, and only reports a file with no object")
	void reconciliation() {
		UUID referenced = file(ALICE, 3, null);
		this.objectStorage.backdate(key(referenced, "small"), Instant.now().minus(Duration.ofDays(3)));
		this.objectStorage.backdate(key(referenced, "large"), Instant.now().minus(Duration.ofDays(3)));
		UUID oldOrphan = UUID.randomUUID();
		this.objectStorage.putAt(key(oldOrphan, "large"), new byte[] { 3 }, Instant.now().minus(Duration.ofDays(3)));
		this.objectStorage.putAt(key(oldOrphan, "small"), new byte[] { 3 }, Instant.now().minus(Duration.ofDays(3)));
		UUID youngOrphan = UUID.randomUUID();
		this.objectStorage.putAt(key(youngOrphan, "large"), new byte[] { 4 }, Instant.now().minus(Duration.ofHours(1)));
		this.objectStorage.putAt(TENANT + "/readme.txt", new byte[] { 5 }, Instant.now().minus(Duration.ofDays(30)));
		this.objectStorage.putAt("another-tenant/avatar/" + UUID.randomUUID() + "/large", new byte[] { 6 },
				Instant.now().minus(Duration.ofDays(30)));
		UUID lost = file(BOB, 3, null);
		this.objectStorage.delete(key(lost, "small"));
		this.objectStorage.delete(key(lost, "large"));
		UUID justUploaded = file(BOB, 0, null);
		this.objectStorage.delete(key(justUploaded, "small"));
		this.objectStorage.delete(key(justUploaded, "large"));

		ReconciliationJob.Result result = this.reconciliation.runOnce();

		assertThat(result.orphanObjectsRemoved()).isEqualTo(2);
		assertThat(result.filesMissingObjects()).as("only the old file; the one just written is within the grace period")
			.isEqualTo(1);
		assertThat(hasObjects(oldOrphan)).isFalse();
		assertThat(hasObjects(youngOrphan)).isTrue();
		assertThat(hasObjects(referenced)).isTrue();
		assertThat(this.objectStorage.contains(TENANT + "/readme.txt")).isTrue();
		assertThat(this.objectStorage.keys()).anyMatch((key) -> key.startsWith("another-tenant/"));
		assertThat(rowExists(lost)).as("metadata is never deleted on the word of a listing").isTrue();
	}

	@Test
	@DisplayName("a storage outage during reconciliation deletes nothing and does not stop the job")
	void reconciliationDuringAnOutage() {
		UUID oldOrphan = UUID.randomUUID();
		this.objectStorage.putAt(key(oldOrphan, "large"), new byte[] { 3 }, Instant.now().minus(Duration.ofDays(3)));
		UUID kept = file(ALICE, 3, null);
		this.objectStorage.setUnavailable(true);

		ReconciliationJob.Result result = this.reconciliation.runOnce();

		assertThat(result.orphanObjectsRemoved()).isZero();
		assertThat(rowExists(kept)).isTrue();
		this.objectStorage.setUnavailable(false);
		assertThat(hasObjects(oldOrphan)).isTrue();
	}

	// --- erasing a user ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("erasing a user removes every file they stored, deleted ones included, at once and not anyone else's")
	void erasingAUser() throws Exception {
		ensureAdmin();
		signIn(ALICE);
		signIn(BOB);
		BufferedImage picture = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(picture, "png", png);
		for (UUID who : new UUID[] { ALICE, ALICE, BOB }) {
			this.mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/me/avatar")
				.file(new MockMultipartFile("file", "a.png", "image/png", png.toByteArray()))
				.with(user(who))).andExpect(status().isOk());
		}
		assertThat(count("select count(*) from stored_file where owner_id = unhex(?)", hex(ALICE))).as("one live, one replaced")
			.isEqualTo(2);
		assertThat(this.objectStorage.count()).isEqualTo(6);

		delete(admin(), "/api/v1/users/" + ALICE).andExpect(status().isNoContent());
		post(admin(), "/api/v1/users/" + ALICE + "/erase", null).andExpect(status().isAccepted());

		Await.until("the files of the erased user to be removed",
				() -> count("select count(*) from stored_file where owner_id = unhex(?)", hex(ALICE)) == 0);
		assertThat(this.objectStorage.count()).as("only Bob's two renditions are left").isEqualTo(2);
		assertThat(count("select count(*) from stored_file where owner_id = unhex(?)", hex(BOB))).isEqualTo(1);
	}

}

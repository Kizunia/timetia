package com.kibootia.backend.timetable.application;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Which live lectures a list asks for: those in a date range that the caller may see, narrowed by optional filters. The
 * store turns it into one query, so a page and its total are correct (the list is filtered before it is paged).
 *
 * @param batch only lectures this batch attends, together with {@code batchDivision} (the division of that batch), since a
 *            theory lecture is for every batch of its division; both null for no batch filter
 */
public record OccurrenceQuery(LocalDate from, LocalDate to, UUID division, UUID batch, UUID batchDivision, UUID faculty,
		Visibility visibility) {

	/**
	 * Where the caller may see lectures, as conditions on the occurrence's own columns. A lecture is visible when any one
	 * holds. Nothing here means the caller sees nothing, so the store must not be asked.
	 *
	 * @param everywhere the caller may see every lecture
	 * @param departments lectures of these departments
	 * @param divisions lectures of these divisions, of any kind
	 * @param batches lab and tutorial lectures of these batches
	 * @param divisionsOfBatches theory lectures of these divisions, which are for every batch in them: the divisions of the
	 *            batches the caller holds
	 * @param faculty lectures taught by these faculty members, for a faculty member's own timetable
	 */
	public record Visibility(boolean everywhere, Set<UUID> departments, Set<UUID> divisions, Set<UUID> batches,
			Set<UUID> divisionsOfBatches, Set<UUID> faculty) {

		public boolean isEmpty() {
			return !this.everywhere && this.departments.isEmpty() && this.divisions.isEmpty() && this.batches.isEmpty()
					&& this.divisionsOfBatches.isEmpty() && this.faculty.isEmpty();
		}

	}

}

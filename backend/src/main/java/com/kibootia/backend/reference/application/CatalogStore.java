package com.kibootia.backend.reference.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Where the timetable's reference catalog is kept: subjects, rooms, faculty and academic terms. The use cases depend on
 * this port, never on JPA (ADR 0003). Reads and writes act on the current tenant's live rows; a code is unique per kind.
 */
public interface CatalogStore {

	// --- subjects ---------------------------------------------------------------------------------------------------

	Subject insertSubject(String code, String name);

	Optional<Subject> findSubject(UUID id);

	Page<Subject> searchSubjects(String text, Pageable pageable);

	Subject renameSubject(UUID id, long expectedVersion, String name);

	void deleteSubject(UUID id);

	boolean subjectExists(UUID id);

	boolean subjectCodeInUse(String code);

	// --- rooms ------------------------------------------------------------------------------------------------------

	Room insertRoom(String code, String name, RoomType roomType, Integer capacity);

	Optional<Room> findRoom(UUID id);

	Page<Room> searchRooms(String text, Pageable pageable);

	Room renameRoom(UUID id, long expectedVersion, String name);

	void deleteRoom(UUID id);

	boolean roomExists(UUID id);

	boolean roomCodeInUse(String code);

	// --- faculty ----------------------------------------------------------------------------------------------------

	Faculty insertFaculty(String code, String name, UUID departmentId, UUID userId);

	Optional<Faculty> findFaculty(UUID id);

	Page<Faculty> searchFaculty(UUID department, String text, Pageable pageable);

	Faculty renameFaculty(UUID id, long expectedVersion, String name);

	/** Links the faculty member to the account they sign in as, or removes the link when {@code userId} is null. */
	Faculty updateFacultyUser(UUID id, long expectedVersion, UUID userId);

	void deleteFaculty(UUID id);

	boolean facultyExists(UUID id);

	boolean facultyCodeInUse(String code);

	// --- terms ------------------------------------------------------------------------------------------------------

	AcademicTerm insertTerm(String code, String name, LocalDate startDate, LocalDate endDate);

	Optional<AcademicTerm> findTerm(UUID id);

	Page<AcademicTerm> searchTerms(String text, Pageable pageable);

	AcademicTerm renameTerm(UUID id, long expectedVersion, String name);

	void deleteTerm(UUID id);

	boolean termExists(UUID id);

	boolean termCodeInUse(String code);

	// --- bulk lookups, for the directory other modules read through --------------------------------------------------

	List<Subject> findSubjects(Collection<UUID> ids);

	List<Room> findRooms(Collection<UUID> ids);

	List<Faculty> findFacultyByIds(Collection<UUID> ids);

	/** The ids of the live faculty records that belong to this user account (normally one). */
	List<UUID> findFacultyIdsOfUser(UUID userId);

}

package com.kibootia.backend.reference.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.kibootia.backend.reference.application.AcademicTerm;
import com.kibootia.backend.reference.application.CatalogStore;
import com.kibootia.backend.reference.application.Faculty;
import com.kibootia.backend.reference.application.Room;
import com.kibootia.backend.reference.application.RoomType;
import com.kibootia.backend.reference.application.Subject;

/** Keeps the reference catalog in MySQL through JPA. A new row is flushed at once so a duplicate code is reported now. */
@Component
class JpaCatalogStore implements CatalogStore {

	private final SubjectRepository subjects;

	private final RoomRepository rooms;

	private final FacultyRepository faculty;

	private final AcademicTermRepository terms;

	JpaCatalogStore(SubjectRepository subjects, RoomRepository rooms, FacultyRepository faculty,
			AcademicTermRepository terms) {
		this.subjects = subjects;
		this.rooms = rooms;
		this.faculty = faculty;
		this.terms = terms;
	}

	// --- subjects ---------------------------------------------------------------------------------------------------

	@Override
	public Subject insertSubject(String code, String name) {
		return CatalogEntityMapper.toModel(this.subjects.saveAndFlush(new SubjectEntity(code, name)));
	}

	@Override
	public Optional<Subject> findSubject(UUID id) {
		return this.subjects.findById(id).map(CatalogEntityMapper::toModel);
	}

	@Override
	public Page<Subject> searchSubjects(String text, Pageable pageable) {
		return this.subjects.search(likePattern(text), pageable).map(CatalogEntityMapper::toModel);
	}

	@Override
	public Subject renameSubject(UUID id, long expectedVersion, String name) {
		SubjectEntity entity = this.subjects.findById(id).orElseThrow();
		requireVersion(entity.getVersion(), expectedVersion, SubjectEntity.class, id);
		entity.rename(name);
		return CatalogEntityMapper.toModel(this.subjects.saveAndFlush(entity));
	}

	@Override
	public void deleteSubject(UUID id) {
		this.subjects.findById(id).ifPresent(this.subjects::delete);
		this.subjects.flush();
	}

	@Override
	public boolean subjectExists(UUID id) {
		return this.subjects.findById(id).isPresent();
	}

	@Override
	public boolean subjectCodeInUse(String code) {
		return this.subjects.existsByCode(code);
	}

	// --- rooms ------------------------------------------------------------------------------------------------------

	@Override
	public Room insertRoom(String code, String name, RoomType roomType, Integer capacity) {
		return CatalogEntityMapper.toModel(this.rooms.saveAndFlush(new RoomEntity(code, name, roomType, capacity)));
	}

	@Override
	public Optional<Room> findRoom(UUID id) {
		return this.rooms.findById(id).map(CatalogEntityMapper::toModel);
	}

	@Override
	public Page<Room> searchRooms(String text, Pageable pageable) {
		return this.rooms.search(likePattern(text), pageable).map(CatalogEntityMapper::toModel);
	}

	@Override
	public Room renameRoom(UUID id, long expectedVersion, String name) {
		RoomEntity entity = this.rooms.findById(id).orElseThrow();
		requireVersion(entity.getVersion(), expectedVersion, RoomEntity.class, id);
		entity.rename(name);
		return CatalogEntityMapper.toModel(this.rooms.saveAndFlush(entity));
	}

	@Override
	public void deleteRoom(UUID id) {
		this.rooms.findById(id).ifPresent(this.rooms::delete);
		this.rooms.flush();
	}

	@Override
	public boolean roomExists(UUID id) {
		return this.rooms.findById(id).isPresent();
	}

	@Override
	public boolean roomCodeInUse(String code) {
		return this.rooms.existsByCode(code);
	}

	// --- faculty ----------------------------------------------------------------------------------------------------

	@Override
	public Faculty insertFaculty(String code, String name, UUID departmentId, UUID userId) {
		return CatalogEntityMapper.toModel(this.faculty.saveAndFlush(new FacultyEntity(code, name, departmentId, userId)));
	}

	@Override
	public Optional<Faculty> findFaculty(UUID id) {
		return this.faculty.findById(id).map(CatalogEntityMapper::toModel);
	}

	@Override
	public Page<Faculty> searchFaculty(UUID department, String text, Pageable pageable) {
		return this.faculty.search(department, likePattern(text), pageable).map(CatalogEntityMapper::toModel);
	}

	@Override
	public Faculty renameFaculty(UUID id, long expectedVersion, String name) {
		FacultyEntity entity = this.faculty.findById(id).orElseThrow();
		requireVersion(entity.getVersion(), expectedVersion, FacultyEntity.class, id);
		entity.rename(name);
		return CatalogEntityMapper.toModel(this.faculty.saveAndFlush(entity));
	}

	@Override
	public void deleteFaculty(UUID id) {
		this.faculty.findById(id).ifPresent(this.faculty::delete);
		this.faculty.flush();
	}

	@Override
	public boolean facultyExists(UUID id) {
		return this.faculty.findById(id).isPresent();
	}

	@Override
	public boolean facultyCodeInUse(String code) {
		return this.faculty.existsByCode(code);
	}

	// --- terms ------------------------------------------------------------------------------------------------------

	@Override
	public AcademicTerm insertTerm(String code, String name, LocalDate startDate, LocalDate endDate) {
		return CatalogEntityMapper.toModel(this.terms.saveAndFlush(new AcademicTermEntity(code, name, startDate, endDate)));
	}

	@Override
	public Optional<AcademicTerm> findTerm(UUID id) {
		return this.terms.findById(id).map(CatalogEntityMapper::toModel);
	}

	@Override
	public Page<AcademicTerm> searchTerms(String text, Pageable pageable) {
		return this.terms.search(likePattern(text), pageable).map(CatalogEntityMapper::toModel);
	}

	@Override
	public AcademicTerm renameTerm(UUID id, long expectedVersion, String name) {
		AcademicTermEntity entity = this.terms.findById(id).orElseThrow();
		requireVersion(entity.getVersion(), expectedVersion, AcademicTermEntity.class, id);
		entity.rename(name);
		return CatalogEntityMapper.toModel(this.terms.saveAndFlush(entity));
	}

	@Override
	public void deleteTerm(UUID id) {
		this.terms.findById(id).ifPresent(this.terms::delete);
		this.terms.flush();
	}

	@Override
	public boolean termExists(UUID id) {
		return this.terms.findById(id).isPresent();
	}

	@Override
	public boolean termCodeInUse(String code) {
		return this.terms.existsByCode(code);
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static void requireVersion(long actual, long expected, Class<?> type, UUID id) {
		if (actual != expected) {
			throw new ObjectOptimisticLockingFailureException(type, id);
		}
	}

	private static String likePattern(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String escaped = text.strip().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escaped + "%";
	}

}

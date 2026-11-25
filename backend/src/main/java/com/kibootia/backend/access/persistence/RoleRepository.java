package com.kibootia.backend.access.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.kibootia.backend.access.application.Role;

/** Entity queries are filtered by tenant and skip deleted roles on their own. */
interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

	@Query("select r from RoleEntity r where r.builtIn = :builtIn")
	Optional<RoleEntity> findByBuiltIn(String builtIn);

	default Optional<RoleEntity> findByBuiltIn(Role.BuiltIn which) {
		return findByBuiltIn(which.name());
	}

	@Query("select count(r) > 0 from RoleEntity r where r.name = :name and (:except is null or r.id <> :except)")
	boolean nameInUse(String name, UUID except);

	@Query("select r from RoleEntity r where r.name = :name and r.scopeType is not null")
	Optional<RoleEntity> findScopedByName(String name);

	List<RoleEntity> findAllByIdIn(Collection<UUID> ids);

	/** {@code SELECT ... FOR UPDATE}: whoever holds the lock decides who may hold the role until the transaction ends. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RoleEntity r where r.id = :id")
	Optional<RoleEntity> lockById(UUID id);

}

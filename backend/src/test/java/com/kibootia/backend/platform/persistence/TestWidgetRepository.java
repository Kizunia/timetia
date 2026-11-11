package com.kibootia.backend.platform.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TestWidgetRepository extends JpaRepository<TestWidget, UUID> {

	@Query("select w from TestWidget w where w.name like :prefix%")
	List<TestWidget> findByPrefix(String prefix);

	@Modifying
	@Query("update TestWidget w set w.name = :name")
	int renameAll(String name);

	@Modifying
	@Query("delete from TestWidget w")
	int deleteAllRows();

}

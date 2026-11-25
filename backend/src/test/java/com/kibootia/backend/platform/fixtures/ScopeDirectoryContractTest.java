package com.kibootia.backend.platform.fixtures;

import java.util.UUID;

import com.kibootia.backend.platform.authorization.ScopeDirectory;
import com.kibootia.backend.platform.fixtures.ScopeFixtures.FakeScopeDirectory;

/**
 * Holds the test double to the same rules as a real provider ({@link ScopeDirectoryContract}), so the tests that use it
 * do not pass on behaviour the real one is not allowed to have. The provider's own test extends the contract the same way.
 */
class ScopeDirectoryContractTest extends ScopeDirectoryContract {

	private FakeScopeDirectory directory;

	@Override
	protected ScopeDirectory newDirectory() {
		this.directory = new FakeScopeDirectory();
		return this.directory;
	}

	@Override
	protected void addPlace(String kind, UUID id, String code) {
		this.directory.add(kind, id, code);
	}

	@Override
	protected void deletePlace(String kind, UUID id) {
		this.directory.remove(kind, id);
	}

	@Override
	protected void addPlaceInAnotherTenant(String kind, UUID id, String code) {
		// The double has one tenant: a place of another one is simply never added to it. A real provider must do the work.
	}

}

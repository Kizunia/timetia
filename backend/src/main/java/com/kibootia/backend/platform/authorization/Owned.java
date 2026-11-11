package com.kibootia.backend.platform.authorization;

import java.util.UUID;

/**
 * A record that belongs to a user. Pass it to {@link Authorization#require(String, Object)} so that an {@link
 * Scope#OWN} permission can be checked against it.
 */
public interface Owned {

	UUID ownerId();

}

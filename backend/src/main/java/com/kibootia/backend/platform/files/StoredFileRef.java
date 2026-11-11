package com.kibootia.backend.platform.files;

import java.util.UUID;

/**
 * What the owner of a file keeps to find it again: the file's ID. The owning module stores it beside its own data, as
 * {@code identity} does with the avatar of a profile.
 */
public record StoredFileRef(UUID id) {

}

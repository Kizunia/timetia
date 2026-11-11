package com.kibootia.backend.platform.persistence;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Converts between {@link UUID} and the 16 bytes of a {@code BINARY(16)} column, for hand-written SQL. */
public final class UuidBytes {

	private UuidBytes() {
	}

	public static byte[] toBytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}

	public static UUID fromBytes(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		return new UUID(buffer.getLong(), buffer.getLong());
	}

}

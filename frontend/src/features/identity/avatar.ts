import { IMAGE_MAX_BYTES, IMAGE_TYPES, checkImageFile } from "@/lib/images";

/** What an avatar may be: the same as any picture the files module accepts (ADR 0029). */
export const AVATAR_TYPES = IMAGE_TYPES;

export const AVATAR_MAX_BYTES = IMAGE_MAX_BYTES;

/** Why the browser would not even send this file, or null when it may be sent. */
export const checkAvatarFile = checkImageFile;

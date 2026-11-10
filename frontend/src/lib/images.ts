/**
 * What an uploaded picture may be, for a quick answer in the browser. The backend decides from the bytes and always checks
 * again (ADR 0029): this only saves a round trip for the common mistakes. The limits are the files module's own.
 */
export const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;

export const IMAGE_MAX_BYTES = 5 * 1024 * 1024;

/** Why the browser would not even send this file, or null when it may be sent. */
export function checkImageFile(file: Pick<File, "type" | "size">): string | null {
  if (!(IMAGE_TYPES as readonly string[]).includes(file.type)) return "Choose a JPEG, PNG or WebP image.";
  if (file.size > IMAGE_MAX_BYTES) return "Choose an image smaller than 5 MB.";
  if (file.size === 0) return "That file is empty.";
  return null;
}

/** What a screen shows where a name is missing: the record was deleted, or the person may not see it. */
export const UNKNOWN_NAME = "—";

/**
 * Turns an ID into a name. Build it once from a directory list; it answers an em dash for an ID that is null or not in
 * the list, so a deleted record never breaks a screen.
 */
export function nameIndex<T extends { id: string }>(
  items: readonly T[] | undefined,
  label: (item: T) => string,
): (id: string | null | undefined) => string {
  const names = new Map((items ?? []).map((item) => [item.id, label(item)]));
  return (id) => (id ? (names.get(id) ?? UNKNOWN_NAME) : UNKNOWN_NAME);
}

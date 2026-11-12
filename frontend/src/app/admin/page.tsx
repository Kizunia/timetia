import { redirect } from "next/navigation";

import { visibleAdminSections } from "@/components/SiteHeader";
import { getMe } from "@/lib/server-me";

export const dynamic = "force-dynamic";

/** The administration area has no page of its own: it opens the first section the user may see. */
export default async function AdminIndex() {
  const me = await getMe();
  const first = me ? visibleAdminSections(me)[0] : undefined;
  // With no section to show, the frame explains that the user has no access.
  redirect(first ? first.href : "/admin/users");
}

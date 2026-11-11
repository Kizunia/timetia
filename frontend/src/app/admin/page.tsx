import { redirect } from "next/navigation";

import { ADMIN_SECTIONS } from "@/components/SiteHeader";
import { getMe } from "@/lib/server-me";

export const dynamic = "force-dynamic";

/** The administration area has no page of its own: it opens the first section the user may see. */
export default async function AdminIndex() {
  const me = await getMe();
  const first = ADMIN_SECTIONS.find((section) => me?.permissions.includes(section.permission));
  // With no section to show, the frame explains that the user has no access.
  redirect(first ? first.href : "/admin/users");
}

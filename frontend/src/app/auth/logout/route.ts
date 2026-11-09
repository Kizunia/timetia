import { defaultDeps } from "@/lib/bff/deps";
import { logout } from "@/lib/bff/auth";

export const dynamic = "force-dynamic";

export function POST(request: Request) {
  return logout(request, defaultDeps());
}

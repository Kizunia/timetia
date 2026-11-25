import { defaultDeps } from "@/lib/bff/deps";
import { login } from "@/lib/bff/auth";

export const dynamic = "force-dynamic";

export function GET(request: Request) {
  return login(request, defaultDeps());
}

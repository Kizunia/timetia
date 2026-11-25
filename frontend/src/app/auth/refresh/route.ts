import { refreshNow } from "@/lib/bff/auth";
import { defaultDeps } from "@/lib/bff/deps";

export const dynamic = "force-dynamic";

export function POST(request: Request) {
  return refreshNow(request, defaultDeps());
}

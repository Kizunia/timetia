import { defaultDeps } from "@/lib/bff/deps";
import { callback } from "@/lib/bff/auth";

export const dynamic = "force-dynamic";

export function GET(request: Request) {
  return callback(request, defaultDeps());
}

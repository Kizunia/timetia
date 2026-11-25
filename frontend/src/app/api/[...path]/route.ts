import { defaultDeps } from "@/lib/bff/deps";
import { proxyApi } from "@/lib/bff/proxy";

export const dynamic = "force-dynamic";

function handle(request: Request) {
  return proxyApi(request, defaultDeps());
}

export const GET = handle;
export const HEAD = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
// Any other method (for example OPTIONS) gets a 405 from the proxy, so no CORS preflight is ever answered.
export const OPTIONS = handle;

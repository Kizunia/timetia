export const dynamic = "force-dynamic";

/** Liveness for the container health check (ADR 0047). It checks only that this server answers. */
export function GET() {
  return Response.json({ status: "ok" }, { headers: { "Cache-Control": "no-store" } });
}

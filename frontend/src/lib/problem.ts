/** RFC 9457 Problem Details for errors the BFF produces itself, in the same shape as the Spring API's (`code`). */
export function problem(status: number, code: string, detail: string, extraHeaders?: HeadersInit): Response {
  const titles: Record<number, string> = {
    400: "Bad Request",
    401: "Unauthorized",
    403: "Forbidden",
    404: "Not Found",
    405: "Method Not Allowed",
    502: "Bad Gateway",
  };
  const headers = new Headers(extraHeaders);
  headers.set("Content-Type", "application/problem+json");
  headers.set("Cache-Control", "no-store");
  return new Response(JSON.stringify({ type: "about:blank", title: titles[status] ?? "Error", status, detail, code }), {
    status,
    headers,
  });
}

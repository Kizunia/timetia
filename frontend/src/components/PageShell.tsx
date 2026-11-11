import { SiteHeader } from "@/components/SiteHeader";
import { PermissionsProvider } from "@/features/identity/permissions";
import { ThemeSync } from "@/features/identity/ThemeSync";
import { UnverifiedBanner } from "@/features/identity/UnverifiedBanner";
import { getMe } from "@/lib/server-me";
import { cn } from "@/lib/utils";

/**
 * The frame of every page: the header, the notice for a user who has not verified their email address, and the page's
 * own content in `<main>`. It loads the signed-in user once per request and makes their permissions available to the
 * client components inside, so screens can hide what the user may not do (usability only; the backend decides).
 *
 * @param signedInOnly send a visitor with no session to sign in; without it the page also works signed out
 */
export async function PageShell({
  children,
  width = "narrow",
  signedInOnly = true,
}: {
  children: React.ReactNode;
  width?: "narrow" | "wide";
  signedInOnly?: boolean;
}) {
  const me = await getMe(!signedInOnly);
  const content = (
    <main id="main" className={cn("mx-auto px-4 py-10 sm:px-6", width === "wide" ? "max-w-6xl" : "max-w-2xl")}>
      {children}
    </main>
  );
  return (
    <>
      <SiteHeader me={me} />
      {me && !me.emailVerified && <UnverifiedBanner />}
      {me ? (
        <PermissionsProvider me={me}>
          <ThemeSync theme={me.theme} />
          {content}
        </PermissionsProvider>
      ) : (
        content
      )}
    </>
  );
}

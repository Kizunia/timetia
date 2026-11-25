/**
 * Reloads the page. One small function so that a test can replace it: the browser's own `location.reload` cannot be
 * replaced in a test.
 */
export function reloadPage(): void {
  window.location.reload();
}

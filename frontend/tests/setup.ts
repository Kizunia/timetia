import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Testing Library only cleans up after itself when the test runner has global hooks, which this one does not.
afterEach(() => cleanup());

// jsdom has no layout. Radix UI (checkboxes, selects, dialogs) measures elements with these, so give it ones that do
// nothing; a test that needs real sizes belongs in the browser tests.
if (typeof window !== "undefined") {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  window.ResizeObserver ??= ResizeObserverStub;
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.setPointerCapture ??= () => {};
  Element.prototype.releasePointerCapture ??= () => {};
  Element.prototype.scrollIntoView ??= () => {};
}

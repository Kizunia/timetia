import { Button } from "@/components/ui/button";

/** Previous and next for a paged list. Pages count from 0 in the API and from 1 on screen. */
export function Pager({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
}) {
  return (
    <nav aria-label="Pages" className="mt-4 flex items-center justify-between gap-4">
      <p className="text-sm text-muted-foreground" aria-live="polite">
        {totalElements === 0 ? "Nothing to show" : `Page ${page + 1} of ${Math.max(totalPages, 1)} · ${totalElements} in all`}
      </p>
      <div className="flex gap-2">
        <Button type="button" variant="outline" size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          Previous
        </Button>
        <Button type="button" variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next
        </Button>
      </div>
    </nav>
  );
}

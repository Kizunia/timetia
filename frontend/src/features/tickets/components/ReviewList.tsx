import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Review, Ticket } from "@/features/tickets/api";
import { CAPACITY_LABEL, REVIEW_STATUS_LABEL } from "@/lib/labels";

function ReviewTable({ reviews, caption }: { reviews: readonly Review[]; caption: string }) {
  return (
    <Table>
      <caption className="sr-only">{caption}</caption>
      <TableHeader>
        <TableRow>
          <TableHead>Person</TableHead>
          <TableHead>Role</TableHead>
          <TableHead>Answer</TableHead>
          <TableHead>Reason</TableHead>
          <TableHead>When</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {reviews.map((review, index) => (
          <TableRow key={`${review.reviewerUserId}-${review.revisionNo}-${index}`}>
            <TableCell>{review.reviewerName ?? "—"}</TableCell>
            <TableCell>{CAPACITY_LABEL[review.capacity]}</TableCell>
            <TableCell>
              {REVIEW_STATUS_LABEL[review.status]}
              {review.escalation && " (escalated)"}
            </TableCell>
            <TableCell>{review.reason ?? ""}</TableCell>
            <TableCell>{review.decidedAt ? new Date(review.decidedAt).toLocaleString() : ""}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/** Who was asked, and what they answered. The round the ticket is in comes first; earlier rounds are folded away. */
export function ReviewList({ ticket }: { ticket: Ticket }) {
  const current = ticket.reviews.filter((review) => review.revisionNo === ticket.revisionNo);
  const earlier = ticket.reviews.filter((review) => review.revisionNo !== ticket.revisionNo);
  if (ticket.reviews.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      {current.length > 0 ? (
        <ReviewTable reviews={current} caption="Reviews of this round" />
      ) : (
        <p className="text-sm text-muted-foreground">Nobody has been asked in this round yet.</p>
      )}
      {earlier.length > 0 && (
        <details>
          <summary className="cursor-pointer text-sm font-medium text-primary">Earlier rounds</summary>
          <ReviewTable reviews={earlier} caption="Reviews of earlier rounds" />
        </details>
      )}
    </div>
  );
}

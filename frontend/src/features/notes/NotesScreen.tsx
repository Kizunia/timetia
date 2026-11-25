"use client";

import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Can, usePermissions } from "@/features/identity/permissions";
import { useNotes, useRestoreNote } from "@/features/notes/api";

type View = "mine" | "everyone" | "deleted";

const SORTS = [
  { value: "createdAt,desc", label: "Newest first" },
  { value: "createdAt,asc", label: "Oldest first" },
  { value: "updatedAt,desc", label: "Recently changed" },
  { value: "title,asc", label: "Title A to Z" },
] as const;

/**
 * The notes list: the reference for a list that depends on what the caller may see (ADR 0043, ADR 0044). Everyone sees
 * their own notes. Someone with `notes.notes.view-any` can switch to everyone's, and someone with `notes.notes.restore`
 * can look at the deleted ones and bring one back. The choices only decide what is offered; the API limits the list to
 * what the caller may see whatever is asked for.
 */
export function NotesScreen() {
  const { me, has } = usePermissions();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [sort, setSort] = useState<string>(SORTS[0].value);
  const [view, setView] = useState<View>("mine");
  const restore = useRestoreNote();

  const views: { value: View; label: string }[] = [{ value: "mine", label: "My notes" }];
  if (has("notes.notes.view-any")) views.push({ value: "everyone", label: "Everyone's notes" });
  if (has("notes.notes.restore")) views.push({ value: "deleted", label: "Deleted notes" });

  const notes = useNotes({
    page,
    size: 20,
    q: q || undefined,
    sort,
    owner: view === "mine" ? me.id : undefined,
    deleted: view === "deleted" ? true : undefined,
  });
  const showOwner = view !== "mine";

  async function bringBack(id: string) {
    restore.reset();
    try {
      await restore.mutateAsync(id);
      toast.success("Note restored");
    } catch {
      // The reason is shown above the list, from the mutation's error.
    }
  }

  return (
    <section aria-labelledby="notes-heading">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="notes-heading" className="text-2xl font-semibold tracking-tight">
          Notes
        </h1>
        <Can permission="notes.notes.create">
          <Button asChild>
            <Link href="/notes/new">New note</Link>
          </Button>
        </Can>
      </div>

      <form
        role="search"
        className="mt-6 flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          setQ(search.trim());
          setPage(0);
        }}
      >
        <div className="grid gap-1.5">
          <Label htmlFor="note-search">Search</Label>
          <Input id="note-search" type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Title or text" className="w-72" />
        </div>
        {views.length > 1 && (
          <div className="grid gap-1.5">
            <Label htmlFor="note-view">Show</Label>
            <Select
              value={view}
              onValueChange={(value) => {
                setView(value as View);
                setPage(0);
              }}
            >
              <SelectTrigger id="note-view" className="w-48">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {views.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        <div className="grid gap-1.5">
          <Label htmlFor="note-sort">Sort by</Label>
          <Select
            value={sort}
            onValueChange={(value) => {
              setSort(value);
              setPage(0);
            }}
          >
            <SelectTrigger id="note-sort" className="w-48">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {SORTS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button type="submit" variant="secondary">
          Search
        </Button>
      </form>

      <div className="mt-6">
        {notes.isError && <ErrorAlert error={notes.error} title="The notes could not be loaded" />}
        {restore.isError && <ErrorAlert error={restore.error} title="The note was not restored" />}
        {notes.isPending && (
          <div className="grid gap-2" aria-busy="true" aria-label="Loading notes">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        )}
        {notes.data && (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  {showOwner && <TableHead>Written by</TableHead>}
                  <TableHead>Changed</TableHead>
                  {view === "deleted" && (
                    <TableHead>
                      <span className="sr-only">Actions</span>
                    </TableHead>
                  )}
                </TableRow>
              </TableHeader>
              <TableBody>
                {notes.data.items.map((note) => (
                  <TableRow key={note.id} data-testid="note-row">
                    <TableCell>
                      <div className="flex flex-wrap items-center gap-2">
                        {view === "deleted" ? (
                          <span className="font-medium">{note.title}</span>
                        ) : (
                          <Link href={`/notes/${note.id}`} className="font-medium text-primary underline underline-offset-2">
                            {note.title}
                          </Link>
                        )}
                        {note.attachmentVersion && me.filesEnabled && view !== "deleted" && <Badge variant="outline">Picture</Badge>}
                      </div>
                    </TableCell>
                    {showOwner && <TableCell>{note.ownerId === me.id ? "You" : `User ${note.ownerId.slice(0, 8)}`}</TableCell>}
                    <TableCell>{new Date(note.updatedAt).toLocaleDateString()}</TableCell>
                    {view === "deleted" && (
                      <TableCell className="text-right">
                        <Button type="button" size="sm" variant="outline" disabled={restore.isPending} onClick={() => void bringBack(note.id)}>
                          Restore<span className="sr-only"> {note.title}</span>
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
                {notes.data.items.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={view === "deleted" ? 4 : showOwner ? 3 : 2} className="text-center text-muted-foreground">
                      {q ? "No notes match." : view === "deleted" ? "There are no deleted notes." : "There are no notes yet."}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
            <Pager
              page={notes.data.page.number}
              totalPages={notes.data.page.totalPages}
              totalElements={notes.data.page.totalElements}
              onPage={setPage}
            />
          </>
        )}
      </div>
    </section>
  );
}

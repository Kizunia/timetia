"use client";

import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDeleteRow, useRenameRow, type Row } from "@/features/setup/api";
import type { Resource } from "@/features/setup/resources";

/** Changes the name, which is the only thing that can change. The version read guards against overwriting someone else. */
export function RenameDialog({ resource, row, onClose }: { resource: Resource; row: Row; onClose: () => void }) {
  const rename = useRenameRow(resource.key);
  const [name, setName] = useState(row.name ?? "");

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      await rename.mutateAsync({ id: row.id, name: name.trim(), version: row.version });
      toast.success("Renamed");
      onClose();
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Rename {row.code}</DialogTitle>
          <DialogDescription>The code stays the same.</DialogDescription>
        </DialogHeader>
        <form onSubmit={(event) => void save(event)} className="grid gap-4">
          <div className="grid gap-2">
            <Label htmlFor="rename-name">Name</Label>
            <Input id="rename-name" value={name} maxLength={150} onChange={(event) => setName(event.target.value)} />
          </div>
          {rename.isError && <ErrorAlert error={rename.error} title="It was not renamed" />}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={!name.trim() || rename.isPending}>
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function DeleteDialog({ resource, row, onClose }: { resource: Resource; row: Row; onClose: () => void }) {
  const remove = useDeleteRow(resource.key);

  const confirm = async () => {
    try {
      await remove.mutateAsync(row.id);
      toast.success("Deleted");
      onClose();
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <AlertDialog open onOpenChange={(open) => !open && onClose()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete {row.code ?? `this ${resource.one}`}?</AlertDialogTitle>
          <AlertDialogDescription>
            Lectures and tickets that refer to it keep working but will show a blank name.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {remove.isError && <ErrorAlert error={remove.error} title="It was not deleted" />}
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <Button variant="destructive" onClick={() => void confirm()} disabled={remove.isPending}>
            Delete
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

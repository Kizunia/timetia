"use client";

import { useState } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

/** A small dialog that picks one thing from a list and sends it: a teacher to mention, or a ticket to link. */
export function PickDialog({
  title,
  description,
  label,
  empty,
  options,
  confirm,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  title: string;
  description: string;
  label: string;
  empty: string;
  options: { value: string; label: string }[];
  confirm: string;
  pending: boolean;
  error: unknown;
  onConfirm: (value: string) => Promise<unknown>;
  onClose: () => void;
}) {
  const [value, setValue] = useState("");

  const send = async () => {
    try {
      await onConfirm(value);
      onClose();
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {options.length === 0 ? (
          <p className="text-sm text-muted-foreground">{empty}</p>
        ) : (
          <div className="grid gap-2">
            <Label htmlFor="pick">{label}</Label>
            <Select value={value} onValueChange={setValue}>
              <SelectTrigger id="pick" className="w-full">
                <SelectValue placeholder="Choose" />
              </SelectTrigger>
              <SelectContent>
                {options.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        {Boolean(error) && <ErrorAlert error={error} title="It was not saved" />}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => void send()} disabled={!value || pending}>
            {confirm}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

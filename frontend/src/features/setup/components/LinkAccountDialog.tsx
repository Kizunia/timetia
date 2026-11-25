"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAccounts, useLinkAccount, type Row } from "@/features/setup/api";
import { FieldSelect } from "@/features/setup/components/FieldSelect";

/** Chooses the account a faculty member signs in as, which makes their lectures and tickets theirs. */
export function LinkAccountDialog({ faculty, onClose }: { faculty: Row; onClose: () => void }) {
  const [text, setText] = useState("");
  const [q, setQ] = useState("");
  const [userId, setUserId] = useState("");
  const accounts = useAccounts(q);
  const link = useLinkAccount();

  // The search waits until the person stops typing.
  useEffect(() => {
    const timer = setTimeout(() => setQ(text.trim()), 300);
    return () => clearTimeout(timer);
  }, [text]);

  const send = async (chosen: string | null) => {
    try {
      await link.mutateAsync({ id: faculty.id, userId: chosen, version: faculty.version });
      toast.success(chosen ? "Account linked" : "Link removed");
      onClose();
    } catch {
      // The alert below shows what the server said.
    }
  };

  const options = (accounts.data?.items ?? []).map((user) => ({
    value: user.id,
    label: user.email ? `${user.name} (${user.email})` : user.name,
  }));

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Account of {faculty.name}</DialogTitle>
          <DialogDescription>
            {faculty.userId ? "An account is linked. Choose another, or remove the link." : "No account is linked yet."}
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-2">
          <Label htmlFor="account-search">Search accounts</Label>
          <Input id="account-search" value={text} onChange={(event) => setText(event.target.value)} />
        </div>
        <FieldSelect id="account" label="Account" value={userId} options={options} onChange={setUserId} />
        {accounts.isError && <ErrorAlert error={accounts.error} title="The accounts could not be loaded" />}
        {link.isError && <ErrorAlert error={link.error} title="The link was not changed" />}
        <DialogFooter>
          {faculty.userId && (
            <Button variant="outline" disabled={link.isPending} onClick={() => void send(null)}>
              Remove link
            </Button>
          )}
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={!userId || link.isPending} onClick={() => void send(userId)}>
            Link
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

"use client";

import { ChevronDownIcon } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { useMyPlaces } from "@/features/campus/useMyPlaces";
import { usePermissions } from "@/features/identity/permissions";
import { KIND_TITLE, RaiseDialog } from "@/features/tickets/components/RaiseDialog";
import { raisableKinds } from "@/features/tickets/lib/kinds";
import type { TicketKind } from "@/features/tickets/lib/request";
import type { Lecture } from "@/features/timetable/api";

/**
 * "Change this lecture": the kinds of ticket the person may raise on it, and the form for the one they pick. Nothing is
 * rendered when there is nothing to offer. It goes in the lecture sheet's actions slot, put there by the timetable page, so
 * the timetable feature never has to know about tickets.
 */
export function LectureActions({ lecture }: { lecture: Lecture }) {
  const { me } = usePermissions();
  const places = useMyPlaces();
  const [kind, setKind] = useState<Exclude<TicketKind, "EXTRA"> | null>(null);
  const kinds = raisableKinds(lecture, me.permissions, places.facultyIds.includes(lecture.facultyId));
  if (kinds.length === 0) return null;
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline">
            Change this lecture <ChevronDownIcon aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          {kinds.map((option) => (
            <DropdownMenuItem key={option} onSelect={() => setKind(option)}>
              {KIND_TITLE[option]}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
      {kind && <RaiseDialog kind={kind} lecture={lecture} open onOpenChange={(open) => !open && setKind(null)} />}
    </>
  );
}

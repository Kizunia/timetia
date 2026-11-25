"use client";

import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAddEntry, type NewEntry } from "@/features/setup/api";
import { FieldSelect } from "@/features/setup/components/FieldSelect";
import { TimetableError } from "@/features/setup/components/TimetableError";
import type { Option } from "@/features/setup/lookups";
import { ApiError, fieldErrors } from "@/lib/api/problem";
import { toApiTime } from "@/lib/dates";
import { DAY_LABEL, MODE_LABEL } from "@/lib/labels";

const DAYS = [1, 2, 3, 4, 5, 6].map((day) => ({ value: String(day), label: DAY_LABEL[day]! }));
const hours = (from: number, to: number) =>
  Array.from({ length: to - from + 1 }, (_, i) => ({ value: String(from + i), label: toApiTime(from + i) }));
const MODES = Object.entries(MODE_LABEL).map(([value, label]) => ({ value, label }));

/** Adds one weekly lecture to a draft. The server refuses a lecture that would book a teacher, room or class twice. */
export function AddEntryForm({ versionId, assignments, rooms }: { versionId: string; assignments: Option[]; rooms: Option[] }) {
  const add = useAddEntry(versionId);
  const [assignmentId, setAssignmentId] = useState("");
  const [day, setDay] = useState("1");
  const [start, setStart] = useState("9");
  const [end, setEnd] = useState("10");
  const [mode, setMode] = useState<NewEntry["mode"]>("IN_PERSON");
  const [roomId, setRoomId] = useState("");
  const [link, setLink] = useState("");

  const problems = add.error instanceof ApiError ? fieldErrors(add.error.problem) : {};
  const placed = ["assignmentId", "dayOfWeek", "startTime", "endTime", "roomId"].some((field) => problems[field]);
  const inPerson = mode === "IN_PERSON";
  const ready = assignmentId && Number(end) > Number(start) && (!inPerson || roomId);

  const submit = async () => {
    try {
      await add.mutateAsync({
        assignmentId,
        dayOfWeek: Number(day),
        startTime: toApiTime(Number(start)),
        endTime: toApiTime(Number(end)),
        mode,
        ...(inPerson ? { roomId } : link.trim() ? { meetingLink: link.trim() } : {}),
      });
      toast.success("Lecture added");
    } catch {
      // The messages below show what the server said.
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2>Add lecture</h2>
        </CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <FieldSelect
            id="entry-assignment"
            label="Lecture"
            value={assignmentId}
            options={assignments}
            onChange={setAssignmentId}
            error={problems.assignmentId?.message}
          />
        </div>
        <FieldSelect id="entry-day" label="Day" value={day} options={DAYS} onChange={setDay} error={problems.dayOfWeek?.message} />
        <FieldSelect id="entry-mode" label="How" value={mode} options={MODES} onChange={(value) => setMode(value as NewEntry["mode"])} />
        <FieldSelect id="entry-start" label="Starts" value={start} options={hours(8, 19)} onChange={setStart} error={problems.startTime?.message} />
        <FieldSelect
          id="entry-end"
          label="Ends"
          value={end}
          options={hours(9, 20)}
          onChange={setEnd}
          error={problems.endTime?.message ?? (Number(end) <= Number(start) ? "End must be after start" : undefined)}
        />
        {inPerson ? (
          <FieldSelect id="entry-room" label="Room" value={roomId} options={rooms} onChange={setRoomId} error={problems.roomId?.message} />
        ) : (
          <div className="grid gap-2">
            <Label htmlFor="entry-link">Meeting link (optional)</Label>
            <Input id="entry-link" type="url" value={link} maxLength={500} onChange={(event) => setLink(event.target.value)} />
          </div>
        )}
        <div className="sm:col-span-2">
          {add.isError && !placed && <TimetableError error={add.error} title="The lecture was not added" />}
        </div>
        <div className="sm:col-span-2">
          <Button disabled={!ready || add.isPending} onClick={() => void submit()}>
            Add lecture
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

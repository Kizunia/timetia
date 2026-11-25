"use client";

import { useEffect } from "react";
import { Controller, useFormContext } from "react-hook-form";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useFreeRooms, type FreeRoom } from "@/features/tickets/api";
import type { TicketFormValues } from "@/features/tickets/lib/request";
import { HOURS } from "@/features/tickets/lib/slot";
import { toApiTime, todayIso, weekday, formatDayLong } from "@/lib/dates";
import type { components } from "@/lib/api/schema.gen";
import { MODE_LABEL, ROOM_TYPE_LABEL } from "@/lib/labels";

type SessionType = components["schemas"]["LiveLectureResponse"]["sessionType"];

const NOT_FITTING: Record<SessionType, string> = {
  LAB: " (not a lab room)",
  THEORY: " (not a classroom)",
  TUTORIAL: " (not a tutorial room)",
};

function roomLabel(room: FreeRoom, sessionType: SessionType | undefined) {
  const kind = (ROOM_TYPE_LABEL as Record<string, string>)[room.roomType] ?? room.roomType;
  const seats = room.capacity ? ` · ${room.capacity} seats` : "";
  return `${room.code} · ${kind}${seats}${!room.fits && sessionType ? NOT_FITTING[sessionType] : ""}`;
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <p id={id} role="alert" className="text-sm text-destructive">
      {message}
    </p>
  ) : null;
}

/**
 * When and where a lecture goes: the date, the hours, in person or online, and a room chosen from the ones that are free then.
 * It reads and writes the form it is placed in. When `lockedHours` is given the lecture keeps its length, so the end hour is
 * worked out from the start rather than chosen.
 */
export function SlotFields({
  sessionType,
  lockedHours,
  excludeLecture,
}: {
  sessionType?: SessionType;
  lockedHours?: number;
  /** The lecture that is moving: its own room counts as free for it. */
  excludeLecture?: string;
}) {
  const { control, register, setValue, watch, formState } = useFormContext<TicketFormValues>();
  const [date, startHour, endHour, mode, roomId] = watch(["date", "startHour", "endHour", "mode", "roomId"]);
  const errors = formState.errors;

  useEffect(() => {
    if (lockedHours) setValue("endHour", startHour + lockedHours);
  }, [lockedHours, startHour, setValue]);

  const validDate = /^\d{4}-\d{2}-\d{2}$/.test(date ?? "") && weekday(date) !== 7;
  const asking = validDate && mode === "IN_PERSON" && endHour > startHour;
  const rooms = useFreeRooms(
    asking ? { date, startTime: toApiTime(startHour), endTime: toApiTime(endHour), sessionType, excludeLecture } : null,
  );

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <div className="grid gap-2">
        <Label htmlFor="slot-date">Date</Label>
        <Input id="slot-date" type="date" min={todayIso()} aria-invalid={errors.date ? true : undefined} {...register("date")} />
        {validDate && <p className="text-sm text-muted-foreground">{formatDayLong(date)}</p>}
        {date && !validDate && /^\d{4}-\d{2}-\d{2}$/.test(date) && <p className="text-sm text-destructive">There are no lectures on a Sunday.</p>}
        <FieldError id="slot-date-error" message={errors.date?.message} />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="slot-mode">How</Label>
        <Controller
          control={control}
          name="mode"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger id="slot-mode">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(["IN_PERSON", "ONLINE"] as const).map((option) => (
                  <SelectItem key={option} value={option}>
                    {MODE_LABEL[option]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="slot-start">Starts</Label>
        <Controller
          control={control}
          name="startHour"
          render={({ field }) => (
            <Select value={String(field.value)} onValueChange={(value) => field.onChange(Number(value))}>
              <SelectTrigger id="slot-start">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {HOURS.map((hour) => (
                  <SelectItem key={hour} value={String(hour)}>
                    {toApiTime(hour)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="slot-end">Ends</Label>
        {lockedHours ? (
          <p id="slot-end" className="flex h-9 items-center text-sm" aria-live="polite">
            {toApiTime(endHour)} <span className="ml-2 text-muted-foreground">(a lecture keeps its length)</span>
          </p>
        ) : (
          <Controller
            control={control}
            name="endHour"
            render={({ field }) => (
              <Select value={String(field.value)} onValueChange={(value) => field.onChange(Number(value))}>
                <SelectTrigger id="slot-end">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {[...HOURS.slice(1), 20].map((hour) => (
                    <SelectItem key={hour} value={String(hour)}>
                      {toApiTime(hour)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        )}
        <FieldError id="slot-end-error" message={errors.endHour?.message} />
      </div>
      {mode === "IN_PERSON" ? (
        <div className="grid gap-2 sm:col-span-2">
          <Label htmlFor="slot-room">Room</Label>
          <Controller
            control={control}
            name="roomId"
            render={({ field }) => (
              <Select value={field.value ?? ""} onValueChange={field.onChange} disabled={!asking || rooms.isPending}>
                <SelectTrigger id="slot-room" aria-invalid={errors.roomId ? true : undefined}>
                  <SelectValue
                    placeholder={
                      !asking ? "Choose a date and time first" : rooms.isPending ? "Looking for free rooms…" : rooms.data?.length === 0 ? "No free room at that time" : "Choose a room"
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {(rooms.data ?? []).map((room) => (
                    <SelectItem key={room.id} value={room.id}>
                      {roomLabel(room, sessionType)}
                    </SelectItem>
                  ))}
                  {/* A room chosen earlier stays selectable while the list is being fetched again. */}
                  {roomId && !(rooms.data ?? []).some((room) => room.id === roomId) && (
                    <SelectItem value={roomId}>Room chosen before</SelectItem>
                  )}
                </SelectContent>
              </Select>
            )}
          />
          {rooms.isError && <p className="text-sm text-destructive">The free rooms could not be loaded.</p>}
          <FieldError id="slot-room-error" message={errors.roomId?.message} />
        </div>
      ) : (
        <div className="grid gap-2 sm:col-span-2">
          <Label htmlFor="slot-link">Meeting link (optional)</Label>
          <Input id="slot-link" type="url" placeholder="https://" {...register("meetingLink")} />
          <FieldError id="slot-link-error" message={errors.meetingLink?.message} />
        </div>
      )}
    </div>
  );
}

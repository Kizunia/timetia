"use client";

import { toast } from "sonner";

import { useFaculties } from "@/features/campus/api";
import { useMentionFaculty, type Issue } from "@/features/issues/api";
import { PickDialog } from "@/features/issues/components/PickDialog";

export function MentionDialog({ issue, onClose }: { issue: Issue; onClose: () => void }) {
  const faculties = useFaculties(issue.departmentId);
  const mention = useMentionFaculty(issue.id);
  const options = (faculties.data ?? [])
    .filter((f) => !issue.mentionedFacultyIds.includes(f.id))
    .map((f) => ({ value: f.id, label: f.name }));

  return (
    <PickDialog
      title="Mention a teacher"
      description="They are told about the issue, and may close it or link a ticket to it."
      label="Teacher"
      empty="Every teacher of the department is already mentioned."
      options={options}
      confirm="Mention"
      pending={mention.isPending}
      error={mention.error}
      onConfirm={async (facultyId) => {
        await mention.mutateAsync(facultyId);
        toast.success("Teacher mentioned");
      }}
      onClose={onClose}
    />
  );
}

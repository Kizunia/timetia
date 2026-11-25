import type { ResourceKey, Row } from "@/features/setup/api";
import type { Lookups } from "@/features/setup/lookups";
import { ROOM_TYPE_LABEL, SESSION_LABEL } from "@/lib/labels";

export type OptionSource = "departments" | "divisions" | "batches" | "subjects" | "faculties" | "sessionTypes" | "roomTypes";

export interface Field {
  name: string;
  label: string;
  type: "text" | "select" | "date" | "number";
  required?: boolean;
  options?: OptionSource;
  maxLength?: number;
}

export interface Resource {
  key: ResourceKey;
  title: string;
  /** One of them, for button and dialog titles. */
  one: string;
  columns: { header: string; cell: (row: Row, lookups: Lookups) => string }[];
  createFields: Field[];
  /** The lists that have a code and a name can be renamed and searched; teaching assignments have neither. */
  named: boolean;
  /** A list filter offered above the table: the query parameter and where its options come from. */
  filter?: { name: "department" | "division"; label: string; options: OptionSource };
}

const code = (maxLength: number): Field => ({ name: "code", label: "Code", type: "text", required: true, maxLength });
const name = (maxLength: number): Field => ({ name: "name", label: "Name", type: "text", required: true, maxLength });

const department: Field = { name: "departmentId", label: "Department", type: "select", required: true, options: "departments" };
const division: Field = { name: "divisionId", label: "Division", type: "select", required: true, options: "divisions" };

const codeAndName: Resource["columns"] = [
  { header: "Code", cell: (row) => row.code ?? "—" },
  { header: "Name", cell: (row) => row.name ?? "—" },
];

const inDepartment: Resource["columns"][number] = {
  header: "Department",
  cell: (row, lookups) => lookups.name("departments", row.departmentId),
};

export const RESOURCES: readonly Resource[] = [
  { key: "departments", title: "Departments", one: "department", columns: codeAndName, createFields: [code(30), name(120)], named: true },
  {
    key: "divisions",
    title: "Divisions",
    one: "division",
    columns: [...codeAndName, inDepartment],
    createFields: [code(40), name(120), department],
    named: true,
    filter: { name: "department", label: "Department", options: "departments" },
  },
  {
    key: "batches",
    title: "Batches",
    one: "batch",
    columns: [...codeAndName, { header: "Division", cell: (row, lookups) => lookups.name("divisions", row.divisionId) }],
    createFields: [code(40), name(120), division],
    named: true,
    filter: { name: "division", label: "Division", options: "divisions" },
  },
  { key: "subjects", title: "Subjects", one: "subject", columns: codeAndName, createFields: [code(30), name(150)], named: true },
  {
    key: "rooms",
    title: "Rooms",
    one: "room",
    columns: [
      ...codeAndName,
      { header: "Kind", cell: (row) => (row.roomType ? ROOM_TYPE_LABEL[row.roomType] : "—") },
      { header: "Seats", cell: (row) => (row.capacity ? String(row.capacity) : "—") },
    ],
    createFields: [
      code(20),
      name(120),
      { name: "roomType", label: "Kind", type: "select", required: true, options: "roomTypes" },
      { name: "capacity", label: "Seats", type: "number" },
    ],
    named: true,
  },
  {
    key: "faculties",
    title: "Faculty",
    one: "faculty member",
    columns: [...codeAndName, inDepartment, { header: "Account", cell: (row) => (row.userId ? "Linked" : "Not linked") }],
    createFields: [code(30), name(150), department],
    named: true,
    filter: { name: "department", label: "Department", options: "departments" },
  },
  {
    key: "academic-terms",
    title: "Terms",
    one: "term",
    columns: [
      ...codeAndName,
      { header: "Starts", cell: (row) => row.startDate ?? "—" },
      { header: "Ends", cell: (row) => row.endDate ?? "—" },
    ],
    createFields: [
      code(30),
      name(120),
      { name: "startDate", label: "Start date", type: "date", required: true },
      { name: "endDate", label: "End date", type: "date", required: true },
    ],
    named: true,
  },
  {
    key: "teaching-assignments",
    title: "Teaching",
    one: "teaching assignment",
    columns: [
      { header: "Division", cell: (row, lookups) => lookups.name("divisions", row.divisionId) },
      { header: "Subject", cell: (row, lookups) => lookups.name("subjects", row.subjectId) },
      { header: "Teacher", cell: (row, lookups) => lookups.name("faculties", row.facultyId) },
      { header: "Session", cell: (row) => (row.sessionType ? SESSION_LABEL[row.sessionType] : "—") },
      { header: "Batch", cell: (row, lookups) => (row.batchId ? lookups.name("batches", row.batchId) : "Whole division") },
    ],
    createFields: [
      division,
      { name: "subjectId", label: "Subject", type: "select", required: true, options: "subjects" },
      { name: "facultyId", label: "Teacher", type: "select", required: true, options: "faculties" },
      { name: "sessionType", label: "Session", type: "select", required: true, options: "sessionTypes" },
      { name: "batchId", label: "Batch", type: "select", options: "batches" },
    ],
    named: false,
    filter: { name: "division", label: "Division", options: "divisions" },
  },
];

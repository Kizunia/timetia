/*
 * Seeds a whole demo college so every screen has data. Use THIS script (not demo-seed.browser.js) for the presentation.
 *
 * What it creates (once; to start over run `pnpm reset`, see scripts/PRESENT.md)
 *   2 branches (departments): CSE with divisions CSE-A, CSE-B, CSE-C; ME with divisions ME-A, ME-B
 *   2 batches in every division (CSE-A1, CSE-A2, ...) = 10 batches, and 5 students in every batch = 50 student accounts
 *   subjects, rooms (6 classrooms, 2 labs), teachers, a 4-week term starting on the Monday of the current week, and a PUBLISHED weekly timetable
 *   for every division (theory on Monday, a two-hour lab on its own weekday, a Friday theory lecture for CSE/ME)
 *   roles: demo = Faculty; demo-admin = Faculty + HOD of CSE; first student of each batch is also its BR (batch
 *   representative); first student of each division is also its CR (class representative)
 *   Dr Demo (the `demo` account) and Dr Admin (`demo-admin`) are CSE teachers with accounts, the others have none.
 *
 * How to run it
 *   1. Sign in ONCE as `demo` (any window), sign out. 2. Sign in as `demo-admin`. 3. F12 -> Console, paste this file, Enter.
 *   It takes about a minute. The student accounts are only invited (emails land in Mailpit, http://localhost:8025).
 *
 * Afterwards `tt` is available in the console (`await tt.get("/api/v1/tickets")`) and `college` holds the ids.
 */
(async () => {
  const HEADERS = { "content-type": "application/json", "x-timetia-request": "1" };
  async function call(method, path, body, form) {
    const response = await fetch(path, {
      method,
      headers: form ? { "x-timetia-request": "1" } : HEADERS,
      body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
    });
    const text = await response.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { json = text; }
    if (!response.ok) throw new Error(`${method} ${path} -> ${response.status}: ${text}`);
    return json;
  }
  const tt = { get: (p) => call("GET", p), post: (p, b) => call("POST", p, b ?? {}), put: (p, b) => call("PUT", p, b) };
  window.tt = tt;
  const create = async (path, body) => (await tt.post(path, body)).id;

  const me = await tt.get("/api/v1/me");
  if (me.username !== "demo-admin") throw new Error(`Sign in as demo-admin first (you are ${me.username}).`);
  if ((await tt.get("/api/v1/departments?size=100")).items.some((d) => d.code === "CSE")) {
    console.warn("The college already exists. Nothing was changed. Run `pnpm reset` to start over (scripts/PRESENT.md).");
    return;
  }
  const demo = (await tt.get("/api/v1/users?size=100")).items.find((u) => u.username === "demo");
  if (!demo) throw new Error("Sign in once as `demo` first so the account exists, then run this again.");

  const iso = (d) => d.toISOString().slice(0, 10);
  // The term starts on the Monday of the CURRENT week (today, if today is a Monday), by the browser's local date, so this
  // week's timetable is already there. Lectures that have started cannot be changed by tickets; the later ones can.
  const today = new Date();
  const start = new Date(Date.UTC(today.getFullYear(), today.getMonth(), today.getDate(), 12));
  while (start.getUTCDay() !== 1) start.setUTCDate(start.getUTCDate() - 1);
  const end = new Date(start);
  end.setUTCDate(end.getUTCDate() + 27);
  const hh = (h) => `${String(h).padStart(2, "0")}:00`;

  // --- rooms, term ------------------------------------------------------------------------------------------------
  const room = {};
  for (const code of ["R101", "R102", "R103", "R104", "R105", "R201"]) {
    room[code] = await create("/api/v1/rooms", { code, name: `Room ${code.slice(1)}`, roomType: "CLASSROOM", capacity: 60 });
  }
  for (const code of ["LAB1", "LAB2"]) {
    room[code] = await create("/api/v1/rooms", { code, name: `Lab ${code.slice(3)}`, roomType: "LAB", capacity: 30 });
  }
  const term = await create("/api/v1/academic-terms", { code: "SEM3", name: "Semester 3", startDate: iso(start), endDate: iso(end) });

  // --- branches, subjects, teachers -------------------------------------------------------------------------------
  const branches = [
    { code: "CSE", name: "Computer Science and Engineering", divisions: ["A", "B", "C"], subjects: [["OOP", "Object-Oriented Programming"], ["DBMS", "Database Systems"], ["DSA", "Data Structures"]],
      teachers: [["DEMO", "Dr Demo", demo.id], ["ADMIN", "Dr Admin", me.id], ["RAO", "Dr Rao", null]] },
    { code: "ME", name: "Mechanical Engineering", divisions: ["A", "B"], subjects: [["THERMO", "Thermodynamics"], ["FM", "Fluid Mechanics"], ["MD", "Machine Design"]],
      teachers: [["MEHTA", "Dr Mehta", null], ["IYER", "Dr Iyer", null], ["KHAN", "Dr Khan", null]] },
  ];
  const college = { rooms: room, term, departments: {}, divisions: {}, batches: {} };
  const csv = [];                       // student rows per division: [email, role, scope]
  let globalDivision = 0;

  for (const b of branches) {
    const dept = await create("/api/v1/departments", { code: b.code, name: b.name });
    college.departments[b.code] = dept;
    const subjects = [];
    for (const [code, name] of b.subjects) subjects.push(await create("/api/v1/subjects", { code, name }));
    const teachers = [];
    for (const [code, name, userId] of b.teachers) {
      teachers.push(await create("/api/v1/faculties", { code, name, departmentId: dept, ...(userId ? { userId } : {}) }));
    }

    for (let i = 0; i < b.divisions.length; i++, globalDivision++) {
      const code = `${b.code}-${b.divisions[i]}`;
      const division = await create("/api/v1/divisions", { code, name: `SY-${code}`, departmentId: dept });
      college.divisions[code] = division;
      const batches = [];
      for (const n of [1, 2]) {
        const batchCode = `${code}${n}`;
        batches.push(await create("/api/v1/batches", { code: batchCode, name: `${b.divisions[i]}${n}`, divisionId: division }));
        college.batches[batchCode] = batches[n - 1];
        for (let s = 1; s <= 5; s++) {
          const email = `${batchCode.toLowerCase()}.s${s}@college.test`;
          csv.push([email, "Student", batchCode]);
          if (s === 1) csv.push([email, "BR", batchCode]);
          if (s === 1 && n === 1) csv.push([email, "CR", code]);
        }
      }
      const assign = (batchId, subjectId, facultyId, sessionType) =>
        create("/api/v1/teaching-assignments", { divisionId: division, subjectId, facultyId, sessionType, ...(batchId ? { batchId } : {}) });
      const theory0 = await assign(null, subjects[0], teachers[0], "THEORY");
      const theory1 = await assign(null, subjects[1], teachers[1], "THEORY");
      const theory2 = await assign(null, subjects[2], teachers[2], "THEORY");
      const lab1 = await assign(batches[0], subjects[0], teachers[0], "LAB");
      const lab2 = await assign(batches[1], subjects[0], teachers[1], "LAB");

      // Each division has its own classroom and its own lab weekday, so nothing clashes. Teachers shared inside a branch
      // teach at different hours: the branch's divisions are staggered by `i`.
      const version = await create("/api/v1/timetable/versions", { divisionId: division, termId: term, effectiveFrom: iso(start) });
      const entry = (assignmentId, dayOfWeek, from, to, roomCode) =>
        tt.post(`/api/v1/timetable/versions/${version}/entries`, {
          assignmentId, dayOfWeek, startTime: hh(from), endTime: hh(to), mode: "IN_PERSON", roomId: room[roomCode],
        });
      const classroom = ["R101", "R102", "R103", "R104", "R105"][globalDivision];
      await entry(theory0, 1, 9 + i, 10 + i, classroom);
      await entry(theory1, 1, 12 + i, 13 + i, classroom);
      await entry(theory2, 5, 14 + i, 15 + i, classroom);
      const labDay = 2 + globalDivision;   // Tue..Sat, one division per day
      await entry(lab1, labDay, 9, 11, "LAB1");
      await entry(lab2, labDay, 9, 11, "LAB2");
      await tt.post(`/api/v1/timetable/versions/${version}/publish`);
      console.log(`${code} published`);
    }
  }

  // --- roles ------------------------------------------------------------------------------------------------------
  const roles = (await tt.get("/api/v1/roles?size=100")).items;
  const role = (name) => roles.find((r) => r.name === name).id;
  await tt.post(`/api/v1/users/${demo.id}/scoped-roles`, { roleId: role("Faculty") });
  await tt.post(`/api/v1/users/${me.id}/scoped-roles`, { roleId: role("Faculty") });
  await tt.post(`/api/v1/users/${me.id}/scoped-roles`, { roleId: role("HOD"), scopeId: college.departments.CSE });

  // --- students: invited through the CSV import, 25 rows at a time -----------------------------------------------
  for (let from = 0; from < csv.length; from += 25) {
    const text = "email,role,scope\n" + csv.slice(from, from + 25).map((r) => r.join(",")).join("\n") + "\n";
    const form = new FormData();
    form.append("file", new Blob([text], { type: "text/csv" }), "students.csv");
    const result = await call("POST", "/api/v1/user-imports", undefined, form);
    console.log("students imported", result);
  }

  window.college = college;
  console.log("College ready. The term starts on " + iso(start) + ". `tt` and `college` are available.", college);
})().catch((error) => console.error(error.message));

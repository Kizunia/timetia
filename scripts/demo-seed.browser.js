/*
 * Seeds a small demo campus for trying Timetia by hand, and leaves a helper to call the API from the browser console.
 *
 * Why a console script: the web app has no Timetia screens yet, and the API needs a bearer token that only the signed-in web
 * app holds (the backend-for-frontend adds it). So this runs inside the signed-in page and calls /api/... like the app does.
 *
 * How to run it
 *   1. Start everything (see the guide), open http://localhost:3470.
 *   2. Sign in ONCE as `demo` (any browser window) so the account exists. Then sign out.
 *   3. Sign in as `demo-admin`. Open the developer tools (F12) -> Console.
 *   4. Paste this whole file and press Enter.
 *
 * What it creates (once; to start over run `pnpm reset`)
 *   department CSE; division CSE-A with batches A1 and A2; rooms R101, R102, LAB1; subjects OOP and DBMS;
 *   a term of four weeks starting next Monday;
 *   Dr Demo (the `demo` account) teaches OOP theory and the lab of batch A1;
 *   Dr Admin (the `demo-admin` account) teaches DBMS theory and the lab of batch A2;
 *   the weekly timetable, published: Mon 9-10 OOP (R101), Mon 10-11 DBMS (R101), Tue 9-11 the two labs in parallel
 *   (LAB1 and R102), Wed 14-15 DBMS online;
 *   roles: `demo` is Faculty; `demo-admin` is Faculty and HOD of CSE, so one person can approve what the other raises.
 *
 * Afterwards `tt` is available in the console: `await tt.get("/api/v1/tickets")`, `await tt.post(path, body)`.
 */
(async () => {
  const HEADERS = { "content-type": "application/json", "x-timetia-request": "1" };

  async function call(method, path, body) {
    const response = await fetch(path, {
      method,
      headers: HEADERS,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      json = text;
    }
    if (!response.ok) throw new Error(`${method} ${path} -> ${response.status}: ${text}`);
    return json;
  }
  const tt = {
    get: (path) => call("GET", path),
    post: (path, body) => call("POST", path, body ?? {}),
    put: (path, body) => call("PUT", path, body),
  };
  window.tt = tt;
  const create = async (path, body) => (await tt.post(path, body)).id;

  const me = await tt.get("/api/v1/me");
  if (me.username !== "demo-admin") {
    throw new Error(`Sign in as demo-admin first (you are ${me.username}).`);
  }
  const existing = await tt.get("/api/v1/departments?size=100");
  if (existing.items.some((d) => d.code === "CSE")) {
    console.warn("The demo campus already exists. Nothing was changed. Use `pnpm reset` to start over.");
    return;
  }
  const people = (await tt.get("/api/v1/users?size=100")).items;
  const demo = people.find((u) => u.username === "demo");
  if (!demo) throw new Error("Sign in once as `demo` first, so that the account exists, then run this again.");

  // --- the term: four weeks from next Monday, so that every lecture is still ahead ---------------------------------
  const iso = (d) => d.toISOString().slice(0, 10);
  const start = new Date();
  start.setUTCHours(12, 0, 0, 0);
  do start.setUTCDate(start.getUTCDate() + 1); while (start.getUTCDay() !== 1);
  const end = new Date(start);
  end.setUTCDate(end.getUTCDate() + 27);

  // --- the campus -------------------------------------------------------------------------------------------------
  const dept = await create("/api/v1/departments", { code: "CSE", name: "Computer Science" });
  const division = await create("/api/v1/divisions", { code: "CSE-A", name: "SY-CSSE-A", departmentId: dept });
  const a1 = await create("/api/v1/batches", { code: "CSE-A1", name: "A1", divisionId: division });
  const a2 = await create("/api/v1/batches", { code: "CSE-A2", name: "A2", divisionId: division });
  const oop = await create("/api/v1/subjects", { code: "OOP", name: "Object-Oriented Programming" });
  const dbms = await create("/api/v1/subjects", { code: "DBMS", name: "Database Systems" });
  const r101 = await create("/api/v1/rooms", { code: "R101", name: "Room 101", roomType: "CLASSROOM" });
  const r102 = await create("/api/v1/rooms", { code: "R102", name: "Room 102", roomType: "CLASSROOM" });
  const lab1 = await create("/api/v1/rooms", { code: "LAB1", name: "Lab 1", roomType: "LAB", capacity: 30 });
  const term = await create("/api/v1/academic-terms", {
    code: "SEM3", name: "Semester 3", startDate: iso(start), endDate: iso(end),
  });
  const drDemo = await create("/api/v1/faculties", {
    code: "DEMO", name: "Dr Demo", departmentId: dept, userId: demo.id,
  });
  const drAdmin = await create("/api/v1/faculties", {
    code: "ADMIN", name: "Dr Admin", departmentId: dept, userId: me.id,
  });
  const assign = (batchId, subjectId, facultyId, sessionType) =>
    create("/api/v1/teaching-assignments", {
      divisionId: division, subjectId, facultyId, sessionType, ...(batchId ? { batchId } : {}),
    });
  const oopTheory = await assign(null, oop, drDemo, "THEORY");
  const dbmsTheory = await assign(null, dbms, drAdmin, "THEORY");
  const oopLab = await assign(a1, oop, drDemo, "LAB");
  const dbmsLab = await assign(a2, dbms, drAdmin, "LAB");

  // --- the weekly timetable, published ----------------------------------------------------------------------------
  const version = await create("/api/v1/timetable/versions", {
    divisionId: division, termId: term, effectiveFrom: iso(start),
  });
  const entry = (assignmentId, dayOfWeek, from, to, roomId) =>
    tt.post(`/api/v1/timetable/versions/${version}/entries`, {
      assignmentId, dayOfWeek, startTime: `${String(from).padStart(2, "0")}:00`,
      endTime: `${String(to).padStart(2, "0")}:00`, mode: "IN_PERSON", roomId,
    });
  await entry(oopTheory, 1, 9, 10, r101);
  await entry(dbmsTheory, 1, 10, 11, r101);
  await entry(oopLab, 2, 9, 11, lab1);
  await entry(dbmsLab, 2, 9, 11, r102);
  await tt.post(`/api/v1/timetable/versions/${version}/entries`, {
    assignmentId: dbmsTheory, dayOfWeek: 3, startTime: "14:00", endTime: "15:00", mode: "ONLINE",
    meetingLink: "https://meet.example/dbms",
  });
  await tt.post(`/api/v1/timetable/versions/${version}/publish`);

  // --- roles: scoped roles are given by name ----------------------------------------------------------------------
  const roles = (await tt.get("/api/v1/roles?size=100")).items;
  const role = (name) => {
    const found = roles.find((r) => r.name === name);
    if (!found) throw new Error(`There is no role named ${name}. Is the backend up to date?`);
    return found.id;
  };
  await tt.post(`/api/v1/users/${demo.id}/scoped-roles`, { roleId: role("Faculty") });
  await tt.post(`/api/v1/users/${me.id}/scoped-roles`, { roleId: role("Faculty") });
  await tt.post(`/api/v1/users/${me.id}/scoped-roles`, { roleId: role("HOD"), scopeId: dept });

  window.demoCampus = { dept, division, a1, a2, r101, r102, lab1, term, drDemo, drAdmin, oopTheory, dbmsTheory, oopLab, dbmsLab, demoUserId: demo.id, adminUserId: me.id, termStart: iso(start) };
  console.log("Demo campus ready. The term starts on " + iso(start) + ". `tt` and `demoCampus` are available.", window.demoCampus);
})().catch((error) => console.error(error.message));

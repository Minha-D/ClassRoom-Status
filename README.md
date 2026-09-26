# University Classroom Management System

A Java Swing desktop app. Shows which classrooms are empty today, lets
teachers cancel/flag a class as empty, and lets students request to book
that freed-up slot for admin approval. No database — everything is saved
to plain CSV files in a local `data/` folder.

## How to run

Requires Java 17+ (JDK, so `javac`/`jar` are available if you rebuild).

**Run the prebuilt jar:**
```
java -jar ClassroomManagementSystem.jar
```

**Or rebuild from source:**
```
javac -d out $(find src -name "*.java")
jar cfe ClassroomManagementSystem.jar com.university.classroommgmt.Main -C out .
java -jar ClassroomManagementSystem.jar
```

A `data/` folder is created next to the jar on first run, pre-seeded with
demo accounts, 3 classrooms, and 3 class sessions for today (one already
cancelled so you can see the "empty" flow immediately).

## Demo accounts

| Username | Password | Role    |
|----------|----------|---------|
| admin    | admin123 | Admin   |
| tsmith   | pass123  | Teacher |
| jdoe     | pass123  | Teacher |
| alice    | pass123  | Student (semester/section 1A) |
| bob      | pass123  | Student (semester/section 1B) |

## Accounts

**Everyone** (Admin, Teacher, Student) can edit their own full name,
username, and password from the **My Account** button on their dashboard.
Changing a username is safe — it automatically updates every class
session and booking request that referenced the old one, so nothing is
left orphaned.

**Adding a teacher or admin account:** Admin Dashboard → Users → "Add
User (Admin/Teacher)" — a plain username/full name/password/role form.

**Adding a student account:** Admin Dashboard → Users → "Add Student" —
a dedicated form that takes:
- Full name
- **Student ID** (must be exactly 16 digits, e.g. `0222510005101083`) —
  this becomes the student's login username
- **Semester** (1–8) and **Section** (A–J)
- Password

The semester+section (e.g. `3B`) is what lets the app show a student
their own routine: it's matched against the `batch` recorded on each
class session (set automatically when importing the routine Excel file,
or settable manually when adding a session by hand). A student sees this
under their **My Class Routine** tab.

## Importing the university routine (Excel)

The **Admin Dashboard → Import Routine** tab reads a weekly class-routine
`.xlsx` workbook and turns it straight into classrooms, teacher accounts,
and class sessions — no manual data entry.

**Expected file shape** (matches the standard department routine template):
- One sheet per weekday named exactly `SATURDAY`, `Sunday`, `Monday`,
  `Tuesday`, `Wednesday` (sheets that don't match a weekday name, e.g. a
  scratch/legend sheet, are skipped automatically).
- A header row containing a `Sem` column, followed by a run of time-slot
  columns labeled like `9.45 am - 10.10 am`.
- Below that, one row per batch/section (e.g. `3B`), with class cells
  shaped `SUBJECT/TEACHER/ROOM`, e.g. `CGVA/AU/404`. A class that spans
  multiple time-slot columns (a merged cell in the sheet) is imported as
  one session running start-to-end across that span.
- A few cells may carry a 4th segment, e.g. `OSL/AJT/606/CL4` or
  `SEL/EAS/604/B1` (a lab/section tag). Whichever segment is purely
  numeric is treated as the room; the other becomes part of the course
  name, e.g. `OSL (CL4)`. The semester/section itself (e.g. `5B`) is
  stored on the session's own `batch` field, not folded into the name.

**What happens on import:**
- A classroom is created for any room number not already in the system
  (using the building/capacity defaults you enter on the tab — edit them
  in the Classrooms tab afterwards if needed).
- A teacher account is created for any teacher code not already a user
  (username = lowercased code, e.g. `AU` → `au`), with the password you
  set in the "default password" field — hand these out to teachers and
  have them treat it as temporary, since there's no in-app way to change
  a password yet.
- Class sessions are created as `SCHEDULED`. Re-importing the same file
  (or an updated one with mostly-unchanged classes) won't create
  duplicates — an identical day/room/time/teacher/course combination is
  skipped.
- Any cell that can't be parsed (missing room, wrong shape, stray text)
  is skipped and listed as a warning in the import log rather than
  guessed at.

No external libraries are used for this — the importer reads the
`.xlsx` zip/XML directly (`importer/XlsxWorkbook.java`), consistent with
the rest of this project having no external dependencies.

## How the workflow works

1. **Teacher** logs in, sees their weekly schedule (grouped day by day —
   Saturday's classes, then Sunday's, and so on), and clicks
   "Cancel Selected Class (Flag as Empty)" on any class, giving a reason.
2. **Student** logs in and sees a live list of "Empty classrooms today"
   (i.e. classes flagged CANCELLED for today's day of week). They select
   one and click "Request to Book Selected Class," optionally leaving a
   note. They can also check their own **My Class Routine** tab (also
   grouped day by day) for their full weekly schedule, matched by their
   semester/section.
3. **Admin** logs in, sees the request under "Pending Requests," and can
   Approve or Reject. Approving marks the session BOOKED and assigns it
   to that student; any other pending requests for the same slot are
   auto-rejected.
4. Admin can also add classrooms, add teacher/admin/student accounts,
   and add new class sessions from their dashboard tabs. "All Sessions"
   is likewise grouped day by day rather than one huge flat table —
   handy once you've imported an entire semester's routine.
5. Everyone can update their own full name, username, and password via
   the **My Account** button on their dashboard.

## Project structure

```
src/com/university/classroommgmt/
  Main.java                  entry point
  model/
    User.java                Admin/Teacher/Student (role-based, one class); students also carry a batch (semester+section)
    Classroom.java            physical room
    ClassSession.java         a scheduled class slot (day/time/status/batch)
    BookingRequest.java       a student's request to claim an empty slot
  storage/
    DataStore.java            in-memory data + CSV load/save (the "DB"); also handles account rename cascades
  importer/
    XlsxWorkbook.java          minimal read-only .xlsx (zip+XML) reader, no dependencies
    RoutineImporter.java       parses the routine workbook into classrooms/teachers/sessions
  gui/
    LoginFrame.java
    AdminDashboard.java        includes the "Import Routine" tab, day-grouped "All Sessions", and Add Student
    TeacherDashboard.java      day-grouped weekly schedule
    StudentDashboard.java      day-grouped "My Class Routine" tab, filtered by semester/section
    AccountDialog.java         shared "My Account" dialog (full name / username / password)
    DaySections.java           shared helper that renders sessions grouped by day of week
```

## Data files (auto-created in `data/`)

- `users.csv` — username,password,fullName,ROLE,batch (batch is only meaningful for students)
- `classrooms.csv` — id,roomNumber,building,capacity
- `sessions.csv` — id,classroomId,courseName,teacherUsername,DAY,start,end,STATUS,bookedBy,cancelReason,batch
- `requests.csv` — id,sessionId,studentUsername,requestedAt,STATUS,note

Old data files saved before the `batch` column existed still load fine —
it just defaults to empty.

## Notes / things you may want to extend

- Passwords are stored in plain text in the CSV for simplicity — fine for
  a class project, not for production. Swap in hashing (e.g. `BCrypt`) if
  this ever needs to be real.
- "Empty" currently means "cancelled for today." If you also want to show
  naturally free time slots (never scheduled), that's a small addition to
  `DataStore.getEmptySessionsForDay`.
- Only one pending request per slot can be approved; others auto-reject.
- There's still no admin UI to edit/delete a classroom or a manually
  added session, or to reset someone else's password — only self-service
  edits via My Account, and account creation.

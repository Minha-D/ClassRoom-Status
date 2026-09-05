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
| alice    | pass123  | Student |
| bob      | pass123  | Student |

## How the workflow works

1. **Teacher** logs in, sees their weekly schedule, and clicks
   "Cancel Selected Class (Flag as Empty)" on any class, giving a reason.
2. **Student** logs in and sees a live list of "Empty classrooms today"
   (i.e. classes flagged CANCELLED for today's day of week). They select
   one and click "Request to Book Selected Class," optionally leaving a
   note.
3. **Admin** logs in, sees the request under "Pending Requests," and can
   Approve or Reject. Approving marks the session BOOKED and assigns it
   to that student; any other pending requests for the same slot are
   auto-rejected.
4. Admin can also add classrooms, add teachers/students, and add new
   class sessions from their dashboard tabs.

## Project structure

```
src/com/university/classroommgmt/
  Main.java                  entry point
  model/
    User.java                Admin/Teacher/Student (role-based, one class)
    Classroom.java            physical room
    ClassSession.java         a scheduled class slot (day/time/status)
    BookingRequest.java       a student's request to claim an empty slot
  storage/
    DataStore.java            in-memory data + CSV load/save (the "DB")
  gui/
    LoginFrame.java
    AdminDashboard.java
    TeacherDashboard.java
    StudentDashboard.java
```

## Data files (auto-created in `data/`)

- `users.csv` — username,password,fullName,ROLE
- `classrooms.csv` — id,roomNumber,building,capacity
- `sessions.csv` — id,classroomId,courseName,teacherUsername,DAY,start,end,STATUS,bookedBy,cancelReason
- `requests.csv` — id,sessionId,studentUsername,requestedAt,STATUS,note

## Notes / things you may want to extend

- Passwords are stored in plain text in the CSV for simplicity — fine for
  a class project, not for production. Swap in hashing (e.g. `BCrypt`) if
  this ever needs to be real.
- "Empty" currently means "cancelled for today." If you also want to show
  naturally free time slots (never scheduled), that's a small addition to
  `DataStore.getEmptySessionsForDay`.
- Only one pending request per slot can be approved; others auto-reject.

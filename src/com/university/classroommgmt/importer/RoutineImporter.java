package com.university.classroommgmt.importer;

import com.university.classroommgmt.model.*;
import com.university.classroommgmt.storage.DataStore;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports a university-style weekly class routine spreadsheet (one sheet per weekday, a "Sem"
 * column for the batch/section, and a run of time-slot column headers like "9.45 am - 10.10 am")
 * into the DataStore as Classrooms, Teachers, and ClassSessions.
 *
 * Expected cell format for a scheduled slot: "SUBJECT/TEACHER/ROOM", e.g. "GE/RMA/902". A few
 * cells in real routines carry a 4th segment (a lab/section tag such as "CL4" or "B1", e.g.
 * "OSL/AJT/606/CL4" or "DBMSL/MRI/C2/604") — whichever segment is purely numeric is treated as
 * the room number, and the other extra segment is folded into the course name.
 *
 * A class that spans more than one time-slot column (shown in the sheet as a merged cell) is
 * imported as a single session running from the first column's start time to the last column's
 * end time.
 */
public class RoutineImporter {

    private static final Pattern TIME_RANGE = Pattern.compile(
            "(\\d{1,2})[.:](\\d{2})\\s*([ap]m)\\s*-\\s*(\\d{1,2})[.:](\\d{2})\\s*([ap]m)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOM_TOKEN = Pattern.compile("\\d{2,4}");
    private static final int MAX_HEADER_SCAN_ROW = 20;
    private static final int MAX_COLUMN_SCAN = 60;

    private RoutineImporter() { }

    public static ImportResult importFromFile(File file, DataStore store, String defaultBuilding,
                                               int defaultCapacity, String defaultTeacherPassword) throws IOException {
        ImportResult result = new ImportResult();
        try (XlsxWorkbook wb = XlsxWorkbook.open(file)) {
            for (String sheetName : wb.getSheetNames()) {
                DayOfWeek day = toDayOfWeek(sheetName);
                if (day == null) {
                    result.sheetsSkipped.add(sheetName);
                    continue;
                }
                XlsxWorkbook.Sheet sheet = wb.getSheet(sheetName);
                importSheet(sheet, sheetName, day, store, defaultBuilding, defaultCapacity,
                        defaultTeacherPassword, result);
            }
        }
        return result;
    }

    private static void importSheet(XlsxWorkbook.Sheet sheet, String sheetName, DayOfWeek day,
                                     DataStore store, String defaultBuilding, int defaultCapacity,
                                     String defaultTeacherPassword, ImportResult result) {
        int[] header = findHeaderRowAndSemColumn(sheet);
        if (header == null) {
            result.warnings.add("[" + sheetName + "] Could not find a header row with a 'Sem' column — sheet skipped.");
            return;
        }
        int headerRow = header[0];
        int semCol = header[1];

        // Map each time-slot column to its [start, end] time, read from the header row.
        TreeMap<Integer, LocalTime[]> colTimes = new TreeMap<>();
        for (int col = semCol + 1; col <= MAX_COLUMN_SCAN; col++) {
            String text = sheet.get(col, headerRow);
            if (text == null) continue;
            LocalTime[] range = parseTimeRange(text);
            if (range != null) colTimes.put(col, range);
        }
        if (colTimes.isEmpty()) {
            result.warnings.add("[" + sheetName + "] Header row found but no time-slot columns could be parsed — sheet skipped.");
            return;
        }

        int maxRow = Math.max(sheet.maxRow(), headerRow);
        String currentBatch = null;
        for (int row = headerRow + 1; row <= maxRow; row++) {
            String sem = sheet.get(semCol, row);
            if (sem != null && !sem.isBlank()) currentBatch = sem.trim();

            for (Map.Entry<Integer, LocalTime[]> entry : colTimes.entrySet()) {
                int col = entry.getKey();
                String raw = sheet.get(col, row);
                if (raw == null || raw.isBlank()) continue; // blank or a non-anchor cell inside a merge

                ParsedClass pc = parseClassCode(raw.trim());
                if (pc == null) {
                    result.warnings.add("[" + sheetName + " " + cellRefLabel(col, row) + "] Could not parse \"" + raw + "\" — skipped.");
                    continue;
                }

                LocalTime start = entry.getValue()[0];
                LocalTime end = entry.getValue()[1];
                Integer mergeEndCol = sheet.mergeEndColumn(col, row);
                if (mergeEndCol != null) {
                    LocalTime[] endRange = colTimes.get(mergeEndCol);
                    if (endRange != null) end = endRange[1];
                }

                applySession(day, start, end, pc, currentBatch, store, defaultBuilding,
                        defaultCapacity, defaultTeacherPassword, result);
            }
        }
    }

    private static void applySession(DayOfWeek day, LocalTime start, LocalTime end, ParsedClass pc,
                                       String batch, DataStore store, String defaultBuilding,
                                       int defaultCapacity, String defaultTeacherPassword, ImportResult result) {
        boolean roomIsNew = store.getClassroomByRoomNumber(pc.room) == null;
        Classroom room = store.getOrCreateClassroom(pc.room, defaultBuilding, defaultCapacity);
        if (roomIsNew) result.classroomsCreated++;

        String teacherUsername = usernameFor(pc.teacher);
        boolean teacherIsNew = store.getUser(teacherUsername) == null;
        User teacher = store.getOrCreateTeacher(teacherUsername, pc.teacher.toUpperCase(Locale.ROOT), defaultTeacherPassword);
        if (teacherIsNew) result.teachersCreated++;

        String courseName = pc.extra != null ? pc.subject + " (" + pc.extra + ")" : pc.subject;
        String safeBatch = batch == null ? "" : batch;

        for (ClassSession existing : store.getAllSessions()) {
            if (existing.getDayOfWeek() == day
                    && existing.getClassroomId().equals(room.getId())
                    && existing.getStartTime().equals(start)
                    && existing.getEndTime().equals(end)
                    && existing.getTeacherUsername().equals(teacher.getUsername())
                    && existing.getCourseName().equals(courseName)
                    && safeBatch.equalsIgnoreCase(existing.getBatch() == null ? "" : existing.getBatch())) {
                result.sessionsSkippedDuplicate++;
                return;
            }
        }

        store.addSession(room.getId(), courseName, teacher.getUsername(), day, start, end, safeBatch);
        result.sessionsAdded++;
    }

    // ---------- parsing helpers ----------

    private static DayOfWeek toDayOfWeek(String sheetName) {
        try {
            return DayOfWeek.valueOf(sheetName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int[] findHeaderRowAndSemColumn(XlsxWorkbook.Sheet sheet) {
        for (int row = 1; row <= MAX_HEADER_SCAN_ROW; row++) {
            for (int col = 1; col <= 6; col++) {
                String v = sheet.get(col, row);
                if (v != null && v.trim().equalsIgnoreCase("Sem")) {
                    return new int[]{row, col};
                }
            }
        }
        return null;
    }

    private static LocalTime[] parseTimeRange(String text) {
        Matcher m = TIME_RANGE.matcher(text);
        if (!m.find()) return null;
        LocalTime start = to24h(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), m.group(3));
        LocalTime end = to24h(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), m.group(6));
        return new LocalTime[]{start, end};
    }

    private static LocalTime to24h(int hour, int minute, String ampm) {
        boolean pm = ampm.equalsIgnoreCase("pm");
        int h = hour % 12;
        if (pm) h += 12;
        return LocalTime.of(h, minute);
    }

    /** Parses "SUBJECT/TEACHER/ROOM[/EXTRA]" — whichever trailing segment is purely numeric is the room. */
    private static ParsedClass parseClassCode(String raw) {
        String[] parts = raw.split("/");
        if (parts.length < 3) return null;
        String subject = parts[0].trim();
        String teacher = parts[1].trim();
        if (subject.isEmpty() || teacher.isEmpty()) return null;

        String room = null;
        String extra = null;
        for (int i = 2; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;
            if (room == null && ROOM_TOKEN.matcher(p).matches()) {
                room = p;
            } else {
                extra = extra == null ? p : extra + ", " + p;
            }
        }
        if (room == null) return null;
        return new ParsedClass(subject, teacher, room, extra);
    }

    private static String usernameFor(String teacherCode) {
        String cleaned = teacherCode.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return cleaned.isEmpty() ? "teacher" : cleaned;
    }

    private static String cellRefLabel(int col, int row) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c > 0) {
            int rem = (c - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            c = (c - 1) / 26;
        }
        return sb + String.valueOf(row);
    }

    private static class ParsedClass {
        final String subject;
        final String teacher;
        final String room;
        final String extra; // may be null

        ParsedClass(String subject, String teacher, String room, String extra) {
            this.subject = subject;
            this.teacher = teacher;
            this.room = room;
            this.extra = extra;
        }
    }

    /** Summary of what an import did, for display to the admin. */
    public static class ImportResult {
        public int sessionsAdded;
        public int sessionsSkippedDuplicate;
        public int classroomsCreated;
        public int teachersCreated;
        public final List<String> sheetsSkipped = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();

        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Sessions added:            ").append(sessionsAdded).append('\n');
            sb.append("Sessions already present:  ").append(sessionsSkippedDuplicate).append(" (skipped)\n");
            sb.append("Classrooms created:        ").append(classroomsCreated).append('\n');
            sb.append("Teacher accounts created:  ").append(teachersCreated).append('\n');
            if (!sheetsSkipped.isEmpty()) {
                sb.append("\nSheets not recognized as a weekday (skipped): ").append(String.join(", ", sheetsSkipped)).append('\n');
            }
            if (!warnings.isEmpty()) {
                sb.append("\nWarnings (").append(warnings.size()).append("):\n");
                for (String w : warnings) sb.append("  - ").append(w).append('\n');
            }
            return sb.toString();
        }
    }
}

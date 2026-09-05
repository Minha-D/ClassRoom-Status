package com.university.classroommgmt.storage;

import com.university.classroommgmt.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Simple singleton data layer. Everything lives in memory while the app runs
 * and is flushed to plain CSV text files under ./data so it survives restarts.
 * No database, no external dependencies.
 */
public class DataStore {
    private static DataStore instance;

    private final Path dataDir = Paths.get("data");
    private final Path usersFile = dataDir.resolve("users.csv");
    private final Path classroomsFile = dataDir.resolve("classrooms.csv");
    private final Path sessionsFile = dataDir.resolve("sessions.csv");
    private final Path requestsFile = dataDir.resolve("requests.csv");

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Classroom> classrooms = new LinkedHashMap<>();
    private final Map<String, ClassSession> sessions = new LinkedHashMap<>();
    private final Map<String, BookingRequest> requests = new LinkedHashMap<>();

    private int sessionCounter = 1;
    private int requestCounter = 1;
    private int classroomCounter = 1;

    private DataStore() {
        loadAll();
        if (users.isEmpty()) seedDemoData();
    }

    public static synchronized DataStore getInstance() {
        if (instance == null) instance = new DataStore();
        return instance;
    }

    // ---------- Users ----------
    public User authenticate(String username, String password) {
        User u = users.get(username);
        if (u != null && u.getPassword().equals(password)) return u;
        return null;
    }

    public boolean addUser(User u) {
        if (users.containsKey(u.getUsername())) return false;
        users.put(u.getUsername(), u);
        saveUsers();
        return true;
    }

    public Collection<User> getAllUsers() { return users.values(); }
    public List<User> getUsersByRole(User.Role role) {
        List<User> out = new ArrayList<>();
        for (User u : users.values()) if (u.getRole() == role) out.add(u);
        return out;
    }
    public User getUser(String username) { return users.get(username); }

    // ---------- Classrooms ----------
    public Collection<Classroom> getAllClassrooms() { return classrooms.values(); }
    public Classroom getClassroom(String id) { return classrooms.get(id); }

    public Classroom addClassroom(String roomNumber, String building, int capacity) {
        String id = "R" + (classroomCounter++);
        while (classrooms.containsKey(id)) id = "R" + (classroomCounter++);
        Classroom c = new Classroom(id, roomNumber, building, capacity);
        classrooms.put(id, c);
        saveClassrooms();
        return c;
    }

    // ---------- Sessions ----------
    public Collection<ClassSession> getAllSessions() { return sessions.values(); }
    public ClassSession getSession(String id) { return sessions.get(id); }

    public ClassSession addSession(String classroomId, String courseName, String teacherUsername,
                                    DayOfWeek day, LocalTime start, LocalTime end) {
        String id = "S" + (sessionCounter++);
        while (sessions.containsKey(id)) id = "S" + (sessionCounter++);
        ClassSession s = new ClassSession(id, classroomId, courseName, teacherUsername, day, start, end,
                ClassSession.Status.SCHEDULED, null, null);
        sessions.put(id, s);
        saveSessions();
        return s;
    }

    public void cancelSession(String sessionId, String reason) {
        ClassSession s = sessions.get(sessionId);
        if (s == null) return;
        s.setStatus(ClassSession.Status.CANCELLED);
        s.setCancelReason(reason);
        s.setBookedByUsername(null);
        saveSessions();
    }

    public void restoreSession(String sessionId) {
        ClassSession s = sessions.get(sessionId);
        if (s == null) return;
        s.setStatus(ClassSession.Status.SCHEDULED);
        s.setCancelReason(null);
        s.setBookedByUsername(null);
        saveSessions();
    }

    public List<ClassSession> getSessionsForDay(DayOfWeek day) {
        List<ClassSession> out = new ArrayList<>();
        for (ClassSession s : sessions.values()) if (s.getDayOfWeek() == day) out.add(s);
        out.sort(Comparator.comparing(ClassSession::getStartTime));
        return out;
    }

    public List<ClassSession> getEmptySessionsForDay(DayOfWeek day) {
        List<ClassSession> out = new ArrayList<>();
        for (ClassSession s : getSessionsForDay(day)) {
            if (s.getStatus() == ClassSession.Status.CANCELLED) out.add(s);
        }
        return out;
    }

    public List<ClassSession> getSessionsForTeacher(String teacherUsername) {
        List<ClassSession> out = new ArrayList<>();
        for (ClassSession s : sessions.values()) if (s.getTeacherUsername().equals(teacherUsername)) out.add(s);
        out.sort(Comparator.comparing(ClassSession::getDayOfWeek).thenComparing(ClassSession::getStartTime));
        return out;
    }

    // ---------- Booking requests ----------
    public Collection<BookingRequest> getAllRequests() { return requests.values(); }

    public BookingRequest createRequest(String sessionId, String studentUsername, String note) {
        String id = "REQ" + (requestCounter++);
        while (requests.containsKey(id)) id = "REQ" + (requestCounter++);
        BookingRequest r = new BookingRequest(id, sessionId, studentUsername, LocalDateTime.now(),
                BookingRequest.Status.PENDING, note);
        requests.put(id, r);
        saveRequests();
        return r;
    }

    public List<BookingRequest> getPendingRequests() {
        List<BookingRequest> out = new ArrayList<>();
        for (BookingRequest r : requests.values()) if (r.getStatus() == BookingRequest.Status.PENDING) out.add(r);
        out.sort(Comparator.comparing(BookingRequest::getRequestedAt));
        return out;
    }

    public List<BookingRequest> getRequestsForStudent(String studentUsername) {
        List<BookingRequest> out = new ArrayList<>();
        for (BookingRequest r : requests.values()) if (r.getStudentUsername().equals(studentUsername)) out.add(r);
        out.sort(Comparator.comparing(BookingRequest::getRequestedAt).reversed());
        return out;
    }

    public void approveRequest(String requestId) {
        BookingRequest r = requests.get(requestId);
        if (r == null || r.getStatus() != BookingRequest.Status.PENDING) return;
        r.setStatus(BookingRequest.Status.APPROVED);
        ClassSession s = sessions.get(r.getSessionId());
        if (s != null) {
            s.setStatus(ClassSession.Status.BOOKED);
            s.setBookedByUsername(r.getStudentUsername());
        }
        // Auto-reject any other pending requests for the same session
        for (BookingRequest other : requests.values()) {
            if (!other.getId().equals(r.getId())
                    && other.getSessionId().equals(r.getSessionId())
                    && other.getStatus() == BookingRequest.Status.PENDING) {
                other.setStatus(BookingRequest.Status.REJECTED);
            }
        }
        saveRequests();
        saveSessions();
    }

    public void rejectRequest(String requestId) {
        BookingRequest r = requests.get(requestId);
        if (r == null || r.getStatus() != BookingRequest.Status.PENDING) return;
        r.setStatus(BookingRequest.Status.REJECTED);
        saveRequests();
    }

    // ---------- Persistence ----------
    private void loadAll() {
        try {
            Files.createDirectories(dataDir);
            loadUsers();
            loadClassrooms();
            loadSessions();
            loadRequests();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize data directory", e);
        }
    }

    private void loadUsers() {
        for (String line : readLines(usersFile)) {
            User u = User.fromCsv(line);
            users.put(u.getUsername(), u);
        }
    }

    private void loadClassrooms() {
        for (String line : readLines(classroomsFile)) {
            Classroom c = Classroom.fromCsv(line);
            classrooms.put(c.getId(), c);
            int n = parseTrailingInt(c.getId());
            if (n >= classroomCounter) classroomCounter = n + 1;
        }
    }

    private void loadSessions() {
        for (String line : readLines(sessionsFile)) {
            ClassSession s = ClassSession.fromCsv(line);
            sessions.put(s.getId(), s);
            int n = parseTrailingInt(s.getId());
            if (n >= sessionCounter) sessionCounter = n + 1;
        }
    }

    private void loadRequests() {
        for (String line : readLines(requestsFile)) {
            BookingRequest r = BookingRequest.fromCsv(line);
            requests.put(r.getId(), r);
            int n = parseTrailingInt(r.getId());
            if (n >= requestCounter) requestCounter = n + 1;
        }
    }

    private int parseTrailingInt(String id) {
        StringBuilder digits = new StringBuilder();
        for (char c : id.toCharArray()) if (Character.isDigit(c)) digits.append(c);
        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }

    private List<String> readLines(Path path) {
        try {
            if (!Files.exists(path)) return Collections.emptyList();
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            lines.removeIf(l -> l == null || l.isBlank());
            return lines;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private void writeLines(Path path, List<String> lines) {
        try {
            Files.createDirectories(dataDir);
            Files.write(path, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save " + path, e);
        }
    }

    private void saveUsers() {
        List<String> lines = new ArrayList<>();
        for (User u : users.values()) lines.add(u.toCsv());
        writeLines(usersFile, lines);
    }

    private void saveClassrooms() {
        List<String> lines = new ArrayList<>();
        for (Classroom c : classrooms.values()) lines.add(c.toCsv());
        writeLines(classroomsFile, lines);
    }

    private void saveSessions() {
        List<String> lines = new ArrayList<>();
        for (ClassSession s : sessions.values()) lines.add(s.toCsv());
        writeLines(sessionsFile, lines);
    }

    private void saveRequests() {
        List<String> lines = new ArrayList<>();
        for (BookingRequest r : requests.values()) lines.add(r.toCsv());
        writeLines(requestsFile, lines);
    }

    // ---------- Demo seed data (first run only) ----------
    private void seedDemoData() {
        users.put("admin", new User("admin", "admin123", "System Admin", User.Role.ADMIN));
        users.put("tsmith", new User("tsmith", "pass123", "Dr. T. Smith", User.Role.TEACHER));
        users.put("jdoe", new User("jdoe", "pass123", "Prof. J. Doe", User.Role.TEACHER));
        users.put("alice", new User("alice", "pass123", "Alice Johnson", User.Role.STUDENT));
        users.put("bob", new User("bob", "pass123", "Bob Williams", User.Role.STUDENT));
        saveUsers();

        Classroom c1 = addClassroom("101", "Main Hall", 40);
        Classroom c2 = addClassroom("202", "Science Block", 30);
        Classroom c3 = addClassroom("305", "Engineering Wing", 60);

        DayOfWeek today = java.time.LocalDate.now().getDayOfWeek();
        addSession(c1.getId(), "Intro to CS", "tsmith", today, LocalTime.of(9, 0), LocalTime.of(10, 30));
        addSession(c2.getId(), "Calculus II", "jdoe", today, LocalTime.of(11, 0), LocalTime.of(12, 30));
        ClassSession s3 = addSession(c3.getId(), "Data Structures", "tsmith", today, LocalTime.of(14, 0), LocalTime.of(15, 30));
        cancelSession(s3.getId(), "Teacher out sick");
    }
}

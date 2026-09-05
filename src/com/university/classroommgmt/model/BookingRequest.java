package com.university.classroommgmt.model;

import java.time.LocalDateTime;

public class BookingRequest {
    public enum Status { PENDING, APPROVED, REJECTED }

    private String id;
    private String sessionId;
    private String studentUsername;
    private LocalDateTime requestedAt;
    private Status status;
    private String note;

    public BookingRequest(String id, String sessionId, String studentUsername,
                           LocalDateTime requestedAt, Status status, String note) {
        this.id = id;
        this.sessionId = sessionId;
        this.studentUsername = studentUsername;
        this.requestedAt = requestedAt;
        this.status = status;
        this.note = note;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getStudentUsername() { return studentUsername; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public Status getStatus() { return status; }
    public String getNote() { return note; }

    public void setStatus(Status status) { this.status = status; }

    public String toCsv() {
        return String.join(",", id, sessionId, studentUsername, requestedAt.toString(),
                status.name(), esc(note == null ? "" : note));
    }

    public static BookingRequest fromCsv(String line) {
        String[] p = line.split(",", -1);
        return new BookingRequest(p[0], p[1], p[2], LocalDateTime.parse(p[3]),
                Status.valueOf(p[4]), p.length > 5 ? unesc(p[5]) : "");
    }

    private static String esc(String s) { return s == null ? "" : s.replace(",", "&#44;"); }
    private static String unesc(String s) { return s == null ? "" : s.replace("&#44;", ","); }
}

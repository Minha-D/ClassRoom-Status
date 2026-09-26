package com.university.classroommgmt.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class ClassSession {
    public enum Status { SCHEDULED, CANCELLED, BOOKED }

    private String id;
    private String classroomId;
    private String courseName;
    private String teacherUsername;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Status status;
    private String bookedByUsername; // set when a student's booking request is approved
    private String cancelReason;
    private String batch; // semester+section this session belongs to, e.g. "3B"; empty if not set

    public ClassSession(String id, String classroomId, String courseName, String teacherUsername,
                         DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Status status,
                         String bookedByUsername, String cancelReason, String batch) {
        this.id = id;
        this.classroomId = classroomId;
        this.courseName = courseName;
        this.teacherUsername = teacherUsername;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.bookedByUsername = bookedByUsername;
        this.cancelReason = cancelReason;
        this.batch = batch == null ? "" : batch;
    }

    public String getId() { return id; }
    public String getClassroomId() { return classroomId; }
    public String getCourseName() { return courseName; }
    public String getTeacherUsername() { return teacherUsername; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public Status getStatus() { return status; }
    public String getBookedByUsername() { return bookedByUsername; }
    public String getCancelReason() { return cancelReason; }
    public String getBatch() { return batch; }

    public void setStatus(Status status) { this.status = status; }
    public void setBookedByUsername(String bookedByUsername) { this.bookedByUsername = bookedByUsername; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public void setTeacherUsername(String teacherUsername) { this.teacherUsername = teacherUsername; }

    public String toCsv() {
        return String.join(",",
                id, classroomId, esc(courseName), teacherUsername, dayOfWeek.name(),
                startTime.toString(), endTime.toString(), status.name(),
                bookedByUsername == null ? "" : bookedByUsername,
                esc(cancelReason == null ? "" : cancelReason),
                esc(batch == null ? "" : batch));
    }

    public static ClassSession fromCsv(String line) {
        String[] p = line.split(",", -1);
        return new ClassSession(
                p[0], p[1], unesc(p[2]), p[3], DayOfWeek.valueOf(p[4]),
                LocalTime.parse(p[5]), LocalTime.parse(p[6]), Status.valueOf(p[7]),
                p.length > 8 && !p[8].isEmpty() ? p[8] : null,
                p.length > 9 ? unesc(p[9]) : "",
                p.length > 10 ? unesc(p[10]) : ""
        );
    }

    private static String esc(String s) { return s == null ? "" : s.replace(",", "&#44;"); }
    private static String unesc(String s) { return s == null ? "" : s.replace("&#44;", ","); }
}

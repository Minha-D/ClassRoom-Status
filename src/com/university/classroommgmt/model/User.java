package com.university.classroommgmt.model;

public class User {
    public enum Role { ADMIN, TEACHER, STUDENT }

    private String username;
    private String password;
    private String fullName;
    private Role role;
    // Semester+section, e.g. "3B" — only meaningful for STUDENT accounts; empty for others.
    // Used to match a student to their rows in the class routine.
    private String batch;

    public User(String username, String password, String fullName, Role role) {
        this(username, password, fullName, role, "");
    }

    public User(String username, String password, String fullName, Role role, String batch) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.batch = batch == null ? "" : batch;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public String getBatch() { return batch; }

    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setBatch(String batch) { this.batch = batch == null ? "" : batch; }

    // Serialize to a single CSV line: username,password,fullName,role,batch
    public String toCsv() {
        return String.join(",", escape(username), escape(password), escape(fullName), role.name(), escape(batch));
    }

    public static User fromCsv(String line) {
        String[] parts = splitCsv(line, 5);
        Role r = Role.valueOf(parts[3]);
        return new User(parts[0], parts[1], parts[2], r, parts[4]);
    }

    // Simple escaping so commas in names don't break the CSV format
    protected static String escape(String s) {
        return s == null ? "" : s.replace(",", "&#44;");
    }

    protected static String unescape(String s) {
        return s == null ? "" : s.replace("&#44;", ",");
    }

    protected static String[] splitCsv(String line, int expected) {
        String[] raw = line.split(",", -1);
        String[] out = new String[expected];
        for (int i = 0; i < expected; i++) {
            out[i] = i < raw.length ? unescape(raw[i]) : "";
        }
        return out;
    }

    @Override
    public String toString() {
        return fullName + " (" + username + ") [" + role + "]";
    }
}

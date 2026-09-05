package com.university.classroommgmt.model;

public class User {
    public enum Role { ADMIN, TEACHER, STUDENT }

    private String username;
    private String password;
    private String fullName;
    private Role role;

    public User(String username, String password, String fullName, Role role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }

    public void setPassword(String password) { this.password = password; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    // Serialize to a single CSV line: username,password,fullName,role
    public String toCsv() {
        return String.join(",", escape(username), escape(password), escape(fullName), role.name());
    }

    public static User fromCsv(String line) {
        String[] parts = splitCsv(line, 4);
        Role r = Role.valueOf(parts[3]);
        return new User(parts[0], parts[1], parts[2], r);
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

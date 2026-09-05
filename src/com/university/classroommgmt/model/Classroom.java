package com.university.classroommgmt.model;

public class Classroom {
    private String id;
    private String roomNumber;
    private String building;
    private int capacity;

    public Classroom(String id, String roomNumber, String building, int capacity) {
        this.id = id;
        this.roomNumber = roomNumber;
        this.building = building;
        this.capacity = capacity;
    }

    public String getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public String getBuilding() { return building; }
    public int getCapacity() { return capacity; }

    public String toCsv() {
        return String.join(",", id, esc(roomNumber), esc(building), String.valueOf(capacity));
    }

    public static Classroom fromCsv(String line) {
        String[] p = line.split(",", -1);
        return new Classroom(p[0], unesc(p[1]), unesc(p[2]), Integer.parseInt(p[3]));
    }

    private static String esc(String s) { return s == null ? "" : s.replace(",", "&#44;"); }
    private static String unesc(String s) { return s == null ? "" : s.replace("&#44;", ","); }

    @Override
    public String toString() {
        return building + " - Room " + roomNumber + " (cap " + capacity + ")";
    }
}

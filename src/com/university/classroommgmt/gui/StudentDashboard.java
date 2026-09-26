package com.university.classroommgmt.gui;

import com.university.classroommgmt.model.*;
import com.university.classroommgmt.storage.DataStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class StudentDashboard extends JFrame {
    private final User student;
    private final DataStore store = DataStore.getInstance();

    private final DefaultTableModel emptyModel = new DefaultTableModel(
            new Object[]{"Session ID", "Course", "Room", "Time", "Teacher", "Reason"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel myRequestsModel = new DefaultTableModel(
            new Object[]{"Request ID", "Course", "Room", "Time", "Status"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };

    private final JTable emptyTable = new JTable(emptyModel);
    private final JTable requestsTable = new JTable(myRequestsModel);
    private final JPanel routineContainer = new JPanel();

    public StudentDashboard(User student) {
        super(titleFor(student));
        this.student = student;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Empty Classrooms Today", buildEmptyClassroomsTab());
        tabs.addTab("My Class Routine", buildRoutineTab());
        tabs.addTab("My Booking Requests", buildRequestsTab());
        root.add(tabs, BorderLayout.CENTER);

        JButton accountBtn = new JButton("My Account");
        accountBtn.addActionListener(e -> AccountDialog.open(this, student, store, () -> {
            setTitle(titleFor(student));
            refresh();
        }));
        JButton logoutBtn = new JButton("Log Out");
        logoutBtn.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(accountBtn);
        south.add(logoutBtn);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        refresh();
    }

    private static String titleFor(User student) {
        String batch = student.getBatch();
        String suffix = batch != null && !batch.isBlank() ? " (Semester/Section " + batch + ")" : "";
        return "Student Dashboard - " + student.getFullName() + suffix;
    }

    // ---------- Empty classrooms today ----------
    private JPanel buildEmptyClassroomsTab() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        String todayName = today.getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel header = new JLabel("Empty classrooms today (" + todayName + ")");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        p.add(header, BorderLayout.NORTH);
        p.add(new JScrollPane(emptyTable), BorderLayout.CENTER);

        JButton requestBtn = new JButton("Request to Book Selected Class");
        requestBtn.addActionListener(e -> requestBooking());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(requestBtn);
        btns.add(refreshBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    // ---------- My Class Routine (day-separated) ----------
    private JPanel buildRoutineTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String batch = student.getBatch();
        String note = (batch == null || batch.isBlank())
                ? "No semester/section is set on your account yet — ask an admin to add it so your routine can show up here."
                : "Showing your routine for semester/section " + batch + ".";
        JLabel header = new JLabel(note);
        p.add(header, BorderLayout.NORTH);

        routineContainer.setLayout(new BoxLayout(routineContainer, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(routineContainer);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        p.add(scroll, BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(refreshBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private void rebuildRoutinePanel() {
        String batch = student.getBatch();
        DaySections.rebuild(routineContainer,
                day -> sessionsForBatchAndDay(batch, day),
                new String[]{"Course", "Room", "Time", "Teacher", "Status"},
                s -> {
                    Classroom room = store.getClassroom(s.getClassroomId());
                    User teacher = store.getUser(s.getTeacherUsername());
                    return new Object[]{
                            s.getCourseName(),
                            room != null ? room.toString() : s.getClassroomId(),
                            s.getStartTime() + " - " + s.getEndTime(),
                            teacher != null ? teacher.getFullName() : s.getTeacherUsername(),
                            s.getStatus()
                    };
                });
    }

    private List<ClassSession> sessionsForBatchAndDay(String batch, DayOfWeek day) {
        List<ClassSession> out = new java.util.ArrayList<>();
        if (batch == null || batch.isBlank()) return out;
        for (ClassSession s : store.getSessionsForDay(day)) {
            if (batch.equalsIgnoreCase(s.getBatch())) out.add(s);
        }
        return out;
    }

    // ---------- My Booking Requests ----------
    private JPanel buildRequestsTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(requestsTable), BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(refreshBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private void refresh() {
        emptyModel.setRowCount(0);
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        List<ClassSession> empty = store.getEmptySessionsForDay(today);
        for (ClassSession s : empty) {
            Classroom room = store.getClassroom(s.getClassroomId());
            User teacher = store.getUser(s.getTeacherUsername());
            emptyModel.addRow(new Object[]{
                    s.getId(),
                    s.getCourseName(),
                    room != null ? room.toString() : s.getClassroomId(),
                    s.getStartTime() + " - " + s.getEndTime(),
                    teacher != null ? teacher.getFullName() : s.getTeacherUsername(),
                    s.getCancelReason() == null ? "" : s.getCancelReason()
            });
        }

        rebuildRoutinePanel();

        myRequestsModel.setRowCount(0);
        for (BookingRequest r : store.getRequestsForStudent(student.getUsername())) {
            ClassSession s = store.getSession(r.getSessionId());
            Classroom room = s != null ? store.getClassroom(s.getClassroomId()) : null;
            myRequestsModel.addRow(new Object[]{
                    r.getId(),
                    s != null ? s.getCourseName() : "(deleted)",
                    room != null ? room.toString() : "-",
                    s != null ? s.getStartTime() + " - " + s.getEndTime() : "-",
                    r.getStatus()
            });
        }
    }

    private void requestBooking() {
        int row = emptyTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an empty class slot first.",
                    "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String sessionId = (String) emptyModel.getValueAt(row, 0);
        ClassSession s = store.getSession(sessionId);
        if (s == null || s.getStatus() != ClassSession.Status.CANCELLED) {
            JOptionPane.showMessageDialog(this, "This slot is no longer available.",
                    "Unavailable", JOptionPane.WARNING_MESSAGE);
            refresh();
            return;
        }
        String note = JOptionPane.showInputDialog(this,
                "Optional note to the admin (reason for booking):", "");
        if (note == null) return; // cancelled
        store.createRequest(sessionId, student.getUsername(), note);
        JOptionPane.showMessageDialog(this, "Request submitted. Awaiting admin approval.");
        refresh();
    }
}

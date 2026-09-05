package com.university.classroommgmt.gui;

import com.university.classroommgmt.model.*;
import com.university.classroommgmt.storage.DataStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public class AdminDashboard extends JFrame {
    private final User admin;
    private final DataStore store = DataStore.getInstance();

    // Pending requests tab
    private final DefaultTableModel reqModel = new DefaultTableModel(
            new Object[]{"Req ID", "Student", "Course", "Room", "Day", "Time", "Note"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable reqTable = new JTable(reqModel);

    // All sessions tab
    private final DefaultTableModel sessModel = new DefaultTableModel(
            new Object[]{"ID", "Course", "Room", "Teacher", "Day", "Time", "Status", "Booked By"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable sessTable = new JTable(sessModel);

    // Users tab
    private final DefaultTableModel userModel = new DefaultTableModel(
            new Object[]{"Username", "Full Name", "Role"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable userTable = new JTable(userModel);

    // Classrooms tab
    private final DefaultTableModel roomModel = new DefaultTableModel(
            new Object[]{"ID", "Room #", "Building", "Capacity"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable roomTable = new JTable(roomModel);

    public AdminDashboard(User admin) {
        super("Admin Dashboard - " + admin.getFullName());
        this.admin = admin;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(880, 620);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Pending Requests", buildRequestsTab());
        tabs.addTab("All Sessions", buildSessionsTab());
        tabs.addTab("Users", buildUsersTab());
        tabs.addTab("Classrooms", buildRoomsTab());

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);

        JButton logoutBtn = new JButton("Log Out");
        logoutBtn.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(logoutBtn);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        refreshAll();
    }

    // ---------- Pending Requests ----------
    private JPanel buildRequestsTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(reqTable), BorderLayout.CENTER);

        JButton approveBtn = new JButton("Approve");
        approveBtn.addActionListener(e -> {
            String id = selectedId(reqTable, reqModel);
            if (id == null) return;
            store.approveRequest(id);
            refreshAll();
        });
        JButton rejectBtn = new JButton("Reject");
        rejectBtn.addActionListener(e -> {
            String id = selectedId(reqTable, reqModel);
            if (id == null) return;
            store.rejectRequest(id);
            refreshAll();
        });
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshAll());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(approveBtn);
        btns.add(rejectBtn);
        btns.add(refreshBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    // ---------- All Sessions ----------
    private JPanel buildSessionsTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(sessTable), BorderLayout.CENTER);

        JButton addBtn = new JButton("Add Class Session");
        addBtn.addActionListener(e -> addSessionDialog());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshAll());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(addBtn);
        btns.add(refreshBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private void addSessionDialog() {
        List<Classroom> rooms = new java.util.ArrayList<>(store.getAllClassrooms());
        List<User> teachers = store.getUsersByRole(User.Role.TEACHER);
        if (rooms.isEmpty() || teachers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one classroom and one teacher first.");
            return;
        }

        JComboBox<Classroom> roomBox = new JComboBox<>(rooms.toArray(new Classroom[0]));
        JComboBox<User> teacherBox = new JComboBox<>(teachers.toArray(new User[0]));
        JTextField courseField = new JTextField();
        JComboBox<DayOfWeek> dayBox = new JComboBox<>(DayOfWeek.values());
        JTextField startField = new JTextField("09:00");
        JTextField endField = new JTextField("10:30");

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Course name:")); form.add(courseField);
        form.add(new JLabel("Classroom:")); form.add(roomBox);
        form.add(new JLabel("Teacher:")); form.add(teacherBox);
        form.add(new JLabel("Day:")); form.add(dayBox);
        form.add(new JLabel("Start (HH:mm):")); form.add(startField);
        form.add(new JLabel("End (HH:mm):")); form.add(endField);

        int result = JOptionPane.showConfirmDialog(this, form, "Add Class Session",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            String course = courseField.getText().trim();
            if (course.isEmpty()) throw new IllegalArgumentException("Course name is required.");
            LocalTime start = LocalTime.parse(startField.getText().trim());
            LocalTime end = LocalTime.parse(endField.getText().trim());
            if (!end.isAfter(start)) throw new IllegalArgumentException("End time must be after start time.");
            Classroom room = (Classroom) roomBox.getSelectedItem();
            User teacher = (User) teacherBox.getSelectedItem();
            DayOfWeek day = (DayOfWeek) dayBox.getSelectedItem();
            store.addSession(room.getId(), course, teacher.getUsername(), day, start, end);
            refreshAll();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------- Users ----------
    private JPanel buildUsersTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JButton addBtn = new JButton("Add User");
        addBtn.addActionListener(e -> addUserDialog());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshAll());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(addBtn);
        btns.add(refreshBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private void addUserDialog() {
        JTextField usernameField = new JTextField();
        JTextField nameField = new JTextField();
        JPasswordField passField = new JPasswordField();
        JComboBox<User.Role> roleBox = new JComboBox<>(User.Role.values());

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Username:")); form.add(usernameField);
        form.add(new JLabel("Full name:")); form.add(nameField);
        form.add(new JLabel("Password:")); form.add(passField);
        form.add(new JLabel("Role:")); form.add(roleBox);

        int result = JOptionPane.showConfirmDialog(this, form, "Add User",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String username = usernameField.getText().trim();
        String fullName = nameField.getText().trim();
        String password = new String(passField.getPassword());
        User.Role role = (User.Role) roleBox.getSelectedItem();

        if (username.isEmpty() || fullName.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All fields are required.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        boolean added = store.addUser(new User(username, password, fullName, role));
        if (!added) {
            JOptionPane.showMessageDialog(this, "That username already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        refreshAll();
    }

    // ---------- Classrooms ----------
    private JPanel buildRoomsTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(roomTable), BorderLayout.CENTER);

        JButton addBtn = new JButton("Add Classroom");
        addBtn.addActionListener(e -> addRoomDialog());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshAll());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(addBtn);
        btns.add(refreshBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private void addRoomDialog() {
        JTextField roomField = new JTextField();
        JTextField buildingField = new JTextField();
        JTextField capField = new JTextField("30");

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Room number:")); form.add(roomField);
        form.add(new JLabel("Building:")); form.add(buildingField);
        form.add(new JLabel("Capacity:")); form.add(capField);

        int result = JOptionPane.showConfirmDialog(this, form, "Add Classroom",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            String roomNum = roomField.getText().trim();
            String building = buildingField.getText().trim();
            int cap = Integer.parseInt(capField.getText().trim());
            if (roomNum.isEmpty() || building.isEmpty()) throw new IllegalArgumentException("Fields required.");
            store.addClassroom(roomNum, building, cap);
            refreshAll();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------- Shared ----------
    private String selectedId(JTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a row first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return (String) model.getValueAt(row, 0);
    }

    private void refreshAll() {
        reqModel.setRowCount(0);
        for (BookingRequest r : store.getPendingRequests()) {
            ClassSession s = store.getSession(r.getSessionId());
            Classroom room = s != null ? store.getClassroom(s.getClassroomId()) : null;
            User student = store.getUser(r.getStudentUsername());
            reqModel.addRow(new Object[]{
                    r.getId(),
                    student != null ? student.getFullName() : r.getStudentUsername(),
                    s != null ? s.getCourseName() : "(deleted)",
                    room != null ? room.toString() : "-",
                    s != null ? s.getDayOfWeek() : "-",
                    s != null ? s.getStartTime() + " - " + s.getEndTime() : "-",
                    r.getNote() == null ? "" : r.getNote()
            });
        }

        sessModel.setRowCount(0);
        for (ClassSession s : store.getAllSessions()) {
            Classroom room = store.getClassroom(s.getClassroomId());
            User teacher = store.getUser(s.getTeacherUsername());
            String bookedBy = "";
            if (s.getBookedByUsername() != null) {
                User u = store.getUser(s.getBookedByUsername());
                bookedBy = u != null ? u.getFullName() : s.getBookedByUsername();
            }
            sessModel.addRow(new Object[]{
                    s.getId(), s.getCourseName(),
                    room != null ? room.toString() : s.getClassroomId(),
                    teacher != null ? teacher.getFullName() : s.getTeacherUsername(),
                    s.getDayOfWeek(),
                    s.getStartTime() + " - " + s.getEndTime(),
                    s.getStatus(),
                    bookedBy
            });
        }

        userModel.setRowCount(0);
        for (User u : store.getAllUsers()) {
            userModel.addRow(new Object[]{u.getUsername(), u.getFullName(), u.getRole()});
        }

        roomModel.setRowCount(0);
        for (Classroom c : store.getAllClassrooms()) {
            roomModel.addRow(new Object[]{c.getId(), c.getRoomNumber(), c.getBuilding(), c.getCapacity()});
        }
    }
}

package com.university.classroommgmt.gui;

import com.university.classroommgmt.importer.RoutineImporter;
import com.university.classroommgmt.model.*;
import com.university.classroommgmt.storage.DataStore;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public class AdminDashboard extends JFrame {
    private User admin;
    private final DataStore store = DataStore.getInstance();

    // Pending requests tab
    private final DefaultTableModel reqModel = new DefaultTableModel(
            new Object[]{"Req ID", "Student", "Course", "Room", "Day", "Time", "Note"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable reqTable = new JTable(reqModel);

    // All sessions tab — rebuilt day-by-day rather than one flat table
    private JPanel sessionsContainer;

    // Users tab
    private final DefaultTableModel userModel = new DefaultTableModel(
            new Object[]{"Username", "Full Name", "Role", "Semester/Section"}, 0) {
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
        setSize(900, 640);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Pending Requests", buildRequestsTab());
        tabs.addTab("All Sessions", buildSessionsTab());
        tabs.addTab("Users", buildUsersTab());
        tabs.addTab("Classrooms", buildRoomsTab());
        tabs.addTab("Import Routine", buildImportTab());

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);

        JButton accountBtn = new JButton("My Account");
        accountBtn.addActionListener(e -> AccountDialog.open(this, admin, store, () -> {
            setTitle("Admin Dashboard - " + admin.getFullName());
            refreshAll();
        }));
        JButton logoutBtn = new JButton("Log Out");
        logoutBtn.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(accountBtn);
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

    // ---------- All Sessions (day-separated) ----------
    private JPanel buildSessionsTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        sessionsContainer = new JPanel();
        sessionsContainer.setLayout(new BoxLayout(sessionsContainer, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(sessionsContainer);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        p.add(scroll, BorderLayout.CENTER);

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

    private void rebuildSessionsPanel() {
        DaySections.rebuild(sessionsContainer,
                store::getSessionsForDay,
                new String[]{"ID", "Course", "Batch", "Room", "Teacher", "Time", "Status", "Booked By"},
                s -> {
                    Classroom room = store.getClassroom(s.getClassroomId());
                    User teacher = store.getUser(s.getTeacherUsername());
                    String bookedBy = "";
                    if (s.getBookedByUsername() != null) {
                        User u = store.getUser(s.getBookedByUsername());
                        bookedBy = u != null ? u.getFullName() : s.getBookedByUsername();
                    }
                    return new Object[]{
                            s.getId(), s.getCourseName(), s.getBatch(),
                            room != null ? room.toString() : s.getClassroomId(),
                            teacher != null ? teacher.getFullName() : s.getTeacherUsername(),
                            s.getStartTime() + " - " + s.getEndTime(),
                            s.getStatus(), bookedBy
                    };
                });
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
        JTextField batchField = new JTextField();

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Course name:")); form.add(courseField);
        form.add(new JLabel("Classroom:")); form.add(roomBox);
        form.add(new JLabel("Teacher:")); form.add(teacherBox);
        form.add(new JLabel("Day:")); form.add(dayBox);
        form.add(new JLabel("Start (HH:mm):")); form.add(startField);
        form.add(new JLabel("End (HH:mm):")); form.add(endField);
        form.add(new JLabel("Semester/Section (optional, e.g. 3B):")); form.add(batchField);

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
            String batch = batchField.getText().trim();
            store.addSession(room.getId(), course, teacher.getUsername(), day, start, end, batch);
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

        JButton addUserBtn = new JButton("Add User (Admin/Teacher)");
        addUserBtn.addActionListener(e -> addUserDialog());
        JButton addStudentBtn = new JButton("Add Student");
        addStudentBtn.addActionListener(e -> addStudentDialog());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshAll());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(addUserBtn);
        btns.add(addStudentBtn);
        btns.add(refreshBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private void addUserDialog() {
        JTextField usernameField = new JTextField();
        JTextField nameField = new JTextField();
        JPasswordField passField = new JPasswordField();
        JComboBox<User.Role> roleBox = new JComboBox<>(new User.Role[]{User.Role.ADMIN, User.Role.TEACHER});

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

    private static final Integer[] SEMESTERS = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final String[] SECTIONS = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};

    private void addStudentDialog() {
        JTextField nameField = new JTextField();
        JTextField idField = new JTextField();
        JComboBox<Integer> semBox = new JComboBox<>(SEMESTERS);
        JComboBox<String> secBox = new JComboBox<>(SECTIONS);
        JPasswordField passField = new JPasswordField();

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Full name:")); form.add(nameField);
        form.add(new JLabel("Student ID (16 digits):")); form.add(idField);
        form.add(new JLabel("Semester:")); form.add(semBox);
        form.add(new JLabel("Section:")); form.add(secBox);
        form.add(new JLabel("Password:")); form.add(passField);

        JPanel wrapper = new JPanel(new BorderLayout(4, 4));
        wrapper.add(new JLabel("<html>The Student ID is used as the login username, e.g. <b>0222510005101083</b>.<br>"
                + "Semester + section (e.g. 3B) is used to show this student their class routine.</html>"), BorderLayout.NORTH);
        wrapper.add(form, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, wrapper, "Add Student",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String fullName = nameField.getText().trim();
        String id = idField.getText().trim();
        String password = new String(passField.getPassword());

        if (fullName.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Full name and password are required.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!id.matches("\\d{16}")) {
            JOptionPane.showMessageDialog(this,
                    "Student ID must be exactly 16 digits, e.g. 0222510005101083.",
                    "Invalid Student ID", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (store.getUser(id) != null) {
            JOptionPane.showMessageDialog(this, "A user with that Student ID already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String batch = semBox.getSelectedItem().toString() + secBox.getSelectedItem().toString();
        User student = new User(id, password, fullName, User.Role.STUDENT, batch);
        store.addUser(student);
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

    // ---------- Import Routine (Excel) ----------
    private JTextArea importLog;

    private JPanel buildImportTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel intro = new JLabel("<html>Upload the university's weekly class routine (.xlsx) to auto-create "
                + "classrooms, teacher accounts, and class sessions.<br>"
                + "One sheet per weekday (Saturday\u2013Wednesday), cells shaped like "
                + "<b>Subject/Teacher/Room</b>, e.g. <b>CGVA/AU/404</b>.</html>");

        JTextField buildingField = new JTextField("Main Building", 12);
        JTextField capacityField = new JTextField("40", 4);
        JTextField passwordField = new JTextField("changeme123", 12);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        form.add(new JLabel("Default building for new rooms:"));
        form.add(buildingField);
        form.add(new JLabel("Default capacity:"));
        form.add(capacityField);
        form.add(new JLabel("Default password for new teacher accounts:"));
        form.add(passwordField);

        importLog = new JTextArea();
        importLog.setEditable(false);
        importLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        importLog.setText("No file imported yet.");

        JButton importBtn = new JButton("Choose Excel File (.xlsx) & Import\u2026");
        importBtn.addActionListener(e -> runImport(buildingField, capacityField, passwordField));

        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(intro, BorderLayout.NORTH);
        north.add(form, BorderLayout.CENTER);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnRow.add(importBtn);
        north.add(btnRow, BorderLayout.SOUTH);

        p.add(north, BorderLayout.NORTH);
        p.add(new JScrollPane(importLog), BorderLayout.CENTER);
        return p;
    }

    private void runImport(JTextField buildingField, JTextField capacityField, JTextField passwordField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));
        int choice = chooser.showOpenDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();

        String building = buildingField.getText().trim();
        if (building.isEmpty()) building = "Main Building";

        int capacity;
        try {
            capacity = Integer.parseInt(capacityField.getText().trim());
        } catch (NumberFormatException ex) {
            capacity = 40;
        }

        String password = passwordField.getText().trim();
        if (password.isEmpty()) password = "changeme123";

        try {
            RoutineImporter.ImportResult result = RoutineImporter.importFromFile(file, store, building, capacity, password);
            importLog.setText("Imported: " + file.getName() + "\n\n" + result.toSummary());
            importLog.setCaretPosition(0);
            JOptionPane.showMessageDialog(this,
                    result.sessionsAdded + " session(s) added, " + result.sessionsSkippedDuplicate
                            + " already present, " + result.classroomsCreated + " classroom(s) created, "
                            + result.teachersCreated + " teacher account(s) created.",
                    "Import finished", JOptionPane.INFORMATION_MESSAGE);
            refreshAll();
        } catch (Exception ex) {
            importLog.setText("Import failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(),
                    "Import error", JOptionPane.ERROR_MESSAGE);
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

        rebuildSessionsPanel();

        userModel.setRowCount(0);
        for (User u : store.getAllUsers()) {
            userModel.addRow(new Object[]{u.getUsername(), u.getFullName(), u.getRole(),
                    u.getBatch() == null ? "" : u.getBatch()});
        }

        roomModel.setRowCount(0);
        for (Classroom c : store.getAllClassrooms()) {
            roomModel.addRow(new Object[]{c.getId(), c.getRoomNumber(), c.getBuilding(), c.getCapacity()});
        }
    }
}

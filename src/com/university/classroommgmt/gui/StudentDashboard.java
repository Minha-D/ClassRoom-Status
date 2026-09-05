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

    public StudentDashboard(User student) {
        super("Student Dashboard - " + student.getFullName());
        this.student = student;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(760, 560);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        DayOfWeek today = LocalDate.now().getDayOfWeek();
        String todayName = today.getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        JLabel header = new JLabel("Empty classrooms today (" + todayName + ")");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 15f));
        root.add(header, BorderLayout.NORTH);

        JScrollPane emptyScroll = new JScrollPane(emptyTable);
        emptyScroll.setPreferredSize(new Dimension(700, 200));

        JButton requestBtn = new JButton("Request to Book Selected Class");
        requestBtn.addActionListener(e -> requestBooking());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());

        JPanel emptyPanel = new JPanel(new BorderLayout(6, 6));
        emptyPanel.add(emptyScroll, BorderLayout.CENTER);
        JPanel emptyBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        emptyBtns.add(requestBtn);
        emptyBtns.add(refreshBtn);
        emptyPanel.add(emptyBtns, BorderLayout.SOUTH);

        JLabel myReqLabel = new JLabel("My Booking Requests");
        myReqLabel.setFont(myReqLabel.getFont().deriveFont(Font.BOLD, 13f));
        JScrollPane reqScroll = new JScrollPane(requestsTable);
        reqScroll.setPreferredSize(new Dimension(700, 180));
        JPanel reqPanel = new JPanel(new BorderLayout(6, 6));
        reqPanel.add(myReqLabel, BorderLayout.NORTH);
        reqPanel.add(reqScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, emptyPanel, reqPanel);
        split.setResizeWeight(0.55);
        root.add(split, BorderLayout.CENTER);

        JButton logoutBtn = new JButton("Log Out");
        logoutBtn.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(logoutBtn);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        refresh();
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

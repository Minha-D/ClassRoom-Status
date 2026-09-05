package com.university.classroommgmt.gui;

import com.university.classroommgmt.model.*;
import com.university.classroommgmt.storage.DataStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class TeacherDashboard extends JFrame {
    private final User teacher;
    private final DataStore store = DataStore.getInstance();

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Session ID", "Course", "Room", "Day", "Time", "Status", "Booked By"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public TeacherDashboard(User teacher) {
        super("Teacher Dashboard - " + teacher.getFullName());
        this.teacher = teacher;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(760, 520);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel header = new JLabel("My Weekly Schedule");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 15f));
        root.add(header, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        root.add(scroll, BorderLayout.CENTER);

        JButton cancelBtn = new JButton("Cancel Selected Class (Flag as Empty)");
        cancelBtn.addActionListener(e -> cancelSelected());
        JButton restoreBtn = new JButton("Restore Selected Class");
        restoreBtn.addActionListener(e -> restoreSelected());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        JButton logoutBtn = new JButton("Log Out");
        logoutBtn.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(cancelBtn);
        south.add(restoreBtn);
        south.add(refreshBtn);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(south, BorderLayout.WEST);
        JPanel logoutPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logoutPanel.add(logoutBtn);
        bottom.add(logoutPanel, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        refresh();
    }

    private void refresh() {
        model.setRowCount(0);
        List<ClassSession> sessions = store.getSessionsForTeacher(teacher.getUsername());
        for (ClassSession s : sessions) {
            Classroom room = store.getClassroom(s.getClassroomId());
            String bookedBy = "";
            if (s.getBookedByUsername() != null) {
                User u = store.getUser(s.getBookedByUsername());
                bookedBy = u != null ? u.getFullName() : s.getBookedByUsername();
            }
            model.addRow(new Object[]{
                    s.getId(), s.getCourseName(),
                    room != null ? room.toString() : s.getClassroomId(),
                    s.getDayOfWeek(),
                    s.getStartTime() + " - " + s.getEndTime(),
                    s.getStatus(),
                    bookedBy
            });
        }
    }

    private void cancelSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a class first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String sessionId = (String) model.getValueAt(row, 0);
        ClassSession s = store.getSession(sessionId);
        if (s == null) return;
        if (s.getStatus() == ClassSession.Status.CANCELLED) {
            JOptionPane.showMessageDialog(this, "That class is already flagged as empty.");
            return;
        }
        if (s.getStatus() == ClassSession.Status.BOOKED) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "This slot is currently booked by a student. Cancelling will remove that booking. Continue?",
                    "Confirm cancel", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
        }
        String reason = JOptionPane.showInputDialog(this, "Reason for cancelling this class:", "");
        if (reason == null) return;
        store.cancelSession(sessionId, reason.isBlank() ? "No reason given" : reason);
        JOptionPane.showMessageDialog(this, "Class flagged as empty. Students can now request to book it.");
        refresh();
    }

    private void restoreSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a class first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String sessionId = (String) model.getValueAt(row, 0);
        ClassSession s = store.getSession(sessionId);
        if (s == null) return;
        if (s.getStatus() == ClassSession.Status.SCHEDULED) {
            JOptionPane.showMessageDialog(this, "That class is already active.");
            return;
        }
        store.restoreSession(sessionId);
        refresh();
    }
}

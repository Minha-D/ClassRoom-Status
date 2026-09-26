package com.university.classroommgmt.gui;

import com.university.classroommgmt.model.*;
import com.university.classroommgmt.storage.DataStore;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TeacherDashboard extends JFrame {
    private final User teacher;
    private final DataStore store = DataStore.getInstance();

    private final JPanel scheduleContainer = new JPanel();
    private String selectedSessionId;

    public TeacherDashboard(User teacher) {
        super("Teacher Dashboard - " + teacher.getFullName());
        this.teacher = teacher;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(780, 560);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel header = new JLabel("My Weekly Schedule");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 15f));
        root.add(header, BorderLayout.NORTH);

        scheduleContainer.setLayout(new BoxLayout(scheduleContainer, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(scheduleContainer);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);

        JButton cancelBtn = new JButton("Cancel Selected Class (Flag as Empty)");
        cancelBtn.addActionListener(e -> cancelSelected());
        JButton restoreBtn = new JButton("Restore Selected Class");
        restoreBtn.addActionListener(e -> restoreSelected());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        JButton accountBtn = new JButton("My Account");
        accountBtn.addActionListener(e -> AccountDialog.open(this, teacher, store, () -> {
            setTitle("Teacher Dashboard - " + teacher.getFullName());
            refresh();
        }));
        JButton logoutBtn = new JButton("Log Out");
        logoutBtn.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(cancelBtn);
        south.add(restoreBtn);
        south.add(refreshBtn);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(south, BorderLayout.WEST);
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.add(accountBtn);
        rightPanel.add(logoutBtn);
        bottom.add(rightPanel, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        refresh();
    }

    private void refresh() {
        selectedSessionId = null;
        DaySections.rebuildSelectable(scheduleContainer,
                day -> filterByDay(store.getSessionsForTeacher(teacher.getUsername()), day),
                new String[]{"Session ID", "Course", "Batch", "Room", "Time", "Status", "Booked By"},
                s -> {
                    Classroom room = store.getClassroom(s.getClassroomId());
                    String bookedBy = "";
                    if (s.getBookedByUsername() != null) {
                        User u = store.getUser(s.getBookedByUsername());
                        bookedBy = u != null ? u.getFullName() : s.getBookedByUsername();
                    }
                    return new Object[]{
                            s.getId(), s.getCourseName(), s.getBatch(),
                            room != null ? room.toString() : s.getClassroomId(),
                            s.getStartTime() + " - " + s.getEndTime(),
                            s.getStatus(), bookedBy
                    };
                },
                id -> selectedSessionId = id);
    }

    private static List<ClassSession> filterByDay(List<ClassSession> sessions, java.time.DayOfWeek day) {
        List<ClassSession> out = new java.util.ArrayList<>();
        for (ClassSession s : sessions) if (s.getDayOfWeek() == day) out.add(s);
        return out;
    }

    private void cancelSelected() {
        if (selectedSessionId == null) {
            JOptionPane.showMessageDialog(this, "Select a class first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ClassSession s = store.getSession(selectedSessionId);
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
        store.cancelSession(selectedSessionId, reason.isBlank() ? "No reason given" : reason);
        JOptionPane.showMessageDialog(this, "Class flagged as empty. Students can now request to book it.");
        refresh();
    }

    private void restoreSelected() {
        if (selectedSessionId == null) {
            JOptionPane.showMessageDialog(this, "Select a class first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ClassSession s = store.getSession(selectedSessionId);
        if (s == null) return;
        if (s.getStatus() == ClassSession.Status.SCHEDULED) {
            JOptionPane.showMessageDialog(this, "That class is already active.");
            return;
        }
        store.restoreSession(selectedSessionId);
        refresh();
    }
}

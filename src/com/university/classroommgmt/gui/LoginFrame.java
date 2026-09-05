package com.university.classroommgmt.gui;

import com.university.classroommgmt.model.User;
import com.university.classroommgmt.storage.DataStore;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    private final JTextField usernameField = new JTextField(16);
    private final JPasswordField passwordField = new JPasswordField(16);

    public LoginFrame() {
        super("University Classroom Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;

        JLabel title = new JLabel("Classroom Management System");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        root.add(title, gc);

        gc.gridy++;
        JLabel subtitle = new JLabel("Sign in to continue");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        root.add(subtitle, gc);

        gc.gridwidth = 1;
        gc.gridy++;
        gc.gridx = 0; root.add(new JLabel("Username:"), gc);
        gc.gridx = 1; root.add(usernameField, gc);

        gc.gridy++;
        gc.gridx = 0; root.add(new JLabel("Password:"), gc);
        gc.gridx = 1; root.add(passwordField, gc);

        gc.gridy++;
        gc.gridx = 0; gc.gridwidth = 2;
        JButton loginBtn = new JButton("Log In");
        root.add(loginBtn, gc);

        gc.gridy++;
        JTextArea hint = new JTextArea(
                "Demo accounts:\n" +
                "  admin / admin123   (Admin)\n" +
                "  tsmith / pass123   (Teacher)\n" +
                "  alice / pass123    (Student)");
        hint.setEditable(false);
        hint.setOpaque(false);
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(Color.GRAY);
        root.add(hint, gc);

        getRootPane().setDefaultButton(loginBtn);
        loginBtn.addActionListener(e -> doLogin());

        setContentPane(root);
        pack();
        setLocationRelativeTo(null);
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both username and password.",
                    "Missing info", JOptionPane.WARNING_MESSAGE);
            return;
        }
        User user = DataStore.getInstance().authenticate(username, password);
        if (user == null) {
            JOptionPane.showMessageDialog(this, "Invalid username or password.",
                    "Login failed", JOptionPane.ERROR_MESSAGE);
            passwordField.setText("");
            return;
        }
        dispose();
        switch (user.getRole()) {
            case ADMIN -> new AdminDashboard(user).setVisible(true);
            case TEACHER -> new TeacherDashboard(user).setVisible(true);
            case STUDENT -> new StudentDashboard(user).setVisible(true);
        }
    }
}

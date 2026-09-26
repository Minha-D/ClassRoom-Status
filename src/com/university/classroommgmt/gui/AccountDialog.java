package com.university.classroommgmt.gui;

import com.university.classroommgmt.model.User;
import com.university.classroommgmt.storage.DataStore;

import javax.swing.*;
import java.awt.*;

/**
 * "My Account" dialog: lets the signed-in user change their own full name, username, and/or
 * password. Username changes are cascaded everywhere that username is referenced (see
 * DataStore.updateAccount), so it's always safe to change.
 */
public final class AccountDialog {

    private AccountDialog() { }

    /**
     * Shows the dialog for {@code user}. If the user confirms and the update succeeds,
     * {@code onSuccess} runs (typically to refresh the window title and any tables).
     */
    public static void open(JFrame parent, User user, DataStore store, Runnable onSuccess) {
        JTextField fullNameField = new JTextField(user.getFullName(), 18);
        JTextField usernameField = new JTextField(user.getUsername(), 18);
        JPasswordField newPasswordField = new JPasswordField(18);
        JPasswordField confirmPasswordField = new JPasswordField(18);

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Full name:"));
        form.add(fullNameField);
        form.add(new JLabel("Username:"));
        form.add(usernameField);
        form.add(new JLabel("New password:"));
        form.add(newPasswordField);
        form.add(new JLabel("Confirm new password:"));
        form.add(confirmPasswordField);

        JPanel wrapper = new JPanel(new BorderLayout(4, 4));
        wrapper.add(new JLabel("Leave the password fields blank to keep your current password."), BorderLayout.NORTH);
        wrapper.add(form, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(parent, wrapper, "My Account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String newPassword = new String(newPasswordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());
        if (!newPassword.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(parent, "New password and confirmation do not match.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String currentUsername = user.getUsername();
        String error = store.updateAccount(currentUsername, usernameField.getText(),
                fullNameField.getText(), newPassword.isEmpty() ? null : newPassword);
        if (error != null) {
            JOptionPane.showMessageDialog(parent, error, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(parent, "Account updated.", "Success", JOptionPane.INFORMATION_MESSAGE);
        if (onSuccess != null) onSuccess.run();
    }
}

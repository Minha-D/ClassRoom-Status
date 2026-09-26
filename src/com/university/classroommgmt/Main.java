package com.university.classroommgmt;

import com.university.classroommgmt.gui.LoginFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        applyLookAndFeel();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    /**
     * Uses FlatLaf (https://www.formdev.com/flatlaf/) for a modern look if its jar has been
     * placed on the classpath (see lib/README.txt) — loaded via reflection so the app still
     * builds and runs fine without it, just falling back to the platform's native look.
     */
    private static void applyLookAndFeel() {
        if (trySetLookAndFeel("com.formdev.flatlaf.FlatLightLaf")) {
            applyFlatLafPolish();
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Falls back to the default (Metal) look and feel — still functional either way.
        }
    }

    /** A few tasteful, officially-documented FlatLaf tweaks — rounded corners and comfier spacing. */
    private static void applyFlatLafPolish() {
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));
    }

    private static boolean trySetLookAndFeel(String className) {
        try {
            Class<?> lafClass = Class.forName(className);
            UIManager.setLookAndFeel((LookAndFeel) lafClass.getDeclaredConstructor().newInstance());
            return true;
        } catch (ClassNotFoundException e) {
            return false; // FlatLaf jar isn't on the classpath — that's fine, just use the fallback
        } catch (Exception e) {
            System.err.println("Found FlatLaf but couldn't activate it (" + e + "); using the fallback look.");
            return false;
        }
    }
}

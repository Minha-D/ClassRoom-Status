package com.university.classroommgmt.gui;

import com.university.classroommgmt.model.ClassSession;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builds a scrollable, day-separated view of class sessions (a bold "Saturday" header and its
 * own small table, then "Sunday" and its table, and so on) instead of dumping every session
 * across every day into one flat table.
 */
final class DaySections {

    /** Week order used throughout the app (matches the uploaded routine's sheet order). */
    static final DayOfWeek[] WEEK_ORDER = {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };

    private DaySections() { }

    interface RowMapper {
        /** Column 0 of the returned row must be the session's id. */
        Object[] map(ClassSession s);
    }

    /** Rebuilds {@code container} (expected to use a vertical BoxLayout) with a read-only
     *  day-by-day breakdown. Days with no sessions are omitted entirely. */
    static void rebuild(JPanel container, Function<DayOfWeek, List<ClassSession>> sessionsByDay,
                         String[] columns, RowMapper mapper) {
        rebuildSelectable(container, sessionsByDay, columns, mapper, null);
    }

    /** Same as {@link #rebuild}, but each day's table is selectable and mutually exclusive with
     *  the others: selecting a row anywhere calls {@code onSelect} with that session's id, and
     *  clears the selection in every other day's table. Returns the tables created, in case the
     *  caller needs to clear them later. Pass {@code onSelect} as null for a read-only view. */
    static List<JTable> rebuildSelectable(JPanel container, Function<DayOfWeek, List<ClassSession>> sessionsByDay,
                                           String[] columns, RowMapper mapper, Consumer<String> onSelect) {
        container.removeAll();
        List<JTable> tables = new ArrayList<>();
        boolean any = false;

        for (DayOfWeek day : WEEK_ORDER) {
            List<ClassSession> list = sessionsByDay.apply(day);
            if (list == null || list.isEmpty()) continue;
            any = true;

            container.add(dayHeader(day));

            DefaultTableModel model = new DefaultTableModel(columns, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };
            for (ClassSession s : list) model.addRow(mapper.map(s));
            JTable table = new JTable(model);
            int rows = Math.max(1, Math.min(list.size(), 8));
            table.setPreferredScrollableViewportSize(new Dimension(760, rows * table.getRowHeight() + 4));
            tables.add(table);

            JScrollPane scroll = new JScrollPane(table);
            scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            scroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
            container.add(scroll);
        }

        if (onSelect != null) {
            for (JTable t : tables) {
                t.getSelectionModel().addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting()) return;
                    int row = t.getSelectedRow();
                    if (row < 0) return;
                    for (JTable other : tables) if (other != t) other.clearSelection();
                    onSelect.accept((String) t.getModel().getValueAt(row, 0));
                });
            }
        }

        if (!any) {
            JLabel empty = new JLabel("Nothing to show.");
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            container.add(empty);
        }
        container.revalidate();
        container.repaint();
        return tables;
    }

    private static JLabel dayHeader(DayOfWeek day) {
        String name = day.toString();
        String label = name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
        JLabel header = new JLabel(label);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        header.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        return header;
    }
}

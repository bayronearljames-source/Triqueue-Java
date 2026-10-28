package triqueue.ui;

import triqueue.core.AppData;
import triqueue.model.Driver;
import triqueue.model.Trip;
import triqueue.storage.FileManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class TripPanel extends JPanel {
    private final JComboBox<String> cbDriver = new JComboBox<>();
    private final JComboBox<String> cbRoute = new JComboBox<>();
    private final JPanel passengerCountsPanel = new JPanel(new GridBagLayout());
    private final Map<String, JSpinner> passengerSpinners = new LinkedHashMap<>();
    private final JLabel lblVehicleType = new JLabel("Select a driver");
    private final JLabel lblBase = new JLabel("0.00");
    private final JLabel lblDiscount = new JLabel("0.00");
    private final JLabel lblFinal = new JLabel("0.00");
    private final DefaultTableModel model = GuiUtil.readOnlyModel("Trip ID", "Driver", "Vehicle", "Route", "Passengers",
            "Total", "Base", "Discount", "Fare", "Time", "Status");
    private final JTable table = GuiUtil.makeTable(model);
    private final JButton bLog = GuiUtil.button("Log Trip");
    private final JButton bCancelEdit = GuiUtil.button("Cancel Edit");
    private String editingTripId;
    private String editingTimestamp;
    private boolean refreshing = false;

    public TripPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] columnWidths = {65, 80, 135, 150, 220, 50, 65, 70, 65, 130, 75};
        for (int column = 0; column < columnWidths.length; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(columnWidths[column]);
        }
        table.getColumnModel().getColumn(4).setCellRenderer(new PassengerCountsRenderer());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Log a Trip"));
        GuiUtil.addRow(form, 0, "Driver:", cbDriver);
        GuiUtil.addRow(form, 1, "Vehicle type:", lblVehicleType);
        GuiUtil.addRow(form, 2, "Route:", cbRoute);
        GuiUtil.addRow(form, 3, "Passenger counts:", passengerCountsPanel);
        lblFinal.setFont(lblFinal.getFont().deriveFont(Font.BOLD, 18f));
        lblFinal.setForeground(GuiUtil.NAVY);
        GuiUtil.addRow(form, 4, "Base fare:", lblBase);
        GuiUtil.addRow(form, 5, "Discount:", lblDiscount);
        GuiUtil.addRow(form, 6, "FARE TO COLLECT:", lblFinal);
        JPanel inner = new JPanel(new BorderLayout());
        inner.add(form, BorderLayout.CENTER);
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bp.add(bLog);
        inner.add(bp, BorderLayout.SOUTH);
        JPanel formWrap = new JPanel(new BorderLayout());
        formWrap.add(inner, BorderLayout.NORTH);
        formWrap.setPreferredSize(new Dimension(360, 0));
        add(formWrap, BorderLayout.WEST);

        JPanel recordsPanel = new JPanel(new BorderLayout());
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createTitledBorder("Trip Records"));
        recordsPanel.add(sp, BorderLayout.CENTER);
        JButton bEdit = GuiUtil.button("Edit Selected Trip");
        JButton bArchive = GuiUtil.button("Archive Selected Trip");
        JButton bViewArchive = GuiUtil.button("View Archive / Restore");
        bCancelEdit.setEnabled(false);
        JPanel recordActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        recordActions.add(bEdit);
        recordActions.add(bArchive);
        recordActions.add(bViewArchive);
        recordActions.add(bCancelEdit);
        recordsPanel.add(recordActions, BorderLayout.SOUTH);
        add(recordsPanel, BorderLayout.CENTER);

        java.awt.event.ActionListener preview = e -> { if (!refreshing) updatePreview(); };
        cbDriver.addActionListener(preview);
        cbRoute.addActionListener(preview);
        bLog.addActionListener(e -> logTrip());
        bEdit.addActionListener(e -> beginEditTrip());
        bArchive.addActionListener(e -> archiveSelectedTrip());
        bViewArchive.addActionListener(e -> showTripArchive());
        bCancelEdit.addActionListener(e -> cancelEditTrip());
        reloadData();
    }

    public final void reloadData() {
        refreshing = true;
        Object selectedDriver = cbDriver.getSelectedItem();
        Object selectedRoute = cbRoute.getSelectedItem();
        Map<String, Integer> previousCounts = new LinkedHashMap<>();
        for (Map.Entry<String, JSpinner> entry : passengerSpinners.entrySet()) {
            previousCounts.put(entry.getKey(), (Integer) entry.getValue().getValue());
        }

        cbDriver.removeAllItems();
        for (Driver driver : AppData.drivers) cbDriver.addItem(AppData.label(driver));
        cbRoute.removeAllItems();
        for (String[] route : FileManager.getRoutes()) cbRoute.addItem(route[0]);
        Driver loading = AppData.queue.getLoadingDriver();
        if (loading != null) {
            cbDriver.setSelectedItem(AppData.label(loading));
            cbDriver.setEnabled(false);
        } else {
            if (selectedDriver != null) cbDriver.setSelectedItem(selectedDriver);
            cbDriver.setEnabled(true);
        }
        if (selectedRoute != null) cbRoute.setSelectedItem(selectedRoute);

        passengerCountsPanel.removeAll();
        passengerSpinners.clear();
        int row = 0;
        for (String[] discount : FileManager.getDiscounts()) {
            addPassengerCountInput(discount[0], Math.min(previousCounts.getOrDefault(discount[0], 0), 10), row++);
        }
        if (row == 0) {
            passengerCountsPanel.add(new JLabel("Configure passenger types in Fares & Routes."));
        }
        passengerCountsPanel.revalidate();
        passengerCountsPanel.repaint();
        refreshing = false;

        model.setRowCount(0);
        Map<String, String> statuses = FileManager.getTripStatuses();
        for (String line : FileManager.getEffectiveTrips().values()) {
            String[] p = line.split("\\|");
            if (p.length >= 9) {
                String status = statuses.getOrDefault(p[0], "ACTIVE");
                if ("ARCHIVED".equals(status)) continue;
                String vehicleType = p.length >= 10 ? p[9] : getCurrentVehicleLabel(p[1]);
                model.addRow(new Object[]{p[0], p[1], vehicleType, p[2], p[3],
                        p[4], p[5], p[6], p[7], p[8], status});
            }
        }
        adjustPassengerRowHeights();
        updatePreview();
    }

    private String getCurrentVehicleLabel(String driverId) {
        Driver driver = AppData.findDriver(driverId);
        return driver == null ? "Not recorded" : driver.getVehicle().getVehicleType() + " (current)";
    }

    private void adjustPassengerRowHeights() {
        int column = 4;
        int width = table.getColumnModel().getColumn(column).getWidth();
        for (int row = 0; row < model.getRowCount(); row++) {
            Component renderer = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
            renderer.setSize(new Dimension(width, Short.MAX_VALUE));
            table.setRowHeight(row, Math.max(table.getRowHeight(), renderer.getPreferredSize().height));
        }
    }

    private static final class PassengerCountsRenderer extends JTextArea implements TableCellRenderer {
        private PassengerCountsRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean hasFocus, int row, int column) {
            String text = value == null ? "" : value.toString().replace(";", ", ").replace(":", ": ");
            setText(text);
            setToolTipText(text);
            setFont(table.getFont());
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            setForeground(selected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }
    }

    private void beginEditTrip() {
        int row = table.getSelectedRow();
        if (row < 0) {
            GuiUtil.error(this, "Select a trip record first.");
            return;
        }
        String status = (String) model.getValueAt(row, 10);
        if ("VOIDED".equals(status) || "ARCHIVED".equals(status)) {
            GuiUtil.error(this, "A voided or archived trip cannot be edited.");
            return;
        }
        String[] p = new String[9];
        p[0] = String.valueOf(model.getValueAt(row, 0));
        p[1] = String.valueOf(model.getValueAt(row, 1));
        p[2] = String.valueOf(model.getValueAt(row, 3));
        p[3] = String.valueOf(model.getValueAt(row, 4));
        p[4] = String.valueOf(model.getValueAt(row, 5));
        p[5] = String.valueOf(model.getValueAt(row, 6));
        p[6] = String.valueOf(model.getValueAt(row, 7));
        p[7] = String.valueOf(model.getValueAt(row, 8));
        p[8] = String.valueOf(model.getValueAt(row, 9));

        Map<String, Integer> counts = parsePassengerCounts(p[3], p[4]);
        if (counts.isEmpty()) {
            GuiUtil.error(this, "The saved passenger counts could not be read, so this trip cannot be edited.");
            return;
        }
        for (String type : counts.keySet()) {
            if (!passengerSpinners.containsKey(type)) {
                GuiUtil.error(this, "This trip uses a passenger type that is no longer configured: " + type);
                return;
            }
        }
        Driver driver = AppData.findDriver(p[1]);
        if (driver == null) {
            GuiUtil.error(this, "The driver for this trip is no longer registered.");
            return;
        }

        editingTripId = p[0];
        editingTimestamp = p[8];
        cbDriver.setSelectedItem(AppData.label(driver));
        cbRoute.setSelectedItem(p[2]);
        for (Map.Entry<String, JSpinner> entry : passengerSpinners.entrySet()) {
            entry.getValue().setValue(counts.getOrDefault(entry.getKey(), 0));
        }
        bLog.setText("Save Trip Changes");
        bCancelEdit.setEnabled(true);
        updatePreview();
    }

    private Map<String, Integer> parsePassengerCounts(String mix, String legacyCount) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (!mix.contains(":")) {
            try {
                counts.put(mix, Integer.parseInt(legacyCount));
            } catch (NumberFormatException ignored) { }
            return counts;
        }
        for (String item : mix.split(";")) {
            int separator = item.lastIndexOf(':');
            if (separator <= 0) continue;
            try {
                int count = Integer.parseInt(item.substring(separator + 1));
                if (count > 0) counts.put(item.substring(0, separator), count);
            } catch (NumberFormatException ignored) { }
        }
        return counts;
    }

    private void cancelEditTrip() {
        editingTripId = null;
        editingTimestamp = null;
        bLog.setText("Log Trip");
        bCancelEdit.setEnabled(false);
        updatePreview();
    }

    private void archiveSelectedTrip() {
        if (editingTripId != null) {
            GuiUtil.error(this, "Cancel the current edit before voiding a trip.");
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0) {
            GuiUtil.error(this, "Select a trip record first.");
            return;
        }
        String status = String.valueOf(model.getValueAt(row, 10));
        if ("VOIDED".equals(status) || "ARCHIVED".equals(status)) {
            GuiUtil.error(this, "That trip is voided or already archived.");
            return;
        }

        String tripId = (String) model.getValueAt(row, 0);
        String reason = JOptionPane.showInputDialog(this, "Reason for archiving trip " + tripId + ":",
                "Archive Trip", JOptionPane.QUESTION_MESSAGE);
        if (reason == null) return;
        reason = reason.trim();
        if (reason.isEmpty()) {
            GuiUtil.error(this, "Enter a reason so the archive action is recorded in the audit log.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Archive trip " + tripId + "? It will be hidden from active trip records and earnings reports, and can be restored later.",
                "Confirm Trip Archive", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        if (!FileManager.archiveTrip(tripId, reason)) {
            GuiUtil.error(this, "Could not archive the trip. It may already be voided or archived, or the data file could not be written.");
            return;
        }
        reloadData();
        GuiUtil.info(this, "Trip " + tripId + " was archived. You can restore it from View Archive.");
    }

    private void showTripArchive() {
        DefaultTableModel archiveModel = GuiUtil.readOnlyModel("Trip ID", "Driver", "Route", "Passengers", "Total", "Fare", "Time");
        Map<String, String> statuses = FileManager.getTripStatuses();
        for (String line : FileManager.getEffectiveTrips().values()) {
            String[] p = line.split("\\|");
            if (p.length >= 9 && "ARCHIVED".equals(statuses.get(p[0])))
                archiveModel.addRow(new Object[]{p[0], p[1], p[2], p[3], p[4], p[7], p[8]});
        }
        JTable archiveTable = GuiUtil.makeTable(archiveModel);
        JScrollPane scroll = new JScrollPane(archiveTable);
        scroll.setPreferredSize(new Dimension(780, 300));
        JButton restore = GuiUtil.button("Restore Selected Trip");
        JButton close = GuiUtil.button("Close");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(restore);
        buttons.add(close);
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Archived Trips", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        restore.addActionListener(e -> {
            int selected = archiveTable.getSelectedRow();
            if (selected < 0) {
                GuiUtil.error(dialog, "Select an archived trip first.");
                return;
            }
            String tripId = String.valueOf(archiveModel.getValueAt(selected, 0));
            if (!FileManager.restoreTrip(tripId)) {
                GuiUtil.error(dialog, "Could not restore this trip.");
                return;
            }
            archiveModel.removeRow(selected);
            reloadData();
            GuiUtil.info(dialog, "Trip " + tripId + " restored to active trip records.");
        });
        close.addActionListener(e -> dialog.dispose());
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void addPassengerCountInput(String type, int initialCount, int row) {
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = row;
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(2, 2, 2, 8);
        passengerCountsPanel.add(new JLabel(type + ":"), label);

        JSpinner count = new JSpinner(new SpinnerNumberModel(Math.min(initialCount, 10), 0, 10, 1));
        count.addChangeListener(e -> { if (!refreshing) updatePreview(); });
        GridBagConstraints input = new GridBagConstraints();
        input.gridx = 1;
        input.gridy = row;
        input.weightx = 1;
        input.fill = GridBagConstraints.HORIZONTAL;
        input.insets = new Insets(2, 2, 2, 2);
        passengerCountsPanel.add(count, input);
        passengerSpinners.put(type, count);
    }

    private Trip buildTrip(String id, String time) {
        Driver driver = AppData.fromLabel((String) cbDriver.getSelectedItem());
        String route = (String) cbRoute.getSelectedItem();
        if (driver == null || route == null || !FileManager.routeExists(route)) return null;

        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, JSpinner> entry : passengerSpinners.entrySet()) {
            int count = (Integer) entry.getValue().getValue();
            if (count > 0) counts.put(entry.getKey(), count);
            total += count;
        }
        if (total < 1 || total > getPassengerLimit(driver)) return null;
        return new Trip(id, driver, route, counts, time);
    }

    private int getPassengerLimit(Driver driver) {
        return driver != null && "HabalHabal".equalsIgnoreCase(driver.getVehicle().getVehicleType()) ? 1 : 10;
    }

    private int getPassengerTotal() {
        int total = 0;
        for (JSpinner spinner : passengerSpinners.values()) {
            total += (Integer) spinner.getValue();
        }
        return total;
    }

    private void updatePreview() {
        Driver selectedDriver = AppData.fromLabel((String) cbDriver.getSelectedItem());
        lblVehicleType.setText(selectedDriver == null ? "-" : selectedDriver.getVehicle().getVehicleType());
        int passengerLimit = getPassengerLimit(selectedDriver);
        for (JSpinner spinner : passengerSpinners.values()) {
            SpinnerNumberModel spinnerModel = (SpinnerNumberModel) spinner.getModel();
            spinnerModel.setMaximum(passengerLimit);
            if ((Integer) spinner.getValue() > passengerLimit) spinner.setValue(passengerLimit);
        }
        Trip trip = buildTrip("PREVIEW", "");
        if (trip == null) {
            lblBase.setText("0.00");
            lblDiscount.setText("0.00");
            lblFinal.setText("0.00");
            return;
        }
        trip.calculateFare();
        lblBase.setText(String.format("%.2f", trip.getFareDetails().getBaseFare()));
        lblDiscount.setText(String.format("%.2f", trip.getFareDetails().getDiscountApplied()));
        lblFinal.setText(String.format("%.2f", trip.getFareDetails().getFinalFare()));
    }

    private void resetPassengerCounts() {
        for (JSpinner spinner : passengerSpinners.values()) {
            spinner.setValue(0);
        }
    }

    private void logTrip() {
        if (editingTripId != null) {
            saveTripCorrection();
            return;
        }
        String id = FileManager.nextId(FileManager.TRIPS, "T");
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        Trip trip = buildTrip(id, time);
        if (trip == null) {
            Driver driver = AppData.fromLabel((String) cbDriver.getSelectedItem());
            int limit = getPassengerLimit(driver);
            if (driver != null && getPassengerTotal() > limit) {
                String vehicleType = driver.getVehicle().getVehicleType();
                GuiUtil.error(this, vehicleType + " can carry at most " + limit
                        + (limit == 1 ? " passenger" : " passengers") + " per trip.");
                return;
            }
            GuiUtil.error(this, "Choose a driver and route, then enter 1 to " + limit
                    + (limit == 1 ? " passenger" : " passengers") + " in total.");
            return;
        }
        double fare = trip.calculateFare();
        if (!FileManager.appendFile(FileManager.TRIPS, trip.toFileString())) {
            GuiUtil.error(this, "The trip could not be saved. Check that the data folder is writable.");
            return;
        }
        resetPassengerCounts();
        reloadData();
        Driver loading = AppData.queue.getLoadingDriver();
        if (loading == null || !loading.getId().equals(trip.getDriverId())) {
            GuiUtil.info(this, String.format("Trip %s saved.%nFare to collect: %.2f", id, fare));
            return;
        }

        int departed = JOptionPane.showConfirmDialog(this,
                String.format("Trip %s was saved for %s.%nFare to collect: %.2f%n%nHas the driver departed? Confirming will mark the driver DEPARTED and call the next driver.",
                        id, loading.getName(), fare),
                "Confirm Departure", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (departed != JOptionPane.YES_OPTION) {
            GuiUtil.info(this, "Trip saved. " + loading.getName()
                    + " remains NOW LOADING. You can confirm departure from the Terminal Queue tab later.");
            return;
        }
        if (!AppData.queue.completeLoadingDriver("Trip " + id + " departure confirmed")) {
            GuiUtil.error(this, "The trip was saved, but departure status could not be saved. Confirm departure from the Terminal Queue tab.");
            return;
        }
        Driver next = AppData.queue.getLoadingDriver();
        reloadData();
        GuiUtil.info(this, next == null
                ? loading.getName() + " is marked DEPARTED. No drivers are waiting."
                : loading.getName() + " is marked DEPARTED. " + next.getName() + " is now loading.");
    }

    private void saveTripCorrection() {
        Trip replacement = buildTrip(editingTripId, editingTimestamp);
        if (replacement == null) {
            GuiUtil.error(this, "Choose a driver and route, then enter 1 to 20 passengers in total.");
            return;
        }
        String reason = JOptionPane.showInputDialog(this, "Reason for correcting trip " + editingTripId + ":",
                "Correct Trip", JOptionPane.QUESTION_MESSAGE);
        if (reason == null) return;
        reason = reason.trim();
        if (reason.isEmpty()) {
            GuiUtil.error(this, "Enter a reason so the correction is recorded in the audit log.");
            return;
        }
        replacement.calculateFare();
        String correctedId = editingTripId;
        if (!FileManager.correctTrip(correctedId, reason, replacement.toFileString())) {
            GuiUtil.error(this, "Could not save the trip correction. It may have been voided or the audit file could not be written.");
            return;
        }
        cancelEditTrip();
        reloadData();
        GuiUtil.info(this, "Trip " + correctedId + " was corrected and recorded in the audit log.");
    }
}

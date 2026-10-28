package triqueue.ui;

import triqueue.core.AppData;
import triqueue.model.Driver;
import triqueue.storage.FileManager;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;

public class EarningsPanel extends JPanel {
    private final JComboBox<String> cbDriver = new JComboBox<>();
    private final JTextField tfAmount = new JTextField(8);
    private final JComboBox<String> cbReportDate = new JComboBox<>();
    private final JComboBox<String> cbPeriod = new JComboBox<>(new String[]{"Selected date", "Last 3 days", "Last 7 days", "Custom range"});
    private final JTextField tfStartDate = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField tfEndDate = new JTextField(LocalDate.now().toString(), 10);
    private final DefaultTableModel model = GuiUtil.readOnlyModel("ID", "Driver", "Trips", "Fares", "Boundary", "Net Earnings");
    private final JLabel lblTotal = new JLabel(" ");
    private LocalDate loadedReportStart;
    private LocalDate loadedReportEnd;

    public EarningsPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel pay = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pay.setBorder(BorderFactory.createTitledBorder("Boundary Payment (today)"));
        JButton bPay = GuiUtil.button("Record Boundary");
        pay.add(new JLabel("Driver:")); pay.add(cbDriver);
        pay.add(new JLabel("Amount:")); pay.add(tfAmount);
        pay.add(bPay);

        JPanel rep = new JPanel(new BorderLayout(6, 4));
        rep.setBorder(BorderFactory.createTitledBorder("Daily Earnings Report"));
        JButton bGen = GuiUtil.button("Generate Report");
        JButton bExport = GuiUtil.button("Export CSV");
        JPanel reportInputs = new JPanel(new FlowLayout(FlowLayout.LEFT));
        reportInputs.add(new JLabel("Ending date (records only):")); reportInputs.add(cbReportDate);
        reportInputs.add(new JLabel("Period:")); reportInputs.add(cbPeriod);
        reportInputs.add(new JLabel("From (yyyy-MM-dd):")); reportInputs.add(tfStartDate);
        reportInputs.add(new JLabel("To (yyyy-MM-dd):")); reportInputs.add(tfEndDate);
        JPanel reportButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        reportButtons.add(bGen); reportButtons.add(bExport);
        rep.add(reportInputs, BorderLayout.CENTER);
        rep.add(reportButtons, BorderLayout.SOUTH);

        JPanel top = new JPanel(new GridLayout(2, 1, 0, 6));
        top.add(pay); top.add(rep);
        add(top, BorderLayout.NORTH);

        add(new JScrollPane(GuiUtil.makeTable(model)), BorderLayout.CENTER);
        lblTotal.setFont(lblTotal.getFont().deriveFont(Font.BOLD, 14f));
        lblTotal.setForeground(GuiUtil.NAVY);
        add(lblTotal, BorderLayout.SOUTH);

        bPay.addActionListener(e -> recordBoundary());
        bGen.addActionListener(e -> generate());
        bExport.addActionListener(e -> exportCsv());
        cbReportDate.addActionListener(e -> generate());
        cbPeriod.addActionListener(e -> periodChanged());
        tfStartDate.setEnabled(false);
        tfEndDate.setEnabled(false);
        reloadData();
    }

    public final void reloadData() {
        Object sel = cbDriver.getSelectedItem();
        cbDriver.removeAllItems();
        for (Driver d : AppData.drivers) cbDriver.addItem(AppData.label(d));
        if (sel != null) cbDriver.setSelectedItem(sel);
        Object selectedDate = cbReportDate.getSelectedItem();
        List<String> dates = FileManager.getReportDates();
        cbReportDate.removeAllItems();
        for (String date : dates) cbReportDate.addItem(date);
        if (selectedDate != null && dates.contains(selectedDate.toString())) cbReportDate.setSelectedItem(selectedDate);
        cbReportDate.setEnabled(!dates.isEmpty() && cbPeriod.getSelectedIndex() != 3);
        generate();
    }

    private void recordBoundary() {
        Driver d = AppData.fromLabel((String) cbDriver.getSelectedItem());
        if (d == null) { GuiUtil.error(this, "Choose a driver."); return; }
        double amount;
        try {
            amount = Double.parseDouble(tfAmount.getText().trim());
        } catch (NumberFormatException ex) {
            GuiUtil.error(this, "Amount must be a number.");
            return;
        }
        if (!Double.isFinite(amount) || amount < 0) {
            GuiUtil.error(this, "Boundary amount must be a finite, non-negative number.");
            return;
        }
        if (!d.payBoundary(amount)) return;
        String id = FileManager.nextId(FileManager.BOUNDARY, "B");
        FileManager.appendFile(FileManager.BOUNDARY,
                id + "|" + d.getId() + "|" + LocalDate.now() + "|" + String.format("%.2f", amount));
        tfAmount.setText("");
        reloadData();
        GuiUtil.info(this, "Boundary of " + String.format("%.2f", amount) + " recorded for " + d.getName() + ".");
    }

    private void generate() {
        LocalDate startDate;
        LocalDate endDate;
        try {
            if (cbPeriod.getSelectedIndex() == 3) {
                startDate = LocalDate.parse(tfStartDate.getText().trim());
                endDate = LocalDate.parse(tfEndDate.getText().trim());
                if (endDate.isBefore(startDate)) throw new DateTimeParseException("End date is before start date", tfEndDate.getText(), 0);
            } else {
                Object selected = cbReportDate.getSelectedItem();
                if (selected == null) {
                    model.setRowCount(0);
                    lblTotal.setText("No trip or boundary records are available. Choose Custom range to report another date range.");
                    loadedReportStart = null;
                    loadedReportEnd = null;
                    return;
                }
                endDate = LocalDate.parse(selected.toString());
                int days = cbPeriod.getSelectedIndex() == 1 ? 3 : cbPeriod.getSelectedIndex() == 2 ? 7 : 1;
                startDate = endDate.minusDays(days - 1L);
            }
        } catch (DateTimeParseException ex) {
            model.setRowCount(0);
            lblTotal.setText("Enter valid dates in yyyy-MM-dd format, with the end date on or after the start date.");
            loadedReportStart = null;
            loadedReportEnd = null;
            return;
        }
        model.setRowCount(0);
        double fares = 0, boundary = 0;
        int trips = 0;
        for (Object[] r : AppData.dispatcher.buildReportRows(AppData.drivers, startDate, endDate)) {
            fares += (Double) r[3];
            boundary += (Double) r[4];
            trips += (Integer) r[2];
            model.addRow(new Object[]{r[0], r[1], r[2],
                    String.format("%.2f", r[3]), String.format("%.2f", r[4]), String.format("%.2f", r[5])});
        }
        lblTotal.setText(String.format("TOTAL (%s to %s)  -  Trips: %d   Fares: %.2f   Boundary: %.2f   Net: %.2f",
                startDate, endDate, trips, fares, boundary, fares - boundary));
        loadedReportStart = startDate;
        loadedReportEnd = endDate;
    }

    private void periodChanged() {
        boolean custom = cbPeriod.getSelectedIndex() == 3;
        tfStartDate.setEnabled(custom);
        tfEndDate.setEnabled(custom);
        cbReportDate.setEnabled(!custom && cbReportDate.getItemCount() > 0);
        if (custom) {
            Object selected = cbReportDate.getSelectedItem();
            String end = selected == null ? LocalDate.now().toString() : selected.toString();
            tfStartDate.setText(end);
            tfEndDate.setText(end);
        }
        generate();
    }

    private void exportCsv() {
        generate();
        if (loadedReportStart == null || loadedReportEnd == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Earnings Report");
        chooser.setSelectedFile(new java.io.File("Earnings-" + loadedReportStart + "-to-" + loadedReportEnd + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path output = chooser.getSelectedFile().toPath();
        if (Files.exists(output) && JOptionPane.showConfirmDialog(this,
                "Replace the selected file?", "Confirm Export", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        List<String> lines = new ArrayList<>();
        StringBuilder header = new StringBuilder();
        for (int col = 0; col < model.getColumnCount(); col++) {
            if (col > 0) header.append(',');
            header.append(csvValue(model.getColumnName(col)));
        }
        lines.add(header.toString());

        double totalFares = 0, totalBoundary = 0, totalNet = 0;
        int totalTrips = 0;
        for (int row = 0; row < model.getRowCount(); row++) {
            StringBuilder csvRow = new StringBuilder();
            for (int col = 0; col < model.getColumnCount(); col++) {
                if (col > 0) csvRow.append(',');
                csvRow.append(csvValue(model.getValueAt(row, col)));
            }
            lines.add(csvRow.toString());
            totalTrips += Integer.parseInt(model.getValueAt(row, 2).toString());
            totalFares += Double.parseDouble(model.getValueAt(row, 3).toString());
            totalBoundary += Double.parseDouble(model.getValueAt(row, 4).toString());
            totalNet += Double.parseDouble(model.getValueAt(row, 5).toString());
        }
        lines.add("TOTAL,," + totalTrips + "," + String.format("%.2f", totalFares) + ","
                + String.format("%.2f", totalBoundary) + "," + String.format("%.2f", totalNet));
        try {
            Files.write(output, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            GuiUtil.info(this, "Report exported to:\n" + output.toAbsolutePath().normalize());
        } catch (IOException ex) {
            GuiUtil.error(this, "Could not export report: " + ex.getMessage());
        }
    }

    private String csvValue(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r"))
            return "\"" + text.replace("\"", "\"\"") + "\"";
        return text;
    }
}

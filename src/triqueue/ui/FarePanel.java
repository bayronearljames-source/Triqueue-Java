package triqueue.ui;

import triqueue.storage.FileManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class FarePanel extends JPanel {
    private DefaultTableModel routeModel = GuiUtil.readOnlyModel("Route", "Tricycle/pax", "HabalHabal/trip");
    private DefaultTableModel discModel = GuiUtil.readOnlyModel("Passenger Type", "Discount %");
    private JTable routeTable = GuiUtil.makeTable(routeModel);
    private JTable discTable = GuiUtil.makeTable(discModel);
    private JTextField tfRoute = new JTextField(14), tfTri = new JTextField(6), tfHab = new JTextField(6);
    private JTextField tfType = new JTextField(14), tfPct = new JTextField(6);

    public FarePanel() {
        setLayout(new GridLayout(1, 2, 12, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ----- Routes -----
        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setBorder(BorderFactory.createTitledBorder("Route Fares"));
        left.add(new JScrollPane(routeTable), BorderLayout.CENTER);
        JPanel lf = new JPanel(new GridBagLayout());
        GuiUtil.addRow(lf, 0, "Route:", tfRoute);
        GuiUtil.addRow(lf, 1, "Tricycle fare:", tfTri);
        GuiUtil.addRow(lf, 2, "HabalHabal fare:", tfHab);
        JButton bRoute = GuiUtil.button("Add / Update Route");
        JButton bDelRoute = GuiUtil.button("Delete Route");
        JPanel lb = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lb.add(bRoute);
        lb.add(bDelRoute);
        JPanel lsouth = new JPanel(new BorderLayout());
        lsouth.add(lf, BorderLayout.CENTER);
        lsouth.add(lb, BorderLayout.SOUTH);
        left.add(lsouth, BorderLayout.SOUTH);

        // ----- Discounts -----
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.setBorder(BorderFactory.createTitledBorder("Passenger Discounts"));
        right.add(new JScrollPane(discTable), BorderLayout.CENTER);
        JPanel rf = new JPanel(new GridBagLayout());
        GuiUtil.addRow(rf, 0, "Passenger type:", tfType);
        GuiUtil.addRow(rf, 1, "Discount %:", tfPct);
        JButton bDisc = GuiUtil.button("Add / Update Discount");
        JButton bDelDisc = GuiUtil.button("Delete Discount");
        JPanel rb = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rb.add(bDisc);
        rb.add(bDelDisc);
        JPanel rsouth = new JPanel(new BorderLayout());
        rsouth.add(rf, BorderLayout.CENTER);
        rsouth.add(rb, BorderLayout.SOUTH);
        right.add(rsouth, BorderLayout.SOUTH);

        routeTable.getColumnModel().getColumn(0).setPreferredWidth(190);
        add(left);
        add(right);

        routeTable.getSelectionModel().addListSelectionListener(e -> {
            int r = routeTable.getSelectedRow();
            if (r >= 0 && !e.getValueIsAdjusting()) {
                tfRoute.setText((String) routeModel.getValueAt(r, 0));
                tfTri.setText((String) routeModel.getValueAt(r, 1));
                tfHab.setText((String) routeModel.getValueAt(r, 2));
            }
        });
        discTable.getSelectionModel().addListSelectionListener(e -> {
            int r = discTable.getSelectedRow();
            if (r >= 0 && !e.getValueIsAdjusting()) {
                tfType.setText((String) discModel.getValueAt(r, 0));
                tfPct.setText((String) discModel.getValueAt(r, 1));
            }
        });

        bRoute.addActionListener(e -> {
            String route = tfRoute.getText().trim();
            try {
                double tri = Double.parseDouble(tfTri.getText().trim());
                double hab = Double.parseDouble(tfHab.getText().trim());
                if (route.isEmpty() || !Double.isFinite(tri) || !Double.isFinite(hab) || tri < 0 || hab < 0) {
                    GuiUtil.error(this, "Enter a route name and non-negative fares.");
                    return;
                }
                FileManager.saveRoute(route, tri, hab);
                reloadData();
            } catch (NumberFormatException ex) {
                GuiUtil.error(this, "Fares must be numbers.");
            }
        });

        bDelRoute.addActionListener(e -> {
            String route = tfRoute.getText().trim();
            if (route.isEmpty()) {
                GuiUtil.error(this, "Select or type a route name to delete.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete route \"" + route + "\"? This cannot be undone.",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            if (FileManager.deleteRoute(route)) {
                tfRoute.setText(""); tfTri.setText(""); tfHab.setText("");
                reloadData();
                GuiUtil.info(this, "Route \"" + route + "\" deleted.");
            } else {
                GuiUtil.error(this, "Route \"" + route + "\" was not found.");
            }
        });

        bDisc.addActionListener(e -> {
            String type = tfType.getText().trim();
            try {
                double pct = Double.parseDouble(tfPct.getText().trim());
                if (type.isEmpty() || !Double.isFinite(pct) || pct < 0 || pct > 100) {
                    GuiUtil.error(this, "Enter a type and a percent from 0 to 100.");
                    return;
                }
                FileManager.saveDiscount(type, pct);
                reloadData();
            } catch (NumberFormatException ex) {
                GuiUtil.error(this, "Discount must be a number.");
            }
        });

        bDelDisc.addActionListener(e -> {
            String type = tfType.getText().trim();
            if (type.isEmpty()) {
                GuiUtil.error(this, "Select or type a passenger type to delete.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete discount for \"" + type + "\"? This cannot be undone.",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            if (FileManager.deleteDiscount(type)) {
                tfType.setText(""); tfPct.setText("");
                reloadData();
                GuiUtil.info(this, "Discount for \"" + type + "\" deleted.");
            } else {
                GuiUtil.error(this, "Discount type \"" + type + "\" was not found.");
            }
        });

        reloadData();
    }

    public final void reloadData() {
        routeModel.setRowCount(0);
        for (String[] r : FileManager.getRoutes()) routeModel.addRow(r);
        discModel.setRowCount(0);
        for (String[] d : FileManager.getDiscounts()) discModel.addRow(d);
    }
}

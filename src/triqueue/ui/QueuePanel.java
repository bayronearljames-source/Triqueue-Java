package triqueue.ui;

import triqueue.core.AppData;
import triqueue.model.Driver;
import triqueue.storage.FileManager;

import javax.swing.*;
import java.awt.*;

public class QueuePanel extends JPanel {
    private final DefaultListModel<String> tricycleListModel = new DefaultListModel<>();
    private final DefaultListModel<String> habalListModel = new DefaultListModel<>();
    private final JList<String> tricycleList = new JList<>(tricycleListModel);
    private final JList<String> habalList = new JList<>(habalListModel);
    private JComboBox<String> cbDriver = new JComboBox<>();
    private JLabel lblNow = new JLabel("Now loading: -", SwingConstants.CENTER);

    public QueuePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        lblNow.setFont(lblNow.getFont().deriveFont(Font.BOLD, 18f));
        lblNow.setOpaque(true);
        lblNow.setBackground(GuiUtil.LIGHT);
        lblNow.setForeground(GuiUtil.NAVY);
        lblNow.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(lblNow, BorderLayout.NORTH);

        tricycleList.setFont(tricycleList.getFont().deriveFont(14f));
        habalList.setFont(habalList.getFont().deriveFont(14f));
        JPanel lanes = new JPanel(new GridLayout(1, 2, 10, 0));
        JScrollPane tricycleScroll = new JScrollPane(tricycleList);
        tricycleScroll.setBorder(BorderFactory.createTitledBorder("Tricycle Queue"));
        JScrollPane habalScroll = new JScrollPane(habalList);
        habalScroll.setBorder(BorderFactory.createTitledBorder("Habal-Habal Queue"));
        lanes.add(tricycleScroll);
        lanes.add(habalScroll);
        add(lanes, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton bAdd = GuiUtil.button("Add to Queue");
        JButton bNext = GuiUtil.button("Call Next Driver");
        JButton bSkip = GuiUtil.button("Skip Absent Driver");
        JButton bDeparted = GuiUtil.button("Mark Departed & Call Next");
        JButton bHistory = GuiUtil.button("View Queue History");
        bottom.add(new JLabel("Driver:"));
        bottom.add(cbDriver);
        bottom.add(bAdd); bottom.add(bNext); bottom.add(bSkip); bottom.add(bDeparted); bottom.add(bHistory);
        add(bottom, BorderLayout.SOUTH);

        bAdd.addActionListener(e -> {
            Driver d = AppData.fromLabel((String) cbDriver.getSelectedItem());
            if (d == null) { GuiUtil.error(this, "Register a driver first."); return; }
            if (AppData.queue.contains(d)) { GuiUtil.error(this, d.getName() + " is already in the queue."); return; }
            AppData.dispatcher.addDriverToQueue(AppData.queue, d);
            reloadData();
        });
        bNext.addActionListener(e -> {
            Driver loading = AppData.queue.getLoadingDriver();
            if (loading != null) {
                GuiUtil.error(this, loading.getName() + " is already NOW LOADING. Mark the driver departed before calling the next one.");
                return;
            }
            Driver d = AppData.queue.getNextDriver();
            if (d == null) { GuiUtil.error(this, "No drivers in the queue, or the queue could not be saved."); return; }
            reloadData();
        });
        bSkip.addActionListener(e -> {
            Driver d = AppData.queue.skipDriver();
            if (d == null) { GuiUtil.error(this, "No drivers in the queue."); return; }
            GuiUtil.info(this, d.getName() + " was skipped and moved to the back.");
            reloadData();
        });
        bDeparted.addActionListener(e -> markDeparted());
        bHistory.addActionListener(e -> showQueueHistory());
        reloadData();
    }

    public final void reloadData() {
        Object selected = cbDriver.getSelectedItem();
        cbDriver.removeAllItems();
        for (Driver d : AppData.drivers) cbDriver.addItem(AppData.label(d));
        if (selected != null) cbDriver.setSelectedItem(selected);

        tricycleListModel.clear();
        for (String row : AppData.queue.getDisplayRows("Tricycle")) tricycleListModel.addElement(row);
        habalListModel.clear();
        for (String row : AppData.queue.getDisplayRows("HabalHabal")) habalListModel.addElement(row);
        Driver loading = AppData.queue.getLoadingDriver();
        if (loading == null) lblNow.setText("Now loading: -");
        else lblNow.setText("Now loading: " + loading.getName() + " (" + loading.getVehicle().getPlateNumber() + ")");
    }

    private void markDeparted() {
        Driver loading = AppData.queue.getLoadingDriver();
        if (loading == null) {
            GuiUtil.error(this, "There is no driver currently marked NOW LOADING.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Confirm that " + loading.getName() + " has departed? The next waiting driver will then be called.",
                "Confirm Departure", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        if (!AppData.queue.completeLoadingDriver("Departure confirmed by dispatcher")) {
            GuiUtil.error(this, "Could not save the departure status. Check that the data folder is writable.");
            return;
        }
        reloadData();
        Driver next = AppData.queue.getLoadingDriver();
        GuiUtil.info(this, next == null
                ? loading.getName() + " is marked DEPARTED. No drivers are waiting."
                : loading.getName() + " is marked DEPARTED. " + next.getName() + " is now loading.");
    }

    private void showQueueHistory() {
        java.util.List<String> history = FileManager.getQueueHistory();
        JTextArea text = new JTextArea(18, 72);
        text.setEditable(false);
        if (history.isEmpty()) text.setText("No queue activity has been recorded yet.");
        else text.setText(String.join(System.lineSeparator(), history));
        text.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(text), "Queue Activity History",
                JOptionPane.INFORMATION_MESSAGE);
    }
}

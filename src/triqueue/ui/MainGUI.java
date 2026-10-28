package triqueue.ui;

import triqueue.core.AppData;
import triqueue.storage.FileManager;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;

public class MainGUI extends JFrame {

    // Size control: 1.0 = original size, 1.3 = 30% larger, 1.5 = 50% larger.
    // Change this one number if the buttons are still too small (or too big).
    private static final float FONT_SCALE = 1.3f;

    public MainGUI() {
        super("TrikeQueue - Tricycle & Habal-Habal Terminal Queue and Fare Management System");
        AppData.load();

        DriverPanel drivers = new DriverPanel();
        QueuePanel queue = new QueuePanel();
        TripPanel trips = new TripPanel();
        FarePanel fares = new FarePanel();
        EarningsPanel earnings = new EarningsPanel();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Drivers & Vehicles", drivers);
        tabs.addTab("Terminal Queue", queue);
        tabs.addTab("Trip Logging", trips);
        tabs.addTab("Fares & Routes", fares);
        tabs.addTab("Boundary & Earnings", earnings);

        // Refresh the visible tab so combo boxes and tables are always up to date
        tabs.addChangeListener(e -> {
            Component c = tabs.getSelectedComponent();
            if (c == drivers) drivers.reloadData();
            else if (c == queue) queue.reloadData();
            else if (c == trips) trips.reloadData();
            else if (c == fares) fares.reloadData();
            else if (c == earnings) earnings.reloadData();
        });

        JLabel header = new JLabel("  TrikeQueue", SwingConstants.LEFT);
        header.setOpaque(true);
        header.setBackground(GuiUtil.NAVY);
        header.setForeground(Color.WHITE);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 26f));
        header.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        JMenu dataMenu = new JMenu("Data");
        JMenuItem backupItem = new JMenuItem("Back Up Data");
        JMenuItem restoreItem = new JMenuItem("Restore Data Backup");
        backupItem.addActionListener(e -> createDataBackup());
        restoreItem.addActionListener(e -> restoreDataBackup(drivers, queue, trips, fares, earnings));
        dataMenu.add(backupItem);
        dataMenu.add(restoreItem);
        menuBar.add(dataMenu);
        setJMenuBar(menuBar);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 720);                         // larger window to fit the bigger controls
        setMinimumSize(new Dimension(950, 620));
        setLocationRelativeTo(null);
    }

    private void createDataBackup() {
        try {
            Path backupDirectory = FileManager.createBackup();
            GuiUtil.info(this, "Data backup created at:\n" + backupDirectory);
        } catch (IOException e) {
            GuiUtil.error(this, "Could not create data backup: " + e.getMessage());
        }
    }

    private void restoreDataBackup(DriverPanel drivers, QueuePanel queue, TripPanel trips,
                                   FarePanel fares, EarningsPanel earnings) {
        JFileChooser chooser = new JFileChooser(FileManager.getBackupDirectory().toFile());
        chooser.setDialogTitle("Choose a TrikeQueue Backup Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path selectedBackup = chooser.getSelectedFile().toPath();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Restoring will replace current records. A safety backup will be created first. Continue?",
                "Confirm Data Restore", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            Path safetyBackup = FileManager.createBackup();
            FileManager.restoreBackup(selectedBackup);
            AppData.load();
            drivers.reloadData();
            queue.reloadData();
            trips.reloadData();
            fares.reloadData();
            earnings.reloadData();
            GuiUtil.info(this, "Backup restored. A safety copy of the previous data is at:\n" + safetyBackup);
        } catch (IOException e) {
            GuiUtil.error(this, "Could not restore backup: " + e.getMessage());
        }
    }

    // Makes every button, label, tab, text field, table and dialog bigger.
    // Must run AFTER setLookAndFeel and BEFORE any component is created.
    private static void applyLargerUI() {
        // 1. Scale every default font in the look and feel
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            Object value = UIManager.get(key);
            if (value instanceof Font f) {
                UIManager.put(key, new FontUIResource(f.deriveFont(f.getSize2D() * FONT_SCALE)));
            }
        }

        // 2. Add padding so buttons, tabs and text fields are bigger to click
        UIManager.put("Button.margin", new InsetsUIResource(8, 22, 8, 22));
        UIManager.put("TabbedPane.tabInsets", new InsetsUIResource(8, 20, 8, 20));
        UIManager.put("TextField.margin", new InsetsUIResource(4, 6, 4, 6));
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | UnsupportedLookAndFeelException ignored) { }
        applyLargerUI();
        SwingUtilities.invokeLater(() -> new MainGUI().setVisible(true));
    }
}

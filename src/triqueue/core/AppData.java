package triqueue.core;

import triqueue.model.*;
import triqueue.storage.FileManager;

import java.util.ArrayList;
import java.util.List;


public class AppData {
    public static List<Driver> drivers = new ArrayList<>();
    public static TerminalQueue queue = new TerminalQueue();
    public static Dispatcher dispatcher = new Dispatcher("DSP001", "Terminal Dispatcher", "09170000000", "Main Terminal");

    public static void load() {
        drivers = FileManager.loadDrivers();
        queue.load(drivers);
    }

    public static Driver findDriver(String id) {
        for (Driver d : drivers) if (d.getId().equalsIgnoreCase(id)) return d;
        return null;
    }

    public static String label(Driver d) {
        return d.getId() + " - " + d.getName();
    }

    public static Driver fromLabel(String label) {
        if (label == null) return null;
        int i = label.indexOf(" - ");
        return findDriver(i < 0 ? label : label.substring(0, i));
    }
}

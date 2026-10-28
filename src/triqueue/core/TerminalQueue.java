package triqueue.core;

import triqueue.model.*;
import triqueue.storage.FileManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TerminalQueue {
    private static class Entry {
        Driver driver;
        String time;
        Entry(Driver d, String t) { driver = d; time = t; }
    }

    private final LinkedList<Entry> queue = new LinkedList<>();
    private Entry loading;

    public boolean addDriver(Driver d) {
        if (loading != null && loading.driver.getId().equals(d.getId())) return false;
        for (Entry e : queue) {
            if (e.driver.getId().equals(d.getId())) {
                System.out.println(d.getName() + " is already in the queue.");
                return false;
            }
        }
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        Entry entry = new Entry(d, now);
        queue.addLast(entry);
        if (!save()) {
            queue.removeLast();
            return false;
        }
        FileManager.logQueueEvent(d.getId(), "JOINED", "Added to waiting queue");
        return true;
    }

    // Moves the first waiting driver into the single active loading slot.
    public Driver getNextDriver() {
        if (loading != null || queue.isEmpty()) return null;
        Entry next = queue.removeFirst();
        loading = next;
        if (!save()) {
            loading = null;
            queue.addFirst(next);
            return null;
        }
        FileManager.logQueueEvent(next.driver.getId(), "NOW_LOADING", "Called to load passengers");
        return next.driver;
    }

    public Driver getLoadingDriver() {
        return loading == null ? null : loading.driver;
    }

    // Completes the current driver's departure and promotes the next waiting driver.
    public boolean completeLoadingDriver(String departureDetails) {
        if (loading == null) return false;
        Entry departed = loading;
        loading = null;
        Entry next = queue.isEmpty() ? null : queue.removeFirst();
        loading = next;
        if (!save()) {
            loading = departed;
            if (next != null) queue.addFirst(next);
            return false;
        }
        String details = departureDetails == null || departureDetails.trim().isEmpty()
                ? "Departure confirmed by dispatcher" : departureDetails.trim();
        FileManager.logQueueEvent(departed.driver.getId(), "DEPARTED", details);
        if (next != null)
            FileManager.logQueueEvent(next.driver.getId(), "NOW_LOADING", "Called after previous driver departed");
        return true;
    }

    // Absent driver goes to the back of the line
    public Driver skipDriver() {
        if (queue.isEmpty()) return null;
        Entry e = queue.removeFirst();
        queue.addLast(e);
        if (!save()) {
            queue.removeLast();
            queue.addFirst(e);
            return null;
        }
        FileManager.logQueueEvent(e.driver.getId(), "SKIPPED", "Moved to back of queue");
        return e.driver;
    }

    public boolean isEmpty() { return queue.isEmpty(); }

    public boolean contains(Driver d) {
        if (loading != null && loading.driver.getId().equals(d.getId())) return true;
        for (Entry e : queue) if (e.driver.getId().equals(d.getId())) return true;
        return false;
    }

    // Used by the GUI to show the queue in order
    public List<String> getDisplayRows() {
        return getDisplayRows(null);
    }

    public List<String> getDisplayRows(String vehicleType) {
        List<String> rows = new ArrayList<>();
        int pos = 1;
        for (Entry e : queue) {
            if (vehicleType != null && !vehicleType.equalsIgnoreCase(e.driver.getVehicle().getVehicleType())) continue;
            rows.add(pos++ + ".  " + e.driver.getName() + "  (" + e.driver.getVehicle().getPlateNumber()
                    + ", " + e.driver.getVehicle().getVehicleType() + ")  -  joined " + e.time);
        }
        return rows;
    }

    public void display() {
        if (queue.isEmpty()) {
            System.out.println("Queue is empty.");
            return;
        }
        int pos = 1;
        for (Entry e : queue) {
            System.out.println(pos++ + ". " + e.driver.getName() + " (" + e.driver.getVehicle().getPlateNumber()
                    + ") - joined " + e.time);
        }
    }

    public boolean save() {
        List<String> lines = new ArrayList<>();
        if (loading != null) lines.add(loading.driver.getId() + "|" + loading.time + "|NOW_LOADING");
        for (Entry e : queue) {
            lines.add(e.driver.getId() + "|" + e.time + "|WAITING");
        }
        return FileManager.writeFile(FileManager.QUEUE, lines);
    }

    public void load(List<Driver> drivers) {
        queue.clear();
        loading = null;
        for (String line : FileManager.readFile(FileManager.QUEUE)) {
            String[] p = line.split("\\|");
            if (p.length < 2) continue;
            for (Driver d : drivers) {
                if (d.getId().equals(p[0])) {
                    Entry entry = new Entry(d, p[1]);
                    if (p.length >= 3 && p[2].equals("NOW_LOADING") && loading == null) loading = entry;
                    else queue.addLast(entry);
                    break;
                }
            }
        }
    }
}

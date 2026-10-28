package triqueue;

import triqueue.core.*;
import triqueue.model.*;
import triqueue.storage.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class Main {
    private static final Scanner sc = new Scanner(System.in);
    private static List<Driver> drivers;
    private static final TerminalQueue queue = new TerminalQueue();
    private static final Dispatcher dispatcher = new Dispatcher("DSP001", "Terminal Dispatcher", "09170000000", "Main Terminal");

    public static void main(String[] args) {
        drivers = FileManager.loadDrivers();    
        queue.load(drivers);

        boolean running = true;
        while (running) {
            System.out.println("\n===== TrikeQueue =====");
            System.out.println("1. Driver & Vehicle Registration");
            System.out.println("2. Terminal Queue Management");
            System.out.println("3. Trip Logging");
            System.out.println("4. Fare Computation & Route List");
            System.out.println("5. Daily Boundary & Earnings Report");
            System.out.println("0. Exit");
            int choice = readInt("Choose: ");
            switch (choice) {
                case 1: registrationMenu(); break;
                case 2: queueMenu(); break;
                case 3: logTrip(); break;
                case 4: fareMenu(); break;
                case 5: earningsMenu(); break;
                case 0: running = false; break;
                default: System.out.println("Invalid choice.");
            }
        }
        System.out.println("Goodbye!");
    }

    // ---------- Input helpers (so bad input never crashes the program) ----------
    private static String readLine(String prompt) {
        System.out.print(prompt);
        return sc.hasNextLine() ? sc.nextLine().trim() : "";
    }

    private static int readInt(String prompt) {
        while (true) {
            String s = readLine(prompt);
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
                if (!sc.hasNextLine()) return 0;
            }
        }
    }

    private static double readDouble(String prompt) {
        while (true) {
            String s = readLine(prompt);
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
                if (!sc.hasNextLine()) return 0;
            }
        }
    }

    private static Driver findDriver(String id) {
        for (Driver d : drivers) if (d.getId().equalsIgnoreCase(id)) return d;
        return null;
    }

    // ---------- 1. Registration ----------
    private static void registrationMenu() {
        System.out.println("\n-- Driver & Vehicle Registration --");
        System.out.println("1. Add  2. Update contact  3. Delete  4. Search  5. List all  0. Back");
        int c = readInt("Choose: ");
        switch (c) {
            case 1: {
                String name = readLine("Name: ");
                String contact = readLine("Contact number (digits only): ");
                String license = readLine("License number: ");
                String plate = readLine("Plate number: ");
                String type = readLine("Vehicle type (Tricycle/HabalHabal): ");
                if (name.isEmpty() || license.isEmpty() || plate.isEmpty()) {
                    System.out.println("Name, license and plate cannot be empty.");
                    return;
                }
                if (!contact.matches("\\d{7,11}")) {
                    System.out.println("Contact number must be 7-11 digits.");
                    return;
                }
                if (!type.equalsIgnoreCase("Tricycle") && !type.equalsIgnoreCase("HabalHabal")) {
                    System.out.println("Vehicle type must be Tricycle or HabalHabal.");
                    return;
                }
                for (Driver existing : drivers) {
                    if (existing.getLicenseNumber().equalsIgnoreCase(license)
                            || existing.getVehicle().getPlateNumber().equalsIgnoreCase(plate)) {
                        System.out.println("That license number or plate number is already registered.");
                        return;
                    }
                }
                String id = FileManager.nextId(FileManager.DRIVERS, "D");
                drivers.add(new Driver(id, name, contact, license, plate, type));
                FileManager.saveDrivers(drivers);
                System.out.println("Registered " + name + " with ID " + id);
                break;
            }
            case 2: {
                Driver d = findDriver(readLine("Driver ID: "));
                if (d == null) { System.out.println("Driver not found."); return; }
                d.setContactNumber(readLine("New contact number: "));
                FileManager.saveDrivers(drivers);
                System.out.println("Updated.");
                break;
            }
            case 3: {
                Driver d = findDriver(readLine("Driver ID: "));
                if (d == null) { System.out.println("Driver not found."); return; }
                drivers.remove(d);
                FileManager.saveDrivers(drivers);
                queue.load(drivers); // drop the deleted driver from the queue
                queue.save();
                System.out.println("Deleted " + d.getName());
                break;
            }
            case 4: {
                String key = readLine("Search name, ID or plate: ").toLowerCase();
                boolean found = false;
                for (Driver d : drivers) {
                    if (d.getName().toLowerCase().contains(key) || d.getId().toLowerCase().equals(key)
                            || d.getVehicle().getPlateNumber().toLowerCase().contains(key)) {
                        System.out.println(d);
                        found = true;
                    }
                }
                if (!found) System.out.println("No match.");
                break;
            }
            case 5:
                if (drivers.isEmpty()) System.out.println("No drivers registered.");
                for (Driver d : drivers) System.out.println(d);
                break;
            default: break;
        }
    }

    // ---------- 2. Queue ----------
    private static void queueMenu() {
        System.out.println("\n-- Terminal Queue --");
        System.out.println("1. Add driver to queue  2. Call next driver  3. Skip absent driver  4. View queue  0. Back");
        int c = readInt("Choose: ");
        switch (c) {
            case 1: {
                Driver d = findDriver(readLine("Driver ID: "));
                if (d == null) { System.out.println("Driver not found."); return; }
                dispatcher.addDriverToQueue(queue, d);
                break;
            }
            case 2: dispatcher.callNextDriver(queue); break;
            case 3: dispatcher.skipNextDriver(queue); break;
            case 4: queue.display(); break;
            default: break;
        }
    }

    // ---------- 3. Trip logging ----------
    private static void logTrip() {
        System.out.println("\n-- Log Trip --");
        Driver d = findDriver(readLine("Driver ID: "));
        if (d == null) { System.out.println("Driver not found."); return; }

        FileManager.printFares();
        String route = readLine("Route: ");
        if (!FileManager.routeExists(route)) { System.out.println("Route not found."); return; }

        Map<String, Integer> passengerCounts = new LinkedHashMap<>();
        int totalPassengers = 0;
        for (String[] discount : FileManager.getDiscounts()) {
            int count = readInt("Number of " + discount[0] + " passengers: ");
            if (count < 0 || count > 20 || totalPassengers + count > 20) {
                System.out.println("Passenger counts must be non-negative and total no more than 20.");
                return;
            }
            if (count > 0) passengerCounts.put(discount[0], count);
            totalPassengers += count;
        }
        if (totalPassengers == 0) {
            System.out.println("Enter at least one passenger.");
            return;
        }

        String tripId = FileManager.nextId(FileManager.TRIPS, "T");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        Trip trip = new Trip(tripId, d, route, passengerCounts, timestamp);
        double fare = trip.calculateFare();

        FileManager.appendFile(FileManager.TRIPS, trip.toFileString());
        System.out.printf("Trip %s saved. Base: %.2f | Discount: %.2f | FARE: %.2f%n", tripId,
                trip.getFareDetails().getBaseFare(), trip.getFareDetails().getDiscountApplied(), fare);
    }

    // ---------- 4. Fares ----------
    private static void fareMenu() {
        System.out.println("\n-- Fares & Routes --");
        System.out.println("1. View fares  2. Add/Update route  3. Add/Update discount  0. Back");
        int c = readInt("Choose: ");
        switch (c) {
            case 1: FileManager.printFares(); break;
            case 2: {
                String route = readLine("Route name: ");
                double tri = readDouble("Tricycle fare per passenger: ");
                double hab = readDouble("HabalHabal fare per trip: ");
                if (route.isEmpty() || !Double.isFinite(tri) || !Double.isFinite(hab) || tri < 0 || hab < 0) {
                    System.out.println("Invalid route or negative fare.");
                    return;
                }
                FileManager.saveRoute(route, tri, hab);
                System.out.println("Route saved.");
                break;
            }
            case 3: {
                String type = readLine("Passenger type: ");
                double pct = readDouble("Discount percent (0-100): ");
                if (type.isEmpty() || !Double.isFinite(pct) || pct < 0 || pct > 100) {
                    System.out.println("Invalid input.");
                    return;
                }
                FileManager.saveDiscount(type, pct);
                System.out.println("Discount saved.");
                break;
            }
            default: break;
        }
    }

    // ---------- 5. Boundary & earnings ----------
    private static void earningsMenu() {
        System.out.println("\n-- Boundary & Earnings --");
        System.out.println("1. Add boundary payment  2. Daily report  0. Back");
        int c = readInt("Choose: ");
        String today = LocalDate.now().toString();
        switch (c) {
            case 1: {
                Driver d = findDriver(readLine("Driver ID: "));
                if (d == null) { System.out.println("Driver not found."); return; }
                double amount = readDouble("Boundary amount: ");
                if (!Double.isFinite(amount) || !d.payBoundary(amount)) return;
                String id = FileManager.nextId(FileManager.BOUNDARY, "B");
                FileManager.appendFile(FileManager.BOUNDARY,
                        id + "|" + d.getId() + "|" + today + "|" + String.format("%.2f", amount));
                System.out.println("Boundary recorded.");
                break;
            }
            case 2: {
                String date = readLine("Date (yyyy-MM-dd, Enter = today): ");
                if (date.isEmpty()) date = today;
                try {
                    dispatcher.generateDailyReport(drivers, LocalDate.parse(date).toString());
                } catch (DateTimeParseException ex) {
                    System.out.println("Please enter a valid date in yyyy-MM-dd format.");
                }
                break;
            }
            default: break;
        }
    }
}

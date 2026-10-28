package triqueue.storage;

import triqueue.model.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FileManager {
    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path DATA_DIRECTORY = Paths.get(
            System.getProperty("triqueue.dataDir", PROJECT_ROOT.resolve("data").toString()))
            .toAbsolutePath().normalize();
    private static final Path BACKUP_DIRECTORY = PROJECT_ROOT.resolve("backups");
    public static final String DIR = DATA_DIRECTORY.toString() + File.separator;
    public static final String DRIVERS = DIR + "drivers.txt";
    public static final String QUEUE = DIR + "queue.txt";
    public static final String TRIPS = DIR + "trips.txt";
    public static final String FARES = DIR + "fares.txt";
    public static final String BOUNDARY = DIR + "boundary.txt";
    public static final String TRIP_ADJUSTMENTS = DIR + "trip_adjustments.txt";
    public static final String QUEUE_HISTORY = DIR + "queue_history.txt";

    private static Path findProjectRoot() {
        try {
            URI location = FileManager.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path current = Paths.get(location).toAbsolutePath().normalize();
            if (!Files.isDirectory(current)) current = current.getParent();
            while (current != null) {
                if (Files.isDirectory(current.resolve("src"))) return current;
                current = current.getParent();
            }
        } catch (URISyntaxException | IllegalArgumentException | SecurityException ignored) { }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public static Path getBackupDirectory() {
        return BACKUP_DIRECTORY;
    }

    private static void ensureFile(String path) throws IOException {
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        if (!f.exists()) f.createNewFile();
    }

    public static List<String> readFile(String path) {
        List<String> lines = new ArrayList<>();
        try {
            ensureFile(path);
            try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty()) lines.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading " + path + ": " + e.getMessage());
        }
        return lines;
    }

    public static boolean writeFile(String path, List<String> lines) {
        try {
            ensureFile(path);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(path, false))) {
                for (String line : lines) {
                    bw.write(line);
                    bw.newLine();
                }
            }
            return true;
        } catch (IOException e) {
            System.out.println("Error writing " + path + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean appendFile(String path, String line) {
        try {
            ensureFile(path);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(path, true))) {
                bw.write(line);
                bw.newLine();
            }
            return true;
        } catch (IOException e) {
            System.out.println("Error appending to " + path + ": " + e.getMessage());
            return false;
        }
    }

    public static Set<String> getVoidedTripIds() {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, String> entry : getTripStatuses().entrySet())
            if (entry.getValue().equals("VOIDED")) ids.add(entry.getKey());
        return ids;
    }

    public static Map<String, String> getTripStatuses() {
        Map<String, String> statuses = new HashMap<>();
        for (String line : readFile(TRIP_ADJUSTMENTS)) {
            String[] p = line.split("\\|", 13);
            if (p.length >= 2) {
                if (p[1].equals("VOID")) statuses.put(p[0], "VOIDED");
                else if (p[1].equals("ARCHIVE")) statuses.put(p[0], "ARCHIVED");
                else if (p[1].equals("RESTORE")) statuses.put(p[0], "ACTIVE");
                else if (p[1].equals("CORRECT")) statuses.put(p[0], "CORRECTED");
            }
        }
        return statuses;
    }

    public static Map<String, String> getEffectiveTrips() {
        Map<String, String> trips = new LinkedHashMap<>();
        for (String line : readFile(TRIPS)) {
            String[] p = line.split("\\|", 2);
            if (p.length == 2) trips.put(p[0], line);
        }
        for (String line : readFile(TRIP_ADJUSTMENTS)) {
            String[] p = line.split("\\|", 13);
            if (p.length >= 2 && p[1].equals("CORRECT") && trips.containsKey(p[0])) {
                if (p.length == 12) {
                    // Legacy correction record without a vehicle type snapshot.
                    trips.put(p[0], String.join("|", p[0], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11]));
                } else if (p.length == 13) {
                    trips.put(p[0], String.join("|", p[0], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], p[12]));
                }
            }
        }
        return trips;
    }

    public static boolean correctTrip(String tripId, String reason, String replacementTripRecord) {
        if (!getEffectiveTrips().containsKey(tripId) || getVoidedTripIds().contains(tripId)) return false;
        String[] replacement = replacementTripRecord.split("\\|", -1);
        if ((replacement.length != 9 && replacement.length != 10) || !replacement[0].equals(tripId)) return false;
        return appendTripAdjustment(tripId, "CORRECT", reason,
                Arrays.copyOfRange(replacement, 1, replacement.length));
    }

    public static boolean voidTrip(String tripId, String reason) {
        if (!getEffectiveTrips().containsKey(tripId) || getVoidedTripIds().contains(tripId)) return false;
        return appendTripAdjustment(tripId, "VOID", reason);
    }

    public static boolean archiveTrip(String tripId, String reason) {
        if (!getEffectiveTrips().containsKey(tripId)) return false;
        String status = getTripStatuses().getOrDefault(tripId, "ACTIVE");
        if (status.equals("ARCHIVED") || status.equals("VOIDED")) return false;
        return appendTripAdjustment(tripId, "ARCHIVE", reason);
    }

    public static boolean restoreTrip(String tripId) {
        if (!getEffectiveTrips().containsKey(tripId)
                || !"ARCHIVED".equals(getTripStatuses().get(tripId))) return false;
        return appendTripAdjustment(tripId, "RESTORE", "Restored from trip archive");
    }

    public static List<String> getReportDates() {
        SortedSet<String> dates = new TreeSet<>(Collections.reverseOrder());
        Map<String, String> statuses = getTripStatuses();
        for (String line : getEffectiveTrips().values()) {
            String[] p = line.split("\\|", -1);
            if (p.length >= 9 && !"VOIDED".equals(statuses.get(p[0]))
                    && !"ARCHIVED".equals(statuses.get(p[0]))) {
                addReportDate(dates, p[8]);
            }
        }
        for (String line : readFile(BOUNDARY)) {
            String[] p = line.split("\\|", -1);
            if (p.length >= 4) addReportDate(dates, p[2]);
        }
        return new ArrayList<>(dates);
    }

    private static void addReportDate(Set<String> dates, String value) {
        try {
            dates.add(java.time.LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value).toString());
        } catch (java.time.DateTimeException ignored) { }
    }

    private static boolean appendTripAdjustment(String tripId, String action, String reason, String... replacement) {
        String safeReason = reason == null ? "" : reason.trim()
                .replace('|', '/')
                .replace('\r', ' ')
                .replace('\n', ' ');
        if (safeReason.isEmpty()) return false;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            ensureFile(TRIP_ADJUSTMENTS);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(TRIP_ADJUSTMENTS, true))) {
                bw.write(tripId + "|" + action + "|" + timestamp + "|" + safeReason);
                for (String field : replacement) bw.write("|" + field.replace('|', '/'));
                bw.newLine();
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error recording trip void: " + e.getMessage());
            return false;
        }
    }

    public static Path createBackup() throws IOException {
        Path dataDirectory = DATA_DIRECTORY;
        if (!Files.isDirectory(dataDirectory)) {
            throw new IOException("Data folder was not found: " + dataDirectory);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path backupDirectory = BACKUP_DIRECTORY.resolve("TrikeQueue-" + timestamp);
        Files.createDirectories(backupDirectory);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dataDirectory, "*.txt")) {
            for (Path source : files) {
                Files.copy(source, backupDirectory.resolve(source.getFileName()), StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        return backupDirectory.toAbsolutePath().normalize();
    }

    public static void restoreBackup(Path selectedBackup) throws IOException {
        Path backupRoot = BACKUP_DIRECTORY.toAbsolutePath().normalize();
        Path sourceDirectory = selectedBackup.toAbsolutePath().normalize();
        if (!sourceDirectory.startsWith(backupRoot) || !Files.isDirectory(sourceDirectory)) {
            throw new IOException("Select a valid TrikeQueue backup folder.");
        }

        String[] requiredFiles = {"drivers.txt", "queue.txt", "trips.txt", "fares.txt", "boundary.txt"};
        for (String name : requiredFiles) {
            if (!Files.isRegularFile(sourceDirectory.resolve(name))) {
                throw new IOException("Backup is missing " + name + ".");
            }
        }
        Files.createDirectories(DATA_DIRECTORY);
        String[] restorableFiles = {"drivers.txt", "queue.txt", "trips.txt", "fares.txt", "boundary.txt",
                "trip_adjustments.txt", "queue_history.txt"};
        for (String name : restorableFiles) {
            Path source = sourceDirectory.resolve(name);
            Path destination = DATA_DIRECTORY.resolve(name);
            if (Files.isRegularFile(source)) Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            else if (name.equals("trip_adjustments.txt") || name.equals("queue_history.txt"))
                Files.write(destination, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    public static void logQueueEvent(String driverId, String action, String details) {
        String safeDetails = details == null ? "" : details.trim().replace('|', '/').replace('\r', ' ').replace('\n', ' ');
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        appendFile(QUEUE_HISTORY, timestamp + "|" + driverId + "|" + action + "|" + safeDetails);
    }

    public static List<String> getQueueHistory() {
        List<String> history = readFile(QUEUE_HISTORY);
        Collections.reverse(history);
        return history;
    }

    // ---------- Drivers ----------
    public static List<Driver> loadDrivers() {
        List<Driver> drivers = new ArrayList<>();
        for (String line : readFile(DRIVERS)) {
            String[] p = line.split("\\|");
            if (p.length < 6) continue; // skip bad lines instead of crashing
            drivers.add(new Driver(p[0], p[1], p[2], p[3], p[4], p[5]));
        }
        return drivers;
    }

    public static boolean saveDrivers(List<Driver> drivers) {
        List<String> lines = new ArrayList<>();
        for (Driver d : drivers) lines.add(d.toFileString());
        return writeFile(DRIVERS, lines);
    }

    // ---------- Fares ----------
    // fares.txt lines: ROUTE|name|tricyclePerPassenger|habalHabalPerTrip
    //                  DISCOUNT|passengerType|percent
    public static double getRoutePrice(String route, boolean tricycle) {
        for (String line : readFile(FARES)) {
            String[] p = line.split("\\|");
            if (p.length >= 4 && p[0].equals("ROUTE") && p[1].equalsIgnoreCase(route)) {
                try {
                    return Double.parseDouble(tricycle ? p[2] : p[3]);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    public static boolean routeExists(String route) {
        for (String line : readFile(FARES)) {
            String[] p = line.split("\\|");
            if (p.length >= 4 && p[0].equals("ROUTE") && p[1].equalsIgnoreCase(route)) return true;
        }
        return false;
    }

    public static double getDiscountPercent(String passengerType) {
        for (String line : readFile(FARES)) {
            String[] p = line.split("\\|");
            if (p.length >= 3 && p[0].equals("DISCOUNT") && p[1].equalsIgnoreCase(passengerType)) {
                try {
                    return Double.parseDouble(p[2]);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    // Adds a new ROUTE line or replaces the existing one with the same name
    public static void saveRoute(String route, double tricycleFare, double habalFare) {
        List<String> lines = readFile(FARES);
        String newLine = "ROUTE|" + route + "|" + String.format("%.2f", tricycleFare)
                + "|" + String.format("%.2f", habalFare);
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            String[] p = lines.get(i).split("\\|");
            if (p.length >= 2 && p[0].equals("ROUTE") && p[1].equalsIgnoreCase(route)) {
                lines.set(i, newLine);
                replaced = true;
            }
        }
        if (!replaced) lines.add(newLine);
        writeFile(FARES, lines);
    }

    public static void saveDiscount(String type, double percent) {
        List<String> lines = readFile(FARES);
        String newLine = "DISCOUNT|" + type + "|" + (int) percent;
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            String[] p = lines.get(i).split("\\|");
            if (p.length >= 2 && p[0].equals("DISCOUNT") && p[1].equalsIgnoreCase(type)) {
                lines.set(i, newLine);
                replaced = true;
            }
        }
        if (!replaced) lines.add(newLine);
        writeFile(FARES, lines);
    }

    public static List<String[]> getRoutes() {
        List<String[]> out = new ArrayList<>();
        for (String line : readFile(FARES)) {
            String[] p = line.split("\\|");
            if (p.length >= 4 && p[0].equals("ROUTE")) out.add(new String[]{p[1], p[2], p[3]});
        }
        return out;
    }

    public static List<String[]> getDiscounts() {
        List<String[]> out = new ArrayList<>();
        for (String line : readFile(FARES)) {
            String[] p = line.split("\\|");
            if (p.length >= 3 && p[0].equals("DISCOUNT")) out.add(new String[]{p[1], p[2]});
        }
        return out;
    }

    // Removes a ROUTE entry by name (case-insensitive). Returns true if a line was removed.
    public static boolean deleteRoute(String route) {
        List<String> lines = readFile(FARES);
        boolean removed = lines.removeIf(line -> {
            String[] p = line.split("\\|");
            return p.length >= 2 && p[0].equals("ROUTE") && p[1].equalsIgnoreCase(route);
        });
        if (removed) writeFile(FARES, lines);
        return removed;
    }

    // Removes a DISCOUNT entry by passenger type (case-insensitive). Returns true if a line was removed.
    public static boolean deleteDiscount(String type) {
        List<String> lines = readFile(FARES);
        boolean removed = lines.removeIf(line -> {
            String[] p = line.split("\\|");
            return p.length >= 2 && p[0].equals("DISCOUNT") && p[1].equalsIgnoreCase(type);
        });
        if (removed) writeFile(FARES, lines);
        return removed;
    }

    public static void printFares() {
        System.out.println("\n--- ROUTES (Tricycle per passenger / HabalHabal per trip) ---");
        for (String line : readFile(FARES)) {
            String[] p = line.split("\\|");
            if (p[0].equals("ROUTE") && p.length >= 4)
                System.out.printf("%-28s %8s %8s%n", p[1], p[2], p[3]);
        }
        System.out.println("--- DISCOUNTS ---");
        for (String line : readFile(FARES)) {
            String[] p = line.split("\\|");
            if (p[0].equals("DISCOUNT") && p.length >= 3)
                System.out.printf("%-28s %7s%%%n", p[1], p[2]);
        }
    }

    // ---------- ID generators ----------
    public static String nextId(String path, String prefix) {
    int max = 0;
    for (String line : readFile(path)) {
        String id = line.split("\\|")[0];
        if (id.startsWith(prefix)) {
            try { max = Math.max(max, Integer.parseInt(id.substring(prefix.length()))); }
            catch (NumberFormatException ignored) { }
        }
    }
    return String.format("%s%03d", prefix, max + 1);
}
}

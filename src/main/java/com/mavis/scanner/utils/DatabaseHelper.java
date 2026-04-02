package com.mavis.scanner.utils;

import com.mavis.scanner.config.AppConfig;
import io.appium.java_client.android.AndroidDriver;

import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.regex.*;

/**
 * Retrieves real UPC codes for barcode scan testing.
 *
 * Data sources (in priority order):
 * 1. tires_upc JSON file - BarcodeMasterList dump with 44K+ items
 * 2. SQL Server DEV database (barcodeMasterMultiUOM table)
 * 3. Device SQLite database (TireInv_db.db3) - populated after app login
 * 4. Hardcoded fallback UPC data
 */
public class DatabaseHelper {

    private static final String SQLITE_DB_PATH =
            "/data/data/" + AppConfig.APP_PACKAGE + "/files/TireInv_db.db3";

    /** Path to the tires_upc JSON file (BarcodeMasterList dump) */
    private static final String UPC_FILE_PATH = "tires_upc";

    /** Path to the parts_upc JSON file (parts BarcodeMasterList dump) */
    private static final String PARTS_UPC_FILE_PATH = "parts_upc";

    private final AndroidDriver driver;

    /** Cached items from tires_upc file */
    private static List<Map<String, String>> cachedItems = null;

    /** Cached items from parts_upc file */
    private static List<Map<String, String>> cachedPartsItems = null;

    public DatabaseHelper(AndroidDriver driver) {
        this.driver = driver;
    }

    /**
     * Get real UPC codes for testing.
     * Tries: tires_upc file -> SQL Server -> device SQLite -> fallback data
     */
    public List<String> getTestUpcs(String store, String invNum, int limit) {
        List<String> upcs;

        // Source 1: tires_upc JSON file
        upcs = getUpcsFromFile(limit);
        if (!upcs.isEmpty()) {
            System.out.println("[DatabaseHelper] Got " + upcs.size() + " UPCs from tires_upc file");
            return upcs;
        }

        // Source 2: SQL Server DEV database
        upcs = getUpcsFromSqlServer(store, invNum, limit);
        if (!upcs.isEmpty()) {
            System.out.println("[DatabaseHelper] Got " + upcs.size() + " UPCs from SQL Server");
            return upcs;
        }

        // Source 3: Device SQLite (populated after login)
        upcs = getUpcsFromDeviceSqlite(limit);
        if (!upcs.isEmpty()) {
            System.out.println("[DatabaseHelper] Got " + upcs.size() + " UPCs from device SQLite");
            return upcs;
        }

        // Source 4: Hardcoded fallback data
        upcs = getFallbackUpcs(limit);
        System.out.println("[DatabaseHelper] Using " + upcs.size() + " fallback UPCs");
        return upcs;
    }

    /**
     * Get UPC details (UPC|Item|Description|Size) for logging.
     */
    public List<String> getTestUpcDetails(String store, String invNum, int limit) {
        List<String> details;

        // Source 1: tires_upc file
        details = getUpcDetailsFromFile(limit);
        if (!details.isEmpty()) return details;

        // Source 2: SQL Server
        details = getUpcDetailsFromSqlServer(store, invNum, limit);
        if (!details.isEmpty()) return details;

        // Source 3: Device SQLite
        details = getUpcDetailsFromDeviceSqlite(limit);
        if (!details.isEmpty()) return details;

        // Source 4: Fallback
        details = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, AppConfig.FALLBACK_TIRE_DATA.length); i++) {
            details.add(AppConfig.FALLBACK_TIRE_DATA[i][1] + "|" +
                    AppConfig.FALLBACK_TIRE_DATA[i][0] + "|fallback tire|");
        }
        return details;
    }

    /**
     * Get the count of items in BarcodeMasterList on the device.
     */
    public int getMasterListCount() {
        // Try file first
        List<Map<String, String>> items = loadUpcFile();
        if (items != null && !items.isEmpty()) {
            return items.size();
        }
        // Fall back to device SQLite
        List<String> result = executeSqliteQuery("SELECT COUNT(*) FROM BarcodeMasterList");
        if (!result.isEmpty()) {
            try {
                return Integer.parseInt(result.get(0).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Get distinct product classes.
     */
    public List<String> getAvailableProductClasses() {
        List<Map<String, String>> items = loadUpcFile();
        if (items != null && !items.isEmpty()) {
            Set<String> pcs = new TreeSet<>();
            for (Map<String, String> item : items) {
                String pc = item.get("pc");
                if (pc != null && !pc.isEmpty()) {
                    pcs.add(pc);
                }
            }
            return new ArrayList<>(pcs);
        }
        return executeSqliteQuery(
                "SELECT DISTINCT pc FROM BarcodeMasterList WHERE pc IS NOT NULL ORDER BY pc");
    }

    // ==================== TIRES_UPC FILE ====================

    /**
     * Load and cache items from the tires_upc JSON file.
     * Uses simple regex parsing to avoid requiring a JSON library dependency.
     */
    private List<Map<String, String>> loadUpcFile() {
        if (cachedItems != null) {
            return cachedItems;
        }

        // Try multiple paths to find the file
        String[] paths = {
                UPC_FILE_PATH,
                System.getProperty("user.dir") + File.separator + UPC_FILE_PATH,
                System.getProperty("user.dir") + File.separator + ".." + File.separator + UPC_FILE_PATH
        };

        for (String path : paths) {
            File file = new File(path);
            if (file.exists() && file.length() > 0) {
                try {
                    cachedItems = parseUpcFile(file);
                    System.out.println("[DatabaseHelper] Loaded " + cachedItems.size() +
                            " items from " + file.getAbsolutePath());
                    return cachedItems;
                } catch (Exception e) {
                    System.out.println("[DatabaseHelper] Failed to parse " + path + ": " + e.getMessage());
                }
            }
        }

        return null;
    }

    /**
     * Parse the tires_upc JSON file. Simple line-by-line parsing for the known format.
     */
    private List<Map<String, String>> parseUpcFile(File file) throws IOException {
        List<Map<String, String>> items = new ArrayList<>();
        Map<String, String> current = null;

        Pattern kvPattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"?([^\",}]*)\"?");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equals("{")) {
                    current = new HashMap<>();
                } else if (line.startsWith("}")) {
                    if (current != null && current.containsKey("upc")) {
                        items.add(current);
                    }
                    current = null;
                } else if (current != null) {
                    Matcher m = kvPattern.matcher(line);
                    if (m.find()) {
                        String key = m.group(1);
                        String value = m.group(2).trim();
                        if (value.equals("null")) value = "";
                        current.put(key, value);
                    }
                }
            }
        }

        return items;
    }

    private List<String> getUpcsFromFile(int limit) {
        List<String> upcs = new ArrayList<>();
        List<Map<String, String>> items = loadUpcFile();
        if (items == null) return upcs;

        for (Map<String, String> item : items) {
            if (upcs.size() >= limit) break;
            String upc = item.get("upc");
            if (upc != null && !upc.isEmpty()) {
                upcs.add(upc);
            }
        }
        return upcs;
    }

    private List<String> getUpcDetailsFromFile(int limit) {
        List<String> details = new ArrayList<>();
        List<Map<String, String>> items = loadUpcFile();
        if (items == null) return details;

        for (Map<String, String> item : items) {
            if (details.size() >= limit) break;
            String upc = item.get("upc");
            if (upc != null && !upc.isEmpty()) {
                details.add(String.format("%s|%s|%s|%s",
                        upc,
                        item.getOrDefault("item", ""),
                        item.getOrDefault("description", ""),
                        item.getOrDefault("size", "")));
            }
        }
        return details;
    }

    /**
     * Get an item number from the file by its UPC code.
     */
    public String getItemNumberByUpc(String upc) {
        List<Map<String, String>> items = loadUpcFile();
        if (items == null) return null;

        for (Map<String, String> item : items) {
            if (upc.equals(item.get("upc")) || upc.equals(item.get("upc1"))
                    || upc.equals(item.get("upc2")) || upc.equals(item.get("upc3"))
                    || upc.equals(item.get("upc4"))) {
                return item.get("item");
            }
        }
        return null;
    }

    /**
     * Get a valid item number from the file for manual Add Item entry.
     */
    public String getValidItemNumber() {
        List<Map<String, String>> items = loadUpcFile();
        if (items != null && !items.isEmpty()) {
            return items.get(0).get("item");
        }
        return AppConfig.FALLBACK_TIRE_DATA[0][0];
    }

    // ==================== PARTS_UPC FILE ====================

    /**
     * Load and cache items from the parts_upc JSON file.
     */
    private List<Map<String, String>> loadPartsUpcFile() {
        if (cachedPartsItems != null) {
            return cachedPartsItems;
        }

        String[] paths = {
                PARTS_UPC_FILE_PATH,
                System.getProperty("user.dir") + File.separator + PARTS_UPC_FILE_PATH,
                System.getProperty("user.dir") + File.separator + ".." + File.separator + PARTS_UPC_FILE_PATH
        };

        for (String path : paths) {
            File file = new File(path);
            if (file.exists() && file.length() > 0) {
                try {
                    cachedPartsItems = parseUpcFile(file);
                    System.out.println("[DatabaseHelper] Loaded " + cachedPartsItems.size() +
                            " items from " + file.getAbsolutePath());
                    return cachedPartsItems;
                } catch (Exception e) {
                    System.out.println("[DatabaseHelper] Failed to parse parts_upc " + path + ": " + e.getMessage());
                }
            }
        }

        return null;
    }

    /**
     * Get UPC codes for a specific PC from the parts_upc file.
     * The file contains multiple PCs — this strictly filters to the requested PC only.
     */
    public List<String> getUpcsFromPartsFile(String pc, int limit) {
        List<String> upcs = new ArrayList<>();
        List<Map<String, String>> items = loadPartsUpcFile();
        if (items == null) return upcs;

        int totalMatchingPc = 0;
        for (Map<String, String> item : items) {
            String itemPc = item.get("pc");
            if (!pc.equals(itemPc)) continue;

            totalMatchingPc++;
            if (upcs.size() < limit) {
                String upc = item.get("upc");
                if (upc != null && !upc.isEmpty()) {
                    upcs.add(upc);
                }
            }
        }
        System.out.println("[DatabaseHelper] parts_upc: found " + totalMatchingPc +
                " total items for PC " + pc + ", returning " + upcs.size() + " UPCs");
        return upcs;
    }

    // ==================== SQL SERVER QUERIES ====================

    private List<String> getUpcsFromSqlServer(String store, String invNum, int limit) {
        List<String> upcs = new ArrayList<>();
        String sql = String.format(
                "SELECT TOP %d b.UPC FROM InventoryScanning.inv.barcodeMasterMultiUOM b " +
                        "JOIN InventoryScanning.inv.inventory a ON a.item_num = b.Item " +
                        "WHERE a.store = %s AND a.inv_num = %s AND b.UPC IS NOT NULL AND b.UPC != ''",
                limit, store, invNum);

        try (Connection conn = getSqlServerConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String upc = rs.getString("UPC");
                if (upc != null && !upc.trim().isEmpty()) {
                    upcs.add(upc.trim());
                }
            }
        } catch (Exception e) {
            System.out.println("[DatabaseHelper] SQL Server query failed: " + e.getMessage());
        }

        if (upcs.isEmpty()) {
            String fallbackSql = String.format(
                    "SELECT TOP %d UPC FROM InventoryScanning.inv.barcodeMasterMultiUOM " +
                            "WHERE UPC IS NOT NULL AND UPC != ''", limit);
            try (Connection conn = getSqlServerConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(fallbackSql)) {
                while (rs.next()) {
                    String upc = rs.getString("UPC");
                    if (upc != null && !upc.trim().isEmpty()) {
                        upcs.add(upc.trim());
                    }
                }
            } catch (Exception e) {
                System.out.println("[DatabaseHelper] SQL Server fallback query failed: " + e.getMessage());
            }
        }

        return upcs;
    }

    private List<String> getUpcDetailsFromSqlServer(String store, String invNum, int limit) {
        List<String> details = new ArrayList<>();
        String sql = String.format(
                "SELECT TOP %d b.UPC, a.item_num, c.Idesc, c.Isize " +
                        "FROM InventoryScanning.inv.inventory a " +
                        "LEFT JOIN InventoryScanning.inv.barcodeMasterMultiUOM b ON a.item_num = b.Item " +
                        "LEFT JOIN TireMaxLive.dbo.Invmas c ON a.item_num = c.Item " +
                        "WHERE a.store = %s AND a.inv_num = %s AND b.UPC IS NOT NULL AND b.UPC != ''",
                limit, store, invNum);

        try (Connection conn = getSqlServerConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String upc = rs.getString("UPC");
                String item = rs.getString("item_num");
                String desc = rs.getString("Idesc");
                String size = rs.getString("Isize");
                details.add(String.format("%s|%s|%s|%s",
                        upc != null ? upc.trim() : "",
                        item != null ? item.trim() : "",
                        desc != null ? desc.trim() : "",
                        size != null ? size.trim() : ""));
            }
        } catch (Exception e) {
            System.out.println("[DatabaseHelper] SQL Server detail query failed: " + e.getMessage());
        }
        return details;
    }

    private Connection getSqlServerConnection() throws Exception {
        String url = String.format(
                "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true;",
                AppConfig.DB_SERVER, AppConfig.DB_PORT, AppConfig.DB_INVENTORY);
        return DriverManager.getConnection(url, AppConfig.DB_USERNAME, AppConfig.DB_PASSWORD);
    }

    // ==================== DEVICE SQLITE QUERIES ====================

    private List<String> getUpcsFromDeviceSqlite(int limit) {
        return executeSqliteQuery(String.format(
                "SELECT Upc FROM BarcodeMasterList WHERE Upc IS NOT NULL AND Upc != '' LIMIT %d", limit));
    }

    private List<String> getUpcDetailsFromDeviceSqlite(int limit) {
        return executeSqliteQuery(String.format(
                "SELECT Upc || '|' || Item || '|' || Description || '|' || Size " +
                        "FROM BarcodeMasterList WHERE Upc IS NOT NULL AND Upc != '' LIMIT %d", limit));
    }

    private List<String> executeSqliteQuery(String sqlQuery) {
        List<String> results = new ArrayList<>();
        String command = String.format("sqlite3 %s \"%s\"", SQLITE_DB_PATH, sqlQuery);

        try {
            Map<String, Object> args = new HashMap<>();
            args.put("command", "sh");
            args.put("args", Arrays.asList("-c", command));
            Object output = driver.executeScript("mobile: shell", args);

            if (output != null) {
                String outputStr = output.toString().trim();
                if (isShellError(outputStr)) {
                    System.out.println("[DatabaseHelper] SQLite shell error: " + outputStr);
                    return results;
                }
                if (!outputStr.isEmpty()) {
                    for (String line : outputStr.split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !isShellError(trimmed)) {
                            results.add(trimmed);
                        }
                    }
                }
            }
        } catch (Exception e) {
            try {
                results = executeAdbQuery(command);
            } catch (Exception ex) {
                // SQLite on device not available
            }
        }

        return results;
    }

    private List<String> executeAdbQuery(String command) throws Exception {
        List<String> results = new ArrayList<>();

        String adbPath = AppConfig.ADB_PATH;

        ProcessBuilder pb = new ProcessBuilder(
                adbPath, "-s", AppConfig.getDeviceUDID(), "shell", command
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !isShellError(trimmed)) {
                results.add(trimmed);
            }
        }

        process.waitFor();
        return results;
    }

    /**
     * Detect shell error messages that should not be treated as query results.
     */
    private static boolean isShellError(String line) {
        if (line == null) return false;
        String lower = line.toLowerCase();
        return lower.contains("inaccessible or not found")
                || lower.contains("not found")
                || lower.contains("error")
                || lower.contains("permission denied")
                || lower.contains("no such file")
                || lower.contains("unable to open")
                || lower.contains("cannot open");
    }

    // ==================== SECTION BARCODES ====================

    /**
     * Get all section barcodes for the current inventory from the device SQLite database.
     * Falls back to SQL Server, then to fallback data.
     */
    public List<String> getSectionBarcodes() {
        List<String> sections;

        // Source 1: Device SQLite (StorageLocations table populated after login)
        sections = executeSqliteQuery(
                "SELECT barcode FROM StorageLocations WHERE barcode IS NOT NULL AND barcode != '' ORDER BY barcode");
        // Validate results look like actual barcodes (STR-XXXX format)
        sections.removeIf(s -> !s.startsWith("STR-"));
        if (!sections.isEmpty()) {
            System.out.println("[DatabaseHelper] Got " + sections.size() + " section barcodes from device SQLite");
            return sections;
        }

        // Source 2: SQL Server
        sections = getSectionBarcodesFromSqlServer();
        if (!sections.isEmpty()) {
            System.out.println("[DatabaseHelper] Got " + sections.size() + " section barcodes from SQL Server");
            return sections;
        }

        // Source 3: Fallback
        sections = new ArrayList<>(Arrays.asList(AppConfig.FALLBACK_SECTION_BARCODES));
        System.out.println("[DatabaseHelper] Using " + sections.size() + " fallback section barcodes");
        return sections;
    }

    private List<String> getSectionBarcodesFromSqlServer() {
        List<String> sections = new ArrayList<>();
        // Use storeSections table — shelf column has numeric section IDs (e.g. 1033)
        // Prefix with "STR-" to match the barcode format the app expects
        String sql = String.format(
                "SELECT DISTINCT shelf FROM InventoryScanning.inv.storeSections " +
                        "WHERE store = %s AND shelf IS NOT NULL AND shelf != '' " +
                        "ORDER BY shelf",
                AppConfig.TEST_STORE);

        try (Connection conn = getSqlServerConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String shelf = rs.getString("shelf");
                if (shelf != null && !shelf.trim().isEmpty()) {
                    String barcode = "STR-" + shelf.trim();
                    sections.add(barcode);
                }
            }
        } catch (Exception e) {
            System.out.println("[DatabaseHelper] SQL Server storeSections query failed: " + e.getMessage());
        }
        return sections;
    }

    // ==================== PARTS-SPECIFIC QUERIES ====================

    /**
     * Get UPC codes filtered by product class (PC) for parts scanning.
     * Primary source: SQL Server barcodeMasterMultiUOM table.
     * Fallback: tires_upc file filtered by PC, device SQLite, hardcoded data.
     */
    public List<String> getTestUpcsByPc(String pc, int limit) {
        List<String> upcs = new ArrayList<>();

        // Source 1: SQL Server barcodeMasterMultiUOM (canonical source for parts UPCs)
        // Use CAST to handle both numeric and string Pc column types
        String sql = String.format(
                "SELECT TOP %d UPC FROM InventoryScanning.inv.barcodeMasterMultiUOM " +
                        "WHERE UPC IS NOT NULL AND UPC != '' AND CAST(Pc AS VARCHAR) = '%s'", limit, pc);
        try (Connection conn = getSqlServerConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String upc = rs.getString("UPC");
                if (upc != null && !upc.trim().isEmpty()) {
                    upcs.add(upc.trim());
                }
            }
            if (upcs.isEmpty()) {
                System.out.println("[DatabaseHelper] SQL query returned 0 UPCs for PC " + pc + " — checking if Pc column has matching data...");
                // Diagnostic: check what Pc values exist near this value
                String diagSql = "SELECT TOP 5 CAST(Pc AS VARCHAR) AS PcVal, COUNT(*) AS cnt " +
                        "FROM InventoryScanning.inv.barcodeMasterMultiUOM " +
                        "WHERE UPC IS NOT NULL AND UPC != '' " +
                        "GROUP BY CAST(Pc AS VARCHAR) ORDER BY cnt DESC";
                try (Statement diagStmt = conn.createStatement();
                     ResultSet diagRs = diagStmt.executeQuery(diagSql)) {
                    while (diagRs.next()) {
                        System.out.println("[DatabaseHelper]   Pc='" + diagRs.getString("PcVal") + "' count=" + diagRs.getInt("cnt"));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[DatabaseHelper] SQL Server barcodeMasterMultiUOM query failed: " + e.getMessage());
        }

        if (!upcs.isEmpty()) {
            System.out.println("[DatabaseHelper] Got " + upcs.size() + " UPCs for PC " + pc + " from barcodeMasterMultiUOM");
            return upcs;
        }

        // Source 2: tires_upc file filtered by PC
        List<Map<String, String>> items = loadUpcFile();
        if (items != null) {
            for (Map<String, String> item : items) {
                if (upcs.size() >= limit) break;
                String itemPc = item.get("pc");
                String upc = item.get("upc");
                if (upc != null && !upc.isEmpty() && pc.equals(itemPc)) {
                    upcs.add(upc);
                }
            }
            if (!upcs.isEmpty()) {
                System.out.println("[DatabaseHelper] Got " + upcs.size() + " UPCs for PC " + pc + " from file");
                return upcs;
            }
        }

        // Source 3: Device SQLite filtered by PC
        upcs = executeSqliteQuery(String.format(
                "SELECT Upc FROM BarcodeMasterList WHERE Upc IS NOT NULL AND Upc != '' AND Pc = '%s' LIMIT %d",
                pc, limit));
        if (!upcs.isEmpty()) {
            System.out.println("[DatabaseHelper] Got " + upcs.size() + " UPCs for PC " + pc + " from device SQLite");
            return upcs;
        }

        // Source 4: Fall back to any available UPCs
        return getFallbackUpcs(limit);
    }

    /**
     * Get UPC details (UPC|Item|Description|Size|Pc) filtered by product class.
     * Primary source: SQL Server barcodeMasterMultiUOM table.
     */
    public List<String> getTestUpcDetailsByPc(String pc, int limit) {
        List<String> details = new ArrayList<>();

        // Source 1: SQL Server barcodeMasterMultiUOM
        String sql = String.format(
                "SELECT TOP %d b.UPC, b.Item, i.Idesc, i.Isize, b.Pc " +
                        "FROM InventoryScanning.inv.barcodeMasterMultiUOM b " +
                        "LEFT JOIN TireMaxLive.dbo.Invmas i ON b.Item = i.Item " +
                        "WHERE b.UPC IS NOT NULL AND b.UPC != '' AND b.Pc = '%s'",
                limit, pc);
        try (Connection conn = getSqlServerConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String upc = rs.getString("UPC");
                String item = rs.getString("Item");
                String desc = rs.getString("Idesc");
                String size = rs.getString("Isize");
                details.add(String.format("%s|%s|%s|%s",
                        upc != null ? upc.trim() : "",
                        item != null ? item.trim() : "",
                        desc != null ? desc.trim() : "",
                        size != null ? size.trim() : ""));
            }
        } catch (Exception e) {
            System.out.println("[DatabaseHelper] SQL Server PC detail query failed: " + e.getMessage());
        }

        if (!details.isEmpty()) {
            System.out.println("[DatabaseHelper] Got " + details.size() + " UPC details for PC " + pc);
            return details;
        }

        // Fallback to file filtered by PC
        List<Map<String, String>> items = loadUpcFile();
        if (items != null) {
            for (Map<String, String> item : items) {
                if (details.size() >= limit) break;
                String itemPc = item.get("pc");
                String upc = item.get("upc");
                if (upc != null && !upc.isEmpty() && pc.equals(itemPc)) {
                    details.add(String.format("%s|%s|%s|%s",
                            upc,
                            item.getOrDefault("item", ""),
                            item.getOrDefault("description", ""),
                            item.getOrDefault("size", "")));
                }
            }
        }

        return details;
    }

    /**
     * Get a valid item number for a specific product class from barcodeMasterMultiUOM.
     */
    public String getValidItemNumberByPc(String pc) {
        // Try SQL Server first
        String sql = String.format(
                "SELECT TOP 1 Item FROM InventoryScanning.inv.barcodeMasterMultiUOM " +
                        "WHERE Item IS NOT NULL AND Item != '' AND Pc = '%s'", pc);
        try (Connection conn = getSqlServerConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String item = rs.getString("Item");
                if (item != null && !item.trim().isEmpty()) {
                    return item.trim();
                }
            }
        } catch (Exception e) {
            System.out.println("[DatabaseHelper] SQL Server item lookup failed: " + e.getMessage());
        }

        // Fallback to file
        List<Map<String, String>> items = loadUpcFile();
        if (items != null) {
            for (Map<String, String> item : items) {
                String itemPc = item.get("pc");
                String itemNum = item.get("item");
                if (pc.equals(itemPc) && itemNum != null && !itemNum.isEmpty()) {
                    return itemNum;
                }
            }
        }
        return getValidItemNumber();
    }

    // ==================== FALLBACK DATA ====================

    private List<String> getFallbackUpcs(int limit) {
        List<String> upcs = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, AppConfig.FALLBACK_TIRE_DATA.length); i++) {
            upcs.add(AppConfig.FALLBACK_TIRE_DATA[i][1]);
        }
        return upcs;
    }
}

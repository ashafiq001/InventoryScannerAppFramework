package com.mavis.scanner.utils;

import com.mavis.scanner.config.AppConfig;

import java.sql.*;
import java.util.*;

/**
 * Dynamically discovers or schedules inventories from the database.
 *
 * Mirrors the pre-fetch logic from ConcurrentScheduleThenCountTest:
 *   1. Query vw_inv_hdr for stores with "Not Started" inventory → store, invNum, invCode, PCs
 *   2. If SCHEDULE_IF_NEEDED, create scheduled_dates + call spBuildInventory
 *   3. Return store + invCode for the E2E test to use at login
 *
 * Configuration (system properties):
 *   -DSCHEDULE_IF_NEEDED=true   — schedule inventory if none exists for the store
 *   -DINVENTORY_PCS=2,62,65,66,86,92,95,188 — PCs to schedule (default: all 8)
 *   -DTEST_STORE=4063            — target a specific store (default: any available)
 */
public class InventorySetupHelper {

    private static final String SUPER_USER = "421628";

    /** All 8 real PC codes used in inventory scanning */
    private static final List<Integer> ALL_PCS = Arrays.asList(2, 62, 65, 66, 86, 92, 95, 188);

    /** Result of a scheduled inventory lookup */
    public static class ScheduledInventory {
        public final String store;
        public final String invNum;
        public final String invCode;
        public final List<Integer> scheduledPCs;

        public ScheduledInventory(String store, String invNum, String invCode, List<Integer> scheduledPCs) {
            this.store = store;
            this.invNum = invNum;
            this.invCode = invCode;
            this.scheduledPCs = scheduledPCs;
        }

        @Override
        public String toString() {
            return String.format("ScheduledInventory{store=%s, invNum=%s, invCode=%s, PCs=%s}",
                    store, invNum, invCode, scheduledPCs);
        }
    }

    /**
     * Main entry point: find or create a scheduled inventory for the E2E test.
     *
     * Logic (mirrors ConcurrentScheduleThenCountTest.prefetchInventoryData):
     *   1. Query vw_inv_hdr for "Not Started" inventory (optionally filtered by TEST_STORE)
     *   2. If none found and SCHEDULE_IF_NEEDED=true, schedule via spBuildInventory
     *   3. Fall back to AppConfig defaults if DB is unreachable
     *
     * @return ScheduledInventory with real store/invCode/PCs, or defaults from AppConfig
     */
    public static ScheduledInventory resolveInventory() {
        String targetStore = System.getProperty("TEST_STORE", AppConfig.TEST_STORE);
        boolean scheduleIfNeeded = Boolean.parseBoolean(
                System.getProperty("SCHEDULE_IF_NEEDED", "false"));
        List<Integer> pcsToSchedule = parseInventoryPCs();

        System.out.println("[InventorySetupHelper] Resolving inventory for store=" + targetStore +
                " scheduleIfNeeded=" + scheduleIfNeeded + " PCs=" + pcsToSchedule);

        // Step 1: Look for existing "Not Started" inventory
        ScheduledInventory inv = findNotStartedInventory(targetStore);
        if (inv != null) {
            System.out.println("[InventorySetupHelper] Found existing inventory: " + inv);
            return inv;
        }

        // Step 2: Try any store if specific store had nothing
        inv = findNotStartedInventory(null);
        if (inv != null) {
            System.out.println("[InventorySetupHelper] Found inventory on different store: " + inv);
            return inv;
        }

        // Step 3: Schedule if enabled
        if (scheduleIfNeeded) {
            System.out.println("[InventorySetupHelper] No 'Not Started' inventory found. Scheduling...");
            boolean scheduled = scheduleInventoryForStore(targetStore, pcsToSchedule);
            if (scheduled) {
                inv = findNotStartedInventory(targetStore);
                if (inv != null) {
                    System.out.println("[InventorySetupHelper] Scheduled and found: " + inv);
                    return inv;
                }
            }
        }

        // Step 4: Fall back to AppConfig defaults
        System.out.println("[InventorySetupHelper] Using AppConfig defaults: store=" +
                AppConfig.TEST_STORE + " invCode=" + AppConfig.TEST_INV_CODE);
        return new ScheduledInventory(
                AppConfig.TEST_STORE,
                null,
                AppConfig.TEST_INV_CODE,
                new ArrayList<>());
    }

    /**
     * Query vw_inv_hdr for a "Not Started" inventory.
     * Groups PCs by store+invNum+invCode (same as ConcurrentScheduleThenCountTest.findStoresWithNotStartedInventory).
     *
     * @param store specific store number, or null for any available store
     * @return ScheduledInventory or null if none found
     */
    public static ScheduledInventory findNotStartedInventory(String store) {
        String storeClause = (store != null) ? "AND h.store = " + store + " " : "";

        String sql = "SELECT h.store, h.InvNum, h.InvCode, h.pc " +
                "FROM InventoryScanning.inv.vw_inv_hdr h " +
                "WHERE h.STATUS = 'Not Started' " +
                storeClause +
                "ORDER BY h.store, h.InvNum, h.pc";

        Map<String, ScheduledInventory> grouped = new LinkedHashMap<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String st = String.valueOf(rs.getInt("store"));
                String invNum = String.valueOf(rs.getInt("InvNum"));
                String invCode = rs.getString("InvCode");
                int pc = rs.getInt("pc");

                String key = st + "|" + invNum;
                ScheduledInventory existing = grouped.get(key);
                if (existing == null) {
                    List<Integer> pcs = new ArrayList<>();
                    pcs.add(pc);
                    grouped.put(key, new ScheduledInventory(st, invNum,
                            invCode != null ? invCode.trim() : null, pcs));
                } else {
                    if (!existing.scheduledPCs.contains(pc)) {
                        existing.scheduledPCs.add(pc);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[InventorySetupHelper] vw_inv_hdr query failed: " + e.getMessage());
            return null;
        }

        // Return the first match (most PCs = better for testing)
        ScheduledInventory best = null;
        for (ScheduledInventory inv : grouped.values()) {
            if (best == null || inv.scheduledPCs.size() > best.scheduledPCs.size()) {
                best = inv;
            }
        }
        return best;
    }

    /**
     * Schedule inventory for a store via spBuildInventory.
     * Mirrors ConcurrentScheduleThenCountTest.scheduleInventoryForStore:
     *   1. Get or create scheduled_date_id per PC in inv.scheduled_dates
     *   2. Call spBuildInventory(store, employee, scheduleIdsCsv)
     *
     * @param store store number
     * @param pcs   product categories to schedule
     * @return true if scheduling succeeded
     */
    public static boolean scheduleInventoryForStore(String store, List<Integer> pcs) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(true);

            // Get or create a scheduled_date_id for EACH PC
            List<String> scheduleIds = new ArrayList<>();
            for (int pc : pcs) {
                int scheduledDateId = getOrCreateScheduledDateId(conn, store, pc);
                if (scheduledDateId > 0) {
                    scheduleIds.add(String.valueOf(scheduledDateId));
                } else {
                    System.err.println("[InventorySetupHelper] Could not get/create scheduled_date_id for store "
                            + store + " PC " + pc);
                }
            }

            if (scheduleIds.isEmpty()) {
                System.err.println("[InventorySetupHelper] No scheduled_date_ids created for store " + store);
                return false;
            }

            // Pass ALL schedule IDs comma-separated to spBuildInventory
            String scheduleIdsCsv = String.join(",", scheduleIds);
            System.out.println("[InventorySetupHelper] Calling spBuildInventory(store=" + store +
                    ", employee=" + SUPER_USER + ", scheduleIds=" + scheduleIdsCsv + ")");

            String spCall = "{CALL InventoryScanning.inv.spBuildInventory(?, ?, ?)}";
            try (CallableStatement cs = conn.prepareCall(spCall)) {
                cs.setInt(1, Integer.parseInt(store));
                cs.setInt(2, Integer.parseInt(SUPER_USER));
                cs.setString(3, scheduleIdsCsv);

                boolean hasResults = cs.execute();
                if (hasResults) {
                    try (ResultSet rs = cs.getResultSet()) {
                        if (rs.next()) {
                            int invNum = rs.getInt("InvNum");
                            int invCode = rs.getInt("invCode");
                            System.out.println("[InventorySetupHelper] spBuildInventory returned InvNum=" +
                                    invNum + ", InvCode=" + invCode + " (PCs: " + pcs + ")");
                        }
                    }
                }
            }

            Thread.sleep(100);

            // Verify the inventory was created
            String verify = "SELECT TOP 1 InvNum FROM InventoryScanning.inv.vw_inv_hdr " +
                    "WHERE store = ? AND STATUS = 'Not Started' ORDER BY DateScanStart DESC";
            try (PreparedStatement ps = conn.prepareStatement(verify)) {
                ps.setInt(1, Integer.parseInt(store));
                try (ResultSet rs = ps.executeQuery()) {
                    boolean found = rs.next();
                    if (found) {
                        System.out.println("[InventorySetupHelper] Verified: inventory created for store " + store);
                    } else {
                        System.err.println("[InventorySetupHelper] Verification failed: no 'Not Started' inventory after scheduling");
                    }
                    return found;
                }
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.contains("PK_Wtupdx_Work") || msg.contains("duplicate key")) {
                System.err.println("[InventorySetupHelper] Skipped — Wtupdx_Work PK conflict (known DB issue)");
            } else {
                System.err.println("[InventorySetupHelper] scheduleInventoryForStore failed: " + msg);
            }
            return false;
        }
    }

    /**
     * Get or create a scheduled_date_id for a store/PC combination.
     * Mirrors ConcurrentScheduleThenCountTest.getOrCreateScheduledDateId.
     */
    private static int getOrCreateScheduledDateId(Connection conn, String store, int pc) {
        // Try to find existing
        String findSql = "SELECT TOP 1 scheduled_date_id FROM InventoryScanning.inv.scheduled_dates " +
                "WHERE store_num = ? AND pc = ? AND date_scheduled >= CAST(GETDATE() AS DATE) " +
                "ORDER BY scheduled_date_id DESC";

        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setInt(1, Integer.parseInt(store));
            ps.setInt(2, pc);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("scheduled_date_id");
            }
        } catch (Exception e) { /* continue to create */ }

        // Create new
        String insertSql = "INSERT INTO InventoryScanning.inv.scheduled_dates (store_num, pc, date_scheduled) " +
                "OUTPUT INSERTED.scheduled_date_id VALUES (?, ?, CAST(GETDATE() AS DATE))";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, Integer.parseInt(store));
            ps.setInt(2, pc);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            System.err.println("[InventorySetupHelper] Failed to create scheduled_date for store "
                    + store + " PC " + pc + ": " + e.getMessage());
        }

        return -1;
    }

    /**
     * Report inventory status distribution (diagnostic helper).
     * Mirrors ConcurrentScheduleThenCountTest.reportInventoryStatusDistribution.
     */
    public static void reportInventoryStatusDistribution() {
        String sql = "SELECT STATUS, COUNT(DISTINCT store) as store_count " +
                "FROM InventoryScanning.inv.vw_inv_hdr " +
                "GROUP BY STATUS ORDER BY store_count DESC";

        System.out.println("\n=== INVENTORY STATUS DISTRIBUTION ===");
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                System.out.println("  " + rs.getString("STATUS") + ": " + rs.getInt("store_count") + " stores");
            }
        } catch (Exception e) {
            System.err.println("  Query failed: " + e.getMessage());
        }
        System.out.println("=====================================\n");
    }

    /**
     * Parse -DINVENTORY_PCS system property into a list of PC codes.
     * Defaults to all 8 PCs: 2,62,65,66,86,92,95,188
     */
    private static List<Integer> parseInventoryPCs() {
        String pcsProp = System.getProperty("INVENTORY_PCS");
        if (pcsProp == null || pcsProp.isEmpty() || pcsProp.startsWith("${")) {
            return new ArrayList<>(ALL_PCS);
        }

        List<Integer> pcs = new ArrayList<>();
        for (String pc : pcsProp.split(",")) {
            String trimmed = pc.trim();
            if (!trimmed.isEmpty()) {
                try {
                    pcs.add(Integer.parseInt(trimmed));
                } catch (NumberFormatException e) {
                    System.err.println("[InventorySetupHelper] Invalid PC code: " + trimmed);
                }
            }
        }
        return pcs.isEmpty() ? new ArrayList<>(ALL_PCS) : pcs;
    }

    private static Connection getConnection() throws Exception {
        String url = String.format(
                "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true;",
                AppConfig.DB_SERVER, AppConfig.DB_PORT, AppConfig.DB_INVENTORY);
        return DriverManager.getConnection(url, AppConfig.DB_USERNAME, AppConfig.DB_PASSWORD);
    }
}

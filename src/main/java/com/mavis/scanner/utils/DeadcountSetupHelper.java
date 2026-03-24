package com.mavis.scanner.utils;

import com.mavis.scanner.config.AppConfig;

import java.sql.*;

/**
 * Dynamically discovers or schedules deadcounts from the database.
 *
 * Mirrors the InventorySetupHelper pattern:
 *   1. Query deadcount_hdr for stores with StatusID=9 ("Not Started") deadcount
 *   2. If SCHEDULE_IF_NEEDED, create via spBuildDeadcount(store, empNum)
 *   3. Return store + invCode for the deadcount test to use at login
 *
 * Configuration (system properties):
 *   -DSCHEDULE_IF_NEEDED=true   — schedule deadcount if none exists for the store
 *   -DTEST_STORE=30             — target a specific store (default: resolved via StoreSelector)
 */
public class DeadcountSetupHelper {

    private static final String SUPER_USER = "421628";

    /** Result of a scheduled deadcount lookup */
    public static class ScheduledDeadcount {
        public final String store;
        public final String invNum;
        public final String invCode;

        public ScheduledDeadcount(String store, String invNum, String invCode) {
            this.store = store;
            this.invNum = invNum;
            this.invCode = invCode;
        }

        @Override
        public String toString() {
            return String.format("ScheduledDeadcount{store=%s, invNum=%s, invCode=%s}",
                    store, invNum, invCode);
        }
    }

    /**
     * Main entry point: find or create a scheduled deadcount for the test.
     *
     * Logic:
     *   1. Query deadcount_hdr for StatusID=9 ("Not Started") (optionally filtered by TEST_STORE)
     *   2. If none found and SCHEDULE_IF_NEEDED=true, schedule via spBuildDeadcount
     *   3. Fall back to system property defaults if DB is unreachable
     *
     * @return ScheduledDeadcount with real store/invCode, or null if nothing available
     */
    public static ScheduledDeadcount resolveDeadcount() {
        String targetStore = System.getProperty("TEST_STORE", AppConfig.TEST_STORE);;
        boolean scheduleIfNeeded = Boolean.parseBoolean(
                System.getProperty("SCHEDULE_IF_NEEDED", "false"));

        System.out.println("[DeadcountSetupHelper] Resolving deadcount for store=" + targetStore +
                " scheduleIfNeeded=" + scheduleIfNeeded);

        // Step 1: Look for existing "Not Started" deadcount for the target store
        ScheduledDeadcount dc = findNotStartedDeadcount(targetStore);
        if (dc != null) {
            System.out.println("[DeadcountSetupHelper] Found existing deadcount: " + dc);
            return dc;
        }

        // Step 2: Try any store if specific store had nothing
        dc = findNotStartedDeadcount(null);
        if (dc != null) {
            System.out.println("[DeadcountSetupHelper] Found deadcount on different store: " + dc);
            return dc;
        }

        // Step 3: Schedule if enabled
        if (scheduleIfNeeded) {
            System.out.println("[DeadcountSetupHelper] No 'Not Started' deadcount found. Scheduling...");
            boolean scheduled = scheduleDeadcountForStore(targetStore);
            if (scheduled) {
                dc = findNotStartedDeadcount(targetStore);
                if (dc != null) {
                    System.out.println("[DeadcountSetupHelper] Scheduled and found: " + dc);
                    return dc;
                }
            }
        }

        // Step 4: Fall back to system property defaults
        String fallbackCode = System.getProperty("DEADCOUNT_CODE", "");
        if (!fallbackCode.isEmpty()) {
            System.out.println("[DeadcountSetupHelper] Using DEADCOUNT_CODE fallback: " + fallbackCode);
            return new ScheduledDeadcount(targetStore, null, fallbackCode);
        }

        System.out.println("[DeadcountSetupHelper] No deadcount available and SCHEDULE_IF_NEEDED=false");
        return null;
    }

    /**
     * Query deadcount_hdr for a "Not Started" deadcount (StatusID=9).
     *
     * @param store specific store number, or null for any available store
     * @return ScheduledDeadcount or null if none found
     */
    public static ScheduledDeadcount findNotStartedDeadcount(String store) {
        String storeClause = (store != null) ? "AND h.store = " + store + " " : "";

        String sql = "SELECT TOP 1 h.store, h.inv_num, h.invCode " +
                "FROM InventoryScanning.inv.deadcount_hdr h " +
                "WHERE h.StatusID = 9 " +
                storeClause +
                "ORDER BY h.date_created DESC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                String st = String.valueOf(rs.getInt("store"));
                String invNum = String.valueOf(rs.getInt("inv_num"));
                String invCode = String.valueOf(rs.getInt("invCode"));
                return new ScheduledDeadcount(st, invNum, invCode);
            }
        } catch (Exception e) {
            System.out.println("[DeadcountSetupHelper] deadcount_hdr query failed: " + e.getMessage());
        }

        return null;
    }

    /**
     * Schedule a deadcount for a store via spBuildDeadcount.
     * The stored proc creates a deadcount_hdr + deadcountinventory snapshot.
     *
     * @param store store number
     * @return true if scheduling succeeded
     */
    public static boolean scheduleDeadcountForStore(String store) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(true);

            System.out.println("[DeadcountSetupHelper] Calling spBuildDeadcount(store=" + store +
                    ", empNum=" + SUPER_USER + ")");

            String spCall = "{CALL InventoryScanning.inv.spBuildDeadcount(?, ?)}";
            try (CallableStatement cs = conn.prepareCall(spCall)) {
                cs.setInt(1, Integer.parseInt(store));
                cs.setInt(2, Integer.parseInt(SUPER_USER));

                boolean hasResults = cs.execute();
                if (hasResults) {
                    try (ResultSet rs = cs.getResultSet()) {
                        if (rs.next()) {
                            int invNum = rs.getInt("InvNum");
                            int invCode = rs.getInt("invCode");
                            System.out.println("[DeadcountSetupHelper] spBuildDeadcount returned InvNum=" +
                                    invNum + ", InvCode=" + invCode);
                        }
                    }
                }
            }

            Thread.sleep(100);

            // Verify the deadcount was created
            String verify = "SELECT TOP 1 inv_num FROM InventoryScanning.inv.deadcount_hdr " +
                    "WHERE store = ? AND StatusID = 9 ORDER BY date_created DESC";
            try (PreparedStatement ps = conn.prepareStatement(verify)) {
                ps.setInt(1, Integer.parseInt(store));
                try (ResultSet rs = ps.executeQuery()) {
                    boolean found = rs.next();
                    if (found) {
                        System.out.println("[DeadcountSetupHelper] Verified: deadcount created for store " + store);
                    } else {
                        System.err.println("[DeadcountSetupHelper] Verification failed: no StatusID=9 deadcount after scheduling");
                    }
                    return found;
                }
            }
        } catch (Exception e) {
            System.err.println("[DeadcountSetupHelper] scheduleDeadcountForStore failed: " + e.getMessage());
            return false;
        }
    }

    private static Connection getConnection() throws Exception {
        String url = String.format(
                "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true;",
                AppConfig.DB_SERVER, AppConfig.DB_PORT, AppConfig.DB_INVENTORY);
        return DriverManager.getConnection(url, AppConfig.DB_USERNAME, AppConfig.DB_PASSWORD);
    }
}

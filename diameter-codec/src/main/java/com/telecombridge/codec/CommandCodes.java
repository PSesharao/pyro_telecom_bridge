package com.telecombridge.codec;

/**
 * Constants for Diameter command codes.
 */
public final class CommandCodes {

    private CommandCodes() {
        // Utility class — no instantiation
    }

    /** Capabilities-Exchange (CER/CEA) command code. */
    public static final int CAPABILITIES_EXCHANGE = 257;

    /** Credit-Control (CCR/CCA) command code. */
    public static final int CREDIT_CONTROL = 272;

    /** Device-Watchdog (DWR/DWA) command code. */
    public static final int DEVICE_WATCHDOG = 280;
}

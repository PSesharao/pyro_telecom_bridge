package com.telecombridge.codec;

/**
 * Constants for Diameter AVP codes as defined in RFC 6733 and RFC 4006.
 */
public final class AvpCodes {

    private AvpCodes() {
        // Utility class — no instantiation
    }

    // RFC 6733 Base Protocol AVPs
    public static final int SESSION_ID = 263;
    public static final int AUTH_APPLICATION_ID = 258;
    public static final int ORIGIN_HOST = 264;
    public static final int ORIGIN_REALM = 296;
    public static final int DESTINATION_REALM = 283;
    public static final int RESULT_CODE = 268;
    public static final int HOST_IP_ADDRESS = 257;
    public static final int VENDOR_ID = 266;
    public static final int PRODUCT_NAME = 269;
    public static final int ORIGIN_STATE_ID = 278;

    // RFC 4006 Credit-Control AVPs
    public static final int CC_REQUEST_TYPE = 416;
    public static final int CC_REQUEST_NUMBER = 415;
    public static final int SUBSCRIPTION_ID = 443;
    public static final int SUBSCRIPTION_ID_TYPE = 450;
    public static final int SUBSCRIPTION_ID_DATA = 444;
    public static final int SERVICE_IDENTIFIER = 439;
    public static final int CC_TIME = 420;
    public static final int CC_TOTAL_OCTETS = 421;
    public static final int CC_SERVICE_SPECIFIC_UNITS = 417;
    public static final int GRANTED_SERVICE_UNIT = 431;
}

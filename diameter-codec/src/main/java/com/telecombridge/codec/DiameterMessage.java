package com.telecombridge.codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Represents a complete Diameter message consisting of a header and a list of AVPs.
 */
public class DiameterMessage {

    private DiameterHeader header;
    private final List<Avp> avps;

    /**
     * Creates a new DiameterMessage with the given header and AVP list.
     *
     * @param header the Diameter message header
     * @param avps   the list of AVPs in this message
     */
    public DiameterMessage(DiameterHeader header, List<Avp> avps) {
        this.header = header;
        this.avps = new ArrayList<>(avps);
    }

    /**
     * Creates a new DiameterMessage with the given header and an empty AVP list.
     *
     * @param header the Diameter message header
     */
    public DiameterMessage(DiameterHeader header) {
        this(header, new ArrayList<>());
    }

    /**
     * Returns the message header.
     */
    public DiameterHeader getHeader() {
        return header;
    }

    /**
     * Sets the message header.
     *
     * @param header the new header
     */
    public void setHeader(DiameterHeader header) {
        this.header = header;
    }

    /**
     * Returns an unmodifiable view of the AVP list.
     */
    public List<Avp> getAvps() {
        return Collections.unmodifiableList(avps);
    }

    /**
     * Adds an AVP to this message.
     *
     * @param avp the AVP to add
     */
    public void addAvp(Avp avp) {
        avps.add(avp);
    }

    /**
     * Returns true if this message has the Request flag set.
     */
    public boolean isRequest() {
        return (header.commandFlags() & DiameterHeader.FLAG_REQUEST) != 0;
    }

    /**
     * Returns true if this message has the Proxiable flag set.
     */
    public boolean isProxiable() {
        return (header.commandFlags() & DiameterHeader.FLAG_PROXIABLE) != 0;
    }

    /**
     * Finds the first AVP with the given code.
     *
     * @param code the AVP code to search for
     * @return an Optional containing the first matching AVP, or empty if not found
     */
    public Optional<Avp> findAvp(int code) {
        for (Avp avp : avps) {
            if (avp.getCode() == code) {
                return Optional.of(avp);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds all AVPs with the given code.
     *
     * @param code the AVP code to search for
     * @return a list of all matching AVPs (may be empty)
     */
    public List<Avp> findAllAvps(int code) {
        List<Avp> result = new ArrayList<>();
        for (Avp avp : avps) {
            if (avp.getCode() == code) {
                result.add(avp);
            }
        }
        return result;
    }
}

package net.teamaof.skylorebosses.bosses.overhead.encounter;

/**
 * ONLINE: feeds the shield, takes integrity damage. OFFLINE: destroyed, inert until a re-arm picks it.
 * REBUILDING: regenerating integrity during P4; damage pushes it back, reaching full integrity brings it ONLINE.
 */
public enum PylonState {
    ONLINE, OFFLINE, REBUILDING;

    public static PylonState byOrdinal(int i) {
        return i >= 0 && i < values().length ? values()[i] : ONLINE;
    }
}

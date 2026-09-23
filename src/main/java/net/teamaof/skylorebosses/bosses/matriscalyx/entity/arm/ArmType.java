package net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm;

/** The six appendages. Satellite order is the hexagon order used by the arena (N, NE, SE, S, SW, NW). */
public enum ArmType {
    NERVE("nerve_arm", 120, 3.0f, 16f, 2.5f, 0),
    GRASPING("grasping_arm", 150, 3.5f, 22f, 3.0f, 1),
    SPITTING("spitting_arm", 140, 4.0f, 16f, 3.0f, 2),
    SLAM("slam_arm", 180, 5.0f, 18f, 3.0f, 3),
    CHARGING("charging_arm", 150, 5.0f, 5.5f, 2.5f, 4),
    MOUTH("mouth_arm", 150, 4.0f, 18f, 3.0f, 5);

    public final String model;
    public final float maxHp;
    public final float width;
    public final float height;
    /** Renderer scale applied to the Blockbench model (1 px = 1/16 block * scale). */
    public final float renderScale;
    public final int slot;

    ArmType(String model, float maxHp, float width, float height, float renderScale, int slot) {
        this.model = model;
        this.maxHp = maxHp;
        this.width = width;
        this.height = height;
        this.renderScale = renderScale;
        this.slot = slot;
    }

    public String soundKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static ArmType bySlot(int slot) {
        for (ArmType t : values()) if (t.slot == slot) return t;
        throw new IllegalArgumentException("slot " + slot);
    }
}

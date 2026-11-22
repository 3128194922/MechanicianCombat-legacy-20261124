package com.mechaniciancombat;

/** Timings and motion curve authored for the r611 Fortress skill. */
public final class FortressSkillRules {
    public static final int LOAD_TICKS = 19;
    public static final int READY_TICKS = 9;
    public static final int GATHER_TICKS = 41;
    public static final int HIT_TICKS = 46;
    public static final int COOLDOWN_TICKS = 600;
    public static final int RETREAT_TICKS = 28;

    public static int chargeLevel(long elapsed) {
        return elapsed >= 30 ? 3 : elapsed >= 20 ? 2 : elapsed >= 10 ? 1 : 0;
    }

    public static float damage(int level) {
        return 10.0F * (float) Math.pow(1.5D, Math.max(1, Math.min(3, level)) - 1);
    }

    public static double retreatDistance(long ticks) {
        int count = (int) Math.max(0L, Math.min(RETREAT_TICKS, ticks));
        double total = 0, travelled = 0;
        for (int i = 0; i < RETREAT_TICKS; i++) {
            double midpoint = i + 0.5D;
            double weight = midpoint <= 12.5D
                    ? Math.sin(midpoint / 12.5D * Math.PI * 0.5D)
                    : 0.5D * (1.0D + Math.cos((midpoint - 12.5D) / 15.5D * Math.PI));
            total += weight;
            if (i < count) travelled += weight;
        }
        return 5.0D * travelled / total;
    }

    private FortressSkillRules() { }
}

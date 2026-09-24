package net.teamaof.skylorebosses.bosses.overhead.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Ground and air telegraph drawing. Uses the long-range particle path so a marker 60 blocks away across the
 * yard still shows (vanilla culls normal server particles at 32 blocks).
 */
public final class Telegraph {
    private Telegraph() {}

    public static void point(ServerLevel level, ParticleOptions type, Vec3 p, int count, double spread) {
        for (ServerPlayer pl : level.players()) {
            if (pl.distanceToSqr(p) < 128 * 128) level.sendParticles(pl, type, true, p.x, p.y, p.z, count, spread, spread * 0.3, spread, 0);
        }
    }

    public static void ring(ServerLevel level, ParticleOptions type, Vec3 c, double r, int n) {
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            point(level, type, c.add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0);
        }
        point(level, type, c.add(0, 0.15, 0), 1, 0);
    }

    public static void line(ServerLevel level, ParticleOptions type, Vec3 a, Vec3 b, double step) {
        double len = a.distanceTo(b);
        int n = Math.max(1, (int) (len / step));
        for (int i = 0; i <= n; i++) point(level, type, a.lerp(b, i / (double) n), 1, 0);
    }
}

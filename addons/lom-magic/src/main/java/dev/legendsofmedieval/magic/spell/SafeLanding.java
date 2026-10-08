package dev.legendsofmedieval.magic.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Conservative landing search. All destinations are verified before moving the player. */
public final class SafeLanding {
    private SafeLanding() {}

    public static Optional<BlockPos> find(ServerLevel level, BlockPos death) {
        // Prefer positions 30-50 away. Only if none exist, search 51-96.
        Optional<BlockPos> first = ring(level, death, 30, 50);
        return first.isPresent() ? first : ring(level, death, 51, 96);
    }
    private static Optional<BlockPos> ring(ServerLevel level, BlockPos death, int min, int max) {
        for (int radius = min; radius <= max; radius += 4) {
            for (int degrees = 0; degrees < 360; degrees += 15) {
                int x = death.getX() + (int)Math.round(Math.cos(Math.toRadians(degrees)) * radius);
                int z = death.getZ() + (int)Math.round(Math.sin(Math.toRadians(degrees)) * radius);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                // Check surface and nearby caves (within a limited vertical distance).
                for (int dy = -12; dy <= 12; dy++) {
                    int y = death.getY() + dy;
                    if (y < level.getMinBuildHeight() + 2 || y > level.getMaxBuildHeight() - 3) continue;
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (safe(level, candidate)) return Optional.of(candidate);
                }
                BlockPos surface = new BlockPos(x, top, z);
                if (safe(level, surface)) return Optional.of(surface);
            }
        }
        return Optional.empty();
    }
    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (!level.getWorldBorder().isWithinBounds(feet)) return false;
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return false;
        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return false;
        BlockPos ground = feet.below();
        if (!level.getFluidState(ground).isEmpty()) return false;
        if (!level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)) return false;
        if (level.getBlockState(ground).is(Blocks.MAGMA_BLOCK) ||
                level.getBlockState(ground).is(Blocks.CACTUS) ||
                level.getBlockState(ground).is(Blocks.CAMPFIRE) ||
                level.getBlockState(ground).is(Blocks.SOUL_CAMPFIRE)) return false;
        // Reject intersecting colliders and unsafe environment.
        return level.noCollision(new AABB(feet.getX()+.2, feet.getY(), feet.getZ()+.2,
                feet.getX()+.8, feet.getY()+1.8, feet.getZ()+.8));
    }

    public static Optional<BlockPos> createEmergencyPlatform(ServerLevel level, BlockPos death) {
        // Strictly limited to a non-colliding 3x3 air volume above a lava sea.
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, death.getX(), death.getZ());
        int feetY = Math.max(death.getY() + 3, top + 3);
        if (feetY > level.getMaxBuildHeight() - 3 || feetY <= level.getMinBuildHeight()+2)
            return Optional.empty();
        BlockPos feet = new BlockPos(death.getX(), feetY, death.getZ());
        if (!level.getWorldBorder().isWithinBounds(feet)) return Optional.empty();
        if (!level.getFluidState(new BlockPos(death.getX(), top-1, death.getZ())).is(net.minecraft.tags.FluidTags.LAVA))
            return Optional.empty();
        for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
            BlockPos floor = feet.offset(dx,-1,dz);
            if (!level.getBlockState(floor).isAir() ||
                    !level.getBlockState(floor.above()).isAir() ||
                    !level.getBlockState(floor.above(2)).isAir()) return Optional.empty();
        }
        java.util.List<BlockPos> blocks = new java.util.ArrayList<>();
        for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
            BlockPos floor = feet.offset(dx,-1,dz);
            level.setBlockAndUpdate(floor, Blocks.OBSIDIAN.defaultBlockState());
            blocks.add(floor.immutable());
        }
        TemporaryPlatforms.register(level, blocks, 1200);
        return Optional.of(feet);
    }
}

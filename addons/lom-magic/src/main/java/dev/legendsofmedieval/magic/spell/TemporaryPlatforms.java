package dev.legendsofmedieval.magic.spell;

import dev.legendsofmedieval.magic.LoMMagic;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid=LoMMagic.MOD_ID, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class TemporaryPlatforms {
    private record Platform(ResourceKey<Level> dimension, List<BlockPos> blocks, long expireAt) {}
    private static final List<Platform> ACTIVE = new ArrayList<>();
    private TemporaryPlatforms() {}
    public static void register(ServerLevel level, List<BlockPos> blocks, long lifetimeTicks) {
        ACTIVE.add(new Platform(level.dimension(), List.copyOf(blocks), level.getGameTime() + lifetimeTicks));
    }
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Iterator<Platform> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Platform platform = iterator.next();
            ServerLevel level = server.getLevel(platform.dimension());
            if (level == null || level.getGameTime() < platform.expireAt()) continue;
            for (BlockPos pos : platform.blocks()) {
                // Never delete a player-replaced block.
                if (level.getBlockState(pos).is(Blocks.OBSIDIAN)) level.removeBlock(pos, false);
            }
            iterator.remove();
        }
    }
}

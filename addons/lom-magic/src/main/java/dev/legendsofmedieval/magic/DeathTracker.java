package dev.legendsofmedieval.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LoMMagic.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeathTracker {
    private static final String KEY = "LoMMagicLastDeath";
    private DeathTracker() {}
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag death = new CompoundTag();
        death.putString("dimension", player.serverLevel().dimension().location().toString());
        death.putDouble("x", player.getX());
        death.putDouble("y", player.getY());
        death.putDouble("z", player.getZ());
        death.putLong("time", player.serverLevel().getGameTime());
        player.getPersistentData().put(KEY, death);
    }
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(KEY, 10)) {
            event.getEntity().getPersistentData().put(KEY, old.getCompound(KEY).copy());
        }
    }
    public static CompoundTag getLastDeath(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        return tag.contains(KEY, 10) ? tag.getCompound(KEY) : null;
    }
}

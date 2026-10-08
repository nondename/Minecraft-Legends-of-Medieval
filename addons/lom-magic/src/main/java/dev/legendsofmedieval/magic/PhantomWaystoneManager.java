package dev.legendsofmedieval.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.menu.BalmMenuProvider;
import net.blay09.mods.waystones.api.WaystoneTeleportEvent;
import net.blay09.mods.waystones.core.WarpMode;
import net.blay09.mods.waystones.menu.WaystoneSelectionMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Development-only spectral anchor lifecycle.
 * Does not register a real Waystone and does not bypass Waystones permissions.
 * TODO: replace invisible marker with a translucent spectral obelisk renderer.
 * Waystones' native selection GUI is used; teleports are checked on the server.
 */
@Mod.EventBusSubscriber(modid = LoMMagic.MOD_ID)
public final class PhantomWaystoneManager {
    private static final int LIFETIME = 90 * 20;
    private static final Map<UUID, Anchor> ACTIVE = new HashMap<>();
    private static final Map<UUID, Anchor> OPEN_MENUS = new HashMap<>();

    public static void registerWaystonesHooks() {
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, PhantomWaystoneManager::onTeleport);
    }

    private static void onTeleport(WaystoneTeleportEvent.Pre event) {
        if (!(event.getContext().getEntity() instanceof ServerPlayer player)) return;
        Anchor anchor = OPEN_MENUS.get(player.getUUID());
        if (anchor == null) return;
        ServerLevel source = player.serverLevel();
        var marker = source.getEntity(anchor.entityId);
        if (!(player.containerMenu instanceof WaystoneSelectionMenu)
                || marker == null || player.distanceToSqr(marker) > 64
                || !ACTIVE.containsKey(anchor.owner)
                || !ACTIVE.get(anchor.owner).equals(anchor)
                || !player.serverLevel().dimension().location().equals(anchor.dimension)
                || player.serverLevel().getGameTime() >= anchor.expiresAt
                || !allowed(event.getDestination().getLevel(), anchor.tier)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.lommagic.phantom_waystone.dimension"), true);
            OPEN_MENUS.remove(player.getUUID());
            return;
        }
        event.setXpCost(0);
        event.setCooldown(0);
        OPEN_MENUS.remove(player.getUUID());
    }

    private record Anchor(UUID owner, UUID entityId, ResourceLocation dimension, long expiresAt, int tier) {}

    private PhantomWaystoneManager() {}

    public static boolean summon(ServerPlayer player, int tier) {
        if (tier < 1 || tier > 3 || !allowed(player.serverLevel(), tier)) {
            player.displayClientMessage(Component.translatable("message.lommagic.phantom_waystone.dimension"), true);
            return false;
        }
        Anchor old = ACTIVE.remove(player.getUUID());
        if (old != null) remove(old, player.server);
        ServerLevel level = player.serverLevel();
        ArmorStand marker = new ArmorStand(level, player.getX(), player.getY(), player.getZ());
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setInvisible(true);
        marker.setCustomName(Component.translatable("spell.lommagic.phantom_waystone"));
        marker.setCustomNameVisible(false);
        marker.getPersistentData().putBoolean("LoMPhantomWaystone", true);
        marker.getPersistentData().putUUID("LoMOwner", player.getUUID());
        marker.getPersistentData().putInt("LoMTier", tier);
        if (!level.addFreshEntity(marker)) return false;
        OPEN_MENUS.entrySet().removeIf(e -> e.getValue().owner.equals(player.getUUID()));
        ACTIVE.put(player.getUUID(), new Anchor(player.getUUID(), marker.getUUID(),
                level.dimension().location(), level.getGameTime() + LIFETIME, tier));
        player.displayClientMessage(Component.translatable("message.lommagic.phantom_waystone.summoned"), true);
        return true;
    }

    private static boolean allowed(ServerLevel level, int tier) {
        ResourceLocation id = level.dimension().location();
        return tier == 3 || id.equals(Level.OVERWORLD.location())
                || (tier >= 2 && id.equals(Level.NETHER.location()));
    }

    private static void remove(Anchor anchor, net.minecraft.server.MinecraftServer server) {
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, anchor.dimension));
        if (level != null) {
            var entity = level.getEntity(anchor.entityId);
            if (entity != null) entity.discard();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        Iterator<Map.Entry<UUID, Anchor>> it = ACTIVE.entrySet().iterator();
        OPEN_MENUS.entrySet().removeIf(entry -> {
            ServerPlayer user = level.getServer().getPlayerList().getPlayer(entry.getKey());
            return user == null || !(user.containerMenu instanceof WaystoneSelectionMenu);
        });
        while (it.hasNext()) {
            Anchor anchor = it.next().getValue();
            if (!anchor.dimension.equals(level.dimension().location())) continue;
            var entity = level.getEntity(anchor.entityId);
            if (level.getGameTime() >= anchor.expiresAt
                    || entity == null || level.getServer().getPlayerList().getPlayer(anchor.owner) == null) {
                if (entity != null) entity.discard();
                OPEN_MENUS.entrySet().removeIf(e -> e.getValue().equals(anchor));
                it.remove();
                continue;
            }
            if (level.getGameTime() % 5 == 0) {
                var particle = anchor.tier == 1 ? ParticleTypes.SOUL_FIRE_FLAME
                        : anchor.tier == 2 ? ParticleTypes.FLAME : ParticleTypes.PORTAL;
                level.sendParticles(particle, entity.getX(), entity.getY() + 1.2, entity.getZ(),
                        12, .45, .8, .45, .02);
            }
        }
    }

    private static final BalmMenuProvider WAYSTONE_MENU = new BalmMenuProvider() {
        @Override public Component getDisplayName() {
            return Component.translatable("spell.lommagic.phantom_waystone");
        }
        @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return WaystoneSelectionMenu.createWaystoneSelection(id, player, WarpMode.WARP_STONE, null);
        }
        @Override public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
            buf.writeByte(WarpMode.WARP_STONE.ordinal());
        }
    };

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ArmorStand marker)
                || !marker.getPersistentData().getBoolean("LoMPhantomWaystone")
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        event.setCanceled(true);
        Anchor anchor = ACTIVE.values().stream().filter(a -> a.entityId.equals(marker.getUUID())).findFirst().orElse(null);
        if (anchor == null || player.distanceToSqr(marker) > 64
                || player.serverLevel().getGameTime() >= anchor.expiresAt) return;
        if (!allowed(player.serverLevel(), anchor.tier)) return;
        OPEN_MENUS.put(player.getUUID(), anchor);
        Balm.getNetworking().openGui(player, WAYSTONE_MENU);
    }
}

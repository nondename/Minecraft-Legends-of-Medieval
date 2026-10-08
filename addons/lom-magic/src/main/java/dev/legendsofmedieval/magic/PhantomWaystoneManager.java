package dev.legendsofmedieval.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
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
        BlockPos spawn = findPlacement(player);
        if (spawn == null) {
            player.displayClientMessage(Component.translatable("message.lommagic.phantom_waystone.no_surface"), true);
            return false;
        }
        ArmorStand marker = new ArmorStand(level, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setInvisible(true);
        marker.setCustomName(Component.literal("LoMPhantomWaystone:" + tier));
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

    private static BlockPos findPlacement(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        HitResult hit = player.pick(6.0, 0, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos clicked = block.getBlockPos().relative(block.getDirection());
            if (canPlace(level, clicked) && clicked.distSqr(player.blockPosition()) > 3) return clicked;
        }
        Vec3 direction = player.getLookAngle();
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
        if (horizontal.lengthSqr() < 0.02) horizontal = new Vec3(0, 0, 1);
        horizontal = horizontal.normalize();
        for (int distance = 4; distance <= 5; distance++) {
            BlockPos center = BlockPos.containing(player.position().add(horizontal.scale(distance)));
            for (int dy = 3; dy >= -5; dy--) {
                BlockPos pos = center.offset(0,dy,0);
                if (canPlace(level,pos)) return pos;
            }
        }
        return null;
    }

    private static boolean canPlace(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY()+3 >= level.getMaxBuildHeight()) return false;
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) return false;
        return level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                && level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
                && level.getBlockState(pos.above(2)).getCollisionShape(level,pos.above(2)).isEmpty()
                && level.getFluidState(pos).isEmpty();
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
            if (level.getGameTime() % 3 == 0) {
                renderSpectralOutline(level, entity.getX(), entity.getY(), entity.getZ(), anchor.tier);
            }
        }
    }

    /**
     * Three-dimensional, transparent particle silhouette until the final
     * translucent Waystone mesh is available. Never places persistent blocks.
     */
    private static void renderSpectralOutline(ServerLevel level, double x, double y, double z, int tier) {
        Vector3f color = tier == 1 ? new Vector3f(0.65f, 0.88f, 1.0f)
                : tier == 2 ? new Vector3f(1.0f, 0.30f, 0.08f)
                : new Vector3f(0.64f, 0.20f, 1.0f);
        DustParticleOptions dust = new DustParticleOptions(color, 1.1f);
        long frame = level.getGameTime();
        for (int layer = 0; layer <= 12; layer++) {
            double height = 0.15 + layer * 0.18;
            double radius = layer < 2 ? 0.46 : layer > 10 ? 0.24 : 0.34;
            for (int corner = 0; corner < 4; corner++) {
                double angle = Math.PI * 0.5 * corner + Math.PI * 0.25;
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;
                // Stagger emission to keep the beacon visible without flooding clients.
                if ((layer + corner + frame / 3) % 3 != 0) continue;
                level.sendParticles(dust, px, y + height, pz, 1, 0, 0, 0, 0);
            }
        }
        double spin = (frame % 160) * Math.PI / 80.0;
        for (int i = 0; i < 8; i++) {
            double angle = spin + i * Math.PI / 4;
            if (i % 2 == 0) level.sendParticles(dust, x + Math.cos(angle) * 0.68, y + 0.08,
                    z + Math.sin(angle) * 0.68, 1, 0, 0, 0, 0);
        }
        if (frame % 12 == 0) {
            level.sendParticles(tier == 2 ? ParticleTypes.FLAME
                            : tier == 3 ? ParticleTypes.PORTAL : ParticleTypes.SOUL,
                    x, y + 1.2, z, 5, .34, .9, .34, .02);
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
    public static void interactAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof ArmorStand stand
                && stand.getPersistentData().getBoolean("LoMPhantomWaystone")) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) open(player, stand);
        }
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof ArmorStand stand
                && stand.getPersistentData().getBoolean("LoMPhantomWaystone")) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) open(player, stand);
        }
    }

    private static void open(ServerPlayer player, ArmorStand marker) {
        Anchor anchor = ACTIVE.values().stream().filter(a -> a.entityId.equals(marker.getUUID())).findFirst().orElse(null);
        if (anchor == null || player.distanceToSqr(marker) > 64
                || player.serverLevel().getGameTime() >= anchor.expiresAt) return;
        if (!allowed(player.serverLevel(), anchor.tier)) return;
        OPEN_MENUS.put(player.getUUID(), anchor);
        Balm.getNetworking().openGui(player, WAYSTONE_MENU);
    }
}

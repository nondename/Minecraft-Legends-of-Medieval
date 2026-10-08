package dev.legendsofmedieval.magic.spell;

import dev.legendsofmedieval.magic.DeathTracker;
import dev.legendsofmedieval.magic.LoMMagic;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

@AutoSpellConfig
public class SoulRecallSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(LoMMagic.MOD_ID, "soul_recall");
    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(900)
            .build();

    public SoulRecallSpell() {
        this.baseManaCost = 250;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.castTime = 100;
    }
    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return config; }
    @Override public CastType getCastType() { return CastType.LONG; }
    // Use server-broadcast sound packets so the caster and nearby players hear the channel.
    @Override
    public void onServerPreCast(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        if (!level.isClientSide) {
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.7f);
        }
    }

    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity caster, MagicData magicData) {
        if (!level.isClientSide && caster.tickCount % 16 == 0) {
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.9f, 0.8f);
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 0.75f + (caster.tickCount % 80) / 150f);
        }
    }
    @Override public Optional<SoundEvent> getCastFinishSound() { return Optional.of(SoundEvents.ENDERMAN_TELEPORT); }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource source, MagicData magicData) {
        if (!(caster instanceof ServerPlayer player)) return;
        CompoundTag death = DeathTracker.getLastDeath(player);
        if (death == null) {
            player.displayClientMessage(Component.literal("Soul Recall: no previous death recorded."), true);
            return;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(death.getString("dimension"));
        if (dimensionId == null) return;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel targetLevel = player.server.getLevel(dimension);
        if (targetLevel == null) {
            player.displayClientMessage(Component.literal("Soul Recall: death dimension is unavailable."), true);
            return;
        }

        BlockPos deathPos = BlockPos.containing(death.getDouble("x"), death.getDouble("y"), death.getDouble("z"));
        // Load only a bounded region. Searching never changes blocks.
        Optional<BlockPos> destination = SafeLanding.find(targetLevel, deathPos);
        boolean platform = false;
        if (destination.isEmpty()) {
            destination = SafeLanding.createEmergencyPlatform(targetLevel, deathPos);
            platform = destination.isPresent();
        }
        if (destination.isEmpty()) {
            player.displayClientMessage(Component.literal("Soul Recall: no safe destination."), true);
            return;
        }
        BlockPos arrival = destination.get();
        // Play at the departure position while the client is still tracking that level.
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.75f);
        if (player.isPassenger()) player.stopRiding();
        player.teleportTo(targetLevel, arrival.getX() + .5, arrival.getY(),
                arrival.getZ() + .5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        // Also play at the destination after a possible dimension change.
        targetLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.15f);
        if (platform) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false));
        }
        player.displayClientMessage(Component.literal(platform ?
                "Soul Recall: temporary obsidian platform (60 seconds)." :
                "Soul Recall: returned near your last death."), true);
        super.onCast(level, spellLevel, caster, source, magicData);
    }
}

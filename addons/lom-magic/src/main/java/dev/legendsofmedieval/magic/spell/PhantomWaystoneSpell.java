package dev.legendsofmedieval.magic.spell;

import dev.legendsofmedieval.magic.LoMMagic;
import dev.legendsofmedieval.magic.PhantomWaystoneManager;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

@AutoSpellConfig
public class PhantomWaystoneSpell extends AbstractSpell {
    private final ResourceLocation spellId = new ResourceLocation(LoMMagic.MOD_ID, "phantom_waystone");
    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(300)
            .build();

    public PhantomWaystoneSpell() {
        this.baseManaCost = 150;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.castTime = 80;
    }

    @Override public ResourceLocation getSpellResource() { return spellId; }
    @Override public DefaultConfig getDefaultConfig() { return config; }
    @Override public CastType getCastType() { return CastType.LONG; }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster,
                       CastSource source, MagicData magicData) {
        if (!(caster instanceof ServerPlayer player)) return;
        if (PhantomWaystoneManager.summon(player, spellLevel)) {
            super.onCast(level, spellLevel, caster, source, magicData);
        }
    }
}

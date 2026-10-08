package dev.legendsofmedieval.magic;

import dev.legendsofmedieval.magic.spell.SoulRecallSpell;
import dev.legendsofmedieval.magic.spell.PhantomWaystoneSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(LoMMagic.MOD_ID)
public final class LoMMagic {
    public static final String MOD_ID = "lommagic";
    private static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, MOD_ID);
    public static final RegistryObject<AbstractSpell> SOUL_RECALL =
            SPELLS.register("soul_recall", SoulRecallSpell::new);
    public static final RegistryObject<AbstractSpell> PHANTOM_WAYSTONE =
            SPELLS.register("phantom_waystone", PhantomWaystoneSpell::new);
    public LoMMagic() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        SPELLS.register(modBus);
    }
}

package com.ssscript.taczfixes;

import com.ssscript.taczfixes.data.TaczFixesDataHandler;
import com.ssscript.taczfixes.enchantment.AnnihilationEnchantment;
import com.ssscript.taczfixes.enchantment.AntiGravityEnchantment;
import com.ssscript.taczfixes.enchantment.ChainExplosionEnchantment;
import com.ssscript.taczfixes.enchantment.ChargeEnchantment;
import com.ssscript.taczfixes.enchantment.CollectorEnchantment;
import com.ssscript.taczfixes.enchantment.DecapitationEnchantment;
import com.ssscript.taczfixes.enchantment.DeepLearningEnchantment;
import com.ssscript.taczfixes.enchantment.ElectromagneticCoilEnchantment;
import com.ssscript.taczfixes.enchantment.EqualizerEnchantment;
import com.ssscript.taczfixes.enchantment.ExplosionExpertEnchantment;
import com.ssscript.taczfixes.enchantment.LifeLeechEnchantment;
import com.ssscript.taczfixes.enchantment.NeurotoxinEnchantment;
import com.ssscript.taczfixes.enchantment.OverloadEnchantment;
import com.ssscript.taczfixes.enchantment.PandoraParadoxEnchantment;
import com.ssscript.taczfixes.enchantment.PreemptiveStrikeEnchantment;
import com.ssscript.taczfixes.enchantment.RandomEnchantment;
import com.ssscript.taczfixes.enchantment.SmartScopeEnchantment;
import com.ssscript.taczfixes.enchantment.SniperEliteEnchantment;
import com.ssscript.taczfixes.enchantment.StabilityEnchantment;
import com.ssscript.taczfixes.enchantment.StandardAmmoEnchantment;
import com.ssscript.taczfixes.handler.GunAnvilHandler;
import com.ssscript.taczfixes.handler.GunEnchantmentHandler;
import com.ssscript.taczfixes.handler.GunLevelHandler;
import com.ssscript.taczfixes.handler.LimbDamageHandler;
import com.ssscript.taczfixes.handler.SpreadRampHandler;
import com.ssscript.taczfixes.handler.SteplessZoomHandler;
import com.ssscript.taczfixes.network.NetworkHandler;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TaczFixesMod.MOD_ID)
public class TaczFixesMod {
    public static final String MOD_ID = "taczfixes";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MOD_ID);
    public static final RegistryObject<Enchantment> OVERLOAD_ENCHANTMENT =
            ENCHANTMENTS.register("overload", OverloadEnchantment::new);
    public static final RegistryObject<Enchantment> ANNIHILATION_ENCHANTMENT =
            ENCHANTMENTS.register("annihilation", AnnihilationEnchantment::new);
    public static final RegistryObject<Enchantment> STABILITY_ENCHANTMENT =
            ENCHANTMENTS.register("stability", StabilityEnchantment::new);
    public static final RegistryObject<Enchantment> ANTI_GRAVITY_ENCHANTMENT =
            ENCHANTMENTS.register("anti_gravity", AntiGravityEnchantment::new);
    public static final RegistryObject<Enchantment> ELECTROMAGNETIC_COIL_ENCHANTMENT =
            ENCHANTMENTS.register("electromagnetic_coil", ElectromagneticCoilEnchantment::new);
    public static final RegistryObject<Enchantment> STANDARD_AMMO_ENCHANTMENT =
            ENCHANTMENTS.register("standard_ammo", StandardAmmoEnchantment::new);
    public static final RegistryObject<Enchantment> NEUROTOXIN_ENCHANTMENT =
            ENCHANTMENTS.register("neurotoxin", NeurotoxinEnchantment::new);
    public static final RegistryObject<Enchantment> CHAIN_EXPLOSION_ENCHANTMENT =
            ENCHANTMENTS.register("chain_explosion", ChainExplosionEnchantment::new);
    public static final RegistryObject<Enchantment> PREEMPTIVE_STRIKE_ENCHANTMENT =
            ENCHANTMENTS.register("preemptive_strike", PreemptiveStrikeEnchantment::new);
    public static final RegistryObject<Enchantment> COLLECTOR_ENCHANTMENT =
            ENCHANTMENTS.register("collector", CollectorEnchantment::new);
    public static final RegistryObject<Enchantment> EXPLOSION_EXPERT_ENCHANTMENT =
            ENCHANTMENTS.register("explosion_expert", ExplosionExpertEnchantment::new);
    public static final RegistryObject<Enchantment> LIFE_LEECH_ENCHANTMENT =
            ENCHANTMENTS.register("life_leech", LifeLeechEnchantment::new);
    public static final RegistryObject<Enchantment> SNIPER_ELITE_ENCHANTMENT =
            ENCHANTMENTS.register("sniper_elite", SniperEliteEnchantment::new);
    public static final RegistryObject<Enchantment> PANDORA_PARADOX_ENCHANTMENT =
            ENCHANTMENTS.register("pandora_paradox", PandoraParadoxEnchantment::new);
    public static final RegistryObject<Enchantment> SMART_SCOPE_ENCHANTMENT =
            ENCHANTMENTS.register("smart_scope", SmartScopeEnchantment::new);
    public static final RegistryObject<Enchantment> DEEP_LEARNING_ENCHANTMENT =
            ENCHANTMENTS.register("deep_learning", DeepLearningEnchantment::new);
    public static final RegistryObject<Enchantment> EQUALIZER_ENCHANTMENT =
            ENCHANTMENTS.register("equalizer", EqualizerEnchantment::new);
    public static final RegistryObject<Enchantment> RANDOM_ENCHANTMENT =
            ENCHANTMENTS.register("random", RandomEnchantment::new);
    public static final RegistryObject<Enchantment> DECAPITATION_ENCHANTMENT =
            ENCHANTMENTS.register("decapitation", DecapitationEnchantment::new);
    public static final RegistryObject<Enchantment> CHARGE_ENCHANTMENT =
            ENCHANTMENTS.register("charge", ChargeEnchantment::new);

    public TaczFixesMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MinecraftForge.EVENT_BUS.register(new LimbDamageHandler());
        MinecraftForge.EVENT_BUS.register(new SpreadRampHandler());
        MinecraftForge.EVENT_BUS.register(new TaczFixesDataHandler());
        MinecraftForge.EVENT_BUS.register(new GunLevelHandler());
        MinecraftForge.EVENT_BUS.register(new GunEnchantmentHandler());
        MinecraftForge.EVENT_BUS.register(new GunAnvilHandler());
        NetworkHandler.init();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENCHANTMENTS.register(modBus);
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeyMappings);
    }

    private void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.ssscript.taczfixes.handler.ScopeSwitchHandler.SWITCH_SCOPE_KEY);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.register(new SteplessZoomHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.handler.ScopeSwitchHandler());
        });
    }
}

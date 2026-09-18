package com.ssscript.taczfixes;

import com.ssscript.taczfixes.common.config.Config;

import com.ssscript.taczfixes.common.data.TaczFixesDataHandler;
import com.ssscript.taczfixes.common.enchantment.AnnihilationEnchantment;
import com.ssscript.taczfixes.common.enchantment.AbyssGazerEnchantment;
import com.ssscript.taczfixes.common.enchantment.AntiGravityEnchantment;
import com.ssscript.taczfixes.common.enchantment.ArcanaEdenEnchantment;
import com.ssscript.taczfixes.common.enchantment.ChainExplosionEnchantment;
import com.ssscript.taczfixes.common.enchantment.ChargeEnchantment;
import com.ssscript.taczfixes.common.enchantment.CollectorEnchantment;
import com.ssscript.taczfixes.common.enchantment.DecapitationEnchantment;
import com.ssscript.taczfixes.common.enchantment.DeepLearningEnchantment;
import com.ssscript.taczfixes.common.enchantment.DoubleShotEnchantment;
import com.ssscript.taczfixes.common.enchantment.ElectromagneticCoilEnchantment;
import com.ssscript.taczfixes.common.enchantment.EqualizerEnchantment;
import com.ssscript.taczfixes.common.enchantment.ExplosionExpertEnchantment;
import com.ssscript.taczfixes.common.enchantment.FocusedAmmoEnchantment;
import com.ssscript.taczfixes.common.enchantment.LifeLeechEnchantment;
import com.ssscript.taczfixes.common.enchantment.NeurotoxinEnchantment;
import com.ssscript.taczfixes.common.enchantment.OverloadEnchantment;
import com.ssscript.taczfixes.common.enchantment.PandoraParadoxEnchantment;
import com.ssscript.taczfixes.common.enchantment.PatienceEnchantment;
import com.ssscript.taczfixes.common.enchantment.PreemptiveStrikeEnchantment;
import com.ssscript.taczfixes.common.enchantment.RandomEnchantment;
import com.ssscript.taczfixes.common.enchantment.SmartScopeEnchantment;
import com.ssscript.taczfixes.common.enchantment.SniperEliteEnchantment;
import com.ssscript.taczfixes.common.enchantment.StabilityEnchantment;
import com.ssscript.taczfixes.common.enchantment.StandardAmmoEnchantment;
import com.ssscript.taczfixes.common.handler.GunAnvilHandler;
import com.ssscript.taczfixes.client.handler.GunEnchantmentHandler;
import com.ssscript.taczfixes.common.handler.GunLevelHandler;
import com.ssscript.taczfixes.common.handler.JumpInaccuracyHandler;
import com.ssscript.taczfixes.common.handler.LimbDamageHandler;
import com.ssscript.taczfixes.common.handler.SpreadRampHandler;
import com.ssscript.taczfixes.client.handler.SteplessZoomHandler;
import com.ssscript.taczfixes.common.network.NetworkHandler;
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
    public static final DeferredRegister<net.minecraft.world.entity.ai.attributes.Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, MOD_ID);
    public static final RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> AIMING_STAMINA_ATTRIBUTE =
            ATTRIBUTES.register("aiming_stamina",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.aiming_stamina", 100.0, 0.0, 100000.0).setSyncable(true));
    public static final RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> AIMING_STAMINA_CONSUMPTION_ATTRIBUTE =
            ATTRIBUTES.register("aiming_stamina_consumption",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.aiming_stamina_consumption", 1.0, 0.0, 100000.0).setSyncable(true));
    public static final RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> AIMING_STAMINA_RECOVERY_ATTRIBUTE =
            ATTRIBUTES.register("aiming_stamina_recovery",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.aiming_stamina_recovery", 20.0, 0.0, 100000.0).setSyncable(true));
    public static final RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> STAMINA_ATTRIBUTE =
            ATTRIBUTES.register("stamina",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.stamina", 100.0, 0.0, 100000.0).setSyncable(true));
    public static final RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> STAMINA_RECOVERY_ATTRIBUTE =
            ATTRIBUTES.register("stamina_recovery",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.stamina_recovery", 5.0, 0.0, 100000.0).setSyncable(true));
    public static final RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> STAMINA_CONSUMPTION_ATTRIBUTE =
            ATTRIBUTES.register("stamina_consumption",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.stamina_consumption", 1.0, 0.0, 100000.0).setSyncable(true));
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
    public static final RegistryObject<Enchantment> ABYSS_GAZER_ENCHANTMENT =
            ENCHANTMENTS.register("abyssgazer", AbyssGazerEnchantment::new);
    public static final RegistryObject<Enchantment> DOUBLE_SHOT_ENCHANTMENT =
            ENCHANTMENTS.register("double_shot", DoubleShotEnchantment::new);
    public static final RegistryObject<Enchantment> FOCUSED_AMMO_ENCHANTMENT =
            ENCHANTMENTS.register("focused_ammo", FocusedAmmoEnchantment::new);
    public static final RegistryObject<Enchantment> ARCANA_EDEN_ENCHANTMENT =
            ENCHANTMENTS.register("arcana_eden", ArcanaEdenEnchantment::new);
    public static final RegistryObject<Enchantment> PATIENCE_ENCHANTMENT =
            ENCHANTMENTS.register("patience", PatienceEnchantment::new);

    public TaczFixesMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        MinecraftForge.EVENT_BUS.register(new LimbDamageHandler());
        MinecraftForge.EVENT_BUS.register(new SpreadRampHandler());
        MinecraftForge.EVENT_BUS.register(new JumpInaccuracyHandler());
        MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.ShieldHandler());
        MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.InputStateCleanHandler());
        MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.GunDataOverrideReloadHandler());
        MinecraftForge.EVENT_BUS.register(new TaczFixesDataHandler());
        MinecraftForge.EVENT_BUS.register(new GunLevelHandler());
        MinecraftForge.EVENT_BUS.register(new GunAnvilHandler());
        MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.AimingStaminaHandler());
        MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.StaminaHandler());
        MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.ChargeCapabilityHandler());
        NetworkHandler.init();
        com.ssscript.taczfixes.common.util.DualWieldOverrides.setProvider(gunId -> {
            com.ssscript.taczfixes.common.data.GunTaczFixesData.DualWieldConfig cfg =
                    com.ssscript.taczfixes.common.data.TaczFixesDataManager.resolveDualWield(gunId);
            return cfg == null ? null : new com.ssscript.taczfixes.common.util.DualWieldOverrides.Value(
                    cfg.enable, cfg.recoil_multiplier, cfg.inaccuracy_multiplier,
                    cfg.focus_aim_recoil_multiplier, cfg.focus_aim_inaccuracy_multiplier,
                    cfg.left_offset, cfg.right_offset,
                    com.ssscript.taczfixes.common.util.DualWieldOverrides.parseHandPos(
                            cfg.hand_pos == null ? null : cfg.hand_pos.on_left,
                            com.ssscript.taczfixes.common.util.DualWieldOverrides.DEFAULT_ON_LEFT),
                    com.ssscript.taczfixes.common.util.DualWieldOverrides.parseHandPos(
                            cfg.hand_pos == null ? null : cfg.hand_pos.on_right,
                            com.ssscript.taczfixes.common.util.DualWieldOverrides.DEFAULT_ON_RIGHT));
        });
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENCHANTMENTS.register(modBus);
        ATTRIBUTES.register(modBus);
        modBus.addListener(this::onEntityAttributeModification);
        modBus.addListener(this::onCommonSetup);
        if (net.minecraftforge.fml.loading.FMLLoader.getDist().isClient()) {
            modBus.addListener(this::onClientSetup);
            modBus.addListener(this::onRegisterKeyMappings);
            modBus.addListener(this::onRegisterClientReloadListeners);
            modBus.addListener(this::onRegisterGuiOverlays);
        }
    }

    private void onRegisterGuiOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("aiming_stamina", new com.ssscript.taczfixes.client.hud.AimingStaminaOverlay());
        event.registerAboveAll("stamina", new com.ssscript.taczfixes.client.hud.StaminaOverlay());
    }

    /** 启动时应用 gun data 覆盖(zip/目录枪包重写需在包被读取前)。 */
    private void onCommonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        com.ssscript.taczfixes.common.util.GunDataOverrideStorage.applyAll();
    }

    private void onEntityAttributeModification(net.minecraftforge.event.entity.EntityAttributeModificationEvent event) {
        event.add(net.minecraft.world.entity.EntityType.PLAYER, AIMING_STAMINA_ATTRIBUTE.get());
        event.add(net.minecraft.world.entity.EntityType.PLAYER, AIMING_STAMINA_CONSUMPTION_ATTRIBUTE.get());
        event.add(net.minecraft.world.entity.EntityType.PLAYER, AIMING_STAMINA_RECOVERY_ATTRIBUTE.get());
        event.add(net.minecraft.world.entity.EntityType.PLAYER, STAMINA_ATTRIBUTE.get());
        event.add(net.minecraft.world.entity.EntityType.PLAYER, STAMINA_RECOVERY_ATTRIBUTE.get());
        event.add(net.minecraft.world.entity.EntityType.PLAYER, STAMINA_CONSUMPTION_ATTRIBUTE.get());
    }

    private void onRegisterClientReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new com.ssscript.taczfixes.client.data.ClientDisplayDataReloadListener());
    }

    private void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.ssscript.taczfixes.client.handler.ScopeSwitchHandler.SWITCH_SCOPE_KEY);
        event.register(com.ssscript.taczfixes.client.handler.ConfigKeyHandler.OPEN_CONFIG_KEY);
        event.register(com.ssscript.taczfixes.client.handler.EditDataKeyHandler.EDIT_DATA_KEY);
        event.register(com.ssscript.taczfixes.client.handler.HoldBreathKeyHandler.HOLD_BREATH_KEY);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.register(new SteplessZoomHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.ScopeSwitchHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.ConfigKeyHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.GunEnchantmentHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.EditDataKeyHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.GunDataMessageHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.InputSyncHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.HoldBreathKeyHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.AimSwayHandler());
            MinecraftForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.StaminaClientHandler());
        });
    }
}

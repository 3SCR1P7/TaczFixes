package com.ssscript.taczfixes;

import com.ssscript.taczfixes.common.config.Config;

import com.ssscript.taczfixes.common.data.TaczFixesDataHandler;
import com.ssscript.taczfixes.common.util.GunEnchantments;
import com.ssscript.taczfixes.common.handler.GunAnvilHandler;
import com.ssscript.taczfixes.client.handler.GunEnchantmentHandler;
import com.ssscript.taczfixes.common.handler.GunLevelHandler;
import com.ssscript.taczfixes.common.handler.JumpInaccuracyHandler;
import com.ssscript.taczfixes.common.handler.LimbDamageHandler;
import com.ssscript.taczfixes.common.handler.SpreadRampHandler;
import com.ssscript.taczfixes.client.handler.SteplessZoomHandler;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TaczFixesMod.MOD_ID)
public class TaczFixesMod {
    public static final String MOD_ID = "taczfixes";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static final DeferredRegister<net.minecraft.world.entity.ai.attributes.Attribute> ATTRIBUTES =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE, MOD_ID);
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> AIMING_STAMINA_ATTRIBUTE =
            ATTRIBUTES.register("aiming_stamina",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.aiming_stamina", 100.0, 0.0, 100000.0).setSyncable(true));
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> AIMING_STAMINA_CONSUMPTION_ATTRIBUTE =
            ATTRIBUTES.register("aiming_stamina_consumption",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.aiming_stamina_consumption", 1.0, 0.0, 100000.0).setSyncable(true));
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> AIMING_STAMINA_RECOVERY_ATTRIBUTE =
            ATTRIBUTES.register("aiming_stamina_recovery",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.aiming_stamina_recovery", 20.0, 0.0, 100000.0).setSyncable(true));
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> STAMINA_ATTRIBUTE =
            ATTRIBUTES.register("stamina",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.stamina", 100.0, 0.0, 100000.0).setSyncable(true));
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> STAMINA_RECOVERY_ATTRIBUTE =
            ATTRIBUTES.register("stamina_recovery",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.stamina_recovery", 5.0, 0.0, 100000.0).setSyncable(true));
    public static final DeferredHolder<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.Attribute> STAMINA_CONSUMPTION_ATTRIBUTE =
            ATTRIBUTES.register("stamina_consumption",
                    () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute(
                            "attribute.name.taczfixes.stamina_consumption", 1.0, 0.0, 100000.0).setSyncable(true));

    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def OVERLOAD_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.OVERLOAD;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def ANNIHILATION_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.ANNIHILATION;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def STABILITY_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.STABILITY;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def ANTI_GRAVITY_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.ANTI_GRAVITY;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def ELECTROMAGNETIC_COIL_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.ELECTROMAGNETIC_COIL;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def STANDARD_AMMO_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.STANDARD_AMMO;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def NEUROTOXIN_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.NEUROTOXIN;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def CHAIN_EXPLOSION_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.CHAIN_EXPLOSION;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def PREEMPTIVE_STRIKE_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.PREEMPTIVE_STRIKE;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def COLLECTOR_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.COLLECTOR;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def EXPLOSION_EXPERT_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.EXPLOSION_EXPERT;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def LIFE_LEECH_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.LIFE_LEECH;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def SNIPER_ELITE_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.SNIPER_ELITE;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def PANDORA_PARADOX_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.PANDORA_PARADOX;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def SMART_SCOPE_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.SMART_SCOPE;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def DEEP_LEARNING_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.DEEP_LEARNING;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def EQUALIZER_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.EQUALIZER;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def RANDOM_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.RANDOM;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def DECAPITATION_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.DECAPITATION;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def CHARGE_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.CHARGE;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def ABYSS_GAZER_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.ABYSS_GAZER;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def DOUBLE_SHOT_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.DOUBLE_SHOT;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def FOCUSED_AMMO_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.FOCUSED_AMMO;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def ARCANA_EDEN_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.ARCANA_EDEN;
    public static final com.ssscript.taczfixes.common.util.GunEnchantments.Def PATIENCE_ENCHANTMENT = com.ssscript.taczfixes.common.util.GunEnchantments.PATIENCE;

    public TaczFixesMod(IEventBus modBus, ModContainer modContainer) {
        for (java.util.Map.Entry<String, net.neoforged.neoforge.common.ModConfigSpec> entry : Config.SPECS.entrySet()) {
            modContainer.registerConfig(ModConfig.Type.COMMON, entry.getValue(),
                    "taczfixes/" + entry.getKey() + ".toml");
        }
        
        NeoForge.EVENT_BUS.register(new LimbDamageHandler());
        NeoForge.EVENT_BUS.register(new SpreadRampHandler());
        NeoForge.EVENT_BUS.register(new JumpInaccuracyHandler());
        NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.CustomSlotOrphanHandler());
        NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.ShieldHandler());
        NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.InputStateCleanHandler());
        NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.GunDataOverrideReloadHandler());
        NeoForge.EVENT_BUS.register(new TaczFixesDataHandler());
        NeoForge.EVENT_BUS.register(new GunLevelHandler());
        NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.PlayerStateCleanupHandler());
        NeoForge.EVENT_BUS.register(new GunAnvilHandler());
        NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.GunDurabilityHandler());
        NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.AimingStaminaHandler());
        NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.handler.StaminaHandler());
        NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.common.compat.ArcanaSkillBridge());
        if (net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) {
            try {
                NeoForge.EVENT_BUS.register(Class.forName("com.ssscript.taczfixes.common.handler.GunSpellHandler")
                        .getDeclaredConstructor().newInstance());
            } catch (Throwable ignored) {
            }
        }
        NetworkHandler.init();
        com.ssscript.taczfixes.common.util.DualWieldOverrides.setProvider(gunId -> {
            com.ssscript.taczfixes.common.data.GunTaczFixesData.DualWieldConfig cfg =
                    com.ssscript.taczfixes.common.data.TaczFixesDataManager.resolveDualWield(gunId);
            return cfg == null ? null : new com.ssscript.taczfixes.common.util.DualWieldOverrides.Value(
                    cfg.enable, cfg.recoil_multiplier, cfg.inaccuracy_multiplier,
                    cfg.focus_aim_recoil_multiplier, cfg.focus_aim_inaccuracy_multiplier,
                    cfg.left_offset, cfg.right_offset,
                    cfg.forward_offset,
                    com.ssscript.taczfixes.common.util.DualWieldOverrides.parseHandPos(
                            cfg.hand_pos == null ? null : cfg.hand_pos.on_left,
                            com.ssscript.taczfixes.common.util.DualWieldOverrides.DEFAULT_ON_LEFT),
                    com.ssscript.taczfixes.common.util.DualWieldOverrides.parseHandPos(
                            cfg.hand_pos == null ? null : cfg.hand_pos.on_right,
                            com.ssscript.taczfixes.common.util.DualWieldOverrides.DEFAULT_ON_RIGHT));
        });
        modBus.register(new com.ssscript.taczfixes.common.handler.ChargeCapabilityHandler());
        modBus.register(new com.ssscript.taczfixes.common.handler.GunSmithTableEnergyHandler());
        ATTRIBUTES.register(modBus);
        modBus.addListener(this::onEntityAttributeModification);
        modBus.addListener(this::onCommonSetup);
        if (net.neoforged.fml.loading.FMLLoader.getDist().isClient()) {
            modContainer.registerExtensionPoint(
                    net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (container, parent) -> com.ssscript.taczfixes.client.screen.ConfigScreen.create(parent));
            com.ssscript.taczfixes.client.render.LightManager.injectShaders();
            modBus.addListener(this::onClientSetup);
            modBus.addListener(this::onRegisterKeyMappings);
            modBus.addListener(this::onRegisterClientReloadListeners);
            modBus.addListener(this::onRegisterGuiLayers);
            modBus.addListener(com.ssscript.taczfixes.common.network.NetworkHandler::register);
        }
    }

    private void onRegisterGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "aiming_stamina"), new com.ssscript.taczfixes.client.hud.AimingStaminaOverlay());
        event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "stamina"), new com.ssscript.taczfixes.client.hud.StaminaOverlay());
    }

    /** 启动时应用 gun data 覆盖(zip/目录枪包重写需在包被读取前)。 */
    private void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        com.ssscript.taczfixes.common.util.GunDataOverrideStorage.applyAll();
        com.ssscript.taczfixes.common.util.AttachmentDataOverrideStorage.applyAll();
    }

    private void onEntityAttributeModification(net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent event) {
        event.add(net.minecraft.world.entity.EntityType.PLAYER, AIMING_STAMINA_ATTRIBUTE);
        event.add(net.minecraft.world.entity.EntityType.PLAYER, AIMING_STAMINA_CONSUMPTION_ATTRIBUTE);
        event.add(net.minecraft.world.entity.EntityType.PLAYER, AIMING_STAMINA_RECOVERY_ATTRIBUTE);
        event.add(net.minecraft.world.entity.EntityType.PLAYER, STAMINA_ATTRIBUTE);
        event.add(net.minecraft.world.entity.EntityType.PLAYER, STAMINA_RECOVERY_ATTRIBUTE);
        event.add(net.minecraft.world.entity.EntityType.PLAYER, STAMINA_CONSUMPTION_ATTRIBUTE);
    }

    private void onRegisterClientReloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new com.ssscript.taczfixes.client.util.ClientDisplayDataReloadListener());
        event.registerReloadListener(new com.ssscript.taczfixes.common.data.AdapterNativeDataReloadListener());
        event.registerReloadListener((barrier, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor) ->
                java.util.concurrent.CompletableFuture.completedFuture(null).thenCompose(barrier::wait)
                        .thenRunAsync(com.ssscript.taczfixes.client.client.CrosshairPartLayout::clear, gameExecutor));
    }

    private void onRegisterKeyMappings(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.ssscript.taczfixes.client.handler.ScopeSwitchHandler.SWITCH_SCOPE_KEY);
        event.register(com.ssscript.taczfixes.client.handler.ConfigKeyHandler.OPEN_CONFIG_KEY);
        event.register(com.ssscript.taczfixes.client.handler.EditDataKeyHandler.EDIT_DATA_KEY);
        event.register(com.ssscript.taczfixes.client.handler.HoldBreathKeyHandler.HOLD_BREATH_KEY);
        event.register(com.ssscript.taczfixes.client.handler.GunPackKeyHandler.GUN_PACK_KEY);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.register(new SteplessZoomHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.ScopeSwitchHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.ConfigKeyHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.GunEnchantmentHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.EditDataKeyHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.GunDataMessageHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.InputSyncHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.HoldBreathKeyHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.AimSwayHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.ScopeStencilHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.StaminaClientHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.ScopeFovTransitionHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.ArcanaOffhandKeyHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.CrawlHitboxDebugHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.DynamicCrosshairHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.GunDurabilityTooltipHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.GunPackKeyHandler());
            NeoForge.EVENT_BUS.register(new com.ssscript.taczfixes.client.handler.GunPackMenuButtonHandler());
        });
    }
}

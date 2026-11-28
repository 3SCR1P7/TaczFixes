package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.client.util.dual.DualRenderContext;
import com.ssscript.taczfixes.client.render.MuzzleSmokeRender;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.MolangWorldParticleRenderType;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.SnowStormParticle;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.WorldEmitterManager;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.util.time.GunTime;
import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class MuzzleAfterSmoke {
    // Keep updating the muzzle anchor while particles from the last emission are visible.
    private static final long ANCHOR_TAIL_MS = 850L;
    private static final ResourceLocation EFFECT_ID = ResourceLocation.tryParse("taczfixes:muzzle_after_smoke");
    private static final ResourceLocation MEDIUM_EFFECT_ID = ResourceLocation.tryParse("taczfixes:muzzle_after_smoke_medium");
    private static final ResourceLocation HEAVY_EFFECT_ID = ResourceLocation.tryParse("taczfixes:muzzle_after_smoke_heavy");
    private static final ResourceLocation WISP_EFFECT_ID = ResourceLocation.tryParse("taczfixes:muzzle_after_smoke_wisp");
    private static final ParticleEffectData EFFECT = new ParticleEffectData(EFFECT_ID, "muzzle_flash", null);
    private static final ParticleEffectData MEDIUM_EFFECT = new ParticleEffectData(MEDIUM_EFFECT_ID, "muzzle_flash", null);
    private static final ParticleEffectData HEAVY_EFFECT = new ParticleEffectData(HEAVY_EFFECT_ID, "muzzle_flash", null);
    private static final ParticleEffectData WISP_EFFECT = new ParticleEffectData(WISP_EFFECT_ID, "muzzle_flash", null);
    private static final Map<LivingEntity, EnumMap<InteractionHand, Trail>> TRAILS = new WeakHashMap<>();
    private static final Map<ParticleEmitterInstance, Trail> FIRST_PERSON_EMITTERS = new WeakHashMap<>();

    private MuzzleAfterSmoke() {
    }

    public static void onShoot(LivingEntity shooter, ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null || !isEnabled()) {
            return;
        }
        ItemStack mainHand = shooter.getMainHandItem();
        ItemStack offhand = shooter.getOffhandItem();
        boolean local = shooter instanceof LocalPlayer;
        if (local && stack != mainHand && stack != offhand) {
            return;
        }
        InteractionHand hand = stack == offhand || (!local && !DualWieldStackId.matches(mainHand, stack)
                && DualWieldStackId.matches(offhand, stack)) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ResourceLocation gunId = gun.getGunId(stack);
        long now = GunTime.nowMillis();
        Trail trail = getTrail(shooter, hand, now);
        if (suppressesAfterSmoke(stack, gun)) {
            if (trail != null) {
                trail.active = false;
            }
            return;
        }
        if (trail == null || !trail.gunId.equals(gunId)) {
            if (trail != null) {
                trail.active = false;
            }
            trail = new Trail(gunId, selectedSlot(shooter, hand));
            TRAILS.computeIfAbsent(shooter, key -> new EnumMap<>(InteractionHand.class)).put(hand, trail);
        }
        trail.recordShot(now, ClientCeaseFireTiming.delay(shooter, hand, stack, gun));
    }

    public static void clear() {
        for (EnumMap<InteractionHand, Trail> byHand : TRAILS.values()) {
            byHand.values().forEach(trail -> trail.active = false);
        }
        TRAILS.clear();
        FIRST_PERSON_EMITTERS.clear();
    }

    public static List<ParticleEffectData> withFirstPersonSmoke(List<ParticleEffectData> particles, InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        long now = GunTime.nowMillis();
        Trail trail = player == null ? null : getTrail(player, hand, now);
        List<ParticleEffectData> smoke = trail == null ? List.of() : trail.claimEffects(player.getItemInHand(hand), now);
        if (smoke.isEmpty()) {
            return particles;
        }
        if (particles == null || particles.isEmpty()) {
            return smoke;
        }
        List<ParticleEffectData> combined = new ArrayList<>(particles);
        combined.addAll(smoke);
        return combined;
    }

    public static boolean isAfterSmoke(ResourceLocation effectId) {
        return EFFECT_ID.equals(effectId) || MEDIUM_EFFECT_ID.equals(effectId)
                || HEAVY_EFFECT_ID.equals(effectId) || WISP_EFFECT_ID.equals(effectId);
    }

    public static void moveFirstPersonSmoke(ParticleEmitterInstance emitter, Matrix4f muzzle, InteractionHand hand) {
        if (!isAfterSmoke(emitter.getDefinition().getIdentifier())) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        Trail current = player == null ? null : getTrail(player, hand, GunTime.nowMillis());
        if (current == null || FIRST_PERSON_EMITTERS.computeIfAbsent(emitter, key -> current) != current) {
            emitter.getParticles().clear();
            emitter.setRemoved(true);
            return;
        }
        // SBM's first-person particles are simulated in hand space after spawning.
        Matrix4f previous = emitter.getEmitterTransform();
        float dx = muzzle.m30() - previous.m30();
        float dy = muzzle.m31() - previous.m31();
        float dz = muzzle.m32() - previous.m32();
        for (ParticleInstance particle : emitter.getParticles()) {
            particle.x += dx;
            particle.y += dy;
            particle.z += dz;
        }
    }

    public static void onThirdPersonMuzzleRendered(LivingEntity shooter, Matrix4f muzzleWorld) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || shooter == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        InteractionHand hand = DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        long now = GunTime.nowMillis();
        Trail trail = getTrail(shooter, hand, now);
        if (trail == null) {
            return;
        }
        Vec3 position = new Vec3(muzzleWorld.m30(), muzzleWorld.m31(), muzzleWorld.m32());
        trail.worldPosition = position;
        WorldEmitterManager manager = WorldEmitterManager.getInstance();
        for (ParticleEffectData effect : trail.claimEffects(shooter.getItemInHand(hand), now)) {
            ParticleEffectDefinition definition = ParticleDefinitionLoader.getInstance().getDefinition(effect.effect());
            if (definition == null) {
                continue;
            }
            ParticleEmitterInstance emitter = manager.addEmitter(level, position, Vec3.ZERO, definition);
            if (emitter == null) {
                continue;
            }
            manager.updateEmitterAnchor(emitter, muzzleWorld);
            emitter.setExternalParticleManagement(particle -> minecraft.particleEngine.add(
                    new WorldSmokeParticle(level, particle, definition, emitter, trail, position)));
            emitter.tick(0.0F);
        }
    }

    private static Trail getTrail(LivingEntity shooter, InteractionHand hand, long now) {
        EnumMap<InteractionHand, Trail> byHand = TRAILS.get(shooter);
        Trail trail = byHand == null ? null : byHand.get(hand);
        if (trail == null) {
            return null;
        }
        trail.finishVolleyIfDue(now);
        if (!trail.active || now < trail.lastShot || trail.isFinished(now) || !shooter.isAlive()
                || !isEnabled()
                || trail.selectedSlot != selectedSlot(shooter, hand)
                || !trail.gunId.equals(getGunId(shooter.getItemInHand(hand)))) {
            trail.active = false;
            byHand.remove(hand);
            if (byHand.isEmpty()) {
                TRAILS.remove(shooter);
            }
            return null;
        }
        return trail;
    }

    private static boolean suppressesAfterSmoke(ItemStack stack, IGun gun) {
        ResourceLocation installed = gun.getAttachmentId(stack, AttachmentType.MUZZLE);
        ResourceLocation id = installed == null || DefaultAssets.isEmptyAttachmentId(installed)
                ? gun.getBuiltInAttachmentId(stack, AttachmentType.MUZZLE) : installed;
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) {
            return false;
        }
        AttachmentData data = TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                .map(index -> index.getGunData().getExclusiveAttachments().get(id))
                .orElseGet(() -> TimelessAPI.getCommonAttachmentIndex(id).map(CommonAttachmentIndex::getData).orElse(null));
        if (data == null) {
            return false;
        }
        JsonProperty<?> silence = data.getModifier().get(SilenceModifier.ID);
        return data.getFlashHider() <= 0.0F || data.getSmokeHider() <= 0.0F
                || silence != null && silence.getValue() instanceof Pair<?, ?> pair && Boolean.TRUE.equals(pair.right());
    }

    private static boolean isEnabled() {
        return Config.MUZZLE_AFTER_SMOKE.get() && RenderConfig.RENDER_MUZZLE_SMOKE.get();
    }

    private static ResourceLocation getGunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? null : gun.getGunId(stack);
    }

    private static int selectedSlot(LivingEntity shooter, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && shooter instanceof LocalPlayer player
                ? player.getInventory().selected : -1;
    }

    private enum SmokeAmount {
        NONE(0L, 0L),
        LIGHT(260L, 95L),
        MEDIUM(390L, 100L),
        HEAVY(520L, 95L);

        private final long duration;
        private final long spacing;

        SmokeAmount(long duration, long spacing) {
            this.duration = duration;
            this.spacing = spacing;
        }
    }

    private static final class Trail {
        private final ResourceLocation gunId;
        private final int selectedSlot;
        private long lastShot = -1L;
        private long stopAt;
        private int shots;
        private SmokeAmount amount = SmokeAmount.NONE;
        private long followUntil;
        private long nextEmissionAt;
        private int emittedBatches;
        private boolean active = true;
        private Vec3 worldPosition;

        private Trail(ResourceLocation gunId, int selectedSlot) {
            this.gunId = gunId;
            this.selectedSlot = selectedSlot;
        }

        private void recordShot(long now, long quietTime) {
            lastShot = now;
            stopAt = now + quietTime;
            shots++;
            amount = SmokeAmount.NONE;
        }

        private void finishVolleyIfDue(long now) {
            if (shots == 0 || now < stopAt) {
                return;
            }
            amount = shots >= 10 ? SmokeAmount.HEAVY
                    : shots >= 7 ? SmokeAmount.MEDIUM
                    : shots >= 4 ? SmokeAmount.LIGHT : SmokeAmount.NONE;
            nextEmissionAt = stopAt;
            emittedBatches = 0;
            shots = 0;
        }

        private boolean isFinished(long now) {
            return shots == 0 && now >= stopAt + amount.duration && now >= followUntil;
        }

        private List<ParticleEffectData> claimEffects(ItemStack stack, long now) {
            if (amount == SmokeAmount.NONE || now < nextEmissionAt || now >= stopAt + amount.duration) {
                return List.of();
            }
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null || suppressesAfterSmoke(stack, gun)) {
                active = false;
                return List.of();
            }
            ParticleEffectData cloud = switch (amount) {
                case LIGHT -> EFFECT;
                case MEDIUM -> MEDIUM_EFFECT;
                case HEAVY -> HEAVY_EFFECT;
                case NONE -> null;
            };
            ParticleDefinitionLoader definitions = ParticleDefinitionLoader.getInstance();
            if (cloud == null || definitions.getDefinition(cloud.effect()) == null) {
                return List.of();
            }
            nextEmissionAt = now + amount.spacing;
            followUntil = now + ANCHOR_TAIL_MS;
            boolean emitWisp = emittedBatches++ % 3 == 0;
            return emitWisp && definitions.getDefinition(WISP_EFFECT_ID) != null
                    ? List.of(cloud, WISP_EFFECT) : List.of(cloud);
        }
    }

    private static final class WorldSmokeParticle extends SnowStormParticle {
        private final Trail trail;
        private final ParticleRenderType renderType;
        private Vec3 previousAnchor;

        private WorldSmokeParticle(ClientLevel level, ParticleInstance particle, ParticleEffectDefinition definition,
                ParticleEmitterInstance emitter, Trail trail, Vec3 spawnAnchor) {
            super(level, particle, definition, emitter);
            this.trail = trail;
            var description = definition.getDescription();
            var texture = description.getTexture();
            this.renderType = MuzzleSmokeRender.world(texture,
                    MolangWorldParticleRenderType.get(description.getMaterial(), texture));
            this.previousAnchor = spawnAnchor;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return renderType;
        }

        @Override
        public void tick() {
            if (followMuzzle()) {
                super.tick();
            }
        }

        @Override
        public void render(VertexConsumer vertices, Camera camera, float partialTick) {
            if (followMuzzle()) {
                super.render(vertices, camera, partialTick);
            }
        }

        private boolean followMuzzle() {
            if (!trail.active) {
                remove();
                return false;
            }
            Vec3 anchor = trail.worldPosition;
            if (anchor != previousAnchor) {
                Vec3 delta = anchor.subtract(previousAnchor);
                setPos(x + delta.x, y + delta.y, z + delta.z);
                xo += delta.x;
                yo += delta.y;
                zo += delta.z;
                previousAnchor = anchor;
            }
            return true;
        }
    }
}

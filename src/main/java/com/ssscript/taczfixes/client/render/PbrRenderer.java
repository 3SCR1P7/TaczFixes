package com.ssscript.taczfixes.client.render;
import com.ssscript.taczfixes.client.mixin.pbr.PbrCompositeAccessor;
import com.ssscript.taczfixes.client.mixin.pbr.PbrStateAccessor;
import com.ssscript.taczfixes.client.mixin.pbr.PbrTextureAccessor;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import com.ssscript.taczfixes.common.config.Config;
import com.mojang.blaze3d.vertex.*;
import com.ssscript.taczfixes.TaczFixesMod;

import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.compat.optifine.OptifineCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PbrRenderer {
    static ShaderInstance shader, blurShader, compositeShader, maskShader;
    // 缂傚倹鎸搁悺?Uniform 鐎殿喗娲滈弫? 闂侇剙鐏濋崢銈呅掕箛鎾村/婵絽绻戦濂稿几椤忓拋娼剧紓浣姑崺妤呮焾閼恒儱鐦婚悗娑欘殘椤戜焦绋夐崣澶屽弨闁瑰吀鍕橀埀?
    private static AbstractUniform uHasSpecular, uHasNormal, uEmissionPass, uScopeDivisionPass;
    private static AbstractUniform uReflectionStrength, uReflectionArea, uGlareStrength, uEmissionStrength;
    private static AbstractUniform uLocalLightDirection, uLocalLightColor, uLocalLightStrength;
    private static AbstractUniform uCelestialDirection, uCelestialColor, uSkyAmbient;
    private static AbstractUniform uCelestialVisibility, uLocalSkyExposure, uWetness;
    private static AbstractUniform uBlurDirection, uCompositeStrength, uHasScopeMask;
    private static VertexBuffer mesh;
    private static int emissionBlendOverride;
    private static final int MATERIALS_MAX = 256;

    public static void applyEmissionBlendOverride() {
        if (emissionBlendOverride > 0) {
            RenderSystem.disableBlend();
        }
    }

    private static void pushEmissionBlendOverride() {
        emissionBlendOverride++;
    }

    private static void popEmissionBlendOverride() {
        if (emissionBlendOverride > 0) {
            emissionBlendOverride--;
        }
    }
    private static final Map<ResourceLocation, Optional<Material>> MATERIALS =
            new java.util.LinkedHashMap<>(MATERIALS_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ResourceLocation, Optional<Material>> eldest) {
                    return size() > MATERIALS_MAX;
                }
            };
    private static final int TYPES_MAX = 512;
    // RenderType 闁哄牜浜ｉ々顐﹀礃?equals/hashCode(濮掓稒顭堥濠氬冀閸ヮ亞妲曢悹鍥跺幒缁?, 闁活潿鍔忛鏍⒒椤旇偐纰?LRU 闂侇剙鐏濋崢銈夊箣濡粯鐏?闁衡偓绾拋妫呭☉鎿冨幗閺嗭絾鎷呴幘瀵割伕缂佸本妞介崳绋款嚈閹巻鍋?
    private static final Map<RenderType, RenderType> TYPES =
            new java.util.LinkedHashMap<>(TYPES_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RenderType, RenderType> eldest) {
                    return size() > TYPES_MAX;
                }
            };
    private static final Method OPTIFINE_SHADERS = findOptifine();

    private record Material(ResourceLocation specular, ResourceLocation normal, boolean emissive) {}

    private static int tracerBloomSuppression;

    /** 闁哄洤鍟块崢婊冾嚕鐟欏嫯顩柡灞炬尰濠€锟犳⒒鐎涙ê顫囬柛鎺撳劶閸ゆ粓宕ｉ幋婵嗗辅婵炲绋戦崢婊堝箲閺団€崇(闁哄嫷鍨伴幆渚€寮甸埀顒傜磼閸垺鏅搁柡浣哥墢閺?Config.PBR_BLOOM_TRACER 闁告劕鍟块悾?闁?*/
    public static void pushTracerBloomSuppression() {
        tracerBloomSuppression++;
    }

    public static void popTracerBloomSuppression() {
        if (tracerBloomSuppression > 0) {
            tracerBloomSuppression--;
        }
    }

    public static boolean isTracerBloomSuppressed() {
        return tracerBloomSuppression > 0;
    }

    private static final java.util.ArrayDeque<Boolean> DIVISION_RENDER_STACK = new java.util.ArrayDeque<>();

    public static void pushScopeDivision(boolean division) {
        DIVISION_RENDER_STACK.push(division);
    }

    public static void popScopeDivision() {
        DIVISION_RENDER_STACK.poll();
    }

    public static boolean isScopeDivision() {
        return Boolean.TRUE.equals(DIVISION_RENDER_STACK.peek());
    }

    public static void resetScopeDivision() {
        DIVISION_RENDER_STACK.clear();
        scopeAttachmentDepth = 0;
    }

    private static int scopeAttachmentDepth;

    public static void pushScopeAttachment() {
        scopeAttachmentDepth++;
    }

    public static void popScopeAttachment() {
        if (scopeAttachmentDepth > 0) {
            scopeAttachmentDepth--;
        }
    }

    public static boolean isRenderingScopeAttachment() {
        return scopeAttachmentDepth > 0;
    }

    public static void updateCelestialLight(float partialTick) {
        if (shader == null) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var origin = camera.getPosition();
        // 閻忕偐鍋撻梺顔哄妼閸樻粌鈹?闁诲浚鍋呮俊?闁诲浚鍨抽妤冪驳?: 闂傚牏鍋涢妵澶岀矚閾忚妯婇幖杈剧細缁″啴寮?
        // 闁告瑯浜滃﹢顏堝嫉閳ь剚娼婚幋婵堟澖闂傚嫬鎳愮划顖炲礆閹壆绠?PBR 闁哄啳鍩栨竟鍌炲箵韫囨挾婀伴梺顔哄妼閸? 闂侇剙鐏濋崢銈夊籍閻樺磭浠?闁?PBR 闁哄啫澧庡▍褔骞嶉锝嗙厵闁秆勩仠閳?
        if (System.currentTimeMillis() - lastPbrDrawAt <= 500L) {
            updateLocalLight(level, origin);
        }
        setUniform(uLocalLightDirection, localLightX, localLightY, localLightZ);
        setUniform(uLocalLightColor, localLightR, localLightG, localLightB);
        setUniform(uLocalLightStrength, localLightStrength);
        if (!level.dimensionType().hasSkyLight()
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            setUniform(uCelestialDirection, 0f, 1f, 0f);
            setUniform(uCelestialColor, 0f, 0f, 0f);
            setUniform(uSkyAmbient, 0f);
            setUniform(uCelestialVisibility, 0f);
            setUniform(uLocalSkyExposure, 0f);
            setUniform(uWetness, updateWetness(0f));
            return;
        }
        var light = CelestialLight.sample(level.getTimeOfDay(partialTick), level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick), level.getMoonBrightness());
        setUniform(uCelestialDirection, light.x(), light.y(), 0f);
        setUniform(uCelestialColor, light.red(), light.green(), light.blue());
        setUniform(uSkyAmbient, light.ambient());
        // A bounded ray catches ceilings and walls in the light's actual direction.
        var end = origin.add(new Vec3(light.x(), light.y(), 0).scale(96));
        boolean visible = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, camera.getEntity())).getType() == HitResult.Type.MISS;
        setUniform(uCelestialVisibility, visible ? 1f : 0f);
        setUniform(uLocalSkyExposure, level.getBrightness(LightLayer.SKY, BlockPos.containing(origin)) / 15f);
        // 婵烇絽顑夊ú锕傚箥閹惧鐣? 濞寸姴鎳嶇€靛本绋夐弽顐ｆ珪闁靛棔绶氬﹢鑸靛緞閳衡偓缁楁牗寰勯幘顔衡偓濠囧籍閻樿绱曢柟绯曞墲濡炲倿鎮介悢鍛婃珡, 鐎殿喖鎼€瑰磭鎹勯悢鍏碱吂闂傚棎鍔戦崳娲Υ?
        // 闁活潿鍔庣敮铏光偓瑙勬构缂嶅懐绱旈搴撳亾鐏炶偐鐟濋柡鍕靛灣濞村寮垫潪鎵Т缂? 缂佹鍏涚粭浣圭閾忓墣鐐烘儎閸涘﹥绨氶柛娆樺灥閸忔﹢宕烽妸銉ф簣婵＄偓鍔曢ˇ? 濞达絽妫欓悘娆撳椽瀹€鈧敮铏光偓纭呮硾濠€顏堟焼椤旇棄鐨″☉鎾愁儎缁楀鎯旈弮鈧晶锕€锕㈢憗銈傚亾?
        float targetWetness = 0f;
        var wetnessAnchor = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.position() : origin;
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && level.isRaining()) {
            BlockPos wetPos = BlockPos.containing(wetnessAnchor.x, wetnessAnchor.y + 0.2D, wetnessAnchor.z);
            // isRainingAt 鐎瑰憡褰冪€垫﹢宕?canSeeSky/濡ゅ倹锚鐎规娊宕?缂傚洢鍊楅柈鎾⒔瀹ュ澶勬俊顐熷亾闁? canSeeSky 闁告劕绉靛Ο澶婎嚕韫囨洍鈧鎷嬮妶鍛粓濡炪倛鍩栧Λ銈夊棘閻熺増鍋ラ梺顒夊枟鐏忓懘濡?
            if (level.canSeeSky(wetPos) && level.isRainingAt(wetPos)) {
                targetWetness = level.getRainLevel(partialTick);
            }
        }
        setUniform(uWetness, updateWetness(targetWetness));
    }

    private static float smoothedWetness = 0f;
    private static long wetnessLastNanos;
    private static final float WET_SECONDS = 5.0f;
    private static final float DRY_SECONDS = 8.0f;

    /** 婵烇絽顑嗙粻?闁哄懏鍎抽崗閬嶆焾閼恒儱鐦婚柡鍐ㄧ埣濡灝銆掗幇顒€缍? 闂侇剙鐏濋崢銈嗘交濞戞ê姣夐悗骞垮€曢崬鎾籍閸撲胶浠滈梻鍌滎棎閻戯箓宕ｅΟ绯曞亾?*/
    private static float updateWetness(float target) {
        long now = System.nanoTime();
        if (wetnessLastNanos == 0L) {
            wetnessLastNanos = now;
        }
        float dt = Math.min((now - wetnessLastNanos) / 1.0e9f, 0.25f);
        wetnessLastNanos = now;
        if (target > smoothedWetness) {
            smoothedWetness = Math.min(target, smoothedWetness + dt / WET_SECONDS);
        } else if (target < smoothedWetness) {
            smoothedWetness = Math.max(target, smoothedWetness - dt / DRY_SECONDS);
        }
        return smoothedWetness;
    }
    private static float localLightX = 0f;
    private static float localLightY = 1f;
    private static float localLightZ = 0f;
    private static float localLightR = 1f;
    private static float localLightG = 1f;
    private static float localLightB = 1f;
    private static float localLightStrength = 0f;
    private static long localLightNextScan = 0L;
    private static BlockPos localLightBlock = null;
    private static int localLightEmission = 0;
    private static long lastPbrDrawAt = 0L;

    /** 闁规鍋呭鍧楁⒔閸曨喚绠柛娆愬灥閸樻粓寮悷鐗堝仴(闁煎搫鍊圭粊?; 闁哄倻鎳撻幃?鐎殿喖鎼€瑰啿袙韫囨挻濮庨柟绋款槸缂嶅宕滃鍕Т缂傚喚鍣ｉ崳鍝ョ不? 缂佸顕ф慨鈺呭籍閸洜褰柛蹇擃槸闁解晛顭ㄩ幋锝囶€€闂傚懎绻嬬粭澶愬础閿熺姰鈧垿濡?*/
    private static void updateLocalLight(net.minecraft.world.level.Level level, Vec3 origin) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        Vec3 visibilityAnchor = player != null ? player.getEyePosition(1.0F) : origin;
        long now = System.currentTimeMillis();
        if (now >= localLightNextScan) {
            localLightNextScan = now + 250L;
            BlockPos center = BlockPos.containing(origin);
            BlockPos best = null;
            int bestEmission = 0;
            double bestScore = 0.0D;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -4, -6), center.offset(6, 4, 6))) {
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                int emission = state.getLightEmission(level, pos);
                if (emission <= 0) continue;
                BlockPos immutable = pos.immutable();
                if (!isLightVisible(level, visibilityAnchor, immutable, player)) continue;
                double dx = pos.getX() + 0.5D - origin.x;
                double dy = pos.getY() + 0.5D - origin.y;
                double dz = pos.getZ() + 0.5D - origin.z;
                double score = emission / (1.0D + (dx * dx + dy * dy + dz * dz) * 0.35D);
                if (score > bestScore) {
                    bestScore = score;
                    best = immutable;
                    bestEmission = emission;
                }
            }
            localLightBlock = best;
            localLightEmission = bestEmission;
            if (best != null) {
                int color = level.getBlockState(best).getMapColor(level, best).col;
                int r = (color >> 16) & 255;
                int g = (color >> 8) & 255;
                int b = color & 255;
                if (r + g + b < 24) {
                    r = 255;
                    g = 220;
                    b = 170;
                }
                localLightR = r / 255f;
                localLightG = g / 255f;
                localLightB = b / 255f;
            }
        }
        float targetStrength = 0f;
        float targetX = localLightX;
        float targetY = localLightY;
        float targetZ = localLightZ;
        if (localLightBlock != null && isLightVisible(level, visibilityAnchor, localLightBlock, player)) {
            double dx = localLightBlock.getX() + 0.5D - origin.x;
            double dy = localLightBlock.getY() + 0.5D - origin.y;
            double dz = localLightBlock.getZ() + 0.5D - origin.z;
            double len = Math.max(1.0E-4D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            targetX = (float) (dx / len);
            targetY = (float) (dy / len);
            targetZ = (float) (dz / len);
            double score = localLightEmission / (1.0D + len * len * 0.35D);
            targetStrength = (float) Math.max(0.0D, Math.min(1.0D, score / 7.0D));
        }
        // 闁哄倻鎳撻幃婊冃掕箛鎾村闁哄洤鐡ㄩ弻?閺夆晝鍋熼悽?, 鐎殿喖鎼€硅櫕娼拠韫俺妤犵偠娅曠划锕傛焼閸喖甯抽柛鎺戞处瀹曟煡宕楁径瀣埍閻犲搫鍟胯ぐ?
        localLightX = targetX;
        localLightY = targetY;
        localLightZ = targetZ;
        localLightStrength += (targetStrength - localLightStrength) * 0.35f;
    }

    /** 闁稿繐顦扮花顕€宕氶幍顔艰礋閻庣懓澧庡┃鍌炴儓濞戞顓洪梻鍌氱摠濡叉悂宕ラ敂鑺ョ畳闂侇剦鍠楃亸鍛村棘閻熺増鍋? 闁绘粏宕甸幏鎴犵驳婢跺鐟濋梺顒夊枟鐏忓懘鎯冮崟顒佺厵闁秆勩仦缁辨壆绮氶懗顖滅畺闁?*/
    private static boolean isLightVisible(net.minecraft.world.level.Level level, Vec3 from, BlockPos lightPos,
                                           Entity entity) {
        Vec3 target = new Vec3(lightPos.getX() + 0.5D, lightPos.getY() + 0.5D, lightPos.getZ() + 0.5D);
        Vec3 start = from;
        Vec3 direction = target.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return true;
        }
        Vec3 step = direction.normalize().scale(0.01D);
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit = level.clip(new ClipContext(start, target, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, entity));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (hit.getBlockPos().equals(lightPos)) {
                return true;
            }
            if (level.getBlockState(hit.getBlockPos()).canOcclude()) {
                return false;
            }
            start = hit.getLocation().add(step);
            if (start.distanceToSqr(target) < 1.0E-6D) {
                return true;
            }
        }
        return false;
    }
    private static Method findOptifine() {
        if (!OptifineCompat.isOptifineInstalled()) return null;
        try {
            return Class.forName("net.optifine.Config").getMethod("isShaders");
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static boolean enabled() {
        if (shader == null || !Config.PBR_ENABLED.get() || OculusCompat.isUsingRenderPack()) return false;
        if (OptifineCompat.isOptifineInstalled()) {
            if (OPTIFINE_SHADERS == null) return false;
            try {
                if ((boolean) OPTIFINE_SHADERS.invoke(null)) return false;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
        // TaCZ's AR compatibility entry point selects conventional gun rendering
        // while this shader is active. Do not query it here: that would recurse.
        return true;
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        MATERIALS.clear();
        TYPES.clear();
        PbrBloom.release();
        if (mesh != null) { mesh.close(); mesh = null; }
        shader = blurShader = compositeShader = maskShader = null;
        clearUniformCache();
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "gun_pbr"), DefaultVertexFormat.NEW_ENTITY), s -> {
                    shader = s;
                    cacheGunUniforms(s);
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "pbr_mask"), DefaultVertexFormat.POSITION_TEX), s -> maskShader = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "pbr_blur"), DefaultVertexFormat.POSITION_TEX), s -> {
                    blurShader = s;
                    uBlurDirection = s.safeGetUniform("Direction");
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "pbr_composite"), DefaultVertexFormat.POSITION_TEX), s -> {
                    compositeShader = s;
                    uCompositeStrength = s.safeGetUniform("Strength");
                    uHasScopeMask = s.safeGetUniform("HasScopeMask");
                });
    }

    private static void clearUniformCache() {
        uHasSpecular = uHasNormal = uEmissionPass = uScopeDivisionPass = null;
        uReflectionStrength = uReflectionArea = uGlareStrength = uEmissionStrength = null;
        uLocalLightDirection = uLocalLightColor = uLocalLightStrength = null;
        uCelestialDirection = uCelestialColor = uSkyAmbient = null;
        uCelestialVisibility = uLocalSkyExposure = uWetness = null;
        uBlurDirection = uCompositeStrength = uHasScopeMask = null;
    }

    private static void cacheGunUniforms(ShaderInstance s) {
        uHasSpecular = s.safeGetUniform("HasSpecular");
        uHasNormal = s.safeGetUniform("HasNormal");
        uEmissionPass = s.safeGetUniform("EmissionPass");
        uScopeDivisionPass = s.safeGetUniform("ScopeDivisionPass");
        uReflectionStrength = s.safeGetUniform("ReflectionStrength");
        uReflectionArea = s.safeGetUniform("ReflectionArea");
        uGlareStrength = s.safeGetUniform("GlareStrength");
        uEmissionStrength = s.safeGetUniform("EmissionStrength");
        uLocalLightDirection = s.safeGetUniform("LocalLightDirection");
        uLocalLightColor = s.safeGetUniform("LocalLightColor");
        uLocalLightStrength = s.safeGetUniform("LocalLightStrength");
        uCelestialDirection = s.safeGetUniform("CelestialDirection");
        uCelestialColor = s.safeGetUniform("CelestialColor");
        uSkyAmbient = s.safeGetUniform("SkyAmbient");
        uCelestialVisibility = s.safeGetUniform("CelestialVisibility");
        uLocalSkyExposure = s.safeGetUniform("LocalSkyExposure");
        uWetness = s.safeGetUniform("Wetness");
    }

    /** 缂傚倹鎸搁悺銊︾▔閾忓厜鏁?閻犙冨缁噣鏌屽鍫熺グ/闁活偀鍋撻柤瑙勫絻濞呮帞绱撻崫鍕╀杭)闁哄啯鍎奸悜锔芥交閸パ冩櫢闁稿繈鍎埀?*/
    static void setUniform(AbstractUniform uniform, int value) {
        if (uniform != null) uniform.set(value);
    }

    static void setUniform(AbstractUniform uniform, float value) {
        if (uniform != null) uniform.set(value);
    }

    static void setUniform(AbstractUniform uniform, float x, float y) {
        if (uniform != null) uniform.set(x, y);
    }

    static void setUniform(AbstractUniform uniform, float x, float y, float z) {
        if (uniform != null) uniform.set(x, y, z);
    }

    /** 闁稿繐褰夐棅鈺佄熼敍鍕囬柣顐熷亾闁艰褰冨▍鎺楁儍閸曨剚鐓欓柛?UV 闁告娲戠紞?闁?*/
    static void setBlurDirection(float x, float y) {
        if (uBlurDirection != null) uBlurDirection.set(x, y);
    }

    /** 闁稿繐褰夐棅鈺佲枖濞戞ê甯ㄩ柛姘墛閸ㄦ岸鎯堥埀顒勬嚌閹绘帗鐝ら柣銊ュ瀹歌鲸鎯旈敂琛″亾?*/
    static void setBloomStrength(float value) {
        if (uCompositeStrength != null) uCompositeStrength.set(value);
    }

    static void setHasScopeMask(int value) {
        if (uHasScopeMask != null) uHasScopeMask.set(value);
    }

    public static RenderType resolve(RenderType original) {
        if (!enabled() || original instanceof PbrType || original.format() != DefaultVertexFormat.NEW_ENTITY
                || !(original instanceof PbrCompositeAccessor composite)) return original;
        return TYPES.computeIfAbsent(original, key -> {
            var textureState = ((PbrStateAccessor) (Object) composite.taczfixes$pbrState()).taczfixes$pbrTexture();
            var texture = ((PbrTextureAccessor) textureState).taczfixes$pbrTextureLocation();
            if (texture.isEmpty()) return original;
            ResourceLocation base = com.ssscript.taczfixes.client.util.GunRecolorManager.sourceOf(texture.get());
            Material material = material(base);
            if (material == null || material.specular() == null && material.normal() == null) {
                ResourceLocation own = com.ssscript.taczfixes.client.util.GunRecolorManager.currentBaseTexture();
                if (own != null && !own.equals(base)) {
                    Material ownMaterial = material(own);
                    if (ownMaterial != null && (ownMaterial.specular() != null || ownMaterial.normal() != null)) {
                        material = ownMaterial;
                    }
                }
            }
            return material == null ? original : new PbrType(original, material);
        });
    }

    private static Material material(ResourceLocation base) {
        return MATERIALS.computeIfAbsent(base, PbrRenderer::findMaterial).orElse(null);
    }

    private static Optional<Material> findMaterial(ResourceLocation base) {
        String path = base.getPath();
        if (!path.endsWith(".png")) return Optional.empty();
        String stem = path.substring(0, path.length() - 4);
        ResourceLocation specular = new ResourceLocation(base.getNamespace(), stem + "_s.png");
        ResourceLocation normal = new ResourceLocation(base.getNamespace(), stem + "_n.png");
        var resources = Minecraft.getInstance().getResourceManager();
        ResourceLocation specularLocation = resources.getResource(specular).isPresent() ? specular : null;
        boolean emissive = specularLocation != null && hasEmissiveAlpha(resources, specularLocation);
        return Optional.of(new Material(specularLocation,
                resources.getResource(normal).isPresent() ? normal : null, emissive));
    }

    /** LabPBR alpha 255 濞戞挻妞藉顏堟嚊椤忓嫬绲洪柛蹇擃槸閹界娀宕楅棃娑掑亾缁楄　鍋? 濞戞挾鍎ゅΛ銈夋嚊椤忓嫬绲洪柛? 闁稿繑婀圭紞?alpha 濞寸媴缍€閵嗗啴宕ｉ幋婵嗗辅鐎殿喖鎼€规娊濡?*/
    private static boolean hasEmissiveAlpha(ResourceManager resources, ResourceLocation location) {
        var resource = resources.getResource(location);
        if (resource.isEmpty()) return false;
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int alpha = (image.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    if (alpha != 0 && alpha != 255) return true;
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return false;
    }

    private static final class PbrType extends RenderType {
        private final RenderType original;
        private final Material material;

        PbrType(RenderType original, Material material) {
            super("taczfixes_pbr", original.format(), original.mode(), original.bufferSize(),
                    original.affectsCrumbling(), true, original::setupRenderState, original::clearRenderState);
            this.original = original;
            this.material = material;
        }

        @Override public Optional<RenderType> outline() { return original.outline(); }

        @Override
        public void end(BufferBuilder builder, VertexSorting sorting) {
            if (!builder.building()) return;
            // Keep vanilla ordering for translucent gun skins, including on the emissive pass.
            if (mode() == VertexFormat.Mode.QUADS) builder.setQuadSorting(sorting);
            var data = builder.endOrDiscardIfEmpty();
            if (data == null) return;
            if (mesh == null) mesh = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            setupRenderState();
            ShaderInstance previous = RenderSystem.getShader();
            int specularSlot = RenderSystem.getShaderTexture(3);
            int normalSlot = RenderSystem.getShaderTexture(4);
            int depthSlot = RenderSystem.getShaderTexture(5);
            try {
                mesh.bind();
                mesh.upload(data);
                if (!enabled()) {
                    mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), previous);
                    return;
                }
                lastPbrDrawAt = System.currentTimeMillis();
                if (material.specular() != null) RenderSystem.setShaderTexture(3, material.specular());
                if (material.normal() != null) RenderSystem.setShaderTexture(4, material.normal());
                setUniform(uHasSpecular, material.specular() == null ? 0 : 1);
                setUniform(uHasNormal, material.normal() == null ? 0 : 1);
                setUniform(uReflectionStrength, material.specular() == null ? 0f : Config.PBR_REFLECTION.get().floatValue());
                setUniform(uReflectionArea, Config.PBR_REFLECTION_AREA.get().floatValue());
                setUniform(uGlareStrength, material.specular() == null ? 0f : Config.PBR_GLARE_STRENGTH.get().floatValue());
                setUniform(uEmissionStrength, Config.PBR_EMISSION.get().floatValue());
                setUniform(uEmissionPass, 0);
                RenderSystem.setShader(() -> shader);
                mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
                int previousFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                int mainFbo = Minecraft.getInstance().getMainRenderTarget().frameBufferId;
                boolean lensDivision = previousFbo != mainFbo && isScopeDivision() && PbrBloom.pureScopeAiming();
                if (PbrBloom.canCapture()
                        && (!isTracerBloomSuppressed() || Config.PBR_BLOOM_TRACER.get())
                        && (previousFbo == mainFbo || lensDivision)) {
                    boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                    boolean stencilWas = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
                    int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
                    int stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
                    int stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
                    int stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
                    int stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
                    int stencilDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
                    int[] viewport = new int[4];
                    GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                    boolean replaceEmission = !isScopeDivision();
                    boolean blendWas = GL11.glIsEnabled(GL11.GL_BLEND);
                    boolean depthTestWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                    int depthFuncWas = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
                    if (replaceEmission) {
                        // The emission target must keep the nearest surface, not add every
                        // PBR draw. Additive blending made bloom from a rear workbench leak
                        // through a workbench rendered in front of it.
                        pushEmissionBlendOverride();
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    }
                    try {
                        RenderSystem.depthMask(true);
                        PbrBloom.bindCapture();
                        // The emission target carries a fresh copy of the main stencil,
                        // so the draw's own stencil test keeps sight divisions visible.
                        // Scope lens suppression only needs to cull everything outside
                        // the division.
                        if (PbrBloom.pureScopeAiming() && PbrBloom.scopeStencilAvailable() && !isScopeDivision()) {
                            GL11.glEnable(GL11.GL_STENCIL_TEST);
                            GL11.glStencilFunc(GL11.GL_EQUAL, 0, 255);
                            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                        }
                        setUniform(uEmissionPass, 1);
                        setUniform(uScopeDivisionPass, isScopeDivision() ? 1 : 0);
                        mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
                    } finally {
                        if (replaceEmission) {
                            popEmissionBlendOverride();
                            if (blendWas) {
                                RenderSystem.enableBlend();
                            } else {
                                RenderSystem.disableBlend();
                            }
                            if (depthTestWas) {
                                RenderSystem.enableDepthTest();
                            } else {
                                RenderSystem.disableDepthTest();
                            }
                            RenderSystem.depthFunc(depthFuncWas);
                        }
                        setUniform(uEmissionPass, 0);
                        setUniform(uScopeDivisionPass, 0);
                        if (stencilWas) {
                            GL11.glEnable(GL11.GL_STENCIL_TEST);
                            GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
                            GL11.glStencilOp(stencilFail, stencilDepthFail, stencilDepthPass);
                        } else {
                            GL11.glDisable(GL11.GL_STENCIL_TEST);
                        }
                        RenderSystem.depthMask(depthMask);
                        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
                        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                    }
                }
            } finally {
                VertexBuffer.unbind();
                RenderSystem.setShaderTexture(3, specularSlot);
                RenderSystem.setShaderTexture(4, normalSlot);
                RenderSystem.setShaderTexture(5, depthSlot);
                RenderSystem.setShader(() -> previous);
                clearRenderState();
            }
        }
    }

    private PbrRenderer() {}
}
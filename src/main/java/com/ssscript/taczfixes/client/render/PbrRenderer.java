package com.ssscript.taczfixes.client.render;
import com.ssscript.taczfixes.client.mixin.shader.PbrCompositeAccessor;
import com.ssscript.taczfixes.client.mixin.shader.PbrStateAccessor;
import com.ssscript.taczfixes.client.mixin.shader.PbrTextureAccessor;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
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
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PbrRenderer {
    static ShaderInstance shader, blurShader, compositeShader, maskShader;
    // 缂傚倸鍊归幐鎼佹偤?Uniform 閻庢鍠楀ú婊堝极? 闂備緡鍓欓悘婵嬪储閵堝憛鎺曠疀閹炬潙顫?濠殿噯绲界换鎴︻敃婵傜鍑犳い蹇撴媼濞煎墽绱撴担濮戭亪宕哄Δ鍛劸闁兼亽鍎遍惁濠氭倵濞戞瑯娈樻い鎴滅劍缁嬪宕ｆ径灞藉绩闂佺懓鍚€閸曟﹢鍩€?
    private static AbstractUniform uHasSpecular, uHasNormal, uEmissionPass, uScopeDivisionPass;
    private static AbstractUniform uReflectionStrength, uReflectionArea, uGlareStrength, uEmissionStrength;
    private static AbstractUniform uGlareDepthTest;
    private static AbstractUniform uLocalLightDirection, uLocalLightColor, uLocalLightStrength;
    private static AbstractUniform uCelestialDirection, uCelestialColor, uSkyAmbient;
    private static AbstractUniform uCelestialVisibility, uLocalSkyExposure, uWetness;
    private static AbstractUniform uBlurDirection, uCompositeStrength, uHasScopeMask;
    private static VertexBuffer mesh;
    private static int emissionBlendOverride;
    private static boolean acceleratedPbrPass;
    private static boolean emissionReplay;
    private static final int MATERIALS_MAX = 256;

    public static void beginAcceleratedPass() {
        acceleratedPbrPass = true;
    }

    public static void endAcceleratedPass() {
        acceleratedPbrPass = false;
    }

    static boolean isAcceleratedPbrPass() {
        return acceleratedPbrPass;
    }

    public static boolean isEmissionReplay() {
        return emissionReplay;
    }

    public static boolean shouldReplayEmission(RenderType type) {
        return enabled() && PbrBloom.canCapture() && !isAcceleratedPbrPass()
                && com.tacz.guns.compat.ar.ARCompat.shouldAccelerate()
                && resolve(type) instanceof PbrType;
    }

    public static void beginEmissionReplay() {
        emissionReplay = true;
    }

    public static void endEmissionReplay() {
        emissionReplay = false;
    }

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
    // RenderType 闂佸搫鐗滄禍锝夈€呴锕€绀?equals/hashCode(婵帗绋掗…鍫ヮ敇婵犳艾鍐€闁搞儺浜炲Σ鏇㈡偣閸ヨ泛骞掔紒?, 闂佹椿娼块崝蹇涱敊閺嶎厽鈷掓い鏃囧亹绾?LRU 闂備緡鍓欓悘婵嬪储閵堝绠ｆ俊顖滅帛閻?闂佽　鍋撶痪顓炴媼濡懎鈽夐幙鍐ㄥ箺闁哄棴绲鹃幏鍛村箻鐎靛壊浼曠紓浣告湰濡炰粙宕崇粙娆惧殘闁诡厽宸婚崑?
    private static final Map<RenderType, RenderType> TYPES =
            new java.util.LinkedHashMap<>(TYPES_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RenderType, RenderType> eldest) {
                    return size() > TYPES_MAX;
                }
            };
    private static final Method OPTIFINE_SHADERS = findOptifine();

    private record Material(ResourceLocation specular, ResourceLocation normal) {}

    private static int tracerBloomSuppression;

    /** 闂佸搫娲ら崯鍧楀储濠婂喚鍤曢悷娆忓椤╊偊鏌＄仦鐐鞍婵犫偓閿熺姵鈷掗悗娑櫭～鍥煕閹烘挸鍔堕柛銈嗙矒瀹曪綁骞嬪┑鍡楄緟濠电偛顦扮粙鎴﹀储濠婂牆绠查柡鍥ｂ偓宕囶唵(闂佸搫瀚烽崹浼村箚娓氣偓瀵敻鍩€椤掑倻纾奸柛顐犲灪閺呮悂鏌℃担鍝ュⅱ闁?Config.PBR_BLOOM_TRACER 闂佸憡鍔曢崯鍧楁偩?闂?*/
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
        // 闁诲繒鍋愰崑鎾绘⒑椤斿搫濡奸柛妯荤矊閳?闂佽娴氶崑鍛繆?闂佽娴氶崹鎶筋敄濡ゅ啰椹?: 闂傚倸鐗忛崑娑㈠Φ婢跺瞼鐭氶柧蹇氼潐濡﹪骞栨潏鍓х窗缂佲€冲暣瀵?
        // 闂佸憡鐟禍婊冿耿椤忓牆瀚夐柍褜鍓氬濠氬箣濠靛牊婢栭梻鍌氬閹虫劗鍒掗鐐茬闁诡垱澹嗙粻?PBR 闂佸搫鍟抽崺鏍ㄧ珶閸岀偛绠甸煫鍥ㄦ尵濠€浼存⒑椤斿搫濡奸柛? 闂備緡鍓欓悘婵嬪储閵堝绫嶉柣妯虹－娴?闂?PBR 闂佸搫鍟晶搴♀枍瑜旈獮宥夘敃閿濆棛鍘甸梺绉嗗嫨浠犻柍?
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
        // 濠电儑绲介澶娒洪敃鍌氱闁规儳顕悾? 婵炲濮撮幊宥団偓闈涙湰缁嬪寮介锝嗙彧闂侀潧妫旂欢姘耿閼搁潧绶為柍琛″亾缂佹鐗楀鍕箻椤旇　鍋撴繝鍥х睄闁绘顔婄槐鏇㈡煙缁洖澧叉俊鐐插€块幃浠嬫偄閸涘﹥鐝? 閻庢鍠栭幖顐も偓鐟扮－閹瑰嫰鎮㈤崗纰卞悅闂傚倸妫庨崝鎴﹀闯濞差亜违?
        // 闂佹椿娼块崝搴ｆ暜閾忓厜鍋撶憴鍕瀯缂傚秴鎳愮槐鏃堫敊鎼存挸浜鹃悘鐐跺亹閻熸繈鏌￠崟闈涚仯婵炴潙顦靛鍨姜閹殿喚孝缂? 缂備焦顨忛崗娑氱箔娴ｅ湱顩查柧蹇撳ⅲ閻愮儤鍎庨柛娑橈攻缁ㄦ岸鏌涘▎妯虹仴闁稿繑锕㈠畷鐑藉Ω閵壯勭埃濠碉紕鍋撻崝鏇⑺? 婵炶揪绲藉Λ娆撴倶濞嗘挸妞界€光偓閳ь剛鏁搹鍏夊亾绾懏纭炬繝鈧鍫熺劶妞ゆ棁妫勯惃鈥斥槈閹炬剚鍎庣紒妤€顦伴幆鏃堝籍閳ь剚鏅堕敃鈧敃銏㈡啑閵堝倸浜?
        float targetWetness = 0f;
        var wetnessAnchor = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.position() : origin;
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && level.isRaining()) {
            BlockPos wetPos = BlockPos.containing(wetnessAnchor.x, wetnessAnchor.y + 0.2D, wetnessAnchor.z);
            // isRainingAt 閻庣懓鎲¤ぐ鍐偓鍨耿瀹?canSeeSky/婵°倕鍊归敋閻庤濞婂畷?缂傚倸娲㈤崐妤呮焾閹绢喗鈷旂€广儱顦版径鍕繆椤愮喎浜鹃梺? canSeeSky 闂佸憡鍔曠粔闈浳熸径濠庡殨闊洦娲嶉埀顒侇焽閹峰濡堕崨顐＄矒婵＄偑鍊涢崺鏍涢妶澶婃闁荤喓澧楅崑銉╂⒑椤掑鏋熼悘蹇撴嚇婵?
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

    /** 濠电儑绲介鍡欑不?闂佸搫鎳忛崕鎶藉礂闁秵鐒鹃柤鎭掑劚閻﹀鏌￠崘銊у煟婵☆偄鐏濋妴鎺楀箛椤掆偓缂? 闂備緡鍓欓悘婵嬪储閵堝棙浜ゆ繛鎴灻В澶愭倵楠炲灝鈧洟宕幘顔肩睄闁告挷鑳舵禒婊堟⒒閸屾粠妫庨柣鎴畵瀹曪絽螣缁洖浜?*/
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

    /** 闂佽顔栭崑鍛嚕閸ф鈷旈柛鏇ㄥ枤缁狀噣鏌涘▎鎰仴闁告ɑ绮撳顒勬偡閻楀牆浠?闂佺厧鎼崐鍦矈?; 闂佸搫鍊婚幊鎾诲箖?閻庢鍠栭幖顐も偓鐟板暱琚欓煫鍥ㄦ尰婵酣鏌熺粙娆炬Ц缂傚秴顑夊畷婊冾吋閸曨厾孝缂傚倸鍠氶崳锝夊闯閸濄儳涓? 缂備礁顦抽褎鎱ㄩ埡鍛睄闁割偅娲滆ぐ顖炴煕韫囨搩妲搁梺瑙ｆ櫅椤劑骞嬮敐鍥垛偓鈧梻鍌氭噹缁诲绮径鎰闁跨喓濮伴埀顒佸灴婵?*/
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
        // 闂佸搫鍊婚幊鎾诲箖濠婂唭鎺曠疀閹炬潙顫撻梺鍝勬搐閻°劑寮?闁哄鏅濋崑鐔兼偨?, 閻庢鍠栭幖顐も偓纭呮珪濞碱亞鎷犻煫顓犱亢濡ょ姷鍋犲▍鏇犲垝閿曞倹鐒奸柛顭戝枛鐢娊鏌涢幒鎴炲鐎规洘鐓″畷妤佸緞鐎ｎ剛鍩嶉柣鐘叉惈閸熻儻銇?
        localLightX = targetX;
        localLightY = targetY;
        localLightZ = targetZ;
        localLightStrength += (targetStrength - localLightStrength) * 0.35f;
    }

    /** 闂佺绻愰ˇ鎵姳椤曗偓瀹曟岸骞嶉鑹扮闁诲海鎳撴晶搴♀攦閸岀偞鍎撴繛鎴烆殘椤撴椽姊婚崒姘辨憼婵″弶鎮傚畷銉╂晜閼恒儳鐣抽梻渚囧墻閸犳浜搁崨鏉戞闁荤喓澧楅崑? 闂佺粯绮忓畷鐢稿箯閹寸姷椹冲璺侯槺閻熸繈姊洪澶婃灍閻忓繐鎳橀幆鍐礋椤掍胶鍘甸梺绉嗗嫨浠︾紒杈ㄥ缁岸鎳楅婊呯暫闂?*/
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
                ResourceLocation.fromNamespaceAndPath("taczfixes", "gun_pbr"), DefaultVertexFormat.NEW_ENTITY), s -> {
                    shader = s;
                    cacheGunUniforms(s);
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "pbr_mask"), DefaultVertexFormat.POSITION_TEX), s -> maskShader = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "pbr_blur"), DefaultVertexFormat.POSITION_TEX), s -> {
                    blurShader = s;
                    uBlurDirection = s.safeGetUniform("Direction");
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "pbr_composite"), DefaultVertexFormat.POSITION_TEX), s -> {
                    compositeShader = s;
                    uCompositeStrength = s.safeGetUniform("Strength");
                    uHasScopeMask = s.safeGetUniform("HasScopeMask");
                });
    }

    private static void clearUniformCache() {
        uHasSpecular = uHasNormal = uEmissionPass = uScopeDivisionPass = null;
        uReflectionStrength = uReflectionArea = uGlareStrength = uEmissionStrength = null;
        uGlareDepthTest = null;
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
        uGlareDepthTest = s.safeGetUniform("GlareDepthTest");
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

    /** 缂傚倸鍊归幐鎼佹偤閵婏妇鈻旈柧蹇撳帨閺?闁荤姍鍐仾缂侇煈鍣ｉ弻灞筋吋閸喓銈?闂佹椿鍋€閸嬫捇鏌ょ憴鍕祷婵炲懏甯炵槐鎾诲传閸曗晙鏉?闂佸搫鍟崕濂告倻閿旇姤浜ら柛銉戝啯娅㈤梺绋跨箞閸庮垶鍩€?*/
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

    /** 闂佺绻愯ぐ澶愭閳轰絼鐔兼晬閸曨厔鍥煟椤愮喎浜鹃梺鑹邦潐瑜板啫鈻嶉幒妤佸剭闁告洦鍓氶悡娆撴煕?UV 闂佸憡顨嗗ú鎴犵礊?闂?*/
    static void setBlurDirection(float x, float y) {
        if (uBlurDirection != null) uBlurDirection.set(x, y);
    }

    /** 闂佺绻愯ぐ澶愭閳轰讲鏋栨繛鎴灻敮銊╂煕濮橆剛澧涢柛銊﹀哺閹牓鍩€椤掑嫭鍤岄柟缁樺笚閻濄倝鏌ｉ妸銉ヮ仼鐎规瓕椴搁幆鏃堟晜鐞涒€充壕?*/
    static void setBloomStrength(float value) {
        if (uCompositeStrength != null) uCompositeStrength.set(value);
    }

    static void setHasScopeMask(int value) {
        if (uHasScopeMask != null) uHasScopeMask.set(value);
    }

    static void applyMaterialState(Material material) {
        if (shader == null) {
            return;
        }
        if (material.specular() != null) {
            RenderSystem.setShaderTexture(3, material.specular());
        }
        if (material.normal() != null) {
            RenderSystem.setShaderTexture(4, material.normal());
        }
        setUniform(uHasSpecular, material.specular() == null ? 0 : 1);
        setUniform(uHasNormal, material.normal() == null ? 0 : 1);
        setUniform(uReflectionStrength, material.specular() == null ? 0f : Config.PBR_REFLECTION.get().floatValue());
        setUniform(uReflectionArea, Config.PBR_REFLECTION_AREA.get().floatValue());
        setUniform(uGlareStrength, material.specular() == null ? 0f : Config.PBR_GLARE_STRENGTH.get().floatValue());
        setUniform(uEmissionStrength, Config.PBR_EMISSION.get().floatValue());
        setUniform(uEmissionPass, 0);
        setUniform(uScopeDivisionPass, 0);
        RenderSystem.setShader(() -> shader);
        bindGlareDepth();
    }

    private static void bindGlareDepth() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        boolean mainTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == main.frameBufferId;
        if (mainTarget) {
            RenderSystem.setShaderTexture(5, main.getDepthTextureId());
        }
        setUniform(uGlareDepthTest, mainTarget ? 1 : 0);
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
        ResourceLocation specular = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), stem + "_s.png");
        ResourceLocation normal = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), stem + "_n.png");
        var resources = Minecraft.getInstance().getResourceManager();
        ResourceLocation specularLocation = resources.getResource(specular).isPresent() ? specular : null;
        return Optional.of(new Material(specularLocation,
                resources.getResource(normal).isPresent() ? normal : null));
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
        public void setupRenderState() {
            original.setupRenderState();
            bindGlareDepth();
            boolean accelerated = isAcceleratedPbrPass();
            if (accelerated) {
                applyMaterialState(material);
            }
        }

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
                bindGlareDepth();
                if (!emissionReplay) {
                    mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
                }
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

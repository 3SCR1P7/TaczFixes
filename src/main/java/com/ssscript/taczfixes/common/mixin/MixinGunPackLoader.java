package com.ssscript.taczfixes.common.mixin;

import com.ssscript.taczfixes.common.data.GunPackHooks;
import com.ssscript.taczfixes.common.data.GunPackSettings;
import com.tacz.guns.resource.GunPackLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按本模组的枪包设置过滤/排序 TaCZ 扫描到的枪包(顺序即资源覆盖优先级, 越前越优先)。 */
@Mixin(value = GunPackLoader.class, remap = false)
public abstract class MixinGunPackLoader {

    @Inject(method = "scanExtensions", at = @At("RETURN"), cancellable = true, remap = false)
    private static void taczfixes$applyGunPackSettings(Path path, CallbackInfoReturnable<List<?>> cir) {
        if (Boolean.TRUE.equals(GunPackHooks.RAW_SCAN.get())) return;
        List<?> packList = cir.getReturnValue();
        if (packList == null || packList.isEmpty()) return;
        Map<String, List<Object>> byName = new LinkedHashMap<>();
        for (Object pack : packList) {
            String name = GunPackHooks.nameOf(pack);
            if (name != null) {
                byName.computeIfAbsent(name, key -> new ArrayList<>()).add(pack);
            }
        }
        List<String> arranged = GunPackSettings.arrange(new ArrayList<>(byName.keySet()));
        List<Object> result = new ArrayList<>();
        for (String name : arranged) {
            List<Object> packs = byName.get(name);
            if (packs != null) result.addAll(packs);
        }
        cir.setReturnValue(result);
    }
}

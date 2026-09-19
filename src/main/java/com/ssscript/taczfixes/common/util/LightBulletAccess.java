package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import javax.annotation.Nullable;

/** 子弹上缓存的动态光照配置(生成时按枪械+配件算好, 随生成数据同步到客户端)。 */
public interface LightBulletAccess {

    /** 是否已在生成时捕获过配置(未捕获时应回退为按枪械 id 解析)。 */
    boolean taczfixes$isLightCaptured();

    /** 捕获到的配置, null 表示该枪械被禁用光照。 */
    @Nullable
    GunTaczFixesData.LightConfig taczfixes$getLightConfig();

    void taczfixes$setLightConfig(@Nullable GunTaczFixesData.LightConfig config, boolean captured);
}

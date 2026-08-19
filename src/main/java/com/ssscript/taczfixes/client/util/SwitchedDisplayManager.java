package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.data.SwitchedDisplayDataManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.pojo.AttachmentIndexPOJO;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SwitchedDisplayManager {
    private static final Map<ResourceLocation, List<ResourceLocation>> SWITCHED = new ConcurrentHashMap<>();
    private static final Map<String, Integer> FORMS = new ConcurrentHashMap<>();
    private static final Map<String, ClientAttachmentIndex> ALT_CACHE = new ConcurrentHashMap<>();

    private SwitchedDisplayManager() {
    }

    public static void refresh() {
        SWITCHED.clear();
        SWITCHED.putAll(SwitchedDisplayDataManager.getAll());
        FORMS.clear();
        ALT_CACHE.clear();
    }

    public static int getForm(ItemStack gun, ResourceLocation attachmentId) {
        String key = formKey(gun, attachmentId);
        if (key == null) return 0;
        Integer form = FORMS.get(key);
        if (form == null) return 0;
        List<ResourceLocation> list = getSwitched(attachmentId);
        if (list == null || list.isEmpty()) return 0;
        return Math.floorMod(form, list.size());
    }

    public static int advanceForm(ItemStack gun, ResourceLocation attachmentId) {
        List<ResourceLocation> list = getSwitched(attachmentId);
        if (list == null || list.isEmpty()) return -1;
        String key = formKey(gun, attachmentId);
        if (key == null) return -1;
        int current = FORMS.getOrDefault(key, 0);
        int next = Math.floorMod(current + 1, list.size());
        FORMS.put(key, next);
        return next;
    }

    public static Optional<ClientAttachmentIndex> getClientAttachmentIndex(ItemStack gun,
                                                                           ResourceLocation attachmentId) {
        List<ResourceLocation> list = getSwitched(attachmentId);
        if (list == null || list.isEmpty()) return Optional.empty();
        int form = getForm(gun, attachmentId);
        String cacheKey = attachmentId + "|" + form;
        ClientAttachmentIndex index = ALT_CACHE.get(cacheKey);
        if (index == null) {
            index = createAltIndex(attachmentId, list.get(form));
            if (index == null) return Optional.empty();
            ALT_CACHE.put(cacheKey, index);
        }
        return Optional.of(index);
    }

    private static List<ResourceLocation> getSwitched(ResourceLocation attachmentId) {
        List<ResourceLocation> list = SWITCHED.get(attachmentId);
        if (list == null) {
            list = SwitchedDisplayDataManager.getAll().get(attachmentId);
        }
        return list;
    }

    private static ClientAttachmentIndex createAltIndex(ResourceLocation attachmentId,
                                                        ResourceLocation switchedDisplay) {
        try {
            Optional<AttachmentIndexPOJO> source = TimelessAPI.getCommonAttachmentIndex(attachmentId)
                    .map(CommonAttachmentIndex::getPojo);
            if (!source.isPresent()) return null;
            AttachmentIndexPOJO original = source.get();
            AttachmentIndexPOJO copy = new AttachmentIndexPOJO();
            setField(copy, "name", original.getName());
            setField(copy, "tooltip", original.getTooltip());
            setField(copy, "data", original.getData());
            setField(copy, "type", original.getType());
            setField(copy, "sort", original.getSort());
            setField(copy, "hidden", original.isHidden());
            setField(copy, "display", switchedDisplay);
            ClientAttachmentIndex index = ClientAttachmentIndex.getInstance(attachmentId, copy);
            return index;
        } catch (Exception e) {
            return null;
        }
    }

    private static void setField(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = AttachmentIndexPOJO.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String formKey(ItemStack gun, ResourceLocation attachmentId) {
        if (gun == null || gun.isEmpty() || attachmentId == null) return null;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return null;
        ResourceLocation gunId = iGun.getGunId(gun);
        if (gunId == null) return null;
        return gunId + "|" + attachmentId;
    }
}
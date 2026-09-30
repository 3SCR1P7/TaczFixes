package com.ssscript.taczfixes.client.client;

import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;

/** Render-thread scope: affects the current held gun and its nested attachments. */
public final class CustomScopeArPolicy {
    private static final ThreadLocal<ArrayDeque<Boolean>> FRAMES =
            ThreadLocal.withInitial(ArrayDeque::new);

    public static void begin(ItemStack gun, ItemDisplayContext display) {
        boolean held = display.firstPerson()
                || display == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || display == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        FRAMES.get().push(held && hasCustomScope(gun));
    }

    public static void end() {
        var frames = FRAMES.get();
        if (!frames.isEmpty()) frames.pop();
        if (frames.isEmpty()) FRAMES.remove();
    }

    public static boolean requiresConventionalRendering() {
        return Boolean.TRUE.equals(FRAMES.get().peek());
    }

    private static boolean hasCustomScope(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return false;
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return false;
        for (String slot : CustomSlotManager.getSlots(igun.getGunId(gun)).keySet()) {
            // Includes physical attachments and effective factory/default attachments.
            ItemStack item = CustomSlotStorage.get(gun, slot);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
            if (attachment == null) continue;
            if (attachment.getType(item) == AttachmentType.SCOPE
                    || TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(item))
                    .map(index -> index.isScope() || index.isSight()).orElse(false)) return true;
        }
        return false;
    }

    private CustomScopeArPolicy() {}
}

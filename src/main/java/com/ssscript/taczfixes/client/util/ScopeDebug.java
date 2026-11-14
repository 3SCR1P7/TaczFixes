package com.ssscript.taczfixes.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/** 临时诊断(仅 1.21.1 调试版): 打印第三人称几何捕获与镜内 stencil 的真实状态。 */
public final class ScopeDebug {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");
    private static long lastFrameLog;
    private static long lastCaptureLog;

    private ScopeDebug() {
    }

    private static long lastGunLog;
    private static String lastGunKey = "";

    /** 打印枪上挂载: 标准 AttachmentType 槽位 + 自定义槽列表与物品。 */
    public static void gun(ItemStack gun) {
        if (gun == null || gun.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastGunLog < 2000L) return;
        lastGunLog = now;
        StringBuilder sb = new StringBuilder();
        com.tacz.guns.api.item.IGun igun = com.tacz.guns.api.item.IGun.getIGunOrNull(gun);
        if (igun != null) {
            for (com.tacz.guns.api.item.attachment.AttachmentType type
                    : com.tacz.guns.api.item.attachment.AttachmentType.values()) {
                if (type == com.tacz.guns.api.item.attachment.AttachmentType.NONE) continue;
                ItemStack item = igun.getAttachment(
                        com.tacz.guns.client.util.ClientRegistryAccess.registries(), gun, type);
                if (item.isEmpty()) {
                    item = igun.getBuiltinAttachment(gun, type);
                }
                com.tacz.guns.api.item.IAttachment ia = com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(item);
                if (ia != null) {
                    sb.append(type).append('=').append(ia.getAttachmentId(item)).append(' ');
                }
            }
        }
        StringBuilder custom = new StringBuilder();
        java.util.Map<String, com.ssscript.taczfixes.common.data.CustomSlotManager.SlotEntry> slots =
                com.ssscript.taczfixes.common.data.CustomSlotManager.getEntries(gun);
        for (String slotId : slots.keySet()) {
            ItemStack item = com.ssscript.taczfixes.common.util.CustomSlotStorage.get(gun, slotId);
            com.tacz.guns.api.item.IAttachment ia = com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(item);
            custom.append(slotId).append('=')
                    .append(ia == null ? "-" : ia.getAttachmentId(item)).append(' ');
        }
        String key = sb + "|" + custom;
        if (key.equals(lastGunKey)) return;
        lastGunKey = key;
        LOGGER.info("GUNDIAG std=[{}] custom=[{}] activeSlot={}", sb, custom,
                com.ssscript.taczfixes.client.util.ScopeSwitchState.getActiveSlot(gun));
    }

    /** 第三人称: tryRender 结果。 */
    public static void thirdPerson(net.minecraft.world.item.ItemDisplayContext ctx, Object status) {
        long now = System.currentTimeMillis();
        if (now - lastFrameLog < 1000L) return;
        lastFrameLog = now;
        LOGGER.info("TPDIAG ctx={} tryRender={}", ctx, status);
    }

    /** 配件绘制: type + id。 */
    public static void attachmentDraw(String type, Object id) {
        long now = System.currentTimeMillis();
        if (now - lastFrameLog < 1000L) return;
        lastFrameLog = now;
        LOGGER.info("ATTDRAW type={} id={}", type, id);
    }
    private static long lastNote;
    public static void note(String tag, Object a, Object b, Object c) {
        long now = System.currentTimeMillis();
        if (now - lastNote < 1000L) return;
        lastNote = now;
        LOGGER.info("NOTEDIAG {} a={} b={} c={}", tag, a, b, c);
    }

    /** 第三人称: 自定义槽配件写入 SBM 几何时调用。 */
    public static void captureSlot(String slotId, net.minecraft.resources.ResourceLocation id, boolean sourceEmpty,
                                   boolean prepared, boolean external, int bone, String node, int totalSlots) {
        long now = System.currentTimeMillis();
        if (now - lastCaptureLog < 1000L) return;
        lastCaptureLog = now;
        LOGGER.info("SBMDIAG slot={} id={} srcEmpty={} prepared={} external={} node={} bone={} totalSlots={}",
                slotId, id, sourceEmpty, prepared, external, node, bone, totalSlots);
    }

    /** 每帧一次: stencil 真实状态与中心像素。 */
    public static void frame(String tag, ItemStack gun, boolean lensView, boolean gateA) {
        long now = System.currentTimeMillis();
        if (now - lastFrameLog < 1000L) return;
        lastFrameLog = now;
        Minecraft mc = Minecraft.getInstance();
        int[] packed = readCenter(mc);
        LOGGER.info("CULLDIAG tag={} lensView={} gateA={} stencilTest={} func={} ref={} centerStencil={} centerDepthTop={} targetStencil={}",
                tag, lensView, gateA,
                GL11.glIsEnabled(GL11.GL_STENCIL_TEST),
                GL11.glGetInteger(GL11.GL_STENCIL_FUNC),
                GL11.glGetInteger(GL11.GL_STENCIL_REF),
                packed[0], packed[1],
                mc.getMainRenderTarget().isStencilEnabled());
    }

    private static int[] readCenter(Minecraft mc) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buf = stack.malloc(4);
            int w = mc.getMainRenderTarget().width;
            int h = mc.getMainRenderTarget().height;
            GL11.glReadPixels(w / 2, h / 2, 1, 1, GL30.GL_DEPTH_STENCIL,
                    GL30.GL_UNSIGNED_INT_24_8, buf);
            int v = buf.getInt(0);
            return new int[]{v & 0xFF, (v >>> 24) & 0xFF};
        } catch (Throwable t) {
            return new int[]{-1, -1};
        }
    }
}

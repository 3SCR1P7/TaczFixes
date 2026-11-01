package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.ssscript.taczfixes.client.client.ClientArGate;
import com.ssscript.taczfixes.common.config.Config;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;

public final class GunGlintContext {
    private static final float GLINT_SCALE = 0.22F;
    private static final float GLINT_FLOW = 0.05F;
    private static final ThreadLocal<Deque<State>> STATES = new ThreadLocal<>();
    private static final ThreadLocal<Integer> SUPPRESS = new ThreadLocal<>();

    private GunGlintContext() {
    }

    public static void begin(ItemStack stack) {
        double configured = Config.GUN_ENCHANT_GLINT_STRENGTH.get();
        boolean active = stack != null && !stack.isEmpty() && stack.isEnchanted() && configured > 0.0D;
        Deque<State> states = STATES.get();
        if (states == null) {
            states = new ArrayDeque<>();
            STATES.set(states);
        }
        states.push(new State(active, active ? (float) configured : 0.0F));
        if (active) {
            ClientArGate.pushForcedVanilla();
        }
    }

    public static MultiBufferSource wrapSource(MultiBufferSource source) {
        if (source == null || source instanceof GlintSource) {
            return source;
        }
        State state = current();
        if (state == null || !state.active) {
            return source;
        }
        return new GlintSource(source);
    }

    public static void suppress() {
        Integer depth = SUPPRESS.get();
        SUPPRESS.set(depth == null ? 1 : depth + 1);
    }

    public static void unsuppress() {
        Integer depth = SUPPRESS.get();
        if (depth == null || depth <= 1) {
            SUPPRESS.remove();
        } else {
            SUPPRESS.set(depth - 1);
        }
    }

    private static State current() {
        Deque<State> states = STATES.get();
        return states == null ? null : states.peek();
    }

    private static VertexConsumer wrap(MultiBufferSource source, RenderType type) {
        VertexConsumer base = source.getBuffer(type);
        State state = current();
        if (state == null || !state.active || type == null
                || type == RenderType.entityGlint() || SUPPRESS.get() != null) {
            return base;
        }
        state.source = source;
        VertexConsumer glint = source.getBuffer(RenderType.entityGlint());
        return new GunGlintVertexConsumer(base, glint, GLINT_SCALE, GLINT_FLOW);
    }

    public static void end() {
        Deque<State> states = STATES.get();
        if (states == null) {
            return;
        }
        State state = states.pop();
        if (states.isEmpty()) {
            STATES.remove();
        }
        if (!state.active) {
            return;
        }
        ClientArGate.popForcedVanilla();
        float previousAlpha = RenderSystem.getShaderGlintAlpha();
        try {
            RenderSystem.setShaderGlintAlpha(previousAlpha * state.strength);
            if (state.source instanceof MultiBufferSource.BufferSource buffers) {
                buffers.endBatch(RenderType.entityGlint());
            }
        } finally {
            RenderSystem.setShaderGlintAlpha(previousAlpha);
        }
    }

    private static final class GlintSource extends MultiBufferSource.BufferSource {
        private final MultiBufferSource delegate;

        private GlintSource(MultiBufferSource delegate) {
            super(new ByteBufferBuilder(1), Object2ObjectSortedMaps.emptyMap());
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return wrap(delegate, type);
        }

        @Override
        public void endLastBatch() {
            if (delegate instanceof BufferSource buffers) {
                buffers.endLastBatch();
            }
        }

        @Override
        public void endBatch() {
            if (delegate instanceof BufferSource buffers) {
                buffers.endBatch();
            }
        }

        @Override
        public void endBatch(RenderType type) {
            if (delegate instanceof BufferSource buffers) {
                buffers.endBatch(type);
            }
        }
    }

    private static final class State {
        private final boolean active;
        private final float strength;
        private MultiBufferSource source;

        private State(boolean active, float strength) {
            this.active = active;
            this.strength = strength;
        }
    }
}


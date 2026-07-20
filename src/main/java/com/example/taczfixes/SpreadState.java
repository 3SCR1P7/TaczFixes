package com.example.taczfixes;

public class SpreadState {
    private static int stacks = 0;
    private static long lastShotTime = 0;

    public static void onShot() {
        stacks = Math.min(stacks + 1, Config.SPREAD_RAMP_MAX_STACKS.get());
        lastShotTime = System.currentTimeMillis();
    }

    public static float getMultiplier() {
        int currentStacks = Math.max(0, stacks - 1);
        return 1.0f + currentStacks * Config.SPREAD_RAMP_INCREMENT.get().floatValue();
    }

    public static void tick() {
        if (stacks <= 0) return;
        if (System.currentTimeMillis() - lastShotTime >= Config.SPREAD_RAMP_DECAY_DELAY_MS.get()) {
            float increment = Config.SPREAD_RAMP_INCREMENT.get().floatValue();
            float decay = Config.SPREAD_RAMP_DECAY.get().floatValue();
            int stacksToRemove = (int) Math.ceil(decay / increment);
            if (stacksToRemove < 1) stacksToRemove = 1;
            stacks = Math.max(0, stacks - stacksToRemove);
        }
    }
}

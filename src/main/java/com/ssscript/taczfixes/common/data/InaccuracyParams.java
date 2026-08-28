package com.ssscript.taczfixes.common.data;

public class InaccuracyParams {
    public final int maxStack;
    public final long cooldownDelay;
    public final double cooldownSpeed;
    public final double shotPercent;
    public final double shotAddend;

    public InaccuracyParams(int maxStack, long cooldownDelay, double cooldownSpeed,
                            double shotPercent, double shotAddend) {
        this.maxStack = maxStack;
        this.cooldownDelay = cooldownDelay;
        this.cooldownSpeed = cooldownSpeed;
        this.shotPercent = shotPercent;
        this.shotAddend = shotAddend;
    }
}

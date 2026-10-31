package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.common.util.ChargeStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public final class ChargeCapabilityProvider implements ICapabilityProvider<ItemStack, Void, IEnergyStorage> {
    @Override
    public @Nullable IEnergyStorage getCapability(ItemStack stack, Void context) {
        return ChargeStorage.isChargeGun(stack) ? new ChargeEnergyStorage(stack) : null;
    }

    private static final class ChargeEnergyStorage implements IEnergyStorage {
        private ItemStack stack;

        private ChargeEnergyStorage(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (stack == null || maxReceive <= 0 || !canReceive()) {
                return 0;
            }
            int current = ChargeStorage.get(this.stack);
            int accepted = Math.min(maxReceive, ChargeStorage.getMax(this.stack) - current);
            if (accepted <= 0) {
                return 0;
            }
            if (!simulate) {
                ChargeStorage.set(this.stack, current + accepted);
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (stack == null || maxExtract <= 0 || !canExtract()) {
                return 0;
            }
            int current = ChargeStorage.get(this.stack);
            int extracted = Math.min(maxExtract, current);
            if (extracted <= 0) {
                return 0;
            }
            if (!simulate) {
                ChargeStorage.set(this.stack, current - extracted);
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return stack == null ? 0 : ChargeStorage.get(this.stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return stack == null ? 0 : ChargeStorage.getMax(this.stack);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return stack != null && ChargeStorage.isChargeGun(this.stack);
        }
    }
}


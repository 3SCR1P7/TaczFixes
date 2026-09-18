package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.common.util.ChargeStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 让带 charge 配置的枪械物品支持 FE 充能(充电站等)。 */
public final class ChargeCapabilityProvider implements ICapabilityProvider {
    private final LazyOptional<IEnergyStorage> storage;

    public ChargeCapabilityProvider(ItemStack stack) {
        this.storage = LazyOptional.of(() -> new ChargeEnergyStorage(stack));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ENERGY ? this.storage.cast() : LazyOptional.empty();
    }

    private static final class ChargeEnergyStorage implements IEnergyStorage {
        private final ItemStack stack;

        private ChargeEnergyStorage(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0 || !canReceive()) {
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
            if (maxExtract <= 0 || !canExtract()) {
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
            return ChargeStorage.get(this.stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return ChargeStorage.getMax(this.stack);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return ChargeStorage.isChargeGun(this.stack);
        }
    }
}

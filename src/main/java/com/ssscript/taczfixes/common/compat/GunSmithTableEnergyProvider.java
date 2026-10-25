package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.common.util.GunSmithTableEnergy;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GunSmithTableEnergyProvider implements ICapabilityProvider {
    private final LazyOptional<IEnergyStorage> storage;

    public GunSmithTableEnergyProvider(GunSmithTableBlockEntity table) {
        this.storage = LazyOptional.of(() -> new Storage(table));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                      @Nullable Direction side) {
        return capability == ForgeCapabilities.ENERGY ? this.storage.cast() : LazyOptional.empty();
    }

    private static final class Storage implements IEnergyStorage {
        private final GunSmithTableBlockEntity table;

        private Storage(GunSmithTableBlockEntity table) {
            this.table = table;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }
            int capacity = GunSmithTableEnergy.capacity(this.table);
            int current = GunSmithTableEnergy.get(this.table);
            int accepted = Math.min(maxReceive, Math.max(0, capacity - current));
            if (accepted > 0 && !simulate) {
                GunSmithTableEnergy.set(this.table, current + accepted);
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) {
                return 0;
            }
            int current = GunSmithTableEnergy.get(this.table);
            int extracted = Math.min(maxExtract, current);
            if (extracted > 0 && !simulate) {
                GunSmithTableEnergy.set(this.table, current - extracted);
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return GunSmithTableEnergy.get(this.table);
        }

        @Override
        public int getMaxEnergyStored() {
            return GunSmithTableEnergy.capacity(this.table);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return GunSmithTableEnergy.capacity(this.table) > 0;
        }
    }
}

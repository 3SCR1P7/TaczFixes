package com.ssscript.taczfixes.common.compat;

import com.ssscript.taczfixes.common.util.GunSmithTableEnergy;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public final class GunSmithTableEnergyProvider
        implements ICapabilityProvider<GunSmithTableBlockEntity, Direction, IEnergyStorage> {
    @Override
    public @Nullable IEnergyStorage getCapability(GunSmithTableBlockEntity table, Direction side) {
        return new Storage(table);
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

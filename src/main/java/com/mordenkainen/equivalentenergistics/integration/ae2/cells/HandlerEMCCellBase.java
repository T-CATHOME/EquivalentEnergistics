package com.mordenkainen.equivalentenergistics.integration.ae2.cells;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.mordenkainen.equivalentenergistics.util.IEMCStorage;
import net.minecraft.nbt.NBTTagCompound;

public abstract class HandlerEMCCellBase implements IMEInventoryHandler<IAEItemStack>, IEMCStorage {

    protected final ISaveProvider saveProvider;
    protected double currentEMC = 0;
    protected double maxEMC = 0;

    public HandlerEMCCellBase(final ISaveProvider saveProvider) {
        this.saveProvider = saveProvider;
    }

    @Override
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable type, final BaseActionSource src) {
        return input;
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final BaseActionSource src) {
        return null;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> stacks) {
        return stacks;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(final IAEItemStack input) {
        return false;
    }

    @Override
    public boolean canAccept(final IAEItemStack input) {
        return false;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int pass) {
        return false;
    }

    public abstract int getCellStatus();

    @Override
    public double getCurrentEMC() {
        return currentEMC;
    }

    @Override
    public double getMaxEMC() {
        return maxEMC;
    }

    @Override
    public double getAvail() {
        return maxEMC - currentEMC;
    }

    @Override
    public double addEMC(double amount) {
        double toAdd = Math.min(amount, getAvail());
        currentEMC += toAdd;
        if (toAdd > 0) {
            saveState();
        }
        return toAdd;
    }

    @Override
    public double extractEMC(double amount) {
        double toExtract = Math.min(amount, currentEMC);
        currentEMC -= toExtract;
        if (toExtract > 0) {
            saveState();
        }
        return toExtract;
    }

    public void setEMC(double emc) {
        double oldEMC = currentEMC;
        currentEMC = Math.min(Math.max(emc, 0), maxEMC);
        if (currentEMC != oldEMC) {
            saveState();
        }
    }

    // -- EMC NBT持久化 --
    private static final String NBT_CURRENT_EMC = "EE_CurrentEMC";
    private static final String NBT_MAX_EMC = "EE_MaxEMC";

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setDouble(NBT_CURRENT_EMC, currentEMC);
        nbt.setDouble(NBT_MAX_EMC, maxEMC);
    }

    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBT_CURRENT_EMC)) {
            currentEMC = nbt.getDouble(NBT_CURRENT_EMC);
        }
        if (nbt.hasKey(NBT_MAX_EMC)) {
            maxEMC = nbt.getDouble(NBT_MAX_EMC);
        }
    }

    protected void saveState() {
        if (saveProvider != null) {
            saveProvider.saveChanges(this);
        }
    }
}

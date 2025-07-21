package com.mordenkainen.equivalentenergistics.integration.ae2.cells;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

import com.mordenkainen.equivalentenergistics.integration.ae2.cache.storage.EMCGridCellHandler;
import com.mordenkainen.equivalentenergistics.util.IEMCStorage;

public abstract class HandlerEMCCellBase implements IMEInventoryHandler<IAEItemStack>, IEMCStorage, 
                                                  EMCGridCellHandler.IWrappedEMCHandler {

    protected final ISaveProvider saveProvider;
    private Runnable changeCallback;
    private int lastStatus = -1;
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
    
    // ========== IEMCStorage 接口实现 ==========
    
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
            notifyStateChanged();
        }
        return toAdd;
    }
    
    @Override
    public double extractEMC(double amount) {
        double toExtract = Math.min(amount, currentEMC);
        currentEMC -= toExtract;
        if (toExtract > 0) {
            notifyStateChanged();
        }
        return toExtract;
    }
    
    public void setEMC(double emc) {
        double oldEMC = currentEMC;
        currentEMC = Math.min(Math.max(emc, 0), maxEMC);
        if (currentEMC != oldEMC) {
            notifyStateChanged();
        }
    }
    
    // ========== EMC 状态回调机制 ==========
    
    /**
     * 设置状态变更回调函数
     * @param callback 状态变化时触发的回调
     */
    public void setChangeCallback(Runnable callback) {
        this.changeCallback = callback;
    }
    
    /**
     * 触发状态变更回调
     */
    protected void notifyStateChanged() {
        if (changeCallback != null) {
            changeCallback.run();
        }
        
        // 检测单元状态变化（用于驱动器状态显示）
        int currentStatus = getCellStatus();
        if (currentStatus != lastStatus) {
            lastStatus = currentStatus;
            requestClientUpdate();
        }
    }
    
    /**
     * 请求客户端更新（模拟原blinkCell功能）
     */
    protected void requestClientUpdate() {
        // 默认实现，子类可以覆盖
        if (saveProvider != null) {
            saveProvider.saveChanges(this);
        }
    }
    
    // ========== IWrappedEMCHandler 接口实现 ==========
    
    @Override
    public IMEInventoryHandler<IAEItemStack> getWrappedHandler() {
        return this;
    }
}

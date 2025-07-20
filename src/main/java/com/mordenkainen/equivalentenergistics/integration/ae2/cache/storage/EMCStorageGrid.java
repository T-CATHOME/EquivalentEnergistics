package com.mordenkainen.equivalentenergistics.integration.ae2.cache.storage;


import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPostCacheConstruction;
import appeng.api.networking.storage.IStorageGrid;

import com.mordenkainen.equivalentenergistics.util.EMCPool;

public class EMCStorageGrid implements IEMCStorageGrid {

    private final IGrid grid;
    private final EMCPool pool = new EMCPool();
    private final EMCGridCellHandler cellHandler = new EMCGridCellHandler(this);
    private final EMCGridCrystalHandler crystalHandler = new EMCGridCrystalHandler(this);

    // 添加状态标记，用于优化更新
    private boolean needsCellUpdate = true;
    private double lastCellEMC = -1;
    private double lastCellMaxEMC = -1;

    public EMCStorageGrid(final IGrid grid) {
        this.grid = grid;
    }

    @MENetworkEventSubscribe
    public void afterCacheConstruction(final MENetworkPostCacheConstruction cacheConstruction) {
        ((IStorageGrid) grid.getCache(IStorageGrid.class)).registerCellProvider(crystalHandler);
    }

    @MENetworkEventSubscribe
    public void cellUpdate(final MENetworkCellArrayUpdate cellUpdate) {
        // 标记需要更新，而不是立即计算
        needsCellUpdate = true;
    }

    @Override
    public void onUpdateTick() {
        // 只在需要时更新
        if (needsCellUpdate) {
            updateCellEMC();
            needsCellUpdate = false;
        }
        crystalHandler.updateDisplay();
    }

    private void updateCellEMC() {
        // 直接调用cellHandler的公共方法来获取总EMC
        double totalEMC = cellHandler.calculateTotalCurrentEMC();
        double totalMaxEMC = cellHandler.calculateTotalMaxEMC();
        
        // 只有当值变化时才更新
        if (totalEMC != lastCellEMC || totalMaxEMC != lastCellMaxEMC) {
            pool.setCurrentEMC(totalEMC);
            pool.setMaxEMC(totalMaxEMC);
            lastCellEMC = totalEMC;
            lastCellMaxEMC = totalMaxEMC;
        }
    }

    @Override
    public void removeNode(final IGridNode gridNode, final IGridHost machine) {
        cellHandler.removeNode(gridNode, machine);
        needsCellUpdate = true; // 标记需要更新
    }

    @Override
    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        cellHandler.addNode(gridNode, machine);
        needsCellUpdate = true; // 标记需要更新
    }

    @Override
    public IGrid getGrid() {
        return grid;
    }

    @Override
    public double getCurrentEMC() {
        // 优先返回缓存值
        return lastCellEMC >= 0 ? lastCellEMC : pool.getCurrentEMC();
    }

    @Override
    public double getMaxEMC() {
        // 优先返回缓存值
        return lastCellMaxEMC >= 0 ? lastCellMaxEMC : pool.getMaxEMC();
    }

    @Override
    public double getAvail() {
        return getMaxEMC() - getCurrentEMC();
    }

    @Override
    public boolean isFull() {
        return getCurrentEMC() >= getMaxEMC();
    }

    @Override
    public boolean isEmpty() {
        return getCurrentEMC() <= 0;
    }

    @Override
    public void setCurrentEMC(final double currentEMC) {
        // 不再直接设置，通过cellHandler分配
        distributeEMC(currentEMC - getCurrentEMC(), Actionable.MODULATE);
    }

    @Override
    public void setMaxEMC(final double maxEMC) {
        // 最大EMC由存储单元决定，不直接设置
    }

    @Override
    public double addEMC(final double emc) {
        return addEMC(emc, Actionable.MODULATE);
    }

    @Override
    public double addEMC(final double emc, final Actionable mode) {
        return cellHandler.injectEMC(emc, mode);
    }

    @Override
    public double extractEMC(final double emc) {
        return extractEMC(emc, Actionable.MODULATE);
    }

    @Override
    public double extractEMC(final double emc, final Actionable mode) {
        return cellHandler.extractEMC(emc, mode);
    }
    
    // 新增EMC分配方法
    private double distributeEMC(double amount, Actionable mode) {
        if (amount > 0) {
            return addEMC(amount, mode);
        } else if (amount < 0) {
            return -extractEMC(-amount, mode);
        }
        return 0;
    }

    public void markDirty() {
        crystalHandler.markDirty();
        needsCellUpdate = true;
    }
}

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

    // 自动刷新相关
    private boolean needInitialRefresh = true;
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
        needsCellUpdate = true;
    }

    @Override
    public void onUpdateTick() {
        // --- 自动刷新：首次进入世界主动刷新EMC池和终端显示 ---
        if (needInitialRefresh) {
            cellHandler.updatePoolState(); // 主动刷新池和终端显示
            needInitialRefresh = false;
        }

        // 只在需要时更新
        if (needsCellUpdate) {
            updateCellEMC();
            needsCellUpdate = false;
        }
        crystalHandler.updateDisplay();
    }

    private void updateCellEMC() {
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
        needsCellUpdate = true;
        needInitialRefresh = true;
    }

    @Override
    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        cellHandler.addNode(gridNode, machine);
        needsCellUpdate = true;
        needInitialRefresh = true;
    }

    @Override
    public IGrid getGrid() {
        return grid;
    }

    @Override
    public double getCurrentEMC() {
        return lastCellEMC >= 0 ? lastCellEMC : pool.getCurrentEMC();
    }

    @Override
    public double getMaxEMC() {
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

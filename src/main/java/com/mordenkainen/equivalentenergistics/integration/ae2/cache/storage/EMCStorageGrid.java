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
    private final EMCGridCellHandler cellHandler = new EMCGridCellHandler(this);
    private final EMCGridCrystalHandler crystalHandler = new EMCGridCrystalHandler(this);

    // 用于强制重新扫描节点
    private boolean initialized = false;
    private boolean needsCellUpdate = true;

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
        if (!initialized) {
            initializeGrid();
            initialized = true;
        }
        if (needsCellUpdate) {
            needsCellUpdate = false;
            // 主动刷新终端显示
            crystalHandler.updateDisplay();
        }
    }

    private void initializeGrid() {
        for (IGridNode node : grid.getNodes()) {
            IGridHost host = node.getGridBlock().getMachine();
            if (host != null) {
                cellHandler.addNode(node, host);
            }
        }
    }

    @Override
    public void removeNode(final IGridNode gridNode, final IGridHost machine) {
        cellHandler.removeNode(gridNode, machine);
        needsCellUpdate = true;
        initialized = false;
    }

    @Override
    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        cellHandler.addNode(gridNode, machine);
        needsCellUpdate = true;
        initialized = false;
    }

    @Override
    public IGrid getGrid() {
        return grid;
    }

    @Override
    public double getCurrentEMC() {
        return cellHandler.calculateTotalCurrentEMC();
    }

    @Override
    public double getMaxEMC() {
        return cellHandler.calculateTotalMaxEMC();
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
        // 不直接设置，由单元分配
        double diff = currentEMC - getCurrentEMC();
        distributeEMC(diff, Actionable.MODULATE);
    }

    @Override
    public void setMaxEMC(final double maxEMC) {
        // 最大EMC由所有单元决定
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

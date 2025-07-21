package com.mordenkainen.equivalentenergistics.integration.ae2.cache.storage;

import java.util.ArrayList;
import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.mordenkainen.equivalentenergistics.integration.ae2.cells.HandlerEMCCellBase;

public class EMCGridCellHandler {

    private final EMCStorageGrid hostGrid;
    protected final List<ICellProvider> driveBays = new ArrayList<ICellProvider>();

    public EMCGridCellHandler(final EMCStorageGrid hostGrid) {
        this.hostGrid = hostGrid;
    }

    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICellProvider) {
            ICellProvider provider = (ICellProvider) machine;
            if (!driveBays.contains(provider)) {
                driveBays.add(provider);
            }
            // 已移除 setChangeCallback 相关代码
        }
    }

    public void cellUpdate(final MENetworkCellArrayUpdate cellUpdate) {
        // 由 EMCStorageGrid 调用 updateCellEMC 统一处理
    }

    public void removeNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICellProvider) {
            driveBays.remove(machine);
        }
    }

    public double injectEMC(final double emc, final Actionable mode) {
        final double toAdd = Math.min(emc, hostGrid.getAvail());
        if (mode != Actionable.MODULATE) {
            return toAdd;
        }
        double added = 0;
        for (final ICellProvider provider : driveBays) {
            for (IMEInventoryHandler<IAEItemStack> cell : getCellHandlers(provider)) {
                final HandlerEMCCellBase handler = getHandler(cell);
                if (handler != null) {
                    added += handler.addEMC(toAdd - added);
                    if (added == toAdd) {
                        break;
                    }
                }
            }
        }
        hostGrid.markDirty();
        return added;
    }

    public double extractEMC(final double emc, final Actionable mode) {
        final double toExtract = Math.min(emc, calculateTotalCurrentEMC());
        if (mode != Actionable.MODULATE) {
            return toExtract;
        }
        double extracted = 0;
        for (final ICellProvider provider : driveBays) {
            for (IMEInventoryHandler<IAEItemStack> cell : getCellHandlers(provider)) {
                final HandlerEMCCellBase handler = getHandler(cell);
                if (handler != null) {
                    extracted += handler.extractEMC(toExtract - extracted);
                    if (extracted == toExtract) {
                        break;
                    }
                }
            }
        }
        hostGrid.markDirty();
        return extracted;
    }

    @SuppressWarnings("unchecked")
    private List<IMEInventoryHandler<IAEItemStack>> getCellHandlers(ICellProvider provider) {
        return (List<IMEInventoryHandler<IAEItemStack>>) (List<?>) provider.getCellArray(StorageChannel.ITEMS);
    }

    public double calculateTotalCurrentEMC() {
        double total = 0;
        for (ICellProvider provider : driveBays) {
            for (IMEInventoryHandler<IAEItemStack> cell : getCellHandlers(provider)) {
                HandlerEMCCellBase handler = getHandler(cell);
                if (handler != null) {
                    total += handler.getCurrentEMC();
                }
            }
        }
        return total;
    }

    public double calculateTotalMaxEMC() {
        double total = 0;
        for (ICellProvider provider : driveBays) {
            for (IMEInventoryHandler<IAEItemStack> cell : getCellHandlers(provider)) {
                HandlerEMCCellBase handler = getHandler(cell);
                if (handler != null) {
                    total += handler.getMaxEMC();
                }
            }
        }
        return total;
    }

    private HandlerEMCCellBase getHandler(final IMEInventoryHandler<IAEItemStack> cell) {
        if (cell instanceof HandlerEMCCellBase) {
            return (HandlerEMCCellBase) cell;
        }
        return null;
    }
}

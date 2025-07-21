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
import com.mordenkainen.equivalentenergistics.util.CommonUtils;

public class EMCGridCellHandle
    r {

    private final EMCStorageGrid hostGrid;
    private final List<ICellProvider> driveBays = new ArrayList<ICellProvider>();

    // 新增接口用于安全访问包装的处理器
    public interface IWrappedEMCHandler {
        IMEInventoryHandler<IAEItemStack> getWrappedHandler();
    }

    public EMCGridCellHandler(final EMCStorageGrid hostGrid) {
        this.hostGrid = hostGrid;
    }

    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICellProvider) {
            ICellProvider provider = (ICellProvider) machine;
            driveBays.add(provider);
            
            // 绑定状态更新回调
            for (IMEInventoryHandler<IAEItemStack> handler : getCellHandlers(provider)) {
                HandlerEMCCellBase emcHandler = getHandler(handler);
                if (emcHandler != null) {
                    emcHandler.setChangeCallback(() -> {
                        updatePoolState();
                        hostGrid.markDirty();
                    });
                }
            }
        }
    }

    public void cellUpdate(final MENetworkCellArrayUpdate cellUpdate) {
        updatePoolState();
    }

    public void removeNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICellProvider && driveBays.remove(machine)) {
            updatePoolState();
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

        // 更新网格状态
        hostGrid.markDirty();
        return added;
    }

    public double extractEMC(final double emc, final Actionable mode) {
        // 修复递归问题：直接从存储单元提取，不再调用StorageGrid
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

        // 更新网格状态
        hostGrid.markDirty();
        return extracted;
    }

    private List<IMEInventoryHandler<IAEItemStack>> getCellHandlers(ICellProvider provider) {
        // 安全类型转换
        @SuppressWarnings("unchecked")
        List<IMEInventoryHandler<IAEItemStack>> cells = 
            (List<IMEInventoryHandler<IAEItemStack>>) (List<?>) provider.getCellArray(StorageChannel.ITEMS);
        return cells;
    }

    // 添加公共方法供StorageGrid调用
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
        // 直接类型检查作为首选方案
        if (cell instanceof HandlerEMCCellBase) {
            return (HandlerEMCCellBase) cell;
        }
        
        // 使用接口探测替代反射
        if (cell instanceof IWrappedEMCHandler) {
            IMEInventoryHandler<IAEItemStack> unwrapped = ((IWrappedEMCHandler) cell).getWrappedHandler();
            if (unwrapped instanceof HandlerEMCCellBase) {
                return (HandlerEMCCellBase) unwrapped;
            }
        }
        
        // 尝试递归解包
        HandlerEMCCellBase handler = recursivelyUnwrap(cell, 0, 3);
        if (handler != null) {
            return handler;
        }
        
        return null;
    }

    private HandlerEMCCellBase recursivelyUnwrap(IMEInventoryHandler<IAEItemStack> handler, int depth, int maxDepth) {
        if (depth >= maxDepth) return null;
        
        try {
            // 尝试访问常见包装字段
            for (String fieldName : new String[]{"internal", "delegate", "wrapped"}) {
                try {
                    java.lang.reflect.Field field = handler.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object inner = field.get(handler);
                    
                    if (inner instanceof IMEInventoryHandler) {
                        @SuppressWarnings("unchecked")
                        IMEInventoryHandler<IAEItemStack> innerHandler = (IMEInventoryHandler<IAEItemStack>) inner;
                        
                        if (innerHandler instanceof HandlerEMCCellBase) {
                            return (HandlerEMCCellBase) innerHandler;
                        }
                        
                        HandlerEMCCellBase result = recursivelyUnwrap(innerHandler, depth + 1, maxDepth);
                        if (result != null) return result;
                    }
                } catch (NoSuchFieldException e) {
                    // 忽略，尝试下一个字段名
                }
            }
            
            // 尝试访问AE2标准包装
            java.lang.reflect.Field[] fields = handler.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (IMEInventoryHandler.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object inner = field.get(handler);
                    
                    if (inner instanceof IMEInventoryHandler) {
                        @SuppressWarnings("unchecked")
                        IMEInventoryHandler<IAEItemStack> innerHandler = (IMEInventoryHandler<IAEItemStack>) inner;
                        
                        if (innerHandler instanceof HandlerEMCCellBase) {
                            return (HandlerEMCCellBase) innerHandler;
                        }
                        
                        HandlerEMCCellBase result = recursivelyUnwrap(innerHandler, depth + 1, maxDepth);
                        if (result != null) return result;
                    }
                }
            }
        } catch (Exception e) {
            CommonUtils.debugLog("Error while unwrapping handler", e);
        }
        
        return null;
    }

     public void updatePoolState() {
        double totalEMC = calculateTotalCurrentEMC();
        double maxEMC = calculateTotalMaxEMC();
        
        hostGrid.setMaxEMC(maxEMC);
        hostGrid.setCurrentEMC(totalEMC);
        hostGrid.markDirty();
    }
}

package com.mordenkainen.equivalentenergistics.integration.ae2.tiles;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.MachineSource;
import appeng.api.util.DimensionalCoord;
import com.mordenkainen.equivalentenergistics.blocks.base.tile.EqETileBase;
import com.mordenkainen.equivalentenergistics.integration.ae2.grid.AEProxy;
import com.mordenkainen.equivalentenergistics.integration.ae2.grid.GridAccessException;
import com.mordenkainen.equivalentenergistics.integration.ae2.grid.GridUtils;
import com.mordenkainen.equivalentenergistics.integration.ae2.grid.IAEProxyHost;
import com.mordenkainen.equivalentenergistics.util.CommonUtils;
import com.mordenkainen.equivalentenergistics.integration.ae2.cells.HandlerEMCCellBase;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import cpw.mods.fml.common.FMLCommonHandler;

public abstract class TileAEBase extends EqETileBase implements IAEProxyHost {

    private final static String POWERED_TAG = "powered";
    private final static String ACTIVE_TAG = "active";
    private final static String EE_CELL_DATA = "EE_CellData";

    protected final AEProxy gridProxy;
    protected MachineSource mySource;
    protected boolean active;
    protected boolean powered;

    public TileAEBase(final ItemStack repItem) {
        super();
        mySource = new MachineSource(this);
        gridProxy = new AEProxy(this, "node0", repItem, true);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        IAEProxyHost.super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        IAEProxyHost.super.invalidate();
    }

    @Override
    public void validate() {
        super.validate();
        IAEProxyHost.super.validate();
    }

    @Override
    public void onReady() {
        IAEProxyHost.super.onReady();
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        IAEProxyHost.super.readFromNBT(data);

        // ----------- 读取 EMC 存储单元状态 -----------
        if (data.hasKey(EE_CELL_DATA)) {
            NBTTagList cellList = data.getTagList(EE_CELL_DATA, 10); // 10 = NBTTagCompound
            for (int i = 0; i < cellList.tagCount(); i++) {
                NBTTagCompound cellData = cellList.getCompoundTagAt(i);
                int slot = cellData.getInteger("Slot");
                IMEInventoryHandler<IAEItemStack> handler = getCellHandlerSafe(slot);
                if (handler instanceof HandlerEMCCellBase) {
                    ((HandlerEMCCellBase) handler).readFromNBT(cellData);
                }
            }
        }
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        IAEProxyHost.super.writeToNBT(data);

        // ----------- 保存 EMC 存储单元状态 -----------
        NBTTagList cellList = new NBTTagList();
        int cellCount = getCellCountSafe();
        for (int i = 0; i < cellCount; i++) {
            IMEInventoryHandler<IAEItemStack> handler = getCellHandlerSafe(i);
            if (handler instanceof HandlerEMCCellBase) {
                NBTTagCompound cellData = new NBTTagCompound();
                cellData.setInteger("Slot", i);
                ((HandlerEMCCellBase) handler).writeToNBT(cellData);
                cellList.appendTag(cellData);
            }
        }
        if (cellList.tagCount() > 0) {
            data.setTag(EE_CELL_DATA, cellList);
        }
    }

    // ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
    // 你需要在实际的驱动器类(比如 TileEMCDrive 或 EMC存储箱的TileEntity)中实现以下两个方法。
    // 例如，如果你的驱动器有10个槽位，并能通过 inv.getCellHandler(i) 获取 handler，可以：
    // @Override
    // protected int getCellCountSafe() { return 10; }
    // @Override
    // protected IMEInventoryHandler<IAEItemStack> getCellHandlerSafe(int slot) { return inv.getCellHandler(slot); }
    protected int getCellCountSafe() {
        return 0;
    }
    protected IMEInventoryHandler<IAEItemStack> getCellHandlerSafe(int slot) {
        return null;
    }
    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑

    @Override
    public AEProxy getProxy() {
        return gridProxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void securityBreak() {
        CommonUtils.destroyAndDrop(worldObj, xCoord, yCoord, zCoord);
    }

    protected boolean checkPermissions(final EntityPlayer player) {
        try {
            final ISecurityGrid sGrid = GridUtils.getSecurity(getProxy());

            return sGrid.hasPermission(player, SecurityPermissions.INJECT)
                    && sGrid.hasPermission(player, SecurityPermissions.EXTRACT)
                    && sGrid.hasPermission(player, SecurityPermissions.BUILD);
        } catch (final GridAccessException e) {
            CommonUtils.debugLog("TileAEBase:checkPermissions: Error accessing grid:", e);
        }
        return true;
    }

    @Override
    public boolean isActive() {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            return active;
        } else {
            return gridProxy.isReady() && gridProxy.isActive();
        }
    }

    @Override
    public boolean isPowered() {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            return powered;
        } else {
            return gridProxy.isReady() && gridProxy.isPowered();
        }
    }

    @Override
    protected void getPacketData(final NBTTagCompound nbttagcompound) {
        nbttagcompound.setBoolean(POWERED_TAG, isPowered());
        nbttagcompound.setBoolean(ACTIVE_TAG, isActive());
    }

    @Override
    protected boolean readPacketData(final NBTTagCompound nbttagcompound) {
        boolean flag = false;
        boolean newState = nbttagcompound.getBoolean(POWERED_TAG);
        if (newState != powered) {
            powered = newState;
            flag = true;
        }
        newState = nbttagcompound.getBoolean(ACTIVE_TAG);
        if (newState != active) {
            active = newState;
            flag = true;
        }
        return flag;
    }

    protected boolean refreshNetworkState() {
        boolean flag = false;
        boolean newState = isPowered();
        if (newState != powered) {
            powered = newState;
            flag = true;
        }
        newState = isActive();
        if (newState != active) {
            active = newState;
            flag = true;
        }
        return flag;
    }
}

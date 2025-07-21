package com.mordenkainen.equivalentenergistics.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.common.base.Equivalence;

public class CompItemStack extends Equivalence<ItemStack> {

    private static final int PRIME = 31;

    @Override
    protected boolean doEquivalent(final ItemStack stack1, final ItemStack stack2) {
        if (stack1 == null) {
            return stack2 == null;
        }
        if (stack2 == null) {
            return false;
        }
        // 只比较物品，不比较 meta/damage
        if (!stack1.getItem().equals(stack2.getItem())) {
            return false;
        }
        // 注释掉 meta 检查，使所有 meta 的 EMC Crystal 视为等价
        // if (stack1.getItemDamage() != stack2.getItemDamage()) {
        //     return false;
        // }
        if (!stack1.hasTagCompound()) {
            return !stack2.hasTagCompound();
        }
        return stack1.stackTagCompound.equals(stack2.stackTagCompound);
    }

    @Override
    protected int doHash(final ItemStack stack) {
        int result = 1;
        result = PRIME * result + Item.getIdFromItem(stack.getItem());
        // 注释掉 meta 的 hash，使所有 meta 的 EMC Crystal hash 相同
        // result = PRIME * result + stack.getItemDamage();
        result = PRIME * result + (stack.hasTagCompound() ? stack.stackTagCompound.hashCode() : 0);
        return result;
    }
}

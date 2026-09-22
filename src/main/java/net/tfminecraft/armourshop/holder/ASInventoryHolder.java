package net.tfminecraft.armourshop.holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ASInventoryHolder implements InventoryHolder{

    private final boolean item;

    public ASInventoryHolder(boolean i) {
        item = i;
    }

    public boolean isItem() {
        return item;
    }

    @Override
    public Inventory getInventory() {
        return null; // Not used in this case
    }
    
}

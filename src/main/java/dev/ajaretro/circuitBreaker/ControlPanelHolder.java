package dev.ajaretro.circuitBreaker;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ControlPanelHolder implements InventoryHolder {
    private final String guiType; // "main", "frozen", "ignored"
    private final Inventory inventory;

    public ControlPanelHolder(String guiType, int size, String title) {
        this.guiType = guiType;
        this.inventory = org.bukkit.Bukkit.createInventory(this, size, title);
    }

    public String getGuiType() {
        return guiType;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

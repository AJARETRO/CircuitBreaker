/*
 * Copyright (c) 2026 AJA_RETRO (https://ajaretro.dev). All Rights Reserved.
 * 
 * This source code and compiled binaries are the intellectual property of the author.
 * Redistribution, modification, or derivative works are strictly prohibited under the
 * terms of the Source-Available License.
 */

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

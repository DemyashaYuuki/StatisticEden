package com.artemis.the.gr8.playerstats.core.gui.listeners;

import com.artemis.the.gr8.playerstats.core.gui.StatsGuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.jetbrains.annotations.NotNull;

public final class StatsGuiListener implements Listener {

    private final StatsGuiManager statsGuiManager;

    public StatsGuiListener(@NotNull StatsGuiManager statsGuiManager) {
        this.statsGuiManager = statsGuiManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        statsGuiManager.handleInventoryClick(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (statsGuiManager.isManagedInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }
}

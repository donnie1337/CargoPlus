package com.cargoplus.listener;

import com.cargoplus.CargoPlus;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChatColorGuiListener implements Listener {
    private static final String TITLE = "§8Escolha a cor do chat";
    private final CargoPlus plugin;

    public ChatColorGuiListener(CargoPlus plugin) { this.plugin = plugin; }

    public void open(Player player) {
        Inventory inventory = plugin.getServer().createInventory(null, 27, TITLE);
        int slot = 10;
        for (Map.Entry<String, String> entry : plugin.chatColors().entrySet()) {
            ChatColor color = parse(entry.getValue());
            if (color == null) continue;
            ItemStack item = new ItemStack(woolFor(color));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color + entry.getKey());
            List<String> lore = new ArrayList<>();
            lore.add("§7Cor do texto que você digita no chat.");
            lore.add("§7Exemplo: " + color + "Sua mensagem");
            if (entry.getKey().equalsIgnoreCase(plugin.permissions().getUser(player.getUniqueId()).chatColor())) {
                lore.add("§a✓ Cor atualmente selecionada");
            } else {
                lore.add("§eClique para selecionar");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            slot++;
            if (slot == 17) slot = 19;
            if (slot > 25) break;
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;
        String selected = ChatColor.stripColor(meta.getDisplayName()).trim().toLowerCase(Locale.ROOT);
        if (!plugin.chatColors().containsKey(selected)) return;
        if (plugin.setChatColor(player, selected)) {
            player.sendMessage(plugin.message("color-changed").replace("{color}", selected));
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
    }

    private static ChatColor parse(String value) {
        String translated = ChatColor.translateAlternateColorCodes('&', value == null ? "" : value.trim());
        if (translated.length() != 2 || translated.charAt(0) != ChatColor.COLOR_CHAR) return null;
        return ChatColor.getByChar(translated.charAt(1));
    }

    private static Material woolFor(ChatColor color) {
        return switch (color) {
            case WHITE -> Material.WHITE_WOOL;
            case GRAY -> Material.GRAY_WOOL;
            case DARK_GRAY -> Material.GRAY_WOOL;
            case BLACK -> Material.BLACK_WOOL;
            case RED -> Material.RED_WOOL;
            case GREEN -> Material.LIME_WOOL;
            case DARK_GREEN -> Material.GREEN_WOOL;
            case BLUE -> Material.BLUE_WOOL;
            case AQUA -> Material.CYAN_WOOL;
            case DARK_AQUA -> Material.CYAN_WOOL;
            case YELLOW -> Material.YELLOW_WOOL;
            case GOLD -> Material.ORANGE_WOOL;
            case LIGHT_PURPLE -> Material.PINK_WOOL;
            case DARK_PURPLE -> Material.PURPLE_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }
}

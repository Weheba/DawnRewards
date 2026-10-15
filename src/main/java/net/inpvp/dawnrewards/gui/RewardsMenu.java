package net.inpvp.dawnrewards.gui;

import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.config.Rewards;
import net.inpvp.dawnrewards.user.DawnUser;
import net.inpvp.dawnrewards.util.DurationFormatter;
import net.inpvp.dawnrewards.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain Bukkit inventory, so one jar runs from 1.20.6 to the latest Paper.
 */
@NullMarked
public class RewardsMenu implements Listener {

    private static final int DAYS_PER_PAGE = 27;
    private static final int SIZE = 36;
    private static final int INFO_SLOT = 27;
    private static final int PREVIOUS_SLOT = 30;
    private static final int NEXT_SLOT = 32;
    private static final String DAWN_URL = "https://dawn.gg";

    private final DawnRewards plugin;

    public RewardsMenu(DawnRewards plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        var user = plugin.getDawnUserCache().get(player);
        open(player, Math.max(0, user.getCurrentDay() - 1) / DAYS_PER_PAGE);
    }

    private void open(Player player, int page) {
        var user = plugin.getDawnUserCache().get(player);
        var length = plugin.getRewards().getLength();
        var pages = Math.max(1, Math.ceilDiv(length, DAYS_PER_PAGE));
        page = Math.clamp(page, 0, pages - 1);

        var holder = new Holder(page);
        var inventory = plugin.getServer().createInventory(holder, SIZE, Text.parse(plugin.getRewards().getMenuTitle()));
        holder.inventory = inventory;

        var nextDay = user.getCurrentDay() >= length ? 1 : user.getCurrentDay() + 1;
        var claimable = plugin.getClaimService().isClaimable(user);
        for (var slot = 0; slot < DAYS_PER_PAGE; slot++) {
            var day = page * DAYS_PER_PAGE + slot + 1;
            if (day > length) {
                break;
            }
            inventory.setItem(slot, dayItem(user, day, nextDay, claimable));
            if (day == nextDay && claimable) {
                holder.claimSlot = slot;
            }
        }

        var filler = item(Material.BLACK_STAINED_GLASS_PANE, Component.empty(), List.of(), false, 0);
        for (var slot = DAYS_PER_PAGE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(INFO_SLOT, infoItem());
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, arrow("Previous Page"));
        }
        if (page < pages - 1) {
            inventory.setItem(NEXT_SLOT, arrow("Next Page"));
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
            return;
        }

        var slot = event.getSlot();
        var length = plugin.getRewards().getLength();
        if (slot == holder.claimSlot) {
            player.closeInventory();
            plugin.getClaimService().claim(player);
        } else if (slot == INFO_SLOT) {
            player.closeInventory();
            showDownload(player);
        } else if (slot == PREVIOUS_SLOT && holder.page > 0) {
            open(player, holder.page - 1);
        } else if (slot == NEXT_SLOT && (holder.page + 1) * DAYS_PER_PAGE < length) {
            open(player, holder.page + 1);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private ItemStack dayItem(DawnUser user, int day, int nextDay, boolean claimable) {
        var isNext = day == nextDay;
        var isClaimed = !isNext && day <= user.getCurrentDay();
        var menuItem = menuItemFor(isNext, isClaimed);

        var lore = new ArrayList<Component>(description(day));
        lore.add(Component.empty());
        lore.add(statusOf(user, day, isNext, isClaimed, claimable));

        return item(menuItem.getMaterial(), Text.plain(menuItem.getName().replace("{day}", String.valueOf(day))),
                lore, isNext && claimable, menuItem.getModelData());
    }

    private Rewards.MenuItem menuItemFor(boolean isNext, boolean isClaimed) {
        var items = plugin.getRewards().getMenuItems();

        if (isClaimed) {
            return items.getClaimed();
        }

        return isNext ? items.getCurrent() : items.getLocked();
    }

    private List<Component> description(int day) {
        var reward = plugin.getRewards().getDay(day);
        if (reward == null) {
            return List.of();
        }

        return reward.getDescription().stream().map(Text::plain).toList();
    }

    private Component statusOf(DawnUser user, int day, boolean isNext, boolean isClaimed, boolean claimable) {
        if (isClaimed) {
            return Text.plain("<green>Already claimed");
        }
        if (!isNext) {
            return Text.plain("<gray>You must unlock day <white>" + (day - 1) + "</white> first");
        }
        if (claimable) {
            return Text.plain("<#F97316><bold>Click to claim!");
        }

        return Text.plain("<gray>Ready in <white>"
                + DurationFormatter.format(plugin.getClaimService().timeUntilClaimable(user)));
    }

    private ItemStack infoItem() {
        return item(Material.ENCHANTED_BOOK, Text.plain("<#F97316><bold>What is Dawn Rewards?"), List.of(
                Text.plain("<gray>Play on this server through the"),
                Text.plain("<#F97316>Dawn Client<gray> to unlock a new"),
                Text.plain("<gray>reward every day."),
                Component.empty(),
                Text.plain("<gray>Miss a day and you start back at day one."),
                Component.empty(),
                Text.plain("<#F97316><bold>Click to download the client")), true, 0);
    }

    private ItemStack arrow(String name) {
        return item(Material.ARROW, Text.plain("<#F97316>" + name), List.of(), false, 0);
    }

    @SuppressWarnings("deprecation") // the integer form is the one every supported version has
    private static ItemStack item(Material material, Component name, List<Component> lore, boolean glint, int modelData) {
        var stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }
        });
        return stack;
    }

    private void showDownload(Player player) {
        if (DownloadDialog.isSupported()) {
            DownloadDialog.show(player, DAWN_URL);
            return;
        }

        player.sendMessage(Text.parse("<gray>Play on this server through the Dawn Client to unlock a new reward every day. ")
                .append(Text.parse("<#F97316><bold><u>Download the Dawn Client</u>").clickEvent(ClickEvent.openUrl(DAWN_URL))));
    }

    private static final class Holder implements InventoryHolder {

        private final int page;
        private int claimSlot = -1;
        private @Nullable Inventory inventory;

        private Holder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            if (inventory == null) {
                throw new IllegalStateException("Menu inventory is not created yet");
            }
            return inventory;
        }
    }
}

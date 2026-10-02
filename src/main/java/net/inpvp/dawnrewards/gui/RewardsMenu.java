package net.inpvp.dawnrewards.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.inpvp.dawnrewards.DawnRewards;
import net.inpvp.dawnrewards.config.Rewards;
import net.inpvp.dawnrewards.user.DawnUser;
import net.inpvp.dawnrewards.util.DurationFormatter;
import net.inpvp.dawnrewards.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;

@NullMarked
public class RewardsMenu {

    private static final int DAYS_PER_PAGE = 27;
    private static final String DAWN_URL = "https://dawn.gg";

    private final DawnRewards plugin;

    public RewardsMenu(DawnRewards plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        var user = plugin.getDawnUserCache().get(player);

        var gui = PagedGui.itemsBuilder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # # # # # # # #",
                        "# # # # # # # # #",
                        "? X X < X > X X X")
                .addIngredient('#', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('?', infoItem())
                .addIngredient('X', filler())
                .addIngredient('<', pageItem("Previous Page", -1))
                .addIngredient('>', pageItem("Next Page", 1))
                .setContent(dayItems(user))
                .build();

        gui.setPage(Math.max(0, user.getCurrentDay() - 1) / DAYS_PER_PAGE);

        Window.builder()
                .setUpperGui(gui)
                .setTitle(Text.parse(plugin.getRewards().getMenuTitle()))
                .open(player);
    }

    private List<Item> dayItems(DawnUser user) {
        var length = plugin.getRewards().getLength();
        var nextDay = user.getCurrentDay() >= length ? 1 : user.getCurrentDay() + 1;
        var claimable = plugin.getClaimService().isClaimable(user);

        var items = new ArrayList<Item>(length);
        for (var day = 1; day <= length; day++) {
            items.add(dayItem(user, day, nextDay, claimable));
        }

        return items;
    }

    private Item dayItem(DawnUser user, int day, int nextDay, boolean claimable) {
        var isNext = day == nextDay;
        var isClaimed = !isNext && day <= user.getCurrentDay();
        var menuItem = menuItemFor(isNext, isClaimed);

        var provider = new ItemBuilder(menuItem.getMaterial())
                .setName(Text.plain(menuItem.getName().replace("{day}", String.valueOf(day))))
                .addLoreLines(description(day))
                .addLoreLines(Component.empty())
                .addLoreLines(statusOf(user, day, isNext, isClaimed, claimable))
                .setGlint(isNext && claimable);

        if (menuItem.getModelData() > 0) {
            provider.setCustomModelData(new float[]{menuItem.getModelData()});
        }

        var builder = Item.builder().setItemProvider(provider);
        if (isNext && claimable) {
            builder.addClickHandler(click -> claim(click.player()));
        }

        return builder.build();
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

    private void claim(Player player) {
        player.closeInventory();
        plugin.getClaimService().claim(player);
    }

    private Item infoItem() {
        return Item.builder()
                .setItemProvider(new ItemBuilder(Material.ENCHANTED_BOOK)
                        .setName(Text.plain("<#F97316><bold>What is Dawn Rewards?"))
                        .addLoreLines(
                                Text.plain("<gray>Play on this server through the"),
                                Text.plain("<#F97316>Dawn Client<gray> to unlock a new"),
                                Text.plain("<gray>reward every day."),
                                Component.empty(),
                                Text.plain("<gray>Miss a day and you start back at day one."),
                                Component.empty(),
                                Text.plain("<#F97316><bold>Click to download the client"))
                        .setGlint(true))
                .addClickHandler(click -> openDownload(click.player()))
                .build();
    }

    private void openDownload(Player player) {
        player.closeInventory();
        player.showDialog(downloadDialog());
    }

    private Dialog downloadDialog() {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Text.parse("<#F97316><bold>Dawn Rewards"))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.item(ItemStack.of(Material.ENCHANTED_BOOK))
                                .description(DialogBody.plainMessage(Text.parse(
                                        "<gray>Play on this server through the Dawn Client to unlock a new reward every day."), 240))
                                .showDecorations(false)
                                .showTooltip(false)
                                .build()))
                        .build())
                .type(DialogType.multiAction(
                        List.of(ActionButton.builder(Text.parse("<#F97316><bold>Download the Dawn Client"))
                                .tooltip(Component.text(DAWN_URL))
                                .width(220)
                                .action(DialogAction.staticAction(ClickEvent.openUrl(DAWN_URL)))
                                .build()),
                        null,
                        1)));
    }

    private Item filler() {
        return Item.simple(fillerProvider());
    }

    private ItemBuilder fillerProvider() {
        return new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName(Component.empty());
    }

    private Item pageItem(String name, int delta) {
        return new PageItem(new ItemBuilder(Material.ARROW).setName(Text.plain("<#F97316>" + name)), fillerProvider(), delta);
    }
}

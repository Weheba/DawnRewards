package net.inpvp.dawnrewards.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jspecify.annotations.NullMarked;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.item.AbstractPagedGuiBoundItem;
import xyz.xenondevs.invui.item.ItemProvider;

@NullMarked
public class PageItem extends AbstractPagedGuiBoundItem {

    private final ItemProvider arrow;
    private final ItemProvider hidden;
    private final int delta;

    public PageItem(ItemProvider arrow, ItemProvider hidden, int delta) {
        this.arrow = arrow;
        this.hidden = hidden;
        this.delta = delta;
    }

    @Override
    public ItemProvider getItemProvider(Player viewer) {
        return hasTargetPage() ? arrow : hidden;
    }

    @Override
    public void handleClick(ClickType clickType, Player player, Click click) {
        if (hasTargetPage()) {
            getGui().setPage(getGui().getPage() + delta);
        }
    }

    private boolean hasTargetPage() {
        var target = getGui().getPage() + delta;
        return target >= 0 && target < getGui().getPageCount();
    }
}

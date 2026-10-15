package net.inpvp.dawnrewards.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.inpvp.dawnrewards.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Dialogs exist from Paper 1.21.6. This class is only loaded once {@link #isSupported()} says so.
 */
@NullMarked
final class DownloadDialog {

    private static final boolean SUPPORTED = classExists("io.papermc.paper.dialog.Dialog");

    private DownloadDialog() {
    }

    static boolean isSupported() {
        return SUPPORTED;
    }

    static void show(Player player, String url) {
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Text.parse("<#F97316><bold>Dawn Rewards"))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.item(new ItemStack(Material.ENCHANTED_BOOK))
                                .description(DialogBody.plainMessage(Text.parse(
                                        "<gray>Play on this server through the Dawn Client to unlock a new reward every day."), 240))
                                .showDecorations(false)
                                .showTooltip(false)
                                .build()))
                        .build())
                .type(DialogType.multiAction(
                        List.of(ActionButton.builder(Text.parse("<#F97316><bold>Download the Dawn Client"))
                                .tooltip(Component.text(url))
                                .width(220)
                                .action(DialogAction.staticAction(ClickEvent.openUrl(url)))
                                .build()),
                        null,
                        1))));
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, DownloadDialog.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}

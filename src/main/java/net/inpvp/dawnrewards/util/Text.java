package net.inpvp.dawnrewards.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component parse(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    public static Component plain(String message) {
        return parse(message).decoration(TextDecoration.ITALIC, false);
    }
}

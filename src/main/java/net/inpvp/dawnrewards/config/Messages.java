package net.inpvp.dawnrewards.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.NoArgsConstructor;
import net.inpvp.dawnrewards.util.Text;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.NullMarked;

@NullMarked
@NoArgsConstructor
@Configuration
public class Messages {

    @Comment("Prepended to every message below")
    private String prefix = "<#F97316><bold>DAWN</bold></#F97316> <dark_gray>»</dark_gray> ";

    @Comment("{time} is formatted like 1d2h3m4s")
    private String claimed = "<gray>You claimed the day <#F97316>{day}</#F97316> reward.";
    private String notOnDawn = "<gray>You must be playing through the <#F97316>Dawn Client</#F97316> to claim rewards.";
    private String onCooldown = "<gray>Your next reward is ready in <#F97316>{time}</#F97316>.";
    private String claimFailed = "<red>Your reward could not be claimed, please try again shortly.";
    private String rewardReady = "<gray>Your daily reward is ready, use <#F97316>/dawn</#F97316> to claim it.";

    public Component claimed(int day) {
        return prefixed(claimed.replace("{day}", String.valueOf(day)));
    }

    public Component notOnDawn() {
        return prefixed(notOnDawn);
    }

    public Component onCooldown(String time) {
        return prefixed(onCooldown.replace("{time}", time));
    }

    public Component claimFailed() {
        return prefixed(claimFailed);
    }

    public Component rewardReady() {
        return prefixed(rewardReady);
    }

    public Component prefixed(String message) {
        return Text.parse(prefix + message);
    }
}

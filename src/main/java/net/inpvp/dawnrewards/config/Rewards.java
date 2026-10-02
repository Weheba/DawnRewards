package net.inpvp.dawnrewards.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@ToString
@NoArgsConstructor
@Configuration
public class Rewards {

    private String menuTitle = "<#F97316><bold>Dawn Rewards";

    @Comment("The current day is enchanted once its reward is ready to claim")
    private MenuItems menuItems = new MenuItems();

    @Comment("Keyed by day number, commands run from console")
    private Map<Integer, RewardDay> days = defaultDays();

    public int getLength() {
        return days.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public @Nullable RewardDay getDay(int day) {
        return days.get(day);
    }

    public void validate() {
        if (days.isEmpty()) {
            throw new IllegalStateException("rewards.yml must define at least day 1");
        }

        for (var day : days.keySet()) {
            if (day < 1) {
                throw new IllegalStateException("rewards.yml defines day " + day + ", day numbers must start at 1");
            }
        }

        var length = getLength();
        for (var day = 1; day <= length; day++) {
            if (!days.containsKey(day)) {
                throw new IllegalStateException("rewards.yml is missing day " + day + ", every day from 1 to " + length + " must be present");
            }
        }
    }

    @Getter
    @ToString
    @NoArgsConstructor
    @Configuration
    public static class MenuItems {
        private MenuItem claimed = new MenuItem(Material.LIME_STAINED_GLASS_PANE, "<green><bold>Day {day}", 0);
        private MenuItem current = new MenuItem(Material.ORANGE_STAINED_GLASS_PANE, "<#F97316><bold>Day {day}", 0);
        private MenuItem locked = new MenuItem(Material.RED_STAINED_GLASS_PANE, "<red><bold>Day {day}", 0);
    }

    @Getter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @Configuration
    public static class MenuItem {
        private Material material = Material.STONE;
        private String name = "<white>Day {day}";
        private int modelData = 0;
    }

    @Getter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @Configuration
    public static class RewardDay {
        private List<String> description = new ArrayList<>();
        private List<String> commands = new ArrayList<>();
    }

    private static Map<Integer, RewardDay> defaultDays() {
        var days = new LinkedHashMap<Integer, RewardDay>();

        days.put(1, new RewardDay(List.of("<white>8x Iron Ingot"), List.of("give {player} minecraft:iron_ingot 8")));
        days.put(2, new RewardDay(List.of("<#c96f50>16x Copper Ingot"), List.of("give {player} minecraft:copper_ingot 16")));
        days.put(3, new RewardDay(List.of("<yellow>8x Gold Ingot"), List.of("give {player} minecraft:gold_ingot 8")));
        days.put(4, new RewardDay(List.of("<aqua>4x Diamond"), List.of("give {player} minecraft:diamond 4")));
        days.put(5, new RewardDay(List.of("<dark_red>1x Netherite Ingot", "<dark_gray>The final reward of the pass"),
                List.of("give {player} minecraft:netherite_ingot 1")));

        return days;
    }
}

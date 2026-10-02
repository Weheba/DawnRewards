package net.inpvp.dawnrewards.config;

import de.exlll.configlib.NameFormatters;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.function.Consumer;

@NullMarked
public class ConfigLoader<T> {

    private static final YamlConfigurationProperties PROPERTIES = YamlConfigurationProperties.newBuilder()
            .setNameFormatter(NameFormatters.LOWER_KEBAB_CASE)
            .createParentDirectories(true)
            .build();

    private final Class<T> type;
    private final Path path;
    private final Consumer<T> validator;

    private T config;

    public ConfigLoader(Class<T> type, Path path) {
        this(type, path, loaded -> {
        });
    }

    public ConfigLoader(Class<T> type, Path path, Consumer<T> validator) {
        this.type = type;
        this.path = path;
        this.validator = validator;

        reload();
    }

    public final void reload() {
        var loaded = YamlConfigurations.update(path, type, PROPERTIES);
        validator.accept(loaded);

        this.config = loaded;
    }

    public final void save() {
        YamlConfigurations.save(path, type, config, PROPERTIES);
    }

    public T get() {
        return config;
    }
}

package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.api.services.AuctionOptionService;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.OptionRepository;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class OptionService extends ZUtils implements AuctionOptionService {

    private final AuctionPlugin plugin;
    private final Map<UUID, Map<PlayerOption, String>> optionsCache = new ConcurrentHashMap<>();

    public OptionService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Void> loadPlayerOptions(UUID playerUniqueId) {
        return CompletableFuture.runAsync(() -> {
            var dtos = this.plugin.getStorageManager().with(OptionRepository.class).selectAll(playerUniqueId);
            Map<PlayerOption, String> options = new EnumMap<>(PlayerOption.class);
            for (var dto : dtos) {
                PlayerOption option = PlayerOption.fromKey(dto.option_name());
                if (option != null) {
                    options.put(option, dto.option_value());
                }
            }
            this.optionsCache.put(playerUniqueId, options);
        }, this.plugin.getExecutorService());
    }

    @Override
    public boolean getOption(UUID playerUniqueId, PlayerOption option) {
        Map<PlayerOption, String> options = this.optionsCache.get(playerUniqueId);
        if (options != null && options.containsKey(option)) {
            return Boolean.parseBoolean(options.get(option));
        }
        return Boolean.parseBoolean(option.getDefaultValue());
    }

    @Override
    public boolean getOption(Player player, PlayerOption option) {
        return getOption(player.getUniqueId(), option);
    }

    @Override
    public void setOption(UUID playerUniqueId, PlayerOption option, boolean value) {
        String strValue = String.valueOf(value);
        if (option.isDefaultValue(strValue)) {
            Map<PlayerOption, String> options = this.optionsCache.get(playerUniqueId);
            if (options != null) options.remove(option);
            this.plugin.getScheduler().runAsync(wrappedTask ->
                    this.plugin.getStorageManager().with(OptionRepository.class).deleteOption(playerUniqueId, option.getKey())
            );
        } else {
            this.optionsCache.computeIfAbsent(playerUniqueId, k -> new EnumMap<>(PlayerOption.class)).put(option, strValue);
            this.plugin.getScheduler().runAsync(wrappedTask ->
                    this.plugin.getStorageManager().with(OptionRepository.class).upsertOption(playerUniqueId, option.getKey(), strValue)
            );
        }
    }

    @Override
    public CompletableFuture<Void> setOptionAsync(UUID playerUniqueId, PlayerOption option, String value) {
        if (option.isDefaultValue(value)) {
            Map<PlayerOption, String> options = this.optionsCache.get(playerUniqueId);
            if (options != null) options.remove(option);
            return CompletableFuture.runAsync(() ->
                    this.plugin.getStorageManager().with(OptionRepository.class).deleteOption(playerUniqueId, option.getKey()),
                    this.plugin.getExecutorService()
            );
        } else {
            this.optionsCache.computeIfAbsent(playerUniqueId, k -> new EnumMap<>(PlayerOption.class)).put(option, value);
            return CompletableFuture.runAsync(() ->
                    this.plugin.getStorageManager().with(OptionRepository.class).upsertOption(playerUniqueId, option.getKey(), value),
                    this.plugin.getExecutorService()
            );
        }
    }

    @Override
    public Map<PlayerOption, String> getPlayerOptions(UUID playerUniqueId) {
        Map<PlayerOption, String> options = this.optionsCache.get(playerUniqueId);
        if (options != null) {
            return new EnumMap<>(options);
        }
        // Return defaults
        Map<PlayerOption, String> defaults = new EnumMap<>(PlayerOption.class);
        for (PlayerOption option : PlayerOption.values()) {
            defaults.put(option, option.getDefaultValue());
        }
        return defaults;
    }

    @Override
    public void resetPlayerOptions(UUID playerUniqueId) {
        this.optionsCache.remove(playerUniqueId);
        this.plugin.getScheduler().runAsync(wrappedTask ->
                this.plugin.getStorageManager().with(OptionRepository.class).deleteAll(playerUniqueId)
        );
    }

    @Override
    public void clearPlayerOptions(UUID playerUniqueId) {
        this.optionsCache.remove(playerUniqueId);
    }
}

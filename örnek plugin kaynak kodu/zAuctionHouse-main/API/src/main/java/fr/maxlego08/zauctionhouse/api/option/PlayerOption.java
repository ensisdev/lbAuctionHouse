package fr.maxlego08.zauctionhouse.api.option;

import fr.maxlego08.zauctionhouse.api.messages.Message;

public enum PlayerOption {

    BROADCAST_SELL("broadcast_sell", "true", Message.OPTION_BROADCAST_SELL_ENABLED, Message.OPTION_BROADCAST_SELL_DISABLED),
    BROADCAST_PURCHASE("broadcast_purchase", "true", Message.OPTION_BROADCAST_PURCHASE_ENABLED, Message.OPTION_BROADCAST_PURCHASE_DISABLED);

    private final String key;
    private final String defaultValue;
    private final Message enabledMessage;
    private final Message disabledMessage;

    PlayerOption(String key, String defaultValue, Message enabledMessage, Message disabledMessage) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.enabledMessage = enabledMessage;
        this.disabledMessage = disabledMessage;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public Message getEnabledMessage() {
        return enabledMessage;
    }

    public Message getDisabledMessage() {
        return disabledMessage;
    }

    public boolean isDefaultValue(String value) {
        return this.defaultValue.equalsIgnoreCase(value);
    }

    public static PlayerOption fromKey(String key) {
        for (PlayerOption option : values()) {
            if (option.key.equalsIgnoreCase(key)) {
                return option;
            }
        }
        return null;
    }
}

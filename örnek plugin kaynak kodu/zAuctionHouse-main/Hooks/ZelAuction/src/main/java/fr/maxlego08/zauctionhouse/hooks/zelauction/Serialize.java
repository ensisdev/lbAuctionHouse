package fr.maxlego08.zauctionhouse.hooks.zelauction;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

public class Serialize {

    public static String serialize(ItemStack itemStack, AuctionPlugin plugin, boolean modern) {
        if (itemStack == null) return "";
        if (modern) try {
            Map<String, Object> map = itemStack.serialize();
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<String, Object> entry : map.entrySet())
                yaml.set(entry.getKey(), entry.getValue());
            return Base64.getEncoder().encodeToString(yaml.saveToString().getBytes());
        } catch (Exception e) {
            plugin.getLogger().info("Failed to serialize ItemStack with modern method: " + e.getMessage());
            return null;
        }
        try {
            ByteArrayOutputStream io = new ByteArrayOutputStream();
            try {
                BukkitObjectOutputStream oi = new BukkitObjectOutputStream(io);
                try {
                    oi.writeObject(itemStack);
                    oi.flush();
                    String str = Base64.getEncoder().encodeToString(io.toByteArray());
                    oi.close();
                    io.close();
                    return str;
                } catch (Throwable throwable) {
                    try {
                        oi.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                    throw throwable;
                }
            } catch (Throwable throwable) {
                try {
                    io.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
                throw throwable;
            }
        } catch (Exception e) {
            plugin.getLogger().info("Failed to serialize ItemStack with legacy method: " + e.getMessage());
            return null;
        }
    }

    public static ItemStack deserialize(String data, AuctionPlugin plugin, boolean legacy) {
        if (data == null || data.isBlank()) return null;
        if (legacy) return deserializeLegacy(data, plugin);
        return deserializeModern(data, plugin);
    }

    public static ItemStack deserializeLegacy(String data, AuctionPlugin plugin) {
        if (data == null || data.isBlank()) return null;
        try {
            ByteArrayInputStream arrayInputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            try {
                BukkitObjectInputStream objectInputStream = new BukkitObjectInputStream(arrayInputStream);
                try {
                    ItemStack itemStack = (ItemStack) objectInputStream.readObject();
                    objectInputStream.close();
                    arrayInputStream.close();
                    return itemStack;
                } catch (Throwable throwable) {
                    try {
                        objectInputStream.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                    throw throwable;
                }
            } catch (Throwable throwable) {
                try {
                    arrayInputStream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
                throw throwable;
            }
        } catch (Exception e) {
            plugin.getLogger().info("Failed to deserialize ItemStack with legacy method");
            plugin.getLogger().info("Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            plugin.getLogger().info("Data length: " + data.length());
            plugin.getLogger().info("Data preview (first 100 chars): " + data.substring(0, Math.min(100, data.length())));
            plugin.getLogger().info("Server version: " + Bukkit.getVersion());
            plugin.getLogger().info("Bukkit version: " + Bukkit.getBukkitVersion());
            plugin.getLogger().info("Full stack trace:");
            e.printStackTrace();
            return null;
        }
    }

    public static ItemStack deserializeModern(String data, AuctionPlugin plugin) {
        if (data == null || data.isBlank() || data.isEmpty()) return null;
        try {
            String yamlString;
            try {
                byte[] decoded = Base64.getDecoder().decode(data);
                yamlString = new String(decoded);
            } catch (IllegalArgumentException e) {
                yamlString = data;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(yamlString);
            Map<String, Object> map = yaml.getValues(false);
            return ItemStack.deserialize(map);
        } catch (Exception e) {
            plugin.getLogger().info("Failed to deserialize ItemStack with modern method");
            plugin.getLogger().info("Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            plugin.getLogger().info("Data length: " + data.length());
            if (e.getMessage() != null && e.getMessage().contains("Material"))
                plugin.getLogger().info("This appears to be a material-related error. The item may be from a different Minecraft version.");
            return null;
        }
    }

    public static String serializeLegacy(ItemStack itemStack, AuctionPlugin plugin) {
        if (itemStack == null) return "";
        try {
            ByteArrayOutputStream io = new ByteArrayOutputStream();
            try {
                BukkitObjectOutputStream oi = new BukkitObjectOutputStream(io);
                try {
                    oi.writeObject(itemStack);
                    oi.flush();
                    String str = Base64.getEncoder().encodeToString(io.toByteArray());
                    oi.close();
                    io.close();
                    return str;
                } catch (Throwable throwable) {
                    try {
                        oi.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                    throw throwable;
                }
            } catch (Throwable throwable) {
                try {
                    io.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
                throw throwable;
            }
        } catch (Exception e) {
            plugin.getLogger().info("Failed to serialize ItemStack with legacy method: " + e.getMessage());
            return null;
        }
    }

    public static String serializeModern(ItemStack itemStack, AuctionPlugin plugin) {
        if (itemStack == null) return "";
        try {
            Map<String, Object> map = itemStack.serialize();
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<String, Object> entry : map.entrySet())
                yaml.set(entry.getKey(), entry.getValue());
            return Base64.getEncoder().encodeToString(yaml.saveToString().getBytes());
        } catch (Exception e) {
            plugin.getLogger().info("Failed to serialize ItemStack with modern method: " + e.getMessage());
            return null;
        }
    }

}

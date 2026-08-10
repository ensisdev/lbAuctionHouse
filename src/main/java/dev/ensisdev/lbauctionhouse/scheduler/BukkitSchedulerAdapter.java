package dev.ensisdev.lbauctionhouse.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Klasik Spigot/Paper implementasyonu — {@code Bukkit.getScheduler()} kullanır.
 */
public final class BukkitSchedulerAdapter implements SchedulerAdapter {

    private final JavaPlugin plugin;

    public BukkitSchedulerAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public void runTask(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runTaskLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public void runTaskAsynchronously(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public RepeatingTask runRepeatingTask(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return bukkitTask::cancel;
    }

    @Override
    public void runTaskForPlayer(Player player, Runnable task) {
        runTask(task);
    }

    @Override
    public void runTaskLaterForPlayer(Player player, Runnable task, long delayTicks) {
        runTaskLater(task, delayTicks);
    }
}

package dev.ensisdev.lbauctionhouse.scheduler;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Folia implementasyonu — reflection tabanlıdır.
 * <p>
 * Folia API {@code org.bukkit} paketinin fork'u olduğu için spigot-api ile aynı
 * compile classpath'inde bulunamaz. Bu yüzden Folia scheduler sınıflarına
 * runtime'da reflection ile erişilir. Method'lar kurulumda bir kez çözümlenir
 * ve cache'lenir, böylece çalışma zamanı ek yükü yok denecek kadardır.
 */
public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;

    private Method bukkitGlobalRegionScheduler;
    private Method bukkitAsyncScheduler;
    private Method globalRun;
    private Method globalRunDelayed;
    private Method globalRunAtFixedRate;
    private Method asyncRunNow;
    private Method entityGetScheduler;
    private Method entityRun;
    private Method entityRunDelayed;
    private Method scheduledTaskCancel;

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        initReflection();
    }

    private void initReflection() {
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Class<?> grsClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> asyncClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

            bukkitGlobalRegionScheduler = bukkitClass.getMethod("getGlobalRegionScheduler");
            bukkitAsyncScheduler = bukkitClass.getMethod("getAsyncScheduler");
            globalRun = grsClass.getMethod("run", Plugin.class, Consumer.class);
            globalRunDelayed = grsClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = grsClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            asyncRunNow = asyncClass.getMethod("runNow", Plugin.class, Consumer.class);
            entityGetScheduler = Player.class.getMethod("getScheduler");
            entityRun = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            entityRunDelayed = entitySchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            scheduledTaskCancel = scheduledTaskClass.getMethod("cancel");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Folia scheduler sınıfları çözümlenemedi — sunucu Folia değil mi?", e);
        }
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    private Object globalRegionScheduler() {
        try {
            return bukkitGlobalRegionScheduler.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.SEVERE, "getGlobalRegionScheduler çağrılamadı", unwrap(e));
            return null;
        }
    }

    private Object asyncScheduler() {
        try {
            return bukkitAsyncScheduler.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.SEVERE, "getAsyncScheduler çağrılamadı", unwrap(e));
            return null;
        }
    }

    private void invokeVoid(Method method, Object target, Object... args) {
        try {
            method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.SEVERE, "Folia scheduler görevi çalıştırılamadı", unwrap(e));
        }
    }

    private Object invokeObject(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.SEVERE, "Folia scheduler görevi çalıştırılamadı", unwrap(e));
            return null;
        }
    }

    private static Throwable unwrap(ReflectiveOperationException e) {
        return (e instanceof InvocationTargetException ite && ite.getCause() != null) ? ite.getCause() : e;
    }

    private static Consumer<?> runnableConsumer(Runnable task) {
        return ignored -> task.run();
    }

    @Override
    public void runTask(Runnable task) {
        Object scheduler = globalRegionScheduler();
        if (scheduler != null) {
            invokeVoid(globalRun, scheduler, plugin, runnableConsumer(task));
        }
    }

    @Override
    public void runTaskLater(Runnable task, long delayTicks) {
        Object scheduler = globalRegionScheduler();
        if (scheduler != null) {
            invokeObject(globalRunDelayed, scheduler, plugin, runnableConsumer(task), delayTicks);
        }
    }

    @Override
    public void runTaskAsynchronously(Runnable task) {
        Object scheduler = asyncScheduler();
        if (scheduler != null) {
            invokeVoid(asyncRunNow, scheduler, plugin, runnableConsumer(task));
        }
    }

    @Override
    public RepeatingTask runRepeatingTask(Runnable task, long delayTicks, long periodTicks) {
        Object scheduler = globalRegionScheduler();
        if (scheduler == null) {
            return () -> { };
        }
        Object scheduledTask = invokeObject(globalRunAtFixedRate, scheduler, plugin, runnableConsumer(task), delayTicks, periodTicks);
        if (scheduledTask == null) {
            return () -> { };
        }
        return () -> {
            try {
                scheduledTaskCancel.invoke(scheduledTask);
            } catch (IllegalAccessException | InvocationTargetException e) {
                plugin.getLogger().log(Level.SEVERE, "Folia tekrarlayan görev iptal edilemedi", unwrap(e));
            }
        };
    }

    @Override
    public void runTaskForPlayer(Player player, Runnable task) {
        Object entityScheduler;
        try {
            entityScheduler = entityGetScheduler.invoke(player);
        } catch (IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.SEVERE, "player.getScheduler() çağrılamadı", unwrap(e));
            return;
        }
        if (entityScheduler != null) {
            invokeObject(entityRun, entityScheduler, plugin, runnableConsumer(task), (Runnable) () -> { });
        }
    }

    @Override
    public void runTaskLaterForPlayer(Player player, Runnable task, long delayTicks) {
        Object entityScheduler;
        try {
            entityScheduler = entityGetScheduler.invoke(player);
        } catch (IllegalAccessException | InvocationTargetException e) {
            plugin.getLogger().log(Level.SEVERE, "player.getScheduler() çağrılamadı", unwrap(e));
            return;
        }
        if (entityScheduler != null) {
            invokeObject(entityRunDelayed, entityScheduler, plugin, runnableConsumer(task), (Runnable) () -> { }, delayTicks);
        }
    }
}

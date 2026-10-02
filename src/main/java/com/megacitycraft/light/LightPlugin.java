package com.megacitycraft.light;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class LightPlugin extends JavaPlugin implements Listener {
    private PlayerSettings settings;
    private BukkitTask updateTask;
    private volatile boolean stopping;

    @Override
    public void onEnable() {
        stopping = false;
        saveDefaultConfig();
        try {
            settings = new PlayerSettings(getDataFolder().toPath().resolve("players.yml"));
        } catch (IOException | InvalidConfigurationException error) {
            getLogger().log(Level.SEVERE, "Unable to read player settings; disabling to protect saved choices.", error);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var command = Objects.requireNonNull(getCommand("light"), "Missing /light in plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        for (Player player : getServer().getOnlinePlayers()) restoreLater(player);
        if (getConfig().getBoolean("update-checker.enabled", true)) {
            // HTTP never runs on the game thread. Timeouts bound failed requests.
            updateTask = getServer().getScheduler().runTaskLaterAsynchronously(this,
                    this::checkForUpdates, 20L);
        }
    }

    @Override
    public void onDisable() {
        stopping = true;
        if (updateTask != null) updateTask.cancel();
    }

    private void checkForUpdates() {
        if (stopping) return;
        try {
            var result = new ReleaseChecker().check(getPluginMeta().getVersion());
            if (stopping) return;
            if (result.latestTag() == null) {
                getLogger().info("No published stable GitHub release found; update status is unknown.");
            } else if (result.updateAvailable()) {
                getLogger().info("Update Available!");
                getLogger().info("Running v" + getPluginMeta().getVersion() + "; latest "
                        + result.latestTag() + ". Download: " + ReleaseChecker.RELEASES_URL);
            } else {
                getLogger().info("Running the most up to date version.");
            }
        } catch (IOException error) {
            if (!stopping) getLogger().warning("Update check unavailable: " + error.getMessage()
                    + ". Night vision is unaffected; will check again on the next server boot.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("version")) {
            sender.sendMessage(Component.text("MegaCityLight v" + getPluginMeta().getVersion(), NamedTextColor.GOLD));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Use /light in-game to change your own night vision.", NamedTextColor.RED));
            return true;
        }
        boolean enabled;
        if (args.length == 0) enabled = !settings.isEnabled(player.getUniqueId());
        else if (args.length == 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "on", "enable" -> enabled = true;
                case "off", "disable" -> enabled = false;
                default -> { showUsage(player); return true; }
            }
        } else { showUsage(player); return true; }

        if (!saveChoice(player, enabled)) return true;
        if (enabled) apply(player);
        else player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        announce(player, enabled);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> choices = sender instanceof Player
                ? List.of("on", "enable", "off", "disable", "version") : List.of("version");
        return choices.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        restoreLater(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // The vanilla respawn process clears effects after the event fires.
        restoreLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMilk(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.MILK_BUCKET) return;
        Player player = event.getPlayer();
        if (!settings.isEnabled(player.getUniqueId())) return;
        // Vanilla removes the effects after this event. Save the choice now so
        // later reconnects and respawns do not restore night vision.
        if (saveChoice(player, false)) announce(player, false);
    }

    private void restoreLater(Player player) {
        getServer().getScheduler().runTask(this, () -> {
            // Recheck the choice here: /light off or milk may have run meanwhile.
            if (player.isOnline() && !player.isDead() && settings.isEnabled(player.getUniqueId())) apply(player);
        });
    }

    private void apply(Player player) {
        // Replace any finite night-vision potion so /light always grants infinity
        // and ensures the requested particle/icon settings.
        PotionEffect existing = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (existing != null && existing.isInfinite() && existing.getAmplifier() == 0
                && !existing.hasParticles() && existing.hasIcon()) return;
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION, 0, false, false, true));
    }

    private boolean saveChoice(Player player, boolean enabled) {
        try {
            settings.setEnabled(player.getUniqueId(), enabled);
            return true;
        } catch (IOException error) {
            getLogger().log(Level.SEVERE, "Unable to save /light setting for " + player.getUniqueId(), error);
            player.sendMessage(Component.text("Could not save your light setting. Please contact an admin.", NamedTextColor.RED));
            return false;
        }
    }

    private void announce(Player player, boolean enabled) {
        player.sendMessage(Component.text("Lights ", NamedTextColor.GOLD)
                .append(Component.text(enabled ? "on!" : "off!", enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private void showUsage(Player player) {
        player.sendMessage(Component.text("Usage: /light [on|enable|off|disable|version]", NamedTextColor.YELLOW));
    }
}

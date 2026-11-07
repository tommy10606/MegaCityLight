package com.megacitycraft.light;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class LightPluginTest {
    private ServerMock server;
    private LightPlugin plugin;
    private PlayerMock player;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        YamlConfiguration config = new YamlConfiguration();
        config.set("update-checker.enabled", false); // Tests must not contact GitHub.
        plugin = MockBukkit.loadWithConfig(LightPlugin.class, config);
        player = server.addPlayer();
        player.setOp(false);
        tick();
    }

    @AfterEach void tearDown() { MockBukkit.unmock(); }
    private void tick() { server.getScheduler().performTicks(2); }
    private boolean hasVision() { return player.hasPotionEffect(PotionEffectType.NIGHT_VISION); }

    @Test void versionShowsActiveMetadataWithoutChangingNightVision() {
        while (player.nextComponentMessage() != null) { /* Drain join messages. */ }
        assertTrue(player.performCommand("light version"));
        assertEquals("MegaCityLight v1.0.2", PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()));
        assertFalse(hasVision());
        player.performCommand("light on");
        player.performCommand("light VERSION");
        assertTrue(hasVision());
        assertTrue(plugin.getCommand("light").tabComplete(player, "light", new String[]{"v"}).contains("version"));
        assertEquals(java.util.List.of("Tommy10606"), plugin.getPluginMeta().getAuthors());
        assertEquals("https://github.com/tommy10606/MegaCityLight", plugin.getPluginMeta().getWebsite());
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "light version"));
    }

    @Test void newPlayerStartsOffAndNonOpCanToggle() {
        assertFalse(player.isOp());
        assertFalse(hasVision());
        assertTrue(player.performCommand("light"));
        assertTrue(hasVision());
        assertTrue(player.performCommand("light"));
        assertFalse(hasVision());
        assertNull(plugin.getCommand("light").getPermission());
    }

    @Test void explicitCommandsAreIdempotentAndCaseInsensitive() {
        for (String arg : new String[]{"on", "on", "enable", "ENABLE"}) {
            assertTrue(player.performCommand("light " + arg));
            assertTrue(hasVision());
        }
        for (String arg : new String[]{"off", "off", "disable", "DISABLE"}) {
            assertTrue(player.performCommand("light " + arg));
            assertFalse(hasVision());
        }
    }

    @Test void grantsInfinityWithVisibleIconAndNoParticles() {
        player.performCommand("light on");
        PotionEffect effect = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        assertNotNull(effect);
        assertTrue(effect.isInfinite());
        assertTrue(effect.hasIcon());
        assertFalse(effect.hasParticles());
        server.getScheduler().performTicks(700);
        assertTrue(hasVision());
    }

    @Test void respawnRestoresEffectAfterVanillaClearsIt() {
        player.performCommand("light on");
        player.setHealth(0);
        player.respawn();
        // Emulate vanilla's post-event removal, which MockBukkit does not do.
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        tick();
        assertTrue(hasVision());
    }

    @Test void milkDisablesChoiceAcrossRespawnReconnectAndPluginRestart() throws Exception {
        player.performCommand("light on");
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(player,
                new ItemStack(Material.MILK_BUCKET), EquipmentSlot.HAND));
        player.removePotionEffect(PotionEffectType.NIGHT_VISION); // Vanilla milk action.
        Path file = plugin.getDataFolder().toPath().resolve("players.yml");
        assertFalse(new PlayerSettings(file).isEnabled(player.getUniqueId()));
        player.setHealth(0);
        player.respawn();
        tick();
        assertFalse(hasVision());
        player.disconnect();
        player.reconnect();
        tick();
        assertFalse(hasVision());
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        tick();
        assertFalse(hasVision());
        player.performCommand("light");
        assertTrue(hasVision());
    }

    @Test void cancelledMilkAndOtherFoodDoNotDisable() {
        player.performCommand("light on");
        var cancelled = new PlayerItemConsumeEvent(player,
                new ItemStack(Material.MILK_BUCKET), EquipmentSlot.OFF_HAND);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(player,
                new ItemStack(Material.APPLE), EquipmentSlot.HAND));
        player.setHealth(0);
        player.respawn();
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        tick();
        assertTrue(hasVision());
    }

    @Test void enabledChoiceSurvivesReconnectAndPluginRestart() throws Exception {
        player.performCommand("light on");
        Path file = plugin.getDataFolder().toPath().resolve("players.yml");
        assertTrue(new PlayerSettings(file).isEnabled(player.getUniqueId()));
        player.disconnect();
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.reconnect();
        tick();
        assertTrue(hasVision());
        server.getPluginManager().disablePlugin(plugin);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        server.getPluginManager().enablePlugin(plugin);
        tick();
        assertTrue(hasVision());
    }

    @Test void pendingRestoreCannotUndoExplicitOff() {
        player.performCommand("light on");
        player.respawn();
        player.performCommand("light off");
        tick();
        assertFalse(hasVision());
    }

    @Test void invalidArgumentsDoNotChangeStateAndPlayersAreIndependent() {
        PlayerMock other = server.addPlayer();
        player.performCommand("light on");
        player.performCommand("light banana");
        player.performCommand("light off extra");
        tick();
        assertTrue(hasVision());
        assertFalse(other.hasPotionEffect(PotionEffectType.NIGHT_VISION));
        other.performCommand("light off");
        assertTrue(hasVision());
    }

    @Test void finitePotionIsReplacedAndOtherEffectTypesArePreserved() {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0));
        player.performCommand("light on");
        assertTrue(player.getPotionEffect(PotionEffectType.NIGHT_VISION).isInfinite());
        player.performCommand("light off");
        assertFalse(hasVision());
        assertTrue(player.hasPotionEffect(PotionEffectType.SPEED));
    }

    @Test void toggleUsesActualEffectEvenBeforeAnExternalClearIsPersisted() throws Exception {
        player.performCommand("light on");
        player.removePotionEffect(PotionEffectType.NIGHT_VISION); // Essentials /heal uses plugin removal.
        assertFalse(hasVision());
        Path file = plugin.getDataFolder().toPath().resolve("players.yml");
        assertTrue(new PlayerSettings(file).isEnabled(player.getUniqueId())); // Old saved flag is still on.
        player.performCommand("light");
        assertTrue(hasVision()); // First toggle must turn it on, not turn the stale flag off.
        tick();
        assertTrue(hasVision());
        assertTrue(new PlayerSettings(file).isEnabled(player.getUniqueId()));
        player.performCommand("light");
        assertFalse(hasVision());
    }

    @Test void healStyleClearSavesOffAndDoesNotReturnOnRespawnReconnectOrRestart() throws Exception {
        player.performCommand("light on");
        player.clearActivePotionEffects();
        tick();
        Path file = plugin.getDataFolder().toPath().resolve("players.yml");
        assertFalse(new PlayerSettings(file).isEnabled(player.getUniqueId()));
        player.setHealth(0); player.respawn(); tick();
        assertFalse(hasVision());
        player.disconnect(); player.reconnect(); tick();
        assertFalse(hasVision());
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin); tick();
        assertFalse(hasVision());
        player.performCommand("light");
        assertTrue(hasVision());
    }

    @Test void externalRemovalBeforeDisconnectIsStillSaved() throws Exception {
        player.performCommand("light on");
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.disconnect(); tick();
        assertFalse(new PlayerSettings(plugin.getDataFolder().toPath().resolve("players.yml")).isEnabled(player.getUniqueId()));
        player.reconnect(); tick();
        assertFalse(hasVision());
    }

    @Test void cancelledRemovalDoesNotDisableChoice() throws Exception {
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
            public void cancel(EntityPotionEffectEvent event) {
                if (event.getEntity().equals(player) && event.getModifiedType().equals(PotionEffectType.NIGHT_VISION)
                        && event.getAction() == EntityPotionEffectEvent.Action.REMOVED) event.setCancelled(true);
            }
        }, plugin);
        player.performCommand("light on");
        player.removePotionEffect(PotionEffectType.NIGHT_VISION); tick();
        assertTrue(hasVision());
        assertTrue(new PlayerSettings(plugin.getDataFolder().toPath().resolve("players.yml")).isEnabled(player.getUniqueId()));
    }

    @Test void deathRemovalPreservesSavedChoice() throws Exception {
        player.performCommand("light on");
        player.removePotionEffect(PotionEffectType.NIGHT_VISION,
                EntityPotionEffectEvent.Cause.DEATH, EntityPotionEffectEvent.Action.CLEARED);
        tick();
        assertTrue(new PlayerSettings(plugin.getDataFolder().toPath().resolve("players.yml")).isEnabled(player.getUniqueId()));
        player.setHealth(0); player.respawn(); tick();
        assertTrue(hasVision());
    }

    @Test void finiteReplacementAndUnrelatedEffectRemovalDoNotDisableSavedChoice() throws Exception {
        player.performCommand("light on");
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 1200, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0));
        player.removePotionEffect(PotionEffectType.SPEED); tick();
        assertTrue(hasVision());
        assertTrue(new PlayerSettings(plugin.getDataFolder().toPath().resolve("players.yml")).isEnabled(player.getUniqueId()));
        player.performCommand("light on"); tick();
        assertTrue(player.getPotionEffect(PotionEffectType.NIGHT_VISION).isInfinite());
        assertTrue(new PlayerSettings(plugin.getDataFolder().toPath().resolve("players.yml")).isEnabled(player.getUniqueId()));
    }

    @Test void toggleRemovesExistingPotionEvenWhenSavedPreferenceWasOff() {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 1200, 0));
        player.performCommand("light"); tick();
        assertFalse(hasVision());
        player.performCommand("light");
        assertTrue(hasVision());
    }

    @Test void settingsRoundTripUsesUuidAndOffIsPersisted(@TempDir Path directory) throws Exception {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        Path file = directory.resolve("players.yml");
        PlayerSettings first = new PlayerSettings(file);
        assertFalse(first.isEnabled(alice));
        first.setEnabled(alice, true);
        first.setEnabled(bob, true);
        PlayerSettings restarted = new PlayerSettings(file);
        assertTrue(restarted.isEnabled(alice));
        assertTrue(restarted.isEnabled(bob));
        restarted.setEnabled(alice, false);
        PlayerSettings again = new PlayerSettings(file);
        assertFalse(again.isEnabled(alice));
        assertTrue(again.isEnabled(bob));
    }
}

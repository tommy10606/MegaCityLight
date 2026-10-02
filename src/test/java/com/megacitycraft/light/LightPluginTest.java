package com.megacitycraft.light;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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
        plugin = MockBukkit.load(LightPlugin.class);
        player = server.addPlayer();
        player.setOp(false);
        tick();
    }

    @AfterEach void tearDown() { MockBukkit.unmock(); }
    private void tick() { server.getScheduler().performTicks(2); }
    private boolean hasVision() { return player.hasPotionEffect(PotionEffectType.NIGHT_VISION); }

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

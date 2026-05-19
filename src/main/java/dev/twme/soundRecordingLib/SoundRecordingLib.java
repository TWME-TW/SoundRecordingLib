package dev.twme.soundRecordingLib;

import com.github.retrooper.packetevents.PacketEvents;
import dev.twme.soundRecordingLib.listener.PlayerSessionListener;
import dev.twme.soundRecordingLib.listener.SoundPacketListener;
import dev.twme.soundRecordingLib.playback.PlaybackManager;
import dev.twme.soundRecordingLib.recording.RecordingManager;
import dev.twme.soundRecordingLib.storage.JsonRecordingStorage;
import dev.twme.soundRecordingLib.storage.RecordingStorage;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SoundRecordingLib extends JavaPlugin {

    private static SoundRecordingLib instance;

    private SoundPacketListener soundPacketListener;
    private RecordingManager recordingManager;
    private PlaybackManager playbackManager;
    private RecordingStorage recordingStorage;

    // E1: dynamic position snapshot task — only active while recordings are happening
    private BukkitTask positionSnapshotTask = null;

    @Override
    public void onLoad() {
        instance = this;
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        soundPacketListener = new SoundPacketListener();
        recordingManager = new RecordingManager(soundPacketListener);
        playbackManager = new PlaybackManager(this);
        recordingStorage = new JsonRecordingStorage(getDataFolder());

        soundPacketListener.setRecordingManager(recordingManager);

        PacketEvents.getAPI().getEventManager().registerListener(soundPacketListener);
        PacketEvents.getAPI().init();

        Bukkit.getPluginManager().registerEvents(
                new PlayerSessionListener(recordingManager, playbackManager), this);

        // Start position snapshot scheduler — dynamic start/stop managed via RecordingManager hooks
        startPositionSnapshotTask();
    }

    @Override
    public void onDisable() {
        if (positionSnapshotTask != null && !positionSnapshotTask.isCancelled()) {
            positionSnapshotTask.cancel();
        }
        PacketEvents.getAPI().terminate();
    }

    private void startPositionSnapshotTask() {
        positionSnapshotTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!soundPacketListener.hasRegisteredPlayers()) return;

            for (var uuid : recordingManager.getActivePlayerUuids()) {
                var player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    var loc = player.getLocation();
                    soundPacketListener.updatePositionSnapshot(
                            uuid, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw());
                }
            }
        }, 1L, 1L);
    }

    public static SoundRecordingLib getInstance() { return instance; }
    public RecordingManager getRecordingManager() { return recordingManager; }
    public PlaybackManager getPlaybackManager() { return playbackManager; }
    public RecordingStorage getRecordingStorage() { return recordingStorage; }
}

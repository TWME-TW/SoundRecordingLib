package dev.twme.soundRecordingLib.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.twme.soundRecordingLib.playback.PlaybackManager;
import dev.twme.soundRecordingLib.recording.RecordingManager;

public class PlayerSessionListener implements Listener {

    private final RecordingManager recordingManager;
    private final PlaybackManager playbackManager;

    public PlayerSessionListener(RecordingManager recordingManager, PlaybackManager playbackManager) {
        this.recordingManager = recordingManager;
        this.playbackManager = playbackManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();

        // Auto-stop recording on disconnect — discard per default (no auto-save)
        if (recordingManager.isRecording(uuid)) {
            recordingManager.stopRecordingByUuid(uuid);
        }

        // Remove player from any active playback targets
        playbackManager.removePlayer(player);
    }
}

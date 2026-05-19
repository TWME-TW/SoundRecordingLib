package dev.twme.soundRecordingLib.api;

import java.util.Collection;
import java.util.Optional;

import org.bukkit.entity.Player;

import dev.twme.soundRecordingLib.SoundRecordingLib;
import dev.twme.soundRecordingLib.data.SoundRecording;

public class SoundRecordingApi {

    private SoundRecordingApi() {}

    public static RecordingSession startRecording(Player player, RecordingOptions options) {
        return SoundRecordingLib.getInstance().getRecordingManager().startRecording(player, options);
    }

    public static SoundRecording stopRecording(Player player) {
        return SoundRecordingLib.getInstance().getRecordingManager().stopRecording(player);
    }

    public static Optional<RecordingSession> getActiveRecording(Player player) {
        return SoundRecordingLib.getInstance().getRecordingManager().getActiveRecording(player);
    }

    public static PlaybackSession startPlayback(SoundRecording recording, Collection<Player> targets, PlaybackOptions options) {
        return SoundRecordingLib.getInstance().getPlaybackManager().startPlayback(recording, targets, options);
    }

    public static void stopPlayback(PlaybackSession session) {
        SoundRecordingLib.getInstance().getPlaybackManager().stopPlayback(session);
    }
}

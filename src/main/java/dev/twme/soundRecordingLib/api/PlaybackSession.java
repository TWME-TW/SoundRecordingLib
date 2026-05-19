package dev.twme.soundRecordingLib.api;

import java.util.Collection;

import org.bukkit.entity.Player;

import dev.twme.soundRecordingLib.data.SoundRecording;

public interface PlaybackSession {

    SoundRecording getRecording();

    Collection<Player> getTargets();

    boolean isActive();

    boolean isPaused();

    void pause();

    void resume();
}

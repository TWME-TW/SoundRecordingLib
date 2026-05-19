package dev.twme.soundRecordingLib.api;

import dev.twme.soundRecordingLib.data.SoundRecording;

public interface RecordingSession {

    SoundRecording getRecording();

    boolean isActive();
}

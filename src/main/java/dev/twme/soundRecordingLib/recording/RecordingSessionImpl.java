package dev.twme.soundRecordingLib.recording;

import dev.twme.soundRecordingLib.api.RecordingSession;
import dev.twme.soundRecordingLib.data.SoundRecording;

public class RecordingSessionImpl implements RecordingSession {

    private final ActiveRecording activeRecording;
    private final RecordingManager manager;

    public RecordingSessionImpl(ActiveRecording activeRecording, RecordingManager manager) {
        this.activeRecording = activeRecording;
        this.manager = manager;
    }

    @Override
    public SoundRecording getRecording() {
        return null; // Still in progress; not yet finalised
    }

    @Override
    public boolean isActive() {
        return manager.isRecording(activeRecording.getPlayerUuid());
    }
}

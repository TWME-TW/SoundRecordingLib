package dev.twme.soundRecordingLib.data;

import java.util.List;
import java.util.UUID;

public record SoundRecording(
        UUID id,
        String name,
        long createdAt,
        long totalTicks,
        String worldName,
        RecordingMode mode,
        double refX,
        double refY,
        double refZ,
        List<RecordedSoundEvent> events
) {}

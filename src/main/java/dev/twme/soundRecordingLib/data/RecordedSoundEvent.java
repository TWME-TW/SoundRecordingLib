package dev.twme.soundRecordingLib.data;

import com.github.retrooper.packetevents.protocol.sound.SoundCategory;

public record RecordedSoundEvent(
        long relativeTick,
        String soundKey,
        SoundCategory category,
        double relX,
        double relY,
        double relZ,
        float volume,
        float pitch,
        long seed,
        float recorderYaw
) {}

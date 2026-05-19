package dev.twme.soundRecordingLib.storage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import dev.twme.soundRecordingLib.data.SoundRecording;

public interface RecordingStorage {

    void save(SoundRecording recording) throws IOException;

    Optional<SoundRecording> load(String name) throws IOException;

    List<String> listNames() throws IOException;

    boolean delete(String name) throws IOException;
}

package dev.twme.soundRecordingLib.data;

public enum RecordingMode {
    /**
     * 固定參考點模式。
     * 錄製開始時設定一個固定的世界座標作為參考點（referencePosition）。
     * 每個聲音事件儲存其相對於 referencePosition 的偏移向量。
     * 適合：紅石音樂、建築內固定音效等位置固定的場景。
     */
    STATIC,

    /**
     * 跟隨玩家模式（第一人稱體驗）。
     * 每個聲音事件儲存其相對於「錄製者在該 tick 的瞬時位置」的偏移向量。
     * 適合：記錄玩家在世界中行走時聽到的完整聲音體驗。
     */
    PLAYER_RELATIVE
}

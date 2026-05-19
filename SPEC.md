# SoundRecordingLib — 設計規格書

> 版本：0.2-draft  
> 日期：2026-05-19  
> 作者：TWME-TW

---

## 1. 目標概述

SoundRecordingLib 是一個 PaperMC 插件 library，提供：

1. **錄製**：透過 packetevents 攔截伺服器送往特定玩家的聲音封包，將其記錄為以 **伺服器 tick** 為基準的時間軸。
2. **播放**：依照 tick 時間軸重播聲音給一個或多個玩家，消除伺服器卡頓造成的時間偏移。
3. **公開 API**：讓其他插件（如 SoundRecord）可以呼叫以管理錄製與播放工作階段。

---

## 2. 設計原則

| 原則 | 說明 |
|------|------|
| Tick 時間軸 | 所有聲音事件以「相對錄製起始的 tick 數」儲存，而非真實時間（ms），消除 lag spike 影響 |
| Packet 層攔截 | 使用 packetevents `SOUND_EFFECT` / `ENTITY_SOUND_EFFECT` packet；不依賴 Bukkit Sound 事件 |
| 位置統一化 | `ENTITY_SOUND_EFFECT` 在攔截當下解析 entity 座標，一律轉成位置型事件儲存 |
| 玩家視角錄製 | 錄製的是「該玩家聽到的聲音」，非全域廣播 |
| 兩種錄製模式 | STATIC（固定參考點）與 PLAYER_RELATIVE（跟隨錄製者瞬時位置），分別對應「場景錄音」與「第一人稱體驗錄音」兩種需求 |
| Immutable 資料 | 已完成的 `SoundRecording` 不可變更，playback 只讀 |

---

## 3. 模組結構

```
SoundRecordingLib (PaperMC Plugin)
└── dev.twme.soundRecordingLib
    ├── SoundRecordingLib.java          # Plugin 主類，初始化 packetevents 及各 Manager
    ├── api/
    │   ├── SoundRecordingApi.java      # 公開 API 入口（靜態工廠）
    │   ├── RecordingSession.java       # 錄製工作階段介面
    │   ├── PlaybackSession.java        # 播放工作階段介面
    │   ├── RecordingOptions.java       # 錄製選項 Builder
    │   └── PlaybackOptions.java        # 播放選項 Builder
    ├── data/
    │   ├── RecordedSoundEvent.java     # 單筆聲音事件（immutable）
    │   └── SoundRecording.java         # 完整錄音資料（immutable）
    ├── recording/
    │   ├── RecordingManager.java       # 管理所有進行中的錄製
    │   └── ActiveRecording.java        # 單一進行中錄製的可變狀態
    ├── playback/
    │   ├── PlaybackManager.java        # 管理所有進行中的播放
    │   └── ActivePlayback.java         # 單一進行中播放的狀態
    ├── listener/
    │   ├── SoundPacketListener.java    # packetevents 聲音封包監聽器
    │   └── PlayerSessionListener.java  # PlayerQuitEvent：自動釋放錄製/播放資源
    └── storage/
        ├── RecordingStorage.java       # 持久化介面
        └── JsonRecordingStorage.java   # JSON 格式實作

SoundRecord (PaperMC Plugin，依賴 SoundRecordingLib)
└── dev.twme.soundRecord
    ├── SoundRecord.java                # Plugin 主類
    └── command/
        └── SrCommand.java             # /sr 指令處理器
```

---

## 4. 資料模型

### 4.0 RecordingMode

```java
public enum RecordingMode {
    /**
     * 固定參考點模式。
     * 錄製開始時設定一個固定的世界座標作為參考點（referencePosition）。
     * 每個聲音事件儲存其相對於 referencePosition 的偏移向量。
     * 適合：紅石音樂、建築內固定音效等位置固定的場景。
     * 播放時：anchor + relativeOffset
     */
    STATIC,

    /**
     * 跟隨玩家模式（第一人稱體驗）。
     * 每個聲音事件儲存其相對於「錄製者在該 tick 的瞬時位置」的偏移向量。
     * 適合：記錄玩家在世界中行走時聽到的完整聲音體驗。
     *
     * 注意：兩種模式都會記錄 recorderYaw，
     * 播放時是否使用由 PlaybackOptions 中的獨立旗標控制。
     */
    PLAYER_RELATIVE
}
```

### 4.1 RecordedSoundEvent（immutable）

```java
public record RecordedSoundEvent(
    long    relativeTick,      // 相對錄製開始的 tick 偏移
    String  soundKey,          // ResourceLocation 字串（e.g. "minecraft:block.note_block.harp"）
                               // ⚠️ 不儲存 Network ID：伺服器重啟/跨版本後整數 ID 可能改變
    SoundCategory category,
    double  relX,              // 聲音來源相對於參考點的偏移 X（見 RecordingMode）
    double  relY,              // 聲音來源相對於參考點的偏移 Y
    double  relZ,              // 聲音來源相對於參考點的偏移 Z
    float   volume,
    float   pitch,
    long    seed,              // 維持原始 seed 以重現相同隨機化
    float   recorderYaw        // 錄製者當時的水平頭部旋轉角（度）；兩種 RecordingMode 均記錄
) {}
```

**設計決策 ①**：`ENTITY_SOUND_EFFECT` 在攔截當下轉換為位置型事件。
- 優點：playback 不需要知道原 entity 是否存在
- 缺點：如果 entity 正在移動，轉換時機的座標可能有偏差（約半個 tick 誤差）
- 替代方案：保留 entityId 並於播放時重新附著；但因為 entity 不一定存在，故不採用

**關於 `recorderYaw`（立體聲方向資訊）**：  
Minecraft 的聲音空間化在客戶端（OpenAL）完成，伺服器只送絕對座標，客戶端依玩家當前位置 + 頭旋轉計算左右聲道。  
`recorderYaw` **兩種模式都會記錄**，讓播放端可獨立決定是否套用錄製者朝向與/或播放者朝向的旋轉補償。  
具體計算邏輯見 Section 7.1。  
> ⚠️ 僅補償水平方向（yaw）；垂直角（pitch）對 Minecraft 聲音空間化影響極小，暫不記錄。

### 4.2 SoundRecording（immutable）

```java
public record SoundRecording(
    UUID                    id,
    String                  name,
    long                    createdAt,          // 系統時間戳（ms）
    long                    totalTicks,         // 錄音總長度（ticks）
    String                  worldName,          // 錄製時所在世界
    RecordingMode           mode,               // 錄製模式
    // STATIC 模式：錄製開始時設定的固定世界座標
    // PLAYER_RELATIVE 模式：錄製開始時玩家的世界座標（作為播放預設 anchor）
    double                  refX,
    double                  refY,
    double                  refZ,
    List<RecordedSoundEvent> events             // 依 relativeTick 排序
) {}
```

---

## 5. Tick 正規化機制

### 5.1 問題說明

伺服器發生 lag spike 時，兩個 tick 之間的真實時間可能遠超 50ms。若以牆鐘時間錄製，播放時會忠實重現延遲。

```
正常情況：  Tick 0──50ms──Tick 1──50ms──Tick 2──50ms──Tick 3
卡頓情況：  Tick 0──50ms──Tick 1────200ms────Tick 2──50ms──Tick 3
```

紅石音樂的每個音符在特定 tick 觸發，lag 只是延遲了 packet 抵達玩家端的時機，**邏輯上仍屬於那個 tick**。

### 5.2 解法：以 `Bukkit.getCurrentTick()` 標記

```
錄製時：relativeTick = Bukkit.getCurrentTick() - recordingStartTick
播放時：在 tick N 觸發的事件，排程於 playbackStartTick + N 的 tick 執行
```

packetevents 的 `PacketSendEvent` 在 Netty I/O thread 觸發，`Bukkit.getCurrentTick()` 回傳 volatile int，跨執行緒讀取安全。

### 5.3 播放排程

**改用單一週期性 Task + Cursor 設計**，取代「每個事件各自 `runTaskLater`」的舊方案。

> ❌ 舊方案的問題：若錄音含 5000 個事件，呼叫 `runTaskLater` 5000 次會在 Bukkit Scheduler 塞入 5000 個 Task 物件，導致排程器過載。

`PlaybackManager` 維護**一個**週期性 BukkitTask（動態啟停，與位置快照 listener 相同策略）。  
每 tick 對所有 `ActivePlayback` 執行：

```
// ── 時間軸推進（累加器設計）──────────────────────────────────────
// 優點：正確支援中途變速；暫停時不累加；避免 formula 方案在 speed ≠ 1.0 時
// 因 lag spike 造成的時間軸偏移問題。
activePlayback.currentRelativeTick += options.speed

while cursor < events.size():
    event = events[cursor]

    // ── 跳過機制（skipThresholdTicks > 0 時啟用）──────────────────
    // 若此事件已落後目前超過閾值，直接跳過（不補發），避免 lag 後爆量
    if options.skipThresholdTicks > 0
        && currentRelativeTick - event.relativeTick > options.skipThresholdTicks:
        cursor++; continue

    // ── 尚未到時間，等下一個 tick ──────────────────────────────────
    if event.relativeTick > currentRelativeTick: break

    sendSound(event, targets, options)
    cursor++

// ── 播放完畢 ────────────────────────────────────────────────────────
if cursor >= events.size():
    if options.loop: activePlayback.currentRelativeTick = 0.0; cursor = 0
    else: stopPlayback(session)
```

**自然 Catch-Up**：伺服器 lag 後，Paper 會快速補回積欠的 tick，task 連續執行，`currentRelativeTick` 連續累加，`while` 迴圈自動補發該段所有事件。`skipThresholdTicks`（> 0）為安全閥——落後超過閾值的事件直接丟棄，防止 lag 後大量音效同時爆出。

> 與舊 formula 設計（`(getCurrentTick() - startTick) * speed`）的差異：舊方案在 speed ≠ 1.0 時，若 lag spike 使 tick 計數偏移，計算出的時間軸進度會不正確；累加器方案每 tick 恰好推進 `speed`，中途變速只影響未來，行為可預期。

---

## 6. 錄製流程

### 6.1 STATIC 模式

```
[API 呼叫者] startRecording(player, name, RecordingMode.STATIC, referencePosition)
  └─ referencePosition 可為 null，此時使用玩家當前位置作為參考點
      │
      ▼
RecordingManager.startRecording()
  - 建立 ActiveRecording(
        startTick, player UUID, name,
        mode = STATIC,
        refPos = referencePosition ?? player.getLocation(),
        events = new ConcurrentLinkedQueue<>()   // Netty thread 寫入安全
    )
  - 向 SoundPacketListener 註冊此 player

[packetevents SOUND_EFFECT 攔截]
SoundPacketListener.onPacketSend()
  - 解析絕對世界座標 (absX, absY, absZ)
  - 計算 relX = absX - refPos.x, relY = ..., relZ = ...
  - 建立 RecordedSoundEvent(relativeTick, sound, category,
        relX, relY, relZ, volume, pitch, seed, recorderYaw=0)
  - 附加至 ActiveRecording.events

[ENTITY_SOUND_EFFECT 攔截]
  - 透過 entity network ID 查詢 entity 取得座標
  - 若 entity == null（已消失）→ 記錄為 (0, 0, 0) 且標記 entityMissing=true，播放時變成位於 anchor 的腳步音
  - 後續與 SOUND_EFFECT 相同
```

### 6.2 PLAYER_RELATIVE 模式

```
[API 呼叫者] startRecording(player, name, RecordingMode.PLAYER_RELATIVE, null)
      │  referencePosition 在此模式下為玩家 startTick 的座標，僅作為 playback 預設 anchor
      ▼
RecordingManager.startRecording()
  - 建立 ActiveRecording(mode = PLAYER_RELATIVE, refPos = player.getLocation())

[packetevents SOUND_EFFECT 攔截]
SoundPacketListener.onPacketSend()
  - 從 ConcurrentHashMap 位置快照讀取錄製者座標 recPos（E1 機制；主執行緒每 tick 更新）
    ⚠️ 不可直接呼叫 recorderPlayer.getLocation()：Bukkit Player 方法在 Netty thread 非執行緒安全
  - 從同一快照讀取錄製者當前 yaw
  - 計算 relX = absX - recPos.x, relY = ..., relZ = ...
  - 建立 RecordedSoundEvent(..., relX, relY, relZ, ..., recorderYaw = yaw)
```

### 6.3 停止錄製

```
[API 呼叫者] stopRecording(player)
      │
      ▼
RecordingManager.stopRecording()
  1. 從 SoundPacketListener 中移除此 player 登記（原子操作）
  2. 等待所有進行中的 onPacketSend 完成
     ⚠️ 步驟 1 完成後，仍可能有已進入 onPacketSend 的 Netty thread 在微秒內繼續寫入 queue。
     修正方案：使用 ReentrantReadWriteLock——onPacketSend 持有 read lock；
     stopRecording 持有 write lock（阻塞至所有 in-flight Netty handler 退出），再執行 drain。
  3. 從 ConcurrentLinkedQueue drain 所有事件
  4. 依 relativeTick 排序 → 建立不可變 List<RecordedSoundEvent>
  5. 產生 SoundRecording（totalTicks = currentTick - startTick）
  6. 回傳 SoundRecording 物件（呼叫者可選擇持久化）
```

> ℹ️ 步驟 2（ReentrantReadWriteLock write lock）確保 drain 從開始就是完整的事件集。

### 6.4 玩家斷線處理（PlayerQuitEvent）

`PlayerSessionListener` 監聽 `PlayerQuitEvent`：

```
若玩家有進行中的錄製
    → 呼叫 stopRecording，可配置為自動儲存或丟棄（預設：丟棄）

若玩家是某個 PlaybackSession 的 targets 之一
    → 從 targets 移除該玩家
    → 若 targets 清空 → 停止該 PlaybackSession
```

---

## 7. 播放流程

```
[API 呼叫者] startPlayback(recording, targets, options)
      │
      ▼
PlaybackManager.startPlayback()
  - 建立 ActivePlayback（cursor = 0, playbackStartTick = currentTick）
  - 若 PlaybackManager 當前無任何活躍播放：啟動週期性 tick task（動態啟停）
  - 播放結束或 stopPlayback() 時：若無其他活躍播放，取消 tick task
```

> **跨世界播放**：`SoundRecording.worldName` 為純詮釋資料（metadata），不用於驗證。  
> 聲音封包只含 `(x, y, z)` 座標，client 收到後在自己**當前所在世界**的該座標播放。  
> 因此 A 世界錄製的錄音完全可以播放給 B 世界的玩家，只需透過 `PlaybackOptions.anchor(locationInWorldB)` 提供 B 世界的參考點。  
> 若未指定 anchor，預設使用 `recording.refPos`（即 A 世界錄製時的座標數值），坐標數值仍有效但語意上可能不合理——由呼叫者負責。

### 7.1 座標還原邏輯

播放時三個 `PlaybackOptions` 旗標**完全獨立**運作，與錄製時的 `RecordingMode` 無關：

```
// ── 步驟 1：決定 base 位置 ──────────────────────────────────────
if (options.followReplayer)
    base = replayer.position          // 聲音跟著播放者移動
else
    base = anchor                     // 聲音固定在世界座標（anchor = options.anchor ?? recording.refPos）

// ── 步驟 2：建立偏移向量 ────────────────────────────────────────
offsetXZ = (event.relX, event.relZ)
offsetY  =  event.relY

// ── 步驟 3：套用錄製者朝向補償（選用）──────────────────────────
// 效果：將偏移向量從「錄製者當時的朝向空間」轉回「世界北方 = 0°」的中性空間
// 使聲音方向不受錄製時錄製者朝哪裡影響
if (options.applyRecorderOrientation)
    offsetXZ = rotate2D(offsetXZ, -event.recorderYaw)

// ── 步驟 4：套用播放者朝向補償（選用）──────────────────────────
// 效果：將偏移向量旋轉到「播放者朝向空間」，使聲音相對於播放者面朝方向的角度固定
// 播放者轉頭時，聲音的世界座標也跟著旋轉，立體聲方向感保持不變
if (options.applyReplayerOrientation)
    offsetXZ = rotate2D(offsetXZ, +replayer.getYaw())

// ── 步驟 5：套用微調偏移並計算最終座標 ──────────────────────────
worldX = base.x + offsetXZ.x + options.offsetX
worldY = base.y + offsetY   + options.offsetY
worldZ = base.z + offsetXZ.z + options.offsetZ
// ── 步驟 6（選用）：虛擬嗚叭模式（speakerMode）─────────────────
// 效果：所有聲音方向均指向播放點；玩家距播放點越近時音量 = 原始，越遠音量 ≤ 原始
if (options.speakerMode):
    anchorPos = options.anchor ?? recording.refPos
    distanceA = sqrt(event.relX² + event.relY² + event.relZ²)  // 原始錄製距離（每事件各自不同）
    d = length(anchorPos - player.pos)                       // 玩家到播放點的距離

    if d > 0 && d < distanceA:
        // 玩家比原始距離更近 → 往播放點方向延伸至 distanceA 處
        dir = normalize(anchorPos - player.pos)
        worldXYZ = player.pos + dir * distanceA
    else:
        // 玩家夠遠 → 直接在播放點播放（方向正確，音量 ≤ 原始）
        worldXYZ = anchorPos```

**常用組合一覽：**

| `followReplayer` | `applyRecorderOrientation` | `applyReplayerOrientation` | `speakerMode` | 效果 |
|:-:|:-:|:-:|:-:|------|
| ✗ | ✗ | ✗ | ✗ | 聲音固定在世界座標（標準 STATIC 播放） |
| ✓ | ✗ | ✗ | ✗ | 聲音跟著播放者移動，但方向以世界北為基準 |
| ✓ | ✓ | ✗ | ✗ | 聲音跟著播放者，移除錄製者朝向偏差，方向以北為基準 |
| ✓ | ✓ | ✓ | ✗ | 完整第一人稱還原：播放者聽到與錄製者相同的立體聲方向感 |
| ✗ | ✗ | ✗ | ✓ | 虛擬嗚叭：所有聲音方向均指向播放點，音量不超過錄製原始 |
| ✓ | ✗ | ✗ | ✗ | 聲音跟著播放者，方向相對播放者朝向固定（忽略錄製者朝向） |

---

## 8. 公開 API（SoundRecordingApi）

```java
public class SoundRecordingApi {

    /** 開始對指定玩家錄製 */
    public static RecordingSession startRecording(Player player, RecordingOptions options);

    /** 停止錄製，回傳完整錄音 */
    public static SoundRecording stopRecording(Player player);

    /** 取得玩家目前的錄製工作階段（若無則 Optional.empty()）*/
    public static Optional<RecordingSession> getActiveRecording(Player player);

    /** 開始播放 */
    public static PlaybackSession startPlayback(
        SoundRecording recording,
        Collection<Player> targets,
        PlaybackOptions options
    );

    /** 停止播放工作階段 */
    public static void stopPlayback(PlaybackSession session);
}
```

### RecordingOptions（Builder）

```java
public final class RecordingOptions {

    public static Builder builder(String name) { return new Builder(name); }

    public static final class Builder {
        private final String name;
        private RecordingMode mode          = RecordingMode.STATIC;
        @Nullable
        private Location      referencePos  = null;  // null → 使用錄製開始時玩家位置
        @Nullable
        private Set<SoundCategory> categoryFilter = null;  // null = 錄製所有 category

        public Builder mode(RecordingMode mode) { ... }
        /** 僅 STATIC 模式有效；若未指定，以玩家 startTick 位置为預設 */
        public Builder reference(Location pos) { ... }
        /**
         * 只錄製指定的 SoundCategory（白名單），在 Netty thread 直接過濾，降低物件建立與 GC 壓力。
         * 不呼叫此方法 = 錄製所有 category。
         * 範例：.filter(SoundCategory.BLOCK, SoundCategory.RECORD)
         */
        public Builder filter(SoundCategory... categories) { ... }
        public RecordingOptions build() { ... }
    }
}
```

**使用範例：**
```java
// STATIC 模式（以玩家當前位置為參考點）
SoundRecordingApi.startRecording(player,
    RecordingOptions.builder("my-music")
        .mode(RecordingMode.STATIC)
        .build());

// STATIC 模式（指定固定參考點）
SoundRecordingApi.startRecording(player,
    RecordingOptions.builder("my-music")
        .mode(RecordingMode.STATIC)
        .reference(someBlockLocation)
        .build());

// PLAYER_RELATIVE 模式
SoundRecordingApi.startRecording(player,
    RecordingOptions.builder("my-walk")
        .mode(RecordingMode.PLAYER_RELATIVE)
        .build());
```

### PlaybackOptions（Builder）

```java
public final class PlaybackOptions {

    public static Builder builder()      { return new Builder(); }
    public static PlaybackOptions defaults()    { return builder().build(); }
    public static PlaybackOptions firstPerson() {
        return builder()
            .followReplayer(true)
            .applyRecorderOrientation(true)
            .applyReplayerOrientation(true)
            .build();
    }

    public static final class Builder {
        @Nullable private Location anchor               = null;
        private double   offsetX                        = 0;
        private double   offsetY                        = 0;
        private double   offsetZ                        = 0;
        private double   speed                          = 1.0;
        private boolean  loop                           = false;
        private int      skipThresholdTicks              = 0;
        // 座標系控制
        private boolean  followReplayer                 = false;
        private boolean  applyRecorderOrientation       = false;
        private boolean  applyReplayerOrientation       = false;
        private boolean  speakerMode                    = false;  // 虛擬嗚叭模式

        /** 覆蓋 recording.refPos（null = 使用錄製時的 refPos） */
        public Builder anchor(Location anchor) { ... }
        /** 在 base 位置上的額外微調偏移 */
        public Builder offset(double x, double y, double z) { ... }
        /** 1.0 = 正常速度 */
        public Builder speed(double speed) { ... }
        public Builder loop(boolean loop) { ... }
        /** 0 = 不跳過（自然 catch-up）；> 0 = 落後超過此 tick 數的事件直接丟棄 */
        public Builder skip(int thresholdTicks) { ... }
        /** 聲音位置跟隨播放者移動 */
        public Builder followReplayer(boolean v) { ... }
        /** 移除錄製者頭部朝向對音效方向的影響 */
        public Builder applyRecorderOrientation(boolean v) { ... }
        /** 將音效方向旋轉到播放者頭部朝向空間 */
        public Builder applyReplayerOrientation(boolean v) { ... }
        /**
         * 虛擬嗚叭模式：所有聲音方向均指向播放點，並選擇能維持原始錄製音量的安全距離。
         * 玩家距播放點 < 原始錄製距離時，聲音往播放點方向延伸至原始距離處播放。
         * 玩家距播放點 >= 原始錄製距離時，聲音直接在播放點播放，音量 <= 原始。
         */
        public Builder speakerMode(boolean v) { ... }
        public PlaybackOptions build() { ... }
    }
}
```

**使用範例：**
```java
// 標準播放
SoundRecordingApi.startPlayback(recording, targets, PlaybackOptions.defaults());

// 完整第一人稱立體聲還原
SoundRecordingApi.startPlayback(recording, targets, PlaybackOptions.firstPerson());

// 自訂：在別處循環播放，1.5倍速
SoundRecordingApi.startPlayback(recording, targets,
    PlaybackOptions.builder()
        .anchor(newLocation)
        .speed(1.5)
        .loop(true)
        .build());
```

---

## 9. 持久化

`RecordingStorage` 為主要抽象介面，函式庫內建 `JsonRecordingStorage` 實作。

> ⚠️ **`JsonRecordingStorage` 為開發/除錯用途**：JSON 格式人類可讀易於除錯，但體積較大。  
> 正式部署建議改用壓縮二進位格式（NBT 或自訂 DataOutputStream），可縮小體積 5–10 倍並降低 GC 壓力。  
> 介面設計確保日後替換儲存後端不影響上層程式碼。

`JsonRecordingStorage` 預設儲存於 `plugins/SoundRecordingLib/recordings/<name>.json`。

```jsonc
{
  "id": "xxxxxxxx-xxxx-...",
  "name": "my-redstone-music",
  "createdAt": 1716000000000,
  "totalTicks": 400,
  "worldName": "world",
  "mode": "STATIC",
  "refX": 100.0, "refY": 64.0, "refZ": 200.0,
  "events": [
    {
      "relativeTick": 0,
      "soundKey": "minecraft:block.note_block.harp",  // ResourceLocation 字串
      "category": "RECORD",
      "relX": 0.5, "relY": 0.0, "relZ": 0.5,
      "volume": 1.0,
      "pitch": 1.0,
      "seed": 1234567890,
      "recorderYaw": 45.0
    }
    // ...
  ]
}
```

---

## 10. SoundRecord 插件指令

| 指令 | 說明 |
|------|------|
| `/sr record start [名稱]` | 開始錄製（對執行者自己） |
| `/sr record stop` | 停止錄製並儲存 |
| `/sr record list` | 列出所有已儲存錄音 |
| `/sr record delete <名稱>` | 刪除指定錄音 |
| `/sr play <名稱> [--offset <x> <y> <z>] [--speed <倍率>] [--loop]` | 播放錄音給執行者 |
| `/sr stop` | 停止目前播放 |
| `/sr info <名稱>` | 顯示錄音資訊（總 tick 數、事件數量等） |

權限節點：`soundrecord.*`、`soundrecord.record`、`soundrecord.play`

---

## 11. 依賴關係

### SoundRecordingLib `pom.xml` 需新增

- `com.github.retrooper:packetevents-spigot` (provided)
- `com.google.code.gson:gson`（JSON 序列化）

### SoundRecord `pom.xml` 需新增

- `dev.twme:SoundRecordingLib`（provided，以 plugin depend 方式載入）
- `plugin.yml` 中加入 `depend: [SoundRecordingLib, packetevents]`

---

## 12. 已知限制與未來工作

| 項目 | 說明 |
|------|------|
| 客製化聲音 | `SOUND_EFFECT` 可能包含 Resource Pack 的自訂音效，序列化為 ResourceLocation key，playback 前提是客戶端已載入相同 RP |
| 錄製過濾 | 目前記錄所有送往該玩家的聲音；可加入白名單/黑名單 category 過濾 |
| 跨世界播放 | `worldName` 為詮釋資料，不阻擋跨世界播放；需由呼叫者透過 `anchor` 提供目標世界的合理參考點 |
| 聲音 range 衍減 | Minecraft 的 `volume > 1.0` 會擴展聲音傳播範圍，playback 時移動音效位置可能造成距離衍減與錄製時不同，屬已知設計許可範圍 |
| speakerMode 與 volume > 1.0 | `speakerMode` 的 `distanceA` 使用物理距離計算，不考慮 `volume > 1.0` 延伸的最大聽覺範圍；對範圍擴展音效的衰減語意可能與原版略有差異，屬已知限制 |
| 錄製過濾 | `RecordingOptions.filter()` 支援 category 白名單，在 Netty thread 直接過濾以降低 GC 壓力 |
| 多人同步播放 | 現有設計可對多個 targets 同時播放，但沒有延遲補償（各玩家 ping 不同） |
| PLAYER_RELATIVE pitch | 僅補償水平 yaw，不補償 pitch（對 Minecraft 音效空間化影響極小） |

---

## 13. 已確認設計決策

| 編號 | 決策 | 結論 |
|------|------|------|
| D1 | 座標儲存方式 | 儲存相對座標；播放時可透過 `anchor` + `offset` 指定位置 |
| D2 | 錄製模式 | 同時支援 `STATIC`（固定參考點）與 `PLAYER_RELATIVE`（跟隨錄製者）|
| D3 | 儲存責任 | 方案 A：SoundRecordingLib 內建 JSON storage（開發/除錯用） |
| D4 | 播放排程 | 單一週期 task + cursor；自然 catch-up；`skipThresholdTicks > 0` 時丟棄超齡事件 |
| D5 | Sound 封包序列化 | 必頭轉為 ResourceLocation 字串儲存，不儲存 Network ID |
| D6 | Entity 消失回退 | entity 為 null 時記錄 `entityMissing=true` 且位置為 (0,0,0)；播放時變成 anchor 的腳音 |
| D7 | 玩家斷線處理 | `PlayerQuitEvent` 自動強制終止錄製/移除播放 targets |
| D8 | 跨世界設計 | `worldName` 為純 metadata；不阻擋跨世界播放；呼叫者需提供目標世界的 `anchor` |

## 14. 待討論項目

> E1 與 E2 均已確認，詳見 Section 13（D4、D5 等決策）。目前無未解決的待討論項目。

*(已解決的討論項目 E1/E2 記錄如下，供參考)*

**E1. 錄製者位置的執行緒安全** ✅ 已確認  
採用**選項 B 加動態註冊**：`ServerTickEndEvent` listener 僅在有錄製進行時存在。

```
startRecording() → activeRecordings 從空變非空 → 註冊 ServerTickEndEvent listener
stopRecording()  → activeRecordings 變空         → 取消註冊 listener
```

每 tick（僅錄製期間）：對每位錄製中玩家執行 `player.getLocation()`（主執行緒，安全）並寫入 `ConcurrentHashMap<UUID, Location>`；`SoundPacketListener`（Netty thread）讀取快照，完全迴避跨執行緒直接存取 NMS entity 欄位的原子性問題。

**E2. `startRecording` API 簽名** ✅ 已確認  
兩者均改用 Builder 模式：`RecordingOptions.builder(name).mode(...).build()` 與 `PlaybackOptions.builder().speed(...).build()`。

# MinigameLib API 文档

MinigameLib 是 Paper 1.21.11 小游戏基础库。对外暴露 Arena API 和 Setup API，世界复制、运行世界加载、loot chest、边界、UI、声音、材质包等由 MinigameLib 内部执行。

**构建**

```bash
mvn package
```

构建产物位于：

```text
target/minigamelib-1.0.0-SNAPSHOT.jar
```

**获取 API**

其他插件通过 Bukkit `ServicesManager` 获取入口：

```java
MinigameLibrary lib = Bukkit.getServicesManager().load(MinigameLibrary.class);
ArenaService arenas = lib.arenas();
SetupService setup = lib.setup();
```

`MinigameLibrary` 当前暴露：

```java
ArenaService arenas();
SetupService setup();
```

**SetupService**

用于外部插件制作地图配置工具。

```java
void startBlockMarker(Player player, SetupBlockMarkListener listener);
void stopBlockMarker(Player player);
boolean isBlockMarkerActive(Player player);
```

示例：

```java
lib.setup().startBlockMarker(player, mark -> {
  Block block = mark.block();
  ArenaPoint point = mark.point();
  player.sendMessage("marked " + block.getType() + " at " + point);
});
```

行为：

- 调用 `startBlockMarker` 后，玩家会获得一把内部标记过的木斧。
- 玩家手持该木斧左键方块时，会取消原交互并触发 `SetupBlockMarkListener`。
- `SetupBlockMark` 提供 `player`、`block`、`location`、`point`。
- `point` 使用被标记方块的位置，可直接转成 Arena 配置里的点位数据。
- `stopBlockMarker` 会停止该玩家的标记监听。

**ArenaService**

生命周期与运行时操作：

```java
void registerTemplate(ArenaTemplate template);
boolean unregisterTemplate(String templateId);
Optional<ArenaTemplate> findTemplate(String templateId);

CompletableFuture<ArenaHandle> createArena(ArenaCreateRequest request);
CompletableFuture<Void> startArena(String arenaId);
CompletableFuture<Void> stopArena(String arenaId, ArenaStopReason reason);
CompletableFuture<Void> destroyArena(String arenaId);

Optional<ArenaHandle> findArena(String arenaId);
List<ArenaHandle> arenas();
```

运行中消息/UI：

```java
CompletableFuture<Void> broadcastMessage(String arenaId, String message);
CompletableFuture<Void> broadcastMessages(String arenaId, List<String> messages);
CompletableFuture<Void> sendActionBar(String arenaId, String message);
CompletableFuture<Void> sendTitle(String arenaId, ArenaTitleFrame title);
CompletableFuture<Void> playSound(String arenaId, ArenaSound sound);
CompletableFuture<Void> updateBossBar(String arenaId, String title, double progress);
```

**基本流程**

```text
registerTemplate
  -> 玩家进服后发送已注册/已创建 Arena 的材质包
  -> createArena
  -> 内部复制模板世界并加载 runtime world
  -> 设置边界和 loot chest
  -> startArena
  -> 玩家按队伍传送到出生点
  -> 发放 beginning items
  -> 倒计时
  -> 游戏开始
  -> 边界阶段 / 统计 / 胜利判定
  -> stopArena
  -> 传送回大厅
  -> destroyArena
```

**世界模板**

外部不再直接调用 World API。Arena 创建时，MinigameLib 会从服务器世界容器下复制模板世界。

目录约定：

```text
server-root/
├── arena/
│   └── skybattle_map_01/
└── plugins/
    └── MinigameLib/
```

`ArenaTemplate.templateWorldName` 对应 `arena` 下的目录名。

`ArenaCreateRequest.runtimeWorldName` 是本局运行世界名。

**ArenaTemplate**

模板代表一种游戏或地图默认配置。

```java
new ArenaTemplate(
    "skybattle",
    "skybattle_map_01",
    defaultLayout,
    defaultSettings,
    defaultListener
);
```

字段：

- `templateId`：模板 ID。
- `templateWorldName`：模板世界目录名。
- `defaultLayout`：默认布局。
- `defaultSettings`：默认配置。
- `defaultListener`：默认生命周期监听器，可为 `null`。

**ArenaCreateRequest**

创建某一局 Arena。

```java
new ArenaCreateRequest(
    "skybattle_001",
    "skybattle",
    "skybattle_runtime_001",
    null,
    null,
    List.of("Steve", "Alex"),
    listener
);
```

字段：

- `arenaId`：本局 ID。
- `templateId`：使用的模板 ID。
- `runtimeWorldName`：运行世界名。
- `layout`：单局覆盖布局，可为 `null`。
- `settings`：单局覆盖设置，可为 `null`。
- `initialPlayerNames`：外部插件传入的玩家列表。
- `listener`：单局监听器，可为 `null`。

MinigameLib 会按玩家列表自动按红、黄、绿、蓝、橙、紫、白、粉、灰、青顺序分配队伍。

**ArenaLayout**

```java
new ArenaLayout(
    spawnPoints,
    teamSpawns,
    lootChestPoints,
    center,
    initialBorderRadius
);
```

字段：

- `spawnPoints`：通用出生点。没有队伍出生点时使用。
- `teamSpawns`：按队伍配置的出生点。
- `lootChestPoints`：兼容用 loot chest 点位；如果 `ArenaSettings.lootChests` 为空，会生成空箱子。
- `center`：Arena 中心点，用于世界边界。
- `initialBorderRadius`：初始边界半径。

`ArenaPoint`：

```java
new ArenaPoint(x, y, z, yaw, pitch);
```

**队伍系统**

队伍颜色顺序：

```text
RED, YELLOW, GREEN, BLUE, ORANGE, PURPLE, WHITE, PINK, GRAY, CYAN
```

队伍出生点：

```java
new ArenaTeamSpawn(
    ArenaTeamColor.RED,
    List.of(new ArenaPoint(0, 80, 0, 0, 0))
);
```

外部插件只传玩家列表，MinigameLib 自动分队。传送时优先使用对应队伍出生点。

**ArenaSettings**

核心配置：

```java
new ArenaSettings(
    10,
    "world",
    new ArenaPoint(0, 80, 0, 0, 0),
    false,
    scoreboard,
    bossBar,
    actionBar,
    title,
    sounds,
    resourcePack,
    beginningItems,
    lootChests,
    initialBoundaryWall,
    boundaryStages,
    ArenaVictoryCondition.OTHER_TEAMS_ALL_FAILED,
    messages
);
```

字段：

- `countdownSeconds`：倒计时秒数。
- `returnWorldName`：游戏结束后返回世界。
- `returnPoint`：游戏结束后返回位置。
- `saveWorldOnUnload`：卸载运行世界时是否保存。
- `scoreboard`：侧边栏配置。
- `bossBar`：BossBar 配置。
- `actionBar`：ActionBar 配置。
- `title`：屏幕中央 Title 配置。
- `sounds`：生命周期声音配置。
- `resourcePack`：材质包配置。
- `beginningItems`：开局物品。
- `lootChests`：loot chest 表。
- `initialBoundaryWall`：初始边界墙。
- `boundaryStages`：边界收缩阶段。
- `victoryCondition`：胜利条件。
- `messages`：聊天消息配置。

**物品系统**

`ArenaItemEntry` 可用于 `beginningItems` 和 `loot chest`。

```java
new ArenaItemEntry(
    "红队方块",
    new ItemStack(Material.WHITE_CONCRETE),
    64,
    ArenaItemMode.INFINITE,
    null,
    false
);
```

字段：

- `name`：显示名称。
- `item`：基础 `ItemStack`。
- `number`：数量。
- `mode`：物品模式。
- `potionConfig`：药水类物品配置。
- `igniteTntOnPlace`：如果基础物品是 TNT，放置后是否自动点燃，默认 `false`。

物品模式：

```text
DEFAULT      普通物品
INFINITE     无限物品
POTION       火球直线投射药水，命中后生成药效球
SELF_POTION  右键自用药水，立即消耗并给使用者效果
```

**无限队伍混凝土**

`INFINITE` 模式会按玩家队伍颜色发放混凝土。

行为：

- 每人保持 64 个。
- 放置后立即补回。
- 挖掉由无限物品放出的方块不会掉落。
- 队伍颜色对应混凝土颜色。

**药水物品**

药水配置：

```java
new ArenaPotionItemConfig(
    4.0,
    Duration.ofSeconds(8),
    PotionEffectType.SPEED,
    1,
    Duration.ofSeconds(2)
);
```

字段：

- `radius`：药效球半径。`SELF_POTION` 可忽略。
- `duration`：药效球持续时间。`SELF_POTION` 可忽略。
- `effectType`：药水效果。
- `amplifier`：效果等级，从 0 开始。
- `effectDuration`：每次施加给玩家的效果时长。

`POTION`：

- 右键后消耗物品。
- 使用 `SmallFireball` 沿玩家视线直线飞行。
- 不点燃、不爆炸破坏。
- 命中后生成一个药效球。
- 药效球每 1 秒对范围内玩家施加效果。
- 到期自动消失。

`SELF_POTION`：

- 右键后立即消耗物品。
- 立即把配置效果施加到使用者身上。
- 常用载体可以是雪球 `Material.SNOWBALL`。

**Loot Chest**

```java
new ArenaLootChest(
    new ArenaPoint(10, 70, 10, 0, 0),
    List.of(
        new ArenaLootEntry(itemEntry, 10.0, 0),
        new ArenaLootEntry(lateGameItem, 3.0, 1)
    ),
    ArenaLootPlacementMode.AUTO,
    true,
    false,
    20L * 60L,
    0L
);
```

`ArenaLootChest` 字段：

- `position`：箱子位置。
- `lootTable`：loot 表。
- `placementMode`：物品放置方式。
- `timedRegeneration`：是否定时重新生成。
- `timedDestruction`：是否定时销毁。
- `regenerationPeriodTicks`：重新生成间隔 tick；0 使用默认 60 秒。
- `destructionDelayTicks`：销毁延迟 tick；0 使用默认 60 秒。

`ArenaLootEntry` 字段：

- `item`：`ArenaItemEntry`。
- `weight`：权重。
- `earliestGenerationRound`：最早生成轮次。0 表示初始生成，1 表示第一次重新生成。

放置模式：

```text
AUTO      单物品居中，多物品左右对称
CENTER    中心格
MIRRORED  左右对称
```

**边界系统**

初始边界墙：

```java
new ArenaBoundaryWall(x1, x2, z1, z2);
```

边界阶段：

```java
new ArenaBoundaryStage(
    20.0,
    20.0,
    Duration.ofSeconds(30),
    Duration.ofSeconds(60)
);
```

字段：

- `xDistanceFromCenter`：距离中心点 X 方向距离。
- `zDistanceFromCenter`：距离中心点 Z 方向距离。
- `delayAfterPreviousStage`：距离上一个阶段结束后的等待时间。
- `duration`：本阶段收缩持续时间。

实现上会用 `WorldBorder.changeSize(...)` 按持续时间计算收缩速度。

**胜利条件与统计**

当前支持一种胜利条件：

```java
ArenaVictoryCondition.OTHER_TEAMS_ALL_FAILED
```

含义：

- 玩家死亡后标记为失败。
- 一个队伍所有玩家失败后，该队失败。
- 只剩一个未失败队伍时，该队胜利。

统计：

- 击杀数。
- 死亡数。
- 玩家失败状态。
- 队伍失败状态。

游戏结束时通过 `ArenaLifecycleListener.onGameEnded` 返回：

```java
ArenaGameResult(
    arenaId,
    winningTeam,
    teamStats,
    playerStats,
    reason
)
```

**Scoreboard**

```java
new ArenaScoreboardConfig(
    true,
    "SkyBattle",
    List.of(
        "队伍: {team}",
        "击杀: {kills}",
        "死亡: {deaths}",
        "存活队伍: {aliveTeams}"
    )
);
```

**BossBar**

```java
new ArenaBossBarConfig(
    true,
    "边界收缩中",
    BarColor.RED,
    BarStyle.SOLID,
    1.0,
    true
);
```

`countdownProgress = true` 时，倒计时阶段会按剩余秒数刷新进度。

**ActionBar**

```java
new ArenaActionBarConfig(
    true,
    "准备传送",
    "倒计时 {countdown}",
    "游戏开始",
    "游戏结束"
);
```

**Title**

```java
new ArenaTitleConfig(
    true,
    teleportFrame,
    countdownFrame,
    startedFrame,
    stoppedFrame
);
```

`ArenaTitleFrame`：

```java
new ArenaTitleFrame(
    "游戏开始",
    "祝你好运",
    Duration.ofMillis(250),
    Duration.ofSeconds(2),
    Duration.ofMillis(250)
);
```

**声音**

生命周期声音配置：

```java
new ArenaSoundConfig(
    true,
    ArenaSound.minecraft(Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f),
    ArenaSound.minecraft(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f),
    ArenaSound.custom("skybattle.start", 1.0f, 1.0f),
    null
);
```

也可以手动播放：

```java
arenas.playSound(arenaId, ArenaSound.custom("skybattle.event", 1.0f, 1.0f));
```

**材质包**

依赖插件可以把材质包 zip 放进自己的 `resources` 目录。MinigameLib 会在玩家进入服务器后，立即发送当前已注册模板或已创建 Arena 中启用的材质包。

```java
new ArenaResourcePackConfig(
    true,
    skyBattlePlugin,
    "packs/skybattle.zip",
    true,
    "请加载 SkyBattle 资源包",
    "http://your-public-host:port"
);
```

字段：

- `enabled`：是否启用。
- `ownerPlugin`：资源包所在插件。
- `resourcePath`：jar resources 内路径。
- `required`：是否强制加载，默认建议强制。
- `prompt`：客户端提示文本。
- `publicUrlBase`：公网可访问地址。为空时使用服务器 IP 和内置 HTTP 端口。

**聊天消息**

```java
new ArenaMessages(
    List.of("Arena {arena} 已创建"),
    List.of("正在传送"),
    "倒计时 {countdown}",
    List.of("开始"),
    List.of("结束: {reason}"),
    List.of("Arena 已销毁")
);
```

**文本占位符**

Scoreboard、BossBar、ActionBar、Title、Message 支持：

```text
{arena}
{template}
{world}
{status}
{players}
{aliveTeams}
{winner}
{team}
{kills}
{deaths}
{countdown}
{reason}
```

`{team}`、`{kills}`、`{deaths}` 会在按玩家渲染的内容里体现当前玩家数据。

**生命周期监听器**

```java
new ArenaLifecycleListener() {
  @Override
  public void onGameEnded(ArenaHandle arena, ArenaGameResult result) {
    Bukkit.getLogger().info("winner=" + result.winningTeam());
  }
};
```

支持事件：

- `onArenaCreated`
- `onBeforeTeleport`
- `onLootChestGenerated`
- `onCountdownTick`
- `onGameStarted`
- `onKillPlayer(ArenaHandle arena, String killerName, String victimName)`
- `onPlayerKilled(ArenaHandle arena, String playerName, String killerName)`
- `onPlayerFailed(ArenaHandle arena, String playerName, ArenaTeamColor teamColor)`
- `onTeamFailed(ArenaHandle arena, ArenaTeamColor teamColor, List<String> playerNames)`
- `onGameStopped`
- `onGameEnded`
- `onArenaDestroyed`

**注意事项**

- World API 不对外暴露。
- Arena 创建会异步复制世界，Bukkit 主线程操作由 MinigameLib 内部调度。
- `lootChestPoints` 是兼容点位；完整 loot 应优先使用 `ArenaSettings.lootChests`。
- `publicUrlBase` 为空时，内置 HTTP URL 可能只适合本地测试，生产服建议配置公网地址。

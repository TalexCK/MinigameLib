# MinigameLib

Paper 1.21.11 插件项目，使用 Maven 构建。

## 构建

```bash
mvn package
```

构建产物位于 `target/minigamelib-1.0.0-SNAPSHOT.jar`。

## World API 最小示例

模板世界目录约定：

```text
server-root/
├── world_templates/
│   └── skybattle_map_01/
└── plugins/
    └── MinigameLib/
```

其他插件可以通过 Bukkit `ServicesManager` 获取 API：

```java
MinigameLibrary lib = Bukkit.getServicesManager().load(MinigameLibrary.class);

WorldCreateRequest request = new WorldCreateRequest(
        "skybattle_map_01",
        "skybattle_runtime_001"
);

lib.worlds().createRuntimeWorld(request).thenAccept(runtimeWorld -> {
    World world = runtimeWorld.world();
    long copyMs = runtimeWorld.copyDuration().toMillis();
    long loadMs = runtimeWorld.loadDuration().toMillis();

    Bukkit.getLogger().info("World ready: " + world.getName());
    Bukkit.getLogger().info("copy=" + copyMs + "ms, load=" + loadMs + "ms");
});
```

如果只想拆开调用：

```java
lib.worlds()
        .copyTemplateWorld(request)
        .thenCompose(result -> lib.worlds().loadWorld(result.runtimeWorldName()));
```

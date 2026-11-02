# System Patterns

## Architecture Overview
Три слоя, зависимость идёт только вниз:

```
mixin/NoiseGeneratorSettingsMixin   (единственная точка входа в генерацию мира)
        |
mc/TerrainDensity                   (единственный мост к API density function Minecraft)
mc/FantasyTerrainMod                (ModInitializer: загрузка конфига)
        |
core/*                              (чистая Java, без типов Minecraft)
```

Если Mojang меняет API, правки касаются только `TerrainDensity`, миксина и `FantasyTerrainMod`.

## Where Is What (карта файлов)

| Путь (от корня репо) | Что это |
|---|---|
| `src/main/java/taleksbetterlandscape/mixin/NoiseGeneratorSettingsMixin.java` | Mixin на `NoiseGeneratorSettings.noiseRouter()`, на `RETURN` подменяет результат вызовом `TerrainDensity.wrap(seaLevel, router)` |
| `src/main/java/taleksbetterlandscape/mc/TerrainDensity.java` | `record` реализует `DensityFunction`: `inner + сумма фич`. Пересобирает `NoiseRouter` через рефлексию по каноническому конструктору, подменяя компонент `finalDensity`. Пропускает миры с уровнем моря не 63 |
| `src/main/java/taleksbetterlandscape/mc/FantasyTerrainMod.java` | Единственный объявленный `main`-entrypoint. `ID = "fantasy_terrain"`. Копирует дефолтный конфиг в `config/fantasy_terrain.properties`, грузит `TerrainConfig`, хранит его в `volatile` поле. `config()` равен `null` до инициализации, тогда ландшафт ванильный |
| `src/main/java/taleksbetterlandscape/core/TerrainFeature.java` | Интерфейс фичи: `density(Sample)`, `minDensity()` (по умолчанию 0), `maxDensity()`. Вложенный `Sample` (x, y, z, continents, erosion, ridges, seaLevel). Статические `clamp01`, `smoothstep` |
| `src/main/java/taleksbetterlandscape/core/Features.java` | Реестр `id -> factory`. Встроенный: `sea_stacks`. `build(config)` создаёт список по ключу `features`, на неизвестный id бросает исключение со списком известных |
| `src/main/java/taleksbetterlandscape/core/SeaStacks.java` | Фича морских башен. `fromConfig` валидирует диапазоны. Формула: зона по континентальности (smoothstep по краям полосы) умножается на smoothstep шума выше порога, вершина = `seaLevel + height * st`, плотность = `strength * st * clamp01((top - y) / band)` |
| `src/main/java/taleksbetterlandscape/core/Noise2D.java` | Value noise 2D с хэшем на целых координатах, результат в [0, 1]. Сид из конфига, не из сида мира |
| `src/main/java/taleksbetterlandscape/core/TerrainConfig.java` | Обёртка над `Properties`: дефолты из встроенного файла, пользовательский файл перекрывает. `number(key, min, max)`, `integer(key)`, `list(key)`; ошибки с именем ключа |
| `src/main/java/taleksbetterlandscape/TalekSBetterLandscape.java` | Шаблонный `ModInitializer` с `MOD_ID = "taleks-better-landscape"` и хелпером `id(path)`. **В `fabric.mod.json` не объявлен, не вызывается** |
| `src/client/java/taleksbetterlandscape/client/TalekSBetterLandscapeClient.java` | Пустой `ClientModInitializer` (шаблон). Не объявлен в `fabric.mod.json` |
| `src/client/java/taleksbetterlandscape/client/TalekSBetterLandscapeDataGenerator.java` | Пустой `DataGeneratorEntrypoint` (шаблон). Не объявлен |
| `src/client/java/taleksbetterlandscape/client/mixin/ExampleClientMixin.java` | Шаблонный клиентский mixin на `Minecraft.run`, ничего не делает |
| `src/client/resources/taleks-better-landscape.client.mixins.json` | Конфиг клиентских миксинов. Нигде не подключён (в `fabric.mod.json` его нет) |
| `src/main/resources/fabric.mod.json` | Метаданные: id `fantasy_terrain`, entrypoint `FantasyTerrainMod`, mixin-конфиг `fantasy_terrain.mixins.json`, зависимости `fabricloader >=0.18.4`, `minecraft ~26.1`, `java >=25`, лицензия MIT |
| `src/main/resources/taleks-better-landscape.mixins.json` | Основной mixin-конфиг (пакет `taleksbetterlandscape.mixin`, класс `NoiseGeneratorSettingsMixin`, JAVA_25) |
| `src/main/resources/taleks-better-landscape.default.properties` | Единственный источник значений по умолчанию; копируется в конфиг игрока |
| `src/main/resources/assets/taleks-better-landscape/icon.png` | Иконка. В `fabric.mod.json` ключа `icon` нет |
| `src/test/java/fantasyterrain/core/CoreSelfCheck.java` | Самопроверка ядра: `main()` с `AssertionError`, без JUnit. Пакет `fantasyterrain.core` (старый), а не `taleksbetterlandscape.core` |
| `build.gradle`, `settings.gradle`, `gradle.properties` | Сборка Fabric Loom, см. `techContext.md` |
| `.github/workflows/build.yml` | CI: `./gradlew build` на push и PR, JDK 25 (Microsoft), артефакты из `build/libs/` |
| `README.md` | Почти пустой, шаблон Fabric (ссылка на документацию) |

## Key Technical Decisions
- Один хук в мир (миксин на `noiseRouter`), без датапаков и без регистрации в реестрах.
- `NoiseRouter` пересобирается рефлексией по `RecordComponent`, чтобы не зашивать список полей, который меняется между версиями. При сбое логируется ошибка и ландшафт остаётся ванильным.
- Плотность фич только неотрицательная (`SeaStacks` не вырезает землю).
- `minValue()/maxValue()` у `TerrainDensity` складываются из границ фич; Minecraft использует их для пропуска работы, поэтому границы должны быть честными.
- Сид шума берётся из конфига, а не из мира, поэтому раскладка башен одинакова во всех мирах (меняется только континентальность). Помечено комментарием `ponytail:`.

## Design Patterns in Use
- Реестр + фабрики (`Features`).
- Слой-мост (`TerrainDensity`) между чистым ядром и API игры.
- Конфиг: дефолт в одном месте, пользовательский файл только перекрывает.
- Fail-loud валидация конфига.

## Component Relationships
`FantasyTerrainMod.onInitialize` -> `TerrainConfig.load` -> статическое поле `config`. Затем `NoiseGeneratorSettingsMixin` -> `TerrainDensity.wrap` -> `Features.build(config)` -> `SeaStacks.fromConfig`. Во время генерации `TerrainDensity.compute/fillArray` -> `TerrainFeature.density(Sample)`, где `Sample` читает `continents/erosion/ridges` из ванильного router.

## Critical Implementation Paths
- Генерация чанка: `finalDensity.compute` -> `inner.compute + extra` -> `SeaStacks.density`.
- `mapAll` обязан отображать и вспомогательные функции (`continents`, `erosion`, `ridges`), иначе их шум не подключится к миру.
- `codec()` бросает `UnsupportedOperationException`: объект существует только в рантайме и на диск не пишется.

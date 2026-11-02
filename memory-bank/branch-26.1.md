# Branch 26.1 (актуальная версия мода)

Остальные файлы memory-bank описывают **master** (старый Java/mixin-прототип «Fantasy Terrain»). Рабочая, актуальная версия мода лежит в ветке **26.1** (коммит `beef803 updated to 1.1.0`). Пользователь сказал работать с ней. Локально клон переключён на ветку `26.1`.

## Суть
Мод «Talek's Better Landscape» v1.1.0 для Minecraft 26.1: кинематографичный рельеф. Почти всё сделано **датапаком на Lithostitched**, а Java нужна только для двух своих Feature. Миксинов нет (`taleks-better-landscape.mixins.json` с пустым списком `mixins`).

## Версии и зависимости (gradle.properties)
Minecraft 26.1, Fabric Loader 0.19.5, Fabric API 0.145.1+26.1, **Lithostitched 1.8.1-fabric-26.1** (Modrinth maven, обязательная зависимость в `fabric.mod.json`), Java 25, Loom 1.18-SNAPSHOT, Gradle wrapper 9.7.1, `-Xmx4G`.

## Метаданные
`fabric.mod.json`: id `taleks-better-landscape`, автор Talek, entrypoint `main` = `taleksbetterlandscape.TalekSBetterLandscape`, лицензия MIT (файл `LICENSE` в репо при этом CC0). В ветке 26.1 нет проблемы с несовпадением имён mixin-конфига, которая была в master.

## Java (src/main/java/taleksbetterlandscape)
| Файл | Назначение |
|---|---|
| `TalekSBetterLandscape.java` | Регистрирует Feature `shore_shelf` и `rocky_cove`; через Fabric `BiomeModifications` убирает карверы `cave`, `cave_extra_underground`, `canyon` с пляжей. Хелпер `id(path)` |
| `ShoreShelfFeature.java` + `ShelfMath.java` | Песчаная полка вокруг океанских берегов: окно `16 + 2R`, снимок высот через `Heightmap` (`OCEAN_FLOOR_WG`, `WORLD_SURFACE_WG`) и `level.hasChunk`, потом `setBlock`. Это готовый образец, как читать высоты вокруг чанка и переписывать блоки |
| `RockyCoveFeature.java` + `CoveMath.java` + `CoveShape.java` | Скальные бухты (в этой сессии подробно не читал) |

Паттерн: чистая математика отдельно (`*Math`), Feature тонкая. Feature подключаются JSON-ом через `lithostitched:add_features`.

## Данные (src/main/resources/data/taleks-better-landscape)
- `lithostitched/worldgen_modifier/*.json`: `wrap_density_function` на `overworld/offset` (`mountain_ridges`, `continents`, `erosion`, `ridges`, `forest_hills`, `forest_spires`, `fantasy_terrain`), `add_surface_rule` (`steep_rock_gradient`, `forest_floor`, `shore_sand_gravel`, `smooth_river_banks` и др.), `add_features` (`add_shore_shelf` на `#minecraft:is_ocean`, шаг `underground_ores`; `add_rocky_cove`; `add_stylized_rocks` на горные и лесные биомы, шаг `local_modifications`), `no_beach_*`, `no_cave_entrances`, `no_cove_frozen_ocean`.
- `lithostitched/biome_injector/*.json`: `force_placement` горных биомов по `taleks-better-landscape:mountain_mask` (например windswept_hills при `mountain_mask >= 0.38`) и `fantasy_cherry_grove`.
- `lithostitched/fast_noise_config/*.json`: шумы гор, башен, холмов.
- `worldgen/density_function/*.json`: `mountain_mask` (по erosion, continents, ridges_folded), `mountain_shape`, `mountain_relief` (= `0.8 * mask² * shape` + windswept-часть), `windswept_mask`, `forest_hills*`, `spire_delta`, `forest_spire_*`, `tepui_delta`, `fantasy_*`, `river_mask`, `beach_mask`, `humidity`.
- `worldgen/noise`, `configured_feature`, `placed_feature`, `processor_list`, `structure/*.nbt` (камни `rock_*`).
- `docs/TUNING_RU.md`: таблица ручек (высота гор = первый множитель в `mountain_relief.json`, сколько гор = `ramp` в `mountain_mask.json` и т.д.).

## Поверхность гор сейчас
`steep_rock_gradient.json` (`lithostitched:add_surface_rule`, overworld): в 8 горных биомах (jagged_peaks, frozen_peaks, stony_peaks, snowy_slopes, grove, windswept_hills, windswept_gravelly_hills, windswept_forest) на `steep` склонах слои stone / andesite / gravel по глубине и шуму; плюс пятна gravel/andesite/stone выше абсолютной **Y 125** (пики) и **Y 100** (windswept); плюс те же правила в зонах `fantasy_zone` (порог 0.5) и `forest_spire_zone` (0.62). Стиль автора: абсолютные Y и шумы, без привязки к вершине конкретной горы. Снега, льда, туфа и травы на горах там нет.

## Что не прочитано
Тела `RockyCoveFeature`, `CoveMath`, `CoveShape`, содержимое большинства density_function/fast_noise_config, `.nbt` структуры. Сборка и запуск не выполнялись.

## Добавлено 02.11: блоки на горах (не собрано)
Новое условие поверхности `taleks-better-landscape:peak` (`PeakCondition.java`, математика в `PeakMath.java`, регистрация в `TalekSBetterLandscape.onInitialize`) и правило `lithostitched/worldgen_modifier/mountain_blocks.json` (priority 900, раньше `steep_rock_gradient.json`): снег, голубой лёд, градиент туф-гравий-андезит-камень, трава на ровном, в биомах meadow, stony_peaks и 4 windswept. Для наследования от `SurfaceRules$LazyXZCondition` добавлен `taleks-better-landscape.accesswidener` (`loom.accessWidenerPath` в `build.gradle`, ключ `accessWidener` в `fabric.mod.json`). Проверка математики: `javac` + `java taleksbetterlandscape.PeakMathCheck`. Подробности и риски: `docs/superpowers/specs/2026-11-02-mountain-surface-design.md`.

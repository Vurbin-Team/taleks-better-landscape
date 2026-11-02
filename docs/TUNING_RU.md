# Рельеф: как устроено и что крутить

Всё построено на Lithostitched 1.8.1 (26.1). Менять можно только JSON-файлы. Новые чанки генерируются
с новыми настройками, старые остаются как есть.

## Идея
Рельеф и биомы привязаны к одним и тем же «маскам», поэтому горы всегда горные биомы, а скалы только там, где горы.

| Слой | Инструмент Lithostitched | Файлы |
|---|---|---|
| Хребты: острые гребни + складчатые гряды + зубцы | `fast_noise` (ridged), `shift` (искривление), `axis`+`sin` (параллельные гряды), `wrap_density_function` на `overworld/offset` | `mountain_ridges`, `density_function/mountain_*`, `fast_noise_config/mountains, mountain_warp_*, mountain_fold_phase, crags` |
| Windswept (разбитые холмы) | то же, маска по эрозии ~0.5 | `windswept_mask`, `windswept_ridges` |
| Небольшие холмы в лесах | маска по влажности (`humidity`) | `forest_hills`, `density_function/forest_hills*`, `fast_noise_config/hills` |
| Фэнтези: карстовые башни | `cellular` (distance + cell_value, одинаковый salt), `select` (3 высоты), `shift` | `spire_*`, `fast_noise_config/spire_*` |
| Редкие низкие башни в лесах | та же схема `cellular` + `select`, отдельная редкая зона (ванильный шум `forest_spire_zone`) | `forest_spires`, `density_function/forest_spire_*`, `fast_noise_config/forest_spire_*` |
| Фэнтези: столовые горы | `floor` (ступени) | `tepui_delta`, `fast_noise_config/tepui` |
| Плавный переход фэнтези и обычного рельефа | `mix` | `fantasy_terrain`, `fantasy_delta` |
| Биомы под рельеф | `biome_injector` / `force_placement` с density function в параметрах | `biome_injector/*` |
| Скалы только в горах | `add_surface_rule` | `steep_rock_gradient` |
| Валуны в зонах гор | `add_features` + `lithostitched:condition` / `sample_density` | `add_mountain_boulders`, `placed_feature/mountain_boulder` |

## Основные ручки
| Что изменить | Где | Сейчас |
|---|---|---|
| Высота гор | `density_function/mountain_relief.json`, первый множитель | `0.8` (1.0 ≈ 128 блоков) |
| Сколько гор | `density_function/mountain_mask.json`, `ramp` по эрозии (0.15 .. 0.6) | горы ~8% суши |
| Резкость гребней | `mountain_shape.json`, `square` над `r01` (убрать = мягче) | `square` |
| Высота лесных холмов | `density_function/forest_hills.json` | `0.10` (≈ ±4 блока типично) |
| Как часто фэнтези | `fantasy_mask.json`, `ramp(..., 0.42, 0.58)` и порог `0.50` в `steep_rock_gradient.json` | ~3% суши |
| Редкость лесных башен | `forest_spire_mask.json`, `ramp(..., 0.55, 0.70)` (выше числа = реже) и порог `0.62` в `steep_rock_gradient.json` | ~1.3% суши |
| Высота лесных башен | `forest_spire_delta.json` в `select`: `0.11` и `0.20` (14 и 26 блоков) | до 26 блоков |
| Количество башен в зоне | `forest_spire_delta.json`, диапазон `[0.1, 0.5]` / `[0.5, 1]` и радиус `0.26` | ~6–10 на зону |
| Какие биомы горные | `biome_injector/*.json` и список `MOUNTAIN_BIOMES` в `steep_rock_gradient.json` | 8 ванильных |

## Отключить слой
Заменить содержимое файла в `worldgen_modifier/` на `{ "type": "lithostitched:no_op" }`
(для биомных инжекторов: удалить файл).
- Без фэнтези целиком: `fantasy_terrain.json`, `biome_injector/fantasy_cherry_grove.json`
- Без лесных холмов: `forest_hills.json`
- Без лесных башен: `forest_spires.json`

## Замечания
- Превью в `docs/previews` посчитаны по высоте `offset` (без ванильного 3D-шума и jaggedness), в игре горы будут чуть выше и рваннее.
- Моды с биомами/структурами, привязанными к тегам вроде `is_mountain`, увидят эти биомы там, где теперь горы.

## Скалистые пики (`rocky_peaks.json`)
Работает только в `jagged_peaks`, `stony_peaks`, `frozen_peaks`. Все ручки это числа в JSON.

| Что изменить | Где | Сейчас |
|---|---|---|
| Сколько склонов становится камнем | `lithostitched:slope` `[2, 256]` в `rocky_peaks.json` (больше число = только круче) | от 2 блоков перепада |
| Доля камня на пологих склонах | порог `peak_rock_cover` у `slope [1,1]` (выше = меньше камня) | -0.05 |
| Камень на ровном | порог `peak_rock_cover` у `slope [0,0]` и `fraction` 0.5 | 0.14 |
| Где кончаются предгорья | `peak` `fraction min` (0.10) и `min_relief` (40) | 0.10 / 40 |
| Размер шапки без изменений | `peak` `drop min` | 15.001 |
| Размер пятен породы | `firstOctave` в `worldgen/noise/peak_rock_tone.json` и `peak_rock_cover.json` | -3 / -4 |
| Трава в горах | в `mountain_blocks.json` список биомов у ветки `grass_block` | meadow, windswept (без `stony_peaks`) |

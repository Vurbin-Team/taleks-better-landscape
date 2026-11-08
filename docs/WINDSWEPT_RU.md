# Windswept: пологие холмы, трава, камни

Относится к четырём биомам: `windswept_hills`, `windswept_gravelly_hills`, `windswept_forest`, `windswept_savanna`.
Новые чанки генерируются с новыми настройками, старые не меняются. **В игре не проверено** (в среде, где делалась правка, нет Gradle и Minecraft).

## Что изменилось
| Что | Было | Стало |
|---|---|---|
| Рельеф пояса | ступенчатые плато до ~30 блоков + сеть расщелин | мягкие холмистые волны (`windswept_relief`) |
| Поверхность | туф → гравий → андезит → камень, снег на перепадах | трава почти везде, камень на крутых склонах, пятна `podzol` / `coarse_dirt` как вереск, снега нет |
| Камни | старые `stylized_rock` (редкие, 1 на 5 чанков) | свои валуны + россыпи мелких камней, чаще; в `gravelly_hills` ещё чаще |
| Трава | ванильная | плюс пучки сухой травы (ванильные блоки `short_dry_grass` / `tall_dry_grass`, своя фича `windswept_dry_grass`) |

`windswept_gravelly_hills` каменистее остальных: трава с большими участками гравия и камня (по грубой оценке трава ~55%, у остальных ~78%).

## Ручки
| Что менять | Где | Сейчас |
|---|---|---|
| Высота широких холмов (1.0 = 128 блоков) | `density_function/windswept_relief.json`, первый множитель | `0.18` (около ±8 блоков типично, до ~24) |
| Высота мелких бугров | там же, второй множитель | `0.035` |
| Размер холмов | `fast_noise_config/windswept_swell.json` (`frequency`, меньше = шире) и `windswept_bumps.json` | 0.011 (~90 блоков) / 0.035 (~30) |
| Совсем плоско | оба множителя в `windswept_relief.json` в `0` | |
| Сколько вереска | `docs/tools/gen_windswept_surface.py`, `HEATH` и `HEATH_EDGE` (выше = меньше) | 0.40 / 0.32 (gravelly 0.45 / 0.37) |
| Сколько гравия и камня на ровном (только gravelly) | там же, `STONE`, `GRAVEL`, `GRAVEL_EDGE`, `COARSE` | 0.52 / 0.30 / 0.22 |
| Камень на склонах | там же, `STEEP` (перепад высот соседних колонок) | 3 (gravelly: 2) |
| Размер пятен | `worldgen/noise/windswept_heather.json`, `windswept_rock_patch.json`, `firstOctave` (-5 ≈ 32 блока, -4 ≈ 16) | -5 |
| Частота валунов | `placed_feature/windswept_boulder.json` (`count`), `_dense` для gravelly | 0–2 / 1–4 на чанк |
| Частота мелких камней | `placed_feature/windswept_stones.json`, `_dense`: `below_noise` / `above_noise` (число патчей на чанк) и `count` (камней в патче) | 0 и 2 / 1 и 4 |
| Состав камней | `configured_feature/windswept_stones.json` (веса), `processor_list/rock_windswept.json` (валуны) | stone, andesite, cobblestone, mossy |

Файл `windswept_surface.json` собирается скриптом: правьте пороги в `docs/tools/gen_windswept_surface.py` и запускайте `python3 docs/tools/gen_windswept_surface.py` из корня проекта. Числа можно править и прямо в JSON.

## Выключить по частям
- Старый рельеф вернуть нельзя (файлы плато удалены), но можно обнулить волны, см. «Совсем плоско».
- Камни и валуны: удалить `add_windswept_rocks.json` и `add_windswept_gravelly_rocks.json`.
- Сухая трава: удалить `add_windswept_dry_grass.json` (густота: `count` и `above_noise` в `placed_feature/windswept_dry_grass.json`).
- Новую поверхность: удалить `windswept_surface.json` (тогда в windswept будет ванильная поверхность).

## Если мир не загрузится
Смотрите `logs/latest.log` (или `crash-reports/`) и ищите `taleks-better-landscape`, `lithostitched`, `Failed to parse`. Подозреваемые файлы, от самых вероятных:
1. `lithostitched/worldgen_modifier/add_windswept_dry_grass.json` и `placed_feature/windswept_dry_grass.json`. Ванильные фичи в windswept добавлять нельзя: общая фича в разных биомах даёт ошибку «Feature order cycle» (так было с `patch_dry_grass_desert`, см. пустыню и `windswept_savanna`).
2. `lithostitched/worldgen_modifier/add_windswept_rocks.json`, `add_windswept_gravelly_rocks.json` и фичи `placed_feature/windswept_*.json`, `configured_feature/windswept_*.json`, `processor_list/rock_windswept.json`.
3. `lithostitched/worldgen_modifier/windswept_surface.json` и шумы `worldgen/noise/windswept_heather.json`, `windswept_rock_patch.json`.
4. `worldgen/density_function/windswept_relief.json` и `lithostitched/fast_noise_config/windswept_swell.json`, `windswept_bumps.json`.
5. Изменённые старые файлы: `mountain_blocks.json`, `hill_terraces.json`, `steep_rock_gradient.json`, `add_stylized_rocks.json` (из них только убраны windswept-биомы и одна ветка).

Быстрый способ найти виновника: убирать файлы из списка по одному, начиная с пункта 1, пока мир не загрузится.

## Как проверялось
Рельеф: грубая симуляция только добавки `windswept_relief` (свой симплекс-шум, без ванильного 3D-шума и jaggedness): стандартное отклонение ~7.6 блока, 95% значений в пределах ±14, перепад ≥2 блоков между соседними колонками у ~8% колонок от одной этой добавки, ≥3 практически нет. Поверхность: проверена логика правила на случайных значениях шума и перепада (все четыре слоя, без «протечек» глубже 4 блоков); проценты в таблице выше зависят от реального распределения шума и в игре будут другими. JSON всех файлов разбирается без ошибок, все внутренние ссылки находятся.

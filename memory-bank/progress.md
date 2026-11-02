# Progress

## What Works
(по чтению кода; сборка и запуск не выполнялись)
- Ядро: `TerrainConfig`, `Features`, `SeaStacks`, `Noise2D`, `TerrainFeature` написаны полностью и покрыты `CoreSelfCheck` (дефолты, пустой список = ваниль, неизвестная фича, плохие значения, форма и границы башни, отсутствие башен на суше и в глубоком океане, детерминизм).
- Мост `TerrainDensity` и миксин `NoiseGeneratorSettingsMixin` написаны.
- Конфиг по умолчанию подробно документирован комментариями.

## What's Left to Build
- Новые фичи кроме `sea_stacks` (описание мода говорит «and more»).
- Опционально: сид шума от сида мира.
- Содержательный README (сейчас шаблонный).
- Реальные тесты в Gradle (сейчас только `main`-самопроверка).

## Current Status
Ранняя стадия, один коммит. Код ядра выглядит завершённым, а упаковка мода (метаданные, имена файлов) недоведена после переименования проекта.

## Known Issues
1. **Несовпадение имён mixin-конфига (вероятная поломка).** `fabric.mod.json` ссылается на `fantasy_terrain.mixins.json`, а файл в репозитории называется `taleks-better-landscape.mixins.json`. Скорее всего мод не загрузит миксин (ошибка при старте или ландшафт без эффекта). Не проверено запуском.
2. **Два идентификатора.** `fabric.mod.json` id `fantasy_terrain` против `MOD_ID = "taleks-better-landscape"` (Gradle `modId`, папка ассетов, `TalekSBetterLandscape`). Loom `mods { "taleks-better-landscape" }` тоже не совпадает с id в `fabric.mod.json`.
3. **Мёртвый шаблонный код:** `TalekSBetterLandscape` (не entrypoint), `TalekSBetterLandscapeClient`, `TalekSBetterLandscapeDataGenerator`, `ExampleClientMixin` и `taleks-better-landscape.client.mixins.json` нигде не подключены в `fabric.mod.json`.
4. **Лицензия:** `LICENSE` это CC0, а `fabric.mod.json` заявляет MIT.
5. **Метаданные:** `authors` пуст, `icon` не указан, хотя `icon.png` лежит в `assets/taleks-better-landscape/`.
6. **Тест:** пакет `fantasyterrain.core` не совпадает с `taleksbetterlandscape.core`; упомянутого Gradle-таска `selfCheck` нет.
7. **README:** в названии остался HTML-эскейп `Talek&#39;s`, текст шаблонный.
8. **Fabric API:** подключена в `build.gradle`, но не объявлена как зависимость в `fabric.mod.json`.
9. `Features.build` вызывается при каждой выдаче noise router, а неверный конфиг бросает исключение прямо в генерации мира (по дизайну, «fail loudly»).

## Decision History
- Проект создан из шаблона Fabric (Loom), затем логика была написана под именем `Fantasy Terrain` (пакеты `fantasyterrain.*`, id `fantasy_terrain`), после этого пакеты и файлы ресурсов переименованы в `taleksbetterlandscape` / `taleks-better-landscape`, но `fabric.mod.json`, `FantasyTerrainMod.ID` и пакет теста остались со старыми именами. Это вывод из состояния файлов; истории коммитов нет (один коммит).

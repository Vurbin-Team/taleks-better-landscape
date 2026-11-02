# Project Brief

## Project Name
Talek's Better Landscape (репозиторий `Vurbin-Team/taleks-better-landscape`, Gradle-проект `taleks-better-landscape`).
Внутри кода и в `fabric.mod.json` мод называется **Fantasy Terrain** (id `fantasy_terrain`). Два имени живут параллельно, это следствие переименования (см. `activeContext.md`).

## Core Goal
Fabric-мод для Minecraft 26.1, который меняет только форму рельефа Overworld: добавляет «сказочные» элементы ландшафта поверх ванильной генерации. Сейчас реализована одна фича: морские скалы-башни (`sea_stacks`) в мелкой воде у берега.

## Key Requirements
- Только форма террейна, без новых блоков, биомов, структур.
- Фичи подключаются списком в конфиге; пустой список означает ваниль.
- Чистая Java-логика фич без типов Minecraft, чтобы её можно проверять без игры.
- Ошибочный конфиг должен падать громко, с именем ключа.
- Новая фича добавляется реализацией `TerrainFeature` и `Features.register(...)`.

## Scope
В рамках: Overworld (уровень моря 63, включая large biomes и amplified). Вне рамок: Nether (уровень 32) и End (уровень 0), они пропускаются в `TerrainDensity.wrap`.

## Meta
- Первоначальный коммит один: `8ce117e created project`. Ветка клонирована с `--depth 1`.
- Лицензия в `LICENSE`: CC0. В `fabric.mod.json` указано MIT (расхождение).
- Автор в `fabric.mod.json` не заполнен (`"authors": []`).

# Tech Context

## Technologies Used
- Java 25 (`options.release = 25`, source/target 25).
- Minecraft 26.1, Fabric Loader 0.19.5, Fabric API 0.145.1+26.1.
- Fabric Loom: плагин `net.fabricmc.fabric-loom`, версия `1.18-SNAPSHOT`.
- Gradle wrapper 9.7.1.
- Mixin (Sponge), compatibility level `JAVA_25`.
- Маппинги Mojang (имена вроде `net.minecraft.resources.Identifier`, `NoiseGeneratorSettings`, `DensityFunction`).

## Development Setup
- `./gradlew build` собирает мод; `build/libs/` содержит jar и sources jar.
- Source sets разделены (`loom { splitEnvironmentSourceSets() }`): `main` и `client` входят в один мод `taleks-better-landscape`.
- Datagen настроен через `fabricApi.configureDataGeneration` (client = true, modId `taleks-better-landscape`), но генератор пустой.
- JVM для Gradle: `-Xmx1G`, включены parallel и configuration-cache.
- `.gitignore` исключает `.gradle/`, `build/`, `run/`, файлы IDE.

## Technical Constraints
- Нужен JDK 25 и Minecraft 26.1 (зависимость `~26.1`).
- Работает только для Overworld (уровень моря 63).
- Конфиг читается один раз при старте; правка требует перезапуска.
- Зависимости, кроме Fabric API, не используются.

## Dependencies
- `minecraft`, `fabric-loader`, `fabric-api` (см. `gradle.properties`).
- В `fabric.mod.json` зависимость от Fabric API не объявлена, хотя в `build.gradle` она подключена как `implementation`.

## Tool Usage Patterns
- Проверка ядра: `src/test/java/fantasyterrain/core/CoreSelfCheck.java`, запускается как `main`. В комментарии упомянут Gradle-таск `selfCheck`, но в `build.gradle` такого таска нет, значит запускать вручную через `java` с классами ядра. Не проверял в этой сессии.
- CI: `.github/workflows/build.yml`, `ubuntu-24.04`, `./gradlew build`, артефакты загружаются как `Artifacts`.
- Сборка и запуск в этой сессии не выполнялись.

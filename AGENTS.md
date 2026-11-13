# FlorisBoard fork: Russian suggestions — рабочие заметки

Форк `florisboard/florisboard` (ветка `feature/russian-suggestions`, от тега v0.5.2).
Цель: вернуть подсказки слов в FlorisBoard 0.5.x, где NLP-движок ещё не реализован
(встроенный `LatinLanguageProvider.suggest()` возвращал пустой список).

## Что сделано

1. **`LatinLanguageProvider.kt`** переписан: словари загружаются по языку подтипа из
   assets (`ime/dict/<lang>.json`, англ. — `data.json`), подсказки — префиксный
   поиск + сортировка по частоте, spell — «слово в словаре → valid, иначе typo
   с ближайшими по префиксу вариантами». Словари кэшируются в памяти (TreeMap).
2. **`app/src/main/assets/ime/dict/ru.json`** — 60 000 самых частотных русских
   слов. Источник: hermitdave/FrequencyWords (частоты OpenSubtitles 2018,
   `content/2018/ru/ru_full.txt`), фильтр `^[а-яё]+$`, частота шкалой 1–255
   по рангу: `max(1, round(255 * (1 - i/N)))`.
3. **jetpref подключён сабмодулем** `jetpref-build` (коммит `d6e12dda`, именно
   из него собирался снапшот `20251119T222500Z-SNAPSHOT`, на который ссылался
   v0.5.2), подключён через `includeBuild()` + явные `dependencySubstitution`
   (автоматическая подстановка через публикации vanniktech-плагина не работает).
4. Версия изменена: `0.5.2 → 0.5.3` (versionCode 117 → 118) в `gradle.properties`.
5. Кредиты форка — в About (`about__fork_credit__*` в strings.xml).
6. **Force suggestions** (кнопка принудительных подсказок): `KeyCode.TOGGLE_FORCE_SUGGESTIONS` (-246)
   + преф `suggestion__force_enabled` (`prefs.suggestion.forceEnabled`). Когда включено,
   клавиатура игнорирует запреты поля ввода (`TYPE_NULL`, `NO_SUGGESTIONS`, raw-редакторы)
   и показывает подсказки в любом поле (необходимо для терминалов при работе с ИИ-агентами).
   Точки изменения: `AbstractEditorInstance.isRawInputForSuggestions()` (raw-редакторы
   обрабатываются как rich), `shouldDetermineComposingRegion`, `isComposingEnabled`
   в EditorInstance, `isSuggestionOn()` в NlpManager. Иконка — собственный векторный
   drawable `ic_force_suggestions.xml` (material-icons-extended в новом compose-bom
   частично недоступен), строки `force_suggestions__*` и
   `quick_action__toggle_force_suggestions*` в strings.xml, стандартный набор —
   QuickActionArrangement.Default.
7. CI: `.github/workflows/russian-suggestions.yml` — `assembleDebug` при push в
   ветку; артефакт `app-debug.apk`. Сборка debug, т.к. release требует
   подписи (секреты), а debug устанавливается рядом с официальным приложением
   (пакет `dev.patrickgold.florisboard.debug`).

## Известные проблемы и решения

- **Локальная сборка в Termux не работает**: на устройстве отсутствует Android SDK
  (нет `ANDROID_HOME`, `~/android-sdk`, `local.properties`), `gradlew assembleDebug`
  завершается ошибкой на конфигурации `:lib:native` с "SDK location not found".
  **Сборка выполняется только через GitHub Actions**: push в `feature/russian-suggestions`
  → workflow `russian-suggestions.yml` (~7 мин) → артефакт `app-debug.apk`.
- **Снапшот jetpref удалён из Sonatype**: `20251119T222500Z-SNAPSHOT` недоступен.
  Релиз `0.5.0-beta01` собран под compileSdk 37 / Kotlin 2.4 — с v0.5.2
  (SDK 36, Kotlin 2.2.20) возникает конфликт метаданных. Решение — composite build
  на соответствующем коммите.
- **JitPack не подходит**: превышение времени ожидания, исторические сборки
  jetpref находятся в статусе Error.
- **rish (Shizuku) не может установить APK из Termux**: shell не читает
  `/data/data/com.termux/...`, а `/sdcard` недоступен system_server (avc denied
  на fuse); `pm install` из `/data/local/tmp` завершается ошибкой Failed to parse APK
  при передаче файла через stdin (rish перехватывает stdin). Рабочий способ —
  `termux-open <apk>` (системный установщик, подтверждение пользователем).
- **«App not installed» при попытке обновиться на 0.4.6**: понижение
  versionCode / несовпадение подписей. Debug-форк — отдельный пакет,
  конфликтов нет.
- Сайт аддонов FlorisBoard (`addons.florisboard.org`) с устройства не
  разрешается DNS — словарь был сгенерирован из открытых источников.

## Как обновлять словарь

1. Править источник частот (`ru_full.txt`, формат `слово частота`).
2. Перегенерировать: `python3` (скрипт в истории коммитов; фильтр `[а-яё]+`,
   топ-60k, шкала 1–255 по рангу).
3. `cp ru.json app/src/main/assets/ime/dict/ru.json && git commit && git push` —
   CI соберёт новый APK примерно за 10 минут.
4. Личные слова не требуют пересборки — добавлять через Settings → Dictionary →
   User Dictionary в самом приложении.

## Структура локальных артефактов (на устройстве)

- `~/floris-kbd-fork/fb-fork` — рабочий клон форка (ветка feature/russian-suggestions)
- `~/floris-kbd-fork/fb` — клон исходников upstream (main, для справки)
- `~/floris-kbd-fork/dict` — `ru_full.txt` (исходные данные) и `ru.json`
- `~/floris-kbd-fork/fb-art` — собранный app-debug.apk
- `~/floris-kbd-fork/florisboard-0.4.6.apk` — официальный стабильный релиз

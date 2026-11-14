# FlorisBoard fork: Russian suggestions — рабочие заметки

Форк `florisboard/florisboard` (ветка `feature/russian-suggestions`, от тега v0.5.2).
Цель: вернуть подсказки слов в FlorisBoard 0.5.x, где NLP-движок ещё не реализован
(встроенный `LatinLanguageProvider.suggest()` возвращал пустой список).

## Что сделано

1. **`LatinLanguageProvider.kt`** переписан: словари загружаются по языку подтипа из
   assets (`ime/dict/<lang>.json`, англ. — `data.json`), подсказки — префиксный
   поиск + сортировка по частоте, spell — «слово в словаре → valid, иначе typo
   с ближайшими по префиксу вариантами». Словари кэшируются в памяти (TreeMap).
   Дополнительно:
   - Auto-commit кандидата срабатывает только если набираемое слово НЕ входит
     в словарь (исправление опечаток). Валидное целое слово при пробеле/
     пунктуации не заменяется (баг «сын,» → «сына,»). Если префиксных
     продолжений нет, для опечатки запускается fuzzy-поиск (Левенштейн ≤1,
     ≤2 для слов от 5 букв, та же первая буква и близкая длина) — «сым␣»
     исправляется в «сын␣». Fuzzy-поиск используется и в spell().
   - Предсказание следующего слова: `NgramStats` (биграммы «предыдущее слово →
     следующее → счётчик», файл `filesDir/ngrams_<lang>.json`). При пустом
     composing и введённом разделителе (пробел/пунктуация) `suggest()` возвращает
     предсказанные слова. Обучение: на принятых кандидатах (+1) и откатах (−1),
     а также на обычном наборе текста («…A B␣» → пара A→B). Принятое предсказание
     (`isNextWordPrediction`) коммитится с trailing-пробелом (`KeyboardManager.
     commitCandidate`, phantom-space предварительно гасится). Кандидаты
     префиксного поиска дополнительно бустятся биграммами.
   - Выгрузка/восстановление модели: при экспорте настроек (чекбокс
     jetpref datastore) `ngrams_*.json` копируются в архив бэкапа
     (`files/`), при restore — возвращаются в filesDir
     (BackupScreen/RestoreScreen). SQLite-хранилище сознательно
     отложено: JSON достаточен для биграмм; мигрировать при переходе
     на триграммы/time-decay.
   - Чистые алгоритмы вынесены в `LatinSuggestionAlgorithms` (без Android-зависимостей),
     юнит-тесты `LatinSuggestionAlgorithmsTest` (JUnit5), CI гоняет
     `testDebugUnitTest` и падает при красных тестах. Fuzzy-кандидат
     авто-коммитится только при подтверждении (биграмма с предыдущим
     словом или единственный вариант), иначе — только показ в панели.
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
   QuickActionArrangement.Default. **Pseudo-composing буфер** (raw-редакторы типа Termux
   отдают пустой `BaseInputConnection`, окружающего текста нет): `AbstractEditorInstance`
   накапливает введённые буквы/цифры в `forceComposingBuffer` (пробел/enter/прочие
   символы сбрасывают, backspace убирает последний и дублирует DEL в редактор),
   `NlpManager.suggest()` подставляет буфер как composing-префикс, приёми кандидата
   (`commitCompletion`) коммитит только суффикс слова (префикс уже отправлен в терминал).
   `imeUiMode` сбрасывается в `TEXT` при `handleStartInputView`, чтобы медиа-панель
   не «прилипала» между активациями клавиатуры.
7. CI: `.github/workflows/russian-suggestions.yml` — `assembleDebug` при push в
   ветку; артефакт `app-debug.apk`. Сборка debug, т.к. release требует
   подписи (секреты), а debug устанавливается рядом с официальным приложением
   (пакет `dev.patrickgold.florisboard.debug`).

## Известные проблемы и решения

- **Биты KeyboardState заняты регионами**: `KeyboardState.rawValue` — битовое поле, где
  биты 24–26 — это регион `ImeUiMode`, бит 27 — RTL. Первый вариант флага
  `F_IS_FORCE_SUGGESTIONS` стоял на бите 24 и при включении тоггла переключал всю
  клавиатуру на панель эмодзи. Свободный бит форка — 28 (`0x10000000`). Новые флаги
  добавлять только на свободные биты!
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

## Релизный процесс

Порядок обязателен, сломанные или непроверенные сборки в релизах не публикуются:

1. Правки в коде → commit → push в `feature/russian-suggestions`.
2. CI (`russian-suggestions.yml`) собирает `app-debug.apk` (~7–10 мин). Дождаться зелёного
   статуса workflow — красная сборка не берётся.
3. Скачать артефакт только после явного согласия пользователя, установка — `termux-open`
   (системный установщик, подтверждение пользователем).
4. Проверка на устройстве: подсказки в Termux с включённым force (набор → кандидаты →
   приём кандидата), обычный ввод в rich-поле, отсутствие «прилипшей» медиа-панели.
5. Только после проверки: аннотированный тег `v<versionName>-r<N>` (пример: `v0.5.3-r1`)
   на коммите прошедшей проверку сборки + GitHub Release через `gh release create` с:
   - заголовком `FlorisBoard fork <versionName> r<N>`;
   - changelog: разделы «Исправлено» и «Доработано» (кратко, по-русски, ссылками на
     поведение, а не на файлы);
   - приложенным проверенным `app-debug.apk`.
6. versionCode поднимается на единицу при каждой публикации (gradle.properties).

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

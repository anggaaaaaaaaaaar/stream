# AGENTS.md

Working notes for coding agents. Read `ARCHITECTURE.md` for how the system fits together; this file is about
how to change it without breaking things.

## What this is

CloudStream: an extension-based Android media player (Kotlin). Sources come from runtime-loaded plugins
(`.cs3` files). This repo is a fork of `recloudstream/cloudstream`, released as CineVault (`applicationId`
`com.cinevault.app`; Kotlin packages are still `com.lagradost.*`). Updates and releases come from this repo, not upstream.
To sync: `git fetch https://github.com/recloudstream/cloudstream master` and merge into `master`.

Modules: `library` (public plugin API, KMP), `app` (Android app), `shared` (Compose Multiplatform UI + MVI view models),
`desktopApp` (Compose Desktop stub), `docs` (Dokka aggregate).

## Commands

Requires JDK 17 and the Android SDK. Builds download a lot; the first run is slow.

```sh
./gradlew library:checkKotlinAbi            # plugin ABI check, CI gate
./gradlew assemblePrereleaseDebug           # debug APK, same variant CI builds
./gradlew assemblePrereleaseDebug lint check   # full CI step
./gradlew :library:jvmTest                  # library unit tests
./gradlew :app:testPrereleaseDebugUnitTest  # app unit tests
./gradlew :desktopApp:run                   # desktop preview
./gradlew library:updateKotlinAbi           # only if an ABI change is intended, see below
```

There is no formatter or ktlint task. Match the surrounding code (`kotlin.code.style=official`, 4-space indent).

## Rules that are easy to break

1. **Plugin ABI.** Anything public in `library` is linked by external plugins. CI fails on any ABI change.
   - New public declarations in `library` get `@Prerelease`.
   - Do not rename, remove, or change signatures of public `library` symbols. Add a new one and `@Deprecated` the old one.
   - Do not run `updateKotlinAbi` to make CI green unless the change is deliberate and explained in the PR.
   - App-side APIs that plugins call (`Plugin`, `VideoClickAction`, `DataStore` helpers) are under the same constraint
     in practice, because plugins also compile against `app` via `makeJar`.
2. **KMP only in `library` and `shared` common code.** No `java.*`, Android, or JVM-only libraries in `commonMain`.
   Put JVM-only code in `jvmCommonMain` (library) or `androidMain`/`jvmMain`.
3. **Provider calls go through `APIRepository`** (`app/.../ui/APIRepository.kt`), never `MainAPI` directly from UI.
   It adds timeouts, error wrapping into `Resource<T>`, and caching.
4. **Extension code is untrusted and slow.** Wrap calls with `safeApiCall` / `safe` / `safeAsync` and `logError`.
   Never let a plugin exception crash the app.
5. **Per-profile storage.** Data that belongs to a user profile must be keyed with `DataStoreHelper.currentAccount`
   (look at existing helpers in `DataStoreHelper.kt` before adding keys). Global settings use the default shared prefs.
   Stored classes are Jackson/kotlinx serialized; renaming a field loses user data. Keep `@JsonProperty` names stable.
6. **Phone and TV.** Most screens run on both. Check `isLayout(TV)` / `isLayout(PHONE)` paths and D-pad focus
   (`nextFocus*` in XML) when changing layouts.
7. **Strings.** Add new strings to `app/src/main/res/values/strings.xml` only. Other locales come from Weblate;
   do not edit `values-*/strings.xml` by hand. Compose-side strings live in `shared/src/commonMain/composeResources/values/strings.xml`.
8. **Plugin manager internals.** Functions named `___DO_NOT_CALL_FROM_A_PLUGIN_*` are app-only. Keep the prefix on new
   entry points with the same intent.

## Compose / CloudStream 4 work

Follow `COMPOSE.md` and `shared/readme.md`. Short version:

- MVI: one immutable state class (`val`, `PersistentList`), one sealed action type, built on
  `shared/.../cloudstream4/state/Viewmodel.kt`.
- Platform behaviour is injected through interfaces (pattern: `ExtensionsViewModel2` + `AndroidRepositoryManager`).
- `shared` gets components, theme and view models. Full screens stay in the platform module.
- Icons: XML drawables. No `ImageVector`, no SVG.
- Keep every feature of the XML screen. Refresh the look, don't redesign. No blur, glass or gradients.
- Wire a new Compose screen in by adding a `XxxFragment2 : BaseComposeFragment(), Screen by XxxScreen` and pointing
  `mobile_navigation.xml` at it. Leave the old fragment in place.
- The maintainer will not accept AI-generated groundwork for the Compose rewrite. Small, reviewed changes on top of
  existing structures only.

## Where to look

| Task | Start here |
|---|---|
| Provider API, response types | `library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt` |
| Add or fix a video host extractor | `library/.../cloudstream3/extractors/`, register in `extractorApis` in `utils/ExtractorApi.kt` |
| Plugin install/load/update | `app/.../plugins/PluginManager.kt`, `RepositoryManager.kt` |
| Result page (episodes, seasons, sync) | `app/.../ui/result/ResultViewModel2.kt` |
| Player | `app/.../ui/player/GeneratorPlayer.kt` (UI), `CS3IPlayer.kt` (ExoPlayer), `FullScreenPlayer.kt` (controls) |
| Downloads | `app/.../utils/downloader/`, `services/VideoDownloadService.kt` |
| Tracking (MAL, AniList, Simkl, Kitsu) | `app/.../syncproviders/providers/` |
| Settings | `app/.../ui/settings/Settings*Screen.kt` (Compose), `Settings*.kt` (old XML) |
| Preferences/state storage | `app/.../utils/DataStore.kt`, `DataStoreHelper.kt` |
| Navigation graph | `app/src/main/res/navigation/mobile_navigation.xml` |

`MainActivity.kt` (~2k lines), `ResultViewModel2.kt` (~2.7k), `GeneratorPlayer.kt` (~2.4k) and `CS3IPlayer.kt` (~2k)
are large. Search by symbol instead of reading them top to bottom.

## Pull requests

From `AI-POLICY.md` (upstream policy, applies to anything sent upstream):

- State AI usage in the PR.
- Test the change yourself before opening the PR. Say how you tested it.
- You must be able to explain every line.

Keep commits focused. CI must pass `library:checkKotlinAbi` and `assemblePrereleaseDebug lint check`.

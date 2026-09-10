# NeoForge 26.1 Modding Toolchain Reference (research snapshot 2026-09-06)

Implementation-grade reference for building a NeoForge mod targeting Minecraft 26.1 with ModDevGradle 2.x, Gradle 9.2.1 and JDK 25. Every version number is cited. Anything that could not be confirmed against 26.1 sources is marked **UNVERIFIED** with a best guess.

Legend: VERIFIED = fetched verbatim from the cited URL on 2026-09-06. UNVERIFIED = inferred; treat as a hypothesis to be checked at compile time.

---

## 0. Pinned versions (summary)

| Component | Pinned value | Source |
|---|---|---|
| Minecraft | `26.1` (patch releases `26.1.1`, `26.1.2` exist; MDK `minecraft_version=26.1`, range `[26.1]`) | https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/gradle.properties ; https://api.modrinth.com/v2/tag/game_version |
| NeoForge (latest stable for 26.1) | **`26.1.2.104`** (`<latest>`/`<release>` in maven-metadata; `lastUpdated` 2026-09-05 16:17:33 UTC) | https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml |
| NeoForge first non-beta for 26.1 | `26.1.2.71` (all `26.1.0.x`/`26.1.1.x`/`26.1.2.0-70` are `-beta`) | same |
| NeoForge (26.2, reference) | latest `26.2.0.76` (stable; betas ended at `26.2.0.56`) | same |
| NeoForge MDK template pin | `neo_version=26.1.0.19-beta` (template is stale; use `26.1.2.104`) | MDK gradle.properties (above) |
| ModDevGradle (MDG) | **`2.0.146`** (`<latest>`, lastUpdated 2026-08-31); MDK pins `2.0.141`; minimum for 26.1 is `2.0.131` ("Support for 26.1 / unobfuscated Minecraft") | https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml ; https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/2.0.146/moddev-gradle-2.0.146-changelog.txt |
| Gradle | **`9.2.1`** (MDK wrapper). Gradle 9.1.0+ required for JDK 25 (both for running Gradle and toolchains). Latest Gradle 9.x today: 9.7.1 | https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/gradle/wrapper/gradle-wrapper.properties ; https://services.gradle.org/versions/all ; https://raw.githubusercontent.com/gradle/gradle/master/platforms/documentation/docs/src/docs/userguide/releases/compatibility.adoc |
| Gradle 9.2.1 bin zip SHA-256 | `72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f` | https://services.gradle.org/versions/all (`checksum` field) / https://services.gradle.org/distributions/gradle-9.2.1-bin.zip.sha256 |
| Gradle 9.2.1 wrapper jar SHA-256 | `423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9` | https://services.gradle.org/distributions/gradle-9.2.1-wrapper.jar.sha256 |
| JDK | **25** (`java.toolchain.languageVersion = JavaLanguageVersion.of(25)`; "Mojang ships Java 25 to end users in 26.1") | https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/build.gradle |
| foojay resolver plugin | `org.gradle.toolchains.foojay-resolver-convention` `1.0.0` | MDK settings.gradle |
| Docker base image | `eclipse-temurin:25-jdk` (active, last_updated 2026-08-21; Linux tag resolves to Ubuntu 26.04 "resolute"; Temurin `25.0.4.1+1`) | https://hub.docker.com/v2/repositories/library/eclipse-temurin/tags/25-jdk |
| FancyModLoader (in NeoForge 26.1.x) | `11.0.15`; Mixin `0.17.3+mixin.0.8.7`; MixinExtras `0.5.4`; AccessTransformers `11.0.1` | https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/gradle.properties |
| NeoForge repo branch | `26.1.x` (client sources under `src/client/java/`, common under `src/main/java/`) | https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/docs/PORTING.md |
| Infiniverse (dynamic dimensions lib) | `26.1.0.1` (maven `net.commoble.infiniverse:infiniverse`, requires NeoForge `>= 26.1.0.17-beta`) | https://maven.commoble.net/net/commoble/infiniverse/infiniverse/ ; https://raw.githubusercontent.com/Commoble/infiniverse/main/gradle.properties |
| GitHub Actions | `actions/checkout@v7` (v7.0.1), `actions/setup-java@v5` (v5.2.0), `gradle/actions/setup-gradle@v6` (v6.3.0), `docker/setup-buildx-action@v4` (v4.2.0), `docker/build-push-action@v7` (v7.3.0), `actions/upload-artifact@v7` (v7.0.1), `softprops/action-gh-release@v3` (v3.0.1) | see section 3.4 |
| Publishing plugins | `me.modmuss50.mod-publish-plugin` `2.2.0`; `com.modrinth.minotaur` `2.9.0`; `net.darkhax.curseforgegradle` `1.3.33` | https://plugins.gradle.org/plugin/me.modmuss50.mod-publish-plugin ; https://plugins.gradle.org/plugin/com.modrinth.minotaur ; https://plugins.gradle.org/plugin/net.darkhax.curseforgegradle |

Version scheme (VERIFIED, https://docs.neoforged.net/docs/gettingstarted/versioning/ and https://neoforged.net/news/26.1release/): Minecraft 26.1+ uses `year.release.patch`; NeoForge is four-component `MC_year.MC_release.MC_patch.NEO_build` (e.g. `26.1.2.104` = 104th build for MC 26.1.2). The MDK pins `minecraft_version_range=[26.1]` and depends on `neoforge` with `versionRange="[${neo_version},)"`.

Non-obvious 26.1 facts that affect everything below (all VERIFIED unless noted):

- `ResourceLocation` was renamed **`Identifier`** in 1.21.11 (`net.minecraft.resources.Identifier`; factories `Identifier.fromNamespaceAndPath(ns, path)`, `Identifier.parse("ns:path")`, `Identifier.withDefaultNamespace(path)`; `ResourceKey#identifier()` replaces `location()`). https://github.com/neoforged/.github/blob/main/primers/1.21.11/index.md , https://docs.neoforged.net/docs/misc/identifier/
- Minecraft 26.1 ships **unobfuscated** with official parameter names; Parchment is unnecessary. https://neoforged.net/news/26.1release/
- `Level#random` is now `protected` (use `level.getRandom()`); `Level#isClientSide` field is private since 1.21.9 (use `isClientSide()`). 26.1 primer: https://github.com/neoforged/.github/blob/main/primers/26.1/index.md
- `GuiGraphics` was renamed **`GuiGraphicsExtractor`**; `Screen#render` -> `extractRenderState`, `renderBackground` -> `extractBackground`, `AbstractContainerScreen#renderBg` -> `Screen#extractBackground`, `renderLabels` -> `extractLabels`. (26.1 primer, "Gui Extractor")
- `DimensionSpecialEffects` and `RegisterDimensionSpecialEffectsEvent` **no longer exist** (removed in 1.21.11). Replacement: environment attributes + NeoForge `RegisterCustomEnvironmentEffectRendererEvent`. (section 4.6)
- `VillagerTradesEvent` / `WandererTradesEvent` **no longer exist** in NeoForge 26.1.x; trades are datapack JSON. (section 4.12)
- `ItemStack`s cannot be created before registries are bound; data files use **`ItemStackTemplate`** (`{id, count, components}`). (section 4.2)
- `AttachmentType.Builder#serialize` takes a **`MapCodec`**, not a `Codec`. (section 4.10)
- `SavedDataType` now takes an `Identifier`; `DimensionDataStorage` -> `SavedDataStorage`; `MinecraftServer#getDataStorage()` exists for global data. (section 4.10)
- `ServerLevel` constructor has **10 parameters** (no `ChunkProgressListener`, no `RandomSequences`); `LevelStem` gained `OptionalLong seedOverride` (`"neoforge:seed_override"`). (section 4.3)
- Tag folders use the **singular** registry path: `data/<ns>/tags/item/`, `tags/block/`, `tags/entity_type/`, `tags/worldgen/biome/`. https://docs.neoforged.net/docs/resources/server/tags

---

## 1. NeoForge versions for MC 26.1 / 26.2

Source (VERIFIED): https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml

```xml
<latest>26.1.2.104</latest>
<release>26.1.2.104</release>
...
<version>26.1.2.70-beta</version>
<version>26.1.2.71</version>      <!-- first non-beta for 26.1 -->
...
<version>26.2.0.56-beta</version>
<version>26.2.0.57</version>      <!-- first non-beta for 26.2 -->
...
<version>26.2.0.76</version>
<version>26.1.2.103</version>
<version>26.1.2.104</version>
<lastUpdated>20260905161733</lastUpdated>
```

- Latest stable NeoForge for MC 26.1: **`26.1.2.104`** (non-beta). It targets MC 26.1.2 (NeoForge `26.1.x` branch `gradle.properties`: `minecraft_version=26.1.2`, `neoform_version=1`, NeoForm coordinate `net.neoforged:neoform:26.1.2-1`). https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/gradle.properties
- Latest for MC 26.2 (reference only): **`26.2.0.76`** (non-beta; 26.2 released 2026-06-16 per Modrinth tag list). 26.3 is at `26.3-pre-2` (2026-09-04).
- The 26.1 MDK template still pins `neo_version=26.1.0.19-beta`; override to `26.1.2.104`.
- NeoForge version is also used as the `neoforge` dependency floor: `versionRange="[26.1.2.104,)"` if you use APIs added late in the cycle (e.g. `LevelStem#seedOverride`, added in `26.1.0.17-beta`; PR https://github.com/neoforged/NeoForge/pull/2977).

Project listing page https://projects.neoforged.net/neoforged/neoforge is JS-rendered (returned no data via fetch); the maven-metadata above is authoritative.

---

## 2. ModDevGradle, Gradle, JDK and the official 26.1 MDK

### 2.1 Versions

- MDG latest: `2.0.146` (VERIFIED maven-metadata, lastUpdated 2026-08-31). Changelog tail (VERIFIED https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/2.0.146/moddev-gradle-2.0.146-changelog.txt):
  ```
  - `2.0.146` Update dependency net.neoforged:neoform-runtime to v2.0.27 (main) (#352)
  - `2.0.144` Update generated Minecraft versions list to v26.2 (main) (#324)
  - `2.0.141` Downgrade "Failed to parse..." message to info level (#331)
  - `2.0.140` Version parsing: support local NeoForge alphas ... (#325)
  - `2.0.136` Disable recompilation in CI by default (#320)
  - `2.0.131` Support for 26.1 / unobfuscated Minecraft (#298)
  ```
  GitHub Releases page for MDG is empty ("There aren't any releases here") — versions are published only to maven.neoforged.net and the Gradle plugin portal. https://github.com/neoforged/ModDevGradle/releases
- MDG requires Gradle >= 8.8 (README) and works on Gradle 9 (fix in 2.0.94). NeoForge 26.1 release post: "Gradle 9.1.0 or newer is required". https://raw.githubusercontent.com/neoforged/ModDevGradle/main/README.md ; https://neoforged.net/news/26.1release/
- Gradle/JDK compatibility (VERIFIED compatibility.adoc): `| 25 | 9.1.0 | 9.1.0 and after` — Gradle 9.1.0 is the first version that can run on JDK 25 and use a JDK 25 toolchain. Gradle 9.2.1 (buildTime 2025-11-17) is `final: true`.
- MDG 2.0.136+: "this pipeline [no decompile/recompile] will be used by default in CI/CD pipelines, if the `CI` environment variable is `true`" — set `ENV CI=true` in Docker to skip decompilation.
- MDG 2.0.111+: "Disallow usage of additionalRuntimeClasspath when targeting Minecraft 1.21.9+" — do not use `additionalRuntimeClasspath` for 26.1; use the `localRuntime` configuration from the MDK.

### 2.2 Official MDK: `NeoForgeMDKs/MDK-26.1-ModDevGradle`

Repo (VERIFIED): https://github.com/NeoForgeMDKs/MDK-26.1-ModDevGradle (template generated by https://github.com/neoforged/mod-generator; 2 commits). Files below are reproduced verbatim from `main`.

#### build.gradle (https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/build.gradle)

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.moddev' version '2.0.141'
    id 'idea'
}

tasks.named('wrapper', Wrapper).configure {
    // Define wrapper values here so as to not have to always do so when updating gradlew.properties.
    // Switching this to Wrapper.DistributionType.ALL will download the full gradle sources that comes with
    // documentation attached on cursor hover of gradle classes and methods. However, this comes with increased
    // file size for Gradle. If you do switch this to ALL, run the Gradle wrapper task twice afterwards.
    // (Verify by checking gradle/wrapper/gradle-wrapper.properties to see if distributionUrl now points to `-all`)
    distributionType = Wrapper.DistributionType.BIN
}

version = mod_version
group = mod_group_id

sourceSets.main.resources {
    // Include resources generated by data generators.
    srcDir('src/generated/resources')

    // Exclude common development only resources from finalized outputs
    exclude("**/*.bbmodel") // BlockBench project files
    exclude("src/generated/**/.cache") // datagen cache files
}

repositories {
    // Add here additional repositories if required by some of the dependencies below.
}

base {
    archivesName = mod_id
}

// Mojang ships Java 25 to end users in 26.1, so mods should target Java 25.
java.toolchain.languageVersion = JavaLanguageVersion.of(25)

neoForge {
    // Specify the version of NeoForge to use.
    version = project.neo_version

    // This line is optional. Access Transformers are automatically detected
    // accessTransformers = project.files('src/main/resources/META-INF/accesstransformer.cfg')

    // Default run configurations.
    // These can be tweaked, removed, or duplicated as needed.
    runs {
        client {
            client()

            // Comma-separated list of namespaces to load gametests from. Empty = all namespaces.
            systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
        }

        server {
            server()
            programArgument '--nogui'
            systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
        }

        // This run config launches GameTestServer and runs all registered gametests, then exits.
        // By default, the server will crash when no gametests are provided.
        // The gametest system is also enabled by default for other run configs under the /test command.
        gameTestServer {
            type = "gameTestServer"
            systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
        }

        data {
            clientData()

            // example of overriding the workingDirectory set in configureEach above, uncomment if you want to use it
            // gameDirectory = project.file('run-data')

            // Specify the modid for data generation, where to output the resulting resource, and where to look for existing resources.
            programArguments.addAll '--mod', project.mod_id, '--all', '--output', file('src/generated/resources/').getAbsolutePath(), '--existing', file('src/main/resources/').getAbsolutePath()
        }

        // applies to all the run configs above
        configureEach {
            // Recommended logging data for a userdev environment
            // The markers can be added/remove as needed separated by commas.
            // "SCAN": For mods scan.
            // "REGISTRIES": For firing of registry events.
            // "REGISTRYDUMP": For getting the contents of all registries.
            systemProperty 'forge.logging.markers', 'REGISTRIES'

            // Recommended logging level for the console
            // You can set various levels here.
            // Please read: https://stackoverflow.com/questions/2031163/when-to-use-the-different-log-levels
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        // define mod <-> source bindings
        // these are used to tell the game which sources are for which mod
        // multi mod projects should define one per mod
        "${mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

// Sets up a dependency configuration called 'localRuntime'.
// This configuration should be used instead of 'runtimeOnly' to declare
// a dependency that will be present for runtime testing but that is
// "optional", meaning it will not be pulled by dependents of this mod.
configurations {
    runtimeClasspath.extendsFrom localRuntime
}

dependencies {
    // Example optional mod dependency with JEI
    // The JEI API is declared for compile time use, while the full JEI artifact is used at runtime
    // compileOnly "mezz.jei:jei-${mc_version}-common-api:${jei_version}"
    // compileOnly "mezz.jei:jei-${mc_version}-neoforge-api:${jei_version}"
    // We add the full version to localRuntime, not runtimeOnly, so that we do not publish a dependency on it
    // localRuntime "mezz.jei:jei-${mc_version}-neoforge:${jei_version}"

    // Example mod dependency using a mod jar from ./libs with a flat dir repository
    // This maps to ./libs/coolmod-${mc_version}-${coolmod_version}.jar
    // The group id is ignored when searching -- in this case, it is "blank"
    // implementation "blank:coolmod-${mc_version}:${coolmod_version}"

    // Example mod dependency using a file as dependency
    // implementation files("libs/coolmod-${mc_version}-${coolmod_version}.jar")

    // Example project dependency using a sister or child project:
    // implementation project(":myproject")

    // For more info:
    // http://www.gradle.org/docs/current/userguide/artifact_dependencies_tutorial.html
    // http://www.gradle.org/docs/current/userguide/dependency_management.html
}

// This block of code expands all declared replace properties in the specified resource targets.
// A missing property will result in an error. Properties are expanded using ${} Groovy notation.
var generateModMetadata = tasks.register("generateModMetadata", ProcessResources) {
    var replaceProperties = [
            minecraft_version      : minecraft_version,
            minecraft_version_range: minecraft_version_range,
            neo_version            : neo_version,
            mod_id                 : mod_id,
            mod_name               : mod_name,
            mod_license            : mod_license,
            mod_version            : mod_version,
    ]
    inputs.properties replaceProperties
    expand replaceProperties
    from "src/main/templates"
    into "build/generated/sources/modMetadata"
}
// Include the output of "generateModMetadata" as an input directory for the build
// this works with both building through Gradle and the IDE.
sourceSets.main.resources.srcDir generateModMetadata
// To avoid having to run "generateModMetadata" manually, make it run on every project reload
neoForge.ideSyncTask generateModMetadata

// Example configuration to allow publishing using the maven-publish plugin
publishing {
    publications {
        register('mavenJava', MavenPublication) {
            from components.java
        }
    }
    repositories {
        maven {
            url "file://${project.projectDir}/repo"
        }
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8' // Use the UTF-8 charset for Java compilation
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module {
        downloadSources = true
        downloadJavadoc = true
    }
}
```

Recommended overrides for a new project: `id 'net.neoforged.moddev' version '2.0.146'`, `neo_version=26.1.2.104`.

#### settings.gradle (https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/settings.gradle)

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}
```

#### gradle.properties (https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/gradle.properties)

```properties
# Sets default memory used for gradle commands. Can be overridden by user or command line properties.
org.gradle.jvmargs=-Xmx1G
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

# Environment Properties
# You can find the latest versions here: https://projects.neoforged.net/neoforged/neoforge
# The Minecraft version must agree with the Neo version to get a valid artifact
minecraft_version=26.1
# The Minecraft version range can use any release version of Minecraft as bounds.
# Snapshots, pre-releases, and release candidates are not guaranteed to sort properly
# as they do not follow standard versioning conventions.
minecraft_version_range=[26.1]
# The Neo version must agree with the Minecraft version to get a valid artifact
neo_version=26.1.0.19-beta

## Mod Properties

# The unique mod identifier for the mod. Must be lowercase in English locale. Must fit the regex [a-z][a-z0-9_]{1,63}
# Must match the String constant located in the main mod class annotated with @Mod.
mod_id=examplemod
# The human-readable display name for the mod.
mod_name=Example Mod
# The license of the mod. Review your options at https://choosealicense.com/. All Rights Reserved is the default.
mod_license=All Rights Reserved
# The mod version. See https://semver.org/
mod_version=1.0.0
# The group ID for the mod. It is only important when publishing as an artifact to a Maven repository.
# This should match the base package used for the mod sources.
# See https://maven.apache.org/guides/mini/guide-naming-conventions.html
mod_group_id=com.example.examplemod
```

#### gradle/wrapper/gradle-wrapper.properties (https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/gradle/wrapper/gradle-wrapper.properties)

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.2.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

Optionally add `distributionSha256Sum=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f`.

Wrapper jar for Gradle 9.2.1 (VERIFIED: returns HTTP 200, `application/octet-stream`; byte size UNVERIFIED):
`https://raw.githubusercontent.com/gradle/gradle/v9.2.1/gradle/wrapper/gradle-wrapper.jar`
(SHA-256 `423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9`, https://services.gradle.org/distributions/gradle-9.2.1-wrapper.jar.sha256). Also copy `gradlew` / `gradlew.bat` from the MDK: https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/gradlew , https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/gradlew.bat

#### src/main/templates/META-INF/neoforge.mods.toml (https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/src/main/templates/META-INF/neoforge.mods.toml)

Note: the MDK keeps this under `src/main/templates/` (expanded by `generateModMetadata`), not `src/main/resources/`.

```toml
# This is an example neoforge.mods.toml file. It contains the data relating to the loading mods.
# There are several mandatory fields (#mandatory), and many more that are optional (#optional).
# The overall format is standard TOML format, v0.5.0.
# Note that there are a couple of TOML lists in this file.
# Find more information on toml format here:  https://github.com/toml-lang/toml

# The license for you mod. This is mandatory metadata and allows for easier comprehension of your redistributive properties.
# Review your options at https://choosealicense.com/. All rights reserved is the default copyright stance, and is thus the default here.
license="${mod_license}"

# A URL to refer people to when problems occur with this mod
#issueTrackerURL="https://change.me.to.your.issue.tracker.example.invalid/" #optional

# A list of mods - how many allowed here is determined by the individual mod loader
[[mods]] #mandatory

# The modid of the mod
modId="${mod_id}" #mandatory

# The version number of the mod
version="${mod_version}" #mandatory

# A display name for the mod
displayName="${mod_name}" #mandatory

# A URL to query for updates for this mod. See the JSON update specification https://docs.neoforged.net/docs/misc/updatechecker/
#updateJSONURL="https://change.me.example.invalid/updates.json" #optional

# A URL for the "homepage" for this mod, displayed in the mod UI
#displayURL="https://change.me.to.your.mods.homepage.example.invalid/" #optional

# A file name (in the root of the mod JAR) containing a logo for display
#logoFile="examplemod.png" #optional

# A text field displayed in the mod UI
#credits="" #optional

# The authors of the mod, displayed in the mod UI (optional)
#authors=""

# The description text for the mod (multi line!) (#mandatory)
description='''
Example mod description.
'''

# The [[mixins]] block allows you to declare your mixin config to FML so that it gets loaded.
#[[mixins]]
#config="${mod_id}.mixins.json"

# The [[accessTransformers]] block allows you to declare where your AT file is.
# If this block is omitted, a fallback attempt will be made to load an AT from META-INF/accesstransformer.cfg
#[[accessTransformers]]
#file="META-INF/accesstransformer.cfg"

# The coremods config file path is not configurable and is always loaded from META-INF/coremods.json

# A dependency - use the . to indicate dependency for a specific modid. Dependencies are optional.
[[dependencies.${mod_id}]] #optional
    # the modid of the dependency
    modId="neoforge" #mandatory
    # The type of the dependency. Can be one of "required", "optional", "incompatible" or "discouraged" (case insensitive).
    # 'required' requires the mod to exist, 'optional' does not
    # 'incompatible' will prevent the game from loading when the mod exists, and 'discouraged' will show a warning
    type="required" #mandatory
    # Optional field describing why the dependency is required or why it is incompatible
    # reason="..."
    # The version range of the dependency
    versionRange="[${neo_version},)" #mandatory
    # An ordering relationship for the dependency.
    # BEFORE - This mod is loaded BEFORE the dependency
    # AFTER - This mod is loaded AFTER the dependency
    ordering="NONE"
    # Side this dependency is applied on - BOTH, CLIENT, or SERVER
    side="BOTH"

# Here's another dependency
[[dependencies.${mod_id}]]
    modId="minecraft"
    type="required"
    # This version range declares a minimum of the current minecraft version up to but not including the next major version
    versionRange="${minecraft_version_range}"
    ordering="NONE"
    side="BOTH"

# Features are specific properties of the game environment, that you may want to declare you require. This example declares
# that your mod requires GL version 3.2 or higher. Other features will be added. They are side aware so declaring this won't
# stop your mod loading on the server for example.
#[features.${mod_id}]
#openGLVersion="[3.2,)"
```

#### src/main/java/com/example/examplemod/ExampleMod.java (https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/src/main/java/com/example/examplemod/ExampleMod.java)

```java
package com.example.examplemod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ExampleMod.MODID)
public class ExampleMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "examplemod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", p -> p.mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "examplemod:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", p -> p.food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "examplemod:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.examplemod")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
```

#### src/main/java/com/example/examplemod/ExampleModClient.java (https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/src/main/java/com/example/examplemod/ExampleModClient.java)

```java
package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ExampleMod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class ExampleModClient {
    public ExampleModClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        ExampleMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        ExampleMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
```

Note: in 26.1 `@EventBusSubscriber` has no `bus = ...` parameter; mod-bus vs game-bus is inferred from the event type (`IModBusEvent`).

#### src/main/java/com/example/examplemod/Config.java (https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/src/main/java/com/example/examplemod/Config.java)

```java
package com.example.examplemod;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemName));
    }
}
```

#### .github/workflows/build.yml (https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/main/.github/workflows/build.yml)

```yaml
name: Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
          fetch-tags: true

      - name: Setup JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build with Gradle
        run: ./gradlew build
```

(The MDK pins action majors that are 1-3 majors behind current; see section 3.4.)

### 2.3 MDG DSL reference (VERIFIED from https://raw.githubusercontent.com/neoforged/ModDevGradle/main/README.md and RunModel.java)

- Run types: `client`, `server`, `data` (legacy, <= 1.21.3), `clientData`, `serverData`, `gameTestServer`. Each run `<name>` creates a `run<Name>` task (MDK: `runClient`, `runServer`, `runGameTestServer`, `runData`).
- Run properties: `type`, `gameDirectory`, `programArguments`/`programArgument`, `jvmArguments`/`jvmArgument`, `systemProperties`/`systemProperty`, `environment`, `logLevel`, `ideName`, `disableIdeRun()`, `sourceSet`, `loadedMods`, `taskBefore`, `mainClass`, `loggingConfigFile`, `devLogin`. Run names must match `[a-zA-Z][\w-]*`.
- `mods { name { sourceSet(sourceSets.main) } }`; `neoForge.ideSyncTask <task>`.
- Access transformers: default `src/main/resources/META-INF/accesstransformer.cfg` needs no config; extra: `neoForge { accessTransformers.from 'path/to/other.cfg' }`; `validateAccessTransformers = true`; publish: `accessTransformers { publish file("...") }`; consume: `dependencies { accessTransformers "group:artifact:version" }`.
- Interface injection: `neoForge { interfaceInjectionData.from "interfaces.json" }` (dev-time only; runtime needs a Mixin/coremod).
- Jar-in-jar: `dependencies { jarJar(implementation("org.commonmark:commonmark")) { version { strictly '[0.1, 1.0)'; prefer '0.21.0' } } }`, `jarJar files(...)`, `jarJar project(":sub")`. "As of Minecraft 1.21.9, external dependencies do not need special handling anymore to be loaded in runs."
- Optional runtime deps: MDK's `localRuntime` configuration.
- Disable recompile explicitly: `neoForge { enable { version = "..."; disableRecompilation = true } }` (automatic when `CI=true` since 2.0.136).
- `neoFormRuntime { version = ...; enableCache = ...; verbose = ...; useEclipseCompiler = ...; analyzeCacheMisses = ...; launcherManifestUrl = ... }` is top-level in MDG 2.
- Unit tests: see section 5.2.

---

## 3. Docker and CI

### 3.1 Base image (VERIFIED)

`eclipse-temurin:25-jdk` exists and is active (`last_updated` 2026-08-21T23:18:33Z; multi-arch amd64/arm64/ppc64le/s390x; Linux resolves to Ubuntu 26.04 "resolute", identical digest to `25-jdk-resolute`; Temurin build `jdk-25.0.4.1+1` per https://api.adoptium.net/v3/assets/latest/25/hotspot). Variants: `25-jdk-noble` (Ubuntu 24.04), `25-jdk-jammy`, `25-jdk-alpine` (= `-alpine-3.24`), `25-jdk-ubi10-minimal` (no `ubi9` for 25), Windows tags. Source: https://hub.docker.com/v2/repositories/library/eclipse-temurin/tags?name=25-jdk&page_size=50

Alternatives: `amazoncorretto:25` (active), `bellsoft/liberica-openjdk-debian:25` (active). The official `gradle` image has **no** `9.2.1-jdk25` tag (only `9.7.1-jdk25`, `jdk25`, `9-jdk25`) — https://hub.docker.com/v2/repositories/library/gradle/tags?name=jdk25&page_size=50 — so use Temurin plus the wrapper.

### 3.2 Dockerfile (BuildKit cache mount pattern)

```dockerfile
# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS build
# CI=true makes MDG >= 2.0.136 skip decompile/recompile (faster, less RAM)
ENV CI=true \
    GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.console=plain"
WORKDIR /src

# 1) Wrapper + build scripts first so the dependency/NeoForm layer is cached.
#    The --mount flag must be the first token after RUN (BuildKit syntax).
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
RUN --mount=type=cache,target=/root/.gradle,id=gradle-home \
    chmod +x gradlew && ./gradlew --no-daemon help

# 2) Sources
COPY src src
RUN --mount=type=cache,target=/root/.gradle,id=gradle-home \
    ./gradlew --no-daemon build --stacktrace

# 3) Export stage: only the jar(s)
FROM scratch AS export
COPY --from=build /src/build/libs/ /
```

Notes: the cache mount persists `~/.gradle` (wrapper distribution, dependency cache, and MDG's NeoForm/NeoForge artifact cache) across builds on the same builder; with `docker/build-push-action` + `cache-to: type=gha`, the mount contents are part of the exported layer cache only when `mode=max` is used and the `RUN` layer is cached — for reliably warm CI caches, add `id=gradle-home` (as above) and keep the two-stage COPY order so the `help` layer rarely invalidates.

Extract locally: `docker buildx build --target export --output type=local,dest=out .` (VERIFIED syntax from https://docs.docker.com/build/exporters/local-tar/: `docker buildx build --output type=local[,parameters] .`; single-platform builds export directly into `dest`).

GHA cache backend (VERIFIED https://docs.docker.com/build/cache/backends/gha/): `--cache-to type=gha[,mode=max,scope=<name>] --cache-from type=gha[,scope=<name>]`; "not supported with the default `docker` driver" — use `docker/setup-buildx-action`. `ghtoken` is auto-set by `docker/build-push-action`.

### 3.3 GitHub Actions workflow (build via Dockerfile, artifact upload, release on tags)

```yaml
name: Build & Release

on:
  push:
    branches: [main]
    tags: ['v*']
  pull_request:

permissions:
  contents: write   # needed for release upload on tags
  packages: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0
          fetch-tags: true

      - uses: docker/setup-buildx-action@v4

      - name: Build mod jar inside Docker (BuildKit + GHA cache)
        uses: docker/build-push-action@v7
        with:
          context: .
          target: export
          outputs: type=local,dest=out
          cache-from: type=gha,scope=modbuild
          cache-to: type=gha,mode=max,scope=modbuild

      - name: List artifacts
        run: ls -la out

      - uses: actions/upload-artifact@v7
        with:
          name: mod-jars
          path: out/*.jar
          if-no-files-found: error

  release:
    if: startsWith(github.ref, 'refs/tags/v')
    needs: build
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@v7
      - uses: actions/download-artifact@v7   # UNVERIFIED major; pair with upload-artifact major
        with:
          name: mod-jars
          path: out
      - name: Publish GitHub Release
        uses: softprops/action-gh-release@v3
        with:
          files: out/*.jar
          generate_release_notes: true
          body_path: CHANGELOG.md   # optional
```

Alternative using the preinstalled CLI: `gh release create "$GITHUB_REF_NAME" out/*.jar --generate-notes` with `GH_TOKEN: ${{ github.token }}` (VERIFIED synopsis at https://cli.github.com/manual/gh_release_create).

A non-Docker equivalent (faster on hosted runners) is the MDK workflow with updated majors: `actions/setup-java@v5` (`java-version: '25'`, `distribution: 'temurin'`) + `gradle/actions/setup-gradle@v6` + `./gradlew build`.

### 3.4 Action versions (VERIFIED 2026-09-06)

| Action | Major | Latest tag | Source |
|---|---|---|---|
| actions/checkout | v7 | v7.0.1 (2026-07-20) | https://github.com/actions/checkout/releases/tag/v7.0.1 |
| actions/setup-java | v5 | v5.2.0 | https://api.github.com/repos/actions/setup-java/tags |
| gradle/actions/setup-gradle | v6 | v6.3.0 (2026-08-02) | https://github.com/gradle/actions/releases/tag/v6.3.0 |
| docker/setup-buildx-action | v4 | v4.2.0 | https://github.com/marketplace/actions/docker-setup-buildx |
| docker/build-push-action | v7 | v7.3.0 (2026-07-01) | https://github.com/docker/build-push-action/releases/tag/v7.3.0 |
| actions/upload-artifact | v7 | v7.0.1 | https://api.github.com/repos/actions/upload-artifact/tags |
| softprops/action-gh-release | v3 | v3.0.1 | https://api.github.com/repos/softprops/action-gh-release/tags |

`actions/download-artifact` major: UNVERIFIED (assume same major as upload-artifact, v7; check https://github.com/actions/download-artifact/releases).

---

## 4. API reference notes for NeoForge 26.1

Sources: NeoForged docs (default version labelled "26.1", https://docs.neoforged.net/docs/...), NeoForge `26.1.x` sources, the 26.1 primer (https://github.com/neoforged/.github/blob/main/primers/26.1/index.md), and the NeoForge 26.1.2.76 javadoc mirror (https://lexxie.dev/neoforge/26.1/).

### 4.1 Registration: DeferredRegister & friends

VERIFIED signatures from https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/src/main/java/net/neoforged/neoforge/registries/DeferredRegister.java :

```java
public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace)
public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String namespace)
public static <B> DeferredRegister<B> create(Identifier registryName, String modid)
public static DeferredRegister.Items createItems(String modid)
public static DeferredRegister.Blocks createBlocks(String modid)
public static DataComponents createDataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String modid)
public static Entities createEntities(String modid)

public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> sup)
public <I extends T> DeferredHolder<T, I> register(String name, Function<Identifier, ? extends I> func)
public void register(IEventBus bus)
public TagKey<T> createTagKey(String path)

// DeferredRegister.Blocks
public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> func, UnaryOperator<BlockBehaviour.Properties> properties)
public DeferredBlock<Block> registerSimpleBlock(String name, UnaryOperator<BlockBehaviour.Properties> properties)
public DeferredBlock<Block> registerSimpleBlock(String name)
// DeferredRegister.Items
public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func, UnaryOperator<Item.Properties> properties)
public DeferredItem<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties)
public DeferredItem<Item> registerSimpleItem(String name)
public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block)
public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block)
// DeferredRegister.DataComponents
public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name, UnaryOperator<DataComponentType.Builder<D>> builder)
// DeferredRegister.Entities
public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name, EntityType.EntityFactory<E> factory, MobCategory category)
public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name, EntityType.EntityFactory<E> factory, MobCategory category, UnaryOperator<EntityType.Builder<E>> builder)
```

`setId` requirement (since 1.21.2/1.21.3, still true): "`setId` - Sets the resource key of the item. This **must** be set on every item; otherwise, an exception will be thrown." (https://docs.neoforged.net/docs/items/). The helpers apply it internally (VERIFIED source): `func.apply(properties.get().setId(ResourceKey.create(Registries.ITEM, key)))` and `...setId(ResourceKey.create(Registries.BLOCK, key))`. Manual form:

```java
public static final DeferredItem<Item> RAW = ITEMS.register("raw",
    registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));
```

Other registries (docs, VERIFIED unless flagged):

```java
// Block entity type: constructor, not Builder (https://docs.neoforged.net/docs/blockentities/)
public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
public static final Supplier<BlockEntityType<MyBlockEntity>> MY_BE = BLOCK_ENTITY_TYPES.register("my_block_entity",
    () -> new BlockEntityType<>(MyBlockEntity::new, /* onlyOpCanSetNbt */ false, MyBlocks.MY_BLOCK.get()));

// Menu type (https://docs.neoforged.net/docs/inventories/menus)
public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
public static final Supplier<MenuType<MyMenu>> MY_MENU = MENUS.register("my_menu", () -> IMenuTypeExtension.create(MyMenu::new)); // IContainerFactory: (int windowId, Inventory inv, RegistryFriendlyByteBuf data)

// Entity type (https://docs.neoforged.net/docs/entities/)
public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(MODID);
public static final Supplier<EntityType<MyEntity>> MY_ENTITY = ENTITY_TYPES.register("my_entity",
    () -> EntityType.Builder.of(MyEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).eyeHeight(0.5f).clientTrackingRange(8).updateInterval(10)
        .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "my_entity"))));

// Creative tab (https://docs.neoforged.net/docs/items/#custom-creative-tabs) -- see ExampleMod above.

// Data component type (https://docs.neoforged.net/docs/items/datacomponents)
public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
public static final Supplier<DataComponentType<MyRecord>> MY_COMPONENT = COMPONENTS.registerComponentType("my_component",
    b -> b.persistent(MyRecord.CODEC).networkSynchronized(MyRecord.STREAM_CODEC));

// Sound event (https://docs.neoforged.net/docs/resources/client/sounds)
public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MODID);
public static final Holder<SoundEvent> MY_SOUND = SOUNDS.register("my_sound", SoundEvent::createVariableRangeEvent);
public static final Holder<SoundEvent> MY_FIXED = SOUNDS.register("my_fixed", id -> SoundEvent.createFixedRangeEvent(id, 16f));

// Attachment type (https://docs.neoforged.net/docs/datastorage/attachments)
public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

// Recipe serializer / type (26.1: RecipeSerializer is a record of MapCodec + StreamCodec; 26.1 primer "Serializer Records and Recipe Info")
public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, MODID);
public static final Supplier<RecipeSerializer<MyRecipe>> MY_SERIALIZER = RECIPE_SERIALIZERS.register("my_recipe",
    () -> new RecipeSerializer<>(MyRecipe.MAP_CODEC, MyRecipe.STREAM_CODEC));
public static final Supplier<RecipeType<MyRecipe>> MY_TYPE = RECIPE_TYPES.register("my_recipe", () -> RecipeType.simple(Identifier.fromNamespaceAndPath(MODID, "my_recipe"))); // RecipeType.simple: UNVERIFIED for 26.1 (1.21.x form)

// Fluid type / fluids (section 4.11)
public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, MODID);
public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MODID);

// Chunk generator / biome source codecs (UNVERIFIED for 26.1 -- pattern unchanged since 1.19)
public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);
public static final Supplier<MapCodec<? extends ChunkGenerator>> MY_GEN = CHUNK_GENERATORS.register("age", () -> MyChunkGenerator.CODEC);
public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, MODID);

// Structure type (UNVERIFIED for 26.1; StructureType<S> is a functional interface returning MapCodec<S>)
public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MODID);
public static final Supplier<StructureType<MyStructure>> MY_STRUCTURE = STRUCTURE_TYPES.register("my_structure", () -> () -> MyStructure.CODEC);

// Villager profession / POI (section 4.12)
public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MODID);
public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, MODID);
```

Datapack registries (dimension types, level stems, biomes, structures, template pools, test instances): register via datagen `GatherDataEvent.Client#createDatapackRegistryObjects(new RegistrySetBuilder().add(Registries.DIMENSION_TYPE, bootstrap -> bootstrap.register(KEY, new DimensionType(...))), Set.of(MODID))` — https://docs.neoforged.net/docs/concepts/registries#data-generation-for-datapack-registries . `DatapackBuiltinEntriesProvider(PackOutput, CompletableFuture<HolderLookup.Provider>, RegistrySetBuilder, Set<String> modIds)` (VERIFIED source).

### 4.2 Data components, ItemStackTemplate, ItemInstance

Docs: https://docs.neoforged.net/docs/items/datacomponents (VERIFIED)

```java
public record ExampleRecord(int value1, boolean value2) {
    public static final Codec<ExampleRecord> BASIC_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("value1").forGetter(ExampleRecord::value1),
        Codec.BOOL.fieldOf("value2").forGetter(ExampleRecord::value2)).apply(i, ExampleRecord::new));
    public static final StreamCodec<ByteBuf, ExampleRecord> BASIC_STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, ExampleRecord::value1,
        ByteBufCodecs.BOOL, ExampleRecord::value2,
        ExampleRecord::new);
}
public static final DeferredRegister.DataComponents REGISTRAR = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "examplemod");
public static final Supplier<DataComponentType<ExampleRecord>> BASIC_EXAMPLE = REGISTRAR.registerComponentType(
    "basic", builder -> builder.persistent(BASIC_CODEC).networkSynchronized(BASIC_STREAM_CODEC));
```

"Either `persistent` or `networkSynchronized` must be provided in the builder; otherwise, a `NullPointerException` will be thrown." Builder also has `cacheEncoding()` and `ignoreSwapAnimation()` (1.21.11). Access: `stack.get(TYPE)`, `stack.set(TYPE, v)`, `stack.has`, `stack.remove`, `stack.update(TYPE, DEFAULT, UnaryOperator)`. Default components: `new Item.Properties().setId(...).component(TYPE, value)`, `.delayedComponent(DataComponents.DAMAGE_RESISTANT, ctx -> new DamageResistant(ctx.getOrThrow(DamageTypeTags.IS_EXPLOSION)))`, `.delayedHolderComponent(DataComponents.DAMAGE_TYPE, DamageTypes.SPEAR)`. `ModifyDefaultComponentsEvent` (mod bus) edits vanilla defaults.

26.1 changes (primer, VERIFIED):
- "Data components have begun their transition from being moved off the raw object and onto the `Holder` itself... Currently, this is only implemented for `Item`s." Read with `holder.components()`; `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.add(key, (components, context, key) -> components.set(...))` for custom registry objects. Components are (re)bound on datapack reload.
- **`ItemStackTemplate`** (immutable; `record`-like with `Holder<Item>`, `int count`, `DataComponentPatch`): `new ItemStackTemplate(Items.APPLE.builtInRegistryHolder(), 5, DataComponentPatch.builder().set(DataComponents.ITEM_NAME, Component.literal("Apple?")).build())`, `template.create()`, `ItemStackTemplate.fromNonEmptyStack(stack)`, `CODEC`, `MAP_CODEC`, `STREAM_CODEC`. Both `ItemStack` and `ItemStackTemplate` implement **`ItemInstance`** (`typeHolder()`, `is(Item)`, `count()`, `get(DataComponentType)`, `getMaxStackSize()`). Templates cannot be empty: `@Nullable ItemStackTemplate` is the empty analog. NeoForge added `FluidStackTemplate` too.
- "instantiating new ItemStacks now require the registries to be loaded" (https://neoforged.net/news/26.1release/) — do not build `ItemStack`s in static initializers; recipes, advancements, loot results, creative-tab icons (use `Supplier`) and villager trades use templates. JSON shape: `{"id": "minecraft:dirt", "count": 4, "components": {...}}`.
- Removed from `ItemStack`: `SINGLE_ITEM_CODEC`, `STRICT_CODEC`, `STRING_SINGLE_ITEM_CODEC`, `SIMPLE_ITEM_CODEC`, `parse`, `save`. `ItemStack#getItemHolder` -> `typeHolder()`. `Recipe#assemble` no longer takes `HolderLookup.Provider`.
- `DataComponents.DYE` (holds `DyeColor`) replaces `DyeItem` color; `EitherHolder` removed.

### 4.3 Dimensions: datapack JSON, Java, and runtime (dynamic) dimensions

#### 4.3.1 Static dimension via datapack

- `data/<ns>/dimension_type/<name>.json` — `DimensionType` record in 26.1 (VERIFIED javadoc): `DimensionType(boolean hasFixedTime, boolean hasSkyLight, boolean hasCeiling, boolean hasEnderDragonFight, double coordinateScale, int minY, int height, int logicalHeight, TagKey<Block> infiniburn, float ambientLight, DimensionType.MonsterSettings monsterSettings, DimensionType.Skybox skybox, CardinalLighting.Type cardinalLightType, EnvironmentAttributeMap attributes, HolderSet<Timeline> timelines, Optional<Holder<WorldClock>> defaultClock)`; `Skybox` = `NONE | OVERWORLD | END`. Old fields `ultrawarm`, `natural`, `bed_works`, `respawn_anchor_works`, `piglin_safe`, `has_raids`, `fixed_time` (now boolean `has_fixed_time`), `effects`, `cloud_height` are replaced by `"attributes": {...}` entries (e.g. `"minecraft:visual/cloud_height": 90`, `"minecraft:gameplay/water_evaporates": true`). Exact attribute id strings other than `visual/cloud_height` are UNVERIFIED — dump vanilla `minecraft:overworld` dimension type from the client jar (`data/minecraft/dimension_type/overworld.json`) as the template.
- `data/<ns>/dimension/<name>.json` — `LevelStem`: `{"type": "<ns>:<dimension_type>", "generator": {...}, "neoforge:seed_override": 12345}` (NeoForge-added optional field, VERIFIED https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/patches/net/minecraft/world/level/dimension/LevelStem.java.patch).
- Java (datagen): `RegistrySetBuilder().add(Registries.DIMENSION_TYPE, bs -> bs.register(MY_TYPE_KEY, new DimensionType(...))).add(Registries.LEVEL_STEM, bs -> bs.register(MY_STEM_KEY, new LevelStem(bs.lookup(Registries.DIMENSION_TYPE).getOrThrow(MY_TYPE_KEY), new MyChunkGenerator(...), OptionalLong.empty())))`. `Level` key: `ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(MODID, "age"))`.

#### 4.3.2 NeoForge API status

NeoForge 26.1 has **no** first-party dynamic-dimension API (no `RegisterDimensionEvent`, no `DimensionManager`). The only dimension-related addition in the 26.1 cycle is PR #2977 (`LevelStem#seedOverride`, `ServerLevel#getSeedOverride()`, released in `26.1.0.17-beta`). `MinecraftServer#forgeGetWorldMap()` and `#markWorldsDirty()` **still exist** (both `@Deprecated //Forge Internal use Only`, `public synchronized`) — VERIFIED https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/patches/net/minecraft/server/MinecraftServer.java.patch :

```java
+    @Deprecated //Forge Internal use Only, You can screw up a lot of things if you mess with this map.
+    public synchronized Map<ResourceKey<Level>, ServerLevel> forgeGetWorldMap() {
+        return this.levels;
+    }
+    private int worldArrayMarker = 0;
+    private int worldArrayLast = -1;
+    private ServerLevel[] worldArray;
+    @Deprecated //Forge Internal use Only, use to protect against concurrent modifications in the world tick loop.
+    public synchronized void markWorldsDirty() {
+        worldArrayMarker++;
+    }
+    private ServerLevel[] getWorldArray() {
+        if (worldArrayMarker == worldArrayLast && worldArray != null)
+            return worldArray;
+        worldArray = this.levels.values().stream().toArray(x -> new ServerLevel[x]);
+        worldArrayLast = worldArrayMarker;
+        return worldArray;
+    }
```

The tick loop iterates `getWorldArray()`, so `markWorldsDirty()` **must** be called after mutating the map or the new level never ticks. `MappedRegistry#unfreeze(boolean clearTags)` is public (`@Deprecated`) in NeoForge — VERIFIED https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/patches/net/minecraft/core/MappedRegistry.java.patch — so no Mixin is needed to unfreeze `LEVEL_STEM`.

Key vanilla/NeoForge types in 26.1 (VERIFIED from patch context and javadoc):

```java
// 10 parameters (ChunkProgressListener and RandomSequences are gone)
public ServerLevel(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess levelStorage,
                   ServerLevelData levelData, ResourceKey<Level> dimension, LevelStem levelStem,
                   boolean isDebug, long biomeZoomSeed, List<CustomSpawner> customSpawners, boolean tickTime)
// vanilla createLevels call site:
new ServerLevel(this, this.executor, this.storageSource, derivedLevelData, dimension, entry.getValue(), isDebug, biomeZoomSeed, ImmutableList.of(), false)

public record LevelStem(Holder<DimensionType> type, ChunkGenerator generator, java.util.OptionalLong seedOverride) { public LevelStem(Holder<DimensionType> type, ChunkGenerator generator) {...} }
public record WorldDimensions(Map<ResourceKey<LevelStem>, LevelStem> dimensions)   // NeoForge uses LenientUnboundedMapCodec (skips unparseable entries)
new DerivedLevelData(WorldData worldData, ServerLevelData overworldData)             // derivedLevelData = new DerivedLevelData(worldData, worldData.overworldData())
MinecraftServer: private final Map<ResourceKey<Level>, ServerLevel> levels; private final Executor executor; protected final LevelStorageSource.LevelStorageAccess storageSource; private final LayeredRegistryAccess<RegistryLayer> registries; LevelLoadListener levelLoadListener (replaces ChunkProgressListenerFactory)
MinecraftServer public: overworld(), getLevel(ResourceKey<Level>), levelKeys(), getAllLevels(), getWorldGenSettings(), getWorldData(), registryAccess(), getDataStorage(), getAbsoluteMaxWorldSize(), getRespawnData(), getPlayerList().addWorldborderListener(ServerLevel)
WorldGenSettings (26.1: a SavedData-style object, stored in <world>/data/minecraft/world_gen_settings.dat): options() -> WorldOptions (seed()), non-public final field `WorldDimensions dimensions`, setDirty()
public record ClientboundLoginPacket(int playerId, boolean hardcore, Set<ResourceKey<Level>> levels, int maxPlayers, int chunkRadius, int simulationDistance, boolean reducedDebugInfo, boolean showDeathScreen, boolean doLimitedCrafting, CommonPlayerSpawnInfo commonPlayerSpawnInfo, boolean enforcesSecureChat)
CommonPlayerSpawnInfo(Holder<DimensionType> dimensionType, ResourceKey<Level> dimension, long seed, GameType gameType, @Nullable GameType previousGameType, boolean isDebug, boolean isFlat, Optional<GlobalPos> lastDeathLocation, int portalCooldown, int seaLevel)
net.neoforged.neoforge.event.level.LevelEvent.Load / .Unload / .Save (game bus)
```

Persistence in 26.1 (VERIFIED via Mojira MC-307144 https://mojira.dev/MC-307144 and Infiniverse 26.1 source): `level.dat` was split; `WorldGenSettings` now lives in `<world>/data/minecraft/world_gen_settings.dat`. On the next start, vanilla merges the saved `WorldDimensions` with datapack dimensions into the DIMENSIONS registry layer and `createLevels` recreates a `ServerLevel` for each `LEVEL_STEM` entry — so a dimension written into `WorldGenSettings.dimensions` **comes back without mod code**, and `LevelEvent.Load` fires for it during `createLevels` (before `ServerStartedEvent`). Caveat (26.1-26.1.2): >2 MiB NBT in that file throws `NbtAccounterException` (fixed in 26.2 snapshot 7) — keep generator settings compact (reference `"settings": "minecraft:overworld"` / registry biomes rather than inlining).

#### 4.3.3 Infiniverse (Commoble) — the canonical approach, ported to 26.1

- Repo https://github.com/Commoble/infiniverse, branch `main` = MC 26.1 (`mod_version = 26.1.0.1`, `neo_version = 26.1.0.17-beta`, `java_version = 25`). Maven: `https://maven.commoble.net/` artifacts `net.commoble.infiniverse:infiniverse:26.1.0.0` (2026-03-30) and `26.1.0.1` (2026-06-19); older `21.9.0`, `21.11.0`, `21.11.1`, `2.1.0.0` (1.21.3), `2.0.1.0` (1.21). License MIT. Changelog 26.1.0.0: "Dimensions can again be created with per-dimension seeds ... by specifying the seed override in the LevelStem when using InfiniverseAPI#getOrCreateLevel". 26.1.0.1: "Infiniverse no longer automatically saves levels before unloading them."
- API (VERIFIED https://raw.githubusercontent.com/Commoble/infiniverse/main/src/main/java/net/commoble/infiniverse/api/InfiniverseAPI.java):

```java
public interface InfiniverseAPI {
    public static InfiniverseAPI get() { return DimensionManager.INSTANCE; }
    public abstract ServerLevel getOrCreateLevel(final MinecraftServer server, final ResourceKey<Level> levelKey, final Supplier<LevelStem> dimensionFactory);
    public abstract void markDimensionForUnregistration(final MinecraftServer server, final ResourceKey<Level> levelToRemove);
    public abstract Set<ResourceKey<Level>> getLevelsPendingUnregistration();
}
```

- Gradle: `repositories { maven { url "https://maven.commoble.net/" } }`, `dependencies { implementation "net.commoble.infiniverse:infiniverse:26.1.0.1" }` (or `jarJar(implementation(...))` to bundle; declare `[[dependencies]]` with `modId="infiniverse"`, `versionRange="[26.1.0.1,)"` if not bundled).
- Its only AT line: `public-f net.minecraft.world.level.levelgen.WorldGenSettings dimensions` (https://raw.githubusercontent.com/Commoble/infiniverse/main/src/main/resources/META-INF/accesstransformer.cfg). Private fields it reads reflectively (`ObfuscationReflectionHelper.findField`): `MinecraftServer.executor`, `MinecraftServer.storageSource`, `MinecraftServer.registries`, `LayeredRegistryAccess.values`, `LayeredRegistryAccess.composite`, `RegistryAccess$ImmutableRegistryAccess.registries`.

#### 4.3.4 Concrete 26.1 code sketch (Infiniverse's DimensionManager, creation path, VERIFIED verbatim excerpt)

Source: https://raw.githubusercontent.com/Commoble/infiniverse/main/src/main/java/net/commoble/infiniverse/internal/DimensionManager.java

```java
private static final RegistrationInfo DIMENSION_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.stable());

public ServerLevel getOrCreateLevel(final MinecraftServer server, final ResourceKey<Level> levelKey, final Supplier<LevelStem> dimensionFactory) {
    @SuppressWarnings("deprecation")
    Map<ResourceKey<Level>, ServerLevel> map = server.forgeGetWorldMap();
    @Nullable ServerLevel existingLevel = map.get(levelKey);
    return existingLevel == null ? createAndRegisterLevel(server, map, levelKey, dimensionFactory) : existingLevel;
}

@SuppressWarnings("deprecation")
private static ServerLevel createAndRegisterLevel(final MinecraftServer server, final Map<ResourceKey<Level>, ServerLevel> map, final ResourceKey<Level> levelKey, Supplier<LevelStem> dimensionFactory) {
    // dimension keys have a 1:1 relationship with level keys, they have the same IDs as well
    final ResourceKey<LevelStem> dimensionKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.identifier());
    final LevelStem dimension = dimensionFactory.get();

    final Executor executor = ReflectionBuddy.MinecraftServerAccess.executor.apply(server);          // or AT: server.executor
    final LevelStorageAccess anvilConverter = ReflectionBuddy.MinecraftServerAccess.storageSource.apply(server); // or AT: server.storageSource
    final WorldData worldData = server.getWorldData();
    final DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, worldData.overworldData());
    WorldGenSettings worldGenSettings = server.getWorldGenSettings();
    long serverSeed = worldGenSettings.options().seed();

    // register the actual dimension
    Registry<LevelStem> dimensionRegistry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
    if (dimensionRegistry instanceof MappedRegistry<LevelStem> writableRegistry) {
        writableRegistry.unfreeze(false);
        writableRegistry.register(dimensionKey, dimension, DIMENSION_REGISTRATION_INFO);
    } else {
        throw new IllegalStateException(String.format("Unable to register dimension %s -- dimension registry not writable", dimensionKey.identifier()));
    }

    // sneak dimension into WorldGenSettings so the game can reconstitute it on server reboot
    WorldDimensions oldDimensions = worldGenSettings.dimensions;          // AT: public-f WorldGenSettings dimensions
    Map<ResourceKey<LevelStem>, LevelStem> dimensionMap = new HashMap<>(oldDimensions.dimensions());
    dimensionMap.put(dimensionKey, dimension);
    WorldDimensions newDimensions = new WorldDimensions(dimensionMap);
    worldGenSettings.dimensions = newDimensions;
    worldGenSettings.setDirty();

    // create the level instance
    final ServerLevel newLevel = new ServerLevel(
        server, executor, anvilConverter, derivedLevelData, levelKey, dimension,
        worldData.isDebugWorld(),
        BiomeManager.obfuscateSeed(dimension.seedOverride().orElse(serverSeed)),
        List.of(),   // "special spawn list" -- always empty for non-overworld dimensions
        false        // "tick time", true for overworld, always false for nether, end, and json dimensions
    );

    newLevel.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
    server.getPlayerList().addWorldborderListener(newLevel);

    // register level
    map.put(levelKey, newLevel);
    // update forge's world cache so the new level can be ticked
    server.markWorldsDirty();
    // fire world load event
    NeoForge.EVENT_BUS.post(new LevelEvent.Load(newLevel));
    // update clients' dimension lists
    QuietPacketDistributors.sendToAll(server, new UpdateDimensionsPacket(Set.of(levelKey), true));
    return newLevel;
}
```

Unregistration (same file): schedule keys, then on `ServerTickEvent.Post` remove from `WorldGenSettings.dimensions` (+`setDirty()`), post cancellable `UnregisterDimensionEvent`, `server.forgeGetWorldMap().remove(key)`, teleport players out via `player.teleportTo(destinationLevel, x, y, z, Set.of(), pitch, yaw, true)` (respawn data from `player.getRespawnConfig()` / `server.getRespawnData()`), `removedLevel.save(null, false, removedLevel.noSave())`, post `LevelEvent.Unload`, rebuild the `LEVEL_STEM` `MappedRegistry` (`new MappedRegistry<>(Registries.LEVEL_STEM, old.registryLifecycle())`) and swap it into the `RegistryLayer.DIMENSIONS` layer of `MinecraftServer.registries` (`LayeredRegistryAccess.values` + `ImmutableRegistryAccess.registries` via reflection/AT), then `markWorldsDirty()` and send the removal packet. Dynamic **`DimensionType`s are not supported** — the `Holder<DimensionType>` must already exist on the client (pre-define age dimension types in datapack JSON). `ChunkGenerator`s must have registered `MapCodec`s (they are written to `world_gen_settings.dat`).

Client sync packet (VERIFIED https://raw.githubusercontent.com/Commoble/infiniverse/main/src/main/java/net/commoble/infiniverse/internal/UpdateDimensionsPacket.java) — needed only for players already connected; players who log in later get the key set from `ClientboundLoginPacket.levels` (built from `server.levelKeys()`), and `ClientboundRespawnPacket` carries the destination key so teleports work regardless:

```java
public record UpdateDimensionsPacket(Set<ResourceKey<Level>> keys, boolean add) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateDimensionsPacket> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(InfiniverseMod.MODID, "update_dimensions"));
    public static final StreamCodec<ByteBuf, UpdateDimensionsPacket> STREAM_CODEC = StreamCodec.composite(
        ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs.list()).map(Set::copyOf, List::copyOf), UpdateDimensionsPacket::keys,
        ByteBufCodecs.BOOL, UpdateDimensionsPacket::add,
        UpdateDimensionsPacket::new);
    public void handle(IPayloadContext context) { context.enqueueWork(() -> ClientHandler.handle(this)); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static class ClientHandler {
        private static void handle(UpdateDimensionsPacket packet) {
            final LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            final Set<ResourceKey<Level>> dimensionList = Objects.requireNonNullElse(player.connection.levels(), Set.of());
            Consumer<ResourceKey<Level>> keyConsumer = packet.add() ? dimensionList::add : dimensionList::remove;
            packet.keys().forEach(keyConsumer);
        }
    }
}
// registration: event.registrar(MODID).optional().playToClient(UpdateDimensionsPacket.TYPE, UpdateDimensionsPacket.STREAM_CODEC, UpdateDimensionsPacket::handle);
// send only to players that have the channel: if (player.connection.hasChannel(packet)) PacketDistributor.sendToPlayer(player, packet);
```

Building a `LevelStem` with a per-age seed and copying a generator (from InfiniverseMod, VERIFIED): `new LevelStem(typeHolder, chunkGenerator, OptionalLong.of(ageSeed))`; deep copy via `ChunkGenerator.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, server.registryAccess()), gen).flatMap(nbt -> ChunkGenerator.CODEC.parse(ops, nbt)).getOrThrow()`.

Required AT lines if you reimplement instead of depending on Infiniverse (`src/main/resources/META-INF/accesstransformer.cfg`; names are Mojang official names = source names):

```
# creating levels (26.1)
public net.minecraft.server.MinecraftServer executor
public net.minecraft.server.MinecraftServer storageSource
public-f net.minecraft.world.level.levelgen.WorldGenSettings dimensions
# optional: direct map access instead of forgeGetWorldMap()
public net.minecraft.server.MinecraftServer levels
# only for unregistering (rebuilding the DIMENSIONS registry layer)
public net.minecraft.server.MinecraftServer registries
public-f net.minecraft.core.LayeredRegistryAccess values
public net.minecraft.core.LayeredRegistryAccess composite
public-f net.minecraft.core.RegistryAccess$ImmutableRegistryAccess registries
```

(`executor`/`storageSource`/`ImmutableRegistryAccess registries` lines VERIFIED verbatim in RFTools Dimensions' 1.21_neo AT https://raw.githubusercontent.com/McJtyMods/RFToolsDimensions/1.21_neo/src/main/resources/META-INF/accesstransformer.cfg ; `WorldGenSettings dimensions` VERIFIED in Infiniverse; the rest derived from Infiniverse's reflection field names.)

Mixin accessor equivalent (if you prefer Mixins): `@Mixin(MinecraftServer.class) interface MinecraftServerAccessor { @Accessor("levels") Map<ResourceKey<Level>, ServerLevel> getLevels(); @Accessor("executor") Executor getExecutor(); @Accessor("storageSource") LevelStorageSource.LevelStorageAccess getStorageSource(); }` and `@Mixin(WorldGenSettings.class) interface WorldGenSettingsAccessor { @Accessor("dimensions") WorldDimensions getDimensions(); @Accessor("dimensions") @Mutable void setDimensions(WorldDimensions d); }`. On NeoForge you still must call `markWorldsDirty()`.

Other mods surveyed: RFTools Dimensions (branch `1.21_neo`, no 26.1; swaps the composite registry map instead of `unfreeze`; removal path unfinished), TeamGalacticraft/DynamicDimensions (Mixin-based, branch `minecraft/1.21`, no 26.1; creates runtime `DimensionType`s and syncs them, does not persist the list), Tardis Refined (`dev/1.21.1`, Commoble pattern + own JSON list), Dimensional Doors (static pre-declared pocket dimensions, not dynamic). No maintained Mystcraft-style mod exists on 26.1 other than Infiniverse-based ones. Mod-owned persistence recommendation: keep age metadata in a server-global `SavedData` (`server.getDataStorage()`) and call `getOrCreateLevel` for each entry in `ServerStartedEvent` (no-op if vanilla already reconstituted it). Do not do this in `ServerAboutToStartEvent` (`server.overworld()` is null there).

### 4.4 Custom ChunkGenerator / BiomeSource

`net.minecraft.world.level.chunk.ChunkGenerator` public API in 26.1 (VERIFIED javadoc mirror https://lexxie.dev/neoforge/26.1/net/minecraft/world/level/chunk/ChunkGenerator.html):

```java
public abstract class ChunkGenerator {
    public static final Codec<ChunkGenerator> CODEC;   // dispatch codec over Registries.CHUNK_GENERATOR
    public ChunkGenerator(BiomeSource biomeSource)
    public ChunkGenerator(BiomeSource biomeSource, Function<Holder<Biome>, BiomeGenerationSettings> generationSettingsGetter)
    protected abstract MapCodec<? extends ChunkGenerator> codec();   // UNVERIFIED (protected members hidden in the -public javadoc; unchanged from 1.21.x)
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess protoChunk)   // no Executor param in 26.1
    public abstract void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk)   // no GenerationStep.Carving param in 26.1
    public abstract void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk)
    public abstract void spawnOriginalMobs(WorldGenRegion worldGenRegion)
    public abstract int getGenDepth()
    public abstract CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess centerChunk)
    public abstract int getSeaLevel()
    public abstract int getMinY()
    public abstract int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState)
    public abstract NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState)
    public abstract void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos)
    public Optional<Identifier> getTypeNameForDataFixer()
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager)
    public BiomeSource getBiomeSource()
    public int getSpawnHeight(LevelHeightAccessor heightAccessor)
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory mobCategory, BlockPos pos)
}
```

Skeleton:

```java
public class AgeChunkGenerator extends ChunkGenerator {
    public static final MapCodec<AgeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
        AgeSettings.CODEC.fieldOf("settings").forGetter(g -> g.settings)
    ).apply(i, AgeChunkGenerator::new));
    private final AgeSettings settings;
    public AgeChunkGenerator(BiomeSource biomeSource, AgeSettings settings) { super(biomeSource); this.settings = settings; }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState rs, StructureManager sm, ChunkAccess chunk) { /* fill blocks */ return CompletableFuture.completedFuture(chunk); }
    @Override public void buildSurface(WorldGenRegion level, StructureManager sm, RandomState rs, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState rs, BiomeManager bm, StructureManager sm, ChunkAccess chunk) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) { NaturalSpawner.spawnMobsForChunkGeneration(...); /* UNVERIFIED signature */ }
    @Override public int getGenDepth() { return 384; }
    @Override public int getSeaLevel() { return 63; }
    @Override public int getMinY() { return -64; }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor h, RandomState rs) { return 64; }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor h, RandomState rs) { return new NoiseColumn(getMinY(), new BlockState[0]); }
    @Override public void addDebugScreenInfo(List<String> out, RandomState rs, BlockPos pos) {}
}
```

Register the codec: `DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID).register("age", () -> AgeChunkGenerator.CODEC)` (UNVERIFIED for 26.1; `BuiltInRegistries.CHUNK_GENERATOR` holds `MapCodec<? extends ChunkGenerator>`). Simplest robust path for a Mystcraft-style mod: wrap vanilla `NoiseBasedChunkGenerator` with per-age `NoiseGeneratorSettings`/`BiomeSource` chosen from symbols, and only subclass for exotic ages.

`BiomeSource` (VERIFIED public API; protected abstract methods UNVERIFIED but expected unchanged): `public static final Codec<BiomeSource> CODEC`; `protected abstract MapCodec<? extends BiomeSource> codec()`; `protected abstract Stream<Holder<Biome>> collectPossibleBiomes()`; `public abstract Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler)`; helpers `possibleBiomes()`, `findBiomeHorizontal(...)`, `findClosestBiome3d(...)`, `addDebugInfo(List<String>, BlockPos, Climate.Sampler)`. Vanilla sources usable directly: `FixedBiomeSource(Holder<Biome>)`, `MultiNoiseBiomeSource`, `CheckerboardColumnBiomeSource`.

### 4.5 Menus and screens (26.1 GUI extraction API)

Menus (https://docs.neoforged.net/docs/inventories/menus, VERIFIED): subclass `AbstractContainerMenu`, implement `stillValid(Player)` and `quickMoveStack(Player, int)`; client ctor `(int containerId, Inventory playerInv, RegistryFriendlyByteBuf extraData)` used by `IContainerFactory`. Slots in 26.1 use the transfer API: `this.addSlot(new ResourceHandlerSlot(handler, handler::set, index, x, y))`, `this.addStandardInventorySlots(playerInventory, 8, 84)`. Data sync: `addDataSlot(DataSlot.standalone())`, `addDataSlots(new SimpleContainerData(3))`. Validity: `AbstractContainerMenu.stillValid(ContainerLevelAccess.create(level, pos), player, MY_BLOCK.get())`.

Open with extra data (VERIFIED `IPlayerExtension`, https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/src/main/java/net/neoforged/neoforge/common/extensions/IPlayerExtension.java):

```java
default OptionalInt openMenu(@Nullable MenuProvider menuProvider, BlockPos pos)
default OptionalInt openMenu(@Nullable MenuProvider menuProvider, @Nullable Consumer<RegistryFriendlyByteBuf> extraDataWriter)
// IMenuProviderExtension: default boolean shouldTriggerClientSideContainerClosingOnOpen(); default void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer)
serverPlayer.openMenu(new SimpleMenuProvider((id, inv, p) -> new MyMenu(id, inv, pos), Component.translatable("menu.title.examplemod.mymenu")), buf -> buf.writeBlockPos(pos));
```

Block hook signature in 26.1: `public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)` (no `InteractionHand`).

Screen registration: `@SubscribeEvent static void registerScreens(RegisterMenuScreensEvent event) { event.register(MY_MENU.get(), MyScreen::new); }` (`register(MenuType<? extends M>, MenuScreens.ScreenConstructor<M, U>)`, VERIFIED source).

Rendering in 26.1 (VERIFIED https://docs.neoforged.net/docs/rendering/screens and 26.1 primer): `GuiGraphics` -> **`GuiGraphicsExtractor`**; `draw*`/`render*` methods lose their prefixes and `*String*` -> `*Text*`: `hLine`->`horizontalLine`, `vLine`->`verticalLine`, `renderOutline`->`outline`, `drawString`->`text`, `drawCenteredString`->`centeredText`, `drawStringWithBackdrop`->`textWithBackdrop`, `renderItem`->`item`, `renderFakeItem`->`fakeItem`, `renderItemDecorations`->`itemDecorations`, `renderTooltip`->`tooltip`, `submitEntityRenderState`->`entity`, `submitMapRenderState`->`map`. Screen: `render`->`extractRenderState`, `renderBackground`->`extractBackground`, `renderTransparentBackground`->`extractTransparentBackground`, `renderPanorama`->`extractPanorama`; `AbstractContainerScreen`: `renderBg`->`Screen#extractBackground`, `renderLabels`->`extractLabels`, `renderTooltip`->`extractTooltip`, `renderSlot`->`extractSlot`; `imageWidth`/`imageHeight` are now final ctor parameters (default 176x166); do not override `render` (it calls `renderTooltip` at the end). `slotClicked`/`clicked` take `ContainerInput` (renamed from `ClickType`).

```java
public class MyContainerScreen extends AbstractContainerScreen<MyMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/container/my_container_screen.png");
    public MyContainerScreen(MyMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166);
        this.titleLabelX = 10; this.inventoryLabelX = 10;
    }
    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);
        graphics.item(new ItemStack(Items.APPLE), 100, 20);   // ItemStack ok here: registries are bound on the client in-game
    }
}
// plain Screen:
@Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) { this.extractTransparentBackground(g); }
@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) { super.extractRenderState(g, mx, my, pt); g.setTooltipForNextFrame(...); }
```

Other `GuiGraphicsExtractor` members (docs): `pose()` -> `Matrix3x2fStack` (`pushMatrix/translate/rotate/scale/popMatrix`), `enableScissor/disableScissor`, `nextStratum()`, `fill`, `fillGradient`, `textWithWordWrap`, `blitSprite(...)` (sprites under `textures/gui/sprites`, `.mcmeta` `gui.scaling` stretch/tile/nine_slice), `submitGuiElementRenderState(...)`, `submitPictureInPictureRenderState(...)`, `blurBeforeThisStratum`. Widgets: `addRenderableWidget(...)`; `AbstractWidget#renderWidget` -> `extractWidgetRenderState`.

### 4.6 Sky / fog / clouds: environment attributes + NeoForge custom renderers

`DimensionSpecialEffects` was **removed in 1.21.11** ("replaced entirely by `EnvironmentAttribute`s"); NeoForge 26.1.x has no `RegisterDimensionSpecialEffectsEvent` (VERIFIED absent from `src/client/java/net/neoforged/neoforge/client/event/`). Replacements:

1. Data-driven: dimension-type JSON `"attributes": {"minecraft:visual/cloud_height": 90, ...}`, `"skybox": "none|overworld|end"`, `"timelines"` tag, biome `"attributes": {"<id>": {"modifier": "add", "argument": 60}}`, timeline JSON `data/<ns>/timeline/<name>.json` (`clock`, `period_ticks`, `tracks`/`keyframes`/`ease`, `time_markers`). Vanilla attributes (`net.minecraft.world.attribute.EnvironmentAttributes`, VERIFIED list): `FOG_COLOR, FOG_START_DISTANCE, FOG_END_DISTANCE, SKY_FOG_END_DISTANCE, CLOUD_FOG_END_DISTANCE, WATER_FOG_COLOR, WATER_FOG_START_DISTANCE, WATER_FOG_END_DISTANCE, SKY_COLOR, SUNRISE_SUNSET_COLOR, CLOUD_COLOR, CLOUD_HEIGHT, SUN_ANGLE, MOON_ANGLE, STAR_ANGLE, MOON_PHASE, STAR_BRIGHTNESS, BLOCK_LIGHT_TINT, SKY_LIGHT_COLOR, SKY_LIGHT_FACTOR, NIGHT_VISION_COLOR, AMBIENT_LIGHT_COLOR, DEFAULT_DRIPSTONE_PARTICLE, AMBIENT_PARTICLES, BACKGROUND_MUSIC, MUSIC_VOLUME, AMBIENT_SOUNDS, FIREFLY_BUSH_SOUNDS, SKY_LIGHT_LEVEL, CAN_START_RAID, WATER_EVAPORATES, BED_RULE, RESPAWN_ANCHOR_WORKS, NETHER_PORTAL_SPAWNS_PIGLINS, FAST_LAVA, INCREASED_FIRE_BURNOUT, EYEBLOSSOM_OPEN, TURTLE_EGG_HATCH_CHANCE, PIGLINS_ZOMBIFY, SNOW_GOLEM_MELTS, CREAKING_ACTIVE, SURFACE_SLIME_SPAWN_CHANCE, CAT_WAKING_UP_GIFT_CHANCE, BEES_STAY_IN_HIVE, MONSTERS_BURN, CAN_PILLAGER_PATROL_SPAWN, VILLAGER_ACTIVITY, BABY_VILLAGER_ACTIVITY`. Custom attribute: `Registry.register(BuiltInRegistries.ENVIRONMENT_ATTRIBUTE, id, EnvironmentAttribute.builder(AttributeTypes.BOOLEAN).defaultValue(false).syncable().build())` (or `DeferredRegister.create(Registries.ENVIRONMENT_ATTRIBUTE, MODID)`). Read: `level.environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, pos)`; server-side layering: `ServerLevel#setEnvironmentAttributes` / `EnvironmentAttributeSystem.builder().addDefaultLayers(level).build()`.
2. Code-driven (NeoForge, VERIFIED sources): `net.neoforged.neoforge.common.world.NeoForgeEnvironmentAttributes` defines syncable `EnvironmentAttribute<Identifier>` `CUSTOM_SKYBOX` (`neoforge:custom_skybox`), `CUSTOM_CLOUDS`, `CUSTOM_WEATHER_EFFECTS` (default `minecraft:default`). Set in dimension-type JSON `"attributes": {"neoforge:custom_skybox": "examplemod:age_sky"}` and register on the client mod bus:

```java
@SubscribeEvent
static void registerSky(RegisterCustomEnvironmentEffectRendererEvent event) {
    event.registerSkyboxRenderer(Identifier.fromNamespaceAndPath(MOD_ID, "age_sky"), new AgeSkyRenderer());
    event.registerCloudRenderer(Identifier.fromNamespaceAndPath(MOD_ID, "age_clouds"), new AgeCloudRenderer());
    event.registerWeatherEffectRenderer(Identifier.fromNamespaceAndPath(MOD_ID, "age_weather"), new AgeWeatherRenderer());
}
public interface CustomSkyboxRenderer {  // net.neoforged.neoforge.client
    /** @return true to prevent vanilla sky rendering */
    default boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState, Matrix4fc modelViewMatrix, Runnable setupFog) { return false; }
}
public interface CustomCloudsRenderer {
    default boolean renderClouds(LevelRenderState levelRenderState, Vec3 camPos, CloudStatus cloudStatus, int cloudColor, float cloudHeight, int cloudRange, Matrix4fc modelViewMatrix) { return false; }
}
public interface CustomWeatherEffectRenderer {
    default boolean renderSnowAndRain(LevelRenderState levelRenderState, WeatherRenderState weatherRenderState, MultiBufferSource bufferSource, Vec3 camPos)
    default boolean tickRain(ClientLevel level, int ticks, Camera camera)
}
```

"Custom render state needed for the various render methods must be extracted via `ExtractLevelRenderStateEvent` and stored in the provided `LevelRenderState`." `RenderLevelStageEvent` is now a family of subclasses (`AfterSky`, `AfterOpaqueBlocks`, `AfterOpaqueFeatures`, `AfterTranslucentFeatures`, `AfterTranslucentBlocks`, `AfterTranslucentParticles`, `AfterWeather`, `AfterLevel`) with `getLevelRenderer()`, `getLevelRenderState()`, `getPoseStack()`, `getModelViewMatrix()`. Fog for fluids: `IClientFluidTypeExtensions#modifyFogRender(Camera, @Nullable FogEnvironment, float renderDistance, float partialTick, FogData)`. 26.1 rendering state: `FogRenderer#setupFog` returns `FogData` stored in `CameraRenderState#fogData`; `GameRenderer#render` split into `update`/`extract`/`render`; `LightTexture` -> `Lightmap`; `BlockRenderDispatcher`/`ItemRenderer` removed (per-quad `ChunkSectionLayer` from texture `Transparency`); `BlockColor` -> `BlockTintSource`. Dimension transition screens: `RegisterDimensionTransitionScreenEvent#registerIncomingEffect/registerOutgoingEffect/registerConditionalEffect`.

### 4.7 Networking

Docs https://docs.neoforged.net/docs/networking/payload and VERIFIED sources (`PayloadRegistrar`, `PacketDistributor`):

```java
public record MyData(String name, int age) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MyData> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mymod", "my_data"));
    public static final StreamCodec<ByteBuf, MyData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, MyData::name, ByteBufCodecs.VAR_INT, MyData::age, MyData::new);
    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
@SubscribeEvent // mod bus
public static void register(RegisterPayloadHandlersEvent event) {
    final PayloadRegistrar registrar = event.registrar("1");            // .versioned(String) / .optional() / .executesOn(HandlerThread.NETWORK)
    registrar.playToServer(MyData.TYPE, MyData.STREAM_CODEC, ServerPayloadHandler::handle);
    registrar.playToClient(MyData.TYPE, MyData.STREAM_CODEC);            // 26.1: client handler registered separately (below) -- or pass a handler here
    registrar.playBidirectional(MyData.TYPE, MyData.STREAM_CODEC, ServerPayloadHandler::handle /* , clientHandler */);
}
@SubscribeEvent // mod bus, physical client only (client source set)
public static void registerClient(RegisterClientPayloadHandlersEvent event) { event.register(MyData.TYPE, ClientPayloadHandler::handle); }
static void handle(final MyData data, final IPayloadContext context) { context.enqueueWork(() -> {...}); /* context.player(), context.disconnect(Component) */ }
// sending
PacketDistributor.sendToPlayer(serverPlayer, payload);            // (ServerPlayer, CustomPacketPayload, CustomPacketPayload...)
PacketDistributor.sendToPlayersInDimension(serverLevel, payload);
PacketDistributor.sendToPlayersNear(serverLevel, excludedOrNull, x, y, z, radius, payload);
PacketDistributor.sendToAllPlayers(payload);
PacketDistributor.sendToPlayersTrackingEntity(entity, payload); PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
PacketDistributor.sendToPlayersTrackingChunk(serverLevel, chunkPos, payload);
ClientPacketDistributor.sendToServer(payload);                    // net.neoforged.neoforge.client.network.ClientPacketDistributor
```

`PayloadRegistrar` methods: `playToClient/playToServer/playBidirectional`, `configuration*`, `common*` (`FriendlyByteBuf` codecs for configuration/common), `executesOn(HandlerThread)`, `versioned(String)`, `optional()`. Limits: client-bound <= 1 MiB, server-bound < 32 KiB.

### 4.8 Entities and renderers

Docs https://docs.neoforged.net/docs/entities/ (VERIFIED):

```java
public class MyEntity extends Entity {
    public static final EntityDataAccessor<Integer> MY_DATA = SynchedEntityData.defineId(MyEntity.class, EntityDataSerializers.INT);
    public MyEntity(EntityType<? extends MyEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(MY_DATA, 0); }
    @Override protected void readAdditionalSaveData(ValueInput input) { this.value = input.getIntOr("my_data", 0); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { output.putInt("my_data", this.value); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return true; }
}
// attributes (mod bus)
@SubscribeEvent static void attrs(EntityAttributeCreationEvent e) { e.put(MY_MOB.get(), LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 50).build()); }
// spawn placement (mod bus): register(EntityType<T>, @Nullable SpawnPlacementType, Heightmap.@Nullable Types, SpawnPlacements.SpawnPredicate<T>, Operation{AND,OR,REPLACE})
@SubscribeEvent static void placements(RegisterSpawnPlacementsEvent e) { e.register(MY_MOB.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE); }
// spawn data: implement IEntityWithComplexSpawn { writeSpawnData(RegistryFriendlyByteBuf); readSpawnData(RegistryFriendlyByteBuf) }
// renderer (client mod bus)
public class MyEntityRenderer extends EntityRenderer<MyEntity, MyEntityRenderState> {
    public MyEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public MyEntityRenderState createRenderState() { return new MyEntityRenderState(); }
    @Override public void extractRenderState(MyEntity entity, MyEntityRenderState state, float partialTick) { super.extractRenderState(entity, state, partialTick); state.foo = entity.getFoo(); }
    @Override public void submit(MyEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) { super.submit(state, poseStack, collector, camera); }
}
@SubscribeEvent static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(MY_ENTITY.get(), MyEntityRenderer::new); }
@SubscribeEvent static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) { e.registerLayerDefinition(MY_LAYER, MyModel::createBodyLayer); }   // VERIFIED: registerLayerDefinition(ModelLayerLocation, Supplier<LayerDefinition>) (docs' `event.add` is stale)
```

`LivingEntityRenderer<T, S extends LivingEntityRenderState, M extends EntityModel<S>>`: `super(context, new MyModel(context.bakeLayer(MY_LAYER)), 0.5f)`, `Identifier getTextureLocation(S state)`; `RenderLayer#submit(PoseStack, SubmitNodeCollector, int lightCoords, S state, float yRot, float xRot)`. 26.1: `EntityRendererProvider.Context#getBlockModelResolver()` (was `getBlockRenderDispatcher`), `getSprites()` (was `getMaterials`); `submitNameTag` -> `submitNameDisplay`; `Entity#interact(Player, InteractionHand, Vec3)` replaces `interactAt`; `Entity#getLightColor` -> `getLightCoords`.

### 4.9 Block entities (ValueInput / ValueOutput)

Docs https://docs.neoforged.net/docs/blockentities/ (VERIFIED) and `ValueInput`/`ValueOutput` method lists (VERIFIED javadoc):

```java
public class MyBlockEntity extends BlockEntity {
    public MyBlockEntity(BlockPos pos, BlockState state) { super(MY_BE.get(), pos, state); }
    @Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); this.value = input.getIntOr("value", 0); this.stack = input.read("stack", ItemStack.CODEC).orElse(ItemStack.EMPTY); }
    @Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); output.putInt("value", this.value); output.storeNullable("stack", ItemStack.CODEC, this.stack); }
    // sync
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return this.saveWithoutMetadata(registries); }
    @Override public void handleUpdateTag(ValueInput input) { super.handleUpdateTag(input); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ValueInput input) { super.onDataPacket(connection, input); }
    public static void tick(Level level, BlockPos pos, BlockState state, MyBlockEntity be) {}
}
public class MyEntityBlock extends Block implements EntityBlock {
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MyBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, MY_BE.get(), MyBlockEntity::tick); }
}
```

`ValueInput`: `<T> Optional<T> read(String, Codec<T>)`, `child(String)`/`childOrEmpty`, `childrenList(String)`/`childrenListOrEmpty`, `list(String, Codec<T>)`/`listOrEmpty`, `getBooleanOr`, `getByteOr`, `getShortOr`, `getInt`/`getIntOr`, `getLong`/`getLongOr`, `getFloatOr`, `getDoubleOr`, `getString`/`getStringOr`, `getIntArray`, `@Deprecated lookup()`. `ValueOutput`: `store(String, Codec<T>, T)`, `storeNullable(String, Codec<T>, @Nullable T)`, `putBoolean/putByte/putShort/putInt/putLong/putFloat/putDouble/putString/putIntArray`, `child(String)`, `childrenList(String)`, `list(String, Codec<T>)`, `discard(String)`, `isEmpty()`. Reserved keys: `id, x, y, z, NeoForgeData, neoforge:attachments`. Bridge to NBT: `TagValueOutput.createWithContext(ProblemReporter, HolderLookup.Provider)` / `TagValueInput.create(ProblemReporter, HolderLookup.Provider, CompoundTag)`. Removal hooks: `preRemoveSideEffects(BlockPos, BlockState)`, `affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean movedByPiston)`. Trigger sync: `level.sendBlockUpdated(pos, old, new, flags)`.

BER (interface + render state, since 1.21.9): `BlockEntityRenderer<T, S extends BlockEntityRenderState>` with `S createRenderState()`, `extractRenderState(T, S, float partialTick, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay)`, `submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`; register `event.registerBlockEntityRenderer(MY_BE.get(), MyBER::new)` in `EntityRenderersEvent.RegisterRenderers`. Block models in a BER: `ctx.blockModelResolver()` + `BlockDisplayContext.create()` (26.1 primer).

### 4.10 Data attachments and SavedData

Attachments (VERIFIED https://raw.githubusercontent.com/neoforged/NeoForge/26.1.x/src/main/java/net/neoforged/neoforge/attachment/AttachmentType.java):

```java
public static <T> Builder<T> builder(Supplier<T> defaultValueSupplier)
public static <T> Builder<T> builder(Function<IAttachmentHolder, T> defaultValueConstructor)
public static <T extends ValueIOSerializable> Builder<T> serializable(Supplier<T> defaultValueSupplier)
Builder<T> serialize(IAttachmentSerializer<T> serializer)          // T read(IAttachmentHolder, ValueInput); boolean write(T, ValueOutput)
Builder<T> serialize(MapCodec<T> codec)                            // MapCodec, not Codec: use Codec.INT.fieldOf("mana")
Builder<T> serialize(MapCodec<T> codec, Predicate<? super T> shouldSerialize)
Builder<T> copyOnDeath()
Builder<T> copyHandler(IAttachmentCopyHandler<T> cloner)
Builder<T> sync(AttachmentSyncHandler<T> syncHandler)
Builder<T> sync(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec)
Builder<T> sync(BiPredicate<IAttachmentHolder, ServerPlayer> sendToPlayer, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec)
AttachmentType<T> build()
// usage on Entity / BlockEntity / LevelChunk / Level (IAttachmentHolder): hasData, getData, getExistingData, setData (marks dirty), removeData, syncData(type)
public static final Supplier<AttachmentType<Integer>> MANA = ATTACHMENT_TYPES.register("mana", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("mana")).copyOnDeath().sync(ByteBufCodecs.INT).build());
```

SavedData (VERIFIED javadoc + docs https://docs.neoforged.net/docs/datastorage/saveddata):

```java
public record SavedDataType<T extends SavedData>(Identifier id, SavedDataType.Factory<T> factory, SavedDataType.Factory<Codec<T>> codecFactory, @Nullable DataFixTypes dataFixType)
public SavedDataType(Identifier id, Supplier<T> constructor, Codec<T> codec, @Nullable DataFixTypes dataFixType)
public SavedDataType(Identifier id, Supplier<T> constructor, Codec<T> codec)
public SavedDataType(Identifier id, SavedDataType.Factory<T> constructor, SavedDataType.Factory<Codec<T>> codec)   // NeoForge-added: Factory receives ServerLevel
public static final SavedDataType<AgeRegistryData> TYPE = new SavedDataType<>(
    Identifier.fromNamespaceAndPath(MODID, "ages/registry"),   // -> <world>/data/<modid>/ages/registry.dat (server-global) ; subdirectories allowed
    AgeRegistryData::new, AgeRegistryData.CODEC);
AgeRegistryData data = server.getDataStorage().computeIfAbsent(TYPE);   // MinecraftServer#getDataStorage() = global; ServerLevel#getDataStorage() = per-level (SavedDataStorage, formerly DimensionDataStorage)
data.setDirty();
```

Per the 26.1 primer, "any global saved data should be stored on the server instance rather than the overworld"; weather, game rules, world-gen settings, wandering trader and boss events are saved data now.

### 4.11 Teleportation and portals

VERIFIED (`Entity.java.patch`, `ServerPlayer.java.patch`, javadoc mirror):

```java
public @Nullable Entity teleport(TeleportTransition transition)          // Entity; returns null if EntityTravelToDimensionEvent is cancelled
public @Nullable ServerPlayer teleport(TeleportTransition transition)    // ServerPlayer
public record TeleportTransition(ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot, boolean missingRespawnBlock, boolean asPassenger, Set<Relative> relatives, TeleportTransition.PostTeleportTransition postTeleportTransition)
public TeleportTransition(ServerLevel newLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, PostTeleportTransition post)
public TeleportTransition(ServerLevel newLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, Set<Relative> relatives, PostTeleportTransition post)
public static final PostTeleportTransition DO_NOTHING, PLAY_PORTAL_SOUND, PLACE_PORTAL_TICKET
public static TeleportTransition createDefault(ServerPlayer player, PostTeleportTransition post)
public static TeleportTransition missingRespawnBlock(ServerPlayer player, PostTeleportTransition post)
public TeleportTransition withRotation(float yRot, float xRot); withPosition(Vec3); transitionAsPassenger()
// PostTeleportTransition: void onTransition(Entity)   (method name inferred from ServerPlayer patch usage -- UNVERIFIED)
// Relative: net.minecraft.world.entity.Relative (package UNVERIFIED)
entity.teleport(new TeleportTransition(targetLevel, new Vec3(x, y, z), Vec3.ZERO, yRot, xRot, TeleportTransition.PLAY_PORTAL_SOUND));
// simpler: entity.teleportTo(ServerLevel, double x, double y, double z, Set<Relative>, float yRot, float xRot, boolean setCamera)   (used by Infiniverse 26.1)
```

`Entity#changeDimension` no longer exists (renamed to `teleport` in 1.21.2). Events: `EntityTravelToDimensionEvent(Entity, ResourceKey<Level>)` (cancellable, game bus), `PlayerEvent.PlayerChangedDimensionEvent`, `EntityTeleportEvent`, `PlayerRespawnPositionEvent#getTeleportTransition()`.

Portal interface (VERIFIED javadoc):

```java
public interface Portal {   // net.minecraft.world.level.block.Portal -- implement on your portal Block
    default int getPortalTransitionTime(ServerLevel level, Entity entity)
    @Nullable TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos);
    default Portal.Transition getLocalTransition()   // enum Transition { CONFUSION, NONE }
}
// in Block#entityInside(BlockState, Level, BlockPos, Entity, InsideBlockEffectApplier): if (entity.canUsePortal(false)) entity.setAsInsidePortal(this, pos);   // UNVERIFIED exact 26.1 signatures (unchanged from 1.21.x per primers)
```

### 4.12 Fluids

VERIFIED sources (`FluidType.java`, `BaseFlowingFluid.java`, `RegisterClientExtensionsEvent`, `IClientFluidTypeExtensions`, `RegisterFluidModelsEvent`):

```java
public static final Supplier<FluidType> INK_TYPE = FLUID_TYPES.register("ink", () -> new FluidType(FluidType.Properties.create()
    .descriptionId("fluid_type.examplemod.ink").density(1200).viscosity(1500).temperature(300).lightLevel(0).canSwim(true).canDrown(true).canExtinguish(true).canConvertToSource(false).supportsBoating(false).rarity(Rarity.COMMON).sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)));
public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> INK = FLUIDS.register("ink", () -> new BaseFlowingFluid.Source(INK_PROPS));
public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_INK = FLUIDS.register("flowing_ink", () -> new BaseFlowingFluid.Flowing(INK_PROPS));
public static final BaseFlowingFluid.Properties INK_PROPS = new BaseFlowingFluid.Properties(INK_TYPE, INK, FLOWING_INK).bucket(INK_BUCKET).block(INK_BLOCK).slopeFindDistance(4).levelDecreasePerBlock(1).explosionResistance(100f).tickRate(5);
public static final DeferredBlock<LiquidBlock> INK_BLOCK = BLOCKS.registerBlock("ink", p -> new LiquidBlock(INK.get(), p), p -> p.mapColor(MapColor.COLOR_BLACK).replaceable().noCollission().strength(100f).liquid().noLootTable()); // LiquidBlock(FlowingFluid, Properties) ctor UNVERIFIED for 26.1
public static final DeferredItem<BucketItem> INK_BUCKET = ITEMS.registerItem("ink_bucket", p -> new BucketItem(INK.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1)); // BucketItem(Fluid, Properties) ctor UNVERIFIED for 26.1
// client: textures are NOT on IClientFluidTypeExtensions any more -- use RegisterFluidModelsEvent (mod bus, client)
@SubscribeEvent static void fluidModels(RegisterFluidModelsEvent e) {
    e.register(new FluidModel.Unbaked(new Material(Identifier.fromNamespaceAndPath(MOD_ID, "block/ink_still"), true), new Material(Identifier.fromNamespaceAndPath(MOD_ID, "block/ink_flowing")), /*overlay*/ null, /*BlockTintSource*/ null), INK, FLOWING_INK);
}
@SubscribeEvent static void clientExt(RegisterClientExtensionsEvent e) { e.registerFluidType(new IClientFluidTypeExtensions() { /* getRenderOverlayTexture(Minecraft), renderOverlay(...), modifyFogColor(Camera, float, ClientLevel, int, float, Vector4f), modifyFogRender(Camera, @Nullable FogEnvironment, float, float, FogData) */ }, INK_TYPE.get()); }
```

`FluidType.Properties` builder methods: `descriptionId, motionScale, canPushEntity, canSwim, canDrown, fallDistanceModifier, canExtinguish, canConvertToSource, supportsBoating, pathType, adjacentPathType, sound(SoundAction, SoundEvent), canHydrate, lightLevel, density, temperature, viscosity, rarity, addDripstoneDripping(float, ParticleOptions, Block, @Nullable SoundEvent), isWaterLike`. NeoForge added `FluidStackTemplate` (26.1). `FluidType#move(FluidState, LivingEntity, Vec3, double)` is deprecated for `move(LivingEntity, Vec3, double)`.

### 4.13 Villagers: profession, POI, trades (datapack-driven in 26.1)

`VillagerTradesEvent` and `WandererTradesEvent` **do not exist** in NeoForge 26.1.x (VERIFIED: `event/village/` only contains `VillageSiegeEvent.java`). Trades are datapack registry objects (26.1 primer "Datapack Villager Trades", VERIFIED):

- `data/<ns>/villager_trade/<path>.json`: `wants` (`{id, count (number provider, default 1), components}`), optional `additional_wants`, `gives` (stack template `{id, count [1,99], components}`), `max_uses` (default 4), `reputation_discount` (default 0), `xp` (default 1), `merchant_predicate` (loot condition), `given_item_modifiers` (loot functions), `double_trade_price_enchantments`.
- `data/<ns>/trade_set/<path>.json`: `{"trades": "#examplemod:linker/level_1", "amount": {"type": "minecraft:uniform", "min": 1, "max": 5}, "allow_duplicates": true, "random_sequence": "examplemod:linker/level_1"}`. Add to vanilla via tags `data/minecraft/tags/villager_trade/<profession>/level_<n>.json`. Datagen: `VillagerTradesTagsProvider`. Registries `Registries.VILLAGER_TRADE`, `Registries.TRADE_SET`.
- Profession (primer verbatim, VERIFIED): `new VillagerProfession(Component name, Predicate<Holder<PoiType>> heldJobSite, Predicate<Holder<PoiType>> acquirableJobSite, ImmutableSet<Item> requestedItems, ImmutableSet<Block> secondaryPoi, @Nullable SoundEvent workSound, Int2ObjectMap<ResourceKey<TradeSet>> tradeSetsByLevel)` with `Int2ObjectMap.ofEntries(Int2ObjectMap.entry(1, ResourceKey.create(Registries.TRADE_SET, Identifier.fromNamespaceAndPath("examplemod", "example_profession/level_1"))))`; job-site predicate `holder -> holder.is(MY_POI_KEY)`.
- POI: `new PoiType(Set<BlockState> matchingStates, int maxTickets, int validRange)` registered to `Registries.POINT_OF_INTEREST_TYPE` (UNVERIFIED for 26.1; unchanged since 1.19.3). Villager brains: `Brain.provider(...)` with `ActivityData` (26.1 primer "Activities and Brains").

### 4.14 Structures

Datapack: `data/<ns>/worldgen/structure/<name>.json` (`"type": "minecraft:jigsaw"`, `start_pool`, `size`, `max_distance_from_center`, `biomes`, `step`, `spawn_overrides`, `terrain_adaptation`), `data/<ns>/worldgen/structure_set/<name>.json` (`structures` + `placement`), `data/<ns>/worldgen/template_pool/<name>.json`, NBT templates at `data/<ns>/structure/<name>.nbt` (VERIFIED folder names per https://docs.neoforged.net/docs/resources/ and the gametest page). Java `StructureType`: `Registries.STRUCTURE_TYPE`, `StructureType<S>` = `() -> MapCodec<S>`; `Structure` subclass implements `Optional<GenerationStub> findGenerationPoint(GenerationContext)` and `StructureType<?> type()` (UNVERIFIED for 26.1). NeoForge `StructureModifier` datapack registry exists (`NeoForgeRegistries.Keys.STRUCTURE_MODIFIERS`). 26.1: `RandomPatchFeature` removed (use `simple_block` + `count`/`random_offset`/`block_predicate_filter` placements); `StructureTemplateManager#save` can write SNBT.

### 4.15 Loot tables, recipes, datagen providers

- 26.1 "Loot Type Unrolling": `LootPoolEntryType`, `LootItemFunctionType`, `LootItemConditionType`, `*ProviderType` records removed; registries hold `MapCodec`s and `getType()` is renamed `codec()`: `Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, id, MyFunction.MAP_CODEC)`. `LootContextParams.TOOL` is `ContextKey<ItemInstance>`. Loot JSON format itself is unchanged (`{"type": "minecraft:block", "pools": [...]}`).
- Recipes: `RecipeSerializer<T>` is a record `new RecipeSerializer<>(MapCodec<T>, StreamCodec<RegistryFriendlyByteBuf, T>)`; `Recipe$CommonInfo`, `Recipe$BookInfo`, `CraftingRecipe$CraftingBookInfo` carry group/category/notification; vanilla results are `ItemStackTemplate` (JSON `"result": {"id": "examplemod:page", "count": 1, "components": {...}}`). Recipe JSON: `{"type": "minecraft:crafting_shaped", "pattern": [...], "key": {...}, "result": {"id": "...", "count": 1}}` (26.1 shape; VERIFIED that recipes take templates, exact JSON UNVERIFIED beyond `{id,count,components}`). `Recipe#assemble` no longer takes `HolderLookup.Provider`.
- Datagen entry point (VERIFIED docs): `GatherDataEvent.Client` (all providers) / `GatherDataEvent.Server` (datapack-only); `event.createProvider(X::new)`, `event.createDatapackRegistryObjects(RegistrySetBuilder, Set<String>)`, `event.createBlockAndItemTags(BlockTags::new, ItemTags::new)`, `event.getLookupProvider()`. Tasks: MDK `runData` (`clientData()`); MDG also has `runServerData` if a `serverData()` run is defined.

```java
// RecipeProvider (26.1 Runner pattern)
public class MyRecipes extends RecipeProvider {
    public MyRecipes(HolderLookup.Provider registries, RecipeOutput output) { super(registries, output); }
    @Override protected void buildRecipes() { ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MyItems.PAGE) /* ItemLike overload; ItemStackTemplate overload exists */.pattern("PP").define('P', Items.PAPER).unlockedBy("has_paper", has(Items.PAPER)).save(this.output); }
    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) { super(output, registries); }
        @Override protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) { return new MyRecipes(registries, output); }
    }
}
// ModelProvider
public class MyModels extends ModelProvider { public MyModels(PackOutput output) { super(output, MODID); }
    @Override protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) { blockModels.createTrivialCube(MyBlocks.BOOKSTAND.get()); itemModels.generateFlatItem(MyItems.PAGE.get(), ModelTemplates.FLAT_ITEM); } }
// LanguageProvider: super(output, MODID, "en_us"); addTranslations(): add(...), addBlock(Supplier), addItem, addEntityType, addDimension(ResourceKey<Level>, String), addBiome(ResourceKey<Biome>, String)
// Tags: class MyBlockTags extends BlockTagsProvider { MyBlockTags(PackOutput o, CompletableFuture<HolderLookup.Provider> l) { super(o, l, MODID); } addTags(HolderLookup.Provider p) { tag(MY_TAG).add(Blocks.DIRT).addTag(BlockTags.PLANKS); } }
//       item tags copying block tags: BlockTagCopyingItemTagProvider(PackOutput, CompletableFuture<HolderLookup.Provider>, CompletableFuture<TagLookup<Block>>) { super(o, l, blockTags, MODID); } copy(BLOCK_TAG, ITEM_TAG)
// LootTableProvider
event.createProvider((output, lookup) -> new LootTableProvider(output, Set.of(), List.of(new LootTableProvider.SubProviderEntry(MyBlockLoot::new, LootContextParamSets.BLOCK)), lookup));
class MyBlockLoot extends BlockLootSubProvider { MyBlockLoot(HolderLookup.Provider p) { super(Set.of(), FeatureFlags.DEFAULT_FLAGS, p); } @Override protected void generate() { dropSelf(MyBlocks.BOOKSTAND.get()); } @Override protected Iterable<Block> getKnownBlocks() { return MyBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator; } }
// Datapack registries (dimension types, biomes, level stems, structures, test instances)
event.createDatapackRegistryObjects(new RegistrySetBuilder()
    .add(Registries.DIMENSION_TYPE, bs -> bs.register(AGE_TYPE, new DimensionType(...)))
    .add(Registries.BIOME, bs -> bs.register(AGE_BIOME, new Biome.BiomeBuilder()....build()))
    .add(Registries.LEVEL_STEM, bs -> bs.register(AGE_STEM, new LevelStem(bs.lookup(Registries.DIMENSION_TYPE).getOrThrow(AGE_TYPE), generator))),
    Set.of(MODID));
// also: DataMapProvider(PackOutput, CompletableFuture<HolderLookup.Provider>) gather(); SoundDefinitionsProvider(output, MODID) registerSounds(); ParticleDescriptionProvider(output) addDescriptions()
```

26.1 datagen changes (primer): `DataGenerator` is abstract (`Cached`/`Uncached`); `RecipeBuilder#getResult` removed, `defaultId()` added; `ShapedRecipeBuilder`/`ShapelessRecipeBuilder`/`SimpleCookingRecipeBuilder`/`SingleItemRecipeBuilder`/`SmithingTransformRecipeBuilder`/`TransmuteRecipeBuilder` take `ItemStackTemplate` results with `ItemLike` overloads (constructors private); `oreSmelting`/`oreBlasting` take a `CookingBookCategory`; new tag providers `HolderTagProvider`, `FeatureTagsProvider`, `PotionTagsProvider`, `VillagerTradesTagsProvider`; `BlockLootSubProvider` 4-arg ctor removed.

### 4.16 Client item model JSON (`assets/<ns>/items/<name>.json`, since 1.21.4)

VERIFIED https://docs.neoforged.net/docs/resources/client/models/items :

```json5
// basic item: assets/examplemod/items/page.json
{ "model": { "type": "minecraft:model", "model": "examplemod:item/page" },
  "properties": { "hand_animation_on_swap": false, "oversized_in_gui": false, "swap_animation_scale": 1.0 } }
// block item: point at the block model (ModelProvider auto-generates this for BlockItems without a client item)
{ "model": { "type": "minecraft:model", "model": "examplemod:block/bookstand" } }
// tinted
{ "model": { "type": "minecraft:model", "model": "examplemod:item/ink_vial", "tints": [ { "type": "minecraft:constant", "value": 65280 }, { "type": "minecraft:dye", "default": 255 } ] } }
// condition (boolean property)
{ "model": { "type": "minecraft:condition", "property": "minecraft:damaged",
    "on_true":  { "type": "minecraft:model", "model": "examplemod:item/page_torn" },
    "on_false": { "type": "minecraft:model", "model": "examplemod:item/page" } } }
// select (enum property)
{ "model": { "type": "minecraft:select", "property": "minecraft:display_context",
    "fallback": { "type": "minecraft:model", "model": "examplemod:item/book" },
    "cases": [ { "when": "gui", "model": { "type": "minecraft:model", "model": "examplemod:item/book_gui" } },
               { "when": "firstperson_righthand", "model": { "type": "minecraft:model", "model": "examplemod:item/book_hand" } } ] } }
// range dispatch (numeric property)
{ "model": { "type": "minecraft:range_dispatch", "property": "minecraft:count", "scale": 1, "normalize": true,
    "fallback": { "type": "minecraft:model", "model": "examplemod:item/pages" },
    "entries": [ { "threshold": 0.33, "model": { "type": "minecraft:model", "model": "examplemod:item/pages_1" } },
                 { "threshold": 0.66, "model": { "type": "minecraft:model", "model": "examplemod:item/pages_2" } } ] } }
```

Custom properties: `RegisterRangeSelectItemModelPropertyEvent` (`float get(ItemStack, @Nullable ClientLevel, @Nullable ItemOwner, int seed)`), `RegisterSelectItemModelPropertyEvent` (`SelectItemModelProperty.Type.create(MapCodec, Codec)`), `RegisterConditionalItemModelPropertyEvent`; custom tint sources via `RegisterColorHandlersEvent.ItemTintSources#register(Identifier, MapCodec)`. Datagen: `ItemModelUtils.plainModel/conditional/rangeSelect/select`, `itemModels.itemModelOutput.accept(item, unbaked)`. 26.1: `ItemModel$Unbaked#bake` takes a parent `Matrix4fc`; `RenderType` selection removed (per-quad from texture transparency; model textures may be `{"sprite": "...", "force_translucent": true}`).

### 4.17 Config (ModConfigSpec)

VERIFIED docs https://docs.neoforged.net/docs/misc/config and `ModConfigSpec.java`: see the MDK `Config` class above. Builder: `comment(String...)`, `translation(String)`, `worldRestart()`, `gameRestart()`, `push/pop`, `define(path, default[, validator])`, `defineInRange(path, int|long|double default, min, max)`, `defineInList`, `defineList/defineListAllowEmpty(path, default, newElementSupplier, validator)`, `defineEnum`, `configure(Function<Builder, T>) -> Pair<T, ModConfigSpec>`, `build()`. Values: `ConfigValue#get/getRaw/getDefault/set/save`, `BooleanValue#getAsBoolean`, `IntValue#getAsInt`. Types: `ModConfig.Type.STARTUP` (read at registration, `-startup`), `CLIENT`, `COMMON`, `SERVER` (per-world `serverconfig`, synced to clients). Register: `modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC)`. Events (mod bus): `ModConfigEvent.Loading`, `.Reloading`, `.Unloading`. Config screen: `container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)` (client).

### 4.18 Creative tabs, tags, sounds.json, neoforge.mods.toml

- Creative tabs: see ExampleMod (`CreativeModeTab.builder().title(...).withTabsBefore(CreativeModeTabs.COMBAT).icon(() -> item.get().getDefaultInstance()).displayItems((params, output) -> output.accept(...)).build()`); vanilla tab injection via `BuildCreativeModeTabContentsEvent` (`event.getTabKey() == CreativeModeTabs.INGREDIENTS`, `event.accept(...)`). 26.1: `CreativeModeTab$Output` is now protected.
- Tags: `data/<ns>/tags/<registry_path>/<path>.json` for Minecraft registries (singular: `tags/item/`, `tags/block/`, `tags/entity_type/`, `tags/fluid/`, `tags/worldgen/biome/`, `tags/villager_trade/`), `data/<ns>/tags/<registry_ns>/<registry_path>/<path>.json` for non-Minecraft registries. Format `{"replace": false, "values": ["minecraft:gold_ingot", "#minecraft:planks", {"id": "othermod:x", "required": false}], "remove": [...]}` (`remove` is NeoForge-only). Common namespace `c` (`c:ingots/gold`). Java: `TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "x"))`, `state.is(TAG)`, `stack.is(TAG)`. 26.1 renamed plant tags to `supports_*`.
- `assets/<ns>/sounds.json`: `{"my_sound": {"subtitle": "subtitles.examplemod.my_sound", "sounds": [{"name": "examplemod:my_sound_1", "type": "sound", "volume": 0.8, "pitch": 1.0, "weight": 1, "stream": false, "attenuation_distance": 16, "preload": false}, "examplemod:my_sound_2"]}}`; `.ogg` files under `assets/<ns>/sounds/`, mono for attenuation. Play: `level.playSound(@Nullable Entity, BlockPos, SoundEvent, SoundSource, float volume, float pitch)`; `Level#playSeededSound(...)`; `Entity#playSound(SoundEvent, float, float)`.
- `neoforge.mods.toml` keys (VERIFIED https://docs.neoforged.net/docs/gettingstarted/modfiles and FML `ModFileInfo.java`): top-level `modLoader` (default `"javafml"`), `loaderVersion` (optional; FML: "You cannot specify a loaderVersion without specifying a modLoader"; NeoForge's own manifest uses `loaderVersion="[3,]"`, its test mod `"[1,)"`; the MDK omits both — omit or use `modLoader="javafml"` + `loaderVersion="[3,)"`), `license` (mandatory), `issueTrackerURL`, `showAsResourcePack`, `showAsDataPack`, `services`, `properties`. `[[mods]]`: `modId`, `namespace`, `version` (`${file.jarVersion}` supported), `displayName`, `description`, `logoFile`, `logoBlur`, `updateJSONURL`, `modUrl`/`displayURL`, `credits`, `authors`, `enumExtensions`, `featureFlags`. `[[dependencies.<modid>]]`: `modId`, `type` = `required|optional|incompatible|discouraged`, `reason`, `versionRange` (Maven range), `ordering` = `NONE|BEFORE|AFTER`, `side` = `BOTH|CLIENT|SERVER`, `referralUrl`. `[[mixins]]`: `config` (mandatory), `requiredMods`, `behaviorVersion`. `[[accessTransformers]]`: `file`. `[features.<modid>]`: `javaVersion="[25,)"`, `openGLVersion`. `[modproperties.<modid>]`: arbitrary. The `loaderVersion "[4,)"` premise is not what 26.1 uses (FML 11.0.15; javafml language version 1..3 accepted).

### 4.19 Mixins and Access Transformers with MDG

- Mixins: no docs page exists for 26.1 (https://docs.neoforged.net/docs/advanced/ has only Access Transformers, Extensible Enums, Feature Flags). Required configuration is only the toml block `[[mixins]] config="examplemod.mixins.json"` (VERIFIED modfiles docs) plus a standard Sponge config; no refmap is needed (Mojang names at runtime; MDG needs no mixin plugin) — UNVERIFIED but consistent with MDG README/MDK containing no mixin config. NeoForge 26.1.x ships Mixin `0.17.3+mixin.0.8.7` and MixinExtras `0.5.4`. Config template (UNVERIFIED `compatibilityLevel` value for Java 25 — `JAVA_21` is the highest widely documented; try `JAVA_25` first, fall back to `JAVA_21`):

```json
{ "required": true, "minVersion": "0.8", "package": "com.example.examplemod.mixin", "compatibilityLevel": "JAVA_21", "refmap": "examplemod.refmap.json",
  "mixins": ["MinecraftServerAccessor"], "client": [], "server": [], "injectors": { "defaultRequire": 1 } }
```

- Access Transformers (VERIFIED https://docs.neoforged.net/docs/advanced/accesstransformers and MDG README): default path `src/main/resources/META-INF/accesstransformer.cfg` is auto-detected by both MDG and FML ("If this block is omitted, a fallback attempt will be made to load an AT from META-INF/accesstransformer.cfg"); other paths need `neoForge { accessTransformers.from 'path' }` and `[[accessTransformers]] file="..."`. Format: `<public|protected|default|private>[+f|-f] <owner.class> [<member> [<descriptor>]]`, e.g. `public net.minecraft.server.MinecraftServer executor`, `public-f net.minecraft.world.level.levelgen.WorldGenSettings dimensions`, `protected-f net.minecraft.server.MinecraftServer random`, `public net.minecraft.core.UUIDUtil leastMostToIntArray(JJ)[I`. `#` comments. `validateAccessTransformers = true` in `neoForge {}` fails the build on bad entries.

### 4.20 Java 25 language features usable in mods

Mods compile with `--release 25` (toolchain 25). Usable final (non-preview) features: records (16), sealed classes/interfaces (17), pattern matching for `instanceof` (16) and for `switch` incl. record deconstruction patterns and guards (21), virtual threads (21), sequenced collections (21), unnamed variables `_` (22), Markdown javadoc (23), **flexible constructor bodies** (JEP 513, final in 25), **module import declarations** `import module java.base;` (JEP 511, final in 25), **compact source files & instance main** (JEP 512, final in 25), **scoped values** (JEP 506, final in 25), generational Shenandoah (25). Still preview in 25 (avoid in mods; would require `--enable-preview` at runtime): primitive types in patterns (JEP 507), structured concurrency (JEP 505), stable values (JEP 502). Sources: https://openjdk.org/projects/jdk/25/ (UNVERIFIED this session; well-known JEP list). Vanilla 26.1 itself uses records heavily (`ChunkPos` is a record in 26.1; `LevelStem`, `TeleportTransition`, `SavedDataType`, `ClientboundLoginPacket` are records), so `switch` patterns over sealed hierarchies are idiomatic.

---

## 5. Testing

### 5.1 GameTests (datapack-driven since 1.21.5)

VERIFIED https://docs.neoforged.net/docs/misc/gametest and `RegisterGameTestsEvent.java`:

- Test definitions are registry entries: `data/<ns>/test_environment/<path>.json` (`minecraft:default`, `minecraft:game_rules`, `minecraft:clock_time`, `minecraft:timeline_attributes`, `minecraft:weather`, `minecraft:function`, `minecraft:all_of`), `data/<ns>/test_instance/<path>.json` (`"type": "minecraft:function"|"minecraft:block_based"`, `environment`, `structure`, `max_ticks`, `setup_ticks`, `required`, `rotation`, `manual_only`, `max_attempts`, `required_successes`, `sky_access`), structures at `data/<ns>/structure/<name>.nbt`. `TestData<E>(E environment, Identifier structure, int maxTicks, int setupTicks, boolean required, Rotation rotation, boolean manualOnly, int maxAttempts, int requiredSuccesses, boolean skyAccess, int padding)`.
- Test functions as registry objects: `DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTION = DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, MODID); TEST_FUNCTION.register("example_function", () -> ExampleFunctions::exampleTest);` and `static void exampleTest(GameTestHelper helper) { ... helper.succeed(); }` (`succeedIf`, `succeedWhen`, `runAtTickTime`, `runAfterDelay`, `absolutePos`, assertions throw `GameTestAssertException`).
- Java registration without JSON (mod bus): `RegisterGameTestsEvent#registerEnvironment(Identifier, TestEnvironmentDefinition<?>...)` and `#registerTest(Identifier, GameTestInstance)` / `#registerTest(Identifier, Function<TestData<Holder<TestEnvironmentDefinition<?>>>, GameTestInstance>, TestData<...>)`; only runs when `GameTestHooks.isGametestEnabled()` = `!FMLEnvironment.isProduction() && (IS_RUNNING_IN_IDE || isGameTestServer() || Boolean.getBoolean("neoforge.enableGameTest"))`. No `@GameTest` annotation scanning in NeoForge core; NeoForge's own tests use the separate `net.neoforged:testframework` artifact (`@ForEachTest`, `@GameTest`, `@TestHolder`, `@EmptyTemplate`).
- Headless CI: `./gradlew runGameTestServer` (MDK run `gameTestServer { type = "gameTestServer"; systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id }`); "The build server returns an exit code of the number of required, failed Game Tests" and "By default, the server will crash when no gametests are provided". In-game: `/test run|runall|runclosest|runthese|runfailed`.

### 5.2 JUnit with MDG (VERIFIED MDG README)

```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.13.4'      // README shows 5.7.1; NeoForge 26.1.x uses jupiter 5.13.4
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    // optional server-backed tests:
    // testImplementation "net.neoforged:testframework:${neo_version}"
}
test { useJUnitPlatform() }
neoForge {
    unitTest {
        enable()
        testedMod = mods.examplemod      // must match the name in mods { }
        // loadedMods = [mods.examplemod, mods.other]
    }
}
```

```java
@ExtendWith(EphemeralTestServerProvider.class)   // net.neoforged.testframework.junit.EphemeralTestServerProvider -- package UNVERIFIED
public class AgeTests { @Test void testMethod(MinecraftServer server) { /* ... */ } }
```

Run: `./gradlew test`. There is no `junit` run type; MDG wires `test` itself.

---

## 6. Publishing

### 6.1 Plugins (VERIFIED versions, 2026-09-06)

- **mod-publish-plugin** `me.modmuss50.mod-publish-plugin` **2.2.0** (2026-07-29, configuration-cache compatible) — recommended: one `publishMods` task for Modrinth + CurseForge + GitHub Releases. https://plugins.gradle.org/plugin/me.modmuss50.mod-publish-plugin , README https://raw.githubusercontent.com/modmuss50/mod-publish-plugin/main/README.md

```groovy
plugins { id "me.modmuss50.mod-publish-plugin" version "2.2.0" }
publishMods {
    file = jar.archiveFile
    changelog = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText
    type = STABLE            // or BETA / ALPHA
    modLoaders.add("neoforge")
    displayName = "${mod_name} ${mod_version} for NeoForge ${minecraft_version}"
    curseforge {
        projectId = "123456"
        projectSlug = "my-mod"
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersionList("26.1, 26.1.1, 26.1.2")
        javaVersions.add(JavaVersion.VERSION_25)   // UNVERIFIED that CurseForge has a "Java 25" tag
        client = true
        server = true      // CurseForge requires environment tags from 2026-07-15
        requires("infiniverse")
    }
    modrinth {
        projectId = "abcdef"
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.add("26.1")   // exact Modrinth game_version strings: "26.1", "26.1.1", "26.1.2"
        environment = CLIENT_AND_SERVER
        requires("infiniverse")
    }
    github {
        repository = "owner/repo"
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        commitish = "main"
        tagName = "v${mod_version}"
    }
}
```

- **Minotaur** `com.modrinth.minotaur` **2.9.0** (2026-03-08) — Modrinth only; DSL `modrinth { token = System.getenv("MODRINTH_TOKEN"); projectId; versionNumber; versionType; uploadFile = jar; gameVersions = ["26.1"]; loaders = ["neoforge"]; dependencies { required.project "infiniverse" } }`. Game versions are not auto-detected under MDG — set them. https://raw.githubusercontent.com/modrinth/minotaur/master/README.md
- **CurseForgeGradle** `net.darkhax.curseforgegradle` **1.3.33** (2026-07-01; GitHub Releases page is stale) — `task publishCurseForge(type: net.darkhax.curseforgegradle.TaskPublishCurseForge) { apiToken = findProperty('curseforge_token'); def mainFile = upload(findProperty('curseforge_project'), jar); mainFile.changelog = ...; mainFile.addGameVersion('26.1'); mainFile.addEnvironment('Client', 'Server') }`; auto-detects loader `NeoForge` when `net.neoforged.moddev` is applied. https://raw.githubusercontent.com/Darkhax/CurseForgeGradle/main/README.md
- Modrinth loader id `"neoforge"`; game version `"26.1"` (release, 2026-03-24), `"26.1.1"`, `"26.1.2"` (VERIFIED https://api.modrinth.com/v2/tag/game_version). CurseForge accepts the version name string `"26.1"`; its numeric id is UNVERIFIED.

### 6.2 Versioning and changelog conventions

- NeoForge docs (VERIFIED https://docs.neoforged.net/docs/gettingstarted/versioning/): mods use semver `major.minor.patch` (`0.x.x` = development, `1.0.0` first full release); file names commonly `modid-<mcversion>-<version>.jar` or `modid-neoforge-<mcversion>-<version>.jar`; the `version` in `neoforge.mods.toml` must remain Maven-Version-Range-comparable (digits, dots, optional `-beta`/`-alpha` qualifier) so other mods can depend on ranges. MDK: `mod_version=1.0.0`, `version = mod_version`, `archivesName = mod_id` -> `examplemod-1.0.0.jar`; to embed the MC version set `archivesName = "${mod_id}-${minecraft_version}"` (keep `version` plain semver).
- Changelog: Keep a Changelog 1.1.0 (`## [Unreleased]`, `## [1.0.0] - 2026-09-06`, `Added/Changed/Deprecated/Removed/Fixed/Security`) https://keepachangelog.com/en/1.1.0/ ; Conventional Commits 1.0.0 (`feat:`/`fix:`/`feat!:`) https://www.conventionalcommits.org/en/v1.0.0/ ; `gh release create vX.Y.Z --generate-notes` or `-F CHANGELOG-section.md`.
- Release automation: tag `vX.Y.Z` -> CI builds (section 3.3) -> `publishMods` with secrets `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`, `GITHUB_TOKEN` (Modrinth PAT scopes: Create/Read/Write versions; CurseForge API token from the account page). Publish the same jar to all targets; mark `type = BETA` for `-beta` versions.

---

## 7. UNVERIFIED items (explicit list)

1. `ChunkGenerator#codec()`, `BiomeSource#codec()` / `collectPossibleBiomes()` exact protected signatures in 26.1 (javadoc hid protected members; expected unchanged from 1.21.x). `NaturalSpawner.spawnMobsForChunkGeneration` signature.
2. `TeleportTransition.PostTeleportTransition#onTransition(Entity)` method name; `Relative` package; `Entity#setAsInsidePortal` / `canUsePortal` 26.1 signatures; `ClientboundRespawnPacket` shape.
3. `LiquidBlock(FlowingFluid, Properties)` and `BucketItem(Fluid, Properties)` ctor arity in 26.1; `PoiType` ctor; `StructureType`/`Structure` abstract method set; `RecipeType.simple`.
4. Exact vanilla environment-attribute JSON ids other than `minecraft:visual/cloud_height` (dump from the client jar).
5. Mixin `compatibilityLevel` for Java 25 and refmap requirements (no docs page).
6. `actions/download-artifact` major; `docker/build-push-action` `outputs`/`target` input wording (inputs exist per Docker docs).
7. Gradle wrapper jar byte size; that `eclipse-temurin:25-jdk` contains exactly Temurin `25.0.4.1+1`.
8. CurseForge numeric game-version id and "Java 25" tag; Parchment availability for 26.1 (moot: unobfuscated).
9. `EphemeralTestServerProvider` package name; `RegisterFluidModelsEvent` full contents beyond the three `register` overloads listed.
10. `WorldData#worldGenOptions()` existence in 26.1 (Infiniverse switched to `server.getWorldGenSettings().options()` — use that).

Primary source index: NeoForge maven-metadata https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml ; MDG maven-metadata https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml ; MDK https://github.com/NeoForgeMDKs/MDK-26.1-ModDevGradle ; MDG README https://github.com/neoforged/ModDevGradle ; NeoForge 26.1.x https://github.com/neoforged/NeoForge/tree/26.1.x ; 26.1 primer https://github.com/neoforged/.github/blob/main/primers/26.1/index.md ; 1.21.11 primer https://github.com/neoforged/.github/blob/main/primers/1.21.11/index.md ; NeoForge 26.1 release post https://neoforged.net/news/26.1release/ ; docs https://docs.neoforged.net ; Infiniverse https://github.com/Commoble/infiniverse ; javadoc mirror https://lexxie.dev/neoforge/26.1/ ; Docker Hub API https://hub.docker.com/v2/repositories/library/eclipse-temurin/tags/25-jdk ; Gradle versions https://services.gradle.org/versions/all ; Modrinth tags https://api.modrinth.com/v2/tag/game_version .

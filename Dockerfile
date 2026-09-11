# syntax=docker/dockerfile:1.7
#
# Reproducible build of Mystcraft Reborn.
#   Local:   ./scripts/build.sh            (or scripts\build.ps1 on Windows)
#   Extract: docker buildx build --target export --output type=local,dest=out .
#   CI:      .github/workflows/build.yml uses this exact file.
#
# Stage 1: build with JDK 25 (Minecraft 26.1 targets Java 25). The Gradle user
# home is a BuildKit cache mount so NeoForm / NeoForge artifacts and the Gradle
# distribution are reused between builds.
FROM eclipse-temurin:25-jdk AS build

# CI=true makes ModDevGradle >= 2.0.136 skip the decompile/recompile pipeline.
ENV CI=true \
    GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.console=plain -Dorg.gradle.configuration-cache=false" \
    GRADLE_USER_HOME=/gradle-home

WORKDIR /src

# 1) Build scripts + wrapper first so the dependency layer is cached independently of sources.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
RUN --mount=type=cache,target=/gradle-home,id=mystcraft-gradle-home \
    chmod +x gradlew && ./gradlew --no-daemon --stacktrace help

# 2) Sources and resources.
COPY src src
ARG GRADLE_TASKS="build"
RUN --mount=type=cache,target=/gradle-home,id=mystcraft-gradle-home \
    ./gradlew --no-daemon --stacktrace ${GRADLE_TASKS} \
    && mkdir -p /out && cp build/libs/*.jar /out/ \
    && ls -la /out

# Stage 2: export only the built jars.
FROM scratch AS export
COPY --from=build /out/ /

# Stage 3 (optional): headless smoke test. Boots the NeoForge dedicated server with the mod
# installed and requires it to finish loading. This exercises mod construction, every registry,
# the access transformer, datapack parsing (dimension type, recipes, loot, trades, tags,
# advancements) and the server-start hooks — everything except client rendering.
#   scripts/smoke.ps1   /   scripts/smoke.sh
FROM build AS smoke
ARG SMOKE_SECONDS=420
ENV SMOKE_SECONDS=${SMOKE_SECONDS}
COPY scripts/smoke-entry.sh /usr/local/bin/smoke-entry.sh
RUN --mount=type=cache,target=/gradle-home,id=mystcraft-gradle-home sh /usr/local/bin/smoke-entry.sh

# Stage 4: export the jar and the smoke log together.
FROM scratch AS smoke-export
COPY --from=smoke /out/ /

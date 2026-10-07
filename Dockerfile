# syntax=docker/dockerfile:1.7
#
# Reproducible build of Mystcraft Reborn.
#   Local:   ./scripts/build.sh
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
#   scripts/smoke.sh
FROM build AS smoke
ARG SMOKE_SECONDS=420
ENV SMOKE_SECONDS=${SMOKE_SECONDS}
COPY docker/smoke-entry.sh /usr/local/bin/smoke-entry.sh
RUN --mount=type=cache,target=/gradle-home,id=mystcraft-gradle-home sh /usr/local/bin/smoke-entry.sh

# Stage 4: export the jar and the smoke log together.
FROM scratch AS smoke-export
COPY --from=smoke /out/ /

# Stage 5 (optional): headless in-game tests. Runs the NeoForge GameTest server with the mod plus the dev-only
# `mystcraft_tests` mod (src/gametest, NeoForge test framework): Age creation and arrival, portals, Disarm, fluids,
# instability. Everything server-side that a player would otherwise have to verify by hand.
#   scripts/gametest.sh
FROM build AS gametest
COPY docker/gametest-entry.sh /usr/local/bin/gametest-entry.sh
RUN --mount=type=cache,target=/gradle-home,id=mystcraft-gradle-home sh /usr/local/bin/gametest-entry.sh

FROM scratch AS gametest-export
COPY --from=gametest /out/ /

# Stage 6 (optional): headless CLIENT smoke test. Xvfb + Mesa llvmpipe give the dev client a real OpenGL context, and
# ClientSelfCheck (MYSTCRAFT_CLIENT_SELFCHECK=1) drives it: fresh flat world -> /myst-dev scene -> /myst visit into a
# new Age -> night -> every QA shelf world (day + night screenshots, compared with scripts/qa/baselines.json).
# Covers model baking, screens, BERs, Age sky/tints - what the server smoke cannot. The X11/Mesa layer is independent of the sources so it stays cached.
#   scripts/client-smoke.sh
FROM eclipse-temurin:25-jdk AS client-tools
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        xvfb mesa-utils libgl1 libglx-mesa0 libgl1-mesa-dri libegl1 \
        libx11-6 libxext6 libxrender1 libxrandr2 libxinerama1 libxcursor1 libxi6 libxxf86vm1 libxkbcommon0 \
        libopenal1 libasound2t64 fontconfig ca-certificates python3 python3-pil \
    && rm -rf /var/lib/apt/lists/*

FROM client-tools AS client-smoke
ENV CI=true \
    GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.console=plain -Dorg.gradle.configuration-cache=false" \
    GRADLE_USER_HOME=/gradle-home
WORKDIR /src
COPY --from=build /src /src
ARG CLIENT_SMOKE_SECONDS=1200
ENV CLIENT_SMOKE_SECONDS=${CLIENT_SMOKE_SECONDS}
COPY docker/client-smoke-entry.sh /usr/local/bin/client-smoke-entry.sh
COPY scripts/qa /usr/local/lib/mystcraft-qa
RUN --mount=type=cache,target=/gradle-home,id=mystcraft-gradle-home sh /usr/local/bin/client-smoke-entry.sh

FROM scratch AS client-smoke-export
COPY --from=client-smoke /out/ /

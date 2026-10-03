package com.techbucketdivision.mystcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static cross-reference check of {@code src/main/resources/assets/mystcraft}. Runs in the plain {@code test} task
 * (no Minecraft runtime), so a broken reference fails the Docker build instead of showing up as
 * "Missing textures in model" / "Missing FluidModel" warnings in a client log.
 * <p>
 * Checks (namespace {@code mystcraft} only; vanilla references are not verified):
 * <ul>
 *   <li>every JSON under {@code assets/} parses;</li>
 *   <li>blockstate variants / multipart models exist;</li>
 *   <li>item definitions ({@code items/*.json}) point at existing models (recursively for composites);</li>
 *   <li>model parents exist and every texture resolves to a {@code .png} that is in the block atlas
 *       ({@code textures/block}, {@code textures/item}, or a {@code single}/{@code directory} source in
 *       {@code atlases/blocks.json});</li>
 *   <li>{@code sounds.json} entries have an {@code .ogg};</li>
 *   <li>every {@code item.mystcraft.*} / {@code block.mystcraft.*} lang key has an item definition / blockstate.</li>
 * </ul>
 */
class AssetIntegrityTest {
    private static final String NS = "mystcraft";
    /** ModDevGradle runs tests from {@code build/minecraft-junit}, so locate the project root by walking up. */
    private static final Path ASSETS = projectDir().resolve("src/main/resources/assets").resolve(NS);

    private static Path projectDir() {
        String override = System.getProperty("mystcraft.projectDir");
        if (override != null) return Paths.get(override);
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle"))) dir = dir.getParent();
        return dir == null ? Paths.get("") : dir;
    }
    private static final Pattern ID = Pattern.compile("^(?:([a-z0-9_.-]+):)?([a-z0-9_./-]+)$");

    private final List<String> problems = new ArrayList<>();

    @Test
    void assetsCrossReference() throws IOException {
        assertTrue(Files.isDirectory(ASSETS), "assets dir missing: " + ASSETS.toAbsolutePath());
        Set<String> atlasSprites = blockAtlasSprites();

        try (Stream<Path> files = Files.walk(ASSETS)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> parse(p)); // syntax
        }
        checkBlockstates();
        checkItemDefinitions();
        checkModels(atlasSprites);
        checkSounds();
        checkLang();

        if (!problems.isEmpty()) {
            StringBuilder sb = new StringBuilder("asset integrity problems (" + problems.size() + "):\n");
            for (String p : new TreeSet<>(problems)) sb.append("  - ").append(p).append('\n');
            throw new AssertionError(sb.toString());
        }
    }

    // --- checks ------------------------------------------------------------------------------------------------

    private void checkBlockstates() throws IOException {
        for (Path p : list("blockstates")) {
            JsonObject root = parse(p).getAsJsonObject();
            String where = rel(p);
            if (root.has("variants")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("variants").entrySet()) {
                    forEachModel(e.getValue(), m -> requireModel(m, where + " variant '" + e.getKey() + "'"));
                }
            }
            if (root.has("multipart")) {
                for (JsonElement part : root.getAsJsonArray("multipart")) {
                    forEachModel(part.getAsJsonObject().get("apply"), m -> requireModel(m, where + " multipart"));
                }
            }
            if (!root.has("variants") && !root.has("multipart")) problems.add(where + ": neither variants nor multipart");
        }
    }

    private void forEachModel(JsonElement variant, java.util.function.Consumer<String> sink) {
        if (variant.isJsonArray()) {
            for (JsonElement e : variant.getAsJsonArray()) forEachModel(e, sink);
        } else if (variant.isJsonObject() && variant.getAsJsonObject().has("model")) {
            sink.accept(variant.getAsJsonObject().get("model").getAsString());
        }
    }

    private void checkItemDefinitions() throws IOException {
        for (Path p : list("items")) {
            JsonObject root = parse(p).getAsJsonObject();
            if (!root.has("model")) {
                problems.add(rel(p) + ": no 'model'");
                continue;
            }
            visitItemModel(root.get("model"), rel(p));
        }
    }

    /** Walks the 1.21.4+ item-model tree (model / composite / select / condition / range_dispatch / special / neoforge:*). */
    private void visitItemModel(JsonElement el, String where) {
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        String type = o.has("type") ? o.get("type").getAsString() : "";
        switch (type) {
            case "minecraft:model", "model" -> requireModel(o.get("model").getAsString(), where);
            case "neoforge:fluid_container" -> {
                if (o.has("textures")) {
                    for (Map.Entry<String, JsonElement> t : o.getAsJsonObject("textures").entrySet()) {
                        requireTexture(t.getValue(), where + " fluid_container." + t.getKey(), Set.of()); // item atlas, no atlas check
                    }
                }
            }
            default -> {
                // generic descent: any nested object/array that could hold a model
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (e.getValue().isJsonObject()) visitItemModel(e.getValue(), where);
                    else if (e.getValue().isJsonArray()) {
                        for (JsonElement c : e.getValue().getAsJsonArray()) {
                            if (c.isJsonObject()) {
                                JsonObject co = c.getAsJsonObject();
                                visitItemModel(co.has("model") && co.get("model").isJsonObject() ? co.get("model") : co, where);
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkModels(Set<String> atlasSprites) throws IOException {
        Set<String> itemAtlas = atlasSingles("items.json");
        for (Path p : list("models")) {
            JsonObject root = parse(p).getAsJsonObject();
            String where = rel(p);
            boolean itemModel = where.startsWith("models/item/");
            if (root.has("parent")) {
                String parent = root.get("parent").getAsString();
                if (ours(parent) && !Files.exists(ASSETS.resolve("models/" + path(parent) + ".json"))) {
                    problems.add(where + ": parent model missing " + parent);
                }
            }
            if (root.has("textures")) {
                for (Map.Entry<String, JsonElement> t : root.getAsJsonObject("textures").entrySet()) {
                    String v = t.getValue().getAsString();
                    if (v.startsWith("#")) continue; // variable, resolved by the parent/child chain
                    // Item models bake against the block atlas too (vanilla block items use block/ textures and the
                    // items atlas only lists item/), so one atlas membership check covers both; a sprite listed in
                    // both atlases is a "Duplicate sprite" warning that future versions reject.
                    if (itemModel && itemAtlas.contains(path(t.getValue().getAsString())) && atlasSprites.contains(path(t.getValue().getAsString()))) {
                        problems.add(where + ": " + t.getValue().getAsString() + " is listed in both atlases/blocks.json and atlases/items.json (duplicate sprite)");
                    }
                    requireTexture(t.getValue(), where + " texture '" + t.getKey() + "'", atlasSprites);
                }
            }
        }
    }

    private void checkSounds() throws IOException {
        Path sounds = ASSETS.resolve("sounds.json");
        if (!Files.exists(sounds)) return;
        for (Map.Entry<String, JsonElement> e : parse(sounds).getAsJsonObject().entrySet()) {
            JsonArray list = e.getValue().getAsJsonObject().getAsJsonArray("sounds");
            if (list == null) continue;
            for (JsonElement s : list) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                if (ours(name) && !Files.exists(ASSETS.resolve("sounds/" + path(name) + ".ogg"))) {
                    problems.add("sounds.json '" + e.getKey() + "': missing sounds/" + path(name) + ".ogg");
                }
            }
        }
    }

    private void checkLang() throws IOException {
        Path lang = ASSETS.resolve("lang/en_us.json");
        if (!Files.exists(lang)) return;
        for (String key : parse(lang).getAsJsonObject().keySet()) {
            if (key.startsWith("item." + NS + ".")) {
                String id = key.substring(("item." + NS + ".").length());
                if (!id.contains(".") && !Files.exists(ASSETS.resolve("items/" + id + ".json"))) {
                    problems.add("lang " + key + ": no items/" + id + ".json");
                }
            } else if (key.startsWith("block." + NS + ".")) {
                String id = key.substring(("block." + NS + ".").length());
                if (!id.contains(".") && !Files.exists(ASSETS.resolve("blockstates/" + id + ".json"))) {
                    problems.add("lang " + key + ": no blockstates/" + id + ".json");
                }
            }
        }
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private void requireModel(String id, String where) {
        if (!ours(id)) return;
        if (!Files.exists(ASSETS.resolve("models/" + path(id) + ".json"))) problems.add(where + ": model missing " + id);
    }

    private void requireTexture(JsonElement value, String where, Set<String> atlasSprites) {
        String id = value.isJsonObject() ? value.getAsJsonObject().get("sprite").getAsString() : value.getAsString();
        if (!ours(id)) return;
        String path = path(id);
        if (!Files.exists(ASSETS.resolve("textures/" + path + ".png"))) {
            problems.add(where + ": texture file missing textures/" + path + ".png (" + id + ")");
            return;
        }
        if (atlasSprites.isEmpty()) return;
        boolean inAtlas = path.startsWith("block/") || path.startsWith("item/") || atlasSprites.contains(path)
                || atlasSprites.stream().anyMatch(d -> d.endsWith("/") && path.startsWith(d));
        if (!inAtlas) {
            problems.add(where + ": " + id + " is not in the block atlas (not under textures/block|item and not listed in atlases/blocks.json)");
        }
    }

    /**
     * Extra sprites the mod adds to the block atlas: exact paths for {@code single}, {@code dir/} for {@code directory}.
     * Atlas definitions are looked up by the atlas id's namespace, so a mod's additions live in
     * {@code assets/minecraft/atlases/blocks.json} (stacked with vanilla's), not under the mod namespace.
     */
    private Set<String> blockAtlasSprites() throws IOException {
        Set<String> out = new TreeSet<>();
        Path atlas = ASSETS.resolveSibling("minecraft").resolve("atlases/blocks.json");
        if (Files.exists(ASSETS.resolve("atlases/blocks.json"))) {
            problems.add("atlases/blocks.json is under assets/" + NS + " - vanilla only reads assets/minecraft/atlases/*.json");
        }
        if (!Files.exists(atlas)) return out;
        for (JsonElement src : parse(atlas).getAsJsonObject().getAsJsonArray("sources")) {
            JsonObject o = src.getAsJsonObject();
            String type = o.get("type").getAsString();
            if (type.endsWith("single")) {
                String res = o.get("resource").getAsString();
                if (ours(res)) {
                    out.add(path(res));
                    if (!Files.exists(ASSETS.resolve("textures/" + path(res) + ".png"))) {
                        problems.add("minecraft/atlases/blocks.json: single source " + res + " has no texture file");
                    }
                }
            } else if (type.endsWith("directory")) {
                out.add(o.get("source").getAsString() + "/");
            }
        }
        return out;
    }

    /** {@code single} sources of {@code assets/minecraft/atlases/<file>} in this mod's namespace. */
    private Set<String> atlasSingles(String file) throws IOException {
        Set<String> out = new TreeSet<>();
        Path atlas = ASSETS.resolveSibling("minecraft").resolve("atlases/" + file);
        if (!Files.exists(atlas)) return out;
        for (JsonElement src : parse(atlas).getAsJsonObject().getAsJsonArray("sources")) {
            JsonObject o = src.getAsJsonObject();
            if (o.get("type").getAsString().endsWith("single") && ours(o.get("resource").getAsString())) out.add(path(o.get("resource").getAsString()));
        }
        return out;
    }

    private static boolean ours(String id) {
        Matcher m = ID.matcher(id);
        if (!m.matches()) return false;
        String ns = m.group(1);
        return ns == null ? false : ns.equals(NS); // un-namespaced ids are vanilla in model/blockstate JSON
    }

    private static String path(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    private static List<Path> list(String dir) throws IOException {
        Path d = ASSETS.resolve(dir);
        if (!Files.isDirectory(d)) return List.of();
        try (Stream<Path> s = Files.walk(d)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private JsonElement parse(Path p) {
        try {
            return JsonParser.parseString(Files.readString(p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            problems.add(rel(p) + ": invalid JSON: " + e.getMessage());
            return new JsonObject();
        }
    }

    private static String rel(Path p) {
        return ASSETS.relativize(p).toString().replace('\\', '/');
    }
}

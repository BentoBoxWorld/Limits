package world.bentobox.limits.objects;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.NamespacedKey;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Backwards-compatible Gson {@link TypeAdapter} for {@code Map<NamespacedKey, Integer>}.
 *
 * <p>Older versions of Limits stored block counts and limits as
 * {@code Map<Material, Integer>}, which Gson serialized as a JSON object whose keys
 * were the Material enum names, e.g.:
 * <pre>{@code
 * "blockCounts": {
 *   "POLISHED_DIORITE": 11,
 *   "DIRT": 459
 * }
 * }</pre>
 *
 * <p>The fields were later changed to {@code Map<NamespacedKey, Integer>}.
 * Without an adapter, Gson's reflective handling of NamespacedKey expects an
 * object form ({@code {"namespace":"minecraft","key":"dirt"}}) for each key, so
 * loading any pre-existing database file fails with
 * {@code Expected BEGIN_OBJECT but was STRING ... path $.blockCounts.}.
 *
 * <p>This adapter reads three input shapes and always writes the simple,
 * compact one:
 * <ul>
 *   <li>Legacy bare enum names ({@code "POLISHED_DIORITE"}) -- mapped to
 *       {@code minecraft:polished_diorite}.</li>
 *   <li>Already-namespaced strings ({@code "minecraft:dirt"}).</li>
 *   <li>The complex array form Gson produces with
 *       {@code enableComplexMapKeySerialization()}: an array of
 *       {@code [{"namespace":"...","key":"..."}, count]} pairs. Handled so any
 *       file written by the broken intermediate version can still be read.</li>
 * </ul>
 */
public class NamespacedKeyMapAdapter extends TypeAdapter<Map<NamespacedKey, Integer>> {

    @Override
    public void write(JsonWriter out, Map<NamespacedKey, Integer> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        for (Map.Entry<NamespacedKey, Integer> entry : value.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            // Writes as "minecraft:dirt"
            out.name(entry.getKey().toString());
            out.value(entry.getValue());
        }
        out.endObject();
    }

    @Override
    public Map<NamespacedKey, Integer> read(JsonReader in) throws IOException {
        Map<NamespacedKey, Integer> result = new HashMap<>();
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return result;
        }
        if (token == JsonToken.BEGIN_OBJECT) {
            // Legacy form (Material enum names) or current clean form (namespaced strings).
            in.beginObject();
            while (in.hasNext()) {
                String rawKey = in.nextName();
                Integer count = readIntOrNull(in);
                NamespacedKey key = parseKey(rawKey);
                if (key != null && count != null) {
                    result.put(key, count);
                }
            }
            in.endObject();
            return result;
        }
        if (token == JsonToken.BEGIN_ARRAY) {
            // Complex map form: [[{"namespace":..,"key":..}, count], ...]
            in.beginArray();
            while (in.hasNext()) {
                in.beginArray();
                NamespacedKey key = readKeyObject(in);
                Integer count = readIntOrNull(in);
                if (key != null && count != null) {
                    result.put(key, count);
                }
                in.endArray();
            }
            in.endArray();
            return result;
        }
        // Unknown shape -- skip and return what we have.
        in.skipValue();
        return result;
    }

    private static NamespacedKey parseKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (raw.indexOf(':') >= 0) {
            return NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        }
        // Bare legacy enum name like "POLISHED_DIORITE"
        try {
            return new NamespacedKey(NamespacedKey.MINECRAFT, raw.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer readIntOrNull(JsonReader in) throws IOException {
        JsonToken t = in.peek();
        if (t == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return in.nextInt();
    }

    private static NamespacedKey readKeyObject(JsonReader in) throws IOException {
        JsonToken t = in.peek();
        if (t == JsonToken.STRING) {
            return parseKey(in.nextString());
        }
        if (t != JsonToken.BEGIN_OBJECT) {
            in.skipValue();
            return null;
        }
        String namespace = null;
        String key = null;
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            if ("namespace".equals(name)) {
                namespace = in.nextString();
            } else if ("key".equals(name)) {
                key = in.nextString();
            } else {
                in.skipValue();
            }
        }
        in.endObject();
        if (namespace == null || key == null) {
            return null;
        }
        try {
            return new NamespacedKey(namespace.toLowerCase(Locale.ROOT), key.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

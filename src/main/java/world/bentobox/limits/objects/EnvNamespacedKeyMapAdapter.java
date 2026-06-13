package world.bentobox.limits.objects;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.World.Environment;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Gson {@link TypeAdapter} for {@code Map<Environment, Map<NamespacedKey, Integer>>}.
 * Outer keys are {@link Environment} names; inner keys are written/read as
 * "namespace:key" strings via the same logic as {@link NamespacedKeyMapAdapter}.
 */
public class EnvNamespacedKeyMapAdapter extends TypeAdapter<Map<Environment, Map<NamespacedKey, Integer>>> {

    @Override
    public void write(JsonWriter out, Map<Environment, Map<NamespacedKey, Integer>> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        for (Map.Entry<Environment, Map<NamespacedKey, Integer>> envEntry : value.entrySet()) {
            if (envEntry.getKey() == null || envEntry.getValue() == null || envEntry.getValue().isEmpty()) {
                continue;
            }
            out.name(envEntry.getKey().name());
            out.beginObject();
            for (Map.Entry<NamespacedKey, Integer> entry : envEntry.getValue().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                out.name(entry.getKey().toString());
                out.value(entry.getValue());
            }
            out.endObject();
        }
        out.endObject();
    }

    @Override
    public Map<Environment, Map<NamespacedKey, Integer>> read(JsonReader in) throws IOException {
        Map<Environment, Map<NamespacedKey, Integer>> result = new EnumMap<>(Environment.class);
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return result;
        }
        in.beginObject();
        while (in.hasNext()) {
            String envName = in.nextName();
            Environment env = parseEnvironment(envName);
            Map<NamespacedKey, Integer> inner = readInner(in);
            if (env != null && !inner.isEmpty()) {
                result.put(env, inner);
            }
        }
        in.endObject();
        return result;
    }

    private static Environment parseEnvironment(String raw) {
        if (raw == null) return null;
        try {
            return Environment.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<NamespacedKey, Integer> readInner(JsonReader in) throws IOException {
        Map<NamespacedKey, Integer> inner = new HashMap<>();
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return inner;
        }
        in.beginObject();
        while (in.hasNext()) {
            String rawKey = in.nextName();
            Integer value = readIntOrNull(in);
            NamespacedKey key = parseKey(rawKey);
            if (key != null && value != null) {
                inner.put(key, value);
            }
        }
        in.endObject();
        return inner;
    }

    private static NamespacedKey parseKey(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (raw.indexOf(':') >= 0) {
            return NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        }
        try {
            return new NamespacedKey(NamespacedKey.MINECRAFT, raw.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer readIntOrNull(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return in.nextInt();
    }
}

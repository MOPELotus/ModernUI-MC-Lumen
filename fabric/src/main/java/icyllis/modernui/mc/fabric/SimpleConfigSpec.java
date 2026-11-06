/*
 * Modern UI.
 * Copyright (C) 2026 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.fabric;

import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static icyllis.modernui.mc.ModernUIMod.LOGGER;
import static icyllis.modernui.mc.ModernUIMod.MARKER;

final class SimpleConfigSpec {

    private final Path file;
    private final List<ConfigValue<?>> values;
    private final Map<List<String>, ValueSpec> specs;
    @Nullable
    private Document document;
    private boolean writable = true;
    private final Map<List<String>, String> loadedValues = new HashMap<>();

    private SimpleConfigSpec(@Nonnull Path file,
                             @Nonnull List<ConfigValue<?>> values,
                             @Nonnull Map<List<String>, ValueSpec> specs) {
        this.file = file;
        this.values = List.copyOf(values);
        this.specs = Map.copyOf(specs);
    }

    public Map<List<String>, ValueSpec> getSpec() {
        return specs;
    }

    public void load() {
        Map<List<String>, String> rawValues = Collections.emptyMap();
        document = null;
        writable = false;
        try {
            if (Files.notExists(file)) {
                writable = true;
            } else {
                document = Document.parse(Files.readString(file, StandardCharsets.UTF_8));
                rawValues = document.values;
                writable = true;
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn(MARKER, "Failed to read config {}, defaults will be used; original file will not be overwritten", file, e);
        }
        loadedValues.clear();
        for (ConfigValue<?> value : values) {
            value.load(rawValues.get(value.getPath()));
            loadedValues.put(value.getPath(), formatValue(value.get()));
        }
    }

    public void save() {
        if (!writable) {
            LOGGER.warn(MARKER, "Not overwriting unreadable or unsupported config {}", file);
            return;
        }
        Path temporary = null;
        try {
            // Do not replace edits made by another application after this spec was loaded.
            if (document == null ? !Files.notExists(file)
                    : !Files.readString(file, StandardCharsets.UTF_8).equals(document.source)) {
                throw new IOException("Config changed on disk; reload before saving");
            }
            String output = document == null ? writeToml() : document.update(values, loadedValues);
            Document replacement = Document.parse(output);
            Path absolute = file.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
            Files.writeString(temporary, output, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            document = replacement;
            for (ConfigValue<?> value : values) {
                loadedValues.put(value.getPath(), formatValue(value.get()));
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error(MARKER, "Failed to save config {}", file, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    LOGGER.warn(MARKER, "Failed to remove temporary config {}", temporary, e);
                }
            }
        }
    }

    private String writeToml() {
        StringBuilder builder = new StringBuilder();
        List<String> lastSection = Collections.emptyList();
        boolean first = true;
        for (ConfigValue<?> value : values) {
            List<String> path = value.getPath();
            List<String> section = path.subList(0, path.size() - 1);
            if (!section.equals(lastSection)) {
                if (!first) {
                    builder.append('\n');
                }
                for (String comment : value.spec.comments) {
                    builder.append("# ").append(comment).append('\n');
                }
                builder.append('[').append(formatPath(section)).append("]\n");
                lastSection = section;
                first = false;
            } else if (!first) {
                builder.append('\n');
            }
            for (String comment : value.spec.comments) {
                builder.append("# ").append(comment).append('\n');
            }
            builder.append(formatKey(path.get(path.size() - 1)))
                    .append(" = ")
                    .append(formatValue(value.get()))
                    .append('\n');
            first = false;
        }
        return builder.toString();
    }

    /**
     * The subset written by this spec and NightConfig: regular tables, bare/quoted keys,
     * single-line basic/literal strings, booleans, decimal numbers and flat string arrays.
     * Unsupported TOML is deliberately rejected, never silently rewritten as defaults.
     */
    private static final class Document {
        private final String source;
        private final Map<List<String>, String> values = new LinkedHashMap<>();
        private final List<Entry> entries = new ArrayList<>();
        private final Map<List<String>, Integer> sectionEnds = new LinkedHashMap<>();

        private Document(String source) {
            this.source = source;
        }

        static Document parse(String source) {
            Document result = new Document(source);
            Cursor cursor = new Cursor(source);
            List<String> section = List.of();
            Set<List<String>> tables = new HashSet<>();
            while (true) {
                cursor.space(true);
                if (cursor.end()) break;
                int statement = cursor.pos;
                if (cursor.peek() == '[') {
                    result.sectionEnds.put(section, statement);
                    cursor.pos++;
                    ArrayList<String> parts = new ArrayList<>();
                    do {
                        cursor.space(false);
                        parts.add(cursor.key());
                        cursor.space(false);
                        if (cursor.peek() != '.') break;
                        cursor.pos++;
                    } while (true);
                    cursor.expect(']');
                    section = List.copyOf(parts);
                    if (!tables.add(section)) throw cursor.error("Duplicate table");
                    cursor.lineEnd();
                } else {
                    String key = cursor.key();
                    cursor.space(false);
                    cursor.expect('=');
                    cursor.space(false);
                    int start = cursor.pos;
                    cursor.value();
                    int end = cursor.pos;
                    cursor.lineEnd();
                    ArrayList<String> parts = new ArrayList<>(section);
                    parts.add(key);
                    List<String> path = List.copyOf(parts);
                    // TOML does not permit a scalar/array key to also be a table.
                    for (List<String> existing : result.values.keySet()) {
                        if (prefix(existing, path) || prefix(path, existing)) throw cursor.error("Conflicting key");
                    }
                    if (result.values.putIfAbsent(path, source.substring(start, end)) != null) {
                        throw cursor.error("Duplicate key");
                    }
                    result.entries.add(new Entry(path, start, end));
                }
            }
            result.sectionEnds.put(section, source.length());
            for (List<String> table : tables) {
                for (List<String> path : result.values.keySet()) {
                    if (prefix(path, table)) throw cursor.error("Key conflicts with table");
                }
            }
            return result;
        }

        private static boolean prefix(List<String> first, List<String> second) {
            return first.size() <= second.size() && second.subList(0, first.size()).equals(first);
        }

        String update(List<ConfigValue<?>> config, Map<List<String>, String> loaded) {
            record Edit(int start, int end, String text) {}
            List<Edit> edits = new ArrayList<>();
            Map<List<String>, ConfigValue<?>> known = new HashMap<>();
            for (ConfigValue<?> value : config) known.put(value.getPath(), value);
            for (Entry entry : entries) {
                ConfigValue<?> value = known.get(entry.path);
                if (value != null && !formatValue(value.get()).equals(loaded.get(entry.path))) {
                    edits.add(new Edit(entry.start, entry.end, formatValue(value.get())));
                }
            }
            Map<List<String>, StringBuilder> missing = new LinkedHashMap<>();
            for (ConfigValue<?> value : config) {
                if (values.containsKey(value.getPath())) continue;
                List<String> path = value.getPath();
                List<String> section = path.subList(0, path.size() - 1);
                StringBuilder builder = missing.computeIfAbsent(section, ignored -> new StringBuilder("\n"));
                for (String comment : value.spec.comments) builder.append("# ").append(comment).append('\n');
                builder.append(formatKey(path.get(path.size() - 1))).append(" = ")
                        .append(formatValue(value.get())).append('\n');
            }
            StringBuilder suffix = new StringBuilder();
            for (var item : missing.entrySet()) {
                Integer position = sectionEnds.get(item.getKey());
                if (position != null) {
                    edits.add(new Edit(position, position, item.getValue().toString()));
                } else {
                    suffix.append("\n[").append(formatPath(item.getKey())).append("]\n").append(item.getValue());
                }
            }
            StringBuilder output = new StringBuilder(source);
            edits.sort(Comparator.comparingInt(Edit::start).reversed());
            for (Edit edit : edits) output.replace(edit.start, edit.end, edit.text);
            output.append(suffix);
            // Validate before touching the user's file, including newly introduced table conflicts.
            parse(output.toString());
            return output.toString();
        }
    }

    private record Entry(List<String> path, int start, int end) {}

    private static String formatKey(String key) {
        return key.matches("[A-Za-z0-9_-]+") ? key : "\"" + escape(key) + "\"";
    }

    private static String formatPath(List<String> path) {
        StringJoiner joiner = new StringJoiner(".");
        for (String key : path) joiner.add(formatKey(key));
        return joiner.toString();
    }

    private static final class Cursor {
        private final String source;
        private int pos;

        Cursor(String source) { this.source = source; }
        boolean end() { return pos == source.length(); }
        char peek() { return end() ? '\0' : source.charAt(pos); }
        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + pos);
        }
        void expect(char expected) {
            if (peek() != expected) throw error("Expected " + expected);
            pos++;
        }
        void space(boolean lines) {
            while (!end()) {
                char c = peek();
                if (c == ' ' || c == '\t' || (lines && (c == '\n' || c == '\r'))) pos++;
                else if (lines && c == '#') {
                    while (!end() && peek() != '\n' && peek() != '\r') pos++;
                } else break;
            }
        }
        void lineEnd() {
            space(false);
            if (peek() == '#') while (!end() && peek() != '\n' && peek() != '\r') pos++;
            if (!end() && peek() != '\n' && peek() != '\r') throw error("Unexpected text after value");
        }
        String key() {
            if (peek() == '\'' || peek() == '"') return string();
            int start = pos;
            while (!end() && (peek() >= 'A' && peek() <= 'Z' || peek() >= 'a' && peek() <= 'z'
                    || peek() >= '0' && peek() <= '9' || peek() == '_' || peek() == '-')) pos++;
            if (start == pos) throw error("Expected key");
            return source.substring(start, pos);
        }
        Object value() {
            if (peek() == '"' || peek() == '\'') return string();
            if (peek() == '[') return strings();
            int start = pos;
            while (!end() && peek() != '#' && peek() != '\n' && peek() != '\r'
                    && peek() != ' ' && peek() != '\t' && peek() != ',' && peek() != ']') pos++;
            String token = source.substring(start, pos);
            if (!token.equals("true") && !token.equals("false")
                    && !token.matches("[+-]?(?:0|[1-9](?:_?[0-9])*)(?:\\.[0-9](?:_?[0-9])*)?(?:[eE][+-]?[0-9](?:_?[0-9])*)?")) {
                throw error("Unsupported or malformed value");
            }
            return token;
        }
        List<String> strings() {
            expect('[');
            ArrayList<String> values = new ArrayList<>();
            space(true);
            if (peek() == ']') { pos++; return List.of(); }
            while (true) {
                if (peek() != '"' && peek() != '\'') throw error("Expected quoted string in array");
                values.add(string());
                space(true);
                if (peek() == ']') { pos++; break; }
                expect(',');
                space(true);
                if (peek() == ']') { pos++; break; }
            }
            return List.copyOf(values);
        }
        String string() {
            char quote = peek();
            if (quote != '"' && quote != '\'') throw error("Expected quoted string");
            pos++;
            StringBuilder value = new StringBuilder();
            while (!end()) {
                char c = source.charAt(pos++);
                if (c == quote) return value.toString();
                if (c < 0x20 && c != '\t' || c == 0x7f) throw error("Control character in string");
                if (c == '\\' && quote == '"') {
                    if (end()) throw error("Unterminated escape");
                    char escaped = source.charAt(pos++);
                    switch (escaped) {
                        case 'b' -> value.append('\b');
                        case 't' -> value.append('\t');
                        case 'n' -> value.append('\n');
                        case 'f' -> value.append('\f');
                        case 'r' -> value.append('\r');
                        case '"', '\\' -> value.append(escaped);
                        case 'u', 'U' -> {
                            int digits = escaped == 'u' ? 4 : 8;
                            if (source.length() - pos < digits) throw error("Incomplete Unicode escape");
                            long code = 0;
                            for (int i = 0; i < digits; i++) {
                                int digit = Character.digit(source.charAt(pos++), 16);
                                if (digit < 0) throw error("Invalid Unicode escape");
                                code = code * 16 + digit;
                            }
                            if (code > 0x10ffff || code >= 0xd800 && code <= 0xdfff) throw error("Invalid Unicode scalar");
                            value.appendCodePoint((int) code);
                        }
                        default -> throw error("Unsupported escape");
                    }
                } else value.append(c);
            }
            throw error("Unterminated string");
        }
    }

    private static String formatValue(@Nullable Object value) {
        if (value instanceof List<?> list) {
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            for (Object element : list) {
                joiner.add(formatValue(element));
            }
            return joiner.toString();
        }
        if (value instanceof String string) {
            return "\"" + escape(string) + "\"";
        }
        if (value instanceof Enum<?> enumValue) {
            return "\"" + enumValue.name() + "\"";
        }
        return String.valueOf(value);
    }

    private static String escape(@Nonnull String string) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\b' -> result.append("\\b");
                case '\t' -> result.append("\\t");
                case '\n' -> result.append("\\n");
                case '\f' -> result.append("\\f");
                case '\r' -> result.append("\\r");
                default -> {
                    if (c < 0x20 || c == 0x7f) result.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
                    else result.append(c);
                }
            }
        }
        return result.toString();
    }

    private static String unquote(@Nonnull String raw) {
        Cursor cursor = new Cursor(raw);
        String value = cursor.string();
        if (!cursor.end()) throw cursor.error("Unexpected text after string");
        return value;
    }

    private static List<String> parseStringList(@Nonnull String raw) {
        Cursor cursor = new Cursor(raw);
        List<String> values = cursor.strings();
        if (!cursor.end()) throw cursor.error("Unexpected text after array");
        return values;
    }

    static final class Builder {

        private final ArrayList<ConfigValue<?>> values = new ArrayList<>();
        private final HashMap<List<String>, ValueSpec> specs = new HashMap<>();
        private final ArrayList<String> section = new ArrayList<>();
        private List<String> pendingComments = List.of();

        public Builder comment(@Nonnull String... comments) {
            pendingComments = Arrays.asList(comments);
            return this;
        }

        public Builder push(@Nonnull String name) {
            section.add(name);
            pendingComments = List.of();
            return this;
        }

        public Builder pop() {
            section.remove(section.size() - 1);
            pendingComments = List.of();
            return this;
        }

        public BooleanValue define(@Nonnull String name, boolean defaultValue) {
            return add(new BooleanValue(path(name), defaultValue, spec(null, null)));
        }

        public ConfigValue<String> define(@Nonnull String name, @Nonnull String defaultValue) {
            return add(new ConfigValue<>(path(name), defaultValue, spec(null, null), String.class, null));
        }

        public IntValue defineInRange(@Nonnull String name, int defaultValue, int min, int max) {
            return add(new IntValue(path(name), defaultValue, spec(min, max)));
        }

        public DoubleValue defineInRange(@Nonnull String name, double defaultValue, double min, double max) {
            return add(new DoubleValue(path(name), defaultValue, spec(min, max)));
        }

        public <E extends Enum<E>> EnumValue<E> defineEnum(@Nonnull String name, @Nonnull E defaultValue) {
            return add(new EnumValue<>(path(name), defaultValue, spec(null, null), defaultValue.getDeclaringClass()));
        }

        public ConfigValue<List<? extends String>> defineList(@Nonnull String name,
                                                              @Nonnull Supplier<List<String>> defaultSupplier,
                                                              @Nonnull Predicate<Object> validator) {
            return add(new ConfigValue<>(path(name), List.copyOf(defaultSupplier.get()),
                    spec(null, null), List.class, validator));
        }

        public SimpleConfigSpec build(@Nonnull Path file) {
            return new SimpleConfigSpec(file, values, specs);
        }

        private List<String> path(@Nonnull String name) {
            ArrayList<String> path = new ArrayList<>(section);
            path.add(name);
            return List.copyOf(path);
        }

        private ValueSpec spec(@Nullable Object min, @Nullable Object max) {
            ValueSpec spec = new ValueSpec(pendingComments, min, max);
            pendingComments = List.of();
            return spec;
        }

        private <T extends ConfigValue<?>> T add(@Nonnull T value) {
            values.add(value);
            specs.put(value.getPath(), value.spec);
            return value;
        }
    }

    static class ConfigValue<T> {

        private final List<String> path;
        private final T defaultValue;
        final ValueSpec spec;
        private final Class<?> valueType;
        @Nullable
        private final Predicate<Object> validator;
        private T value;

        private ConfigValue(@Nonnull List<String> path,
                            @Nonnull T defaultValue,
                            @Nonnull ValueSpec spec,
                            @Nonnull Class<?> valueType,
                            @Nullable Predicate<Object> validator) {
            this.path = path;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.spec = spec;
            this.valueType = valueType;
            this.validator = validator;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = sanitize(value);
        }

        public T getDefault() {
            return defaultValue;
        }

        public List<String> getPath() {
            return path;
        }

        @SuppressWarnings("unchecked")
        private void load(@Nullable String raw) {
            if (raw == null) {
                value = defaultValue;
                return;
            }
            try {
                Object parsed;
                if (valueType == Boolean.class) {
                    if (!raw.equals("true") && !raw.equals("false")) throw new IllegalArgumentException("Expected boolean");
                    parsed = Boolean.valueOf(raw);
                } else if (valueType == Integer.class) {
                    parsed = Integer.parseInt(raw.replace("_", ""));
                } else if (valueType == Double.class) {
                    parsed = Double.parseDouble(raw.replace("_", ""));
                } else if (valueType == String.class) {
                    parsed = unquote(raw);
                } else if (valueType == List.class) {
                    parsed = parseStringList(raw);
                } else if (Enum.class.isAssignableFrom(valueType)) {
                    parsed = Enum.valueOf((Class<? extends Enum>) valueType.asSubclass(Enum.class), unquote(raw));
                } else {
                    parsed = defaultValue;
                }
                value = sanitize((T) parsed);
            } catch (RuntimeException e) {
                LOGGER.warn(MARKER, "Wrong config value for {}, using default {}", String.join(".", path),
                        defaultValue, e);
                value = defaultValue;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private T sanitize(@Nullable T candidate) {
            if (candidate == null) {
                return defaultValue;
            }
            Object value = candidate;
            if (spec.min instanceof Number min && spec.max instanceof Number max && value instanceof Number number) {
                if (valueType == Integer.class) {
                    int n = Math.max(min.intValue(), Math.min(max.intValue(), number.intValue()));
                    value = n;
                } else if (valueType == Double.class) {
                    if (!Double.isFinite(number.doubleValue())) return defaultValue;
                    double n = Math.max(min.doubleValue(), Math.min(max.doubleValue(), number.doubleValue()));
                    value = n;
                }
            }
            if (validator != null && value instanceof List<?> list) {
                for (Object element : list) {
                    if (!validator.test(element)) {
                        return defaultValue;
                    }
                }
            }
            if (value instanceof List<?> list) value = List.copyOf(list);
            return (T) value;
        }
    }

    static final class BooleanValue extends ConfigValue<Boolean> {

        private BooleanValue(@Nonnull List<String> path, boolean defaultValue, @Nonnull ValueSpec spec) {
            super(path, defaultValue, spec, Boolean.class, null);
        }
    }

    static final class IntValue extends ConfigValue<Integer> {

        private IntValue(@Nonnull List<String> path, int defaultValue, @Nonnull ValueSpec spec) {
            super(path, defaultValue, spec, Integer.class, null);
        }
    }

    static final class DoubleValue extends ConfigValue<Double> {

        private DoubleValue(@Nonnull List<String> path, double defaultValue, @Nonnull ValueSpec spec) {
            super(path, defaultValue, spec, Double.class, null);
        }
    }

    static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {

        private EnumValue(@Nonnull List<String> path,
                          @Nonnull E defaultValue,
                          @Nonnull ValueSpec spec,
                          @Nonnull Class<E> enumClass) {
            super(path, defaultValue, spec, enumClass, null);
        }
    }

    static final class ValueSpec {

        private final List<String> comments;
        @Nullable
        private final Object min;
        @Nullable
        private final Object max;

        private ValueSpec(@Nonnull List<String> comments, @Nullable Object min, @Nullable Object max) {
            this.comments = List.copyOf(comments);
            this.min = min;
            this.max = max;
        }

        @Nullable
        public Object getMin() {
            return min;
        }

        @Nullable
        public Object getMax() {
            return max;
        }
    }
}

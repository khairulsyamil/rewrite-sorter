package me.khairulsyamil.openrewrite.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.PathUtils;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class JsonSorter extends Recipe {
    @Option(displayName = "File pattern",
            description = "A glob expression representing a file path to search for (relative to the project root). Blank/null matches all.",
            required = false,
            example = "**/{messages,errors}.properties")
    @Nullable
    String filePattern;

    private static final Logger logger = LoggerFactory.getLogger(JsonSorter.class);

    @JsonCreator
    public JsonSorter(@Nullable @JsonProperty("filePattern") String filePattern) {
        this.filePattern = filePattern;
    }

    @Override
    public @NonNull String getDisplayName() {
        return "JsonSorter";
    }

    @Override
    public @NonNull String getDescription() {
        return "Reformat a json file to remove duplicates and sort by label. All comments will be removed as well.";
    }

    @Override
    public String toString() {
        return "JsonSorter{}";
    }

    @Override
    public boolean causesAnotherCycle() {
        return true;
    }

    @Override
    public @NonNull JsonIsoVisitor<ExecutionContext> getVisitor() {
        return new JsonSorterVisitor(filePattern);
    }

    public static class JsonSorterVisitor extends JsonIsoVisitor<ExecutionContext> {
        private final String filePattern;
        private final Space beforeDelimiter = Space.build(" ", Collections.emptyList());
        private final Space lastMember = Space.build("\n", Collections.emptyList());

        public JsonSorterVisitor(String filePattern) {
            this.filePattern = filePattern;
        }

        @Override
        public @NonNull Json.JsonObject visitObject(@NonNull Json.JsonObject obj, @NonNull ExecutionContext executionContext) {
            obj = super.visitObject(obj, executionContext);

            if (CollectionUtils.isEmpty(obj.getMembers())) {
                return obj;
            }

            boolean requiresChange = false;
            Json.Member previous = null;

            for (Json c: obj.getMembers()) {
                if (!(c instanceof Json.Member)) {
                    continue;
                }

                if (previous != null) {
                    Json.Member bMem = (Json.Member) c;

                    String aKey = previous.getKey().toString();
                    String bKey = bMem.getKey().toString();

                    if (StringUtils.compareIgnoreCase(aKey, bKey) > 0) {
                        requiresChange = true;
                        break;
                    }
                }

                previous = (Json.Member) c;
            }

            if (!requiresChange) {
                return obj;
            }

            Json.Member originalFirstMember = (Json.Member) obj.getMembers().get(0);

            Map<String, String> seen = new HashMap<>(obj.getMembers().size());

            List<JsonRightPadded<Json>> newMembers = new ArrayList<>(obj.getMembers()).stream()
                    .filter(c -> {
                        Json.Member m = (Json.Member) c;
                        JsonKey k = m.getKey();
                        JsonValue v = m.getValue();

                        String key = k.toString();
                        String value = v.getId().toString();
                        
                        if (v instanceof Json.Literal) {
                            value = ((Json.Literal) v).getValue().toString();
                        }

                        String seenValue = seen.get(key);

                        if (seenValue == null) {
                            // Key does not already exists.
                            seen.put(key, value);

                            return true;

                        } else if (StringUtils.equals(seenValue, value)) {
                            // Key already exists and the value is the same.
                            logger.warn("Duplicate key found and removed\nKey: {}\nValue: {}",
                                    key,
                                    value);

                            return false;

                        } else {
                            // Key already exists and the value is not the same.
                            logger.warn("Duplicate key found\nKey: {}\nValue 1: {}\nValue 2: {}",
                                    key,
                                    seenValue,
                                    value);

                            return true;
                        }
                    })
                    .sorted((a, b) -> {
                        Json.Member am = (Json.Member) a;
                        Json.Member bm = (Json.Member) b;

                        String ak = am.getKey().toString();
                        String bk = bm.getKey().toString();

                        return StringUtils.compareIgnoreCase(ak, bk);
                    })
                    .map(c -> {
                        Json.Member m = (Json.Member) c;
                        JsonKey key = m.getKey();
                        JsonValue v = m.getValue();

                        m = m.withKey(key.withPrefix(originalFirstMember.getPrefix()));
                        m = m.withValue(v.withPrefix(beforeDelimiter));
                        m = m.withPrefix(Space.EMPTY);

                        return JsonRightPadded.build((Json) m);
                    })
                    .collect(Collectors.toList());

            String originalWhitespace = originalFirstMember.getPrefix().getWhitespace();
            originalWhitespace = originalWhitespace.substring(0, Math.max(0, originalWhitespace.length() - 4));

            Space lastMemberAfter = lastMember.withWhitespace(originalWhitespace);

            int lastIdx = newMembers.size() - 1;
            newMembers.set(lastIdx, newMembers.get(lastIdx).withAfter(lastMemberAfter));

            obj = new Json.JsonObject(obj.getId(), obj.getPrefix(), obj.getMarkers(), newMembers);

            return obj;
        }

        @Override
        public @NonNull Json.Document visitDocument(@NonNull Json.Document file, @NonNull ExecutionContext ctx) {
            if (StringUtils.isNotBlank(filePattern)
                    && !PathUtils.matchesGlob(file.getSourcePath().getFileName(), filePattern)) {
                return file;
            }

            return super.visitDocument(file, ctx);
        }
    }
}

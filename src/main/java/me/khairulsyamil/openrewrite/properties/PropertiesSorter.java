package me.khairulsyamil.openrewrite.properties;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.openrewrite.Option;
import org.openrewrite.PathUtils;
import org.openrewrite.internal.lang.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.StringUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropertiesSorter extends Recipe {
    @Option(displayName = "File pattern",
            description = "A glob expression representing a file path to search for (relative to the project root). Blank/null matches all.",
            required = false,
            example = "**/{messages,errors}.properties")
    @Nullable
    String filePattern;

    private static final Logger logger = LoggerFactory.getLogger(PropertiesSorter.class);

    @JsonCreator
    public PropertiesSorter(@Nullable @JsonProperty("filePattern") String filePattern) {
        this.filePattern = filePattern;
    }

    @Override
    public @NonNull String getDisplayName() {
        return "PropertiesSorter";
    }

    @Override
    public @NonNull String getDescription() {
        return "Reformat a properties file to remove duplicates and sort by label. All comments will be removed as well.";
    }

    @Override
    public String toString() {
        return "PropertiesSorter{}";
    }

    @Override
    public @NonNull PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesSorterVisitor(filePattern);
    }

    public static class PropertiesSorterVisitor extends PropertiesIsoVisitor<org.openrewrite.ExecutionContext> {
        private final String filePattern;

        public PropertiesSorterVisitor(String filePattern) {
            this.filePattern = filePattern;
        }

        @Override
        public @NonNull Properties.File visitFile(@NonNull Properties.File file, @NonNull ExecutionContext ctx) {
            Properties.File p = super.visitFile(file, ctx);

            if (StringUtils.isNotBlank(filePattern)
                    && !PathUtils.matchesGlob(p.getSourcePath().getFileName(), filePattern)) {
                return p;
            }

            boolean requiresChange = false;
            Properties.Content previous = null;

            for (Properties.Content c: p.getContent()) {
                if (c instanceof Properties.Comment) {
                    requiresChange = true;
                    break;
                }

                if (previous != null) {
                    Properties.Entry aProp = (Properties.Entry) previous;
                    Properties.Entry bProp = (Properties.Entry) c;

                    String aLabel = aProp.getKey();
                    String bLabel = bProp.getKey();

                    if (StringUtils.compareIgnoreCase(aLabel, bLabel) > 0) {
                        requiresChange = true;
                        break;
                    }

                    if (!StringUtils.equals(bProp.getPrefix(), "\n")) {
                        requiresChange = true;
                        break;
                    }
                }

                previous = c;
            }

            if (!requiresChange) {
                return p;
            }

            List<Properties.Content> sorted = p.getContent().stream()
                    .filter(c -> c instanceof Properties.Entry)
                    .sorted((a, b) -> {
                        Properties.Entry aProp = (Properties.Entry) a;
                        Properties.Entry bProp = (Properties.Entry) b;

                        String aLabel = aProp.getKey();
                        String bLabel = bProp.getKey();

                        return StringUtils.compareIgnoreCase(aLabel, bLabel);
                    })
                    .toList();

            // Set prefix.
            sorted = ListUtils.map(sorted, (i, c) -> {
                Properties.Entry e = (Properties.Entry) c;

                if (i == 0) {
                    e = e.withPrefix("");
                }
                else if (!StringUtils.equals(c.getPrefix(), "\n")) {
                    e = e.withPrefix("\n");
                }

                return e;
            });

            // Trim values.
            sorted = ListUtils.map(sorted, (i, c) -> {
                Properties.Entry e = (Properties.Entry) c;

                String value = StringUtils.trim(e.getValue().getText());

                return e.withValue(e.getValue().withText(value));
            });

            // Remove duplicates.
            Map<String, String> seen = new HashMap<>(sorted.size());
            sorted = sorted.stream()
                    .filter(c -> {
                        Properties.Entry e = (Properties.Entry) c;

                        String key = e.getKey();
                        String value = e.getValue().getText();

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
                    .toList();

            p = p.withContent(sorted);

            return p;
        }
    }
}

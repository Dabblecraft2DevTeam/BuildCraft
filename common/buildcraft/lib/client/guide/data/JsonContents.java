package buildcraft.lib.client.guide.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.annotations.SerializedName;

import buildcraft.api.core.BCLog;

public class JsonContents {
    public final JsonEntry[] contents;

    @SerializedName("default_tags")
    public final JsonTypeTags defaultTags;

    @SerializedName("entry_mask")
    public final String entryMask;

    public JsonContents(String entryMask, JsonTypeTags defaultTags, JsonEntry[] contents) {
        this.contents = contents;
        this.defaultTags = defaultTags;
        this.entryMask = entryMask;
    }

    public JsonContents inheritMissingTags() {
        final JsonTypeTags tags;
        if (defaultTags == null) {
            tags = JsonTypeTags.EMPTY;
        } else {
            tags = defaultTags.inheritMissingTags(JsonTypeTags.EMPTY);
        }

        JsonEntry[] entries = new JsonEntry[contents.length];
        int j = 0;
        for (int i = 0; i < contents.length; i++) {
            JsonEntry entry = contents[i];
            if (entry != null) {
                entries[j] = entry.inherit(tags, entryMask);
                j++;
            }
        }
        entries = Arrays.copyOf(entries, j);
        return new JsonContents(entryMask, tags, entries);
    }

    public static List<JsonEntry> mergeAll(Iterable<JsonContents> all) {
        List<JsonEntry> entries = new ArrayList<>();

        for (JsonContents contents : all) {
            if (contents == null) {
                continue;
            }
            contents = contents.inheritMissingTags();
            for (JsonEntry entry : contents.contents) {
                entries.add(entry);
            }
        }

        return entries;
    }

    public void printContents() {
        BCLog.logger.info("{");
        if (defaultTags == null) {
            BCLog.logger.info("  default_tags = null");
        } else {
            BCLog.logger.info("  default_tags = {");
            defaultTags.printContents(2);
            BCLog.logger.info("  },");
        }
        if (contents == null) {
            BCLog.logger.info("  contents = null");
        } else {
            BCLog.logger.info("  contents = [");
            for (int i = 0; i < contents.length; i++) {
                JsonEntry entry = contents[i];
                boolean last = i + 1 == contents.length;
                if (entry == null) {
                    if (last) {
                        BCLog.logger.info("    null");
                    } else {
                        BCLog.logger.info("    null,");
                    }
                } else {
                    BCLog.logger.info("    {");
                    entry.printContents();
                    if (last) {
                        BCLog.logger.info("    }");
                    } else {
                        BCLog.logger.info("    },");
                    }
                }
            }
            BCLog.logger.info("  ]");
        }
        BCLog.logger.info("}");
    }
}

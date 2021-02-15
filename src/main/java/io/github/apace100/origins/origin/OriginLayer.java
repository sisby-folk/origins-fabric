package io.github.apace100.origins.origin;

import com.google.common.collect.Lists;
import com.google.gson.*;
import io.github.apace100.origins.power.factory.condition.ConditionFactory;
import io.github.apace100.origins.power.factory.condition.ConditionTypes;
import io.github.apace100.origins.util.SerializableData;
import io.github.apace100.origins.util.SerializableDataType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class OriginLayer implements Comparable<OriginLayer> {

    private int order;
    private Identifier identifier;
    private List<ConditionedOrigin> conditionedOrigins;
    private boolean enabled = false;

    private String nameTranslationKey;
    private String missingOriginNameTranslationKey;
    private String missingOriginDescriptionTranslationKey;

    private boolean isRandomAllowed = false;
    private boolean doesRandomAllowUnchoosable = false;
    private List<Identifier> originsExcludedFromRandom = null;

    public String getOrCreateTranslationKey() {
        if(nameTranslationKey == null || nameTranslationKey.isEmpty()) {
            this.nameTranslationKey = "layer." + identifier.getNamespace() + "." + identifier.getPath() + ".name";
        }
        return nameTranslationKey;
    }

    public String getTranslationKey() {
        return getOrCreateTranslationKey();
    }

    public String getMissingOriginNameTranslationKey() {
        if(missingOriginNameTranslationKey == null || missingOriginNameTranslationKey.isEmpty()) {
            this.missingOriginNameTranslationKey = "layer." + identifier.getNamespace() + "." + identifier.getPath() + ".missing_origin.name";
        }
        return missingOriginNameTranslationKey;
    }

    public String getMissingOriginDescriptionTranslationKey() {
        if(missingOriginDescriptionTranslationKey == null || missingOriginDescriptionTranslationKey.isEmpty()) {
            this.missingOriginDescriptionTranslationKey = "layer." + identifier.getNamespace() + "." + identifier.getPath() + ".missing_origin.description";
        }
        return missingOriginDescriptionTranslationKey;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Identifier> getOrigins() {
        return conditionedOrigins.stream().flatMap(co -> co.getOrigins().stream()).filter(OriginRegistry::contains).collect(Collectors.toList());
    }

    public List<Identifier> getOrigins(PlayerEntity playerEntity) {
        return conditionedOrigins.stream().filter(co -> co.isConditionFulfilled(playerEntity)).flatMap(co -> co.getOrigins().stream()).filter(OriginRegistry::contains).collect(Collectors.toList());
    }

    public boolean contains(Origin origin) {
        return conditionedOrigins.stream().anyMatch(co -> co.getOrigins().stream().anyMatch(o -> o.equals(origin.getIdentifier())));
    }

    public boolean contains(Origin origin, PlayerEntity playerEntity) {
        return conditionedOrigins.stream().filter(co -> co.isConditionFulfilled(playerEntity)).anyMatch(co -> co.getOrigins().stream().anyMatch(o -> o.equals(origin.getIdentifier())));
    }

    public boolean isRandomAllowed() {
        return isRandomAllowed;
    }

    public List<Identifier> getRandomOrigins(PlayerEntity playerEntity) {
        return conditionedOrigins.stream().filter(co -> co.isConditionFulfilled(playerEntity)).flatMap(co -> co.getOrigins().stream()).filter(OriginRegistry::contains).filter(o -> !originsExcludedFromRandom.contains(o)).filter(id -> doesRandomAllowUnchoosable || OriginRegistry.get(id).isChoosable()).collect(Collectors.toList());
    }

    public void merge(JsonObject json) {
        if(json.has("order")) {
            this.order = json.get("order").getAsInt();
        }
        if(json.has("enabled")) {
            this.enabled = json.get("enabled").getAsBoolean();
        }
        if(json.has("origins")) {
            JsonArray originArray = json.getAsJsonArray("origins");
            originArray.forEach(je -> this.conditionedOrigins.add(ConditionedOrigin.read(je)));
        }
        if(json.has("name")) {
            this.nameTranslationKey = JsonHelper.getString(json, "name", "");
        }
        if(json.has("missing_name")) {
            this.missingOriginNameTranslationKey = JsonHelper.getString(json, "missing_name", "");
        }
        if(json.has("missing_description")) {
            this.missingOriginDescriptionTranslationKey = JsonHelper.getString(json, "missing_description", "");
        }
        if(json.has("allow_random")) {
            this.isRandomAllowed = JsonHelper.getBoolean(json, "allow_random");
        }
        if(json.has("allow_random_unchoosable")) {
            this.doesRandomAllowUnchoosable = JsonHelper.getBoolean(json, "allow_random_unchoosable");
        }
        if(json.has("exclude_random") && json.get("exclude_random").isJsonArray()) {
            boolean replaceExclude = JsonHelper.getBoolean(json, "replace_exclude_random", false);
            if(replaceExclude) {
                originsExcludedFromRandom.clear();
            }
            JsonArray excludeRandomArray = json.getAsJsonArray("exclude_random");
            excludeRandomArray.forEach(je -> originsExcludedFromRandom.add(Identifier.tryParse(je.getAsString())));
        }
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == this) {
            return true;
        } else if(!(obj instanceof OriginLayer)) {
            return false;
        } else {
            return identifier.equals(((OriginLayer)obj).identifier);
        }
    }

    @Override
    public int compareTo(OriginLayer o) {
        return Integer.compare(order, o.order);
    }

    public void write(PacketByteBuf buffer) {
        buffer.writeString(identifier.toString());
        buffer.writeInt(order);
        buffer.writeBoolean(enabled);
        buffer.writeInt(conditionedOrigins.size());
        conditionedOrigins.forEach(co -> co.write(buffer));
        buffer.writeString(getOrCreateTranslationKey());
        buffer.writeString(getMissingOriginNameTranslationKey());
        buffer.writeString(getMissingOriginDescriptionTranslationKey());
        buffer.writeBoolean(isRandomAllowed());
        if(isRandomAllowed()) {
            buffer.writeBoolean(doesRandomAllowUnchoosable);
            buffer.writeInt(originsExcludedFromRandom.size());
            originsExcludedFromRandom.forEach(buffer::writeIdentifier);
        }
    }

    @Environment(EnvType.CLIENT)
    public static OriginLayer read(PacketByteBuf buffer) {
        OriginLayer layer = new OriginLayer();
        layer.identifier = Identifier.tryParse(buffer.readString());
        layer.order = buffer.readInt();
        layer.enabled = buffer.readBoolean();
        int conditionedOriginCount = buffer.readInt();
        layer.conditionedOrigins = new ArrayList<>(conditionedOriginCount);
        for(int i = 0; i < conditionedOriginCount; i++) {
            layer.conditionedOrigins.add(ConditionedOrigin.read(buffer));
        }
        layer.nameTranslationKey = buffer.readString();
        layer.missingOriginNameTranslationKey = buffer.readString();
        layer.missingOriginDescriptionTranslationKey = buffer.readString();
        layer.isRandomAllowed = buffer.readBoolean();
        if(layer.isRandomAllowed) {
            layer.doesRandomAllowUnchoosable = buffer.readBoolean();
            int excludedSize = buffer.readInt();
            layer.originsExcludedFromRandom = new LinkedList<>();
            for(int i = 0; i < excludedSize; i++) {
                layer.originsExcludedFromRandom.add(buffer.readIdentifier());
            }
        }
        return layer;
    }

    public static OriginLayer fromJson(Identifier id, JsonObject json) {
        int order = JsonHelper.getInt(json, "order", OriginLayers.size());
        if(!json.has("origins") || !json.get("origins").isJsonArray()) {
            throw new JsonParseException("Origin layer JSON requires \"origins\" array of origin IDs to include in the layer.");
        }
        JsonArray originArray = json.getAsJsonArray("origins");
        List<ConditionedOrigin> list = new ArrayList<>(originArray.size());
        originArray.forEach(je -> list.add(ConditionedOrigin.read(je)));
        boolean enabled = JsonHelper.getBoolean(json, "enabled", true);
        OriginLayer layer = new OriginLayer();
        layer.order = order;
        layer.conditionedOrigins = list;
        layer.enabled = enabled;
        layer.identifier = id;
        layer.nameTranslationKey = JsonHelper.getString(json, "name", "");
        layer.missingOriginNameTranslationKey = JsonHelper.getString(json, "missing_name", "");
        layer.missingOriginDescriptionTranslationKey = JsonHelper.getString(json, "missing_description", "");

        layer.isRandomAllowed = JsonHelper.getBoolean(json, "allow_random", false);
        layer.doesRandomAllowUnchoosable = JsonHelper.getBoolean(json, "allow_random_unchoosable", false);
        layer.originsExcludedFromRandom = new LinkedList<>();
        if(json.has("exclude_random") && json.get("exclude_random").isJsonArray()) {
            JsonArray excludeRandomArray = json.getAsJsonArray("exclude_random");
            excludeRandomArray.forEach(je -> layer.originsExcludedFromRandom.add(Identifier.tryParse(je.getAsString())));
        }

        return layer;
    }

    public static class ConditionedOrigin {
        private final ConditionFactory<PlayerEntity>.Instance condition;
        private final List<Identifier> origins;

        public ConditionedOrigin(ConditionFactory<PlayerEntity>.Instance condition, List<Identifier> origins) {
            this.condition = condition;
            this.origins = origins;
        }

        public boolean isConditionFulfilled(PlayerEntity playerEntity) {
            return condition == null || condition.test(playerEntity);
        }

        public List<Identifier> getOrigins() {
            return origins;
        }
        private static final SerializableData conditionedOriginObjectData = new SerializableData()
            .add("condition", SerializableDataType.PLAYER_CONDITION)
            .add("origins", SerializableDataType.IDENTIFIERS);

        public void write(PacketByteBuf buffer) {
            buffer.writeBoolean(condition != null);
            if(condition != null)
                condition.write(buffer);
            buffer.writeInt(origins.size());
            origins.forEach(buffer::writeIdentifier);
        }

        @Environment(EnvType.CLIENT)
        public static ConditionedOrigin read(PacketByteBuf buffer) {
            ConditionFactory<PlayerEntity>.Instance condition = null;
            if(buffer.readBoolean()) {
                condition = ConditionTypes.PLAYER.read(buffer);
            }
            int originCount = buffer.readInt();
            List<Identifier> originList = new ArrayList<>(originCount);
            for(int i = 0; i < originCount; i++) {
                originList.add(buffer.readIdentifier());
            }
            return new ConditionedOrigin(condition, originList);
        }

        @SuppressWarnings("unchecked")
        public static ConditionedOrigin read(JsonElement element) {
            if(element.isJsonPrimitive()) {
                JsonPrimitive elemPrimitive = element.getAsJsonPrimitive();
                if(elemPrimitive.isString()) {
                    return new ConditionedOrigin(null, Lists.newArrayList(Identifier.tryParse(elemPrimitive.getAsString())));
                }
                throw new JsonParseException("Expected origin in layer to be either a string or an object.");
            } else if(element.isJsonObject()) {
                SerializableData.Instance data = conditionedOriginObjectData.read(element.getAsJsonObject());
                return new ConditionedOrigin((ConditionFactory<PlayerEntity>.Instance)data.get("condition"), (List<Identifier>)data.get("origins"));
            }
            throw new JsonParseException("Expected origin in layer to be either a string or an object.");
        }
    }
}

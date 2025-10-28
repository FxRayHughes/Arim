package top.maplex.arim.tools.gson;

import com.google.gson.*;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.internal.bind.ObjectTypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Gson 工具类
 * 提供完整的 JSON 序列化/反序列化功能
 * 支持 Bukkit ConfigurationSerializable
 *
 * @author GermMC
 */
public class GsonUtils {

    private static Gson gson = null;

    public static Gson getGson() {
        if (gson != null) {
            return gson;
        }
        gson = new GsonBuilder()
                .registerTypeAdapterFactory(new BukkitSerializableAdapterFactory())
                .disableHtmlEscaping()
                .setLenient()
                .serializeSpecialFloatingPointValues()
                .serializeNulls()
                .create();
        try {
            Field factories = Gson.class.getDeclaredField("factories");
            factories.setAccessible(true);
            Object o = factories.get(gson);
            Class<?>[] declaredClasses = Collections.class.getDeclaredClasses();
            for (Class c : declaredClasses) {
                if ("java.util.Collections$UnmodifiableList".equals(c.getName())) {
                    Field listField = c.getDeclaredField("list");
                    listField.setAccessible(true);
                    List<TypeAdapterFactory> list = (List<TypeAdapterFactory>) listField.get(o);
                    int i = list.indexOf(ObjectTypeAdapter.FACTORY);
                    list.set(i, MapTypeAdapter.FACTORY);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return gson;
    }

    /**
     * 将对象序列化为JSON字符串
     */
    public static <T> String toJson(T obj) {
        return getGson().toJson(obj);
    }

    /**
     * 将JSON字符串反序列化为对象
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        return getGson().fromJson(json, classOfT);
    }

    private static final class BukkitSerializableAdapterFactory implements TypeAdapterFactory {

        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<? super T> clazz = type.getRawType();
            if (!ConfigurationSerializable.class.isAssignableFrom(clazz))
                return null;
            return (TypeAdapter<T>) new Adapter(gson);
        }

        private final class Adapter extends TypeAdapter<ConfigurationSerializable> {
            private final Type RAW_OUTPUT_TYPE = (new TypeToken<Map<String, Object>>() {

            }).getType();

            private final Gson gson;

            private Adapter(Gson gson) {
                this.gson = gson;
            }

            public void write(JsonWriter out, ConfigurationSerializable value) {
                Map<String, Object> serialized = value.serialize();
                Map<String, Object> map = new LinkedHashMap<>(serialized.size() + 1);
                map.put("==", ConfigurationSerialization.getAlias(value.getClass()));
                map.putAll(serialized);
                this.gson.toJson(map, RAW_OUTPUT_TYPE, out);
            }

            public ConfigurationSerializable read(JsonReader in) {
                Map<String, Object> map = this.gson.fromJson(in, RAW_OUTPUT_TYPE);
                deserializeChildren(map);
                return ConfigurationSerialization.deserializeObject(map);
            }

            private void deserializeChildren(Map<String, Object> map) {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getValue() instanceof Map)
                        try {
                            Map<String, Object> value = (Map<String, Object>) entry.getValue();
                            deserializeChildren(value);
                            if (value.containsKey("=="))
                                entry.setValue(ConfigurationSerialization.deserializeObject(value));
                        } catch (Exception exception) {
                        }
                    if (entry.getValue() instanceof Number) {
                        double doubleVal = ((Number) entry.getValue()).doubleValue();
                        int intVal = (int) doubleVal;
                        long longVal = (long) doubleVal;
                        if (intVal == doubleVal) {
                            entry.setValue(intVal);
                            continue;
                        }
                        if (longVal == doubleVal) {
                            entry.setValue(longVal);
                            continue;
                        }
                        entry.setValue(doubleVal);
                    }
                }
            }
        }
    }

    private static class MapTypeAdapter extends TypeAdapter<Object> {
        public static final TypeAdapterFactory FACTORY = new TypeAdapterFactory() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                if (type.getRawType() == Object.class) {
                    return (TypeAdapter<T>) new MapTypeAdapter(gson);
                }
                return null;
            }
        };

        private final Gson gson;

        private MapTypeAdapter(Gson gson) {
            this.gson = gson;
        }

        @Override
        public Object read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            //判断字符串的实际类型
            switch (token) {
                case BEGIN_ARRAY:
                    List<Object> list = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        list.add(read(in));
                    }
                    in.endArray();
                    return list;

                case BEGIN_OBJECT:
                    Map<String, Object> map = new LinkedTreeMap<>();
                    in.beginObject();
                    while (in.hasNext()) {
                        map.put(in.nextName(), read(in));
                    }
                    in.endObject();
                    return map;
                case STRING:
                    return in.nextString();
                case NUMBER:
                    String s = in.nextString();
                    if (s.contains(".")) {
                        return Double.valueOf(s);
                    } else {
                        try {
                            return Integer.valueOf(s);
                        } catch (Exception e) {
                            return Long.valueOf(s);
                        }
                    }
                case BOOLEAN:
                    return in.nextBoolean();
                case NULL:
                    in.nextNull();
                    return null;
                default:
                    throw new IllegalStateException();
            }
        }

        @Override
        public void write(JsonWriter out, Object value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            //noinspection unchecked
            TypeAdapter<Object> typeAdapter = (TypeAdapter<Object>) gson.getAdapter(value.getClass());
            if (typeAdapter instanceof ObjectTypeAdapter) {
                out.beginObject();
                out.endObject();
                return;
            }
            typeAdapter.write(out, value);
        }
    }

    /**
     * 深度反序列化 JSON 到 Map
     */
    public static Map<String, Object> deepDeserialize(String json) {
        return (Map) deepParse(getGson().fromJson(json, JsonElement.class));
    }

    /**
     * 递归解析 JsonElement
     */
    private static Object deepParse(JsonElement jsonElement) {
        if (jsonElement.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                map.put(entry.getKey(), deepParse(entry.getValue()));
            }
            return map;
        } else if (jsonElement.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            for (JsonElement element : jsonArray) {
                list.add(deepParse(element));
            }
            return list;
        } else if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive primitive = jsonElement.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                // 处理数字（可区分整数和小数）
                if (primitive.getAsString().contains(".")) {
                    return primitive.getAsDouble();
                } else {
                    return primitive.getAsLong();
                }
            } else {
                return primitive.getAsString();
            }
        } else if (jsonElement.isJsonNull()) {
            return null;
        }
        return null;
    }
}

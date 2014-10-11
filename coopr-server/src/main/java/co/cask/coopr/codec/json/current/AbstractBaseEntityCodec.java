/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.cask.coopr.codec.json.current;

import co.cask.coopr.codec.json.AbstractCodec;
import co.cask.coopr.spec.BaseEntity;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;

import java.lang.reflect.Type;

/**
 * Base codec for serializing and deserializing a {@link co.cask.coopr.spec.BaseEntity}.
 */
public abstract class AbstractBaseEntityCodec<T extends BaseEntity> extends AbstractCodec<T> {

  @Override
  public JsonElement serialize(T entity, Type type, JsonSerializationContext context) {
    JsonObject jsonObj = new JsonObject();
    jsonObj.add("name", context.serialize(entity.getName()));
    jsonObj.add("label", context.serialize(entity.getLabel()));
    jsonObj.add("description", context.serialize(entity.getDescription()));
    jsonObj.add("icon", context.serialize(entity.getIcon()));

    addChildFields(entity, jsonObj, context);
    return jsonObj;
  }

  @Override
  public T deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    String name = context.deserialize(jsonObj.get("name"), String.class);
    String label = context.deserialize(jsonObj.get("label"), String.class);
    String icon = context.deserialize(jsonObj.get("icon"), String.class);
    String description = context.deserialize(jsonObj.get("description"), String.class);

    return getBuilder(jsonObj, context)
      .setBaseFields(name, label, description, icon)
      .build();
  }

  /**
   * Add child specific fields to the json object.
   *
   * @param entity entity being serialized
   * @param jsonObj object that should be populated with child specific fields
   * @param context context for serialization
   */
  protected abstract void addChildFields(T entity, JsonObject jsonObj, JsonSerializationContext context);

  /**
   * Get a builder initialized with child specific fields.
   *
   * @param jsonObj object that is being deserialized
   * @param context context for deserialization
   * @return builder initialized with child specific fields
   */
  protected abstract BaseEntity.Builder<T> getBuilder(JsonObject jsonObj, JsonDeserializationContext context);

}
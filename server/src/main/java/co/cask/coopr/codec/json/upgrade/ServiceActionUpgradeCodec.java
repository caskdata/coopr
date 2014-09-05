/*
 * Copyright © 2012-2014 Cask Data, Inc.
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

package co.cask.coopr.codec.json.upgrade;

import co.cask.coopr.codec.json.AbstractCodec;
import co.cask.coopr.spec.service.ServiceAction;
import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Codec for serializing/deserializing a {@link co.cask.coopr.spec.service.ServiceAction}.
 */
public class ServiceActionUpgradeCodec extends AbstractCodec<ServiceAction> {

  @Override
  public JsonElement serialize(ServiceAction action, Type type, JsonSerializationContext context) {
    JsonObject jsonObj = new JsonObject();

    jsonObj.add("type", context.serialize(action.getType()));
    jsonObj.add("fields", context.serialize(action.getFields()));

    return jsonObj;
  }

  @Override
  public ServiceAction deserialize(JsonElement json, Type type, JsonDeserializationContext context)
    throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    String actionType = context.deserialize(jsonObj.get("type"), String.class);
    Map<String, String> fields =
      context.deserialize(jsonObj.get("fields"), new TypeToken<Map<String, String>>() {}.getType());
    // For backwards compatibility.
    if (jsonObj.has("script")) {
      if (fields == null) {
        fields = Maps.newHashMap();
      }
      fields.put("script", jsonObj.get("script").getAsString());
    }
    if (jsonObj.has("data")) {
      if (fields == null) {
        fields = Maps.newHashMap();
      }
      fields.put("data", jsonObj.get("data").getAsString());
    }

    return new ServiceAction(actionType, fields);
  }
}

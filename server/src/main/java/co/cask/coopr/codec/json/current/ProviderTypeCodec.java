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
package co.cask.coopr.codec.json.current;

import co.cask.coopr.spec.plugin.ParameterType;
import co.cask.coopr.spec.plugin.ParametersSpecification;
import co.cask.coopr.spec.plugin.ProviderType;
import co.cask.coopr.spec.plugin.ResourceTypeSpecification;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Codec for deserializing a {@link co.cask.coopr.spec.plugin.ProviderType}.
 * Used so that the constructor is called to avoid null values where they do not make sense.
 */
public class ProviderTypeCodec implements JsonDeserializer<ProviderType> {

  @Override
  public ProviderType deserialize(JsonElement json, Type type, JsonDeserializationContext context)
    throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    String name = context.deserialize(jsonObj.get("name"), String.class);
    String description = context.deserialize(jsonObj.get("description"), String.class);
    Map<ParameterType, ParametersSpecification> parameters = context.deserialize(
      jsonObj.get("parameters"), new TypeToken<Map<ParameterType, ParametersSpecification>>() {}.getType());
    Map<String, ResourceTypeSpecification> resourceTypes = context.deserialize(
      jsonObj.get("resourceTypes"), new TypeToken<Map<String, ResourceTypeSpecification>>() {}.getType());

    return new ProviderType(name, description, parameters, resourceTypes);
  }
}

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
package com.continuuity.loom.codec.json.current;

import com.continuuity.loom.account.Account;
import com.continuuity.loom.cluster.Cluster;
import com.continuuity.loom.spec.Provider;
import com.continuuity.loom.spec.template.ClusterTemplate;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * Codec for serializing/deserializing a {@link Cluster}. Used for backwards compatibility.
 */
public class ClusterCodec implements JsonDeserializer<Cluster> {

  @Override
  public Cluster deserialize(JsonElement json, Type type, JsonDeserializationContext context)
    throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    return Cluster.builder()
      .setID(context.<String>deserialize(jsonObj.get("id"), String.class))
      .setName(context.<String>deserialize(jsonObj.get("name"), String.class))
      .setDescription(context.<String>deserialize(jsonObj.get("description"), String.class))
      .setAccount(context.<Account>deserialize(jsonObj.get("account"), Account.class))
      .setCreateTime(jsonObj.get("createTime").getAsLong())
      .setExpireTime(context.<Long>deserialize(jsonObj.get("expireTime"), Long.class))
      .setProvider(context.<Provider>deserialize(jsonObj.get("provider"), Provider.class))
      .setClusterTemplate(context.<ClusterTemplate>deserialize(jsonObj.get("clusterTemplate"), ClusterTemplate.class))
      .setNodes(context.<Set<String>>deserialize(jsonObj.get("nodes"), new TypeToken<Set<String>>() {}.getType()))
      .setServices(context.<Set<String>>deserialize(jsonObj.get("services"), new TypeToken<Set<String>>() {}.getType()))
      .setLatestJobID(context.<String>deserialize(jsonObj.get("latestJobId"), String.class))
      .setStatus(context.<Cluster.Status>deserialize(jsonObj.get("status"), Cluster.Status.class))
      .setConfig(context.<JsonObject>deserialize(jsonObj.get("config"), JsonObject.class))
      .build();
  }
}

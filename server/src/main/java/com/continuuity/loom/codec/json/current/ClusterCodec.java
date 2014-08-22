/*
 * Copyright 2012-2014, Continuuity, Inc.
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
import com.continuuity.loom.spec.template.ClusterTemplate;
import com.continuuity.loom.spec.Provider;
import com.continuuity.loom.cluster.Cluster;
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

    String id = context.deserialize(jsonObj.get("id"), String.class);
    String name = context.deserialize(jsonObj.get("name"), String.class);
    String description = context.deserialize(jsonObj.get("description"), String.class);
    long createTime = jsonObj.get("createTime").getAsLong();
    Long expireTime = context.deserialize(jsonObj.get("expireTime"), Long.class);
    Provider provider = context.deserialize(jsonObj.get("provider"), Provider.class);
    ClusterTemplate template = context.deserialize(jsonObj.get("clusterTemplate"), ClusterTemplate.class);
    Set<String> nodes = context.deserialize(jsonObj.get("nodes"), new TypeToken<Set<String>>() {}.getType());
    Set<String> services = context.deserialize(jsonObj.get("services"), new TypeToken<Set<String>>() {}.getType());
    String latestJobId = context.deserialize(jsonObj.get("latestJobId"), String.class);
    Account account = context.deserialize(jsonObj.get("account"), Account.class);
    Cluster.Status status = context.deserialize(jsonObj.get("status"), Cluster.Status.class);
    JsonObject config = context.deserialize(jsonObj.get("config"), JsonObject.class);

    return new Cluster(id, account, name, createTime, expireTime == null ? 0 : expireTime, description,
                       provider, template, nodes, services, config, status, latestJobId);
  }
}

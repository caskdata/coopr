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
package com.continuuity.loom.codec.json;

import com.continuuity.loom.admin.Administration;
import com.continuuity.loom.admin.ClusterDefaults;
import com.continuuity.loom.admin.ClusterTemplate;
import com.continuuity.loom.admin.Constraints;
import com.continuuity.loom.admin.HardwareType;
import com.continuuity.loom.admin.ImageType;
import com.continuuity.loom.admin.LayoutConstraint;
import com.continuuity.loom.admin.LeaseDuration;
import com.continuuity.loom.admin.Provider;
import com.continuuity.loom.admin.Service;
import com.continuuity.loom.admin.ServiceAction;
import com.continuuity.loom.admin.ServiceConstraint;
import com.continuuity.loom.http.ClusterConfigureRequest;
import com.continuuity.loom.layout.ClusterRequest;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.lang.reflect.Type;

/**
 * Class for serializing and deserializing objects to/from json, using gson.
 */
public class JsonSerde {
  private final Gson gson;

  public JsonSerde() {
    gson = new GsonBuilder()
      .registerTypeAdapter(Provider.class, new ProviderCodec())
      .registerTypeAdapter(HardwareType.class, new HardwareTypeCodec())
      .registerTypeAdapter(ImageType.class, new ImageTypeCodec())
      .registerTypeAdapter(Service.class, new ServiceCodec())
      .registerTypeAdapter(ClusterTemplate.class, new ClusterTemplateCodec())
      .registerTypeAdapter(ServiceAction.class, new ServiceActionCodec())
      .registerTypeAdapter(ServiceConstraint.class, new ServiceConstraintCodec())
      .registerTypeAdapter(LayoutConstraint.class, new LayoutConstraintCodec())
      .registerTypeAdapter(Constraints.class, new ConstraintsCodec())
      .registerTypeAdapterFactory(new LowercaseEnumTypeAdapterFactory())
      .registerTypeAdapter(ClusterDefaults.class, new ClusterDefaultsCodec())
      .registerTypeAdapter(Administration.class, new AdministrationCodec())
      .registerTypeAdapter(LeaseDuration.class, new LeaseDurationCodec())
      .registerTypeAdapter(ClusterRequest.class, new ClusterRequestCodec())
      .registerTypeAdapter(ClusterConfigureRequest.class, new ClusterConfigureRequestCodec())
      .enableComplexMapKeySerialization()
      .create();
  }

  /**
   * Serialize the object of specified type.
   *
   * @param object Object to serialize.
   * @param type Type of the object to serialize.
   * @param <T> Object class.
   * @return serialized object.
   */
  public <T> byte[] serialize(T object, Type type) {
    return gson.toJson(object, type).getBytes(Charsets.UTF_8);
  }

  /**
   * Deserialize an object given a reader for the object and the type of the object.
   *
   * @param reader Reader for reading the object to deserialize.
   * @param type Type of the object to deserialize.
   * @param <T> Object class.
   * @return deserialized object.
   */
  public <T> T deserialize(Reader reader, Type type) {
    return gson.fromJson(reader, type);
  }

  /**
   * Deserialize an object given the object as a byte array and the type of the object.
   *
   * @param bytes Serialized object.
   * @param type Type of the object to deserialize.
   * @param <T> Object class.
   * @return deserialized object.
   */
  public <T> T deserialize(byte[] bytes, Type type) {
    return gson.fromJson(new String(bytes, Charsets.UTF_8), type);
  }

  /**
   * Get the Gson used for serialization and deserialization.
   *
   * @return Gson used for serialization and deserialization.
   */
  public Gson getGson() {
    return gson;
  }
}

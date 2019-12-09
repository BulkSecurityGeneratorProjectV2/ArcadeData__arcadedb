/*
 * Copyright (c) - Arcade Analytics LTD (https://arcadeanalytics.com)
 */

package com.arcadedb.database;

import com.arcadedb.graph.EmbeddedDocument;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.Property;
import com.arcadedb.schema.Type;
import org.json.JSONObject;

import java.util.*;

public class MutableDocument extends BaseDocument implements RecordInternal {
  private   Map<String, Object> map;
  protected boolean             dirty = false;

  protected MutableDocument(final Database database, final String typeName, final RID rid) {
    super(database, typeName, rid, null);
    this.map = new LinkedHashMap<String, Object>();
  }

  protected MutableDocument(final Database database, final String typeName, final RID rid, final Binary buffer) {
    super(database, typeName, rid, buffer);
    buffer.position(buffer.position() + 1); // SKIP RECORD TYPE
  }

  public void merge(final Document other) {
    for (String p : other.getPropertyNames())
      set(p, other.get(p));
  }

  public boolean isDirty() {
    return dirty;
  }

  @Override
  public void setBuffer(final Binary buffer) {
    super.setBuffer(buffer);
    dirty = false;
    map = null;
  }

  @Override
  public void unsetDirty() {
    map = null;
    dirty = false;
  }

  public void fromMap(final Map<String, Object> map) {
    this.map = new LinkedHashMap<>();

    final DocumentType type = database.getSchema().getType(typeName);

    for (Map.Entry<String, Object> entry : map.entrySet())
      this.map.put(entry.getKey(), convertValueToSchemaType(entry.getKey(), entry.getValue(), type));

    dirty = true;
  }

  @Override
  public Map<String, Object> toMap() {
    return new HashMap<>(map);
  }

  public void fromJSON(final JSONObject json) {
    fromMap(new JSONSerializer(database).json2map(json));
  }

  @Override
  public JSONObject toJSON() {
    checkForLazyLoadingProperties();
    return new JSONSerializer(database).map2json(map);
  }

  @Override
  public boolean has(String propertyName) {
    checkForLazyLoadingProperties();
    return map.containsKey(propertyName);
  }

  public Object get(final String propertyName) {
    checkForLazyLoadingProperties();
    return map.get(propertyName);
  }

  /**
   * Sets the property value in the document. If the property has been defined in the schema, the value is converted according to the property type.
   */
  public MutableDocument set(final String name, Object value) {
    checkForLazyLoadingProperties();
    dirty = true;

    final DocumentType type = database.getSchema().getType(typeName);
    value = setTransformValue(value);
    map.put(name, convertValueToSchemaType(name, value, type));
    return this;
  }

  /**
   * Sets the property values in the document. If any properties has been defined in the schema, the value is converted according to the property type.
   *
   * @param properties Array containing pairs of name (String) and value (Object)
   */
  public MutableDocument set(final Object... properties) {
    if (properties.length % 2 != 0)
      throw new IllegalArgumentException("properties must be an even pair of key/values");

    checkForLazyLoadingProperties();
    dirty = true;

    final DocumentType type = database.getSchema().getType(typeName);

    for (int p = 0; p < properties.length; p += 2) {
      final Object value = setTransformValue(properties[p + 1]);
      map.put((String) properties[p], convertValueToSchemaType((String) properties[p], value, type));
    }

    return this;
  }

  /**
   * Sets the property values in the document from a map. If any properties has been defined in the schema, the value is converted according to the property type.
   *
   * @param properties Map<String,Object> containing pairs of name (String) and value (Object)
   */
  public MutableDocument set(final Map<String, Object> properties) {
    checkForLazyLoadingProperties();
    dirty = true;

    final DocumentType type = database.getSchema().getType(typeName);

    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      final Object value = setTransformValue(entry.getValue());
      map.put(entry.getKey(), convertValueToSchemaType(entry.getKey(), value, type));
    }

    return this;
  }

  public Object remove(final String name) {
    checkForLazyLoadingProperties();
    dirty = true;
    return map.remove(name);
  }

  public MutableDocument save() {
    dirty = true;
    if (rid != null)
      database.updateRecord(this);
    else
      database.createRecord(this);
    return this;
  }

  public MutableDocument save(final String bucketName) {
    dirty = true;
    if (rid != null)
      throw new IllegalStateException("Cannot update a record in a custom bucket");

    database.createRecord(this, bucketName);
    return this;
  }

  @Override
  public void setIdentity(final RID rid) {
    this.rid = rid;
  }

  @Override
  public String toString() {
    final StringBuilder buffer = new StringBuilder(256);
    if (rid != null)
      buffer.append(rid);
    if (typeName != null) {
      buffer.append('@');
      buffer.append(typeName);
    }
    buffer.append('[');
    if (map == null) {
      buffer.append('?');
    } else {
      int i = 0;
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        if (i > 0)
          buffer.append(',');

        buffer.append(entry.getKey());
        buffer.append('=');
        buffer.append(entry.getValue());
        i++;
      }
    }
    buffer.append(']');
    return buffer.toString();
  }

  @Override
  public Set<String> getPropertyNames() {
    checkForLazyLoadingProperties();
    return map.keySet();
  }

  public MutableDocument modify() {
    return this;
  }

  @Override
  public void reload() {
    dirty = false;
    map = null;
    buffer = null;
    super.reload();
  }

  protected void checkForLazyLoadingProperties() {
    if (this.map == null) {
      if (buffer == null)
        reload();

      buffer.position(propertiesStartingPosition);
      this.map = this.database.getSerializer().deserializeProperties(this.database, buffer);
    }
  }

  private Object convertValueToSchemaType(final String name, final Object value, final DocumentType type) {
    final Property prop = type.getPolymorphicPropertyIfExists(name);
    if (prop != null)
      return Type.convert(database, value, prop.getType().getDefaultJavaType());

    return value;
  }

  private Object setTransformValue(Object value) {
    if (value instanceof EmbeddedDocument)
      value = ((EmbeddedDocument) value).modify();
    else if (value instanceof List) {
      List<Object> list = (List) value;
      for (int i = 0; i < list.size(); i++) {
        final Object v = list.get(i);
        if (v instanceof Document)
          list.set(i, ((Document) v).modify());
      }
    } else if (value instanceof Map) {
      final Map<Object, Object> map = (Map) value;
      for (Object key : map.keySet()) {
        final Object v = map.get(key);
        if (v instanceof Document)
          map.put(key, ((Document) v).modify());
      }
    }
    return value;
  }
}

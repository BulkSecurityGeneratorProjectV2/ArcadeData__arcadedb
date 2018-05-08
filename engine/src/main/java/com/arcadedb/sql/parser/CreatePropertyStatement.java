/* Generated By:JJTree: Do not edit this line. OCreatePropertyStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.database.Database;
import com.arcadedb.sql.executor.CommandContext;
import com.arcadedb.sql.executor.InternalResultSet;
import com.arcadedb.sql.executor.ResultInternal;
import com.arcadedb.sql.executor.ResultSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CreatePropertyStatement extends ODDLStatement {
  public Identifier className;
  public Identifier propertyName;
  boolean ifNotExists = false;
  public Identifier propertyType;
  public Identifier linkedType;
  public boolean                                unsafe     = false;
  public List<CreatePropertyAttributeStatement> attributes = new ArrayList<CreatePropertyAttributeStatement>();

  public CreatePropertyStatement(int id) {
    super(id);
  }

  public CreatePropertyStatement(SqlParser p, int id) {
    super(p, id);
  }

  @Override public ResultSet executeDDL(CommandContext ctx) {
    ResultInternal result = new ResultInternal();
    result.setProperty("operation", "create property");
    result.setProperty("className", className.getStringValue());
    result.setProperty("propertyName", propertyName.getStringValue());
    executeInternal(ctx, result);
    InternalResultSet rs = new InternalResultSet();
    rs.add(result);
    return rs;
  }

  private void executeInternal(CommandContext ctx, ResultInternal result) {
    Database db = ctx.getDatabase();
//    OClassEmbedded clazz = (OClassEmbedded) db.getMetadata().getSchema().getClass(className.getStringValue());
//    if (clazz == null) {
//      throw new PCommandExecutionException("Class not found: " + className.getStringValue());
//    }
//    if (clazz.getProperty(propertyName.getStringValue()) != null) {
//      if (ifNotExists) {
//        return;
//      }
//      throw new PCommandExecutionException(
//          "Property " + className.getStringValue() + "." + propertyName.getStringValue() + " already exists");
//    }
//    OType type = OType.valueOf(propertyType.getStringValue().toUpperCase(Locale.ENGLISH));
//    if (type == null) {
//      throw new PCommandExecutionException("Invalid property type: " + propertyType.getStringValue());
//    }
//    OClass linkedClass = null;
//    OType linkedType = null;
//    if (this.linkedType != null) {
//      String linked = this.linkedType.getStringValue();
//      // FIRST SEARCH BETWEEN CLASSES
//      linkedClass = db.getMetadata().getSchema().getClass(linked);
//      if (linkedClass == null)
//        // NOT FOUND: SEARCH BETWEEN TYPES
//        linkedType = OType.valueOf(linked.toUpperCase(Locale.ENGLISH));
//    }
//    // CREATE IT LOCALLY
//    OPropertyImpl internalProp = (OPropertyImpl) clazz.addProperty(propertyName.getStringValue(), type, linkedType, linkedClass, unsafe);
//    for (OCreatePropertyAttributeStatement attr : attributes) {
//      Object val = attr.setOnProperty(internalProp, ctx);
//      result.setProperty(attr.settingName.getStringValue(), val);
//    }
    throw new UnsupportedOperationException();
  }

  @Override public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("CREATE PROPERTY ");
    className.toString(params, builder);
    builder.append(".");
    propertyName.toString(params, builder);
    if (ifNotExists) {
      builder.append(" IF NOT EXISTS");
    }
    builder.append(" ");
    propertyType.toString(params, builder);
    if (linkedType != null) {
      builder.append(" ");
      linkedType.toString(params, builder);
    }

    if (!attributes.isEmpty()) {
      builder.append(" (");
      for (int i = 0; i < attributes.size(); i++) {
        CreatePropertyAttributeStatement att = attributes.get(i);
        att.toString(params, builder);

        if (i < attributes.size() - 1) {
          builder.append(", ");
        }
      }
      builder.append(")");
    }

    if (unsafe) {
      builder.append(" UNSAFE");
    }
  }

  @Override public CreatePropertyStatement copy() {
    CreatePropertyStatement result = new CreatePropertyStatement(-1);
    result.className = className == null ? null : className.copy();
    result.propertyName = propertyName == null ? null : propertyName.copy();
    result.propertyType = propertyType == null ? null : propertyType.copy();
    result.linkedType = linkedType == null ? null : linkedType.copy();
    result.unsafe = unsafe;
    result.ifNotExists = ifNotExists;
    result.attributes = attributes == null ? null : attributes.stream().map(x -> x.copy()).collect(Collectors.toList());
    return result;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    CreatePropertyStatement that = (CreatePropertyStatement) o;

    if (unsafe != that.unsafe)
      return false;
    if (className != null ? !className.equals(that.className) : that.className != null)
      return false;
    if (propertyName != null ? !propertyName.equals(that.propertyName) : that.propertyName != null)
      return false;
    if (propertyType != null ? !propertyType.equals(that.propertyType) : that.propertyType != null)
      return false;
    if (linkedType != null ? !linkedType.equals(that.linkedType) : that.linkedType != null)
      return false;
    if (attributes != null ? !attributes.equals(that.attributes) : that.attributes != null)
      return false;
    if (ifNotExists != that.ifNotExists) {
      return false;
    }

    return true;
  }

  @Override public int hashCode() {
    int result = className != null ? className.hashCode() : 0;
    result = 31 * result + (propertyName != null ? propertyName.hashCode() : 0);
    result = 31 * result + (propertyType != null ? propertyType.hashCode() : 0);
    result = 31 * result + (linkedType != null ? linkedType.hashCode() : 0);
    result = 31 * result + (unsafe ? 1 : 0);
    result = 31 * result + (attributes != null ? attributes.hashCode() : 0);
    return result;
  }
}
/* JavaCC - OriginalChecksum=ff78676483d59013ab10b13bde2678d3 (do not edit this line) */

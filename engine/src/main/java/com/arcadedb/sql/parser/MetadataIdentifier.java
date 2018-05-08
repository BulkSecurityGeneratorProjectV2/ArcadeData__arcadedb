/* Generated By:JJTree: Do not edit this line. OMetadataIdentifier.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.sql.executor.Result;
import com.arcadedb.sql.executor.ResultInternal;

import java.util.Map;

public class MetadataIdentifier extends SimpleNode {

  protected String name;

  public MetadataIdentifier(int id) {
    super(id);
  }

  public MetadataIdentifier(SqlParser p, int id) {
    super(p, id);
  }

  /**
   * Accept the visitor.
   **/
  public Object jjtAccept(SqlParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("metadata:");
    builder.append(name);
  }

  public String getName() {
    return name;
  }

  public MetadataIdentifier copy() {
    MetadataIdentifier result = new MetadataIdentifier(-1);
    result.name = name;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    MetadataIdentifier that = (MetadataIdentifier) o;

    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    return name != null ? name.hashCode() : 0;
  }

  public Result serialize() {
    ResultInternal result = new ResultInternal();
    result.setProperty("name", name);
    return result;
  }

  public void deserialize(Result fromResult) {
    name = fromResult.getProperty("name");
  }
}
/* JavaCC - OriginalChecksum=85e179b9505270f0596904070fdf0745 (do not edit this line) */

/* Generated By:JJTree: Do not edit this line. OPositionalParameter.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.sql.executor.Result;
import com.arcadedb.sql.executor.ResultInternal;

import java.util.Map;

public class PositionalParameter extends InputParameter {

  protected int paramNumber;

  public PositionalParameter(int id) {
    super(id);
  }

  public PositionalParameter(SqlParser p, int id) {
    super(p, id);
  }

  /**
   * Accept the visitor.
   **/
  public Object jjtAccept(SqlParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  @Override
  public String toString() {
    return "?";
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    Object finalValue = bindFromInputParams(params);
    if (finalValue == this) {
      builder.append("?");
    } else if (finalValue instanceof String) {
      builder.append("\"");
      builder.append(Expression.encode(finalValue.toString()));
      builder.append("\"");
    } else if (finalValue instanceof SimpleNode) {
      ((SimpleNode) finalValue).toString(params, builder);
    } else {
      builder.append(finalValue);
    }
  }

  public Object getValue(Map<Object, Object> params) {
    Object result = null;
    if (params != null) {
      result = params.get(paramNumber);
    }
    return result;
  }

  public Object bindFromInputParams(Map<Object, Object> params) {
    if (params != null) {
      Object value = params.get(paramNumber);
      Object result = toParsedTree(value);
      return result;
    }
    return this;
  }

  @Override
  public PositionalParameter copy() {
    PositionalParameter result = new PositionalParameter(-1);
    result.paramNumber = paramNumber;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    PositionalParameter that = (PositionalParameter) o;

    if (paramNumber != that.paramNumber)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    return paramNumber;
  }

  public Result serialize() {
    ResultInternal result = (ResultInternal) super.serialize();
    result.setProperty("paramNumber", paramNumber);
    return result;
  }

  public void deserialize(Result fromResult) {
    paramNumber = fromResult.getProperty("paramNumber");
  }
}
/* JavaCC - OriginalChecksum=f73bea7d9b3994a9d4e79d2c330d8ba2 (do not edit this line) */

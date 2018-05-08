/* Generated By:JJTree: Do not edit this line. OExpansionItem.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.sql.executor.CommandContext;
import com.arcadedb.sql.executor.Result;
import com.arcadedb.sql.executor.ResultInternal;

import java.util.Map;

public class NestedProjectionItem extends SimpleNode {

  protected boolean exclude = false;

  protected boolean star = false;

  protected Expression expression;
  protected boolean rightWildcard = false;

  protected NestedProjection expansion;
  protected Identifier       alias;

  public NestedProjectionItem(int id) {
    super(id);
  }

  public NestedProjectionItem(SqlParser p, int id) {
    super(p, id);
  }

  @Override
  public NestedProjectionItem copy() {
    NestedProjectionItem result = new NestedProjectionItem(-1);
    result.exclude = exclude;
    result.star = star;
    result.expression = expression == null ? null : expression.copy();
    result.rightWildcard = rightWildcard;
    result.expansion = expansion == null ? null : expansion.copy();
    result.alias = alias == null ? null : alias.copy();
    return result;
  }

  /**
   * given a property name, calculates if this property name matches this nested projection item, eg.
   * <ul>
   * <li>this is a *, so it matches any property name</li>
   * <li>the field name for this projection item is the same as the input property name</li>
   * <li>this item has a wildcard and the partial field is a prefix of the input property name</li>
   * </ul>
   *
   * @param propertyName
   *
   * @return
   */
  public boolean matches(String propertyName) {
    if (star) {
      return true;
    }
    if (expression != null) {
      String fieldString = expression.getDefaultAlias().getStringValue();
      if (fieldString.equals(propertyName)) {
        return true;
      }
      if (rightWildcard && propertyName.startsWith(fieldString)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void toString(Map<Object, Object> params, StringBuilder builder) {
    if (exclude) {
      builder.append("!");
    }
    if (star) {
      builder.append("*");
    }
    if (expression != null) {
      expression.toString(params, builder);
      if (rightWildcard) {
        builder.append("*");
      }
    }
    if (expansion != null) {
      expansion.toString(params, builder);
    }
    if (alias != null) {
      builder.append(" AS ");
      alias.toString(params, builder);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    NestedProjectionItem that = (NestedProjectionItem) o;

    if (exclude != that.exclude)
      return false;
    if (star != that.star)
      return false;
    if (rightWildcard != that.rightWildcard)
      return false;
    if (expression != null ? !expression.equals(that.expression) : that.expression != null)
      return false;
    if (expansion != null ? !expansion.equals(that.expansion) : that.expansion != null)
      return false;
    return alias != null ? alias.equals(that.alias) : that.alias == null;
  }

  @Override
  public int hashCode() {
    int result = (exclude ? 1 : 0);
    result = 31 * result + (star ? 1 : 0);
    result = 31 * result + (expression != null ? expression.hashCode() : 0);
    result = 31 * result + (rightWildcard ? 1 : 0);
    result = 31 * result + (expansion != null ? expansion.hashCode() : 0);
    result = 31 * result + (alias != null ? alias.hashCode() : 0);
    return result;
  }

  public Object expand(Expression expression, String name, Object value, CommandContext ctx, int recursion) {
    return expansion.apply(expression, value, ctx);
  }

  public Result serialize() {
    ResultInternal result = new ResultInternal();
    result.setProperty("exclude", exclude);
    result.setProperty("star", star);
    if (expression != null) {
      result.setProperty("expression", expression.serialize());
    }
    result.setProperty("rightWildcard", rightWildcard);
    if (expansion != null) {
      result.setProperty("expansion", expansion.serialize());
    }
    if (alias != null) {
      result.setProperty("alias", alias.serialize());
    }
    return result;
  }

  public void deserialize(Result fromResult) {
    exclude = fromResult.getProperty("exclude");
    star = fromResult.getProperty("star");
    if (fromResult.getProperty("field") != null) {
      expression = new Expression(-1);
      expression.deserialize(fromResult.getProperty("expression"));
    }
    rightWildcard = fromResult.getProperty("rightWildcard");
    if (fromResult.getProperty("expansion") != null) {
      expansion = new NestedProjection(-1);
      expansion.deserialize(fromResult.getProperty("expansion"));
    }
    if (fromResult.getProperty("alias") != null) {
      alias = new Identifier(-1);
      alias.deserialize(fromResult.getProperty("alias"));
    }
  }
}
/* JavaCC - OriginalChecksum=606b3fe37ff952934e3e2e3daa9915f2 (do not edit this line) */

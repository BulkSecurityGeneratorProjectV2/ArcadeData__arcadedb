/* Generated By:JJTree: Do not edit this line. OContainsValueCondition.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.database.PIdentifiable;
import com.arcadedb.sql.executor.OCommandContext;
import com.arcadedb.sql.executor.OResult;

import java.util.*;

public class OContainsValueCondition extends OBooleanExpression {
  protected OExpression            left;
  protected OContainsValueOperator operator;
  protected OOrBlock               condition;
  protected OExpression            expression;

  public OContainsValueCondition(int id) {
    super(id);
  }

  public OContainsValueCondition(OrientSql p, int id) {
    super(p, id);
  }

  /**
   * Accept the visitor.
   **/
  public Object jjtAccept(OrientSqlVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  @Override
  public boolean evaluate(PIdentifiable currentRecord, OCommandContext ctx) {
    Object leftValue = left.execute(currentRecord, ctx);
    if (leftValue instanceof Map) {
      Map map = (Map) leftValue;
      if (condition != null) {
        for (Object o : map.values()) {
          if (condition.evaluate(o, ctx)) {
            return true;
          }
        }
        return false;
      } else {
        Object rightValue = expression.execute(currentRecord, ctx);
        return map.values().contains(rightValue);//TODO type conversions...?
      }

    }
    return false;
  }

  @Override
  public boolean evaluate(OResult currentRecord, OCommandContext ctx) {
    Object leftValue = left.execute(currentRecord, ctx);
    if (leftValue instanceof Map) {
      Map map = (Map) leftValue;
      if (condition != null) {
        for (Object o : map.values()) {
          if (condition.evaluate(o, ctx)) {
            return true;
          }
        }
        return false;
      } else {
        Object rightValue = expression.execute(currentRecord, ctx);
        return map.values().contains(rightValue);//TODO type conversions...?
      }

    }
    return false;
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {

    left.toString(params, builder);
    builder.append(" CONTAINSVALUE ");
    if (condition != null) {
      builder.append("(");
      condition.toString(params, builder);
      builder.append(")");
    } else {
      expression.toString(params, builder);
    }

  }

  @Override
  public boolean supportsBasicCalculation() {
    return true;
  }

  @Override
  protected int getNumberOfExternalCalculations() {
    if (condition == null) {
      return 0;
    }
    return condition.getNumberOfExternalCalculations();
  }

  @Override
  protected List<Object> getExternalCalculationConditions() {
    if (condition == null) {
      return Collections.EMPTY_LIST;
    }
    return condition.getExternalCalculationConditions();
  }

  @Override
  public boolean needsAliases(Set<String> aliases) {
    if (left != null && left.needsAliases(aliases)) {
      return true;
    }
    if (condition != null && condition.needsAliases(aliases)) {
      return true;
    }
    if (expression != null && expression.needsAliases(aliases)) {
      return true;
    }

    return false;
  }

  @Override
  public OContainsValueCondition copy() {
    OContainsValueCondition result = new OContainsValueCondition(-1);
    result.left = left.copy();
    result.operator = operator;
    result.condition = condition == null ? null : condition.copy();
    result.expression = expression == null ? null : expression.copy();
    return result;
  }

  @Override
  public void extractSubQueries(SubQueryCollector collector) {
    left.extractSubQueries(collector);
    if (condition != null) {
      condition.extractSubQueries(collector);
    }
    if (expression != null) {
      expression.extractSubQueries(collector);
    }
  }

  @Override
  public boolean refersToParent() {
    if (left != null && left.refersToParent()) {
      return true;
    }
    if (condition != null && condition.refersToParent()) {
      return true;
    }
    if (expression != null && condition.refersToParent()) {
      return true;
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OContainsValueCondition that = (OContainsValueCondition) o;

    if (left != null ? !left.equals(that.left) : that.left != null)
      return false;
    if (operator != null ? !operator.equals(that.operator) : that.operator != null)
      return false;
    if (condition != null ? !condition.equals(that.condition) : that.condition != null)
      return false;
    if (expression != null ? !expression.equals(that.expression) : that.expression != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = left != null ? left.hashCode() : 0;
    result = 31 * result + (operator != null ? operator.hashCode() : 0);
    result = 31 * result + (condition != null ? condition.hashCode() : 0);
    result = 31 * result + (expression != null ? expression.hashCode() : 0);
    return result;
  }

  @Override
  public List<String> getMatchPatternInvolvedAliases() {
    List<String> leftX = left == null ? null : left.getMatchPatternInvolvedAliases();
    List<String> expressionX = expression == null ? null : expression.getMatchPatternInvolvedAliases();
    List<String> conditionX = condition == null ? null : condition.getMatchPatternInvolvedAliases();

    List<String> result = new ArrayList<String>();
    if (leftX != null) {
      result.addAll(leftX);
    }
    if (expressionX != null) {
      result.addAll(expressionX);
    }
    if (conditionX != null) {
      result.addAll(conditionX);
    }

    return result.size() == 0 ? null : result;
  }

  @Override
  public boolean isCacheable() {
    if (left != null && !left.isCacheable()) {
      return false;
    }
    if (condition != null && !condition.isCacheable()) {
      return false;
    }
    if (expression != null && !expression.isCacheable()) {
      return false;
    }

    return true;
  }
}
/* JavaCC - OriginalChecksum=6fda752f10c8d8731f43efa706e39459 (do not edit this line) */

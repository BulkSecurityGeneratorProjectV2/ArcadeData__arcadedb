/* Generated By:JJTree: Do not edit this line. OInstanceofCondition.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.database.Document;
import com.arcadedb.database.Identifiable;
import com.arcadedb.database.Record;
import com.arcadedb.sql.executor.CommandContext;
import com.arcadedb.sql.executor.Result;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstanceofCondition extends BooleanExpression {

  protected Expression left;
  protected Identifier right;
  protected String     rightString;

  public InstanceofCondition(int id) {
    super(id);
  }

  public InstanceofCondition(SqlParser p, int id) {
    super(p, id);
  }

  /**
   * Accept the visitor.
   **/
  public Object jjtAccept(SqlParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  @Override
  public boolean evaluate(Identifiable currentRecord, CommandContext ctx) {
    if (currentRecord == null) {
      return false;
    }
    Record record = currentRecord.getRecord();
    if (record == null) {
      return false;
    }
    if (!(record instanceof Document)) {
      return false;
    }
    Document doc = (Document)record;
    String clazz = doc.getType();
    if (clazz == null) {
      return false;
    }
    if (right != null) {
      return clazz.equals(right.getStringValue());
    } else if (rightString != null) {
      return clazz.equals(decode(rightString));
    }
    return false;
  }

  @Override
  public boolean evaluate(Result currentRecord, CommandContext ctx) {
    if (currentRecord == null) {
      return false;
    }
    if (!currentRecord.isElement()) {
      return false;
    }
    Record record = currentRecord.getElement().get().getRecord();
    if (record == null) {
      return false;
    }
    if (!(record instanceof Document)) {
      return false;
    }
    Document doc = (Document) record;
    String clazz = doc.getType();
    if (clazz == null) {
      return false;
    }
    if (right != null) {
      return clazz.equals(right.getStringValue());
    } else if (rightString != null) {
      return clazz.equals(decode(rightString));
    }
    return false;
  }

  private String decode(String rightString) {
    if (rightString == null) {
      return null;
    }
    return BaseExpression.decode(rightString.substring(1, rightString.length() - 1));
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    left.toString(params, builder);
    builder.append(" instanceof ");
    if (right != null) {
      right.toString(params, builder);
    } else if (rightString != null) {
      builder.append(rightString);
    }
  }

  @Override
  public boolean supportsBasicCalculation() {
    return left.supportsBasicCalculation();
  }

  @Override
  protected int getNumberOfExternalCalculations() {
    if (!left.supportsBasicCalculation()) {
      return 1;
    }
    return 0;
  }

  @Override
  protected List<Object> getExternalCalculationConditions() {
    if (!left.supportsBasicCalculation()) {
      return (List) Collections.singletonList(left);
    }
    return Collections.EMPTY_LIST;
  }

  @Override
  public boolean needsAliases(Set<String> aliases) {
    if (left.needsAliases(aliases)) {
      return true;
    }
    return false;
  }

  @Override
  public InstanceofCondition copy() {
    InstanceofCondition result = new InstanceofCondition(-1);
    result.left = left.copy();
    result.right = right == null ? null : right.copy();
    result.rightString = rightString;
    return result;
  }

  @Override
  public void extractSubQueries(SubQueryCollector collector) {
    left.extractSubQueries(collector);
  }

  @Override
  public boolean refersToParent() {
    if (left != null && left.refersToParent()) {
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

    InstanceofCondition that = (InstanceofCondition) o;

    if (left != null ? !left.equals(that.left) : that.left != null)
      return false;
    if (right != null ? !right.equals(that.right) : that.right != null)
      return false;
    if (rightString != null ? !rightString.equals(that.rightString) : that.rightString != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = left != null ? left.hashCode() : 0;
    result = 31 * result + (right != null ? right.hashCode() : 0);
    result = 31 * result + (rightString != null ? rightString.hashCode() : 0);
    return result;
  }

  @Override
  public List<String> getMatchPatternInvolvedAliases() {
    return left == null ? null : left.getMatchPatternInvolvedAliases();
  }

  @Override
  public boolean isCacheable() {
    return left.isCacheable();
  }

}
/* JavaCC - OriginalChecksum=0b5eb529744f307228faa6b26f0592dc (do not edit this line) */

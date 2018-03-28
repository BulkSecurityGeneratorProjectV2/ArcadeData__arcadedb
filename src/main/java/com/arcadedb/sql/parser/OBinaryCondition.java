/* Generated By:JJTree: Do not edit this line. OBinaryCondition.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.database.PDatabase;
import com.arcadedb.database.PIdentifiable;
import com.arcadedb.database.PRecord;
import com.arcadedb.exception.PCommandExecutionException;
import com.arcadedb.schema.PDocumentType;
import com.arcadedb.sql.executor.OCollate;
import com.arcadedb.sql.executor.OCommandContext;
import com.arcadedb.sql.executor.OResult;
import com.arcadedb.sql.executor.OResultInternal;

import java.util.*;

public class OBinaryCondition extends OBooleanExpression {
  protected OExpression            left;
  protected OBinaryCompareOperator operator;
  protected OExpression            right;

  public OBinaryCondition(int id) {
    super(id);
  }

  public OBinaryCondition(OrientSql p, int id) {
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
    return operator.execute(left.execute(currentRecord, ctx), right.execute(currentRecord, ctx));
  }

  @Override
  public boolean evaluate(OResult currentRecord, OCommandContext ctx) {
    Object leftVal = left.execute(currentRecord, ctx);
    Object rightVal = right.execute(currentRecord, ctx);
    OCollate collate = left.getCollate(currentRecord, ctx);
    if (collate == null) {
      collate = right.getCollate(currentRecord, ctx);
    }
    if (collate != null) {
      leftVal = collate.transform(leftVal);
      rightVal = collate.transform(rightVal);
    }
    return operator.execute(leftVal, rightVal);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    left.toString(params, builder);
    builder.append(" ");
    builder.append(operator.toString());
    builder.append(" ");
    right.toString(params, builder);
  }

  protected boolean supportsBasicCalculation() {
    if (!operator.supportsBasicCalculation()) {
      return false;
    }
    return left.supportsBasicCalculation() && right.supportsBasicCalculation();

  }

  @Override
  protected int getNumberOfExternalCalculations() {
    int total = 0;
    if (!operator.supportsBasicCalculation()) {
      total++;
    }
    if (!left.supportsBasicCalculation()) {
      total++;
    }
    if (!right.supportsBasicCalculation()) {
      total++;
    }
    return total;
  }

  @Override
  protected List<Object> getExternalCalculationConditions() {
    List<Object> result = new ArrayList<Object>();
    if (!operator.supportsBasicCalculation()) {
      result.add(this);
    }
    if (!left.supportsBasicCalculation()) {
      result.add(left);
    }
    if (!right.supportsBasicCalculation()) {
      result.add(right);
    }
    return result;
  }

  public OBinaryCondition isIndexedFunctionCondition(PDocumentType iSchemaClass, PDatabase database) {
    if (left.isIndexedFunctionCal()) {
      return this;
    }
    return null;
  }

  public long estimateIndexed(OFromClause target, OCommandContext context) {
    return left.estimateIndexedFunction(target, context, operator, right.execute((OResult) null, context));
  }

  public Iterable<PRecord> executeIndexedFunction(OFromClause target, OCommandContext context) {
    return left.executeIndexedFunction(target, context, operator, right.execute((OResult) null, context));
  }

  /**
   * tests if current expression involves an indexed funciton AND that function can also be executed without using the index
   *
   * @param target  the query target
   * @param context the execution context
   *
   * @return true if current expression involves an indexed function AND that function can be used on this target, false otherwise
   */
  public boolean canExecuteIndexedFunctionWithoutIndex(OFromClause target, OCommandContext context) {
    return left.canExecuteIndexedFunctionWithoutIndex(target, context, operator, right.execute((OResult) null, context));
  }

  /**
   * tests if current expression involves an indexed function AND that function can be used on this target
   *
   * @param target  the query target
   * @param context the execution context
   *
   * @return true if current expression involves an indexed function AND that function can be used on this target, false otherwise
   */
  public boolean allowsIndexedFunctionExecutionOnTarget(OFromClause target, OCommandContext context) {
    return left.allowsIndexedFunctionExecutionOnTarget(target, context, operator, right.execute((OResult) null, context));
  }

  /**
   * tests if current expression involves an indexed function AND the function has also to be executed after the index search. In
   * some cases, the index search is accurate, so this condition can be excluded from further evaluation. In other cases the result
   * from the index is a superset of the expected result, so the function has to be executed anyway for further filtering
   *
   * @param target  the query target
   * @param context the execution context
   *
   * @return true if current expression involves an indexed function AND the function has also to be executed after the index
   * search.
   */
  public boolean executeIndexedFunctionAfterIndexSearch(OFromClause target, OCommandContext context) {
    return left.executeIndexedFunctionAfterIndexSearch(target, context, operator, right.execute((OResult) null, context));
  }

  public List<OBinaryCondition> getIndexedFunctionConditions(PDocumentType iSchemaClass, PDatabase database) {
    if (left.isIndexedFunctionCal()) {
      return Collections.singletonList(this);
    }
    return null;
  }

  @Override
  public boolean needsAliases(Set<String> aliases) {
    if (left.needsAliases(aliases)) {
      return true;
    }
    if (right.needsAliases(aliases)) {
      return true;
    }
    return false;
  }

  @Override
  public OBinaryCondition copy() {
    OBinaryCondition result = new OBinaryCondition(-1);
    result.left = left.copy();
    result.operator = (OBinaryCompareOperator) operator.copy();
    result.right = right.copy();
    return result;
  }

  @Override
  public void extractSubQueries(SubQueryCollector collector) {
    left.extractSubQueries(collector);
    right.extractSubQueries(collector);
  }

  @Override
  public boolean refersToParent() {
    return left.refersToParent() || right.refersToParent();
  }

  @Override
  public Optional<OUpdateItem> transformToUpdateItem() {
    if (!checkCanTransformToUpdate()) {
      return Optional.empty();
    }
    if (operator instanceof OEqualsCompareOperator) {
      OUpdateItem result = new OUpdateItem(-1);
      result.operator = OUpdateItem.OPERATOR_EQ;
      OBaseExpression baseExp = ((OBaseExpression) left.mathExpression);
      result.left = baseExp.identifier.suffix.identifier.copy();
      result.leftModifier = baseExp.modifier == null ? null : baseExp.modifier.copy();
      result.right = right.copy();
      return Optional.of(result);
    }
    return super.transformToUpdateItem();
  }

  private boolean checkCanTransformToUpdate() {
    if (left == null || left.mathExpression == null || !(left.mathExpression instanceof OBaseExpression)) {
      return false;
    }
    OBaseExpression base = (OBaseExpression) left.mathExpression;
    if (base.identifier == null || base.identifier.suffix == null || base.identifier.suffix.identifier == null) {
      return false;
    }
    return true;
  }

  public OExpression getLeft() {
    return left;
  }

  public OBinaryCompareOperator getOperator() {
    return operator;
  }

  public OExpression getRight() {
    return right;
  }

  public void setLeft(OExpression left) {
    this.left = left;
  }

  public void setOperator(OBinaryCompareOperator operator) {
    this.operator = operator;
  }

  public void setRight(OExpression right) {
    this.right = right;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OBinaryCondition that = (OBinaryCondition) o;

    if (left != null ? !left.equals(that.left) : that.left != null)
      return false;
    if (operator != null ? !operator.equals(that.operator) : that.operator != null)
      return false;
    if (right != null ? !right.equals(that.right) : that.right != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = left != null ? left.hashCode() : 0;
    result = 31 * result + (operator != null ? operator.hashCode() : 0);
    result = 31 * result + (right != null ? right.hashCode() : 0);
    return result;
  }

  @Override
  public List<String> getMatchPatternInvolvedAliases() {
    List<String> leftX = left.getMatchPatternInvolvedAliases();
    List<String> rightX = right.getMatchPatternInvolvedAliases();
    if (leftX == null) {
      return rightX;
    }
    if (rightX == null) {
      return leftX;
    }

    List<String> result = new ArrayList<String>();
    result.addAll(leftX);
    result.addAll(rightX);
    return result;
  }

  @Override
  public void translateLuceneOperator() {
    if (operator instanceof OLuceneOperator) {
      OExpression newLeft = new OExpression(-1);
      newLeft.mathExpression = new OBaseExpression(-1);
      OBaseIdentifier identifirer = new OBaseIdentifier(-1);
      ((OBaseExpression) newLeft.mathExpression).identifier = identifirer;
      identifirer.levelZero = new OLevelZeroIdentifier(-1);
      OFunctionCall function = new OFunctionCall(-1);
      identifirer.levelZero.functionCall = function;
      function.name = new OIdentifier("search_fields");
      function.params = new ArrayList<>();
      function.params.add(fieldNamesToStrings(left));
      function.params.add(right);
      left = newLeft;

      operator = new OEqualsCompareOperator(-1);
      right = new OExpression(-1);
      right.booleanValue = true;
    }
  }

  private OExpression fieldNamesToStrings(OExpression left) {
    if (left.isBaseIdentifier()) {
      OIdentifier identifier = ((OBaseExpression) left.mathExpression).identifier.suffix.identifier;
      OCollection newColl = new OCollection(-1);
      newColl.expressions = new ArrayList<>();
      newColl.expressions.add(identifierToStringExpr(identifier));
      OExpression result = new OExpression(-1);
      OBaseExpression newBase = new OBaseExpression(-1);
      result.mathExpression = newBase;
      newBase.identifier = new OBaseIdentifier(-1);
      newBase.identifier.levelZero = new OLevelZeroIdentifier(-1);
      newBase.identifier.levelZero.collection = newColl;
      return result;
    } else if (left.mathExpression instanceof OBaseExpression) {
      OBaseExpression base = (OBaseExpression) left.mathExpression;
      if (base.identifier != null && base.identifier.levelZero != null && base.identifier.levelZero.collection != null) {
        OCollection coll = base.identifier.levelZero.collection;

        OCollection newColl = new OCollection(-1);
        newColl.expressions = new ArrayList<>();

        for (OExpression exp : coll.expressions) {
          if (exp.isBaseIdentifier()) {
            OIdentifier identifier = ((OBaseExpression) exp.mathExpression).identifier.suffix.identifier;
            OExpression val = identifierToStringExpr(identifier);
            newColl.expressions.add(val);
          } else {
            throw new PCommandExecutionException("Cannot execute because of invalid LUCENE expression");
          }
        }
        OExpression result = new OExpression(-1);
        OBaseExpression newBase = new OBaseExpression(-1);
        result.mathExpression = newBase;
        newBase.identifier = new OBaseIdentifier(-1);
        newBase.identifier.levelZero = new OLevelZeroIdentifier(-1);
        newBase.identifier.levelZero.collection = newColl;
        return result;
      }
    }
    throw new PCommandExecutionException("Cannot execute because of invalid LUCENE expression");
  }

  private OExpression identifierToStringExpr(OIdentifier identifier) {
    OBaseExpression bexp = new OBaseExpression(identifier.getStringValue());

    OExpression result = new OExpression(-1);
    result.mathExpression = bexp;
    return result;
  }

  public OResult serialize() {
    OResultInternal result = new OResultInternal();
    result.setProperty("left", left.serialize());
    result.setProperty("operator", operator.getClass().getName());
    result.setProperty("right", right.serialize());
    return result;
  }

  public void deserialize(OResult fromResult) {
    left = new OExpression(-1);
    left.deserialize(fromResult.getProperty("left"));
    try {
      operator = (OBinaryCompareOperator) Class.forName(String.valueOf(fromResult.getProperty("operator"))).newInstance();
    } catch (Exception e) {
      throw new PCommandExecutionException(e);
    }
    right = new OExpression(-1);
    right.deserialize(fromResult.getProperty("right"));
  }

  @Override
  public boolean isCacheable() {
    return left.isCacheable() && right.isCacheable();
  }

}
/* JavaCC - OriginalChecksum=99ed1dd2812eb730de8e1931b1764da5 (do not edit this line) */

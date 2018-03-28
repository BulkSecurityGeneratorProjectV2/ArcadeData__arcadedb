package com.arcadedb.sql.executor;

import com.arcadedb.exception.PTimeoutException;
import com.arcadedb.sql.parser.OIndexIdentifier;
import com.arcadedb.sql.parser.OInsertBody;

/**
 * Created by luigidellaquila on 20/03/17.
 */
public class InsertIntoIndexStep extends AbstractExecutionStep {
  private final OIndexIdentifier targetIndex;
  private final OInsertBody      body;

  boolean executed = false;

  public InsertIntoIndexStep(OIndexIdentifier targetIndex, OInsertBody insertBody, OCommandContext ctx, boolean profilingEnabled) {
    super(ctx, profilingEnabled);
    this.targetIndex = targetIndex;
    this.body = insertBody;
  }

  @Override
  public OResultSet syncPull(OCommandContext ctx, int nRecords) throws PTimeoutException {
    getPrev().ifPresent(x -> x.syncPull(ctx, nRecords));
    throw new UnsupportedOperationException();
//    return new OResultSet() {
//      @Override
//      public boolean hasNext() {
//        return !executed;
//      }
//
//      @Override
//      public OResult next() {
//        if (!hasNext()) {
//          throw new IllegalStateException();
//        }
//        //TODO
//        OIndex<?> index = ctx.getDatabase().getMetadata().getIndexManager().getIndex(targetIndex.getIndexName());
//        if (index == null) {
//          throw new PCommandExecutionException("Index not found: " + targetIndex);
//        }
//        List<OInsertSetExpression> setExps = body.getSetExpressions();
//        if (body.getContent() != null) {
//          throw new PCommandExecutionException("Invalid expression: INSERT INTO INDEX:... CONTENT ...");
//        }
//        int count;
//        if (setExps != null) {
//          count = handleSet(setExps, index, ctx);
//        } else {
//          count = handleKeyValues(body.getIdentifierList(), body.getValueExpressions(), index, ctx);
//        }
//
//        executed = true;
//        OResultInternal result = new OResultInternal();
//        result.setProperty("count", count);
//        return result;
//      }
//
//      private int handleKeyValues(List<OIdentifier> identifierList, List<List<OExpression>> setExpressions, OIndex index,
//          OCommandContext ctx) {
//        OExpression keyExp = null;
//        OExpression valueExp = null;
//        if (identifierList == null || setExpressions == null) {
//          throw new PCommandExecutionException("Invalid insert expression");
//        }
//        int count = 0;
//        for (List<OExpression> valList : setExpressions) {
//          if (identifierList.size() != valList.size()) {
//            throw new PCommandExecutionException("Invalid insert expression");
//          }
//          for (int i = 0; i < identifierList.size(); i++) {
//            OIdentifier key = identifierList.get(i);
//            if (key.getStringValue().equalsIgnoreCase("key")) {
//              keyExp = valList.get(i);
//            }
//            if (key.getStringValue().equalsIgnoreCase("rid")) {
//              valueExp = valList.get(i);
//            }
//          }
//          count += doExecute(index, ctx, keyExp, valueExp);
//        }
//        if (keyExp == null || valueExp == null) {
//          throw new PCommandExecutionException("Invalid insert expression");
//        }
//        return count;
//      }
//
//      private int handleSet(List<OInsertSetExpression> setExps, OIndex index, OCommandContext ctx) {
//        OExpression keyExp = null;
//        OExpression valueExp = null;
//        for (OInsertSetExpression exp : setExps) {
//          if (exp.getLeft().getStringValue().equalsIgnoreCase("key")) {
//            keyExp = exp.getRight();
//          } else if (exp.getLeft().getStringValue().equalsIgnoreCase("rid")) {
//            valueExp = exp.getRight();
//          } else {
//            throw new PCommandExecutionException("Cannot set " + exp + " on index");
//          }
//        }
//        if (keyExp == null || valueExp == null) {
//          throw new PCommandExecutionException("Invalid insert expression");
//        }
//        return doExecute(index, ctx, keyExp, valueExp);
//      }
//
//      private int doExecute(OIndex index, OCommandContext ctx, OExpression keyExp, OExpression valueExp) {
//        int count = 0;
//        Object key = keyExp.execute((OResult) null, ctx);
//        Object value = valueExp.execute((OResult) null, ctx);
//        if (value instanceof PIdentifiable) {
//          index.put(key, (PIdentifiable) value);
//          count++;
//        } else if (value instanceof OResult && ((OResult) value).isElement()) {
//          index.put(key, ((OResult) value).getElement().get());
//          count++;
//        } else if (value instanceof OResultSet) {
//          ((OResultSet) value).elementStream().forEach(x -> index.put(key, x));
//        } else if (OMultiValue.isMultiValue(value)) {
//          Iterator iterator = OMultiValue.getMultiValueIterator(value);
//          while (iterator.hasNext()) {
//            Object item = iterator.next();
//            if (value instanceof PIdentifiable) {
//              index.put(key, (PIdentifiable) value);
//              count++;
//            } else if (value instanceof OResult && ((OResult) value).isElement()) {
//              index.put(key, ((OResult) value).getElement().get());
//              count++;
//            } else {
//              throw new PCommandExecutionException("Cannot insert into index " + value);
//            }
//          }
//        }
//        return count;
//      }
//
//      @Override
//      public void close() {
//
//      }
//
//      @Override
//      public Optional<OExecutionPlan> getExecutionPlan() {
//        return null;
//      }
//
//      @Override
//      public Map<String, Long> getQueryStats() {
//        return null;
//      }
//    };
  }

}

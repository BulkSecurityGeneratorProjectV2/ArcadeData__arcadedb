package com.arcadedb.sql.executor;

import com.arcadedb.database.Document;
import com.arcadedb.database.Identifiable;
import com.arcadedb.exception.CommandExecutionException;
import com.arcadedb.sql.parser.*;

import java.util.*;

/**
 * Created by luigidellaquila on 14/10/16.
 */
public class MatchMultiEdgeTraverser extends MatchEdgeTraverser {
  public MatchMultiEdgeTraverser(Result lastUpstreamRecord, EdgeTraversal edge) {
    super(lastUpstreamRecord, edge);
  }

  protected Iterable<ResultInternal> traversePatternEdge(Identifiable startingPoint, CommandContext iCommandContext) {

    Iterable possibleResults = null;
    //    if (this.edge.edge.item.getFilter() != null) {
    //      String alias = this.edge.edge.item.getFilter().getAlias();
    //      Object matchedNodes = iCommandContext.getVariable(MatchPrefetchStep.PREFETCHED_MATCH_ALIAS_PREFIX + alias);
    //      if (matchedNodes != null) {
    //        if (matchedNodes instanceof Iterable) {
    //          possibleResults = (Iterable) matchedNodes;
    //        } else {
    //          possibleResults = Collections.singleton(matchedNodes);
    //        }
    //      }
    //    }

    MultiMatchPathItem item = (MultiMatchPathItem) this.item;
    List<ResultInternal> result = new ArrayList<>();

    List<Object> nextStep = new ArrayList<>();
    nextStep.add(startingPoint);

    Object oldCurrent = iCommandContext.getVariable("$current");
    for (MatchPathItem sub : item.getItems()) {
      List<ResultInternal> rightSide = new ArrayList<>();
      for (Object o : nextStep) {
        WhereClause whileCond = sub.getFilter() == null ? null : sub.getFilter().getWhileCondition();

        MethodCall method = sub.getMethod();
        if (sub instanceof MatchPathItemFirst) {
          method = ((MatchPathItemFirst) sub).getFunction().toMethod();
        }

        if (whileCond != null) {
          Object current = o;
          if (current instanceof Result) {
            current = ((Result) current).getElement().orElse(null);
          }
          MatchEdgeTraverser subtraverser = new MatchEdgeTraverser(null, sub);
          subtraverser.executeTraversal(iCommandContext, sub, (Identifiable) current, 0, null).forEach(x -> rightSide.add(x));

        } else {
          iCommandContext.setVariable("$current", o);
          Object nextSteps = method.execute(o, possibleResults, iCommandContext);
          if (nextSteps instanceof Collection) {
            ((Collection) nextSteps).stream().map(x -> toOResultInternal(x)).filter(Objects::nonNull)
                .forEach(i -> rightSide.add((ResultInternal) i));
          } else if (nextSteps instanceof Document) {
            rightSide.add(new ResultInternal((Document) nextSteps));
          } else if (nextSteps instanceof ResultInternal) {
            rightSide.add((ResultInternal) nextSteps);
          } else if (nextSteps instanceof Iterable) {
            for (Object step : (Iterable) nextSteps) {
              ResultInternal converted = toOResultInternal(step);
              if (converted != null) {
                rightSide.add(converted);
              }
            }
          } else if (nextSteps instanceof Iterator) {
            Iterator iterator = (Iterator) nextSteps;
            while (iterator.hasNext()) {
              ResultInternal converted = toOResultInternal(iterator.next());
              if (converted != null) {
                rightSide.add(converted);
              }
            }
          }
        }
      }
      nextStep = (List) rightSide;
      result = rightSide;
    }

    iCommandContext.setVariable("$current", oldCurrent);
    //    return (qR instanceof Iterable) ? (Iterable) qR : Collections.singleton((PIdentifiable) qR);
    return (Iterable) result;
  }

  private ResultInternal toOResultInternal(Object x) {
    if (x instanceof ResultInternal) {
      return (ResultInternal) x;
    }
    if (x instanceof Document) {
      return new ResultInternal((Document) x);
    }
    throw new CommandExecutionException("Cannot execute traversal on " + x);
  }
}

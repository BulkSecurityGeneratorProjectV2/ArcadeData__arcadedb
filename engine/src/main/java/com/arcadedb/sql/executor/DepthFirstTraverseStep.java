package com.arcadedb.sql.executor;

import com.arcadedb.database.Document;
import com.arcadedb.database.Identifiable;
import com.arcadedb.database.RID;
import com.arcadedb.sql.parser.PInteger;
import com.arcadedb.sql.parser.TraverseProjectionItem;
import com.arcadedb.sql.parser.WhereClause;

import java.util.*;

/**
 * Created by luigidellaquila on 26/10/16.
 */
public class DepthFirstTraverseStep extends AbstractTraverseStep {

  public DepthFirstTraverseStep(List<TraverseProjectionItem> projections, WhereClause whileClause, PInteger maxDepth,
      CommandContext ctx, boolean profilingEnabled) {
    super(projections, whileClause, maxDepth, ctx, profilingEnabled);
  }

  @Override
  protected void fetchNextEntryPoints(CommandContext ctx, int nRecords) {
    ResultSet nextN = getPrev().get().syncPull(ctx, nRecords);
    while (nextN.hasNext()) {
      Result item = toTraverseResult(nextN.next());
      if (item == null) {
        continue;
      }
      ((ResultInternal) item).setMetadata("$depth", 0);

      ArrayDeque stack = new ArrayDeque();
      item.getIdentity().ifPresent(x -> stack.push(x));
      ((ResultInternal) item).setMetadata("$stack", stack);

      List<Identifiable> path = new ArrayList<>();
      path.add(item.getIdentity().get());
      ((ResultInternal) item).setMetadata("$path", path);

      if (item != null && item.isElement() && !traversed.contains(item.getElement().get().getIdentity())) {
        tryAddEntryPoint(item, ctx);
        traversed.add(item.getElement().get().getIdentity());
      }
    }
  }

  private Result toTraverseResult(Result item) {
    TraverseResult res = null;
    if (item instanceof TraverseResult) {
      res = (TraverseResult) item;
    } else if (item.isElement()) {
      res = new TraverseResult();
      res.setElement(item.getElement().get());
      res.depth = 0;
    } else if (item.getPropertyNames().size() == 1) {
      Object val = item.getProperty(item.getPropertyNames().iterator().next());
      if (val instanceof Document) {
        res = new TraverseResult();
        res.setElement((Document) val);
        res.depth = 0;
        res.setMetadata("$depth", 0);
      } else if (val instanceof RID) {
        throw new UnsupportedOperationException("manage prid");
      }
    }

    return res;
  }

  @Override
  protected void fetchNextResults(CommandContext ctx, int nRecords) {
    if (!this.entryPoints.isEmpty()) {
      TraverseResult item = (TraverseResult) this.entryPoints.remove(0);
      this.results.add(item);
      for (TraverseProjectionItem proj : projections) {
        Object nextStep = proj.execute(item, ctx);
        if (this.maxDepth == null || this.maxDepth.getValue().intValue() > item.depth) {
          addNextEntryPoints(nextStep, item.depth + 1, (List) item.getMetadata("$path"), ctx);
        }
      }
    }
  }

  private void addNextEntryPoints(Object nextStep, int depth, List<Identifiable> path, CommandContext ctx) {
    if (nextStep instanceof Identifiable) {
      addNextEntryPoint(((Identifiable) nextStep), depth, path, ctx);
    } else if (nextStep instanceof Iterable) {
      addNextEntryPoints(((Iterable) nextStep).iterator(), depth, path, ctx);
    } else if (nextStep instanceof Result) {
      addNextEntryPoint(((Result) nextStep), depth, path, ctx);
    }
  }

  private void addNextEntryPoints(Iterator nextStep, int depth, List<Identifiable> path, CommandContext ctx) {
    while (nextStep.hasNext()) {
      addNextEntryPoints(nextStep.next(), depth, path, ctx);
    }
  }

  private void addNextEntryPoint(Identifiable nextStep, int depth, List<Identifiable> path, CommandContext ctx) {
    if (this.traversed.contains(nextStep.getIdentity())) {
      return;
    }
    TraverseResult res = new TraverseResult();
    if (nextStep instanceof Document) {
      res.setElement((Document) nextStep);
    } else {
      throw new UnsupportedOperationException("TODO");
    }
    res.depth = depth;
    res.setMetadata("$depth", depth);

    List<Identifiable> newPath = new ArrayList<>();
    newPath.addAll(path);
    newPath.add(res.getIdentity().get());
    res.setMetadata("$path", newPath);

    List reverseStack = new ArrayList();
    reverseStack.addAll(newPath);
    Collections.reverse(reverseStack);
    ArrayDeque newStack = new ArrayDeque();
    newStack.addAll(reverseStack);

    res.setMetadata("$stack", newStack);

    tryAddEntryPoint(res, ctx);
  }

  private void addNextEntryPoint(Result nextStep, int depth, List<Identifiable> path, CommandContext ctx) {
    if (!nextStep.isElement()) {
      return;
    }
    if (this.traversed.contains(nextStep.getElement().get().getIdentity())) {
      return;
    }
    if (nextStep instanceof TraverseResult) {
      ((TraverseResult) nextStep).depth = depth;
      ((TraverseResult) nextStep).setMetadata("$depth", depth);
      List<Identifiable> newPath = new ArrayList<>();
      newPath.addAll(path);
      nextStep.getIdentity().ifPresent(x -> newPath.add(x.getIdentity()));
      ((TraverseResult) nextStep).setMetadata("$path", newPath);

      List reverseStack = new ArrayList();
      reverseStack.addAll(newPath);
      Collections.reverse(reverseStack);
      ArrayDeque newStack = new ArrayDeque();
      newStack.addAll(reverseStack);
      ((TraverseResult) nextStep).setMetadata("$stack", newStack);

      tryAddEntryPoint(nextStep, ctx);
    } else {
      TraverseResult res = new TraverseResult();
      res.setElement(nextStep.getElement().get());
      res.depth = depth;
      res.setMetadata("$depth", depth);
      List<Identifiable> newPath = new ArrayList<>();
      newPath.addAll(path);
      nextStep.getIdentity().ifPresent(x -> newPath.add(x.getIdentity()));
      ((TraverseResult) nextStep).setMetadata("$path", newPath);

      List reverseStack = new ArrayList();
      reverseStack.addAll(newPath);
      Collections.reverse(reverseStack);
      ArrayDeque newStack = new ArrayDeque();
      newStack.addAll(reverseStack);
      ((TraverseResult) nextStep).setMetadata("$stack", newStack);

      tryAddEntryPoint(res, ctx);
    }
  }

  private void tryAddEntryPoint(Result res, CommandContext ctx) {
    if (whileClause == null || whileClause.matchesFilters(res, ctx)) {
      this.entryPoints.add(0, res);
    }
    traversed.add(res.getElement().get().getIdentity());
  }

  @Override
  public String prettyPrint(int depth, int indent) {
    String spaces = ExecutionStepInternal.getIndent(depth, indent);
    StringBuilder result = new StringBuilder();
    result.append(spaces);
    result.append("+ DEPTH-FIRST TRAVERSE \n");
    result.append(spaces);
    result.append("  " + projections.toString());
    if (whileClause != null) {
      result.append("\n");
      result.append(spaces);
      result.append("WHILE " + whileClause.toString());
    }
    return result.toString();
  }
}

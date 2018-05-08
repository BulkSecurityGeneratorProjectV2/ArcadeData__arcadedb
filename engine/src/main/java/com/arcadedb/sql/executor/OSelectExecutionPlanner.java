package com.arcadedb.sql.executor;

import com.arcadedb.database.Database;
import com.arcadedb.database.Identifiable;
import com.arcadedb.database.RID;
import com.arcadedb.exception.CommandExecutionException;
import com.arcadedb.index.PIndex;
import com.arcadedb.schema.PDocumentType;
import com.arcadedb.sql.parser.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Luigi Dell'Aquila (l.dellaquila-(at)-orientdb.com)
 */
public class OSelectExecutionPlanner {

  private static String LOCAL_NODE_NAME = "local";
  QueryPlanningInfo info;
  SelectStatement   statement;

  public OSelectExecutionPlanner(SelectStatement oSelectStatement) {
    this.statement = oSelectStatement;
  }

  private void init() {
    //copying the content, so that it can be manipulated and optimized
    info = new QueryPlanningInfo();
    info.projection = this.statement.getProjection() == null ? null : this.statement.getProjection().copy();
    info.projection = translateDistinct(info.projection);
    info.distinct = info.projection == null ? false : info.projection.isDistinct();
    if (info.projection != null) {
      info.projection.setDistinct(false);
    }

    info.target = this.statement.getTarget();
    info.whereClause = this.statement.getWhereClause() == null ? null : this.statement.getWhereClause().copy();
    info.whereClause = translateLucene(info.whereClause);
    info.perRecordLetClause = this.statement.getLetClause() == null ? null : this.statement.getLetClause().copy();
    info.groupBy = this.statement.getGroupBy() == null ? null : this.statement.getGroupBy().copy();
    info.orderBy = this.statement.getOrderBy() == null ? null : this.statement.getOrderBy().copy();
    info.unwind = this.statement.getUnwind() == null ? null : this.statement.getUnwind().copy();
    info.skip = this.statement.getSkip();
    info.limit = this.statement.getLimit();
//    info.lockRecord = this.statement.getLockRecord();

  }

  public InternalExecutionPlan createExecutionPlan(CommandContext ctx, boolean enableProfiling) {
    Database db = ctx.getDatabase();
    if (!enableProfiling && statement.executinPlanCanBeCached()) {
      ExecutionPlan plan = OExecutionPlanCache.get(statement.getOriginalStatement(), ctx, db);
      if (plan != null) {
        return (InternalExecutionPlan) plan;
      }
    }

    long planningStart = System.currentTimeMillis();

    init();
    SelectExecutionPlan result = new SelectExecutionPlan(ctx);

    if (info.expand && info.distinct) {
      throw new CommandExecutionException("Cannot execute a statement with DISTINCT expand(), please use a subquery");
    }

    optimizeQuery(info);

    if (handleHardwiredOptimizations(result, ctx, enableProfiling)) {
      return result;
    }

    handleGlobalLet(result, info, ctx, enableProfiling);

    calculateShardingStrategy(info, ctx);

    handleFetchFromTarger(result, info, ctx, enableProfiling);

    if (info.globalLetPresent) {
      // do the raw fetch remotely, then do the rest on the coordinator
      buildDistributedExecutionPlan(result, info, ctx, enableProfiling);
    }

    handleLet(result, info, ctx, enableProfiling);

    handleWhere(result, info, ctx, enableProfiling);

    // TODO optimization: in most cases the projections can be calculated on remote nodes
    buildDistributedExecutionPlan(result, info, ctx, enableProfiling);

    handleLockRecord(result, info, ctx, enableProfiling);

    handleProjectionsBlock(result, info, ctx, enableProfiling);

    if (!enableProfiling && statement.executinPlanCanBeCached() && result.canBeCached()
        && OExecutionPlanCache.getLastInvalidation(db) < planningStart) {
      OExecutionPlanCache.put(statement.getOriginalStatement(), result, ctx.getDatabase());
    }
    return result;
  }

  private void handleLockRecord(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx, boolean enableProfiling) {
    //TODO
//    if (info.lockRecord != null) {
//      result.chain(new LockRecordStep(info.lockRecord, ctx, enableProfiling));
//    }
  }

  public static void handleProjectionsBlock(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean enableProfiling) {
    handleProjectionsBeforeOrderBy(result, info, ctx, enableProfiling);

    if (info.expand || info.unwind != null) {

      handleProjections(result, info, ctx, enableProfiling);
      handleExpand(result, info, ctx, enableProfiling);
      handleUnwind(result, info, ctx, enableProfiling);
      handleOrderBy(result, info, ctx, enableProfiling);
      if (info.skip != null) {
        result.chain(new SkipExecutionStep(info.skip, ctx, enableProfiling));
      }
      if (info.limit != null) {
        result.chain(new LimitExecutionStep(info.limit, ctx, enableProfiling));
      }
    } else {
      handleOrderBy(result, info, ctx, enableProfiling);
      if (info.distinct) {
        handleProjections(result, info, ctx, enableProfiling);
        handleDistinct(result, info, ctx, enableProfiling);
        if (info.skip != null) {
          result.chain(new SkipExecutionStep(info.skip, ctx, enableProfiling));
        }
        if (info.limit != null) {
          result.chain(new LimitExecutionStep(info.limit, ctx, enableProfiling));
        }
      } else {
        if (info.skip != null) {
          result.chain(new SkipExecutionStep(info.skip, ctx, enableProfiling));
        }
        if (info.limit != null) {
          result.chain(new LimitExecutionStep(info.limit, ctx, enableProfiling));
        }
        handleProjections(result, info, ctx, enableProfiling);
      }
    }
  }

  private void buildDistributedExecutionPlan(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean enableProfiling) {
    if (info.distributedFetchExecutionPlans == null) {
      return;
    }
//    String currentNode =  ctx.getDatabase().getLocalNodeName();
    String currentNode = LOCAL_NODE_NAME;
    if (info.distributedFetchExecutionPlans.size() == 1) {
      if (info.distributedFetchExecutionPlans.get(currentNode) != null) {
        //everything is executed on local server
        SelectExecutionPlan localSteps = info.distributedFetchExecutionPlans.get(currentNode);
        for (ExecutionStep step : localSteps.getSteps()) {
          result.chain((ExecutionStepInternal) step);
        }
      } else {
        //everything is executed on a single remote node
        String node = info.distributedFetchExecutionPlans.keySet().iterator().next();
        SelectExecutionPlan subPlan = info.distributedFetchExecutionPlans.get(node);
        DistributedExecutionStep step = new DistributedExecutionStep(subPlan, node, ctx, enableProfiling);
        result.chain(step);
      }
      info.distributedFetchExecutionPlans = null;
    } else {
      //sharded fetching
      List<ExecutionPlan> subPlans = new ArrayList<>();
      for (Map.Entry<String, SelectExecutionPlan> entry : info.distributedFetchExecutionPlans.entrySet()) {
        if (entry.getKey().equals(currentNode)) {
          subPlans.add(entry.getValue());
        } else {
          DistributedExecutionStep step = new DistributedExecutionStep(entry.getValue(), entry.getKey(), ctx, enableProfiling);
          SelectExecutionPlan subPlan = new SelectExecutionPlan(ctx);
          subPlan.chain(step);
          subPlans.add(subPlan);
        }
      }
    }
    info.distributedPlanCreated = true;
  }

  /**
   * based on the cluster/server map and the query target, this method tries to find an optimal strategy to execute the query on the
   * cluster.
   *
   * @param info
   * @param ctx
   */
  private void calculateShardingStrategy(QueryPlanningInfo info, CommandContext ctx) {
    Database db = ctx.getDatabase();
    info.distributedFetchExecutionPlans = new LinkedHashMap<>();

    //TODO remove all this
//    Map<String, Set<String>> clusterMap = db.getActiveClusterMap();
    Map<String, Set<String>> clusterMap = new HashMap<>();
    clusterMap.put(LOCAL_NODE_NAME, new HashSet<>());
    clusterMap.get(LOCAL_NODE_NAME).add("*");


    Set<String> queryClusters = calculateTargetClusters(info, ctx);
    if (queryClusters == null || queryClusters.size() == 0) {//no target
      String localNode = LOCAL_NODE_NAME;
      info.serverToClusters = new LinkedHashMap<>();
      info.serverToClusters.put(localNode, clusterMap.get(localNode));
      info.distributedFetchExecutionPlans.put(localNode, new SelectExecutionPlan(ctx));
      return;
    }

//    Set<String> serversWithAllTheClusers = getServersThatHasAllClusters(clusterMap, queryClusters);
//    if (serversWithAllTheClusers.isEmpty()) {
    // sharded query
    Map<String, Set<String>> minimalSetOfNodes = getMinimalSetOfNodesForShardedQuery(LOCAL_NODE_NAME, clusterMap, queryClusters);
    if (minimalSetOfNodes == null) {
      throw new CommandExecutionException("Cannot execute sharded query");
    }
    info.serverToClusters = minimalSetOfNodes;
    for (String node : info.serverToClusters.keySet()) {
      info.distributedFetchExecutionPlans.put(node, new SelectExecutionPlan(ctx));
    }
//    } else {
//      // all on a node
//      String targetNode = serversWithAllTheClusers.contains(db.getLocalNodeName()) ?
//          db.getLocalNodeName() :
//          serversWithAllTheClusers.iterator().next();
//      info.serverToClusters = new HashMap<>();
//      info.serverToClusters.put(targetNode, queryClusters);
//    }
  }

  /**
   * given a cluster map and a set of clusters involved in a query, tries to calculate the minimum number of nodes that will have to
   * be involved in the query execution, with clusters involved for each node.
   *
   * @param clusterMap
   * @param queryClusters
   *
   * @return a map that has node names as a key and clusters (data files) for each node as a value
   */
  private Map<String, Set<String>> getMinimalSetOfNodesForShardedQuery(String localNode, Map<String, Set<String>> clusterMap,
      Set<String> queryClusters) {
    HashMap<String, Set<String>> result = new HashMap<String, Set<String>>();
    result.put(LOCAL_NODE_NAME, queryClusters);
    return result;

//    //approximate algorithm, the problem is NP-complete
//    Map<String, Set<String>> result = new LinkedHashMap<>();
//    Set<String> uncovered = new HashSet<>();
//    uncovered.addAll(queryClusters);
//
//    //try local node first
//    Set<String> nextNodeClusters = new HashSet<>();
//    Set<String> clustersForNode = clusterMap.get(localNode);
//    if (clustersForNode != null) {
//      nextNodeClusters.addAll(clustersForNode);
//    }
//    nextNodeClusters.retainAll(uncovered);
//    if (nextNodeClusters.size() > 0) {
//      result.put(localNode, nextNodeClusters);
//      uncovered.removeAll(nextNodeClusters);
//    }
//
//    while (uncovered.size() > 0) {
//      String nextNode = findItemThatCoversMore(uncovered, clusterMap);
//      nextNodeClusters = new HashSet<>();
//      nextNodeClusters.addAll(clusterMap.get(nextNode));
//      nextNodeClusters.retainAll(uncovered);
//      if (nextNodeClusters.size() == 0) {
//        throw new PCommandExecutionException(
//            "Cannot execute a sharded query: clusters [" + uncovered.stream().collect(Collectors.joining(", "))
//                + "] are not present on any node" + "\n [" + clusterMap.entrySet().stream()
//                .map(x -> "" + x.getKey() + ":(" + x.getValue().stream().collect(Collectors.joining(",")) + ")")
//                .collect(Collectors.joining(", ")) + "]");
//      }
//      result.put(nextNode, nextNodeClusters);
//      uncovered.removeAll(nextNodeClusters);
//    }
//    return result;
  }

  private String findItemThatCoversMore(Set<String> uncovered, Map<String, Set<String>> clusterMap) {
    String lastFound = null;
    int lastSize = -1;
    for (Map.Entry<String, Set<String>> nodeConfig : clusterMap.entrySet()) {
      Set<String> current = new HashSet<>();
      current.addAll(nodeConfig.getValue());
      current.retainAll(uncovered);
      int thisSize = current.size();
      if (lastFound == null || thisSize > lastSize) {
        lastFound = nodeConfig.getKey();
        lastSize = thisSize;
      }
    }
    return lastFound;

  }

  /**
   * @param clusterMap    the cluster map for current sharding configuration
   * @param queryClusters the clusters that are target of the query
   *
   * @return
   */
  private Set<String> getServersThatHasAllClusters(Map<String, Set<String>> clusterMap, Set<String> queryClusters) {
    Set<String> remainingServers = clusterMap.keySet();
    for (String cluster : queryClusters) {
      for (Map.Entry<String, Set<String>> serverConfig : clusterMap.entrySet()) {
        if (!serverConfig.getValue().contains(cluster)) {
          remainingServers.remove(serverConfig.getKey());
        }
      }
    }
    return remainingServers;
  }

  /**
   * tries to calculate which clusters will be impacted by this query
   *
   * @param info
   * @param ctx
   *
   * @return a set of cluster names this query will fetch from
   */
  private Set<String> calculateTargetClusters(QueryPlanningInfo info, CommandContext ctx) {
    if (info.target == null) {
      return Collections.EMPTY_SET;
    }

    Set<String> result = new HashSet<>();
    Database db = ctx.getDatabase();
    FromItem item = info.target.getItem();
    if (item.getRids() != null && item.getRids().size() > 0) {
      if (item.getRids().size() == 1) {
        PInteger cluster = item.getRids().get(0).getCluster();
        result.add(db.getSchema().getBucketById(cluster.getValue().intValue()).getName());
      } else {
        for (Rid rid : item.getRids()) {
          PInteger cluster = rid.getCluster();
          result.add(db.getSchema().getBucketById(cluster.getValue().intValue()).getName());
        }
      }
      return result;
    } else if (item.getInputParams() != null && item.getInputParams().size() > 0) {
      return null;
    } else if (item.getCluster() != null) {
      String name = item.getCluster().getClusterName();
      if (name == null) {
        name = db.getSchema().getBucketById(item.getCluster().getClusterNumber()).getName();
      }
      if (name != null) {
        result.add(name);
        return result;
      } else {
        return null;
      }
    } else if (item.getClusterList() != null) {
      for (Cluster cluster : item.getClusterList().toListOfClusters()) {
        String name = cluster.getClusterName();
        if (name == null) {
          name = db.getSchema().getBucketById(cluster.getClusterNumber()).getName();
        }
        if (name != null) {
          result.add(name);
        }
      }
      return result;
    } else if (item.getIndex() != null) {
      throw new UnsupportedOperationException("TODO support query on index targer...?");
//      String indexName = item.getIndex().getIndexName();
//      PIndex idx = db.getSchema().getIndexByName(indexName);
//      if (idx == null) {
//        throw new PCommandExecutionException("Index " + indexName + " does not exist");
//      }
//      result.addAll(//the index clusters);
//      if (result.isEmpty()) {
//        return null;
//      }
//      return result;
    } else if (item.getInputParam() != null) {
      return null;
    } else if (item.getIdentifier() != null) {
      String className = item.getIdentifier().getStringValue();
      PDocumentType clazz = db.getSchema().getType(className);
      if (clazz == null) {
        return null;
      }
      int[] clusterIds = clazz.getBuckets(true).stream().mapToInt(x -> x.getId()).toArray();
      for (int clusterId : clusterIds) {
        String clusterName = db.getSchema().getBucketById(clusterId).getName();
        if (clusterName != null) {
          result.add(clusterName);
        }
      }
      return result;
    }

    return null;
  }

  private WhereClause translateLucene(WhereClause whereClause) {
    if (whereClause == null) {
      return null;
    }

    if (whereClause.getBaseExpression() != null) {
      whereClause.getBaseExpression().translateLuceneOperator();
    }
    return whereClause;
  }

  /**
   * for backward compatibility, translate "distinct(foo)" to "DISTINCT foo". This method modifies the projection itself.
   *
   * @param projection the projection
   */
  protected static Projection translateDistinct(Projection projection) {
    if (projection != null && projection.getItems().size() == 1) {
      if (isDistinct(projection.getItems().get(0))) {
        projection = projection.copy();
        ProjectionItem item = projection.getItems().get(0);
        FunctionCall function = ((BaseExpression) item.getExpression().getMathExpression()).getIdentifier().getLevelZero()
            .getFunctionCall();
        Expression exp = function.getParams().get(0);
        ProjectionItem resultItem = new ProjectionItem(-1);
        resultItem.setAlias(item.getAlias());
        resultItem.setExpression(exp.copy());
        Projection result = new Projection(-1);
        result.setItems(new ArrayList<>());
        result.setDistinct(true);
        result.getItems().add(resultItem);
        return result;
      }
    }
    return projection;
  }

  /**
   * checks if a projection is a distinct(expr). In new executor the distinct() function is not supported, so "distinct(expr)" is
   * translated to "DISTINCT expr"
   *
   * @param item the projection
   *
   * @return
   */
  private static boolean isDistinct(ProjectionItem item) {
    if (item.getExpression() == null) {
      return false;
    }
    if (item.getExpression().getMathExpression() == null) {
      return false;
    }
    if (!(item.getExpression().getMathExpression() instanceof BaseExpression)) {
      return false;
    }
    BaseExpression base = (BaseExpression) item.getExpression().getMathExpression();
    if (base.getIdentifier() == null) {
      return false;
    }
    if (base.getModifier() != null) {
      return false;
    }
    if (base.getIdentifier().getLevelZero() == null) {
      return false;
    }
    FunctionCall function = base.getIdentifier().getLevelZero().getFunctionCall();
    if (function == null) {
      return false;
    }
    if (function.getName().getStringValue().equalsIgnoreCase("distinct")) {
      return true;
    }
    return false;
  }

  private boolean handleHardwiredOptimizations(SelectExecutionPlan result, CommandContext ctx, boolean profilingEnabled) {
    return handleHardwiredCountOnIndex(result, info, ctx, profilingEnabled) || handleHardwiredCountOnClass(result, info, ctx,
        profilingEnabled);
  }

  private boolean handleHardwiredCountOnClass(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean profilingEnabled) {
    Identifier targetClass = info.target == null ? null : info.target.getItem().getIdentifier();
    if (targetClass == null) {
      return false;
    }
    if (info.distinct || info.expand) {
      return false;
    }
    if (info.preAggregateProjection != null) {
      return false;
    }
    if (!isCountStar(info)) {
      return false;
    }
    if (!isMinimalQuery(info)) {
      return false;
    }
    result.chain(new CountFromClassStep(targetClass, info.projection.getAllAliases().iterator().next(), ctx, profilingEnabled));
    return true;
  }

  private boolean handleHardwiredCountOnIndex(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean profilingEnabled) {
    IndexIdentifier targetIndex = info.target == null ? null : info.target.getItem().getIndex();
    if (targetIndex == null) {
      return false;
    }
    if (info.distinct || info.expand) {
      return false;
    }
    if (info.preAggregateProjection != null) {
      return false;
    }
    if (!isCountStar(info)) {
      return false;
    }
    if (!isMinimalQuery(info)) {
      return false;
    }
    result.chain(new CountFromIndexStep(targetIndex, info.projection.getAllAliases().iterator().next(), ctx, profilingEnabled));
    return true;
  }

  /**
   * returns true if the query is minimal, ie. no WHERE condition, no SKIP/LIMIT, no UNWIND, no GROUP/ORDER BY, no LET
   *
   * @return
   */
  private boolean isMinimalQuery(QueryPlanningInfo info) {
    if (info.projectionAfterOrderBy != null || info.globalLetClause != null || info.perRecordLetClause != null
        || info.whereClause != null || info.flattenedWhereClause != null || info.groupBy != null || info.orderBy != null
        || info.unwind != null || info.skip != null || info.limit != null) {
      return false;
    }
    return true;
  }

  private boolean isCountStar(QueryPlanningInfo info) {
    if (info.aggregateProjection == null || info.projection == null || info.aggregateProjection.getItems().size() != 1
        || info.projection.getItems().size() != 1) {
      return false;
    }
    ProjectionItem item = info.aggregateProjection.getItems().get(0);
    if (!item.getExpression().toString().equalsIgnoreCase("count(*)")) {
      return false;
    }

    return true;
  }

  private boolean isCount(Projection aggregateProjection, Projection projection) {
    if (aggregateProjection == null || projection == null || aggregateProjection.getItems().size() != 1
        || projection.getItems().size() != 1) {
      return false;
    }
    ProjectionItem item = aggregateProjection.getItems().get(0);
    return item.getExpression().isCount();
  }

  public static void handleUnwind(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean profilingEnabled) {
    if (info.unwind != null) {
      result.chain(new UnwindStep(info.unwind, ctx, profilingEnabled));
    }
  }

  private static void handleDistinct(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean profilingEnabled) {
    result.chain(new DistinctExecutionStep(ctx, profilingEnabled));
  }

  private static void handleProjectionsBeforeOrderBy(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean profilingEnabled) {
    if (info.orderBy != null) {
      handleProjections(result, info, ctx, profilingEnabled);
    }
  }

  private static void handleProjections(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean profilingEnabled) {
    if (!info.projectionsCalculated && info.projection != null) {
      if (info.preAggregateProjection != null) {
        result.chain(new ProjectionCalculationStep(info.preAggregateProjection, ctx, profilingEnabled));
      }
      if (info.aggregateProjection != null) {
        result.chain(new AggregateProjectionCalculationStep(info.aggregateProjection, info.groupBy, ctx, profilingEnabled));
      }
      result.chain(new ProjectionCalculationStep(info.projection, ctx, profilingEnabled));

      info.projectionsCalculated = true;
    }
  }

  protected static void optimizeQuery(QueryPlanningInfo info) {
    splitLet(info);
    extractSubQueries(info);
    if (info.projection != null && info.projection.isExpand()) {
      info.expand = true;
      info.projection = info.projection.getExpandContent();
    }
    if (info.whereClause != null) {
      info.flattenedWhereClause = info.whereClause.flatten();
      //this helps index optimization
      info.flattenedWhereClause = moveFlattededEqualitiesLeft(info.flattenedWhereClause);
    }

    splitProjectionsForGroupBy(info);
    addOrderByProjections(info);
  }

  /**
   * splits LET clauses in global (executed once) and local (executed once per record)
   */
  private static void splitLet(QueryPlanningInfo info) {
    if (info.perRecordLetClause != null && info.perRecordLetClause.getItems() != null) {
      Iterator<LetItem> iterator = info.perRecordLetClause.getItems().iterator();
      while (iterator.hasNext()) {
        LetItem item = iterator.next();
        if (item.getExpression() != null && item.getExpression().isEarlyCalculated()) {
          iterator.remove();
          addGlobalLet(info, item.getVarName(), item.getExpression());
        } else if (item.getQuery() != null && !item.getQuery().refersToParent()) {
          iterator.remove();
          addGlobalLet(info, item.getVarName(), item.getQuery());
        }
      }
    }
  }

  /**
   * re-writes a list of flat AND conditions, moving left all the equality operations
   *
   * @param flattenedWhereClause
   *
   * @return
   */
  private static List<AndBlock> moveFlattededEqualitiesLeft(List<AndBlock> flattenedWhereClause) {
    if (flattenedWhereClause == null) {
      return null;
    }

    List<AndBlock> result = new ArrayList<>();
    for (AndBlock block : flattenedWhereClause) {
      List<BooleanExpression> equalityExpressions = new ArrayList<>();
      List<BooleanExpression> nonEqualityExpressions = new ArrayList<>();
      AndBlock newBlock = block.copy();
      for (BooleanExpression exp : newBlock.getSubBlocks()) {
        if (exp instanceof BinaryCondition) {
          if (((BinaryCondition) exp).getOperator() instanceof EqualsCompareOperator) {
            equalityExpressions.add(exp);
          } else {
            nonEqualityExpressions.add(exp);
          }
        } else {
          nonEqualityExpressions.add(exp);
        }
      }
      AndBlock newAnd = new AndBlock(-1);
      newAnd.getSubBlocks().addAll(equalityExpressions);
      newAnd.getSubBlocks().addAll(nonEqualityExpressions);
      result.add(newAnd);
    }

    return result;
  }

  /**
   * creates additional projections for ORDER BY
   */
  private static void addOrderByProjections(QueryPlanningInfo info) {
    if (info.orderApplied || info.expand || info.unwind != null || info.orderBy == null || info.orderBy.getItems().size() == 0
        || info.projection == null || info.projection.getItems() == null || (info.projection.getItems().size() == 1
        && info.projection.getItems().get(0).isAll())) {
      return;
    }

    OrderBy newOrderBy = info.orderBy == null ? null : info.orderBy.copy();
    List<ProjectionItem> additionalOrderByProjections = calculateAdditionalOrderByProjections(info.projection.getAllAliases(),
        newOrderBy);
    if (additionalOrderByProjections.size() > 0) {
      info.orderBy = newOrderBy;//the ORDER BY has changed
    }
    if (additionalOrderByProjections.size() > 0) {
      info.projectionAfterOrderBy = new Projection(-1);
      info.projectionAfterOrderBy.setItems(new ArrayList<>());
      for (String alias : info.projection.getAllAliases()) {
        info.projectionAfterOrderBy.getItems().add(projectionFromAlias(new Identifier(alias)));
      }

      for (ProjectionItem item : additionalOrderByProjections) {
        if (info.preAggregateProjection != null) {
          info.preAggregateProjection.getItems().add(item);
          info.aggregateProjection.getItems().add(projectionFromAlias(item.getAlias()));
          info.projection.getItems().add(projectionFromAlias(item.getAlias()));
        } else {
          info.projection.getItems().add(item);
        }
      }
    }
  }

  /**
   * given a list of aliases (present in the existing projections) calculates a list of additional projections to add to the
   * existing projections to allow ORDER BY calculation. The sorting clause will be modified with new replaced aliases
   *
   * @param allAliases existing aliases in the projection
   * @param orderBy    sorting clause
   *
   * @return a list of additional projections to add to the existing projections to allow ORDER BY calculation (empty if nothing has
   * to be added).
   */
  private static List<ProjectionItem> calculateAdditionalOrderByProjections(Set<String> allAliases, OrderBy orderBy) {
    List<ProjectionItem> result = new ArrayList<>();
    int nextAliasCount = 0;
    if (orderBy != null && orderBy.getItems() != null || !orderBy.getItems().isEmpty()) {
      for (OrderByItem item : orderBy.getItems()) {
        if (!allAliases.contains(item.getAlias())) {
          ProjectionItem newProj = new ProjectionItem(-1);
          if (item.getAlias() != null) {
            newProj.setExpression(new Expression(new Identifier(item.getAlias()), item.getModifier()));
          } else if (item.getRecordAttr() != null) {
            RecordAttribute attr = new RecordAttribute(-1);
            attr.setName(item.getRecordAttr());
            newProj.setExpression(new Expression(attr, item.getModifier()));
          } else if (item.getRid() != null) {
            Expression exp = new Expression(-1);
            exp.setRid(item.getRid().copy());
            newProj.setExpression(exp);
          }
          Identifier newAlias = new Identifier("_$$$ORDER_BY_ALIAS$$$_" + (nextAliasCount++));
          newProj.setAlias(newAlias);
          item.setAlias(newAlias.getStringValue());
          result.add(newProj);
        }
      }
    }
    return result;
  }

  /**
   * splits projections in three parts (pre-aggregate, aggregate and final) to efficiently manage aggregations
   */
  private static void splitProjectionsForGroupBy(QueryPlanningInfo info) {
    if (info.projection == null) {
      return;
    }

    Projection preAggregate = new Projection(-1);
    preAggregate.setItems(new ArrayList<>());
    Projection aggregate = new Projection(-1);
    aggregate.setItems(new ArrayList<>());
    Projection postAggregate = new Projection(-1);
    postAggregate.setItems(new ArrayList<>());

    boolean isSplitted = false;

    //split for aggregate projections
    AggregateProjectionSplit result = new AggregateProjectionSplit();
    for (ProjectionItem item : info.projection.getItems()) {
      result.reset();
      if (isAggregate(item)) {
        isSplitted = true;
        ProjectionItem post = item.splitForAggregation(result);
        Identifier postAlias = item.getProjectionAlias();
        postAlias = new Identifier(postAlias, true);
        post.setAlias(postAlias);
        postAggregate.getItems().add(post);
        aggregate.getItems().addAll(result.getAggregate());
        preAggregate.getItems().addAll(result.getPreAggregate());
      } else {
        preAggregate.getItems().add(item);
        //also push the alias forward in the chain
        ProjectionItem aggItem = new ProjectionItem(-1);
        aggItem.setExpression(new Expression(item.getProjectionAlias()));
        aggregate.getItems().add(aggItem);
        postAggregate.getItems().add(aggItem);
      }
    }

    //bind split projections to the execution planner
    if (isSplitted) {
      info.preAggregateProjection = preAggregate;
      if (info.preAggregateProjection.getItems() == null || info.preAggregateProjection.getItems().size() == 0) {
        info.preAggregateProjection = null;
      }
      info.aggregateProjection = aggregate;
      if (info.aggregateProjection.getItems() == null || info.aggregateProjection.getItems().size() == 0) {
        info.aggregateProjection = null;
      }
      info.projection = postAggregate;

      addGroupByExpressionsToProjections(info);
    }
  }

  private static boolean isAggregate(ProjectionItem item) {
    if (item.isAggregate()) {
      return true;
    }
    return false;
  }

  private static ProjectionItem projectionFromAlias(Identifier oIdentifier) {
    ProjectionItem result = new ProjectionItem(-1);
    result.setExpression(new Expression(oIdentifier));
    return result;
  }

  /**
   * if GROUP BY is performed on an expression that is not explicitly in the pre-aggregate projections, then that expression has to
   * be put in the pre-aggregate (only here, in subsequent steps it's removed)
   */
  private static void addGroupByExpressionsToProjections(QueryPlanningInfo info) {
    if (info.groupBy == null || info.groupBy.getItems() == null || info.groupBy.getItems().size() == 0) {
      return;
    }
    GroupBy newGroupBy = new GroupBy(-1);
    int i = 0;
    for (Expression exp : info.groupBy.getItems()) {
      if (exp.isAggregate()) {
        throw new CommandExecutionException("Cannot group by an aggregate function");
      }
      boolean found = false;
      if (info.preAggregateProjection != null) {
        for (String alias : info.preAggregateProjection.getAllAliases()) {
          //if it's a simple identifier and it's the same as one of the projections in the query,
          //then the projection itself is used for GROUP BY without recalculating; in all the other cases, it is evaluated separately
          if (alias.equals(exp.getDefaultAlias().getStringValue()) && exp.isBaseIdentifier()) {
            found = true;
            newGroupBy.getItems().add(exp);
            break;
          }
        }
      }
      if (!found) {
        ProjectionItem newItem = new ProjectionItem(-1);
        newItem.setExpression(exp);
        Identifier groupByAlias = new Identifier("_$$$GROUP_BY_ALIAS$$$_" + i);
        newItem.setAlias(groupByAlias);
        if (info.preAggregateProjection == null) {
          info.preAggregateProjection = new Projection(-1);
        }
        if (info.preAggregateProjection.getItems() == null) {
          info.preAggregateProjection.setItems(new ArrayList<>());
        }
        info.preAggregateProjection.getItems().add(newItem);
        newGroupBy.getItems().add(new Expression(groupByAlias));
      }

      info.groupBy = newGroupBy;
    }

  }

  /**
   * translates subqueries to LET statements
   */
  private static void extractSubQueries(QueryPlanningInfo info) {
    SubQueryCollector collector = new SubQueryCollector();
    if (info.perRecordLetClause != null) {
      info.perRecordLetClause.extractSubQueries(collector);
    }
    int i = 0;
    int j = 0;
    for (Map.Entry<Identifier, Statement> entry : collector.getSubQueries().entrySet()) {
      Identifier alias = entry.getKey();
      Statement query = entry.getValue();
      if (query.refersToParent()) {
        addRecordLevelLet(info, alias, query, j++);
      } else {
        addGlobalLet(info, alias, query, i++);
      }
    }
    collector.reset();

    if (info.whereClause != null) {
      info.whereClause.extractSubQueries(collector);
    }
    if (info.projection != null) {
      info.projection.extractSubQueries(collector);
    }
    if (info.orderBy != null) {
      info.orderBy.extractSubQueries(collector);
    }
    if (info.groupBy != null) {
      info.groupBy.extractSubQueries(collector);
    }

    for (Map.Entry<Identifier, Statement> entry : collector.getSubQueries().entrySet()) {
      Identifier alias = entry.getKey();
      Statement query = entry.getValue();
      if (query.refersToParent()) {
        addRecordLevelLet(info, alias, query);
      } else {
        addGlobalLet(info, alias, query);
      }
    }
  }

  private static void addGlobalLet(QueryPlanningInfo info, Identifier alias, Expression exp) {
    if (info.globalLetClause == null) {
      info.globalLetClause = new LetClause(-1);
    }
    LetItem item = new LetItem(-1);
    item.setVarName(alias);
    item.setExpression(exp);
    info.globalLetClause.addItem(item);
  }

  private static void addGlobalLet(QueryPlanningInfo info, Identifier alias, Statement stm) {
    if (info.globalLetClause == null) {
      info.globalLetClause = new LetClause(-1);
    }
    LetItem item = new LetItem(-1);
    item.setVarName(alias);
    item.setQuery(stm);
    info.globalLetClause.addItem(item);
  }

  private static void addGlobalLet(QueryPlanningInfo info, Identifier alias, Statement stm, int pos) {
    if (info.globalLetClause == null) {
      info.globalLetClause = new LetClause(-1);
    }
    LetItem item = new LetItem(-1);
    item.setVarName(alias);
    item.setQuery(stm);
    info.globalLetClause.getItems().add(pos, item);
  }

  private static void addRecordLevelLet(QueryPlanningInfo info, Identifier alias, Statement stm) {
    if (info.perRecordLetClause == null) {
      info.perRecordLetClause = new LetClause(-1);
    }
    LetItem item = new LetItem(-1);
    item.setVarName(alias);
    item.setQuery(stm);
    info.perRecordLetClause.addItem(item);
  }

  private static void addRecordLevelLet(QueryPlanningInfo info, Identifier alias, Statement stm, int pos) {
    if (info.perRecordLetClause == null) {
      info.perRecordLetClause = new LetClause(-1);
    }
    LetItem item = new LetItem(-1);
    item.setVarName(alias);
    item.setQuery(stm);
    info.perRecordLetClause.getItems().add(pos, item);
  }

  private void handleFetchFromTarger(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean profilingEnabled) {

    FromItem target = info.target == null ? null : info.target.getItem();
    for (Map.Entry<String, SelectExecutionPlan> shardedPlan : info.distributedFetchExecutionPlans.entrySet()) {
      if (target == null) {
        handleNoTarget(shardedPlan.getValue(), ctx, profilingEnabled);
      } else if (target.getIdentifier() != null) {
        Set<String> filterClusters = info.serverToClusters.get(shardedPlan.getKey());

        AndBlock ridRangeConditions = extractRidRanges(info.flattenedWhereClause, ctx);
        if (ridRangeConditions != null && !ridRangeConditions.isEmpty()) {
          info.ridRangeConditions = ridRangeConditions;
          filterClusters = filterClusters.stream()
              .filter(x -> clusterMatchesRidRange(x, ridRangeConditions, ctx.getDatabase(), ctx)).collect(Collectors.toSet());
        }

        handleClassAsTarget(shardedPlan.getValue(), filterClusters, info, ctx, profilingEnabled);
      } else if (target.getCluster() != null) {
        handleClustersAsTarget(shardedPlan.getValue(), info, Collections.singletonList(target.getCluster()), ctx, profilingEnabled);
      } else if (target.getClusterList() != null) {
        List<Cluster> allClusters = target.getClusterList().toListOfClusters();
        List<Cluster> clustersForShard = new ArrayList<>();
        for (Cluster cluster : allClusters) {
          String name = cluster.getClusterName();
          if (name == null) {
            name = ctx.getDatabase().getSchema().getBucketById(cluster.getClusterNumber()).getName();
          }
          if (name != null && info.serverToClusters.get(shardedPlan.getKey()).contains(name)) {
            clustersForShard.add(cluster);
          }
        }
        handleClustersAsTarget(shardedPlan.getValue(), info, clustersForShard, ctx, profilingEnabled);
      } else if (target.getStatement() != null) {
        handleSubqueryAsTarget(shardedPlan.getValue(), target.getStatement(), ctx, profilingEnabled);
      } else if (target.getFunctionCall() != null) {
        //        handleFunctionCallAsTarget(result, target.getFunctionCall(), ctx);//TODO
        throw new CommandExecutionException("function call as target is not supported yet");
      } else if (target.getInputParam() != null) {
        handleInputParamAsTarget(shardedPlan.getValue(), info.serverToClusters.get(shardedPlan.getKey()), info,
            target.getInputParam(), ctx, profilingEnabled);
      } else if (target.getInputParams() != null && target.getInputParams().size() > 0) {
        List<InternalExecutionPlan> plans = new ArrayList<>();
        for (InputParameter param : target.getInputParams()) {
          SelectExecutionPlan subPlan = new SelectExecutionPlan(ctx);
          handleInputParamAsTarget(subPlan, info.serverToClusters.get(shardedPlan.getKey()), info, param, ctx, profilingEnabled);
          plans.add(subPlan);
        }
        shardedPlan.getValue().chain(new ParallelExecStep(plans, ctx, profilingEnabled));
      } else if (target.getIndex() != null) {
        handleIndexAsTarget(shardedPlan.getValue(), info, target.getIndex(), null, ctx, profilingEnabled);
        if (info.serverToClusters.size() > 1) {
          shardedPlan.getValue()
              .chain(new FilterByClustersStep(info.serverToClusters.get(shardedPlan.getKey()), ctx, profilingEnabled));
        }
      } else if (target.getMetadata() != null) {
        handleMetadataAsTarget(shardedPlan.getValue(), target.getMetadata(), ctx, profilingEnabled);
      } else if (target.getRids() != null && target.getRids().size() > 0) {
        Set<String> filterClusters = info.serverToClusters.get(shardedPlan.getKey());
        List<Rid> rids = new ArrayList<>();
        for (Rid rid : target.getRids()) {
          if (filterClusters == null || isFromClusters(rid, filterClusters, ctx.getDatabase())) {
            rids.add(rid);
          }
        }
        if (rids.size() > 0) {
          handleRidsAsTarget(shardedPlan.getValue(), rids, ctx, profilingEnabled);
        } else {
          result.chain(new EmptyStep(ctx, profilingEnabled));//nothing to return
        }
      } else {
        throw new UnsupportedOperationException();
      }
    }
  }

  private boolean clusterMatchesRidRange(String clusterName, AndBlock ridRangeConditions, Database database,
      CommandContext ctx) {
    int thisClusterId = database.getSchema().getBucketByName(clusterName).getId();
    for (BooleanExpression ridRangeCondition : ridRangeConditions.getSubBlocks()) {
      if (ridRangeCondition instanceof BinaryCondition) {
        BinaryCompareOperator operator = ((BinaryCondition) ridRangeCondition).getOperator();
        RID conditionRid;

        Object obj;
        if (((BinaryCondition) ridRangeCondition).getRight().getRid() != null) {
          obj = ((BinaryCondition) ridRangeCondition).getRight().getRid().toRecordId((Result) null, ctx);
        } else {
          obj = ((BinaryCondition) ridRangeCondition).getRight().execute((Result) null, ctx);
        }

        conditionRid = ((Identifiable) obj).getIdentity();

        if (conditionRid != null) {
          int conditionClusterId = conditionRid.getBucketId();
          if (operator instanceof GtOperator || operator instanceof GeOperator) {
            if (thisClusterId < conditionClusterId) {
              return false;
            }
          } else if (operator instanceof LtOperator || operator instanceof LeOperator) {
            if (thisClusterId > conditionClusterId) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  private AndBlock extractRidRanges(List<AndBlock> flattenedWhereClause, CommandContext ctx) {
    AndBlock result = new AndBlock(-1);

    if (flattenedWhereClause == null || flattenedWhereClause.size() != 1) {
      return result;
    }
    //TODO optimization: merge multiple conditions

    for (BooleanExpression booleanExpression : flattenedWhereClause.get(0).getSubBlocks()) {
      if (isRidRange(booleanExpression, ctx)) {
        result.getSubBlocks().add(booleanExpression.copy());
      }
    }

    return result;
  }

  private boolean isRidRange(BooleanExpression booleanExpression, CommandContext ctx) {
    if (booleanExpression instanceof BinaryCondition) {
      BinaryCondition cond = ((BinaryCondition) booleanExpression);
      BinaryCompareOperator operator = cond.getOperator();
      if (isRangeOperator(operator) && cond.getLeft().toString().equalsIgnoreCase("@rid")) {
        Object obj;
        if (cond.getRight().getRid() != null) {
          obj = cond.getRight().getRid().toRecordId((Result) null, ctx);
        } else {
          obj = cond.getRight().execute((Result) null, ctx);
        }
        return obj instanceof Identifiable;
      }
    }
    return false;
  }

  private boolean isRangeOperator(BinaryCompareOperator operator) {
    return operator instanceof LtOperator || operator instanceof LeOperator || operator instanceof GtOperator
        || operator instanceof GeOperator;
  }

  private void handleInputParamAsTarget(SelectExecutionPlan result, Set<String> filterClusters, QueryPlanningInfo info,
      InputParameter inputParam, CommandContext ctx, boolean profilingEnabled) {
    Object paramValue = inputParam.getValue(ctx.getInputParameters());
    if (paramValue == null) {
      result.chain(new EmptyStep(ctx, profilingEnabled));//nothing to return
    } else if (paramValue instanceof PDocumentType) {
      FromClause from = new FromClause(-1);
      FromItem item = new FromItem(-1);
      from.setItem(item);
      item.setIdentifier(new Identifier(((PDocumentType) paramValue).getName()));
      handleClassAsTarget(result, filterClusters, from, info, ctx, profilingEnabled);
    } else if (paramValue instanceof String) {
      //strings are treated as classes
      FromClause from = new FromClause(-1);
      FromItem item = new FromItem(-1);
      from.setItem(item);
      item.setIdentifier(new Identifier((String) paramValue));
      handleClassAsTarget(result, filterClusters, from, info, ctx, profilingEnabled);
    } else if (paramValue instanceof Identifiable) {
      RID orid = ((Identifiable) paramValue).getIdentity();

      Rid rid = new Rid(-1);
      PInteger cluster = new PInteger(-1);
      cluster.setValue(orid.getBucketId());
      PInteger position = new PInteger(-1);
      position.setValue(orid.getPosition());
      rid.setLegacy(true);
      rid.setCluster(cluster);
      rid.setPosition(position);

      if (filterClusters == null || isFromClusters(rid, filterClusters, ctx.getDatabase())) {
        handleRidsAsTarget(result, Collections.singletonList(rid), ctx, profilingEnabled);
      } else {
        result.chain(new EmptyStep(ctx, profilingEnabled));//nothing to return
      }

    } else if (paramValue instanceof Iterable) {
      //try list of RIDs
      List<Rid> rids = new ArrayList<>();
      for (Object x : (Iterable) paramValue) {
        if (!(x instanceof Identifiable)) {
          throw new CommandExecutionException("Cannot use colleciton as target: " + paramValue);
        }
        RID orid = ((Identifiable) x).getIdentity();

        Rid rid = new Rid(-1);
        PInteger cluster = new PInteger(-1);
        cluster.setValue(orid.getBucketId());
        PInteger position = new PInteger(-1);
        position.setValue(orid.getPosition());
        rid.setCluster(cluster);
        rid.setPosition(position);
        if (filterClusters == null || isFromClusters(rid, filterClusters, ctx.getDatabase())) {
          rids.add(rid);
        }
      }
      if (rids.size() > 0) {
        handleRidsAsTarget(result, rids, ctx, profilingEnabled);
      } else {
        result.chain(new EmptyStep(ctx, profilingEnabled));//nothing to return
      }
    } else {
      throw new CommandExecutionException("Invalid target: " + paramValue);
    }
  }

  /**
   * checks if this RID is from one of these clusters
   *
   * @param rid
   * @param filterClusters
   * @param database
   *
   * @return
   */
  private boolean isFromClusters(Rid rid, Set<String> filterClusters, Database database) {
    if (filterClusters == null) {
      throw new IllegalArgumentException();
    }
    String clusterName = database.getSchema().getBucketById(rid.getCluster().getValue().intValue()).getName();
    return filterClusters.contains(clusterName);
  }

  private void handleNoTarget(SelectExecutionPlan result, CommandContext ctx, boolean profilingEnabled) {
    result.chain(new EmptyDataGeneratorStep(1, ctx, profilingEnabled));
  }

  private void handleIndexAsTarget(SelectExecutionPlan result, QueryPlanningInfo info, IndexIdentifier indexIdentifier,
      Set<String> filterClusters, CommandContext ctx, boolean profilingEnabled) {
    String indexName = indexIdentifier.getIndexName();
    PIndex index = ctx.getDatabase().getSchema().getIndexByName(indexName);
    if (index == null) {
      throw new CommandExecutionException("Index not found: " + indexName);
    }

    int[] filterClusterIds = null;
    if (filterClusters != null) {
      filterClusterIds = filterClusters.stream().map(name -> ctx.getDatabase().getSchema().getBucketByName(name).getId())
          .mapToInt(i -> i).toArray();
    }

    switch (indexIdentifier.getType()) {
    case INDEX:
      BooleanExpression keyCondition = null;
      BooleanExpression ridCondition = null;
      if (info.flattenedWhereClause == null || info.flattenedWhereClause.size() == 0) {
//        if (!index.supportsOrderedIterations()) {
        throw new CommandExecutionException("Index " + indexName + " does not allow iteration without a condition");
//        }
      } else if (info.flattenedWhereClause.size() > 1) {
        throw new CommandExecutionException(
            "Index queries with this kind of condition are not supported yet: " + info.whereClause);
      } else {
        AndBlock andBlock = info.flattenedWhereClause.get(0);
        if (andBlock.getSubBlocks().size() == 1) {

          info.whereClause = null;//The WHERE clause won't be used anymore, the index does all the filtering
          info.flattenedWhereClause = null;
          keyCondition = getKeyCondition(andBlock);
          if (keyCondition == null) {
            throw new CommandExecutionException(
                "Index queries with this kind of condition are not supported yet: " + info.whereClause);
          }
        } else if (andBlock.getSubBlocks().size() == 2) {
          info.whereClause = null;//The WHERE clause won't be used anymore, the index does all the filtering
          info.flattenedWhereClause = null;
          keyCondition = getKeyCondition(andBlock);
          ridCondition = getRidCondition(andBlock);
          if (keyCondition == null || ridCondition == null) {
            throw new CommandExecutionException(
                "Index queries with this kind of condition are not supported yet: " + info.whereClause);
          }
        } else {
          throw new CommandExecutionException(
              "Index queries with this kind of condition are not supported yet: " + info.whereClause);
        }
      }
      result.chain(new FetchFromIndexStep(index, keyCondition, null, ctx, profilingEnabled));
      if (ridCondition != null) {
        WhereClause where = new WhereClause(-1);
        where.setBaseExpression(ridCondition);
        result.chain(new FilterStep(where, ctx, profilingEnabled));
      }
      break;
    case VALUES:
    case VALUESASC:
//      if (!index.supportsOrderedIterations()) {
      throw new CommandExecutionException("Index " + indexName + " does not allow iteration on values");
//      }
//      result.chain(new FetchFromIndexValuesStep(index, true, ctx, profilingEnabled));
//      result.chain(new GetValueFromIndexEntryStep(ctx, filterClusterIds, profilingEnabled));

//      break;
    case VALUESDESC:
//      if (!index.supportsOrderedIterations()) {
      throw new CommandExecutionException("Index " + indexName + " does not allow iteration on values");
//      }
//      result.chain(new FetchFromIndexValuesStep(index, false, ctx, profilingEnabled));
//      result.chain(new GetValueFromIndexEntryStep(ctx, filterClusterIds, profilingEnabled));
//      break;
    }
  }

  private BooleanExpression getKeyCondition(AndBlock andBlock) {
    for (BooleanExpression exp : andBlock.getSubBlocks()) {
      String str = exp.toString();
      if (str.length() < 5) {
        continue;
      }
      if (str.substring(0, 4).equalsIgnoreCase("key ")) {
        return exp;
      }
    }
    return null;
  }

  private BooleanExpression getRidCondition(AndBlock andBlock) {
    for (BooleanExpression exp : andBlock.getSubBlocks()) {
      String str = exp.toString();
      if (str.length() < 5) {
        continue;
      }
      if (str.substring(0, 4).equalsIgnoreCase("rid ")) {
        return exp;
      }
    }
    return null;
  }

  private void handleMetadataAsTarget(SelectExecutionPlan plan, MetadataIdentifier metadata, CommandContext ctx,
      boolean profilingEnabled) {
    Database db = ctx.getDatabase();
    String schemaRecordIdAsString = null;
    if (metadata.getName().toLowerCase().startsWith("metadata:")) {
      throw new UnsupportedOperationException();
    }
//    if (metadata.getName().equalsIgnoreCase(OCommandExecutorSQLAbstract.METADATA_SCHEMA)) {
//      schemaRecordIdAsString = db.getStorage().getConfiguration().getSchemaRecordId();
//      ORecordId schemaRid = new ORecordId(schemaRecordIdAsString);
//      plan.chain(new FetchFromRidsStep(Collections.singleton(schemaRid), ctx, profilingEnabled));
//    } else if (metadata.getName().equalsIgnoreCase(OCommandExecutorSQLAbstract.METADATA_INDEXMGR)) {
//      schemaRecordIdAsString = db.getStorage().getConfiguration().getIndexMgrRecordId();
//      ORecordId schemaRid = new ORecordId(schemaRecordIdAsString);
//      plan.chain(new FetchFromRidsStep(Collections.singleton(schemaRid), ctx, profilingEnabled));
//    } else if (metadata.getName().equalsIgnoreCase(OCommandExecutorSQLAbstract.METADATA_STORAGE)) {
//      plan.chain(new FetchFromStorageMetadataStep(ctx, profilingEnabled));
//    } else if (metadata.getName().equalsIgnoreCase(OCommandExecutorSQLAbstract.METADATA_DATABASE)) {
//      plan.chain(new FetchFromDatabaseMetadataStep(ctx, profilingEnabled));
//    } else {
//      throw new UnsupportedOperationException("Invalid metadata: " + metadata.getName());
//    }

  }

  private void handleRidsAsTarget(SelectExecutionPlan plan, List<Rid> rids, CommandContext ctx, boolean profilingEnabled) {
    List<RID> actualRids = new ArrayList<>();
    for (Rid rid : rids) {
      actualRids.add(rid.toRecordId((Result) null, ctx));
    }
    plan.chain(new FetchFromRidsStep(actualRids, ctx, profilingEnabled));
  }

  private static void handleExpand(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx,
      boolean profilingEnabled) {
    if (info.expand) {
      result.chain(new ExpandStep(ctx, profilingEnabled));
    }
  }

  private void handleGlobalLet(SelectExecutionPlan result, QueryPlanningInfo info, CommandContext ctx, boolean profilingEnabled) {
    if (info.globalLetClause != null) {
      List<LetItem> items = info.globalLetClause.getItems();
      for (LetItem item : items) {
        if (item.getExpression() != null) {
          result.chain(new GlobalLetExpressionStep(item.getVarName(), item.getExpression(), ctx, profilingEnabled));
        } else {
          result.chain(new GlobalLetQueryStep(item.getVarName(), item.getQuery(), ctx, profilingEnabled));
        }
        info.globalLetPresent = true;
      }
    }
  }

  private void handleLet(SelectExecutionPlan plan, QueryPlanningInfo info, CommandContext ctx, boolean profilingEnabled) {
    if (info.perRecordLetClause != null) {
      List<LetItem> items = info.perRecordLetClause.getItems();
      if (info.distributedPlanCreated) {
        for (LetItem item : items) {
          if (item.getExpression() != null) {
            plan.chain(new LetExpressionStep(item.getVarName(), item.getExpression(), ctx, profilingEnabled));
          } else {
            plan.chain(new LetQueryStep(item.getVarName(), item.getQuery(), ctx, profilingEnabled));
          }
        }
      } else {
        for (SelectExecutionPlan shardedPlan : info.distributedFetchExecutionPlans.values()) {
          for (LetItem item : items) {
            if (item.getExpression() != null) {
              shardedPlan
                  .chain(new LetExpressionStep(item.getVarName().copy(), item.getExpression().copy(), ctx, profilingEnabled));
            } else {
              shardedPlan.chain(new LetQueryStep(item.getVarName().copy(), item.getQuery().copy(), ctx, profilingEnabled));
            }
          }
        }
      }
    }
  }

  private void handleWhere(SelectExecutionPlan plan, QueryPlanningInfo info, CommandContext ctx, boolean profilingEnabled) {
    if (info.whereClause != null) {
      if (info.distributedPlanCreated) {
        plan.chain(new FilterStep(info.whereClause, ctx, profilingEnabled));
      } else {
        for (SelectExecutionPlan shardedPlan : info.distributedFetchExecutionPlans.values()) {
          shardedPlan.chain(new FilterStep(info.whereClause.copy(), ctx, profilingEnabled));
        }
      }
    }
  }

  public static void handleOrderBy(SelectExecutionPlan plan, QueryPlanningInfo info, CommandContext ctx,
      boolean profilingEnabled) {
    int skipSize = info.skip == null ? 0 : info.skip.getValue(ctx);
    if (skipSize < 0) {
      throw new CommandExecutionException("Cannot execute a query with a negative SKIP");
    }
    int limitSize = info.limit == null ? -1 : info.limit.getValue(ctx);
    java.lang.Integer maxResults = null;
    if (limitSize >= 0) {
      maxResults = skipSize + limitSize;
    }
    if (info.expand || info.unwind != null) {
      maxResults = null;
    }
    if (!info.orderApplied && info.orderBy != null && info.orderBy.getItems() != null && info.orderBy.getItems().size() > 0) {
      plan.chain(new OrderByStep(info.orderBy, maxResults, ctx, profilingEnabled));
      if (info.projectionAfterOrderBy != null) {
        plan.chain(new ProjectionCalculationStep(info.projectionAfterOrderBy, ctx, profilingEnabled));
      }
    }
  }

  /**
   * @param plan             the execution plan where to add the fetch step
   * @param filterClusters   clusters of interest (all the others have to be excluded from the result)
   * @param info
   * @param ctx
   * @param profilingEnabled
   */
  private void handleClassAsTarget(SelectExecutionPlan plan, Set<String> filterClusters, QueryPlanningInfo info,
      CommandContext ctx, boolean profilingEnabled) {
    handleClassAsTarget(plan, filterClusters, info.target, info, ctx, profilingEnabled);
  }

  private void handleClassAsTarget(SelectExecutionPlan plan, Set<String> filterClusters, FromClause from, QueryPlanningInfo info,
      CommandContext ctx, boolean profilingEnabled) {
    Identifier identifier = from.getItem().getIdentifier();
    if (handleClassAsTargetWithIndexedFunction(plan, filterClusters, identifier, info, ctx, profilingEnabled)) {
      plan.chain(new FilterByClassStep(identifier, ctx, profilingEnabled));
      return;
    }

    if (handleClassAsTargetWithIndex(plan, identifier, filterClusters, info, ctx, profilingEnabled)) {
      plan.chain(new FilterByClassStep(identifier, ctx, profilingEnabled));
      return;
    }

    if (info.orderBy != null && handleClassWithIndexForSortOnly(plan, identifier, filterClusters, info, ctx, profilingEnabled)) {
      plan.chain(new FilterByClassStep(identifier, ctx, profilingEnabled));
      return;
    }

    Boolean orderByRidAsc = null;//null: no order. true: asc, false:desc
    if (isOrderByRidAsc(info)) {
      orderByRidAsc = true;
    } else if (isOrderByRidDesc(info)) {
      orderByRidAsc = false;
    }
    FetchFromClassExecutionStep fetcher = new FetchFromClassExecutionStep(identifier.getStringValue(), filterClusters, info, ctx,
        orderByRidAsc, profilingEnabled);
    if (orderByRidAsc != null && info.serverToClusters.size() == 1) {
      info.orderApplied = true;
    }
    plan.chain(fetcher);
  }

  private boolean handleClassAsTargetWithIndexedFunction(SelectExecutionPlan plan, Set<String> filterClusters,
      Identifier queryTarget, QueryPlanningInfo info, CommandContext ctx, boolean profilingEnabled) {
    if (queryTarget == null) {
      return false;
    }
    PDocumentType clazz = ctx.getDatabase().getSchema().getType(queryTarget.getStringValue());
    if (clazz == null) {
      throw new CommandExecutionException("Class not found: " + queryTarget);
    }
    if (info.flattenedWhereClause == null || info.flattenedWhereClause.size() == 0) {
      return false;
    }

    List<InternalExecutionPlan> resultSubPlans = new ArrayList<>();

    boolean indexedFunctionsFound = false;

    for (AndBlock block : info.flattenedWhereClause) {
//      List<OBinaryCondition> indexedFunctionConditions = block.getIndexedFunctionConditions(clazz, ctx.getDatabase());
      List<BinaryCondition> indexedFunctionConditions = new ArrayList<>();

      indexedFunctionConditions = filterIndexedFunctionsWithoutIndex(indexedFunctionConditions, info.target, ctx);

      if (indexedFunctionConditions == null || indexedFunctionConditions.size() == 0) {
//        IndexSearchDescriptor bestIndex = findBestIndexFor(ctx, clazz.getIndexes(), block, clazz);
//        if (bestIndex != null) {
        //TODO implement search on indexes
//
//          FetchFromIndexStep step = new FetchFromIndexStep(bestIndex.idx, bestIndex.keyCondition,
//              bestIndex.additionalRangeCondition, true, ctx, profilingEnabled);
//
//          OSelectExecutionPlan subPlan = new OSelectExecutionPlan(ctx);
//          subPlan.chain(step);
//          int[] filterClusterIds = null;
//          if (filterClusters != null) {
//            filterClusterIds = filterClusters.stream().map(name -> ctx.getDatabase().getClusterIdByName(name)).mapToInt(i -> i)
//                .toArray();
//          }
//          subPlan.chain(new GetValueFromIndexEntryStep(ctx, filterClusterIds, profilingEnabled));
//          if (requiresMultipleIndexLookups(bestIndex.keyCondition)) {
//            subPlan.chain(new DistinctExecutionStep(ctx, profilingEnabled));
//          }
//          if (!block.getSubBlocks().isEmpty()) {
//            subPlan.chain(new FilterStep(createWhereFrom(block), ctx, profilingEnabled));
//          }
//          resultSubPlans.add(subPlan);
//        } else {
          FetchFromClassExecutionStep step = new FetchFromClassExecutionStep(clazz.getName(), filterClusters, ctx, true,
              profilingEnabled);
          SelectExecutionPlan subPlan = new SelectExecutionPlan(ctx);
          subPlan.chain(step);
          if (!block.getSubBlocks().isEmpty()) {
            subPlan.chain(new FilterStep(createWhereFrom(block), ctx, profilingEnabled));
          }
          resultSubPlans.add(subPlan);
//        }
      } else {
        BinaryCondition blockCandidateFunction = null;
        for (BinaryCondition cond : indexedFunctionConditions) {
          if (!cond.allowsIndexedFunctionExecutionOnTarget(info.target, ctx)) {
            if (!cond.canExecuteIndexedFunctionWithoutIndex(info.target, ctx)) {
              throw new CommandExecutionException("Cannot execute " + block + " on " + queryTarget);
            }
          }
          if (blockCandidateFunction == null) {
            blockCandidateFunction = cond;
          } else {
            boolean thisAllowsNoIndex = cond.canExecuteIndexedFunctionWithoutIndex(info.target, ctx);
            boolean prevAllowsNoIndex = blockCandidateFunction.canExecuteIndexedFunctionWithoutIndex(info.target, ctx);
            if (!thisAllowsNoIndex && !prevAllowsNoIndex) {
              //none of the functions allow execution without index, so cannot choose one
              throw new CommandExecutionException(
                  "Cannot choose indexed function between " + cond + " and " + blockCandidateFunction
                      + ". Both require indexed execution");
            } else if (thisAllowsNoIndex && prevAllowsNoIndex) {
              //both can be calculated without index, choose the best one for index execution
              long thisEstimate = cond.estimateIndexed(info.target, ctx);
              long lastEstimate = blockCandidateFunction.estimateIndexed(info.target, ctx);
              if (thisEstimate > -1 && thisEstimate < lastEstimate) {
                blockCandidateFunction = cond;
              }
            } else if (prevAllowsNoIndex) {
              //choose current condition, because the other one can be calculated without index
              blockCandidateFunction = cond;
            }
          }
        }

        FetchFromIndexedFunctionStep step = new FetchFromIndexedFunctionStep(blockCandidateFunction, info.target, ctx,
            profilingEnabled);
        if (!blockCandidateFunction.executeIndexedFunctionAfterIndexSearch(info.target, ctx)) {
          block = block.copy();
          block.getSubBlocks().remove(blockCandidateFunction);
        }
        if (info.flattenedWhereClause.size() == 1) {
          plan.chain(step);
          plan.chain(new FilterByClustersStep(filterClusters, ctx, profilingEnabled));
          if (!block.getSubBlocks().isEmpty()) {
            plan.chain(new FilterStep(createWhereFrom(block), ctx, profilingEnabled));
          }
        } else {
          SelectExecutionPlan subPlan = new SelectExecutionPlan(ctx);
          subPlan.chain(step);
          if (!block.getSubBlocks().isEmpty()) {
            subPlan.chain(new FilterStep(createWhereFrom(block), ctx, profilingEnabled));
          }
          resultSubPlans.add(subPlan);
        }
        indexedFunctionsFound = true;
      }
    }

    if (indexedFunctionsFound) {
      if (resultSubPlans.size() > 1) { //if resultSubPlans.size() == 1 the step was already chained (see above)
        plan.chain(new ParallelExecStep(resultSubPlans, ctx, profilingEnabled));
        plan.chain(new FilterByClustersStep(filterClusters, ctx, profilingEnabled));
        plan.chain(new DistinctExecutionStep(ctx, profilingEnabled));
      }
      //WHERE condition already applied
      info.whereClause = null;
      info.flattenedWhereClause = null;
      return true;
    } else {
      return false;
    }
  }

  private List<BinaryCondition> filterIndexedFunctionsWithoutIndex(List<BinaryCondition> indexedFunctionConditions,
      FromClause fromClause, CommandContext ctx) {
    if (indexedFunctionConditions == null) {
      return null;
    }
    List<BinaryCondition> result = new ArrayList<>();
    for (BinaryCondition cond : indexedFunctionConditions) {
      if (cond.allowsIndexedFunctionExecutionOnTarget(fromClause, ctx)) {
        result.add(cond);
      } else if (!cond.canExecuteIndexedFunctionWithoutIndex(fromClause, ctx)) {
        throw new CommandExecutionException("Cannot evaluate " + cond + ": no index defined");
      }
    }
    return result;
  }

  /**
   * tries to use an index for sorting only. Also adds the fetch step to the execution plan
   *
   * @param plan current execution plan
   * @param info the query planning information
   * @param ctx  the current context
   *
   * @return true if it succeeded to use an index to sort, false otherwise.
   */

  private boolean handleClassWithIndexForSortOnly(SelectExecutionPlan plan, Identifier queryTarget, Set<String> filterClusters,
      QueryPlanningInfo info, CommandContext ctx, boolean profilingEnabled) {

    PDocumentType clazz = ctx.getDatabase().getSchema().getType(queryTarget.getStringValue());
    if (clazz == null) {
      throw new CommandExecutionException("Class not found: " + queryTarget.getStringValue());
    }

    //TODO search on indexes
//    for (PIndex idx : clazz.getIndexes().stream().filter(i -> i.supportsOrderedIterations()).filter(i -> i.getDefinition() != null)
//        .collect(Collectors.toList())) {
//      List<String> indexFields = idx.getDefinition().getFields();
//      if (indexFields.size() < info.orderBy.getItems().size()) {
//        continue;
//      }
//      boolean indexFound = true;
//      String orderType = null;
//      for (int i = 0; i < info.orderBy.getItems().size(); i++) {
//        OOrderByItem orderItem = info.orderBy.getItems().get(i);
//        String indexField = indexFields.get(i);
//        if (i == 0) {
//          orderType = orderItem.getType();
//        } else {
//          if (orderType == null || !orderType.equals(orderItem.getType())) {
//            indexFound = false;
//            break;//ASC/DESC interleaved, cannot be used with index.
//          }
//        }
//        if (!indexField.equals(orderItem.getAlias())) {
//          indexFound = false;
//          break;
//        }
//      }
//      if (indexFound && orderType != null) {
//        plan.chain(new FetchFromIndexValuesStep(idx, orderType.equals(OOrderByItem.ASC), ctx, profilingEnabled));
//        int[] filterClusterIds = null;
//        if (filterClusters != null) {
//          filterClusterIds = filterClusters.stream().map(name -> ctx.getDatabase().getClusterIdByName(name)).mapToInt(i -> i)
//              .toArray();
//        }
//        plan.chain(new GetValueFromIndexEntryStep(ctx, filterClusterIds, profilingEnabled));
//        if (info.serverToClusters.size() == 1) {
//          info.orderApplied = true;
//        }
//        return true;
//      }
//    }
    return false;
  }

  private boolean handleClassAsTargetWithIndex(SelectExecutionPlan plan, Identifier targetClass, Set<String> filterClusters,
      QueryPlanningInfo info, CommandContext ctx, boolean profilingEnabled) {

    List<ExecutionStepInternal> result = handleClassAsTargetWithIndex(targetClass.getStringValue(), filterClusters, info, ctx,
        profilingEnabled);
    if (result != null) {
      result.stream().forEach(x -> plan.chain(x));
      info.whereClause = null;
      info.flattenedWhereClause = null;
      return true;
    }
    //TODO
//    PDocumentType clazz = ctx.getDatabase().getSchema().getType(targetClass.getStringValue());
//    if (clazz == null) {
//      throw new PCommandExecutionException("Cannot find class " + targetClass);
//    }
//    if (clazz.count(false) != 0 || clazz.getSubclasses().size() == 0 || isDiamondHierarchy(clazz)) {
//      return false;
//    }
//    //try subclasses
//
//    Collection<OClass> subclasses = clazz.getSubclasses();
//
//    List<OInternalExecutionPlan> subclassPlans = new ArrayList<>();
//    for (OClass subClass : subclasses) {
//      List<OExecutionStepInternal> subSteps = handleClassAsTargetWithIndexRecursive(subClass.getName(), filterClusters, info, ctx,
//          profilingEnabled);
//      if (subSteps == null || subSteps.size() == 0) {
//        return false;
//      }
//      OSelectExecutionPlan subPlan = new OSelectExecutionPlan(ctx);
//      subSteps.stream().forEach(x -> subPlan.chain(x));
//      subclassPlans.add(subPlan);
//    }
//    if (subclassPlans.size() > 0) {
//      plan.chain(new ParallelExecStep(subclassPlans, ctx, profilingEnabled));
//      return true;
//    }
    return false;
  }

  /**
   * checks if a class is the top of a diamond hierarchy
   *
   * @param clazz
   *
   * @return
   */
  private boolean isDiamondHierarchy(PDocumentType clazz) {
    //TODO no class hierarchies..?
//    Set<OClass> traversed = new HashSet<>();
//    List<OClass> stack = new ArrayList<>();
//    stack.add(clazz);
//    while (!stack.isEmpty()) {
//      OClass current = stack.remove(0);
//      traversed.add(current);
//      for (OClass sub : current.getSubclasses()) {
//        if (traversed.contains(sub)) {
//          return true;
//        }
//        stack.add(sub);
//        traversed.add(sub);
//      }
//    }
    return false;
  }

  private List<ExecutionStepInternal> handleClassAsTargetWithIndexRecursive(String targetClass, Set<String> filterClusters,
      QueryPlanningInfo info, CommandContext ctx, boolean profilingEnabled) {
    List<ExecutionStepInternal> result = handleClassAsTargetWithIndex(targetClass, filterClusters, info, ctx, profilingEnabled);
    if (result == null) {
      result = new ArrayList<>();
      PDocumentType clazz = ctx.getDatabase().getSchema().getType(targetClass);
      if (clazz == null) {
        throw new CommandExecutionException("Cannot find class " + targetClass);
      }
//      if (clazz.count(false) != 0 || clazz.getSubclasses().size() == 0 || isDiamondHierarchy(clazz)) {
        return null;
//      }

//      Collection<OClass> subclasses = clazz.getSubclasses();
//
//      List<OInternalExecutionPlan> subclassPlans = new ArrayList<>();
//      for (OClass subClass : subclasses) {
//        List<OExecutionStepInternal> subSteps = handleClassAsTargetWithIndexRecursive(subClass.getName(), filterClusters, info, ctx,
//            profilingEnabled);
//        if (subSteps == null || subSteps.size() == 0) {
//          return null;
//        }
//        OSelectExecutionPlan subPlan = new OSelectExecutionPlan(ctx);
//        subSteps.stream().forEach(x -> subPlan.chain(x));
//        subclassPlans.add(subPlan);
//      }
//      if (subclassPlans.size() > 0) {
//        result.add(new ParallelExecStep(subclassPlans, ctx, profilingEnabled));
//      }
    }
    return result.size() == 0 ? null : result;
  }

  private List<ExecutionStepInternal> handleClassAsTargetWithIndex(String targetClass, Set<String> filterClusters,
      QueryPlanningInfo info, CommandContext ctx, boolean profilingEnabled) {
    if (info.flattenedWhereClause == null || info.flattenedWhereClause.size() == 0) {
      return null;
    }

    PDocumentType clazz = ctx.getDatabase().getSchema().getType(targetClass);
    if (clazz == null) {
      throw new CommandExecutionException("Cannot find class " + targetClass);
    }

    //TODO!!!
//    Set<PIndex> indexes = clazz.getIndexes();
    Set<PIndex> indexes = new HashSet<>();

    List<IndexSearchDescriptor> indexSearchDescriptors = info.flattenedWhereClause.stream()
        .map(x -> findBestIndexFor(ctx, indexes, x, clazz)).filter(Objects::nonNull).collect(Collectors.toList());
    if (indexSearchDescriptors.size() != info.flattenedWhereClause.size()) {
      return null; //some blocks could not be managed with an index
    }

    List<ExecutionStepInternal> result = new ArrayList<>();
    //TODO!!!
//    List<IndexSearchDescriptor> optimumIndexSearchDescriptors = commonFactor(indexSearchDescriptors);
//
//    if (indexSearchDescriptors.size() == 1) {
//      IndexSearchDescriptor desc = indexSearchDescriptors.get(0);
//      result = new ArrayList<>();
//      Boolean orderAsc = getOrderDirection(info);
//      result.add(
//          new FetchFromIndexStep(desc.idx, desc.keyCondition, desc.additionalRangeCondition, !Boolean.FALSE.equals(orderAsc), ctx,
//              profilingEnabled));
//      int[] filterClusterIds = null;
//      if (filterClusters != null) {
//        filterClusterIds = filterClusters.stream().map(name -> ctx.getDatabase().getClusterIdByName(name)).mapToInt(i -> i)
//            .toArray();
//      }
//      result.add(new GetValueFromIndexEntryStep(ctx, filterClusterIds, profilingEnabled));
//      if (requiresMultipleIndexLookups(desc.keyCondition)) {
//        result.add(new DistinctExecutionStep(ctx, profilingEnabled));
//      }
//      if (orderAsc != null && info.orderBy != null && fullySorted(info.orderBy, desc.keyCondition, desc.idx)
//          && info.serverToClusters.size() == 1) {
//        info.orderApplied = true;
//      }
//      if (desc.remainingCondition != null && !desc.remainingCondition.isEmpty()) {
//        result.add(new FilterStep(createWhereFrom(desc.remainingCondition), ctx, profilingEnabled));
//      }
//    } else {
//      result = new ArrayList<>();
//      result.add(createParallelIndexFetch(optimumIndexSearchDescriptors, filterClusters, ctx, profilingEnabled));
//    }
    return result;
  }

  private boolean fullySorted(OrderBy orderBy, AndBlock conditions, PIndex idx) {
//    if (!idx.supportsOrderedIterations())
      return false;
//
//    List<String> orderItems = new ArrayList<>();
//    String order = null;
//
//    for (OOrderByItem item : orderBy.getItems()) {
//      if (order == null) {
//        order = item.getType();
//      } else if (!order.equals(item.getType())) {
//        return false;
//      }
//      orderItems.add(item.getAlias());
//    }
//
//    List<String> conditionItems = new ArrayList<>();
//
//    for (int i = 0; i < conditions.getSubBlocks().size(); i++) {
//      OBooleanExpression item = conditions.getSubBlocks().get(i);
//      if (item instanceof OBinaryCondition) {
//        if (((OBinaryCondition) item).getOperator() instanceof OEqualsCompareOperator) {
//          conditionItems.add(((OBinaryCondition) item).getLeft().toString());
//        } else if (i != conditions.getSubBlocks().size() - 1) {
//          return false;
//        }
//
//      } else if (i != conditions.getSubBlocks().size() - 1) {
//        return false;
//      }
//    }
//
//    List<String> orderedFields = new ArrayList<>();
//    boolean overlapping = false;
//    for (String s : conditionItems) {
//      if (orderItems.isEmpty()) {
//        return true;//nothing to sort, the conditions completely overlap the ORDER BY
//      }
//      if (s.equals(orderItems.get(0))) {
//        orderItems.remove(0);
//        overlapping = true; //start overlapping
//      } else if (overlapping) {
//        return false; //overlapping, but next order item does not match...
//      }
//      orderedFields.add(s);
//    }
//    orderedFields.addAll(orderItems);
//
//    final OIndexDefinition definition = idx.getDefinition();
//    final List<String> fields = definition.getFields();
//    if (fields.size() < orderedFields.size()) {
//      return false;
//    }
//
//    for (int i = 0; i < orderedFields.size(); i++) {
//      final String orderFieldName = orderedFields.get(i);
//      final String indexFieldName = fields.get(i);
//      if (!orderFieldName.equals(indexFieldName)) {
//        return false;
//      }
//    }
//
//    return true;
  }

  /**
   * returns TRUE if all the order clauses are ASC, FALSE if all are DESC, null otherwise
   *
   * @return TRUE if all the order clauses are ASC, FALSE if all are DESC, null otherwise
   */
  private Boolean getOrderDirection(QueryPlanningInfo info) {
    if (info.orderBy == null) {
      return null;
    }
    String result = null;
    for (OrderByItem item : info.orderBy.getItems()) {
      if (result == null) {
        result = item.getType() == null ? OrderByItem.ASC : item.getType();
      } else {
        String newType = item.getType() == null ? OrderByItem.ASC : item.getType();
        if (!newType.equals(result)) {
          return null;
        }
      }
    }
    return result == null || result.equals(OrderByItem.ASC) ? true : false;
  }

  private ExecutionStepInternal createParallelIndexFetch(List<IndexSearchDescriptor> indexSearchDescriptors,
      Set<String> filterClusters, CommandContext ctx, boolean profilingEnabled) {
    List<InternalExecutionPlan> subPlans = new ArrayList<>();
    for (IndexSearchDescriptor desc : indexSearchDescriptors) {
      SelectExecutionPlan subPlan = new SelectExecutionPlan(ctx);
      subPlan.chain(new FetchFromIndexStep(desc.idx, desc.keyCondition, desc.additionalRangeCondition, ctx, profilingEnabled));
      int[] filterClusterIds = null;
      if (filterClusters != null) {
        filterClusterIds = filterClusters.stream().map(name -> ctx.getDatabase().getSchema().getBucketByName(name).getId()).mapToInt(i -> i)
            .toArray();
      }
      subPlan.chain(new GetValueFromIndexEntryStep(ctx, filterClusterIds, profilingEnabled));
      if (requiresMultipleIndexLookups(desc.keyCondition)) {
        subPlan.chain(new DistinctExecutionStep(ctx, profilingEnabled));
      }
      if (desc.remainingCondition != null && !desc.remainingCondition.isEmpty()) {
        subPlan.chain(new FilterStep(createWhereFrom(desc.remainingCondition), ctx, profilingEnabled));
      }
      subPlans.add(subPlan);
    }
    return new ParallelExecStep(subPlans, ctx, profilingEnabled);
  }

  /**
   * checks whether the condition has CONTAINSANY or similar expressions, that require multiple index evaluations
   *
   * @param keyCondition
   *
   * @return
   */
  private boolean requiresMultipleIndexLookups(AndBlock keyCondition) {
    for (BooleanExpression oBooleanExpression : keyCondition.getSubBlocks()) {
      if (!(oBooleanExpression instanceof BinaryCondition)) {
        return true;
      }
    }
    return false;
  }

  private WhereClause createWhereFrom(BooleanExpression remainingCondition) {
    WhereClause result = new WhereClause(-1);
    result.setBaseExpression(remainingCondition);
    return result;
  }

  /**
   * given a flat AND block and a set of indexes, returns the best index to be used to process it, with the complete description on
   * how to use it
   *
   * @param ctx
   * @param indexes
   * @param block
   *
   * @return
   */
  private IndexSearchDescriptor findBestIndexFor(CommandContext ctx, Set<PIndex> indexes, AndBlock block, PDocumentType clazz) {
    return indexes.stream()
        //.filter(index -> index.getInternal().canBeUsedInEqualityOperators())
        .map(index -> buildIndexSearchDescriptor(ctx, index, block, clazz)).filter(Objects::nonNull)
        .filter(x -> x.keyCondition != null).filter(x -> x.keyCondition.getSubBlocks().size() > 0)
        .min(Comparator.comparing(x -> x.cost(ctx))).orElse(null);
  }

  /**
   * given an index and a flat AND block, returns a descriptor on how to process it with an index (index, index key and additional
   * filters to apply after index fetch
   *
   * @param ctx
   * @param index
   * @param block
   * @param clazz
   *
   * @return
   */
  private IndexSearchDescriptor buildIndexSearchDescriptor(CommandContext ctx, PIndex index, AndBlock block, PDocumentType clazz) {
    //TODO!!!
//    List<String> indexFields = index.getDefinition().getFields();
//    OBinaryCondition keyCondition = new OBinaryCondition(-1);
//    OIdentifier key = new OIdentifier("key");
//    keyCondition.setLeft(new OExpression(key));
//    boolean allowsRange = allowsRangeQueries(index);
//    boolean found = false;
//
//    OAndBlock blockCopy = block.copy();
//    Iterator<OBooleanExpression> blockIterator;
//
//    OAndBlock indexKeyValue = new OAndBlock(-1);
//    IndexSearchDescriptor result = new IndexSearchDescriptor();
//    result.idx = index;
//    result.keyCondition = indexKeyValue;
//    for (String indexField : indexFields) {
//      blockIterator = blockCopy.getSubBlocks().iterator();
//      boolean breakHere = false;
//      boolean indexFieldFound = false;
//      while (blockIterator.hasNext()) {
//        OBooleanExpression singleExp = blockIterator.next();
//        if (singleExp instanceof OBinaryCondition) {
//          OExpression left = ((OBinaryCondition) singleExp).getLeft();
//          if (left.isBaseIdentifier()) {
//            String fieldName = left.getDefaultAlias().getStringValue();
//            if (indexField.equals(fieldName)) {
//              OBinaryCompareOperator operator = ((OBinaryCondition) singleExp).getOperator();
//              if (!((OBinaryCondition) singleExp).getRight().isEarlyCalculated()) {
//                continue; //this cannot be used because the value depends on single record
//              }
//              if (operator instanceof OEqualsCompareOperator) {
//                found = true;
//                indexFieldFound = true;
//                OBinaryCondition condition = new OBinaryCondition(-1);
//                condition.setLeft(left);
//                condition.setOperator(operator);
//                condition.setRight(((OBinaryCondition) singleExp).getRight().copy());
//                indexKeyValue.getSubBlocks().add(condition);
//                blockIterator.remove();
//                break;
//              } else if (allowsRange && operator.isRangeOperator()) {
//                found = true;
//                indexFieldFound = true;
//                breakHere = true;//this is last element, no other fields can be added to the key because this is a range condition
//                OBinaryCondition condition = new OBinaryCondition(-1);
//                condition.setLeft(left);
//                condition.setOperator(operator);
//                condition.setRight(((OBinaryCondition) singleExp).getRight().copy());
//                indexKeyValue.getSubBlocks().add(condition);
//                blockIterator.remove();
//                //look for the opposite condition, on the same field, for range queries (the other side of the range)
//                while (blockIterator.hasNext()) {
//                  OBooleanExpression next = blockIterator.next();
//                  if (createsRangeWith((OBinaryCondition) singleExp, next)) {
//                    result.additionalRangeCondition = (OBinaryCondition) next;
//                    blockIterator.remove();
//                    break;
//                  }
//                }
//                break;
//              }
//            }
//          }
//        } else if (singleExp instanceof OContainsAnyCondition) {
//          OExpression left = ((OContainsAnyCondition) singleExp).getLeft();
//          if (left.isBaseIdentifier()) {
//            String fieldName = left.getDefaultAlias().getStringValue();
//            if (indexField.equals(fieldName)) {
//              if (!((OContainsAnyCondition) singleExp).getRight().isEarlyCalculated()) {
//                continue; //this cannot be used because the value depends on single record
//              }
//              found = true;
//              indexFieldFound = true;
//              OContainsAnyCondition condition = new OContainsAnyCondition(-1);
//              condition.setLeft(left);
//              condition.setRight(((OContainsAnyCondition) singleExp).getRight().copy());
//              indexKeyValue.getSubBlocks().add(condition);
//              blockIterator.remove();
//              break;
//            }
//          }
//        } else if (singleExp instanceof OInCondition) {
//          OExpression left = ((OInCondition) singleExp).getLeft();
//          if (left.isBaseIdentifier()) {
//            String fieldName = left.getDefaultAlias().getStringValue();
//            if (indexField.equals(fieldName)) {
//              if (((OInCondition) singleExp).getRightMathExpression() != null) {
//
//                if (!((OInCondition) singleExp).getRightMathExpression().isEarlyCalculated()) {
//                  continue; //this cannot be used because the value depends on single record
//                }
//                found = true;
//                indexFieldFound = true;
//                OInCondition condition = new OInCondition(-1);
//                condition.setLeft(left);
//                condition.setRightMathExpression(((OInCondition) singleExp).getRightMathExpression().copy());
//                indexKeyValue.getSubBlocks().add(condition);
//                blockIterator.remove();
//                break;
//              } else if (((OInCondition) singleExp).getRightParam() != null) {
//                found = true;
//                indexFieldFound = true;
//                OInCondition condition = new OInCondition(-1);
//                condition.setLeft(left);
//                condition.setRightParam(((OInCondition) singleExp).getRightParam().copy());
//                indexKeyValue.getSubBlocks().add(condition);
//                blockIterator.remove();
//                break;
//              }
//            }
//          }
//        }
//      }
//      if (breakHere || !indexFieldFound) {
//        break;
//      }
//    }
//
//    if (result.keyCondition.getSubBlocks().size() < index.getDefinition().getFields().size() && !index
//        .supportsOrderedIterations()) {
//      //hash indexes do not support partial key match
//      return null;
//    }
//
//    if (found) {
//      result.remainingCondition = blockCopy;
//      return result;
//    }
    return null;
  }

  private boolean createsRangeWith(BinaryCondition left, BooleanExpression next) {
    if (!(next instanceof BinaryCondition)) {
      return false;
    }
    BinaryCondition right = (BinaryCondition) next;
    if (!left.getLeft().equals(right.getLeft())) {
      return false;
    }
    BinaryCompareOperator leftOperator = left.getOperator();
    BinaryCompareOperator rightOperator = right.getOperator();
    if (leftOperator instanceof GeOperator || leftOperator instanceof GtOperator) {
      return rightOperator instanceof LeOperator || rightOperator instanceof LtOperator;
    }
    if (leftOperator instanceof LeOperator || leftOperator instanceof LtOperator) {
      return rightOperator instanceof GeOperator || rightOperator instanceof GtOperator;
    }
    return false;
  }

  private boolean allowsRangeQueries(PIndex index) {
//    return index.supportsOrderedIterations();
    return false;
  }
  /**
   * aggregates multiple index conditions that refer to the same key search
   *
   * @param indexSearchDescriptors
   *
   * @return
   */
  private List<IndexSearchDescriptor> commonFactor(List<IndexSearchDescriptor> indexSearchDescriptors) {
    //index, key condition, additional filter (to aggregate in OR)
    Map<PIndex, Map<IndexCondPair, OrBlock>> aggregation = new HashMap<>();
    for (IndexSearchDescriptor item : indexSearchDescriptors) {
      Map<IndexCondPair, OrBlock> filtersForIndex = aggregation.get(item.idx);
      if (filtersForIndex == null) {
        filtersForIndex = new HashMap<>();
        aggregation.put(item.idx, filtersForIndex);
      }
      IndexCondPair extendedCond = new IndexCondPair(item.keyCondition, item.additionalRangeCondition);

      OrBlock existingAdditionalConditions = filtersForIndex.get(extendedCond);
      if (existingAdditionalConditions == null) {
        existingAdditionalConditions = new OrBlock(-1);
        filtersForIndex.put(extendedCond, existingAdditionalConditions);
      }
      existingAdditionalConditions.getSubBlocks().add(item.remainingCondition);
    }
    List<IndexSearchDescriptor> result = new ArrayList<>();
    for (Map.Entry<PIndex, Map<IndexCondPair, OrBlock>> item : aggregation.entrySet()) {
      for (Map.Entry<IndexCondPair, OrBlock> filters : item.getValue().entrySet()) {
        result.add(new IndexSearchDescriptor(item.getKey(), filters.getKey().mainCondition, filters.getKey().additionalRange,
            filters.getValue()));
      }
    }
    return result;
  }

  private void handleClustersAsTarget(SelectExecutionPlan plan, QueryPlanningInfo info, List<Cluster> clusters,
      CommandContext ctx, boolean profilingEnabled) {

    throw new UnsupportedOperationException();
//
//    PDatabase db = ctx.getDatabase();
//
//    PDocumentType candidateClass = null;
//    boolean tryByIndex = true;
//    Set<String> clusterNames = new HashSet<>();
//
//    for (OCluster cluster : clusters) {
//      String name = cluster.getClusterName();
//      Integer clusterId = cluster.getClusterNumber();
//      if (name == null) {
//        name = db.getSchema().getBucketById(clusterId).getName();
//      }
//      if (clusterId == null) {
//        clusterId = db.getSchema().getBucketByName(name).getId();
//      }
//      if (name != null) {
//        clusterNames.add(name);
//        PDocumentType clazz = db.getSchema().getClassByClusterId(clusterId);
//        if (clazz == null) {
//          tryByIndex = false;
//          break;
//        }
//        if (candidateClass == null) {
//          candidateClass = clazz;
//        } else if (!candidateClass.equals(clazz)) {
//          candidateClass = null;
//          tryByIndex = false;
//          break;
//        }
//      } else {
//        tryByIndex = false;
//        break;
//      }
//
//    }
//
//    if (tryByIndex) {
//      OIdentifier clazz = new OIdentifier(candidateClass.getName());
//      if (handleClassAsTargetWithIndexedFunction(plan, clusterNames, clazz, info, ctx, profilingEnabled)) {
//        return;
//      }
//
//      if (handleClassAsTargetWithIndex(plan, clazz, clusterNames, info, ctx, profilingEnabled)) {
//        return;
//      }
//
//      if (info.orderBy != null && handleClassWithIndexForSortOnly(plan, clazz, clusterNames, info, ctx, profilingEnabled)) {
//        return;
//      }
//    }
//
//    Boolean orderByRidAsc = null;//null: no order. true: asc, false:desc
//    if (isOrderByRidAsc(info)) {
//      orderByRidAsc = true;
//    } else if (isOrderByRidDesc(info)) {
//      orderByRidAsc = false;
//    }
//    if (orderByRidAsc != null && info.serverToClusters.size() == 1) {
//      info.orderApplied = true;
//    }
//    if (clusters.size() == 1) {
//      OCluster cluster = clusters.get(0);
//      Integer clusterId = cluster.getClusterNumber();
//      if (clusterId == null) {
//        clusterId = db.getClusterIdByName(cluster.getClusterName());
//      }
//      if (clusterId == null) {
//        throw new PCommandExecutionException("Cluster " + cluster + " does not exist");
//      }
//      FetchFromClusterExecutionStep step = new FetchFromClusterExecutionStep(clusterId, ctx, profilingEnabled);
//      if (Boolean.TRUE.equals(orderByRidAsc)) {
//        step.setOrder(FetchFromClusterExecutionStep.ORDER_ASC);
//      } else if (Boolean.FALSE.equals(orderByRidAsc)) {
//        step.setOrder(FetchFromClusterExecutionStep.ORDER_DESC);
//      }
//      plan.chain(step);
//    } else {
//      int[] clusterIds = new int[clusters.size()];
//      for (int i = 0; i < clusters.size(); i++) {
//        OCluster cluster = clusters.get(i);
//        Integer clusterId = cluster.getClusterNumber();
//        if (clusterId == null) {
//          clusterId = db.getClusterIdByName(cluster.getClusterName());
//        }
//        if (clusterId == null) {
//          throw new PCommandExecutionException("Cluster " + cluster + " does not exist");
//        }
//        clusterIds[i] = clusterId;
//      }
//      FetchFromClustersExecutionStep step = new FetchFromClustersExecutionStep(clusterIds, ctx, orderByRidAsc, profilingEnabled);
//      plan.chain(step);
//    }
  }

  private void handleSubqueryAsTarget(SelectExecutionPlan plan, Statement subQuery, CommandContext ctx,
      boolean profilingEnabled) {
    BasicCommandContext subCtx = new BasicCommandContext();
    subCtx.setDatabase(ctx.getDatabase());
    subCtx.setParent(ctx);
    InternalExecutionPlan subExecutionPlan = subQuery.createExecutionPlan(subCtx, profilingEnabled);
    plan.chain(new SubQueryStep(subExecutionPlan, ctx, subCtx, profilingEnabled));
  }

  private boolean isOrderByRidDesc(QueryPlanningInfo info) {
    if (!hasTargetWithSortedRids(info)) {
      return false;
    }

    if (info.orderBy == null) {
      return false;
    }
    if (info.orderBy.getItems().size() == 1) {
      OrderByItem item = info.orderBy.getItems().get(0);
      String recordAttr = item.getRecordAttr();
      if (recordAttr != null && recordAttr.equalsIgnoreCase("@rid") && OrderByItem.DESC.equals(item.getType())) {
        return true;
      }
    }
    return false;
  }

  private boolean isOrderByRidAsc(QueryPlanningInfo info) {
    if (!hasTargetWithSortedRids(info)) {
      return false;
    }

    if (info.orderBy == null) {
      return false;
    }
    if (info.orderBy.getItems().size() == 1) {
      OrderByItem item = info.orderBy.getItems().get(0);
      String recordAttr = item.getRecordAttr();
      if (recordAttr != null && recordAttr.equalsIgnoreCase("@rid") && (item.getType() == null || OrderByItem.ASC
          .equals(item.getType()))) {
        return true;
      }
    }
    return false;
  }

  private boolean hasTargetWithSortedRids(QueryPlanningInfo info) {
    if (info.target == null) {
      return false;
    }
    if (info.target.getItem() == null) {
      return false;
    }
    if (info.target.getItem().getIdentifier() != null) {
      return true;
    } else if (info.target.getItem().getCluster() != null) {
      return true;
    } else if (info.target.getItem().getClusterList() != null) {
      return true;
    }
    return false;
  }

}

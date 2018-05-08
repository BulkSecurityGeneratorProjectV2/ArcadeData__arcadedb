/* Generated By:JJTree: Do not edit this line. OStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.database.Database;
import com.arcadedb.exception.CommandExecutionException;
import com.arcadedb.exception.CommandSQLParsingException;
import com.arcadedb.sql.executor.*;

import java.util.Map;

public class Statement extends SimpleNode {

  //only for internal use!!! (caching)
  protected String originalStatement;

  public static final String CUSTOM_STRICT_SQL = "strictSql";

  public Statement(int id) {
    super(id);
  }

  public Statement(SqlParser p, int id) {
    super(p, id);
  }

  /**
   * Accept the visitor.
   **/
  public Object jjtAccept(SqlParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    throw new UnsupportedOperationException("missing implementation in " + getClass().getSimpleName());
  }

  public void validate() throws CommandSQLParsingException {

  }

  @Override
  public String toString(String prefix) {
    StringBuilder builder = new StringBuilder();
    toString(null, builder);
    return builder.toString();
  }

//  public Object execute(OSQLAsynchQuery<ODocument> request, OCommandContext context, OProgressListener progressListener) {
//    throw new UnsupportedOperationException("Unsupported command: " + getClass().getSimpleName());
//  }

  public ResultSet execute(Database db, Object[] args) {
    return execute(db, args, null);
  }

  public ResultSet execute(Database db, Object[] args, CommandContext parentContext) {
    throw new UnsupportedOperationException();
  }

  public ResultSet execute(Database db, Map args) {
    return execute(db, args, null);
  }

  public ResultSet execute(Database db, Map args, CommandContext parentContext) {
    throw new UnsupportedOperationException();
  }

  /**
   * creates an execution plan for current statement, with profiling disabled
   *
   * @param ctx the context that will be used to execute the statement
   *
   * @return an execution plan
   */
  public InternalExecutionPlan createExecutionPlan(CommandContext ctx) {
    return createExecutionPlan(ctx, false);
  }

  /**
   * creates an execution plan for current statement
   *
   * @param ctx     the context that will be used to execute the statement
   * @param profile true to enable profiling, false to disable it
   *
   * @return an execution plan
   */
  public InternalExecutionPlan createExecutionPlan(CommandContext ctx, boolean profile) {
    throw new UnsupportedOperationException();
  }

  public Statement copy() {
    throw new UnsupportedOperationException("IMPLEMENT copy() ON " + getClass().getSimpleName());
  }

  public boolean refersToParent() {
    throw new UnsupportedOperationException("Implement " + getClass().getSimpleName() + ".refersToParent()");
  }

  public boolean isIdempotent() {
    return false;
  }

  public static Statement deserializeFromOResult(Result doc) {
    try {
      Statement result = (Statement) Class.forName(doc.getProperty("__class")).getConstructor(Integer.class).newInstance(-1);
      result.deserialize(doc);
    } catch (Exception e) {
      throw new CommandExecutionException(e);
    }
    return null;
  }

  public Result serialize() {
    ResultInternal result = new ResultInternal();
    result.setProperty("__class", getClass().getName());
    return result;
  }

  public void deserialize(Result fromResult) {
    throw new UnsupportedOperationException();
  }

  public boolean executinPlanCanBeCached() {
    return false;
  }

  public String getOriginalStatement() {
    return originalStatement;
  }

}
/* JavaCC - OriginalChecksum=589c4dcc8287f430e46d8eb12b0412c5 (do not edit this line) */

/* Generated By:JJTree: Do not edit this line. ODropIndexStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.database.Database;
import com.arcadedb.sql.executor.CommandContext;
import com.arcadedb.sql.executor.InternalResultSet;
import com.arcadedb.sql.executor.ResultSet;

import java.util.Map;

public class DropIndexStatement extends ODDLStatement {

  protected boolean all = false;
  protected IndexName name;
  protected boolean ifExists = false;

  public DropIndexStatement(int id) {
    super(id);
  }

  public DropIndexStatement(SqlParser p, int id) {
    super(p, id);
  }

  @Override
  public ResultSet executeDDL(CommandContext ctx) {
    InternalResultSet rs = new InternalResultSet();
    Database db = ctx.getDatabase();
//    OIndexManager idxMgr = db.getMetadata().getIndexManager();
//    if (all) {
//      for (OIndex<?> idx : idxMgr.getIndexes()) {
//        db.getMetadata().getIndexManager().dropIndex(idx.getName());
//        OResultInternal result = new OResultInternal();
//        result.setProperty("operation", "drop index");
//        result.setProperty("clusterName", idx.getName());
//        rs.add(result);
//      }
//
//    } else {
//      if (!idxMgr.existsIndex(name.getValue()) && !ifExists) {
//        throw new PCommandExecutionException("Index not found: " + name.getValue());
//      }
//      idxMgr.dropIndex(name.getValue());
//      OResultInternal result = new OResultInternal();
//      result.setProperty("operation", "drop index");
//      result.setProperty("indexName", name.getValue());
//      rs.add(result);
//    }
//
//    return rs;
    throw new UnsupportedOperationException();
  }

  @Override
  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("DROP INDEX ");
    if (all) {
      builder.append("*");
    } else {
      name.toString(params, builder);
    }
    if (ifExists) {
      builder.append(" IF EXISTS");
    }
  }

  @Override
  public DropIndexStatement copy() {
    DropIndexStatement result = new DropIndexStatement(-1);
    result.all = all;
    result.name = name == null ? null : name.copy();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    DropIndexStatement that = (DropIndexStatement) o;

    if (all != that.all)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (all ? 1 : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }
}
/* JavaCC - OriginalChecksum=51c8221d049e4f114378e4be03797050 (do not edit this line) */

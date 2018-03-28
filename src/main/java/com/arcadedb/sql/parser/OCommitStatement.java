/* Generated By:JJTree: Do not edit this line. OCommitStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.sql.executor.OCommandContext;
import com.arcadedb.sql.executor.OInternalResultSet;
import com.arcadedb.sql.executor.OResultInternal;
import com.arcadedb.sql.executor.OResultSet;

import java.util.Map;

public class OCommitStatement extends OSimpleExecStatement {

  protected OInteger retry;

  public OCommitStatement(int id) {
    super(id);
  }

  public OCommitStatement(OrientSql p, int id) {
    super(p, id);
  }

  @Override public OResultSet executeSimple(OCommandContext ctx) {
    ctx.getDatabase().commit();
    OInternalResultSet result = new OInternalResultSet();
    OResultInternal item = new OResultInternal();
    item.setProperty("operation", "commit");
    result.add(item);
    return result;
  }

  @Override public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("COMMIT");
    if (retry != null) {
      builder.append(" RETRY ");
      retry.toString(params, builder);
    }
  }

  @Override public OCommitStatement copy() {
    OCommitStatement result = new OCommitStatement(-1);
    result.retry = retry == null ? null : retry.copy();
    return result;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OCommitStatement that = (OCommitStatement) o;

    if (retry != null ? !retry.equals(that.retry) : that.retry != null)
      return false;

    return true;
  }

  @Override public int hashCode() {
    return retry != null ? retry.hashCode() : 0;
  }
}
/* JavaCC - OriginalChecksum=eaa0bc8f765fdaa017789953861bc0aa (do not edit this line) */

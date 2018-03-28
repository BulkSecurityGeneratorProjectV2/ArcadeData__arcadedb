/* Generated By:JJTree: Do not edit this line. ORollbackStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.sql.executor.OCommandContext;
import com.arcadedb.sql.executor.OInternalResultSet;
import com.arcadedb.sql.executor.OResultInternal;
import com.arcadedb.sql.executor.OResultSet;

import java.util.Map;

public class ORollbackStatement extends OSimpleExecStatement {
  public ORollbackStatement(int id) {
    super(id);
  }

  public ORollbackStatement(OrientSql p, int id) {
    super(p, id);
  }

  @Override public OResultSet executeSimple(OCommandContext ctx) {
    ctx.getDatabase().rollback();
    OInternalResultSet result = new OInternalResultSet();
    OResultInternal item = new OResultInternal();
    item.setProperty("operation", "rollback");
    result.add(item);
    return result;
  }


  @Override public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("ROLLBACK");
  }

  @Override public ORollbackStatement copy() {
    ORollbackStatement result = new ORollbackStatement(-1);
    return result;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    return true;
  }

  @Override public int hashCode() {
    return 0;
  }
}
/* JavaCC - OriginalChecksum=7efe0306e0cec51e035d64cad02ebc30 (do not edit this line) */

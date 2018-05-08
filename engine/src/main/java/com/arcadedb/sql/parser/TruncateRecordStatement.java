/* Generated By:JJTree: Do not edit this line. OTruncateRecordStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.arcadedb.sql.parser;

import com.arcadedb.sql.executor.CommandContext;
import com.arcadedb.sql.executor.ResultSet;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TruncateRecordStatement extends SimpleExecStatement {
  protected Rid       record;
  protected List<Rid> records;

  public TruncateRecordStatement(int id) {
    super(id);
  }

  public TruncateRecordStatement(SqlParser p, int id) {
    super(p, id);
  }

  @Override
  public ResultSet executeSimple(CommandContext ctx) {
//    List<ORid> recs = new ArrayList<>();
//    if (record != null) {
//      recs.add(record);
//    } else {
//      recs.addAll(records);
//    }
//
//    OInternalResultSet rs = new OInternalResultSet();
//    final ODatabaseDocumentInternal database = (ODatabaseDocumentInternal) ctx.getDatabase();
//    for (ORid rec : recs) {
//      try {
//        final ORecordId rid = rec.toRecordId((OResult) null, ctx);
//        final OStorageOperationResult<Boolean> result = database.getStorage().deleteRecord(rid, -1, 0, null);
//        database.getLocalCache().deleteRecord(rid);
//
//        if (result.getResult()) {
//          OResultInternal recordRes = new OResultInternal();
//          recordRes.setProperty("operation", "truncate record");
//          recordRes.setProperty("record", rec.toString());
//          rs.add(recordRes);
//        }
//      } catch (Exception e) {
//        throw OException.wrapException(new PCommandExecutionException("Error on executing command"), e);
//      }
//    }
//
//    return rs;
    throw new UnsupportedOperationException();
  }

  @Override
  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append("TRUNCATE RECORD ");
    if (record != null) {
      record.toString(params, builder);
    } else {
      builder.append("[");
      boolean first = true;
      for (Rid r : records) {
        if (!first) {
          builder.append(",");
        }
        r.toString(params, builder);
        first = false;
      }
      builder.append("]");
    }
  }

  @Override
  public TruncateRecordStatement copy() {
    TruncateRecordStatement result = new TruncateRecordStatement(-1);
    result.record = record == null ? null : record.copy();
    result.records = records == null ? null : records.stream().map(x -> x.copy()).collect(Collectors.toList());
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    TruncateRecordStatement that = (TruncateRecordStatement) o;

    if (record != null ? !record.equals(that.record) : that.record != null)
      return false;
    if (records != null ? !records.equals(that.records) : that.records != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = record != null ? record.hashCode() : 0;
    result = 31 * result + (records != null ? records.hashCode() : 0);
    return result;
  }
}
/* JavaCC - OriginalChecksum=9da68e9fe4c4bf94a12d8a6f8864097a (do not edit this line) */

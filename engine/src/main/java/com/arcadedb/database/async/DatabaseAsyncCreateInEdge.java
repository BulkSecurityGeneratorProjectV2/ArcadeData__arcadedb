package com.arcadedb.database.async;

import com.arcadedb.database.RID;
import com.arcadedb.graph.VertexInternal;

public class DatabaseAsyncCreateInEdge extends DatabaseAsyncCommand {
  public final VertexInternal destinationVertex;
  public final RID            edgeRID;
  public final RID            sourceVertexRID;

  public DatabaseAsyncCreateInEdge(final VertexInternal destinationVertex, final RID edgeRID, final RID sourceVertexRID) {
    this.destinationVertex = destinationVertex;
    this.edgeRID = edgeRID;
    this.sourceVertexRID = sourceVertexRID;
  }
}

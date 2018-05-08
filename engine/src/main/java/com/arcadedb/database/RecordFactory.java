package com.arcadedb.database;

import com.arcadedb.exception.DatabaseMetadataException;
import com.arcadedb.graph.*;

public class RecordFactory {
  public Record newImmutableRecord(final Database database, final String typeName, final RID rid, final byte type) {
    switch (type) {
    case Document.RECORD_TYPE:
      return new ImmutableDocument(database, typeName, rid, null);
    case Vertex.RECORD_TYPE:
      return new ImmutableVertex(database, typeName, rid, null);
    case Edge.RECORD_TYPE:
      return new ImmutableEdge(database, typeName, rid, (Binary) null);
    case EdgeChunk.RECORD_TYPE:
      return new ModifiableEdgeChunk(database, rid, (Binary) null);
    }
    throw new DatabaseMetadataException("Cannot find record type '" + type + "'");
  }

  public Record newImmutableRecord(final Database database, final String typeName, final RID rid, final Binary content) {
    final byte type = content.getByte();

    switch (type) {
    case Document.RECORD_TYPE:
      return new ImmutableDocument(database, typeName, rid, content);
    case Vertex.RECORD_TYPE:
      return new ImmutableVertex(database, typeName, rid, content);
    case Edge.RECORD_TYPE:
      return new ImmutableEdge(database, typeName, rid, content);
    case EdgeChunk.RECORD_TYPE:
      return new ModifiableEdgeChunk(database, rid, content);
    }
    throw new DatabaseMetadataException("Cannot find record type '" + type + "'");
  }

  public Record newModifiableRecord(final Database database, final String typeName, final RID rid, final Binary content) {
    final byte type = content.getByte();

    switch (type) {
    case Document.RECORD_TYPE:
      return new ModifiableDocument(database, typeName, rid, content);
    case Vertex.RECORD_TYPE:
      return new ModifiableVertex(database, typeName, rid);
    case Edge.RECORD_TYPE:
      return new ModifiableEdge(database, typeName, rid);
    case EdgeChunk.RECORD_TYPE:
      return new ModifiableEdgeChunk(database, rid);
    }
    throw new DatabaseMetadataException("Cannot find record type '" + type + "'");
  }
}

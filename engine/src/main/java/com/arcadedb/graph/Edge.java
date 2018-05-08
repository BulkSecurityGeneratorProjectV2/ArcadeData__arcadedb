package com.arcadedb.graph;

import com.arcadedb.database.Document;
import com.arcadedb.database.RID;

public interface Edge extends Document {
  byte RECORD_TYPE = 2;

  RID getOut();

  Vertex getOutVertex();

  RID getIn();

  Vertex getInVertex();

  Vertex getVertex(Vertex.DIRECTION iDirection);
}

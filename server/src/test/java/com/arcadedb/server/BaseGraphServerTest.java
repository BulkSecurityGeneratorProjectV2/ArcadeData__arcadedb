/*
 * Copyright (c) 2018 - Arcade Analytics LTD (https://arcadeanalytics.com)
 */

package com.arcadedb.server;

import com.arcadedb.ContextConfiguration;
import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.RID;
import com.arcadedb.engine.PaginatedFile;
import com.arcadedb.graph.ModifiableEdge;
import com.arcadedb.graph.ModifiableVertex;
import com.arcadedb.utility.FileUtils;
import com.arcadedb.utility.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public abstract class BaseGraphServerTest {
  protected static final String VERTEX1_TYPE_NAME = "V1";
  protected static final String VERTEX2_TYPE_NAME = "V2";
  protected static final String EDGE1_TYPE_NAME   = "E1";
  protected static final String EDGE2_TYPE_NAME   = "E2";
  protected static final String DB_PATH           = "../databases/";

  protected static RID              root;
  private          ArcadeDBServer[] servers;

  static {
    Properties prop = new Properties();
    prop.setProperty("log4j.rootLogger", "WARN");
    PropertyConfigurator.configure(prop);
  }

  @BeforeEach
  public void populate() {
    LogManager.instance().info(this, "Starting test %s...", getClass().getName());

    FileUtils.deleteRecursively(new File(getDatabasePath()));

    new DatabaseFactory(getDatabasePath(), PaginatedFile.MODE.READ_WRITE).execute(new DatabaseFactory.POperation() {
      @Override
      public void execute(Database database) {
        Assertions.assertFalse(database.getSchema().existsType(VERTEX1_TYPE_NAME));
        database.getSchema().createVertexType(VERTEX1_TYPE_NAME, 3);

        Assertions.assertFalse(database.getSchema().existsType(VERTEX2_TYPE_NAME));
        database.getSchema().createVertexType(VERTEX2_TYPE_NAME, 3);

        database.getSchema().createEdgeType(EDGE1_TYPE_NAME);
        database.getSchema().createEdgeType(EDGE2_TYPE_NAME);

        database.getSchema().createDocumentType("Person");
      }
    });

    final Database db = new DatabaseFactory(getDatabasePath(), PaginatedFile.MODE.READ_WRITE).acquire();
    db.begin();
    try {
      final ModifiableVertex v1 = db.newVertex(VERTEX1_TYPE_NAME);
      v1.set("name", VERTEX1_TYPE_NAME);
      v1.save();

      final ModifiableVertex v2 = db.newVertex(VERTEX2_TYPE_NAME);
      v2.set("name", VERTEX2_TYPE_NAME);
      v2.save();

      // CREATION OF EDGE PASSING PARAMS AS VARARGS
      ModifiableEdge e1 = (ModifiableEdge) v1.newEdge(EDGE1_TYPE_NAME, v2, true, "name", "E1");
      Assertions.assertEquals(e1.getOut(), v1);
      Assertions.assertEquals(e1.getIn(), v2);

      final ModifiableVertex v3 = db.newVertex(VERTEX2_TYPE_NAME);
      v3.set("name", "V3");
      v3.save();

      Map<String, Object> params = new HashMap<>();
      params.put("name", "E2");

      // CREATION OF EDGE PASSING PARAMS AS MAP
      ModifiableEdge e2 = (ModifiableEdge) v2.newEdge(EDGE2_TYPE_NAME, v3, true, params);
      Assertions.assertEquals(e2.getOut(), v2);
      Assertions.assertEquals(e2.getIn(), v3);

      ModifiableEdge e3 = (ModifiableEdge) v1.newEdge(EDGE2_TYPE_NAME, v3, true);
      Assertions.assertEquals(e3.getOut(), v1);
      Assertions.assertEquals(e3.getIn(), v3);

      db.commit();

      root = v1.getIdentity();

    } finally {
      db.close();
    }

    final int totalServers = getServers();
    servers = new ArcadeDBServer[totalServers];
    for (int i = 0; i < totalServers; ++i) {
      final ContextConfiguration config = new ContextConfiguration();
      config.setValue(GlobalConfiguration.SERVER_DATABASE_DIRECTORY, "../databases" + (i == 0 ? "" : i));
      servers[i] = new ArcadeDBServer(config);

      final int serverId = i;

      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            servers[serverId].start();
          } catch (IOException e) {
            e.printStackTrace();
          }
          LogManager.instance().info(this, "Test Server-%d is down", serverId);
        }
      }).start();

      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      if (serverId > 0) {
        HttpURLConnection connection = null;
        try {
          connection = (HttpURLConnection) new URL("http://127.0.0.1:2480/server").openConnection();

          connection.setRequestMethod("POST");

          final String payload = "{\"add\":[\"Person\"],\"remove\":[\"Jay\"]}";

          connection.setRequestMethod("POST");
          connection.setDoOutput(true);

          connection.connect();

          PrintWriter pw = new PrintWriter(new OutputStreamWriter(connection.getOutputStream()));
          pw.write(payload);
          pw.close();

          final String response = readResponse(connection);

          Assertions.assertEquals(200, connection.getResponseCode());
          Assertions.assertEquals("OK", connection.getResponseMessage());

          LogManager.instance().info(this, "Response: ", response);

        } catch (IOException e) {
          e.printStackTrace();
        } finally {
          connection.disconnect();
        }
      }
    }
  }

  protected int getServers() {
    return 1;
  }

  @AfterEach
  public void drop() {
    LogManager.instance().info(this, "Cleaning test %s...", getClass().getName());
    for (int i = 0; i < servers.length; ++i) {
      if (servers[i] != null)
        servers[i].stop();

      final Database db = new DatabaseFactory("../databases" + (i == 0 ? "" : i) + "/" + getDatabaseName(),
          PaginatedFile.MODE.READ_WRITE).acquire();
      db.drop();
    }
  }

  protected String getDatabaseName() {
    return "graph";
  }

  protected String getDatabasePath() {
    return DB_PATH + "/" + getDatabaseName();
  }

  protected String readResponse(final HttpURLConnection connection) throws IOException {
    InputStream in = connection.getInputStream();
    Scanner scanner = new Scanner(in);

    final StringBuilder buffer = new StringBuilder();

    while (scanner.hasNext()) {
      buffer.append(scanner.next().replace('\n', ' '));
    }

    return buffer.toString();
  }
}
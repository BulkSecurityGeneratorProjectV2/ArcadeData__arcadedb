/*
 * Copyright (c) 2018 - Arcade Analytics LTD (https://arcadeanalytics.com)
 */

package performance;

import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.*;
import com.arcadedb.database.async.ErrorCallback;
import com.arcadedb.engine.PaginatedFile;
import com.arcadedb.engine.WALFile;
import com.arcadedb.index.Index;
import com.arcadedb.log.LogManager;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.schema.SchemaImpl;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

public class PerformanceVertexIndexTest {
  private static final int    TOT               = 10_000_000;
  private static final int    COMPACTION_RAM_MB = 1024; // 1GB
  private static final String TYPE_NAME         = "Device";

  public static void main(String[] args) throws IOException {
    new PerformanceVertexIndexTest().run();
  }

  private void run() throws IOException {
    GlobalConfiguration.INDEX_COMPACTION_RAM_MB.setValue(COMPACTION_RAM_MB);
    insertData();
    checkLookups(10);

    // THIS TIME LOOK UP FOR KEYS WHILE COMPACTION
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        try {
          compaction();
        } catch (Exception e) {
          Assertions.fail(e);
        }
      }
    }, 0);

    checkLookups(10);
  }

  private void compaction() throws IOException, InterruptedException {
    try (Database database = new DatabaseFactory(PerformanceTest.DATABASE_PATH).open()) {
      for (Index index : database.getSchema().getIndexes())
        Assertions.assertTrue(index.compact());
    }
  }

  private void insertData() {
    PerformanceTest.clean();

    final int parallel = 4;

    Database database = new DatabaseFactory(PerformanceTest.DATABASE_PATH).open();
    try {
      if (!database.getSchema().existsType(TYPE_NAME)) {
        database.begin();

        DocumentType v = database.getSchema().createDocumentType(TYPE_NAME, parallel);

        v.createProperty("id", String.class);
        v.createProperty("number", String.class);
        v.createProperty("relativeName", String.class);

        v.createProperty("lastModifiedUserId", String.class);
        v.createProperty("createdDate", String.class);
        v.createProperty("assocJointClosureId", String.class);
        v.createProperty("HolderSpec_Name", String.class);
        v.createProperty("Name", String.class);
        v.createProperty("holderGroupName", String.class);
        v.createProperty("slot2slottype", String.class);
        v.createProperty("inventoryStatus", String.class);
        v.createProperty("lastModifiedDate", String.class);
        v.createProperty("createdUserId", String.class);
        v.createProperty("orientation", String.class);
        v.createProperty("operationalStatus", String.class);
        v.createProperty("supplierName", String.class);

        database.getSchema().createIndexes(SchemaImpl.INDEX_TYPE.LSM_TREE, false, "Device", new String[] { "id" }, 2 * 1024 * 1024, null);
        database.getSchema().createIndexes(SchemaImpl.INDEX_TYPE.LSM_TREE, false, "Device", new String[] { "number" }, 2 * 1024 * 1024, null);
        database.getSchema().createIndexes(SchemaImpl.INDEX_TYPE.LSM_TREE, false, "Device", new String[] { "relativeName" }, 2 * 1024 * 1024, null);
//        database.getSchema().createClassIndexes(SchemaImpl.INDEX_TYPE.LSM_TREE, false, "Device", new String[] { "HolderSpec_Name" }, 2 * 1024 * 1024, null);
//        database.getSchema().createClassIndexes(SchemaImpl.INDEX_TYPE.LSM_TREE, false, "Device", new String[] { "Name" }, 2 * 1024 * 1024, null);

        database.commit();
      }
    } finally {
      database.close();
    }

    database = new DatabaseFactory(PerformanceTest.DATABASE_PATH).open();

    long begin = System.currentTimeMillis();

    try {

      database.setReadYourWrites(false);
      database.async().setCommitEvery(50000);
      database.async().setParallelLevel(parallel);
      database.async().setTransactionUseWAL(true);
      database.async().setTransactionSync(WALFile.FLUSH_TYPE.YES_NOMETADATA);

      database.async().onError(new ErrorCallback() {
        @Override
        public void call(Exception exception) {
          LogManager.instance().log(this, Level.INFO, "TEST: ERROR: " + exception);
          exception.printStackTrace();
          Assertions.fail(exception);
        }
      });

      final int totalToInsert = TOT;
      final long startTimer = System.currentTimeMillis();
      long lastLap = startTimer;
      long lastLapCounter = 0;

      long counter = 0;
      for (; counter < totalToInsert; ++counter) {
        final MutableDocument v = database.newDocument("Device");

        final String randomString = "" + counter;

        v.set("id", randomString); // INDEXED
        v.set("number", "" + counter); // INDEXED
        v.set("relativeName", "/shelf=" + counter + "/slot=1"); // INDEXED

        v.set("lastModifiedUserId", "Holder");
        v.set("createdDate", "2011-09-12 14:50:57.0");
        v.set("assocJointClosureId", "434746");
        v.set("HolderSpec_Name", "Slot" + counter);
        v.set("Name", "1" + counter);
        v.set("holderGroupName", "TBC");
        v.set("slot2slottype", "1900000012");
        v.set("inventoryStatus", "INI");
        v.set("lastModifiedDate", "2011-09-12 14:54:13.0");
        v.set("createdUserId", "Holder");
        v.set("orientation", "NA");
        v.set("operationalStatus", "NotAvailable");
        v.set("supplierName", "TBD");

        database.async().createRecord(v);

        if (counter % 1000 == 0) {
          if (System.currentTimeMillis() - lastLap > 1000) {
            LogManager.instance().log(this, Level.INFO, "TEST: - Progress %d/%d (%d records/sec)", null, counter, totalToInsert, counter - lastLapCounter);
            lastLap = System.currentTimeMillis();
            lastLapCounter = counter;
          }
        }
      }

      LogManager.instance().log(this, Level.INFO, "TEST: Inserted " + counter + " elements in " + (System.currentTimeMillis() - begin) + "ms");

    } finally {
      database.close();
      LogManager.instance().log(this, Level.INFO, "TEST: Insertion finished in " + (System.currentTimeMillis() - begin) + "ms");
    }
  }

  private void checkLookups(final int step) {
    Database database = new DatabaseFactory(PerformanceTest.DATABASE_PATH).open(PaginatedFile.MODE.READ_ONLY);
    long begin = System.currentTimeMillis();

    try {
      LogManager.instance().log(this, Level.INFO, "TEST: Lookup for keys...");

      begin = System.currentTimeMillis();

      int checked = 0;

      for (long id = 0; id < TOT; id += step) {
        final Cursor<RID> records = database.lookupByKey(TYPE_NAME, new String[] { "id" }, new Object[] { id });
        Assertions.assertNotNull(records);
        Assertions.assertEquals(1, records.size(), "Wrong result for lookup of key " + id);

        final Document record = (Document) records.next().getRecord();
        Assertions.assertEquals("" + id, record.get("id"));

        checked++;

        if (checked % 10000 == 0) {
          long delta = System.currentTimeMillis() - begin;
          if (delta < 1)
            delta = 1;
          LogManager.instance().log(this, Level.INFO, "Checked " + checked + " lookups in " + delta + "ms = " + (10000 / delta) + " lookups/msec");
          begin = System.currentTimeMillis();
        }
      }
    } finally {
      database.close();
      LogManager.instance().log(this, Level.INFO, "TEST: Lookup finished in " + (System.currentTimeMillis() - begin) + "ms");
    }
  }
}
/*
 * Copyright (c) - Arcade Data LTD (https://arcadedata.com)
 */

package com.arcadedb.engine;

import com.arcadedb.database.Binary;
import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.exception.DatabaseMetadataException;
import com.arcadedb.exception.SchemaException;
import com.arcadedb.schema.DocumentType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HEADER = [itemCount(int:4),pageSize(int:4)] CONTENT-PAGES = [propertyName(string)]
 * <p>
 */
public class Dictionary extends PaginatedComponent {
  public static final  String               DICT_EXT               = "dict";
  public static final  int                  DEF_PAGE_SIZE          = 65536 * 5;
  private static final int                  DICTIONARY_ITEM_COUNT  = 0;
  private static final int                  DICTIONARY_HEADER_SIZE = Binary.INT_SERIALIZED_SIZE;
  private              int                  itemCount;
  private              List<String>         dictionary             = new CopyOnWriteArrayList<>();
  private              Map<String, Integer> dictionaryMap          = new ConcurrentHashMap<>();

  public static class PaginatedComponentFactoryHandler implements PaginatedComponentFactory.PaginatedComponentFactoryHandler {
    @Override
    public PaginatedComponent createOnLoad(DatabaseInternal database, String name, String filePath, final int fileId, PaginatedFile.MODE mode, int pageSize)
        throws IOException {
      return new Dictionary(database, name, filePath, fileId, mode, pageSize);
    }
  }

  /**
   * Called at creation time.
   */
  public Dictionary(final DatabaseInternal database, final String name, String filePath, final PaginatedFile.MODE mode, final int pageSize) throws IOException {
    super(database, name, filePath, DICT_EXT, mode, pageSize);
    if (file.getSize() == 0) {
      // NEW FILE, CREATE HEADER PAGE
      final MutablePage header = database.getTransaction().addPage(new PageId(file.getFileId(), 0), pageSize);
      itemCount = 0;
      updateCounters(header);
    }
  }

  /**
   * Called at load time.
   */
  public Dictionary(final DatabaseInternal database, final String name, final String filePath, final int id, final PaginatedFile.MODE mode, final int pageSize)
      throws IOException {
    super(database, name, filePath, id, mode, pageSize);
    reload();
  }

  public int getIdByName(final String name, final boolean create) {
    if (name == null)
      throw new IllegalArgumentException("Dictionary item name was null");

    Integer pos = dictionaryMap.get(name);
    if (pos == null && create) {
      // SYNCHRONIZE THIS BLOCK TO AVOID RACE CONDITIONS WITH RETRIES
      synchronized (this) {
        pos = dictionaryMap.get(name);
        if (pos == null) {
          final int itemCountBeforeIncrement = itemCount;

          try {
            database.transaction((tx) -> {
              itemCount = itemCountBeforeIncrement; // RESET COUNTER IN CASE OF RETRY
              addItemToPage(name);
            }, false);

            if (dictionaryMap.putIfAbsent(name, itemCountBeforeIncrement) == null)
              dictionary.add(name);
            pos = dictionaryMap.get(name);

          } catch (Exception e) {
            itemCount = itemCountBeforeIncrement; // RESET COUNTER IN CASE OF ERROR
            throw e;
          }
        }
      }
    }

    if (pos == null)
      return -1;

    return pos;
  }

  public String getNameById(final int nameId) {
    if (nameId < 0 || nameId >= dictionary.size())
      throw new IllegalArgumentException("Dictionary item with id " + nameId + " is not valid (total=" + dictionary.size() + ")");

    final String itemName = dictionary.get(nameId);
    if (itemName == null)
      throw new IllegalArgumentException("Dictionary item with id " + nameId + " was not found");

    return itemName;
  }

  public Map<String, Integer> getDictionaryMap() {
    return Collections.unmodifiableMap(dictionaryMap);
  }

  /**
   * Updates a name. The update will impact the entire database with both properties and values (if used as ENUM). The update is valid only if the name has not been used as type name.
   *
   * @param oldName The old name to rename. Must be already present in the schema dictionary
   * @param newName The new name. Can be already present in the schema dictionary
   */
  public void updateName(final String oldName, final String newName) {
    if (!database.isTransactionActive())
      throw new SchemaException("Error on adding new item to the database schema dictionary because no transaction was active");

    if (oldName == null)
      throw new IllegalArgumentException("Dictionary old item name was null");

    if (newName == null)
      throw new IllegalArgumentException("Dictionary new item name was null");

    Integer oldIndex = dictionaryMap.remove(oldName);
    if (oldIndex == null)
      throw new IllegalArgumentException("Item '" + oldName + "' not found in the dictionary");

    for (DocumentType t : database.getSchema().getTypes())
      if (oldName.equals(t.getName()))
        throw new IllegalArgumentException("Cannot rename the item '" + oldName + "' in the dictionary because it has been used as a type name");

    dictionary.set(oldIndex, newName);
    try {
      final MutablePage header = database.getTransaction().getPageToModify(new PageId(file.getFileId(), 0), pageSize, false);

      header.clearContent();

      for (String d : dictionary) {
        final byte[] property = d.getBytes();

        if (header.getAvailableContentSize() < Binary.SHORT_SERIALIZED_SIZE + property.length)
          throw new DatabaseMetadataException("No space left in dictionary file (items=" + itemCount + ")");

        header.writeString(header.getContentSize(), d);

        itemCount++;
      }

      final Integer newIndex = dictionaryMap.get(newName);
      if (newIndex == null)
        dictionaryMap.putIfAbsent(newName, oldIndex); // IF ALREADY PRESENT, USE THE PREVIOUS KEY INDEX

      updateCounters(header);

    } catch (IOException e) {
      dictionary.set(oldIndex, oldName);
      throw new SchemaException("Error on updating name in dictionary");
    }
  }

  private void addItemToPage(final String propertyName) {
    if (!database.isTransactionActive())
      throw new SchemaException("Error on adding new item to the database schema dictionary because no transaction was active");

    final byte[] property = propertyName.getBytes();

    final MutablePage header;
    try {
      header = database.getTransaction().getPageToModify(new PageId(file.getFileId(), 0), pageSize, false);

      if (header.getAvailableContentSize() < Binary.SHORT_SERIALIZED_SIZE + property.length)
        throw new DatabaseMetadataException("No space left in dictionary file (items=" + itemCount + ")");

      header.writeString(header.getContentSize(), propertyName);

      itemCount++;

      updateCounters(header);
    } catch (IOException e) {
      throw new SchemaException("Error on adding new item to the database schema dictionary");
    }
  }

  private void updateCounters(final MutablePage header) {
    header.writeInt(DICTIONARY_ITEM_COUNT, itemCount);
  }

  public void reload() throws IOException {
    if (file.getSize() == 0) {
      // NEW FILE, CREATE HEADER PAGE
      final MutablePage header = database.getTransaction().addPage(new PageId(file.getFileId(), 0), pageSize);
      itemCount = 0;
      updateCounters(header);

    } else {
      final BasePage header = database.getTransaction().getPage(new PageId(file.getFileId(), 0), pageSize);

      header.setBufferPosition(0);
      final int newItemCount = header.readInt(DICTIONARY_ITEM_COUNT);

      final List<String> newDictionary = new CopyOnWriteArrayList<>();

      // LOAD THE DICTIONARY IN RAM
      header.setBufferPosition(DICTIONARY_HEADER_SIZE);
      for (int i = 0; i < newItemCount; ++i)
        newDictionary.add(header.readString());

      final Map<String, Integer> newDictionaryMap = new ConcurrentHashMap<>();
      for (int i = 0; i < newDictionary.size(); ++i)
        newDictionaryMap.putIfAbsent(newDictionary.get(i), i);

      this.itemCount = newItemCount;
      this.dictionary = newDictionary;
      this.dictionaryMap = newDictionaryMap;
    }
  }
}

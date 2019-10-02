/*
 * Copyright 2019 The Board of Trustees of The Leland Stanford Junior University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.susom.starr.dbtoavro.functions;

import com.github.susom.database.Config;
import com.github.susom.database.DatabaseException;
import com.github.susom.starr.dbtoavro.entity.Database;
import com.github.susom.starr.dbtoavro.entity.Table;
import com.github.susom.starr.dbtoavro.util.DatabaseProviderRx;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.io.File;
import java.util.List;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DatabaseFns {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseFns.class);

  protected final Config config;
  protected final DatabaseProviderRx.Builder dbb;

  public DatabaseFns(Config config, DatabaseProviderRx.Builder dbb) {
    this.config = config;
    this.dbb = dbb;
  }

  /**
   * Pointer to a database running in a docker container
   *
   * @param containerId running database
   * @return database object
   */
  public Single<Database> getDatabase(String containerId) {
    return dbb.transactRx(db -> {
      Database database = new Database(containerId);
      database.flavor = db.get().flavor();
      return database;
    }).toSingle();
  }

  /**
   * Attempts to isValid to the DB
   *
   * @return Single boolean indicating if database connection was successful
   */
  public Single<Boolean> isValid() {
    return dbb.transactRx(db -> {
      LOGGER.debug("Attempting to connect to database...");
      try {
        return db.get().underlyingConnection().isValid(30000);
      } catch (DatabaseException ex) {
        return false;
      }
    }).toSingle();
  }

  /**
   * Get schemas in catalog
   *
   * @param catalog catalog to query
   * @return observable of schemas
   */
  public abstract Observable<String> getSchemas(String catalog);

  /**
   * Get tables in a schema
   *
   * @param catalog catalog to query
   * @param schema schema to query
   * @return observable of catalogs
   */
  public abstract Observable<String> getTables(String catalog, String schema);

  /**
   * Introspects a database table, required for selecting the appropriate splitting and exporting method.
   *
   * @param catalog catalog to query
   * @param schema schema to query
   * @param table table to introspect
   * @param filters filter definitions
   * @return observable of table with row counts, byte sizes, and supported column information
   */
  public abstract Observable<Table> introspect(String catalog, String schema, String table, List<String> filters);

  public abstract Single<String> getRestoreSql(String catalog, List<String> backupFiles);

  /**
   * Executes contents of an SQL file
   *
   * @param file path to SQL file
   * @return completable status
   */
  public Completable transactFile(File file) {
    if (file == null) {
      return Completable.complete();
    }
    return dbb.transactRx(db -> {
      Scanner scanner = new Scanner(file, "UTF-8").useDelimiter(";");
      while (scanner.hasNext()) {
        String statement = scanner.next() + ";";
        LOGGER.debug("Pre-SQL: {}", statement);
        db.get().ddl(statement).execute();
      }
    });
  }

  /**
   * Executes an SQL string
   *
   * @return completable status
   */
  public Completable transact(String sql) {
    if (sql == null) {
      return Completable.complete();
    }
    return dbb.transactRx(db -> {
      db.get().ddl(sql).execute();
    });
  }

}
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

package com.github.susom.starr.db_to_avro.jobrunner.functions.impl;

import com.github.susom.starr.db_to_avro.jobrunner.docker.ConsoleOutput;
import com.github.susom.starr.db_to_avro.jobrunner.docker.DockerService;
import com.github.susom.starr.db_to_avro.jobrunner.functions.DockerFns;
import com.github.susom.database.Config;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Manages an SQL server container that is running in docker
 */
public class SqlServerDockerFns implements DockerFns {

  private DockerService dockerService;
  private List<String> mounts;
  private List<String> env;
  private String image;
  private String password;

  public SqlServerDockerFns(final DockerService dockerService, final Config config, final List<String> mounts) {
    this.dockerService = dockerService;
    this.mounts = mounts;
    this.image = config.getString("sqlserver.image", "mcr.microsoft.com/mssql/server:2017-latest");
    this.password = config.getStringOrThrow("sqlserver.password");
    this.env = Arrays
        .stream(config.getStringOrThrow("sqlserver.env").split("\\s*,\\s*"))
        .collect(Collectors.toList());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Single<String> create() {
    return Single.create(emitter -> {
      try {
        emitter.onSuccess(
            dockerService.createContainer(image, mounts, env.stream().map(object -> Objects.toString(object, null))
                .collect(Collectors.toList())));
      } catch (Exception ex) {
        emitter.onError(ex);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Completable start(String containerId) {
    return Completable.create(emitter -> {
      try {
        this.dockerService.startContainer(containerId);
        emitter.onComplete();
      } catch (Exception ex) {
        emitter.onError(ex);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Completable stop(String containerId) {
    return Completable.create(emitter -> {
      try {
        this.dockerService.stopContainer(containerId);
        emitter.onComplete();
      } catch (Exception ex) {
        emitter.onError(ex);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Completable destroy(String containerId) {
    return Completable.create(emitter -> {
      try {
        this.dockerService.removeContainer(containerId);
        emitter.onComplete();
      } catch (Exception ex) {
        emitter.onError(ex);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Observable<ConsoleOutput> execSqlShell(String containerId, String query) {
    return dockerService.exec(containerId,
        "/opt/mssql-tools/bin/sqlcmd",
        "-s", "localhost",
        "-U", "sa",
        "-P", password,
        "-Q", query);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Completable healthCheck(String containerId) {
    return execSqlShell(containerId, "SELECT 1;")
        .filter(p -> p.getData().contains("1 rows affected"))
        .count()
        .flatMapCompletable(count -> {
          if (count > 0) {
            return Completable.complete();
          } else {
            return Completable.error(new IllegalArgumentException("Health check failed"));
          }
        });

  }

}

// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.export;

import static google.registry.request.Action.Method.POST;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.model.Table;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.ViewDefinition;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import google.registry.bigquery.BigqueryFactory;
import google.registry.config.RegistryConfig.Config;
import google.registry.request.Action;
import google.registry.request.HttpException.InternalServerErrorException;
import google.registry.request.Parameter;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import google.registry.util.SqlTemplate;
import java.io.IOException;
import javax.inject.Inject;

/** Update a well-known view to point at a certain Datastore snapshot table in BigQuery. */
@Action(path = UpdateSnapshotViewAction.PATH, method = POST, auth = Auth.AUTH_INTERNAL_ONLY)
public class UpdateSnapshotViewAction implements Runnable {

  /** Headers for passing parameters into the servlet. */
  static final String UPDATE_SNAPSHOT_DATASET_ID_PARAM = "dataset";

  static final String UPDATE_SNAPSHOT_TABLE_ID_PARAM = "table";
  static final String UPDATE_SNAPSHOT_KIND_PARAM = "kind";

  static final String LEGACY_LATEST_SNAPSHOT_DATASET = "latest_snapshot";
  static final String STANDARD_LATEST_SNAPSHOT_DATASET = "latest_datastore_export";

  /** Servlet-specific details needed for enqueuing tasks against itself. */
  static final String QUEUE = "export-snapshot-update-view"; // See queue.xml.

  static final String PATH = "/_dr/task/updateSnapshotView"; // See web.xml.

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject
  @Parameter(UPDATE_SNAPSHOT_DATASET_ID_PARAM)
  String datasetId;

  @Inject
  @Parameter(UPDATE_SNAPSHOT_TABLE_ID_PARAM)
  String tableId;

  @Inject
  @Parameter(UPDATE_SNAPSHOT_KIND_PARAM)
  String kindName;

  @Inject
  @Config("projectId")
  String projectId;

  @Inject BigqueryFactory bigqueryFactory;

  @Inject
  UpdateSnapshotViewAction() {}

  /** Create a task for updating a snapshot view. */
  static TaskOptions createViewUpdateTask(String datasetId, String tableId, String kindName) {
    return TaskOptions.Builder.withUrl(PATH)
        .method(Method.POST)
        .param(UPDATE_SNAPSHOT_DATASET_ID_PARAM, datasetId)
        .param(UPDATE_SNAPSHOT_TABLE_ID_PARAM, tableId)
        .param(UPDATE_SNAPSHOT_KIND_PARAM, kindName);
  }

  @Override
  public void run() {
    try {
      // TODO(b/32377148): Remove the legacySql view when migration complete.
      SqlTemplate legacyTemplate =
          SqlTemplate.create(
              "#legacySQL\nSELECT * FROM [%PROJECT%:%SOURCE_DATASET%.%SOURCE_TABLE%]");
      updateSnapshotView(
          datasetId, tableId, kindName, LEGACY_LATEST_SNAPSHOT_DATASET, legacyTemplate, true);

      SqlTemplate standardTemplate =
          SqlTemplate.create(
              "#standardSQL\nSELECT * FROM `%PROJECT%.%SOURCE_DATASET%.%SOURCE_TABLE%`");
      updateSnapshotView(
          datasetId, tableId, kindName, STANDARD_LATEST_SNAPSHOT_DATASET, standardTemplate, false);

    } catch (Throwable e) {
      logger.severefmt(e, "Could not update snapshot view for table %s", tableId);
      throw new InternalServerErrorException("Error in update snapshot view action");
    }
  }

  private void updateSnapshotView(
      String sourceDatasetId,
      String sourceTableId,
      String kindName,
      String viewDataset,
      SqlTemplate viewQueryTemplate,
      boolean useLegacySql)
      throws IOException {

    Bigquery bigquery = bigqueryFactory.create(projectId, viewDataset);
    updateTable(
        bigquery,
        new Table()
            .setTableReference(
                new TableReference()
                    .setProjectId(projectId)
                    .setDatasetId(viewDataset)
                    .setTableId(kindName))
            .setView(
                new ViewDefinition()
                    .setUseLegacySql(useLegacySql)
                    .setQuery(
                        viewQueryTemplate
                            .put("PROJECT", projectId)
                            .put("SOURCE_DATASET", sourceDatasetId)
                            .put("SOURCE_TABLE", sourceTableId)
                            .build())));

    logger.infofmt(
        "Updated view %s to point at snapshot table %s.",
        String.format("[%s:%s.%s]", projectId, viewDataset, kindName),
        String.format("[%s:%s.%s]", projectId, sourceDatasetId, sourceTableId));
  }

  private static void updateTable(Bigquery bigquery, Table table) throws IOException {
    TableReference ref = table.getTableReference();
    try {
      bigquery
          .tables()
          .update(ref.getProjectId(), ref.getDatasetId(), ref.getTableId(), table)
          .execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getDetails().getCode() == 404) {
        bigquery.tables().insert(ref.getProjectId(), ref.getDatasetId(), table).execute();
      } else {
        logger.warningfmt("UpdateSnapshotViewAction failed, caught exception %s", e.getDetails());
      }
    }
  }
}

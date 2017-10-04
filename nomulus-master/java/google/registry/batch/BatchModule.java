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

package google.registry.batch;

import static google.registry.request.RequestParameters.extractOptionalBooleanParameter;
import static google.registry.request.RequestParameters.extractOptionalIntParameter;
import static google.registry.request.RequestParameters.extractOptionalParameter;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import google.registry.request.Parameter;
import javax.servlet.http.HttpServletRequest;

/**
 * Dagger module for injecting common settings for batch actions.
 */
@Module
public class BatchModule {

  @Provides
  @IntoMap
  @StringKey(EntityIntegrityAlertsSchema.TABLE_ID)
  static ImmutableList<TableFieldSchema> provideEntityIntegrityAlertsSchema() {
    return EntityIntegrityAlertsSchema.SCHEMA_FIELDS;
  }

  @Provides
  @Parameter("jobName")
  static Optional<String> provideJobName(HttpServletRequest req) {
    return extractOptionalParameter(req, "jobName");
  }

  @Provides
  @Parameter("jobId")
  static Optional<String> provideJobId(HttpServletRequest req) {
    return extractOptionalParameter(req, "jobId");
  }

  @Provides
  @Parameter("numJobsToDelete")
  static Optional<Integer> provideNumJobsToDelete(HttpServletRequest req) {
    return extractOptionalIntParameter(req, "numJobsToDelete");
  }

  @Provides
  @Parameter("daysOld")
  static Optional<Integer> provideDaysOld(HttpServletRequest req) {
    return extractOptionalIntParameter(req, "daysOld");
  }

  @Provides
  @Parameter("force")
  static Optional<Boolean> provideForce(HttpServletRequest req) {
    return extractOptionalBooleanParameter(req, "force");
  }
}

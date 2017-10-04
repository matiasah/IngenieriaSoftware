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

package google.registry.monitoring.whitebox;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.MonitoringScopes;
import com.google.api.services.monitoring.v3.model.MonitoredResource;
import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.modules.ModulesService;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import google.registry.monitoring.metrics.MetricWriter;
import google.registry.monitoring.metrics.stackdriver.StackdriverWriter;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import javax.inject.Named;
import org.joda.time.Duration;

/** Dagger module for Google Stackdriver service connection objects. */
@Module
public final class StackdriverModule {

  // We need a fake GCE zone to appease Stackdriver's resource model.
  // TODO(b/31021585): Revisit this if/when gae_instance exists.
  private static final String SPOOFED_GCE_ZONE = "us-central1-f";

  @Provides
  static Monitoring provideMonitoring(
      HttpTransport transport,
      JsonFactory jsonFactory,
      Function<Set<String>, ? extends HttpRequestInitializer> credential,
      @Config("projectId") String projectId) {
    return new Monitoring.Builder(transport, jsonFactory, credential.apply(MonitoringScopes.all()))
        .setApplicationName(projectId)
        .build();
  }

  @Provides
  static MetricWriter provideMetricWriter(
      Monitoring monitoringClient,
      @Config("projectId") String projectId,
      ModulesService modulesService,
      @Config("stackdriverMaxQps") int maxQps,
      @Config("stackdriverMaxPointsPerRequest") int maxPointsPerRequest) {
    // The MonitoredResource for GAE apps is not writable (and missing fields anyway) so we just
    // use the gce_instance resource type instead.
    return new StackdriverWriter(
        monitoringClient,
        projectId,
        new MonitoredResource()
            .setType("gce_instance")
            .setLabels(
                ImmutableMap.of(
                    // The "zone" field MUST be a valid GCE zone, so we fake one.
                    "zone",
                    SPOOFED_GCE_ZONE,
                    // Overload the GCE "instance_id" field with the GAE module name, version and
                    // instance_id.
                    "instance_id",
                    modulesService.getCurrentModule()
                        + ":"
                        + modulesService.getCurrentVersion()
                        + ":"
                        + modulesService.getCurrentInstanceId())),
        maxQps,
        maxPointsPerRequest);
  }

  @Provides
  @Named("metricsBackgroundThreadFactory")
  static ThreadFactory provideThreadFactory() {
    return ThreadManager.backgroundThreadFactory();
  }

  @Provides
  @Named("metricsWriteInterval")
  static long provideMetricsWriteInterval(
      @Config("metricsWriteInterval") Duration metricsWriteInterval) {
    return metricsWriteInterval.getStandardSeconds();
  }
}

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

package google.registry.flows;

import static org.mockito.Mockito.mock;

import com.google.appengine.api.modules.ModulesService;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import google.registry.config.RegistryConfig.ConfigModule;
import google.registry.config.RegistryConfig.ConfigModule.TmchCaMode;
import google.registry.dns.DnsQueue;
import google.registry.flows.custom.CustomLogicFactory;
import google.registry.flows.custom.TestCustomLogicFactory;
import google.registry.flows.domain.DomainFlowTmchUtils;
import google.registry.monitoring.whitebox.BigQueryMetricsEnqueuer;
import google.registry.monitoring.whitebox.EppMetric;
import google.registry.request.RequestScope;
import google.registry.request.lock.LockHandler;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeLockHandler;
import google.registry.testing.FakeSleeper;
import google.registry.tmch.TmchCertificateAuthority;
import google.registry.tmch.TmchXmlSignature;
import google.registry.util.Clock;
import google.registry.util.Sleeper;
import javax.inject.Singleton;

/** Dagger component for running EPP tests. */
@Singleton
@Component(
    modules = {
        ConfigModule.class,
        EppTestComponent.FakesAndMocksModule.class
    })
interface EppTestComponent {

  RequestComponent startRequest();

  /** Module for injecting fakes and mocks. */
  @Module
  static class FakesAndMocksModule {

    private BigQueryMetricsEnqueuer metricsEnqueuer;
    private DnsQueue dnsQueue;
    private DomainFlowTmchUtils domainFlowTmchUtils;
    private EppMetric.Builder metricBuilder;
    private FakeClock clock;
    private FakeLockHandler lockHandler;
    private ModulesService modulesService;
    private Sleeper sleeper;

    public static FakesAndMocksModule create() {
      FakeClock clock = new FakeClock();
      return create(clock, EppMetric.builderForRequest("request-id-1", clock));
    }

    public static FakesAndMocksModule create(FakeClock clock, EppMetric.Builder metricBuilder) {
      return create(
          clock,
          metricBuilder,
          new TmchXmlSignature(new TmchCertificateAuthority(TmchCaMode.PILOT)));
    }

    public static FakesAndMocksModule create(
        FakeClock clock,
        EppMetric.Builder eppMetricBuilder,
        TmchXmlSignature tmchXmlSignature) {
      FakesAndMocksModule instance = new FakesAndMocksModule();
      instance.clock = clock;
      instance.domainFlowTmchUtils = new DomainFlowTmchUtils(tmchXmlSignature);
      instance.sleeper = new FakeSleeper(clock);
      instance.dnsQueue = DnsQueue.create();
      instance.metricBuilder = eppMetricBuilder;
      instance.modulesService = mock(ModulesService.class);
      instance.metricsEnqueuer = mock(BigQueryMetricsEnqueuer.class);
      instance.lockHandler = new FakeLockHandler(true);
      return instance;
    }

    @Provides
    BigQueryMetricsEnqueuer provideBigQueryMetricsEnqueuer() {
      return metricsEnqueuer;
    }

    @Provides
    Clock provideClock() {
      return clock;
    }

    @Provides
    LockHandler provideLockHandler() {
      return lockHandler;
    }

    @Provides
    CustomLogicFactory provideCustomLogicFactory() {
      return new TestCustomLogicFactory();
    }

    @Provides
    DnsQueue provideDnsQueue() {
      return dnsQueue;
    }

    @Provides
    DomainFlowTmchUtils provideDomainFlowTmchUtils() {
      return domainFlowTmchUtils;
    }

    @Provides
    EppMetric.Builder provideMetrics() {
      return metricBuilder;
    }

    @Provides
    ModulesService provideModulesService() {
      return modulesService;
    }

    @Provides
    Sleeper provideSleeper() {
      return sleeper;
    }

    @Provides
    ServerTridProvider provideServerTridProvider() {
      return new FakeServerTridProvider();
    }
  }

  public static class FakeServerTridProvider implements ServerTridProvider {

    @Override
    public String createServerTrid() {
      return "server-trid";
    }
  }

  /** Subcomponent for request scoped injections. */
  @RequestScope
  @Subcomponent
  interface RequestComponent {
    EppController eppController();
    FlowComponent.Builder flowComponentBuilder();
  }
}


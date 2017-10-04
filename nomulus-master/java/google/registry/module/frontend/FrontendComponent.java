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

package google.registry.module.frontend;

import dagger.Component;
import google.registry.braintree.BraintreeModule;
import google.registry.config.RegistryConfig.ConfigModule;
import google.registry.flows.ServerTridProviderModule;
import google.registry.flows.custom.CustomLogicFactoryModule;
import google.registry.keyring.api.KeyModule;
import google.registry.keyring.kms.KeyringModule;
import google.registry.keyring.kms.KmsModule;
import google.registry.module.frontend.FrontendRequestComponent.FrontendRequestComponentModule;
import google.registry.monitoring.metrics.MetricReporter;
import google.registry.monitoring.whitebox.StackdriverModule;
import google.registry.request.Modules.AppIdentityCredentialModule;
import google.registry.request.Modules.Jackson2Module;
import google.registry.request.Modules.ModulesServiceModule;
import google.registry.request.Modules.UrlFetchTransportModule;
import google.registry.request.Modules.UseAppIdentityCredentialForGoogleApisModule;
import google.registry.request.Modules.UserServiceModule;
import google.registry.request.auth.AuthModule;
import google.registry.ui.ConsoleConfigModule;
import google.registry.util.SystemClock.SystemClockModule;
import google.registry.util.SystemSleeper.SystemSleeperModule;
import javax.inject.Singleton;

/** Dagger component with instance lifetime for "default" App Engine module. */
@Singleton
@Component(
    modules = {
        AppIdentityCredentialModule.class,
        AuthModule.class,
        BraintreeModule.class,
        ConfigModule.class,
        ConsoleConfigModule.class,
        CustomLogicFactoryModule.class,
        FrontendMetricsModule.class,
        FrontendRequestComponentModule.class,
        Jackson2Module.class,
        KeyModule.class,
        KeyringModule.class,
        KmsModule.class,
        ModulesServiceModule.class,
        ServerTridProviderModule.class,
        StackdriverModule.class,
        SystemClockModule.class,
        SystemSleeperModule.class,
        UrlFetchTransportModule.class,
        UseAppIdentityCredentialForGoogleApisModule.class,
        UserServiceModule.class,
    })
interface FrontendComponent {
  FrontendRequestHandler requestHandler();
  MetricReporter metricReporter();
}

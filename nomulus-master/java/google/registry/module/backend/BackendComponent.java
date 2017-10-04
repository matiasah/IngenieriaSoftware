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

package google.registry.module.backend;

import dagger.Component;
import google.registry.bigquery.BigqueryModule;
import google.registry.config.RegistryConfig.ConfigModule;
import google.registry.dns.writer.VoidDnsWriterModule;
import google.registry.export.DriveModule;
import google.registry.export.sheet.SheetsServiceModule;
import google.registry.gcs.GcsServiceModule;
import google.registry.groups.DirectoryModule;
import google.registry.groups.GroupsModule;
import google.registry.groups.GroupssettingsModule;
import google.registry.keyring.api.KeyModule;
import google.registry.keyring.kms.KeyringModule;
import google.registry.keyring.kms.KmsModule;
import google.registry.module.backend.BackendRequestComponent.BackendRequestComponentModule;
import google.registry.monitoring.metrics.MetricReporter;
import google.registry.monitoring.whitebox.StackdriverModule;
import google.registry.rde.JSchModule;
import google.registry.request.Modules.AppIdentityCredentialModule;
import google.registry.request.Modules.DatastoreServiceModule;
import google.registry.request.Modules.GoogleCredentialModule;
import google.registry.request.Modules.Jackson2Module;
import google.registry.request.Modules.ModulesServiceModule;
import google.registry.request.Modules.URLFetchServiceModule;
import google.registry.request.Modules.UrlFetchTransportModule;
import google.registry.request.Modules.UseAppIdentityCredentialForGoogleApisModule;
import google.registry.request.Modules.UserServiceModule;
import google.registry.request.auth.AuthModule;
import google.registry.util.SystemClock.SystemClockModule;
import google.registry.util.SystemSleeper.SystemSleeperModule;
import javax.inject.Singleton;

/** Dagger component with instance lifetime for "backend" App Engine module. */
@Singleton
@Component(
    modules = {
        AppIdentityCredentialModule.class,
        AuthModule.class,
        BackendRequestComponentModule.class,
        BigqueryModule.class,
        ConfigModule.class,
        DatastoreServiceModule.class,
        DirectoryModule.class,
        DriveModule.class,
        GcsServiceModule.class,
        GoogleCredentialModule.class,
        GroupsModule.class,
        GroupssettingsModule.class,
        JSchModule.class,
        Jackson2Module.class,
        KeyModule.class,
        KeyringModule.class,
        KmsModule.class,
        ModulesServiceModule.class,
        SheetsServiceModule.class,
        StackdriverModule.class,
        SystemClockModule.class,
        SystemSleeperModule.class,
        URLFetchServiceModule.class,
        UrlFetchTransportModule.class,
        UseAppIdentityCredentialForGoogleApisModule.class,
        UserServiceModule.class,
        VoidDnsWriterModule.class,
    })
interface BackendComponent {
  BackendRequestHandler requestHandler();
  MetricReporter metricReporter();
}

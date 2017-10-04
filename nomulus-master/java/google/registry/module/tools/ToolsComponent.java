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

package google.registry.module.tools;

import dagger.Component;
import google.registry.config.RegistryConfig.ConfigModule;
import google.registry.export.DriveModule;
import google.registry.flows.ServerTridProviderModule;
import google.registry.flows.custom.CustomLogicFactoryModule;
import google.registry.gcs.GcsServiceModule;
import google.registry.groups.DirectoryModule;
import google.registry.groups.GroupsModule;
import google.registry.groups.GroupssettingsModule;
import google.registry.keyring.api.KeyModule;
import google.registry.keyring.kms.KeyringModule;
import google.registry.keyring.kms.KmsModule;
import google.registry.module.tools.ToolsRequestComponent.ToolsRequestComponentModule;
import google.registry.request.Modules.AppIdentityCredentialModule;
import google.registry.request.Modules.DatastoreServiceModule;
import google.registry.request.Modules.GoogleCredentialModule;
import google.registry.request.Modules.Jackson2Module;
import google.registry.request.Modules.ModulesServiceModule;
import google.registry.request.Modules.UrlFetchTransportModule;
import google.registry.request.Modules.UseAppIdentityCredentialForGoogleApisModule;
import google.registry.request.Modules.UserServiceModule;
import google.registry.request.auth.AuthModule;
import google.registry.util.SystemClock.SystemClockModule;
import google.registry.util.SystemSleeper.SystemSleeperModule;
import javax.inject.Singleton;

/** Dagger component with instance lifetime for "tools" App Engine module. */
@Singleton
@Component(
    modules = {
        AppIdentityCredentialModule.class,
        AuthModule.class,
        ConfigModule.class,
        CustomLogicFactoryModule.class,
        DatastoreServiceModule.class,
        DirectoryModule.class,
        DriveModule.class,
        GcsServiceModule.class,
        GoogleCredentialModule.class,
        GroupsModule.class,
        GroupssettingsModule.class,
        Jackson2Module.class,
        KeyModule.class,
        KeyringModule.class,
        KmsModule.class,
        ModulesServiceModule.class,
        ServerTridProviderModule.class,
        SystemClockModule.class,
        SystemSleeperModule.class,
        ToolsRequestComponentModule.class,
        UrlFetchTransportModule.class,
        UseAppIdentityCredentialForGoogleApisModule.class,
        UserServiceModule.class,
    })
interface ToolsComponent {
  ToolsRequestHandler requestHandler();
}

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

package google.registry.groups;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.groupssettings.Groupssettings;
import com.google.api.services.groupssettings.GroupssettingsScopes;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import google.registry.config.RegistryConfig.Config;
import javax.inject.Named;

/** Dagger module for the Google {@link Groupssettings} service. */
@Module
public final class GroupssettingsModule {

  @Provides
  static Groupssettings provideDirectory(
      @Named("delegatedAdmin") GoogleCredential credential,
      @Config("projectId") String projectId) {
    return new Groupssettings.Builder(
            credential.getTransport(),
            credential.getJsonFactory(),
            credential.createScoped(ImmutableSet.of(GroupssettingsScopes.APPS_GROUPS_SETTINGS)))
        .setApplicationName(projectId)
        .build();
  }
}

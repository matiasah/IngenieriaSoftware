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

package google.registry.whois;

import dagger.Component;
import google.registry.config.RegistryConfig.ConfigModule;
import google.registry.request.RequestModule;
import google.registry.util.SystemClock.SystemClockModule;
import google.registry.util.SystemSleeper.SystemSleeperModule;
import javax.inject.Singleton;

@Singleton
@Component(modules = {
    ConfigModule.class,
    RequestModule.class,
    SystemClockModule.class,
    SystemSleeperModule.class,
    WhoisModule.class,
})
interface WhoisTestComponent {
  WhoisHttpServer whoisHttpServer();
  WhoisServer whoisServer();
}

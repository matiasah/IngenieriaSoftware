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

package google.registry.model.translators;

import org.joda.time.Duration;

/** Stores {@link Duration} as a canonicalized string. */
public class DurationTranslatorFactory extends AbstractSimpleTranslatorFactory<Duration, String> {

  public DurationTranslatorFactory() {
    super(Duration.class);
  }

  @Override
  protected SimpleTranslator<Duration, String> createTranslator() {
    return new SimpleTranslator<Duration, String>() {
      @Override
      public Duration loadValue(String datastoreValue) {
        return Duration.parse(datastoreValue);
      }

      @Override
      public String saveValue(Duration pojoValue) {
        return pojoValue.toString();
      }};
  }
}

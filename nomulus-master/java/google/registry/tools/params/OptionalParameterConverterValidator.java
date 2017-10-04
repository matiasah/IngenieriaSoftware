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

package google.registry.tools.params;

import com.google.common.base.Optional;
import google.registry.util.TypeUtils.TypeInstantiator;

/**
 * Class for parameters that can handle special string "null" or empty values to
 * indicate a desire to pass an empty value (i.e. when clearing out nullable
 * fields on a resource).
 */
public class OptionalParameterConverterValidator<T, C extends ParameterConverterValidator<T>>
    extends ParameterConverterValidator<Optional<T>> {

  private static final String NULL_STRING = "null";

  ParameterConverterValidator<T> validator = new TypeInstantiator<C>(getClass()){}.instantiate();

  @Override
  public void validate(String name, String value) {
    if (!value.isEmpty() && !value.equals(NULL_STRING)) {
      validator.validate(name, value);
    }
  }

  @Override
  public final Optional<T> convert(String value) {
    if (value.equals(NULL_STRING) || value.isEmpty()) {
      return Optional.absent();
    } else {
      return Optional.of(validator.convert(value));
    }
  }
}

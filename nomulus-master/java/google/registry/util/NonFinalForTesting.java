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

package google.registry.util;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation that discourages future maintainers from marking a field {@code final}.
 *
 * <p>This annotation serves purely as documention to indicate that even though a {@code private}
 * field may <em>appear</em> safe to change to {@code final}, it will actually be reflectively
 * modified by a unit test, and therefore should not be {@code final}.
 *
 * <p>When this annotation is used on methods, it means that you should not override the method
 * and it's only non-{@code final} so it can be mocked.
 *
 * @see google.registry.testing.InjectRule
 */
@Documented
@Retention(SOURCE)
@Target({FIELD, METHOD, TYPE})
public @interface NonFinalForTesting {}

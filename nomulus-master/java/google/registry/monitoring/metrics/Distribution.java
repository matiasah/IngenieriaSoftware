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

package google.registry.monitoring.metrics;

import com.google.common.collect.ImmutableRangeMap;

/**
 * Models a distribution of double-precision floating point sample data, and provides summary
 * statistics of the distribution. This class also models the probability density function (PDF) of
 * the distribution with a histogram.
 *
 * <p>The summary statistics provided are the mean and sumOfSquaredDeviation of the distribution.
 *
 * <p>The histogram fitting function is provided via a {@link DistributionFitter} implementation.
 *
 * @see DistributionFitter
 */
public interface Distribution {

  /** Returns the mean of this distribution. */
  double mean();

  /** Returns the sum of squared deviations from the mean of this distribution. */
  double sumOfSquaredDeviation();

  /** Returns the count of samples in this distribution. */
  long count();

  /** Returns a histogram of the distribution's values. */
  ImmutableRangeMap<Double, Long> intervalCounts();

  /** Returns the {@link DistributionFitter} of this distribution. */
  DistributionFitter distributionFitter();
}

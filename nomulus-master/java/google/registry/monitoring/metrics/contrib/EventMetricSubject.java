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

package google.registry.monitoring.metrics.contrib;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SubjectFactory;
import google.registry.monitoring.metrics.Distribution;
import google.registry.monitoring.metrics.EventMetric;
import google.registry.monitoring.metrics.MetricPoint;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Truth subject for the {@link EventMetric} class.
 *
 * <p>For use with the Google <a href="https://google.github.io/truth/">Truth</a> framework. Usage:
 *
 * <pre>  assertThat(myEventMetric)
 *       .hasAnyValueForLabels("label1", "label2", "label3")
 *       .and()
 *       .hasNoOtherValues();
 *   assertThat(myEventMetric)
 *       .doesNotHaveAnyValueForLabels("label1", "label2");
 * </pre>
 *
 * <p>The assertions treat an empty distribution as no value at all. This is not how the data is
 * actually stored; event metrics do in fact have an empty distribution after they are reset. But
 * it's difficult to write assertions about expected metric data when any number of empty
 * distributions can also be present, so they are screened out for convenience.
 */
public final class EventMetricSubject
    extends AbstractMetricSubject<Distribution, EventMetric, EventMetricSubject> {

  /** {@link SubjectFactory} for assertions about {@link EventMetric} objects. */
  private static final SubjectFactory<EventMetricSubject, EventMetric>
      SUBJECT_FACTORY =
          new SubjectFactory<EventMetricSubject, EventMetric>() {
            // The Truth extensibility documentation indicates that the target should be nullable.
            @Override
            public EventMetricSubject getSubject(
                FailureStrategy failureStrategy, @Nullable EventMetric target) {
              return new EventMetricSubject(failureStrategy, target);
            }
          };

  /** Static assertThat({@link EventMetric}) shortcut method. */
  public static EventMetricSubject assertThat(@Nullable EventMetric metric) {
    return assertAbout(SUBJECT_FACTORY).that(metric);
  }

  private EventMetricSubject(FailureStrategy strategy, EventMetric actual) {
    super(strategy, actual);
  }

  /**
   * Returns an indication to {@link AbstractMetricSubject#hasNoOtherValues} on whether a {@link
   * MetricPoint} has a non-empty distribution.
   */
  @Override
  protected boolean hasDefaultValue(MetricPoint<Distribution> metricPoint) {
    return metricPoint.value().count() == 0;
  }

  /** Returns an appropriate string representation of a metric value for use in error messages. */
  @Override
  protected String getMessageRepresentation(Distribution distribution) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<Range<Double>, Long> entry :
        distribution.intervalCounts().asMapOfRanges().entrySet()) {
      if (entry.getValue() != 0L) {
        if (first) {
          first = false;
        } else {
          sb.append(',');
        }
        if (entry.getKey().hasLowerBound()) {
          sb.append((entry.getKey().lowerBoundType() == BoundType.CLOSED) ? '[' : '(');
          sb.append(entry.getKey().lowerEndpoint());
        }
        sb.append("..");
        if (entry.getKey().hasUpperBound()) {
          sb.append(entry.getKey().upperEndpoint());
          sb.append((entry.getKey().upperBoundType() == BoundType.CLOSED) ? ']' : ')');
        }
        sb.append('=');
        sb.append(entry.getValue());
      }
    }
    sb.append('}');
    return sb.toString();
  }
}

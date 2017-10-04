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

package google.registry.flows.async;

import static com.google.appengine.api.taskqueue.QueueConstants.maxLeaseCount;
import static google.registry.flows.async.AsyncFlowMetrics.OperationType.CONTACT_AND_HOST_DELETE;
import static google.registry.flows.async.AsyncFlowMetrics.OperationType.DNS_REFRESH;
import static google.registry.monitoring.metrics.EventMetric.DEFAULT_FITTER;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import google.registry.monitoring.metrics.DistributionFitter;
import google.registry.monitoring.metrics.EventMetric;
import google.registry.monitoring.metrics.FibonacciFitter;
import google.registry.monitoring.metrics.IncrementableMetric;
import google.registry.monitoring.metrics.LabelDescriptor;
import google.registry.monitoring.metrics.MetricRegistryImpl;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;
import google.registry.util.NonFinalForTesting;
import javax.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * Instrumentation for async flows (contact/host deletion and DNS refreshes).
 *
 * @see AsyncFlowEnqueuer
 */
public class AsyncFlowMetrics {

  private static final FormattingLogger logger = getLoggerForCallerClass();

  private final Clock clock;

  @Inject
  public AsyncFlowMetrics(Clock clock) {
    this.clock = clock;
  }

  /**
   * A Fibonacci fitter used for bucketing the batch count.
   *
   * <p>We use a Fibonacci filter because it provides better resolution at the low end than an
   * exponential fitter, which is important because most batch sizes are likely to be very low,
   * despite going up to 1,000 on the high end. Also, the precision is better, as batch size is
   * inherently an integer, whereas an exponential fitter with an exponent base less than 2 would
   * have unintuitive boundaries.
   */
  private static final DistributionFitter FITTER_BATCH_SIZE =
      FibonacciFitter.create(maxLeaseCount());

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS =
      ImmutableSet.of(
          LabelDescriptor.create("operation_type", "The type of async flow operation."),
          LabelDescriptor.create("result", "The result of the async flow operation."));

  @NonFinalForTesting
  @VisibleForTesting
  static IncrementableMetric asyncFlowOperationCounts =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/async_flows/operations",
              "Count of Async Flow Operations",
              "count",
              LABEL_DESCRIPTORS);

  @NonFinalForTesting
  @VisibleForTesting
  static EventMetric asyncFlowOperationProcessingTime =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/async_flows/processing_time",
              "Async Flow Processing Time",
              "milliseconds",
              LABEL_DESCRIPTORS,
              DEFAULT_FITTER);

  @NonFinalForTesting
  @VisibleForTesting
  static EventMetric asyncFlowBatchSize =
      MetricRegistryImpl.getDefault()
          .newEventMetric(
              "/async_flows/batch_size",
              "Async Operation Batch Size",
              "batch size",
              ImmutableSet.of(
                  LabelDescriptor.create("operation_type", "The type of async flow operation.")),
              FITTER_BATCH_SIZE);

  /** The type of asynchronous operation. */
  public enum OperationType {
    CONTACT_DELETE("contactDelete"),
    HOST_DELETE("hostDelete"),
    CONTACT_AND_HOST_DELETE("contactAndHostDelete"),
    DNS_REFRESH("dnsRefresh");

    private final String metricLabelValue;

    private OperationType(String metricLabelValue) {
      this.metricLabelValue = metricLabelValue;
    }

    String getMetricLabelValue() {
      return metricLabelValue;
    }
  }

  /** The result of an asynchronous operation. */
  public enum OperationResult {
    /** The operation processed correctly and the result was success. */
    SUCCESS("success"),

    /** The operation processed correctly and the result was failure. */
    FAILURE("failure"),

    /** The operation did not process correctly due to some unexpected error. */
    ERROR("error"),

    /** The operation was skipped because the request is now stale. */
    STALE("stale");

    private final String metricLabelValue;

    private OperationResult(String metricLabelValue) {
      this.metricLabelValue = metricLabelValue;
    }

    String getMetricLabelValue() {
      return metricLabelValue;
    }
  }

  public void recordAsyncFlowResult(
      OperationType operationType, OperationResult operationResult, DateTime whenEnqueued) {
    asyncFlowOperationCounts.increment(
        operationType.getMetricLabelValue(), operationResult.getMetricLabelValue());
    long processingMillis = new Duration(whenEnqueued, clock.nowUtc()).getMillis();
    asyncFlowOperationProcessingTime.record(
        processingMillis,
        operationType.getMetricLabelValue(),
        operationResult.getMetricLabelValue());
    logger.infofmt(
        "Asynchronous %s operation took %d ms to process, yielding result: %s.",
        operationType.getMetricLabelValue(),
        processingMillis,
        operationResult.getMetricLabelValue());
  }

  public void recordContactHostDeletionBatchSize(long batchSize) {
    asyncFlowBatchSize.record(
        Double.valueOf(batchSize), CONTACT_AND_HOST_DELETE.getMetricLabelValue());
  }

  public void recordDnsRefreshBatchSize(long batchSize) {
    asyncFlowBatchSize.record(Double.valueOf(batchSize), DNS_REFRESH.getMetricLabelValue());
  }
}

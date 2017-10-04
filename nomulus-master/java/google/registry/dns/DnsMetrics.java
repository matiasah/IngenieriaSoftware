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

package google.registry.dns;

import com.google.common.collect.ImmutableSet;
import google.registry.monitoring.metrics.IncrementableMetric;
import google.registry.monitoring.metrics.LabelDescriptor;
import google.registry.monitoring.metrics.MetricRegistryImpl;
import javax.inject.Inject;

/** DNS instrumentation. */
public class DnsMetrics {

  /** Disposition of a publish request. */
  public enum Status { ACCEPTED, REJECTED }

  private static final ImmutableSet<LabelDescriptor> LABEL_DESCRIPTORS =
      ImmutableSet.of(
          LabelDescriptor.create("tld", "TLD"),
          LabelDescriptor.create(
              "status", "Whether the publish request was accepted or rejected."));

  private static final IncrementableMetric publishDomainRequests =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/dns/publish_domain_requests",
              "count of publishDomain requests",
              "count",
              LABEL_DESCRIPTORS);

  private static final IncrementableMetric publishHostRequests =
      MetricRegistryImpl.getDefault()
          .newIncrementableMetric(
              "/dns/publish_host_requests",
              "count of publishHost requests",
              "count",
              LABEL_DESCRIPTORS);

  @Inject
  DnsMetrics() {}

  /**
   * Increment a monotonic counter that tracks calls to {@link
   * google.registry.dns.writer.DnsWriter#publishDomain(String)}, per TLD.
   */
  public void incrementPublishDomainRequests(String tld, Status status) {
    publishDomainRequests.increment(tld, status.name());
  }

  /**
   * Increment a monotonic counter that tracks calls to {@link
   * google.registry.dns.writer.DnsWriter#publishHost(String)}, per TLD.
   */
  public void incrementPublishHostRequests(String tld, Status status) {
    publishHostRequests.increment(tld, status.name());
  }
}

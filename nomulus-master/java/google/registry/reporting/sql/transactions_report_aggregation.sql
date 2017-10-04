#standardSQL
  -- Copyright 2017 The Nomulus Authors. All Rights Reserved.
  --
  -- Licensed under the Apache License, Version 2.0 (the "License");
  -- you may not use this file except in compliance with the License.
  -- You may obtain a copy of the License at
  --
  --     http://www.apache.org/licenses/LICENSE-2.0
  --
  -- Unless required by applicable law or agreed to in writing, software
  -- distributed under the License is distributed on an "AS IS" BASIS,
  -- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -- See the License for the specific language governing permissions and
  -- limitations under the License.

  -- Construct the transaction reports' rows from the intermediary data views.

  -- This query pulls from all intermediary tables to create the activity
  -- report csv, via a table transpose and sum over all activity report fields.

SELECT
  registrars.tld as tld,
  registrars.registrar_name as registrar_name,
  registrars.iana_id as iana_id,
  SUM(IF(metrics.metricName = 'TOTAL_DOMAINS', metrics.metricValue, 0)) AS total_domains,
  SUM(IF(metrics.metricName = 'TOTAL_NAMESERVERS', metrics.metricValue, 0)) AS total_nameservers,
  SUM(IF(metrics.metricName = 'NET_ADDS_1_YR', metrics.metricValue, 0)) AS net_adds_1_yr,
  SUM(IF(metrics.metricName = 'NET_ADDS_2_YR', metrics.metricValue, 0)) AS net_adds_2_yr,
  SUM(IF(metrics.metricName = 'NET_ADDS_3_YR', metrics.metricValue, 0)) AS net_adds_3_yr,
  SUM(IF(metrics.metricName = 'NET_ADDS_4_YR', metrics.metricValue, 0)) AS net_adds_4_yr,
  SUM(IF(metrics.metricName = 'NET_ADDS_5_YR', metrics.metricValue, 0)) AS net_adds_5_yr,
  SUM(IF(metrics.metricName = 'NET_ADDS_6_YR', metrics.metricValue, 0)) AS net_adds_6_yr,
  SUM(IF(metrics.metricName = 'NET_ADDS_7_YR', metrics.metricValue, 0)) AS net_adds_7_yr,
  SUM(IF(metrics.metricName = 'NET_ADDS_8_YR', metrics.metricValue, 0)) AS net_adds_8_yr,
  SUM(IF(metrics.metricName = 'NET_ADDS_9_YR', metrics.metricValue, 0)) AS net_adds_9_yr,
  SUM(IF(metrics.metricName = 'NET_ADDS_10_Yr', metrics.metricValue, 0)) AS net_adds_10_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_1_YR', metrics.metricValue, 0)) AS net_renews_1_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_2_YR', metrics.metricValue, 0)) AS net_renews_2_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_3_YR', metrics.metricValue, 0)) AS net_renews_3_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_4_YR', metrics.metricValue, 0)) AS net_renews_4_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_5_YR', metrics.metricValue, 0)) AS net_renews_5_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_6_YR', metrics.metricValue, 0)) AS net_renews_6_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_7_YR', metrics.metricValue, 0)) AS net_renews_7_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_8_YR', metrics.metricValue, 0)) AS net_renews_8_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_9_YR', metrics.metricValue, 0)) AS net_renews_9_yr,
  SUM(IF(metrics.metricName = 'NET_RENEWS_10_YR', metrics.metricValue, 0)) AS net_renews_10_yr,
  SUM(IF(metrics.metricName = 'TRANSFER_GAINING_SUCCESSFUL', metrics.metricValue, 0)) AS transfer_gaining_successful,
  SUM(IF(metrics.metricName = 'TRANSFER_GAINING_NACKED', metrics.metricValue, 0)) AS transfer_gaining_nacked,
  SUM(IF(metrics.metricName = 'TRANSFER_LOSING_SUCCESSFUL', metrics.metricValue, 0)) AS transfer_losing_successful,
  SUM(IF(metrics.metricName = 'TRANSFER_LOSING_NACKED', metrics.metricValue, 0)) AS transfer_losing_nacked,
  -- We don't interact with transfer disputes
  0 AS transfer_disputed_won,
  0 AS transfer_disputed_lost,
  0 AS transfer_disputed_nodecision,
  SUM(IF(metrics.metricName = 'DELETED_DOMAINS_GRACE', metrics.metricValue, 0)) AS deleted_domains_grace,
  SUM(IF(metrics.metricName = 'DELETED_DOMAINS_NOGRACE', metrics.metricValue, 0)) AS deleted_domains_nograce,
  SUM(IF(metrics.metricName = 'RESTORED_DOMAINS', metrics.metricValue, 0)) AS restored_domains,
  -- We don't require restore reports
  0 AS restored_noreport,
  -- We don't enforce AGP limits right now
  0 AS agp_exemption_requests,
  0 AS agp_exemptions_granted,
  0 AS agp_exempted_domains,
  SUM(IF(metrics.metricName = 'ATTEMPTED_ADDS', metrics.metricValue, 0)) AS attempted_adds
FROM (
  SELECT *
  FROM `%PROJECT_ID%.%ICANN_REPORTING_DATA_SET%.%REGISTRAR_IANA_ID_TABLE%`) AS registrars
-- We LEFT JOIN to produce reports even if the registrar made no transactions
LEFT OUTER JOIN (
  -- Gather all intermediary data views
  SELECT *
  FROM `%PROJECT_ID%.%ICANN_REPORTING_DATA_SET%.%TOTAL_DOMAINS_TABLE%`
  UNION ALL
  SELECT *
  FROM `%PROJECT_ID%.%ICANN_REPORTING_DATA_SET%.%TOTAL_NAMESERVERS_TABLE%`
  UNION ALL
  SELECT *
  FROM `%PROJECT_ID%.%ICANN_REPORTING_DATA_SET%.%TRANSACTION_COUNTS_TABLE%`
  UNION ALL
  SELECT *
  FROM `%PROJECT_ID%.%ICANN_REPORTING_DATA_SET%.%TRANSACTION_TRANSFER_LOSING_TABLE%`
  UNION ALL
  SELECT *
  FROM `%PROJECT_ID%.%ICANN_REPORTING_DATA_SET%.%ATTEMPTED_ADDS_TABLE%` ) AS metrics
-- Join on tld and registrar name
ON registrars.tld = metrics.tld
AND registrars.registrar_name = metrics.registrar_name
GROUP BY
tld, registrar_name, iana_id
ORDER BY
tld, registrar_name


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

package google.registry.loadtest;

import static com.google.appengine.api.taskqueue.QueueConstants.maxTasksPerAdd;
import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.partition;
import static com.google.common.collect.Lists.transform;
import static google.registry.security.XsrfTokenManager.X_CSRF_TOKEN;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;
import static google.registry.util.ResourceUtils.readResourceUtf8;
import static java.util.Arrays.asList;
import static org.joda.time.DateTimeZone.UTC;

import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import google.registry.config.RegistryEnvironment;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.auth.Auth;
import google.registry.security.XsrfTokenManager;
import google.registry.util.FormattingLogger;
import google.registry.util.TaskEnqueuer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * Simple load test action that can generate configurable QPSes of various EPP actions.
 *
 * <p>All aspects of the load test are configured via URL parameters that are specified when the
 * loadtest URL is being POSTed to. The {@code clientId} and {@code tld} parameters are required.
 * All of the other parameters are optional, but if none are specified then no actual load testing
 * will be done since all of the different kinds of checks default to running zero per second. So at
 * least one must be specified in order for load testing to do anything.
 */
@Action(
  path = LoadTestAction.PATH,
  method = Action.Method.POST,
  automaticallyPrintOk = true,
  auth = Auth.AUTH_INTERNAL_OR_ADMIN
)
public class LoadTestAction implements Runnable {

  private static final FormattingLogger logger = getLoggerForCallerClass();

  private static final int NUM_QUEUES = 10;
  private static final int ARBITRARY_VALID_HOST_LENGTH = 40;
  private static final int MAX_CONTACT_LENGTH = 13;
  private static final int MAX_DOMAIN_LABEL_LENGTH = 63;

  private static final String EXISTING_DOMAIN = "testdomain";
  private static final String EXISTING_CONTACT = "contact";
  private static final String EXISTING_HOST = "ns1";

  private static final Random random = new Random();

  public static final String PATH = "/_dr/loadtest";

  /** The client identifier of the registrar to use for load testing. */
  @Inject
  @Parameter("loadtestClientId")
  String clientId;

  /**
   * The number of seconds to delay the execution of the first load testing tasks by. Preparatory
   * work of creating independent contacts and hosts that will be used for later domain creation
   * testing occurs during this period, so make sure that it is long enough.
   */
  @Inject
  @Parameter("delaySeconds")
  int delaySeconds;

  /**
   * The number of seconds that tasks will be enqueued for. Note that if system QPS cannot handle
   * the given load then it will take longer than this number of seconds for the test to complete.
   */
  @Inject
  @Parameter("runSeconds")
  int runSeconds;

  /** The number of successful domain creates to enqueue per second over the length of the test. */
  @Inject
  @Parameter("successfulDomainCreates")
  int successfulDomainCreatesPerSecond;

  /** The number of failed domain creates to enqueue per second over the length of the test. */
  @Inject
  @Parameter("failedDomainCreates")
  int failedDomainCreatesPerSecond;

  /** The number of successful domain infos to enqueue per second over the length of the test. */
  @Inject
  @Parameter("domainInfos")
  int domainInfosPerSecond;

  /** The number of successful domain checks to enqueue per second over the length of the test. */
  @Inject
  @Parameter("domainChecks")
  int domainChecksPerSecond;

  /** The number of successful contact creates to enqueue per second over the length of the test. */
  @Inject
  @Parameter("successfulContactCreates")
  int successfulContactCreatesPerSecond;

  /** The number of failed contact creates to enqueue per second over the length of the test. */
  @Inject
  @Parameter("failedContactCreates")
  int failedContactCreatesPerSecond;

  /** The number of successful contact infos to enqueue per second over the length of the test. */
  @Inject
  @Parameter("contactInfos")
  int contactInfosPerSecond;

  /** The number of successful host creates to enqueue per second over the length of the test. */
  @Inject
  @Parameter("successfulHostCreates")
  int successfulHostCreatesPerSecond;

  /** The number of failed host creates to enqueue per second over the length of the test. */
  @Inject
  @Parameter("failedHostCreates")
  int failedHostCreatesPerSecond;

  /** The number of successful host infos to enqueue per second over the length of the test. */
  @Inject
  @Parameter("hostInfos")
  int hostInfosPerSecond;

  @Inject
  TaskEnqueuer taskEnqueuer;

  private final String xmlContactCreateTmpl;
  private final String xmlContactCreateFail;
  private final String xmlContactInfo;
  private final String xmlDomainCheck;
  private final String xmlDomainCreateTmpl;
  private final String xmlDomainCreateFail;
  private final String xmlDomainInfo;
  private final String xmlHostCreateTmpl;
  private final String xmlHostCreateFail;
  private final String xmlHostInfo;

  /**
   * The XSRF token to be used for making requests to the epptool endpoint.
   *
   * <p>Note that the email address is set to empty, because the logged-in user hitting this
   * endpoint will not be the same as when the tasks themselves fire and hit the epptool endpoint.
   */
  private final String xsrfToken;

  @Inject
  LoadTestAction(@Parameter("tld") String tld, XsrfTokenManager xsrfTokenManager) {
    xmlContactCreateTmpl = loadXml("contact_create");
    xmlContactCreateFail = xmlContactCreateTmpl.replace("%contact%", EXISTING_CONTACT);
    xmlContactInfo = loadXml("contact_info").replace("%contact%", EXISTING_CONTACT);
    xmlDomainCheck =
        loadXml("domain_check").replace("%tld%", tld).replace("%domain%", EXISTING_DOMAIN);
    xmlDomainCreateTmpl = loadXml("domain_create").replace("%tld%", tld);
    xmlDomainCreateFail =
        xmlDomainCreateTmpl
            .replace("%domain%", EXISTING_DOMAIN)
            .replace("%contact%", EXISTING_CONTACT)
            .replace("%host%", EXISTING_HOST);
    xmlDomainInfo =
        loadXml("domain_info").replace("%tld%", tld).replace("%domain%", EXISTING_DOMAIN);
    xmlHostCreateTmpl = loadXml("host_create");
    xmlHostCreateFail = xmlHostCreateTmpl.replace("%host%", EXISTING_HOST);
    xmlHostInfo = loadXml("host_info").replace("%host%", EXISTING_HOST);
    xsrfToken = xsrfTokenManager.generateToken("");
  }

  @Override
  public void run() {
    validateAndLogRequest();
    DateTime initialStartSecond = DateTime.now(UTC).plusSeconds(delaySeconds);
    ImmutableList.Builder<String> preTaskXmls = new ImmutableList.Builder<>();
    ImmutableList.Builder<String> contactNamesBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<String> hostPrefixesBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < successfulDomainCreatesPerSecond; i++) {
      String contactName = getRandomLabel(MAX_CONTACT_LENGTH);
      String hostPrefix = getRandomLabel(ARBITRARY_VALID_HOST_LENGTH);
      contactNamesBuilder.add(contactName);
      hostPrefixesBuilder.add(hostPrefix);
      preTaskXmls.add(
          xmlContactCreateTmpl.replace("%contact%", contactName),
          xmlHostCreateTmpl.replace("%host%", hostPrefix));
    }
    enqueue(createTasks(preTaskXmls.build(), DateTime.now(UTC)));
    ImmutableList<String> contactNames = contactNamesBuilder.build();
    ImmutableList<String> hostPrefixes = hostPrefixesBuilder.build();

    ImmutableList.Builder<TaskOptions> tasks = new ImmutableList.Builder<>();
    for (int offsetSeconds = 0; offsetSeconds < runSeconds; offsetSeconds++) {
      DateTime startSecond = initialStartSecond.plusSeconds(offsetSeconds);
      // The first "failed" creates might actually succeed if the object doesn't already exist, but
      // that shouldn't affect the load numbers.
      tasks.addAll(
          createTasks(
              createNumCopies(xmlContactCreateFail, failedContactCreatesPerSecond), startSecond));
      tasks.addAll(
          createTasks(createNumCopies(xmlHostCreateFail, failedHostCreatesPerSecond), startSecond));
      tasks.addAll(
          createTasks(
              createNumCopies(xmlDomainCreateFail, failedDomainCreatesPerSecond), startSecond));
      // We can do infos on the known existing objects.
      tasks.addAll(
          createTasks(createNumCopies(xmlContactInfo, contactInfosPerSecond), startSecond));
      tasks.addAll(createTasks(createNumCopies(xmlHostInfo, hostInfosPerSecond), startSecond));
      tasks.addAll(createTasks(createNumCopies(xmlDomainInfo, domainInfosPerSecond), startSecond));
      // The domain check template uses "example.TLD" which won't exist, and one existing domain.
      tasks.addAll(
          createTasks(createNumCopies(xmlDomainCheck, domainChecksPerSecond), startSecond));
      // Do successful creates on random names
      tasks.addAll(
          createTasks(
              transform(
                  createNumCopies(xmlContactCreateTmpl, successfulContactCreatesPerSecond),
                  randomNameReplacer("%contact%", MAX_CONTACT_LENGTH)),
              startSecond));
      tasks.addAll(
          createTasks(
              transform(
                  createNumCopies(xmlHostCreateTmpl, successfulHostCreatesPerSecond),
                  randomNameReplacer("%host%", ARBITRARY_VALID_HOST_LENGTH)),
              startSecond));
      tasks.addAll(
          createTasks(
              FluentIterable.from(
                      createNumCopies(xmlDomainCreateTmpl, successfulDomainCreatesPerSecond))
                  .transform(randomNameReplacer("%domain%", MAX_DOMAIN_LABEL_LENGTH))
                  .transform(listNameReplacer("%contact%", contactNames))
                  .transform(listNameReplacer("%host%", hostPrefixes))
                  .toList(),
              startSecond));
    }
    ImmutableList<TaskOptions> taskOptions = tasks.build();
    enqueue(taskOptions);
    logger.infofmt("Added %d total load test tasks", taskOptions.size());
  }

  private void validateAndLogRequest() {
    checkArgument(
        RegistryEnvironment.get() != RegistryEnvironment.PRODUCTION,
        "DO NOT RUN LOADTESTS IN PROD!");
    checkArgument(
        successfulDomainCreatesPerSecond > 0
            || failedDomainCreatesPerSecond > 0
            || domainInfosPerSecond > 0
            || domainChecksPerSecond > 0
            || successfulContactCreatesPerSecond > 0
            || failedContactCreatesPerSecond > 0
            || contactInfosPerSecond > 0
            || successfulHostCreatesPerSecond > 0
            || failedHostCreatesPerSecond > 0
            || hostInfosPerSecond > 0,
        "You must specify at least one of the 'operations per second' parameters.");
    logger.infofmt(
        "Running load test with the following params. clientId: %s, delaySeconds: %d, "
            + "runSeconds: %d, successful|failed domain creates/s: %d|%d, domain infos/s: %d, "
            + "domain checks/s: %d, successful|failed contact creates/s: %d|%d, "
            + "contact infos/s: %d, successful|failed host creates/s: %d|%d, host infos/s: %d.",
        clientId,
        delaySeconds,
        runSeconds,
        successfulDomainCreatesPerSecond,
        failedDomainCreatesPerSecond,
        domainInfosPerSecond,
        domainChecksPerSecond,
        successfulContactCreatesPerSecond,
        failedContactCreatesPerSecond,
        contactInfosPerSecond,
        successfulHostCreatesPerSecond,
        failedHostCreatesPerSecond,
        hostInfosPerSecond);
  }

  private String loadXml(String name) {
    return readResourceUtf8(LoadTestAction.class, String.format("templates/%s.xml", name));
  }

  private List<String> createNumCopies(String xml, int numCopies) {
    String[] xmls = new String[numCopies];
    Arrays.fill(xmls, xml);
    return asList(xmls);
  }

  private Function<String, String> listNameReplacer(final String toReplace, List<String> choices) {
    final Iterator<String> iterator = Iterators.cycle(choices);
    return new Function<String, String>() {
      @Override
      public String apply(String xml) {
        return xml.replace(toReplace, iterator.next());
      }};
  }

  private Function<String, String> randomNameReplacer(final String toReplace, final int numChars) {
    return new Function<String, String>() {
      @Override
      public String apply(String xml) {
        return xml.replace(toReplace, getRandomLabel(numChars));
      }};
  }

  private String getRandomLabel(int numChars) {
    StringBuilder name = new StringBuilder();
    for (int j = 0; j < numChars; j++) {
      name.append(Character.forDigit(random.nextInt(Character.MAX_RADIX), Character.MAX_RADIX));
    }
    return name.toString();
  }

  private List<TaskOptions> createTasks(List<String> xmls, DateTime start) {
    ImmutableList.Builder<TaskOptions> tasks = new ImmutableList.Builder<>();
    for (int i = 0; i < xmls.size(); i++) {
      // Space tasks evenly within across a second.
      int offsetMillis = (int) (1000.0 / xmls.size() * i);
      tasks.add(TaskOptions.Builder.withUrl("/_dr/epptool")
          .etaMillis(start.getMillis() + offsetMillis)
          .header(X_CSRF_TOKEN, xsrfToken)
          .param("clientId", clientId)
          .param("superuser", Boolean.FALSE.toString())
          .param("dryRun", Boolean.FALSE.toString())
          .param("xml", xmls.get(i)));
    }
    return tasks.build();
  }

  private void enqueue(List<TaskOptions> tasks) {
    List<List<TaskOptions>> chunks = partition(tasks, maxTasksPerAdd());
    // Farm out tasks to multiple queues to work around queue qps quotas.
    for (int i = 0; i < chunks.size(); i++) {
      taskEnqueuer.enqueue(getQueue("load" + (i % NUM_QUEUES)), chunks.get(i));
    }
  }
}

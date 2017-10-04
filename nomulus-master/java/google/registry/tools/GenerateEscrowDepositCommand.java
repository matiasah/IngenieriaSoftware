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

package google.registry.tools;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static google.registry.model.registry.Registries.assertTldsExist;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskOptions;
import google.registry.model.rde.RdeMode;
import google.registry.rde.RdeModule;
import google.registry.rde.RdeStagingAction;
import google.registry.request.RequestParameters;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.DateTimeParameter;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import org.joda.time.DateTime;

/**
 * Command to kick off the server-side generation of an XML RDE or BRDA escrow deposit, which will
 * be stored in the specified manual subdirectory of the GCS RDE bucket.
 */
@Parameters(separators = " =", commandDescription = "Generate an XML escrow deposit.")
final class GenerateEscrowDepositCommand implements RemoteApiCommand {

  @Parameter(
      names = {"-t", "--tld"},
      description = "Top level domain(s) for which deposit should be generated.",
      required = true)
  private List<String> tlds;

  @Parameter(
      names = {"-w", "--watermark"},
      description = "Point-in-time timestamp(s) for snapshotting Datastore.",
      required = true,
      converter = DateTimeParameter.class)
  private List<DateTime> watermarks;

  @Parameter(
      names = {"-m", "--mode"},
      description = "Mode of operation: FULL for RDE deposits, THIN for BRDA deposits.")
  private RdeMode mode = RdeMode.FULL;

  @Parameter(
      names = {"-r", "--revision"},
      description = "Revision number. Use >0 for resends.")
  private Integer revision;

  @Parameter(
      names = {"-o", "--outdir"},
      description = "Specify output subdirectory (under GCS RDE bucket, directory manual).",
      required = true)
  private String outdir;

  @Inject ModulesService modulesService;
  @Inject @Named("rde-report") Queue queue;

  @Override
  public void run() throws Exception {

    if (tlds.isEmpty()) {
      throw new ParameterException("At least one TLD must be specified");
    }
    assertTldsExist(tlds);

    for (DateTime watermark : watermarks) {
      if (!watermark.withTimeAtStartOfDay().equals(watermark)) {
        throw new ParameterException("Each watermark date must be the start of a day");
      }
    }

    if ((revision != null) && (revision < 0)) {
      throw new ParameterException("Revision must be greater than or equal to zero");
    }

    if (outdir.isEmpty()) {
      throw new ParameterException("Output subdirectory must not be empty");
    }

    // Unlike many tool commands, this command is actually invoking an action on the backend module
    // (because it's a mapreduce). So we invoke it in a different way.
    String hostname = modulesService.getVersionHostname("backend", null);
    TaskOptions opts =
        withUrl(RdeStagingAction.PATH)
            .header("Host", hostname)
            .param(RdeModule.PARAM_MANUAL, String.valueOf(true))
            .param(RdeModule.PARAM_MODE, mode.toString())
            .param(RdeModule.PARAM_DIRECTORY, outdir);
    for (String tld : tlds) {
      opts = opts.param(RequestParameters.PARAM_TLD, tld);
    }
    for (DateTime watermark : watermarks) {
      opts = opts.param(RdeModule.PARAM_WATERMARK, watermark.toString());
    }
    if (revision != null) {
      opts = opts.param(RdeModule.PARAM_REVISION, String.valueOf(revision));
    }
    queue.add(opts);
  }
}

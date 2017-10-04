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

package google.registry.tools.server;

import com.google.common.collect.ImmutableMap;
import google.registry.request.JsonResponse;
import google.registry.request.Parameter;
import google.registry.util.FormattingLogger;
import javax.inject.Inject;

/**
 * Abstract base class for actions that update premium lists.
 */
public abstract class CreateOrUpdatePremiumListAction implements Runnable {

  protected static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  public static final String NAME_PARAM = "name";
  public static final String INPUT_PARAM = "inputData";

  @Inject JsonResponse response;
  @Inject @Parameter("premiumListName") String name;
  @Inject @Parameter(INPUT_PARAM) String inputData;

  @Override
  public void run() {
    try {
      savePremiumList();
    } catch (IllegalArgumentException iae) {
      logger.info(iae, "Usage error in attempting to save premium list from nomulus tool command");
      response.setPayload(ImmutableMap.of("error", iae.toString(), "status", "error"));
    } catch (Exception e) {
      logger.severe(e, "Unexpected error saving premium list from nomulus tool command");
      response.setPayload(ImmutableMap.of("error", e.toString(), "status", "error"));
    }
  }

  /** Creates a new premium list or updates an existing one. */
  protected abstract void savePremiumList();
}

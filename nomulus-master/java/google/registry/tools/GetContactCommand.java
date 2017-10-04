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

import static google.registry.model.EppResourceUtils.loadByForeignKey;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import google.registry.model.contact.ContactResource;
import java.util.List;

/** Command to show one or more contacts. */
@Parameters(separators = " =", commandDescription = "Show contact resource(s)")
final class GetContactCommand extends GetEppResourceCommand {

  @Parameter(
      description = "Contact id(s)",
      required = true)
  private List<String> mainParameters;

  @Override
  public void runAndPrint() {
    for (String contactId : mainParameters) {
      printResource(
          "Contact", contactId, loadByForeignKey(ContactResource.class, contactId, readTimestamp));
    }
  }
}

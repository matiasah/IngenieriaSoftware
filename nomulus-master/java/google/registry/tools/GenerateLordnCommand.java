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

import static google.registry.model.ofy.ObjectifyService.ofy;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.ImmutableList;
import google.registry.model.domain.DomainResource;
import google.registry.tmch.LordnTask;
import google.registry.tools.Command.RemoteApiCommand;
import google.registry.tools.params.PathParameter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joda.time.DateTime;

/** Command to generate a LORDN CSV file for an entire TLD. */
@Parameters(separators = " =", commandDescription = "Generate LORDN CSV file")
final class GenerateLordnCommand implements RemoteApiCommand {

  @Parameter(
      names = {"-t", "--tld"},
      description = "TLD to generate LORDN for",
      required = true)
  private String tld;

  @Parameter(
      names = {"-c", "--claims_file"},
      description = "Claims CSV output file.",
      validateWith = PathParameter.OutputFile.class,
      required = true)
  private Path claimsOutputPath;

  @Parameter(
      names = {"-s", "--sunrise_file"},
      description = "Sunrise CSV output file.",
      validateWith = PathParameter.OutputFile.class,
      required = true)
  private Path sunriseOutputPath;

  @Override
  public void run() throws IOException {
    DateTime now = DateTime.now(UTC);
    ImmutableList.Builder<String> claimsCsv = new ImmutableList.Builder<>();
    ImmutableList.Builder<String> sunriseCsv = new ImmutableList.Builder<>();
    for (DomainResource domain : ofy().load().type(DomainResource.class).filter("tld", tld)) {
      String status = " ";
      if (domain.getLaunchNotice() == null && domain.getSmdId() != null) {
        sunriseCsv.add(LordnTask.getCsvLineForSunriseDomain(domain, domain.getCreationTime()));
        status = "S";
      } else if (domain.getLaunchNotice() != null || domain.getSmdId() != null) {
        claimsCsv.add(LordnTask.getCsvLineForClaimsDomain(domain, domain.getCreationTime()));
        status = "C";
      }
      System.out.printf("%s[%s] ", domain.getFullyQualifiedDomainName(), status);
    }
    ImmutableList<String> claimsRows = claimsCsv.build();
    ImmutableList<String> claimsAll = new ImmutableList.Builder<String>()
        .add(String.format("1,%s,%d", now, claimsRows.size()))
        .add(LordnTask.COLUMNS_CLAIMS)
        .addAll(claimsRows)
        .build();
    ImmutableList<String> sunriseRows = sunriseCsv.build();
    ImmutableList<String> sunriseAll = new ImmutableList.Builder<String>()
        .add(String.format("1,%s,%d", now.plusMillis(1), sunriseRows.size()))
        .add(LordnTask.COLUMNS_SUNRISE)
        .addAll(sunriseRows)
        .build();
    Files.write(claimsOutputPath, claimsAll, UTF_8);
    Files.write(sunriseOutputPath, sunriseAll, UTF_8);
  }
}

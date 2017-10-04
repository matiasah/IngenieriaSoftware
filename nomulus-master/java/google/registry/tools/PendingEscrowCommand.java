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

import com.beust.jcommander.Parameters;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Ordering;
import google.registry.rde.PendingDeposit;
import google.registry.rde.PendingDepositChecker;
import google.registry.tools.Command.RemoteApiCommand;
import javax.inject.Inject;

/** Command to show what escrow deposits are pending generation on the server. */
@Parameters(separators = " =", commandDescription = "List pending RDE/BRDA deposits.")
final class PendingEscrowCommand implements RemoteApiCommand {

  private static final Ordering<PendingDeposit> SORTER =
      new Ordering<PendingDeposit>() {
        @Override
        public int compare(PendingDeposit left, PendingDeposit right) {
          return ComparisonChain.start()
              .compare(left.tld(), right.tld())
              .compare(left.mode(), right.mode())
              .compare(left.watermark(), right.watermark())
              .result();
        }};

  @Inject
  PendingDepositChecker checker;

  @Override
  public void run() throws Exception {
    System.out.println(FluentIterable
        .from(SORTER.sortedCopy(checker.getTldsAndWatermarksPendingDepositForRdeAndBrda().values()))
        .transform(Functions.toStringFunction())
        .join(Joiner.on('\n')));
  }
}

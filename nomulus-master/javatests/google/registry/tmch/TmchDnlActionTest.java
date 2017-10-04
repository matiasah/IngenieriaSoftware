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

package google.registry.tmch;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import google.registry.model.tmch.ClaimsListShard;
import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link TmchDnlAction}. */
public class TmchDnlActionTest extends TmchActionTestCase {

  private TmchDnlAction newTmchDnlAction() {
    TmchDnlAction action = new TmchDnlAction();
    action.marksdb = marksdb;
    action.marksdbDnlLogin = Optional.of(MARKSDB_LOGIN);
    return action;
  }

  @Test
  public void testDnl() throws Exception {
    assertThat(ClaimsListShard.get().getClaimKey("xn----7sbejwbn3axu3d")).isNull();
    when(httpResponse.getContent())
        .thenReturn(TmchTestData.loadBytes("dnl-latest.csv").read())
        .thenReturn(TmchTestData.loadBytes("dnl-latest.sig").read());
    newTmchDnlAction().run();
    verify(fetchService, times(2)).fetch(httpRequest.capture());
    assertThat(httpRequest.getAllValues().get(0).getURL().toString())
        .isEqualTo(MARKSDB_URL + "/dnl/dnl-latest.csv");
    assertThat(httpRequest.getAllValues().get(1).getURL().toString())
        .isEqualTo(MARKSDB_URL + "/dnl/dnl-latest.sig");

    // Make sure the contents of testdata/dnl-latest.csv got inserted into the database.
    ClaimsListShard claimsList = ClaimsListShard.get();
    assertThat(claimsList.getCreationTime()).isEqualTo(DateTime.parse("2013-11-24T23:15:37.4Z"));
    assertThat(claimsList.getClaimKey("xn----7sbejwbn3axu3d"))
        .isEqualTo("2013112500/7/4/8/dIHW0DiuybvhdP8kIz");
    assertThat(claimsList.getClaimKey("lolcat")).isNull();
  }
}

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


package google.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import google.registry.testing.FakeHttpSession;
import google.registry.testing.ShardableTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/** Tests for {@link EppTlsAction}. */
@RunWith(JUnit4.class)
public class EppTlsActionTest extends ShardableTestCase {

  private static final byte[] INPUT_XML_BYTES = "<xml>".getBytes(UTF_8);

  @Test
  public void testPassesArgumentsThrough() {
    EppTlsAction action = new EppTlsAction();
    action.inputXmlBytes = INPUT_XML_BYTES;
    action.tlsCredentials = mock(TlsCredentials.class);
    when(action.tlsCredentials.hasSni()).thenReturn(true);
    action.session = new FakeHttpSession();
    action.session.setAttribute("CLIENT_ID", "ClientIdentifier");
    action.eppRequestHandler = mock(EppRequestHandler.class);
    action.run();
    ArgumentCaptor<SessionMetadata> captor = ArgumentCaptor.forClass(SessionMetadata.class);
    verify(action.eppRequestHandler).executeEpp(
        captor.capture(),
        same(action.tlsCredentials),
        eq(EppRequestSource.TLS),
        eq(false),
        eq(false),
        eq(INPUT_XML_BYTES));
    assertThat(captor.getValue().getClientId()).isEqualTo("ClientIdentifier");
  }
}

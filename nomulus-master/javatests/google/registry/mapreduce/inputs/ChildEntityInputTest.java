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

package google.registry.mapreduce.inputs;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static google.registry.mapreduce.inputs.EppResourceInputs.createChildEntityInput;
import static google.registry.model.index.EppResourceIndexBucket.getBucketKey;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newContactResource;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistEppResourceInFirstBucket;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResource;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;

import com.google.appengine.tools.mapreduce.InputReader;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.EppResource;
import google.registry.model.ImmutableObject;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainResource;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link ChildEntityInput} */
@RunWith(JUnit4.class)
public class ChildEntityInputTest {

  private static final DateTime now = DateTime.now(DateTimeZone.UTC);

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  DomainResource domainA;
  DomainResource domainB;
  HistoryEntry domainHistoryEntryA;
  HistoryEntry domainHistoryEntryB;
  HistoryEntry contactHistoryEntry;
  BillingEvent.OneTime oneTimeA;
  BillingEvent.OneTime oneTimeB;
  BillingEvent.Recurring recurringA;
  BillingEvent.Recurring recurringB;

  private void setupResources() {
    createTld("tld");
    ContactResource contact = persistEppResourceInFirstBucket(newContactResource("contact1234"));
    domainA = persistEppResourceInFirstBucket(newDomainResource("a.tld", contact));
    domainHistoryEntryA = persistResource(
        new HistoryEntry.Builder()
            .setParent(domainA)
            .setModificationTime(now)
            .build());
    contactHistoryEntry = persistResource(
        new HistoryEntry.Builder()
            .setParent(contact)
            .setModificationTime(now)
            .build());
    oneTimeA = persistResource(
        new BillingEvent.OneTime.Builder()
            .setParent(domainHistoryEntryA)
            .setReason(Reason.CREATE)
            .setFlags(ImmutableSet.of(BillingEvent.Flag.ANCHOR_TENANT))
            .setPeriodYears(2)
            .setCost(Money.of(USD, 1))
            .setEventTime(now)
            .setBillingTime(now.plusDays(5))
            .setClientId("TheRegistrar")
            .setTargetId("a.tld")
            .build());
    recurringA = persistResource(
        new BillingEvent.Recurring.Builder()
            .setParent(domainHistoryEntryA)
            .setReason(Reason.RENEW)
            .setEventTime(now.plusYears(1))
            .setRecurrenceEndTime(END_OF_TIME)
            .setClientId("TheRegistrar")
            .setTargetId("a.tld")
            .build());
  }

  private void setupSecondDomainResources() {
    domainB = persistEppResourceInFirstBucket(newDomainResource("b.tld"));
    domainHistoryEntryB = persistResource(
        new HistoryEntry.Builder()
            .setParent(domainB)
            .setModificationTime(now)
            .build());
    oneTimeB = persistResource(
        new BillingEvent.OneTime.Builder()
            .setParent(domainHistoryEntryA)
            .setReason(Reason.CREATE)
            .setFlags(ImmutableSet.of(BillingEvent.Flag.ANCHOR_TENANT))
            .setPeriodYears(2)
            .setCost(Money.of(USD, 1))
            .setEventTime(now)
            .setBillingTime(now.plusDays(5))
            .setClientId("TheRegistrar")
            .setTargetId("a.tld")
            .build());
    recurringB = persistResource(
        new BillingEvent.Recurring.Builder()
            .setParent(domainHistoryEntryA)
            .setReason(Reason.RENEW)
            .setEventTime(now.plusYears(1))
            .setRecurrenceEndTime(END_OF_TIME)
            .setClientId("TheRegistrar")
            .setTargetId("a.tld")
            .build());
  }

  @SuppressWarnings("unchecked")
  private <T> T serializeAndDeserialize(T obj) throws Exception {
    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream objectOut = new ObjectOutputStream(byteOut)) {
      objectOut.writeObject(obj);
      try (ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
          ObjectInputStream objectIn = new ObjectInputStream(byteIn)) {
        return (T) objectIn.readObject();
      }
    }
  }

  @Test
  public void testSuccess_childEntityReader_multipleParentsAndChildren() throws Exception {
    setupResources();
    setupSecondDomainResources();
    Set<ImmutableObject> seen = new HashSet<>();
    InputReader<ImmutableObject> reader = EppResourceInputs.createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(EppResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(
            HistoryEntry.class, BillingEvent.OneTime.class, BillingEvent.Recurring.class))
        .createReaders().get(0);
    reader.beginShard();
    reader.beginSlice();
    for (int i = 0; i < 8; i++) {
      reader.endSlice();
      reader = serializeAndDeserialize(reader);
      reader.beginSlice();
      if (i == 7) {
        thrown.expect(NoSuchElementException.class);
      }
      seen.add(reader.next());
    }
    assertThat(seen).containsExactly(
        domainHistoryEntryA,
        domainHistoryEntryB,
        contactHistoryEntry,
        oneTimeA,
        recurringA,
        oneTimeB,
        recurringB);
  }

  @Test
  public void testSuccess_childEntityInput_polymorphicBaseType() throws Exception {
    createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(EppResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(BillingEvent.class));
  }

  @Test
  public void testSuccess_childEntityReader_multipleChildTypes() throws Exception {
    setupResources();
    InputReader<ImmutableObject> reader = EppResourceInputs.createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(EppResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(
            HistoryEntry.class, BillingEvent.OneTime.class, BillingEvent.Recurring.class))
        .createReaders().get(0);
    assertThat(getAllFromReader(reader)).containsExactly(
        domainHistoryEntryA, contactHistoryEntry, oneTimeA, recurringA);
  }

  private static Set<ImmutableObject> getAllFromReader(InputReader<ImmutableObject> reader)
      throws Exception {
    reader.beginShard();
    reader.beginSlice();
    ImmutableSet.Builder<ImmutableObject> seen = new ImmutableSet.Builder<>();
    try {
      while (true) {
        seen.add(reader.next());
      }
    } catch (NoSuchElementException e) {
      // Swallow; this is expected.
    }
    return seen.build();
  }

  @Test
  public void testSuccess_childEntityReader_filterParentTypes() throws Exception {
    setupResources();
    InputReader<ImmutableObject> reader = EppResourceInputs.createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(ContactResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(
            HistoryEntry.class, BillingEvent.OneTime.class, BillingEvent.Recurring.class))
        .createReaders().get(0);
    assertThat(getAllFromReader(reader)).containsExactly(contactHistoryEntry);
  }

  @Test
  public void testSuccess_childEntityReader_polymorphicChildFiltering() throws Exception {
    setupResources();
    InputReader<ImmutableObject> reader = EppResourceInputs.createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(EppResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(BillingEvent.OneTime.class))
            .createReaders().get(0);
    assertThat(getAllFromReader(reader)).containsExactly(oneTimeA);
  }

  @Test
  public void testSuccess_childEntityReader_polymorphicChildClass() throws Exception {
    setupResources();
    InputReader<ImmutableObject> reader = EppResourceInputs.createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(EppResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(BillingEvent.class))
        .createReaders().get(0);
    assertThat(getAllFromReader(reader)).containsExactly(oneTimeA, recurringA);
  }

  @Test
  public void testSuccess_childEntityReader_noneReturned() throws Exception {
    createTld("tld");
    InputReader<ImmutableObject> reader = EppResourceInputs.createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(ContactResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(
            BillingEvent.OneTime.class)).createReaders().get(0);
    assertThat(getAllFromReader(reader)).isEmpty();
  }

  @Test
  public void testSuccess_childEntityReader_readerCountMatchesBucketCount() throws Exception {
    assertThat(EppResourceInputs.createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(DomainResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(
            BillingEvent.OneTime.class)).createReaders()).hasSize(3);
  }

  @Test
  public void testSuccess_childEntityReader_oneReaderPerBucket() throws Exception {
    createTld("tld");
    Set<ImmutableObject> historyEntries = new HashSet<>();
    for (int i = 1; i <= 3; i++) {
      DomainResource domain = persistSimpleResource(newDomainResource(i + ".tld"));
      historyEntries.add(persistResource(
          new HistoryEntry.Builder()
              .setParent(domain)
              .setModificationTime(now)
              .setClientId(i + ".tld")
              .build()));
      persistResource(EppResourceIndex.create(getBucketKey(i), Key.create(domain)));
    }
    Set<ImmutableObject> seen = new HashSet<>();
    for (InputReader<ImmutableObject> reader : EppResourceInputs.createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(DomainResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(HistoryEntry.class)).createReaders()) {
      reader.beginShard();
      reader.beginSlice();
      seen.add(reader.next());
      try {
        ImmutableObject o = reader.next();
        assert_().fail("Unexpected element: " + o);
      } catch (NoSuchElementException expected) {
      }
    }
    assertThat(seen).containsExactlyElementsIn(historyEntries);
  }


  @Test
  public void testSuccess_childEntityReader_survivesAcrossSerialization() throws Exception {
    setupResources();
    Set<ImmutableObject> seen = new HashSet<>();
    InputReader<ImmutableObject> reader = EppResourceInputs.createChildEntityInput(
        ImmutableSet.<Class<? extends EppResource>>of(EppResource.class),
        ImmutableSet.<Class<? extends ImmutableObject>>of(
            HistoryEntry.class, BillingEvent.OneTime.class, BillingEvent.Recurring.class))
        .createReaders().get(0);
    reader.beginShard();
    reader.beginSlice();
    seen.add(reader.next());
    seen.add(reader.next());
    reader.endSlice();
    reader = serializeAndDeserialize(reader);
    reader.beginSlice();
    seen.add(reader.next());
    seen.add(reader.next());
    assertThat(seen).containsExactly(
        domainHistoryEntryA, contactHistoryEntry, oneTimeA, recurringA);
    thrown.expect(NoSuchElementException.class);
    reader.next();
  }
}

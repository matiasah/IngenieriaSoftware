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

package google.registry.flows.custom;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import google.registry.flows.EppException;
import google.registry.flows.FlowMetadata;
import google.registry.flows.SessionMetadata;
import google.registry.flows.domain.DomainInfoFlow;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.DomainInfoData;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;

/**
 * A no-op base class for {@link DomainInfoFlow} custom logic.
 *
 * <p>Extend this class and override the hook(s) to perform custom logic.
 */
public class DomainInfoFlowCustomLogic extends BaseFlowCustomLogic {

  protected DomainInfoFlowCustomLogic(
      EppInput eppInput, SessionMetadata sessionMetadata, FlowMetadata flowMetadata) {
    super(eppInput, sessionMetadata, flowMetadata);
  }

  /** A hook that runs before any validation. This is useful to e.g. add allowable extensions. */
  @SuppressWarnings("unused")
  public void beforeValidation() throws EppException {
    // Do nothing.
  }

  /** A hook that runs at the end of the validation step to perform additional validation. */
  @SuppressWarnings("unused")
  public void afterValidation(AfterValidationParameters parameters) throws EppException {
    // Do nothing.
  }

  /**
   * A hook that runs before the response is returned.
   *
   * <p>This takes the {@link DomainResource} and {@link ResponseExtension}s as input and returns
   * them, potentially with modifications.
   */
  @SuppressWarnings("unused")
  public BeforeResponseReturnData beforeResponse(BeforeResponseParameters parameters)
      throws EppException {
    return BeforeResponseReturnData.newBuilder()
        .setResData(parameters.resData())
        .setResponseExtensions(parameters.responseExtensions())
        .build();
  }

  /** A class to encapsulate parameters for a call to {@link #afterValidation}. */
  @AutoValue
  public abstract static class AfterValidationParameters extends ImmutableObject {

    public abstract DomainResource domain();

    public static Builder newBuilder() {
      return new AutoValue_DomainInfoFlowCustomLogic_AfterValidationParameters.Builder();
    }

    /** Builder for {@link AfterValidationParameters}. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setDomain(DomainResource domain);

      public abstract AfterValidationParameters build();
    }
  }

  /** A class to encapsulate parameters for a call to {@link #beforeResponse}. */
  @AutoValue
  public abstract static class BeforeResponseParameters extends ImmutableObject {

    public abstract DomainResource domain();

    public abstract DomainInfoData resData();

    public abstract ImmutableList<? extends ResponseExtension> responseExtensions();

    public static Builder newBuilder() {
      return new AutoValue_DomainInfoFlowCustomLogic_BeforeResponseParameters.Builder();
    }

    /** Builder for {@link BeforeResponseParameters}. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setDomain(DomainResource domain);

      public abstract Builder setResData(DomainInfoData resData);

      public abstract Builder setResponseExtensions(
          ImmutableList<? extends ResponseExtension> responseExtensions);

      public abstract BeforeResponseParameters build();
    }
  }

  /**
   * A class to encapsulate parameters for the return values from a call to {@link #beforeResponse}.
   */
  @AutoValue
  public abstract static class BeforeResponseReturnData extends ImmutableObject {

    public abstract DomainInfoData resData();

    public abstract ImmutableList<? extends ResponseExtension> responseExtensions();

    public static Builder newBuilder() {
      return new AutoValue_DomainInfoFlowCustomLogic_BeforeResponseReturnData.Builder();
    }

    /** Builder for {@link BeforeResponseReturnData}. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setResData(DomainInfoData resData);

      public abstract Builder setResponseExtensions(
          ImmutableList<? extends ResponseExtension> responseExtensions);

      public abstract BeforeResponseReturnData build();
    }
  }
}

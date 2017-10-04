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

package google.registry.flows.async;

import static google.registry.flows.async.AsyncFlowEnqueuer.QUEUE_ASYNC_DELETE;
import static google.registry.flows.async.AsyncFlowEnqueuer.QUEUE_ASYNC_HOST_RENAME;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import dagger.Module;
import dagger.Provides;
import javax.inject.Named;

/** Dagger module for the async flows package. */
@Module
public final class AsyncFlowsModule {

  @Provides
  @Named(QUEUE_ASYNC_DELETE)
  static Queue provideAsyncDeletePullQueue() {
    return QueueFactory.getQueue(QUEUE_ASYNC_DELETE);
  }

  @Provides
  @Named(QUEUE_ASYNC_HOST_RENAME)
  static Queue provideAsyncHostRenamePullQueue() {
    return QueueFactory.getQueue(QUEUE_ASYNC_HOST_RENAME);
  }
}

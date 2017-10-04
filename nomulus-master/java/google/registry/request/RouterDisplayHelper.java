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

package google.registry.request;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Utility class to help in dumping routing maps.
 *
 * <p>Each of the App Engine services (frontend, backend, and tools) has a Dagger component used for
 * routing requests (e.g. FrontendRequestComponent). This class produces a text file representation
 * of the routing configuration, showing what paths map to what action classes, as well as the
 * properties of the action classes' annotations (which cover things like allowable HTTP methods,
 * authentication settings, etc.). The text file can be useful for documentation, and is also used
 * in unit tests to check against golden routing maps to find possibly unexpected changes.
 *
 * <p>The file has fixed-width columns with a header row. The width of the columns is determined by
 * the content to be displayed. The columns are:
 *
 * <ol>
 * <li>the URL path which maps to this action (with a "(*)" after it if the prefix flag is set)
 * <li>the simple name of the action class
 * <li>the allowable HTTP methods
 * <li>whether to automatically print "ok" in the response
 * <li>the allowable authentication methods
 * <li>the minimum authentication level
 * <li>the user policy
 * </ol>
 *
 * <p>See the Auth class for more information about authentication settings.
 */
public class RouterDisplayHelper {

  private static final String PATH = "path";
  private static final String CLASS = "class";
  private static final String METHODS = "methods";
  private static final String AUTH_METHODS = "authMethods";
  private static final String MINIMUM_LEVEL = "minLevel";

  private static final String FORMAT =
      "%%-%ds %%-%ds %%-%ds %%-2s %%-%ds %%-%ds %%s";

  /** Returns a string representation of the routing map in the specified component. */
  public static String extractHumanReadableRoutesFromComponent(Class<?> componentClass) {
    return formatRoutes(Router.extractRoutesFromComponent(componentClass).values());
  }

  private static String getFormatString(Map<String, Integer> columnWidths) {
    return String.format(
        FORMAT,
        columnWidths.get(PATH),
        columnWidths.get(CLASS),
        columnWidths.get(METHODS),
        columnWidths.get(AUTH_METHODS),
        columnWidths.get(MINIMUM_LEVEL));
  }

  private static String headerToString(String formatString) {
    return String.format(
        formatString,
        "PATH",
        "CLASS",
        "METHODS",
        "OK",
        "AUTH_METHODS",
        "MIN",
        "USER_POLICY");
  }

  private static String routeToString(Route route, String formatString) {
    return String.format(
        formatString,
        route.action().isPrefix() ? (route.action().path() + "(*)") : route.action().path(),
        route.actionClass().getSimpleName(),
        Joiner.on(",").join(route.action().method()),
        route.action().automaticallyPrintOk() ? "y" : "n",
        Joiner.on(",").join(route.action().auth().authSettings().methods()),
        route.action().auth().authSettings().minimumLevel(),
        route.action().auth().authSettings().userPolicy());
  }

  private static String formatRoutes(Iterable<Route> routes) {

    // Use the column header length as a minimum.
    int pathWidth = 4;
    int classWidth = 5;
    int methodsWidth = 7;
    int authMethodsWidth = 12;
    int minLevelWidth = 3;
    for (Route route : routes) {
      int len =
          route.action().isPrefix()
              ? (route.action().path().length() + 3)
              : route.action().path().length();
      if (len > pathWidth) {
        pathWidth = len;
      }
      len = route.actionClass().getSimpleName().length();
      if (len > classWidth) {
        classWidth = len;
      }
      len = Joiner.on(",").join(route.action().method()).length();
      if (len > methodsWidth) {
        methodsWidth = len;
      }
      len = Joiner.on(",").join(route.action().auth().authSettings().methods()).length();
      if (len > authMethodsWidth) {
        authMethodsWidth = len;
      }
      len = route.action().auth().authSettings().minimumLevel().toString().length();
      if (len > minLevelWidth) {
        minLevelWidth = len;
      }
    }
    final String formatString =
        getFormatString(
            new ImmutableMap.Builder<String, Integer>()
                .put(PATH, pathWidth)
                .put(CLASS, classWidth)
                .put(METHODS, methodsWidth)
                .put(AUTH_METHODS, authMethodsWidth)
                .put(MINIMUM_LEVEL, minLevelWidth)
                .build());
    return headerToString(formatString)
        + String.format("%n")
        + FluentIterable.from(routes)
            .transform(
                new Function<Route, String>() {
                  @Override
                  public String apply(Route route) {
                    return routeToString(route, formatString);
                  }
                })
            .join(Joiner.on(String.format("%n")));
  }
}

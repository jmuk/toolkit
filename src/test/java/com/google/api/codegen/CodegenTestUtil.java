/* Copyright 2016 Google Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen;

import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class CodegenTestUtil {

  public static ConfigProto readConfig(
      DiagCollector diagCollector, TestDataLocator testDataLocator, String[] gapicConfigFileNames) {
    List<String> inputNames = new ArrayList<>();
    List<String> inputs = new ArrayList<>();

    for (String gapicConfigFileName : gapicConfigFileNames) {
      URL gapicConfigUrl = testDataLocator.findTestData(gapicConfigFileName);
      String configData = testDataLocator.readTestData(gapicConfigUrl);
      inputNames.add(gapicConfigFileName);
      inputs.add(configData);
    }

    ImmutableMap<String, Message> supportedConfigTypes =
        ImmutableMap.<String, Message>of(
            ConfigProto.getDescriptor().getFullName(), ConfigProto.getDefaultInstance());
    ConfigProto configProto =
        (ConfigProto) MultiYamlReader.read(diagCollector, inputNames, inputs, supportedConfigTypes);
    if (diagCollector.getErrorCount() > 0) {
      System.err.println(diagCollector.toString());
      return null;
    }

    return configProto;
  }
}

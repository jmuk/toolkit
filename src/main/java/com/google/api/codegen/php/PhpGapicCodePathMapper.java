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
package com.google.api.codegen.php;

import com.google.api.codegen.ApiConfig;
import com.google.api.codegen.gapic.GapicCodePathMapper;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import java.util.ArrayList;

import javax.annotation.Nullable;

/**
 * An implementation of GapicCodePathMapper that generates the output path from a prefix, and/or
 * package name.
 */
@AutoValue
public abstract class PhpGapicCodePathMapper implements GapicCodePathMapper {

  private static String PACKAGE_SPLIT_REGEX = "[\\\\]";
  private static String[] PACKAGE_PREFIX_TO_SKIP = {"Google", "Cloud"};

  @Nullable
  public abstract String getPrefix();

  @Nullable
  public abstract String getSuffix();

  @Override
  public String getOutputPath(ProtoElement element, ApiConfig config) {
    ArrayList<String> dirs = new ArrayList<>();
    String prefix = getPrefix();
    if (!Strings.isNullOrEmpty(prefix)) {
      dirs.add(prefix);
    }

    if (!Strings.isNullOrEmpty(config.getPackageName())) {
      String[] packageSplit = config.getPackageName().split(PACKAGE_SPLIT_REGEX);
      int packageStartIndex = 0;
      // Skip common package prefix only when it is an exact match in sequence.
      for (int i = 0; i < PACKAGE_PREFIX_TO_SKIP.length && i < packageSplit.length; i++) {
        if (packageSplit[i].equals(PACKAGE_PREFIX_TO_SKIP[i])) {
          packageStartIndex++;
        } else {
          break;
        }
      }
      for (int i = packageStartIndex; i < packageSplit.length; i++) {
        dirs.add(packageSplit[i]);
      }
    }

    String suffix = getSuffix();
    if (!Strings.isNullOrEmpty(suffix)) {
      dirs.add(suffix);
    }
    return Joiner.on("/").join(dirs);
  }

  public static Builder newBuilder() {
    return new AutoValue_PhpGapicCodePathMapper.Builder();
  }

  public static PhpGapicCodePathMapper defaultInstance() {
    return newBuilder().build();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setPrefix(String prefix);

    public abstract Builder setSuffix(String suffix);

    public abstract PhpGapicCodePathMapper build();
  }
}

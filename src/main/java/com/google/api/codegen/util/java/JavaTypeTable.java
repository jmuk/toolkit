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
package com.google.api.codegen.util.java;

import com.google.api.codegen.LanguageUtil;
import com.google.api.codegen.util.NamePath;
import com.google.api.codegen.util.TypeAlias;
import com.google.api.codegen.util.TypeName;
import com.google.api.codegen.util.TypeTable;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.TreeMap;

/**
 * The TypeTable for Java.
 */
public class JavaTypeTable implements TypeTable {
  /**
   * A bi-map from full names to short names indicating the import map.
   */
  private final BiMap<String, String> imports = HashBiMap.create();

  /**
   * A map from simple type name to a boolean, indicating whether its in java.lang or not. If a
   * simple type name is not in the map, this information is unknown.
   */
  private final Map<String, Boolean> implicitImports = Maps.newHashMap();

  private static final String JAVA_LANG_TYPE_PREFIX = "java.lang.";

  /**
   * A map from unboxed Java primitive type name to boxed counterpart.
   */
  private static final ImmutableMap<String, String> BOXED_TYPE_MAP =
      ImmutableMap.<String, String>builder()
          .put("boolean", "Boolean")
          .put("int", "Integer")
          .put("long", "Long")
          .put("float", "Float")
          .put("double", "Double")
          .build();

  private final String implicitPackageName;

  public JavaTypeTable(String implicitPackageName) {
    this.implicitPackageName = implicitPackageName;
  }

  @Override
  public TypeTable cloneEmpty() {
    return new JavaTypeTable(implicitPackageName);
  }

  @Override
  public TypeName getTypeName(String fullName) {
    int lastDotIndex = fullName.lastIndexOf('.');
    if (lastDotIndex < 0) {
      return new TypeName(fullName, fullName);
    }
    String shortTypeName = fullName.substring(lastDotIndex + 1);
    return new TypeName(fullName, shortTypeName);
  }

  @Override
  public NamePath getNamePath(String fullName) {
    return NamePath.dotted(fullName);
  }

  @Override
  public TypeName getContainerTypeName(String containerFullName, String elementFullName) {
    TypeName containerTypeName = getTypeName(containerFullName);
    TypeName elementTypeName = getTypeName(elementFullName);
    return new TypeName(
        containerTypeName.getFullName(),
        containerTypeName.getNickname(),
        "%s<%i>",
        elementTypeName);
  }

  @Override
  public String getAndSaveNicknameFor(String fullName) {
    return getAndSaveNicknameFor(getTypeName(fullName));
  }

  @Override
  public String getAndSaveNicknameFor(TypeName typeName) {
    return typeName.getAndSaveNicknameIn(this);
  }

  @Override
  public String getAndSaveNicknameFor(TypeAlias alias) {
    if (!alias.needsImport()) {
      return alias.getNickname();
    }
    // Derive a short name if possible
    if (imports.containsKey(alias.getFullName())) {
      // Short name already there.
      return imports.get(alias.getFullName());
    }
    if (imports.containsValue(alias.getNickname())
        || !alias.getFullName().startsWith(JAVA_LANG_TYPE_PREFIX)
            && isImplicitImport(alias.getNickname())) {
      // Short name clashes, use long name.
      return alias.getFullName();
    }
    imports.put(alias.getFullName(), alias.getNickname());
    return alias.getNickname();
  }

  /**
   * Returns the Java representation of a basic type in boxed form.
   */
  public static String getBoxedTypeName(String primitiveTypeName) {
    return LanguageUtil.getRename(primitiveTypeName, BOXED_TYPE_MAP);
  }

  @Override
  public Map<String, String> getImports() {
    // Clean up the imports.
    Map<String, String> cleanedImports = new TreeMap<>();
    // Imported type is in java.lang or in package, can be ignored.
    for (String imported : imports.keySet()) {
      if (imported.startsWith(JAVA_LANG_TYPE_PREFIX)) {
        continue;
      } else if (!implicitPackageName.isEmpty() && imported.startsWith(implicitPackageName)) {
        // Imported type is in a subpackage must not be ignored.
        if (!imported.substring(implicitPackageName.length() + 1).contains(".")) {
          continue;
        }
      }
      cleanedImports.put(imported, imports.get(imported));
    }
    return cleanedImports;
  }

  /**
   * Checks whether the simple type name is implicitly imported from java.lang.
   */
  private boolean isImplicitImport(String name) {
    Boolean yes = implicitImports.get(name);
    if (yes != null) {
      return yes;
    }
    // Use reflection to determine whether the name exists in java.lang.
    try {
      Class.forName("java.lang." + name);
      yes = true;
    } catch (Exception e) {
      yes = false;
    }
    implicitImports.put(name, yes);
    return yes;
  }
}

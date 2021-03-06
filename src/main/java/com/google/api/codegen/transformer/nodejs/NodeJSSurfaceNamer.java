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
package com.google.api.codegen.transformer.nodejs;

import com.google.api.codegen.CollectionConfig;
import com.google.api.codegen.MethodConfig;
import com.google.api.codegen.ServiceMessages;
import com.google.api.codegen.transformer.ModelTypeFormatterImpl;
import com.google.api.codegen.transformer.SurfaceNamer;
import com.google.api.codegen.util.Name;
import com.google.api.codegen.util.nodejs.NodeJSNameFormatter;
import com.google.api.codegen.util.nodejs.NodeJSTypeTable;
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.TypeRef;
import com.google.common.base.Splitter;

import java.util.List;

/**
 * The SurfaceNamer for NodeJS.
 */
public class NodeJSSurfaceNamer extends SurfaceNamer {
  public NodeJSSurfaceNamer(String implicitPackageName) {
    super(
        new NodeJSNameFormatter(),
        new ModelTypeFormatterImpl(new NodeJSModelTypeNameConverter(implicitPackageName)),
        new NodeJSTypeTable(implicitPackageName));
  }

  /**
   * NodeJS uses a special format for ApiWrapperModuleName.
   *
   * <p>The name for the module for this vkit module. This assumes that the service's full name will
   * be in the format of 'google.some.apiname.version.ServiceName', and extracts the 'apiname' and
   * 'version' part and combine them to lower-camelcased style (like pubsubV1).
   *
   * <p>Based on {@link com.google.api.codegen.nodejs.NodeJSGapicContext#getModuleName}.
   */
  public String getApiWrapperModuleName(Interface interfaze) {
    List<String> names = Splitter.on(".").splitToList(interfaze.getFullName());
    if (names.size() < 3) {
      throw new IllegalArgumentException(interfaze.getFullName());
    }

    return names.get(names.size() - 3) + Name.from(names.get(names.size() - 2)).toUpperCamel();
  }

  @Override
  public String getFieldSetFunctionName(TypeRef type, Name identifier) {
    if (type.isMap() || type.isRepeated()) {
      return methodName(Name.from("add").join(identifier));
    } else {
      return methodName(Name.from("set").join(identifier));
    }
  }

  @Override
  public String getPathTemplateName(CollectionConfig collectionConfig) {
    return inittedConstantName(Name.from(collectionConfig.getEntityName(), "name", "template"));
  }

  @Override
  public String getFormatFunctionName(CollectionConfig collectionConfig) {
    return staticFunctionName(Name.from(collectionConfig.getEntityName(), "path"));
  }

  @Override
  public String getClientConfigPath(Interface service) {
    return "./resources/"
        + Name.upperCamel(service.getSimpleName()).join("client_config").toLowerUnderscore()
        + ".json";
  }

  @Override
  public boolean shouldImportRequestObjectParamType(Field field) {
    return field.getType().isMap();
  }

  @Override
  public String getOptionalArrayTypeName() {
    return "gax.CallOptions";
  }

  @Override
  public String getDynamicLangReturnTypeName(Method method, MethodConfig methodConfig) {
    if (new ServiceMessages().isEmptyType(method.getOutputType())) {
      return "";
    }

    return getModelTypeFormatter().getFullNameFor(method.getOutputType());
  }
}

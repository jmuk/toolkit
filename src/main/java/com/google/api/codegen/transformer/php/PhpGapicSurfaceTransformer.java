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
package com.google.api.codegen.transformer.php;

import com.google.api.codegen.ApiConfig;
import com.google.api.codegen.InterfaceView;
import com.google.api.codegen.ServiceConfig;
import com.google.api.codegen.gapic.GapicCodePathMapper;
import com.google.api.codegen.transformer.ApiMethodTransformer;
import com.google.api.codegen.transformer.ImportTypeTransformer;
import com.google.api.codegen.transformer.ModelToViewTransformer;
import com.google.api.codegen.transformer.ModelTypeTable;
import com.google.api.codegen.transformer.PageStreamingTransformer;
import com.google.api.codegen.transformer.PathTemplateTransformer;
import com.google.api.codegen.transformer.ServiceTransformer;
import com.google.api.codegen.transformer.SurfaceNamer;
import com.google.api.codegen.transformer.SurfaceTransformerContext;
import com.google.api.codegen.util.php.PhpTypeTable;
import com.google.api.codegen.viewmodel.ApiMethodView;
import com.google.api.codegen.viewmodel.DynamicLangXApiView;
import com.google.api.codegen.viewmodel.GrpcStubView;
import com.google.api.codegen.viewmodel.ViewModel;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ModelToViewTransformer to transform a Model into the standard GAPIC surface in PHP.
 */
public class PhpGapicSurfaceTransformer implements ModelToViewTransformer {
  private GapicCodePathMapper pathMapper;
  private ServiceTransformer serviceTransformer;
  private PathTemplateTransformer pathTemplateTransformer;
  private PageStreamingTransformer pageStreamingTransformer;
  private ApiMethodTransformer apiMethodTransformer;

  private static final String XAPI_TEMPLATE_FILENAME = "php/main.snip";

  public PhpGapicSurfaceTransformer(ApiConfig apiConfig, GapicCodePathMapper pathMapper) {
    this.pathMapper = pathMapper;
    this.serviceTransformer = new ServiceTransformer();
    this.pathTemplateTransformer = new PathTemplateTransformer();
    this.pageStreamingTransformer = new PageStreamingTransformer();
    this.apiMethodTransformer = new ApiMethodTransformer();
  }

  @Override
  public List<String> getTemplateFileNames() {
    return Arrays.asList(XAPI_TEMPLATE_FILENAME);
  }

  @Override
  public List<ViewModel> transform(Model model, ApiConfig apiConfig) {
    List<ViewModel> surfaceDocs = new ArrayList<>();
    for (Interface service : new InterfaceView().getElementIterable(model)) {
      ModelTypeTable modelTypeTable =
          new ModelTypeTable(
              new PhpTypeTable(apiConfig.getPackageName()),
              new PhpModelTypeNameConverter(apiConfig.getPackageName()));
      SurfaceTransformerContext context =
          SurfaceTransformerContext.create(
              service, apiConfig, modelTypeTable, new PhpSurfaceNamer(apiConfig.getPackageName()));

      surfaceDocs.addAll(transform(context));
    }
    return surfaceDocs;
  }

  public List<ViewModel> transform(SurfaceTransformerContext context) {
    String outputPath = pathMapper.getOutputPath(context.getInterface(), context.getApiConfig());
    SurfaceNamer namer = context.getNamer();

    List<ViewModel> surfaceData = new ArrayList<>();

    addXApiImports(context);

    List<ApiMethodView> methods = generateApiMethods(context);

    ImportTypeTransformer importTypeTransformer = new ImportTypeTransformer();

    DynamicLangXApiView.Builder xapiClass = DynamicLangXApiView.newBuilder();

    xapiClass.doc(serviceTransformer.generateServiceDoc(context, methods.get(0)));

    xapiClass.templateFileName(XAPI_TEMPLATE_FILENAME);
    xapiClass.protoFilename(context.getInterface().getFile().getSimpleName());
    xapiClass.packageName(context.getApiConfig().getPackageName());
    String name = namer.getApiWrapperClassName(context.getInterface());
    xapiClass.name(name);
    ServiceConfig serviceConfig = new ServiceConfig();
    xapiClass.serviceAddress(serviceConfig.getServiceAddress(context.getInterface()));
    xapiClass.servicePort(serviceConfig.getServicePort());
    xapiClass.serviceTitle(serviceConfig.getTitle(context.getInterface()));
    xapiClass.authScopes(serviceConfig.getAuthScopes(context.getInterface()));

    xapiClass.pathTemplates(pathTemplateTransformer.generatePathTemplates(context));
    xapiClass.formatResourceFunctions(
        pathTemplateTransformer.generateFormatResourceFunctions(context));
    xapiClass.parseResourceFunctions(
        pathTemplateTransformer.generateParseResourceFunctions(context));
    xapiClass.pathTemplateGetterFunctions(
        pathTemplateTransformer.generatePathTemplateGetterFunctions(context));
    xapiClass.pageStreamingDescriptors(pageStreamingTransformer.generateDescriptors(context));

    xapiClass.methodKeys(generateMethodKeys(context));
    xapiClass.clientConfigPath(namer.getClientConfigPath(context.getInterface()));
    xapiClass.interfaceKey(context.getInterface().getFullName());
    String grpcClientTypeName = namer.getGrpcClientTypeName(context.getInterface());
    xapiClass.grpcClientTypeName(context.getTypeTable().getAndSaveNicknameFor(grpcClientTypeName));

    xapiClass.apiMethods(methods);

    xapiClass.stubs(generateGrpcStubs(context));

    // must be done as the last step to catch all imports
    xapiClass.imports(importTypeTransformer.generateImports(context.getTypeTable().getImports()));

    xapiClass.outputPath(outputPath + "/" + name + ".php");

    surfaceData.add(xapiClass.build());

    return surfaceData;
  }

  private void addXApiImports(SurfaceTransformerContext context) {
    ModelTypeTable typeTable = context.getTypeTable();
    typeTable.saveNicknameFor("Google\\GAX\\AgentHeaderDescriptor");
    typeTable.saveNicknameFor("Google\\GAX\\ApiCallable");
    typeTable.saveNicknameFor("Google\\GAX\\CallSettings");
    typeTable.saveNicknameFor("Google\\GAX\\GrpcConstants");
    typeTable.saveNicknameFor("Google\\GAX\\GrpcCredentialsHelper");
    typeTable.saveNicknameFor("Google\\GAX\\PathTemplate");
  }

  private List<String> generateMethodKeys(SurfaceTransformerContext context) {
    List<String> methodKeys = new ArrayList<>(context.getInterface().getMethods().size());

    for (Method method : context.getNonStreamingMethods()) {
      methodKeys.add(context.getNamer().getMethodKey(method));
    }

    return methodKeys;
  }

  private List<ApiMethodView> generateApiMethods(SurfaceTransformerContext context) {
    List<ApiMethodView> apiMethods = new ArrayList<>(context.getInterface().getMethods().size());

    for (Method method : context.getNonStreamingMethods()) {
      apiMethods.add(
          apiMethodTransformer.generateOptionalArrayMethod(context.asMethodContext(method)));
    }

    return apiMethods;
  }

  private List<GrpcStubView> generateGrpcStubs(SurfaceTransformerContext context) {
    List<GrpcStubView> stubs = new ArrayList<>();
    SurfaceNamer namer = context.getNamer();

    Map<String, Interface> interfaces = new HashMap<>();
    for (Method method : context.getNonStreamingMethods()) {
      Interface targetInterface = context.asMethodContext(method).getTargetInterface();
      interfaces.put(targetInterface.getFullName(), targetInterface);
    }

    List<String> interfaceNames = new ArrayList<>();
    interfaceNames.addAll(interfaces.keySet());
    Collections.sort(interfaceNames);

    for (String interfaceName : interfaceNames) {
      Interface interfaze = interfaces.get(interfaceName);
      GrpcStubView.Builder stub = GrpcStubView.newBuilder();

      stub.name(namer.getStubName(interfaze));
      stub.createStubFunctionName(namer.getCreateStubFunctionName(interfaze));
      String grpcClientTypeName = namer.getGrpcClientTypeName(interfaze);
      stub.grpcClientTypeName(context.getTypeTable().getAndSaveNicknameFor(grpcClientTypeName));

      stubs.add(stub.build());
    }

    return stubs;
  }
}

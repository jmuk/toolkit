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
package com.google.api.codegen.nodejs;

import com.google.api.codegen.ApiConfig;
import com.google.api.codegen.GapicContext;
import com.google.api.codegen.MethodConfig;
import com.google.api.codegen.transformer.ApiMethodTransformer;
import com.google.api.codegen.transformer.MethodTransformerContext;
import com.google.api.codegen.transformer.ModelTypeTable;
import com.google.api.codegen.transformer.SurfaceTransformerContext;
import com.google.api.codegen.transformer.nodejs.NodeJSModelTypeNameConverter;
import com.google.api.codegen.transformer.nodejs.NodeJSSurfaceNamer;
import com.google.api.codegen.util.nodejs.NodeJSTypeTable;
import com.google.api.codegen.viewmodel.ApiMethodView;
import com.google.api.tools.framework.aspects.documentation.model.DocumentationUtil;
import com.google.api.tools.framework.aspects.documentation.model.ElementDocumentationAttribute;
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.MessageType;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoContainerElement;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.ProtoFile;
import com.google.api.tools.framework.model.TypeRef;
import com.google.api.tools.framework.model.TypeRef.Cardinality;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;

import java.util.List;

import javax.annotation.Nullable;

/**
 * A GapicContext specialized for NodeJS.
 */
public class NodeJSGapicContext extends GapicContext implements NodeJSContext {

  public NodeJSGapicContext(Model model, ApiConfig apiConfig) {
    super(model, apiConfig);
  }

  // Snippet Helpers
  // ===============

  /**
   * Return ApiMethodView for sample gen.
   *
   * NOTE: Temporary solution to use MVVM with just sample gen. This class
   *       will eventually go away when code gen also converts to MVVM.
   */
  public ApiMethodView getApiMethodView(Interface service, Method method) {
    ModelTypeTable modelTypeTable =
        new ModelTypeTable(
            new NodeJSTypeTable(getApiConfig().getPackageName()),
            new NodeJSModelTypeNameConverter(getApiConfig().getPackageName()));
    SurfaceTransformerContext context =
        SurfaceTransformerContext.create(
            service,
            getApiConfig(),
            modelTypeTable,
            new NodeJSSurfaceNamer(getApiConfig().getPackageName()));
    MethodTransformerContext methodContext = context.asMethodContext(method);
    ApiMethodTransformer apiMethodTransformer = new ApiMethodTransformer();

    return apiMethodTransformer.generateOptionalArrayMethod(methodContext);
  }

  public String filePath(ProtoFile file) {
    return file.getSimpleName().replace(".proto", "_pb2.js");
  }

  /**
   * Return comments lines for a given proto element, extracted directly from the proto doc
   */
  public List<String> defaultComments(ProtoElement element) {
    if (!element.hasAttribute(ElementDocumentationAttribute.KEY)) {
      return ImmutableList.<String>of();
    }
    return convertToCommentedBlock(
        JSDocCommentFixer.jsdocify(DocumentationUtil.getScopedDescription(element)));
  }

  /**
   * The package name of the grpc module for the API.
   */
  public String grpcClientName(Interface service) {
    return "grpc-" + service.getFile().getFullName().replace('.', '-');
  }

  public boolean isGcloud() {
    return NodeJSUtils.isGcloud(getApiConfig());
  }

  /**
   * The namespace (full package name) for the service.
   */
  public String getNamespace(Interface service) {
    String fullName = service.getFullName();
    int slash = fullName.lastIndexOf('.');
    return fullName.substring(0, slash);
  }

  /**
   * The name for the module for this vkit module. This assumes that the service's
   * full name will be in the format of 'google.some.apiname.version.ServiceName',
   * and extracts the 'apiname' and 'version' part and combine them to lower-camelcased
   * style (like pubsubV1).
   */
  public String getModuleName(Interface service) {
    List<String> names = Splitter.on(".").splitToList(service.getFullName());
    return names.get(names.size() - 3) + lowerUnderscoreToUpperCamel(names.get(names.size() - 2));
  }

  /**
   * Returns the major version part in the API namespace. This assumes that the service's
   * full name will be in the format of 'google.some.apiname.version.ServiceName', and
   * extracts the 'version' part.
   */
  public String getApiVersion(Interface service) {
    List<String> names = Splitter.on(".").splitToList(service.getFullName());
    return names.get(names.size() - 2);
  }

  /**
   * Returns the filename for documenting messages.
   */
  public String getDocFilename(ProtoFile file) {
    String filePath = file.getSimpleName().replace(".proto", ".js");
    if (isExternalFile(file)) {
      filePath = filePath.replaceAll("/", "_");
    } else {
      int lastSlash = filePath.lastIndexOf('/');
      if (lastSlash >= 0) {
        filePath = filePath.substring(lastSlash + 1);
      }
    }
    return "doc_" + filePath;
  }

  /**
   * Returns true if the proto file is external to the current package.
   * Currently, it only checks the file path and thinks it is external if
   * the file is well-known common protos.
   */
  public boolean isExternalFile(ProtoFile file) {
    String filePath = file.getSimpleName();
    for (String commonPath : COMMON_PROTO_PATHS) {
      if (filePath.startsWith(commonPath)) {
        return true;
      }
    }
    return false;
  }

  public String getFileURL(ProtoFile file) {
    String filePath = file.getSimpleName();
    if (filePath.startsWith("google/protobuf")) {
      return "https://github.com/google/protobuf/blob/master/src/" + filePath;
    } else {
      return "https://github.com/googleapis/googleapis/blob/master/" + filePath;
    }
  }

  /**
   * Returns type information for a field in JSDoc style.
   */
  private String fieldTypeCardinalityComment(Field field) {
    TypeRef type = field.getType();

    String cardinalityComment = "";
    if (type.getCardinality() == Cardinality.REPEATED) {
      if (type.isMap()) {
        String keyType = jsTypeName(type.getMapKeyField().getType());
        String valueType = jsTypeName(type.getMapValueField().getType());
        return String.format("Object.<%s, %s>", keyType, valueType);
      } else {
        cardinalityComment = "[]";
      }
    }
    String typeComment = jsTypeName(field.getType());
    return String.format("%s%s", typeComment, cardinalityComment);
  }

  /**
   * Returns a JSDoc comment string for the field as a parameter to a function.
   */
  private String fieldParamComment(Field field, String paramComment, boolean isOptional) {
    String commentType = fieldTypeCardinalityComment(field);
    String fieldName = wrapIfKeywordOrBuiltIn(lowerUnderscoreToLowerCamel(field.getSimpleName()));
    if (isOptional) {
      fieldName = "otherArgs." + fieldName;
      commentType = commentType + "=";
    }
    return fieldComment(
        String.format("@param {%s} %s", commentType, fieldName), paramComment, field);
  }

  /**
   * Returns a JSDoc comment string for the field as an attribute of a message.
   */
  public List<String> fieldPropertyComment(Field field) {
    String commentType = fieldTypeCardinalityComment(field);
    String fieldName = wrapIfKeywordOrBuiltIn(field.getSimpleName());
    return convertToCommentedBlock(
        fieldComment(String.format("@property {%s} %s", commentType, fieldName), null, field));
  }

  private String fieldComment(String comment, String paramComment, Field field) {
    if (paramComment == null) {
      paramComment = DocumentationUtil.getScopedDescription(field);
    }
    if (!Strings.isNullOrEmpty(paramComment)) {
      paramComment = JSDocCommentFixer.jsdocify(paramComment);
      comment += "\n  " + paramComment.replaceAll("(\\r?\\n)", "\n  ");
    }
    if (field.getType().isMessage() && !field.getType().isMap()) {
      if (!Strings.isNullOrEmpty(paramComment)) {
        comment += "\n";
      }
      comment +=
          "\n  This object should have the same structure as "
              + linkForMessage(field.getType().getMessageType());
    } else if (field.getType().isEnum()) {
      if (!Strings.isNullOrEmpty(paramComment)) {
        comment += "\n";
      }
      comment +=
          "\n  The number should be among the values of "
              + linkForMessage(field.getType().getEnumType());
    }
    return comment + "\n";
  }

  /**
   * Return JSDoc callback comment and return type comment for the given method.
   */
  @Nullable
  private String returnTypeComment(Method method, MethodConfig config) {
    if (config.isPageStreaming()) {
      TypeRef resourceType = config.getPageStreaming().getResourcesField().getType();
      String resourceTypeName;
      if (resourceType.isMessage()) {
        resourceTypeName =
            "an object representing\n  " + linkForMessage(resourceType.getMessageType());
      } else if (resourceType.isEnum()) {
        resourceTypeName = "a number of\n  " + linkForMessage(resourceType.getEnumType());
      } else {
        resourceTypeName = "a " + jsTypeName(resourceType);
      }
      return "@returns {Stream}\n"
          + "  An object stream. By default, this emits "
          + resourceTypeName
          + " on 'data' event.\n"
          + "  This object can also be configured to emit\n"
          + "  pages of the responses through the options parameter.";
    }

    MessageType returnMessageType = method.getOutputMessage();
    boolean isEmpty = returnMessageType.getFullName().equals("google.protobuf.Empty");

    String classInfo = jsTypeName(method.getOutputType());

    String callbackType =
        isEmpty ? "function(?Error)" : String.format("function(?Error, ?%s)", classInfo);
    String callbackMessage =
        "@param {"
            + callbackType
            + "=} callback\n"
            + "  The function which will be called with the result of the API call.";
    if (!isEmpty) {
      callbackMessage +=
          "\n\n  The second parameter to the callback is an object representing "
              + linkForMessage(returnMessageType);
    }

    String returnMessage =
        "@returns {"
            + (config.isBundling() ? "gax.BundleEventEmitter" : "gax.EventEmitter")
            + "} - the event emitter to handle the call\n"
            + "  status.";
    if (config.isBundling()) {
      returnMessage +=
          " When isBundling: false is specified in the options, it still returns\n"
              + "  a gax.BundleEventEmitter but the API is immediately invoked, so it behaves same\n"
              + "  as a gax.EventEmitter does.";
    }
    return callbackMessage + "\n" + returnMessage;
  }

  /**
   * Return the list of messages within element which should be documented in Node.JS.
   */
  public ImmutableList<MessageType> filterDocumentingMessages(ProtoContainerElement element) {
    ImmutableList.Builder<MessageType> builder = ImmutableList.builder();
    for (MessageType msg : element.getMessages()) {
      // Doesn't have to document map entries in Node.JS because Object is used.
      if (!msg.isMapEntry()) {
        builder.add(msg);
      }
    }
    return builder.build();
  }

  /**
   * Return comments lines for a given method, consisting of proto doc and parameter type
   * documentation.
   */
  public List<String> methodComments(Method msg) {
    MethodConfig config =
        getApiConfig().getInterfaceConfig((Interface) msg.getParent()).getMethodConfig(msg);

    // Generate parameter types
    StringBuilder paramTypesBuilder = new StringBuilder();
    for (Field field : config.getRequiredFields()) {
      paramTypesBuilder.append(fieldParamComment(field, null, false));
    }
    Iterable<Field> optionalParams = removePageTokenFromFields(config.getOptionalFields(), config);
    if (optionalParams.iterator().hasNext()) {
      paramTypesBuilder.append("@param {Object=} otherArgs\n");
      for (Field field : optionalParams) {
        if (config.isPageStreaming()
            && field.equals((config.getPageStreaming().getPageSizeField()))) {
          paramTypesBuilder.append(
              fieldParamComment(
                  field,
                  "The maximum number of resources contained in the underlying API\n"
                      + "response. If page streaming is performed per-resource, this\n"
                      + "parameter does not affect the return value. If page streaming is\n"
                      + "performed per-page, this determines the maximum number of\n"
                      + "resources in a page.",
                  true));
        } else {
          paramTypesBuilder.append(fieldParamComment(field, null, true));
        }
      }
    }
    paramTypesBuilder.append(
        "@param {gax.CallOptions=} options\n"
            + "  Overrides the default settings for this call, e.g, timeout,\n"
            + "  retries, etc.");
    String paramTypes = paramTypesBuilder.toString();

    String returnType = returnTypeComment(msg, config);

    // Generate comment contents
    StringBuilder contentBuilder = new StringBuilder();
    if (msg.hasAttribute(ElementDocumentationAttribute.KEY)) {
      contentBuilder.append(
          JSDocCommentFixer.jsdocify(DocumentationUtil.getScopedDescription(msg)));
      if (!Strings.isNullOrEmpty(paramTypes)) {
        contentBuilder.append("\n\n");
      }
    }
    contentBuilder.append(paramTypes);
    if (returnType != null) {
      contentBuilder.append("\n" + returnType);
    }
    return convertToCommentedBlock(contentBuilder.toString());
  }

  /**
   * Return a non-conflicting safe name if name is a JS reserved word.
   */
  public String wrapIfKeywordOrBuiltIn(String name) {
    if (KEYWORD_BUILT_IN_SET.contains(name)) {
      return name + "_";
    }
    return name;
  }

  /**
   * Returns the name of JS type for the given typeRef.
   */
  public String jsTypeName(TypeRef typeRef) {
    switch (typeRef.getKind()) {
      case TYPE_MESSAGE:
        return "Object";
      case TYPE_ENUM:
        return "number";
      default:
        {
          String name = PRIMITIVE_TYPE_NAMES.get(typeRef.getKind());
          if (!Strings.isNullOrEmpty(name)) {
            return name;
          }
          throw new IllegalArgumentException("unknown type kind: " + typeRef.getKind());
        }
    }
  }

  /**
   * Returns the name of the JS type name for arguejs parameter definitions.
   */
  public String getFieldType(Field field) {
    TypeRef typeRef = field.getType();
    if (typeRef.isMap()) {
      return "Object";
    }
    if (typeRef.getCardinality() == Cardinality.REPEATED) {
      return "Array";
    }
    switch (typeRef.getKind()) {
      case TYPE_MESSAGE:
        return "Object";
      case TYPE_BOOL:
        return "Boolean";
      case TYPE_STRING:
      case TYPE_BYTES:
        return "String";
      default:
        // Numeric types and enums.
        return "Number";
    }
  }

  /**
   * Returns the JSDoc format of link to the element.
   */
  public String linkForMessage(ProtoElement element) {
    if (isExternalFile(element.getFile())) {
      String fullName = element.getFullName();
      return String.format("[%s]{@link external:\"%s\"}", fullName, fullName);
    } else {
      String simpleName = element.getSimpleName();
      return String.format("[%s]{@link %s}", simpleName, simpleName);
    }
  }

  /**
   * Returns the JavaScript representation of the function to return the byte length.
   */
  public String getByteLengthFunction(TypeRef typeRef) {
    switch (typeRef.getKind()) {
      case TYPE_MESSAGE:
        return "gax.createByteLengthFunction(grpcClient."
            + typeRef.getMessageType().getFullName()
            + ")";
      case TYPE_STRING:
      case TYPE_BYTES:
        return "function(s) { return s.length; }";
      default:
        // There is no easy way to say the actual length of the numeric fields.
        // For now throwing an exception.
        throw new IllegalArgumentException(
            "Can't determine the byte length function for " + typeRef.getKind());
    }
  }

  /**
   * Convert the content string into a commented block that can be directly printed out in the
   * generated JS files.
   */
  private List<String> convertToCommentedBlock(String content) {
    if (Strings.isNullOrEmpty(content)) {
      return ImmutableList.<String>of();
    }
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String comment : Splitter.on("\n").splitToList(content)) {
      builder.add(comment);
    }
    return builder.build();
  }

  // Constants
  // =========

  /**
   * A map from primitive types to its default value.
   */
  private static final ImmutableMap<Type, String> DEFAULT_VALUE_MAP =
      ImmutableMap.<Type, String>builder()
          .put(Type.TYPE_BOOL, "false")
          .put(Type.TYPE_DOUBLE, "0.0")
          .put(Type.TYPE_FLOAT, "0.0")
          .put(Type.TYPE_INT64, "0")
          .put(Type.TYPE_UINT64, "0")
          .put(Type.TYPE_SINT64, "0")
          .put(Type.TYPE_FIXED64, "0")
          .put(Type.TYPE_SFIXED64, "0")
          .put(Type.TYPE_INT32, "0")
          .put(Type.TYPE_UINT32, "0")
          .put(Type.TYPE_SINT32, "0")
          .put(Type.TYPE_FIXED32, "0")
          .put(Type.TYPE_SFIXED32, "0")
          .put(Type.TYPE_STRING, "\'\'")
          .put(Type.TYPE_BYTES, "\'\'")
          .build();

  private static final ImmutableMap<Type, String> PRIMITIVE_TYPE_NAMES =
      ImmutableMap.<Type, String>builder()
          .put(Type.TYPE_BOOL, "boolean")
          .put(Type.TYPE_DOUBLE, "number")
          .put(Type.TYPE_FLOAT, "number")
          .put(Type.TYPE_INT64, "number")
          .put(Type.TYPE_UINT64, "number")
          .put(Type.TYPE_SINT64, "number")
          .put(Type.TYPE_FIXED64, "number")
          .put(Type.TYPE_SFIXED64, "number")
          .put(Type.TYPE_INT32, "number")
          .put(Type.TYPE_UINT32, "number")
          .put(Type.TYPE_SINT32, "number")
          .put(Type.TYPE_FIXED32, "number")
          .put(Type.TYPE_SFIXED32, "number")
          .put(Type.TYPE_STRING, "string")
          .put(Type.TYPE_BYTES, "string")
          .build();

  /**
   * A set of ECMAScript 2016 reserved words. See
   * https://tc39.github.io/ecma262/2016/#sec-reserved-words
   */
  private static final ImmutableSet<String> KEYWORD_BUILT_IN_SET =
      ImmutableSet.<String>builder()
          .add(
              "break",
              "do",
              "in",
              "typeof",
              "case",
              "else",
              "instanceof",
              "var",
              "catch",
              "export",
              "new",
              "void",
              "class",
              "extends",
              "return",
              "while",
              "const",
              "finally",
              "super",
              "with",
              "continue",
              "for",
              "switch",
              "yield",
              "debugger",
              "function",
              "this",
              "default",
              "if",
              "throw",
              "delete",
              "import",
              "try",
              "let",
              "static",
              "enum",
              "await",
              "implements",
              "package",
              "protected",
              "interface",
              "private",
              "public",
              "null",
              "true",
              "false",
              // common parameters passed to methods.
              "otherArgs",
              "options",
              "callback")
          .build();

  private static final ImmutableSet<String> COMMON_PROTO_PATHS =
      ImmutableSet.<String>builder()
          .add(
              "google/api",
              "google/bytestream",
              "google/logging/type",
              "google/longrunning",
              "google/protobuf",
              "google/rpc",
              "google/type")
          .build();
}

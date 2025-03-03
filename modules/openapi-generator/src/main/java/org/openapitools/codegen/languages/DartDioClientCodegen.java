/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.models.media.Schema;

import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.underscore;

public class DartDioClientCodegen extends DartClientCodegen {
    private static final Logger LOGGER = LoggerFactory.getLogger(DartDioClientCodegen.class);

    private static final String NULLABLE_FIELDS = "nullableFields";
    private static final String IS_FORMAT_JSON = "jsonFormat";
    private static final String CLIENT_NAME = "clientName";

    private static Set<String> modelToIgnore = new HashSet<>();

    static {
        modelToIgnore.add("datetime");
        modelToIgnore.add("map");
        modelToIgnore.add("object");
        modelToIgnore.add("list");
        modelToIgnore.add("file");
        modelToIgnore.add("uint8list");
    }

    private static final String SERIALIZATION_JSON = "json";

    private boolean nullableFields = true;

    public DartDioClientCodegen() {
        super();
        browserClient = false;
        outputFolder = "generated-code/dart-dio";
        embeddedTemplateDir = templateDir = "dart-dio";

        //no tests at this time
        modelTestTemplateFiles.clear();
        apiTestTemplateFiles.clear();

        cliOptions.add(new CliOption(NULLABLE_FIELDS, "Is the null fields should be in the JSON payload"));

        typeMapping.put("file", "Uint8List");
        typeMapping.put("binary", "Uint8List");

        importMapping.put("BuiltList", "built_collection/built_collection");
        importMapping.put("BuiltMap", "built_collection/built_collection");
    }

    @Override
    public String getName() {
        return "dart-dio";
    }

    @Override
    public String getHelp() {
        return "Generates a Dart Dio client library.";
    }

    @Override
    public String toDefaultValue(Schema p) {
        if (ModelUtils.isMapSchema(p)) {
            return "const {}";
        } else if (ModelUtils.isArraySchema(p)) {
            return "const []";
        }
        return super.toDefaultValue(p);
    }

    @Override
    public String escapeReservedWord(String name) {
        if (this.reservedWordsMappings().containsKey(name)) {
            return this.reservedWordsMappings().get(name);
        }
        return "_" + name;
    }

    @Override
    public String toEnumVarName(String name, String datatype) {
        if (name.length() == 0) {
            return "empty";
        }
        if ("number".equalsIgnoreCase(datatype) ||
            "int".equalsIgnoreCase(datatype)) {
            name = "Number" + name;
        }
        name = camelize(name, true);

        // for reserved word or word starting with number, append _
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        return name;
    }

    @Override
    public void processOpts() {
        if (StringUtils.isEmpty(System.getenv("DART_POST_PROCESS_FILE"))) {
            LOGGER.info("Environment variable DART_POST_PROCESS_FILE not defined so the Dart code may not be properly formatted. To define it, try `export DART_POST_PROCESS_FILE=\"/usr/local/bin/dartfmt -w\"` (Linux/Mac)");
            LOGGER.info("NOTE: To enable file post-processing, 'enablePostProcessFile' must be set to `true` (--enable-post-process-file for CLI).");
        }

        if (additionalProperties.containsKey(NULLABLE_FIELDS)) {
            nullableFields = convertPropertyToBooleanAndWriteBack(NULLABLE_FIELDS);
        } else {
            //not set, use to be passed to template
            additionalProperties.put(NULLABLE_FIELDS, nullableFields);
        }

        additionalProperties.put(IS_FORMAT_JSON, true);

        if (additionalProperties.containsKey(PUB_NAME)) {
            this.setPubName((String) additionalProperties.get(PUB_NAME));
        } else {
            //not set, use to be passed to template
            additionalProperties.put(PUB_NAME, pubName);
        }

        if (!additionalProperties.containsKey(CLIENT_NAME)) {
            additionalProperties.put(CLIENT_NAME, org.openapitools.codegen.utils.StringUtils.camelize(pubName));
        }

        if (additionalProperties.containsKey(PUB_VERSION)) {
            this.setPubVersion((String) additionalProperties.get(PUB_VERSION));
        } else {
            //not set, use to be passed to template
            additionalProperties.put(PUB_VERSION, pubVersion);
        }

        if (additionalProperties.containsKey(PUB_DESCRIPTION)) {
            this.setPubDescription((String) additionalProperties.get(PUB_DESCRIPTION));
        } else {
            //not set, use to be passed to template
            additionalProperties.put(PUB_DESCRIPTION, pubDescription);
        }

        if (additionalProperties.containsKey(USE_ENUM_EXTENSION)) {
            this.setUseEnumExtension(convertPropertyToBooleanAndWriteBack(USE_ENUM_EXTENSION));
        } else {
            // Not set, use to be passed to template.
            additionalProperties.put(USE_ENUM_EXTENSION, useEnumExtension);
        }

        if (additionalProperties.containsKey(CodegenConstants.SOURCE_FOLDER)) {
            this.setSourceFolder((String) additionalProperties.get(CodegenConstants.SOURCE_FOLDER));
        }

        // make api and model doc path available in mustache template
        additionalProperties.put("apiDocPath", apiDocPath);
        additionalProperties.put("modelDocPath", modelDocPath);

        final String libFolder = sourceFolder + File.separator + "lib";
        supportingFiles.add(new SupportingFile("pubspec.mustache", "", "pubspec.yaml"));
        supportingFiles.add(new SupportingFile("analysis_options.mustache", "", "analysis_options.yaml"));
        supportingFiles.add(new SupportingFile("apilib.mustache", libFolder, "api.dart"));
        supportingFiles.add(new SupportingFile("serializers.mustache", libFolder, "serializers.dart"));

        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
    }

    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        objs = super.postProcessModels(objs);
        List<Object> models = (List<Object>) objs.get("models");
        ProcessUtils.addIndexToProperties(models, 1);
        for (Object _mo : models) {
            Map<String, Object> mo = (Map<String, Object>) _mo;
            Set<String> modelImports = new HashSet<>();
            CodegenModel cm = (CodegenModel) mo.get("model");
            for (String modelImport : cm.imports) {
                if (importMapping.containsKey(modelImport)) {
                    modelImports.add(importMapping.get(modelImport));
                } else {
                    if (!modelToIgnore.contains(modelImport.toLowerCase(Locale.ROOT))) {
                        modelImports.add(pubName + "/model/" + underscore(modelImport));
                    }
                }
            }

            cm.imports = modelImports;
            cm.vendorExtensions.put("hasVars", cm.vars.size() > 0);
        }
        return objs;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        if (nullableFields) {
            property.isNullable = true;
        }

        if (property.isListContainer) {
            //Updates any List properties on a model to a BuiltList. This happens in post processing rather
            //than type mapping as we only want this to apply to models, not every other class.
            if ("List".equals(property.baseType)) {
                property.setDatatype(property.dataType.replaceAll(property.baseType, "BuiltList"));
                property.setBaseType("BuiltList");
                model.imports.add("BuiltList");
            }
        }
        if (property.isMapContainer) {
            //Updates any List properties on a model to a BuiltList. This happens in post processing rather
            //than type mapping as we only want this to apply to models, not every other class.
            if ("Map".equals(property.baseType)) {
                property.setDatatype(property.dataType.replaceAll(property.baseType, "BuiltMap"));
                property.setBaseType("BuiltMap");
                model.imports.add("BuiltMap");
            }
        }

    }

    @Override
    public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
        objs = super.postProcessOperationsWithModels(objs, allModels);
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");

        Set<String> modelImports = new HashSet<>();
        Set<String> fullImports = new HashSet<>();

        for (CodegenOperation op : operationList) {
            op.httpMethod = op.httpMethod.toLowerCase(Locale.ROOT);
            boolean isJson = true; //default to JSON
            boolean isForm = false;
            boolean isMultipart = false;
            if (op.consumes != null) {
                for (Map<String, String> consume : op.consumes) {
                    if (consume.containsKey("mediaType")) {
                        String type = consume.get("mediaType");
                        isJson = type.equalsIgnoreCase("application/json");
                        isForm = type.equalsIgnoreCase("application/x-www-form-urlencoded");
                        isMultipart = type.equalsIgnoreCase("multipart/form-data");
                        break;
                    }
                }
            }

            for (CodegenParameter param : op.bodyParams) {
                if (param.baseType != null && param.baseType.equalsIgnoreCase("Uint8List") && isMultipart) {
                    param.baseType = "MultipartFile";
                    param.dataType = "MultipartFile";
                }
            }

            op.vendorExtensions.put("isJson", isJson);
            op.vendorExtensions.put("isForm", isForm);
            op.vendorExtensions.put("isMultipart", isMultipart);

            Set<String> imports = new HashSet<>();
            for (String item : op.imports) {
                if (!modelToIgnore.contains(item.toLowerCase(Locale.ROOT))) {
                    imports.add(underscore(item));
                } else if (item.equalsIgnoreCase("Uint8List")) {
                    fullImports.add("dart:typed_data");
                }
            }
            modelImports.addAll(imports);
            op.imports = imports;
        }

        objs.put("modelImports", modelImports);
        objs.put("fullImports", fullImports);

        return objs;
    }
}

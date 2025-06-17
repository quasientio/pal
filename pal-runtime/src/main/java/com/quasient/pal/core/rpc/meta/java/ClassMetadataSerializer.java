package com.quasient.pal.core.rpc.meta.java;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quasient.pal.core.rpc.exec.java.CustomClassloader;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ScanResult;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans the classpath to extract metadata for classes and serializes the collected data into JSON
 * format. The output file may be compressed using GZIP and Base64 encoded based on the
 * configuration. The generated metadata includes details such as class modifiers, source file,
 * inheritance hierarchies, constructors, methods, and fields. In addition, it supports merging of
 * inherited members from ancestor classes and interfaces.
 */
@Singleton
public class ClassMetadataSerializer {

  /** Logger for reporting scanning and serialization events. */
  private static final Logger logger = LoggerFactory.getLogger(ClassMetadataSerializer.class);

  /** Prefix identifier for PAL classes used in exclusion filters. */
  private static final String PAL_PREFIX = "com.quasient.pal.";

  /** Set of class name prefixes to be excluded from scanning. */
  private static final Set<String> CLASS_PREFIXES_TO_EXCLUDE =
      Set.of("com.sun.", "sun.", "jdk.", PAL_PREFIX);

  /** Flag indicating whether non-public classes and members should be scanned. */
  private final boolean scanNonPublic;

  /**
   * Optional custom classloader used to load classes during scanning. Null to use the default
   * classloader.
   */
  @Nullable private final CustomClassloader customClassloader;

  /**
   * Constructs a ClassMetadataSerializer with the specified non-public scanning option and optional
   * custom classloader.
   *
   * @param rpcAllowNonpublicStr string value that is parsed to determine if non-public members
   *     should be scanned.
   * @param customClassloader optional custom classloader to be used for scanning; may be null.
   */
  @Inject
  public ClassMetadataSerializer(
      @Named("rpc.allow_nonpublic") String rpcAllowNonpublicStr,
      @Nullable CustomClassloader customClassloader) {
    this.scanNonPublic = Boolean.parseBoolean(rpcAllowNonpublicStr);
    this.customClassloader = customClassloader;
  }

  /**
   * Constructs a ClassMetadataSerializer configured to allow or disallow scanning of non-public
   * members.
   *
   * @param rpcAllowNonpublic flag to enable scanning of non-public classes and members.
   */
  ClassMetadataSerializer(boolean rpcAllowNonpublic) {
    this.scanNonPublic = rpcAllowNonpublic;
    this.customClassloader = null;
  }

  /**
   * Scans the classpath to collect metadata for classes and serializes the result into a temporary
   * JSON file. Depending on the parameters, the output may be compressed with GZIP and encoded
   * using Base64. The metadata includes class details such as name, modifiers, inheritance
   * information, constructors, methods, and fields.
   *
   * @param compressAndEncode if true, the output JSON is GZip-compressed and Base64-encoded.
   * @param includeClasses optional set of fully qualified class names to restrict the scan; if
   *     null, all classes are scanned.
   * @param additionalExcludePrefixes optional additional prefixes for class names to exclude from
   *     scanning.
   * @param mergeAncestry if true, merges inherited methods and fields with local declarations.
   * @return the Path of a temporary file containing the serialized metadata.
   * @throws Exception if scanning or file operations fail.
   */
  public Path scannedClasspathToJson(
      boolean compressAndEncode,
      @Nullable Set<String> includeClasses,
      @Nullable Set<String> additionalExcludePrefixes,
      boolean mergeAncestry)
      throws Exception {

    ObjectMapper mapper = new ObjectMapper();
    int classesScanned = 0;

    ClassGraph classGraph =
        new ClassGraph()
            .enableClassInfo()
            .enableMethodInfo()
            .enableFieldInfo()
            .disableRuntimeInvisibleAnnotations()
            .enableSystemJarsAndModules()
            .removeTemporaryFilesAfterScan();

    if (includeClasses != null && !includeClasses.isEmpty()) {
      // we will only scan the classes to include
      classGraph.acceptClasses(includeClasses.toArray(new String[0]));
    } else {
      classGraph.acceptPackages();
    }

    if (customClassloader != null) {
      classGraph.overrideClassLoaders(customClassloader);
      classGraph.ignoreParentClassLoaders();
    }

    if (scanNonPublic) {
      classGraph.ignoreClassVisibility().ignoreMethodVisibility().ignoreFieldVisibility();
    }

    // Decide on file suffix
    String suffix = compressAndEncode ? ".gz.b64" : ".json";
    Path outFile = Files.createTempFile("classinfo_metadata_", suffix);

    try (FileOutputStream fos = new FileOutputStream(outFile.toFile());
        OutputStream finalOut =
            compressAndEncode ? new GZIPOutputStream(Base64.getEncoder().wrap(fos)) : fos;
        JsonGenerator jGenerator = new JsonFactory().createGenerator(finalOut, JsonEncoding.UTF8);
        ScanResult scanResult = classGraph.scan()) {

      jGenerator.writeStartArray();
      for (ClassInfo classInfo :
          scanResult.getAllClasses().filter(ci -> !ci.getName().contains("$"))) {
        String className = classInfo.getName();

        // skip class if it starts with an exclude prefix
        if (CLASS_PREFIXES_TO_EXCLUDE.stream().anyMatch(className::startsWith)) {
          continue;
        }
        if (additionalExcludePrefixes != null
            && additionalExcludePrefixes.stream().anyMatch(className::startsWith)) {
          continue;
        }

        // create JSON object for the class
        ObjectNode classObject = mapper.createObjectNode();
        classObject.put("className", className);
        classObject.put("simpleName", classInfo.getSimpleName());
        classObject.put("package", classInfo.getPackageName());
        classObject.put("modifiers", classInfo.getModifiers());
        classObject.put("majorVersion", classInfo.getClassfileMajorVersion());
        classObject.put("minorVersion", classInfo.getClassfileMinorVersion());
        classObject.put("sourceFile", classInfo.getSourceFile());
        classObject.put("isArrayClass", classInfo.isArrayClass());
        if (classInfo.getSuperclass() != null) {
          classObject.put("superclass", classInfo.getSuperclass().getName());
        }

        // superclasses
        ArrayNode superClassesArray = mapper.createArrayNode();
        for (ClassInfo superClass : classInfo.getSuperclasses()) {
          superClassesArray.add(superClass.getName());
        }
        classObject.set("superclasses", superClassesArray);

        // interfaces
        ArrayNode interfacesArray = mapper.createArrayNode();
        for (ClassInfo iFace : classInfo.getInterfaces()) {
          interfacesArray.add(iFace.getName());
        }
        classObject.set("interfaces", interfacesArray);

        // subclasses
        ArrayNode subClassesArray = mapper.createArrayNode();
        for (ClassInfo subclass : classInfo.getSubclasses()) {
          subClassesArray.add(subclass.getName());
        }
        classObject.set("subclasses", subClassesArray);

        // If mergeAncestry = false, we only collect declared members from classInfo
        // Otherwise, we collect and unify from all ancestors.
        // The final arrays below will contain a merged set of members.
        ArrayNode constructorsArray = mapper.createArrayNode();
        ArrayNode methodsArray = mapper.createArrayNode();
        ArrayNode fieldsArray = mapper.createArrayNode();

        fillConstructorsArray(mapper, classInfo, constructorsArray);
        if (!mergeAncestry) {
          fillMethodsArray(mapper, classInfo, methodsArray);
          fillFieldsArray(mapper, classInfo, fieldsArray);
        } else {
          // We'll gather from all ancestors (including interfaces).
          // This method returns a merged representation for each category.
          mergeMethods(mapper, classInfo, methodsArray);
          mergeFields(mapper, classInfo, fieldsArray);
        }

        // Attach arrays
        classObject.set("constructors", constructorsArray);
        classObject.set("methods", methodsArray);
        classObject.set("fields", fieldsArray);

        // output new class info to file
        mapper.writeValue(jGenerator, classObject);
        classesScanned++;
      }

      jGenerator.writeEndArray();

      if (logger.isInfoEnabled()) {
        logger.info("Metadata scanned and serialized for {} classes", classesScanned);
      }
    }

    return outFile;
  }

  /**
   * Collects declared constructors for the given class metadata, excluding synthetic and
   * aspect-weaver generated constructors.
   *
   * @param mapper ObjectMapper used for constructing JSON objects.
   * @param classInfo metadata of the class whose constructors are to be serialized.
   * @param constructorsArray ArrayNode where the serialized constructor data is added.
   */
  private static void fillConstructorsArray(
      ObjectMapper mapper, ClassInfo classInfo, ArrayNode constructorsArray) {
    for (MethodInfo constructorInfo : classInfo.getDeclaredConstructorInfo()) {
      if (isAspectWeaverMethod(constructorInfo)) {
        continue;
      }
      ObjectNode constructorObject = createConstructorJson(mapper, constructorInfo);
      constructorsArray.add(constructorObject);
    }
  }

  /**
   * Collects declared methods for the given class metadata, excluding synthetic and aspect-weaver
   * items, and adds them to the provided JSON array. Additionally, ensures inclusion of
   * java.lang.Object methods.
   *
   * @param mapper ObjectMapper used to create JSON nodes.
   * @param classInfo metadata of the class to process.
   * @param methodsArray ArrayNode to which the method metadata is added.
   */
  private static void fillMethodsArray(
      ObjectMapper mapper, ClassInfo classInfo, ArrayNode methodsArray) {

    Map<String, ObjectNode> signatureToMethodMap = new HashMap<>();
    for (MethodInfo methodInfo : classInfo.getMethodInfo()) {
      if (isAspectWeaverMethod(methodInfo)) {
        continue;
      }
      String sig = methodSignature(methodInfo);
      ObjectNode methodObject = createMethodJson(mapper, methodInfo, null, false);
      signatureToMethodMap.put(sig, methodObject);
    }

    // 2) forcibly add all java.lang.Object methods via reflection
    addJavaLangObjectMethodsViaReflection(mapper, signatureToMethodMap);

    // Put all the methods in the final array
    for (ObjectNode method : signatureToMethodMap.values()) {
      methodsArray.add(method);
    }
  }

  /**
   * Collects declared fields for the provided class metadata, excluding synthetic and aspect-weaver
   * fields, and appends the serialized data into the given JSON array.
   *
   * @param mapper ObjectMapper used for JSON creation.
   * @param classInfo metadata of the class whose fields are being processed.
   * @param fieldsArray ArrayNode to hold the serialized field data.
   */
  private static void fillFieldsArray(
      ObjectMapper mapper, ClassInfo classInfo, ArrayNode fieldsArray) {
    for (FieldInfo fieldInfo : classInfo.getFieldInfo()) {
      if (isAspectWeaverField(fieldInfo)) {
        continue;
      }
      ObjectNode fieldObject = createFieldJson(mapper, fieldInfo, null, false);
      fieldsArray.add(fieldObject);
    }
  }

  /**
   * Merges methods from all ancestors (superclasses, interfaces, and their ancestors) into a
   * unified set. For methods overridden in the local class, the local version replaces the
   * inherited one and is marked as overridden. Also ensures that all methods from java.lang.Object
   * are included.
   *
   * @param mapper ObjectMapper used for creating JSON representations.
   * @param classInfo metadata of the class for which to merge methods.
   * @param methodsArray ArrayNode where the merged method metadata is stored.
   */
  private static void mergeMethods(
      ObjectMapper mapper, ClassInfo classInfo, ArrayNode methodsArray) {
    Map<String, ObjectNode> signatureToMethodMap = new HashMap<>();

    // 1) gather from all ancestors
    Set<ClassInfo> allAncestors = gatherAllAncestors(classInfo);
    for (ClassInfo ancestor : allAncestors) {
      for (MethodInfo methodInfo : ancestor.getDeclaredMethodInfo()) {
        if (isAspectWeaverMethod(methodInfo)) {
          continue;
        }
        String sig = methodSignature(methodInfo);
        if (!signatureToMethodMap.containsKey(sig)) {
          ObjectNode methodJson = createMethodJson(mapper, methodInfo, ancestor.getName(), false);
          signatureToMethodMap.put(sig, methodJson);
        }
      }
    }

    // 2) forcibly add all java.lang.Object methods via reflection
    addJavaLangObjectMethodsViaReflection(mapper, signatureToMethodMap);

    // 3) incorporate local declared methods
    for (MethodInfo methodInfo : classInfo.getDeclaredMethodInfo()) {
      if (isAspectWeaverMethod(methodInfo)) {
        continue;
      }
      String sig = methodSignature(methodInfo);
      if (signatureToMethodMap.containsKey(sig)) {
        // It's an override
        ObjectNode ancestorMethod = signatureToMethodMap.get(sig);
        String inheritedFrom = ancestorMethod.get("inheritedFrom").asText();
        ObjectNode localJson = createMethodJson(mapper, methodInfo, inheritedFrom, true);
        signatureToMethodMap.put(sig, localJson);
      } else {
        // Purely local
        ObjectNode localJson = createMethodJson(mapper, methodInfo, null, false);
        signatureToMethodMap.put(sig, localJson);
      }
    }

    // Put all methods in the final array
    for (ObjectNode method : signatureToMethodMap.values()) {
      methodsArray.add(method);
    }
  }

  /**
   * Merges fields from all ancestors into a unified set. If the local class declares a field that
   * shadows an inherited field, the local field replaces the ancestor field and is flagged as
   * overridden.
   *
   * @param mapper ObjectMapper used to construct JSON objects.
   * @param classInfo metadata of the class whose fields are merged.
   * @param fieldsArray ArrayNode to which the merged field data is added.
   */
  private static void mergeFields(ObjectMapper mapper, ClassInfo classInfo, ArrayNode fieldsArray) {
    Map<String, ObjectNode> nameMap = new HashMap<>();

    // Step 1: gather from all ancestors
    Set<ClassInfo> allAncestors = gatherAllAncestors(classInfo);
    for (ClassInfo ancestor : allAncestors) {
      for (FieldInfo fieldInfo : ancestor.getDeclaredFieldInfo()) {
        if (isAspectWeaverField(fieldInfo)) {
          continue;
        }
        String fieldName = fieldInfo.getName();
        if (!nameMap.containsKey(fieldName)) {
          ObjectNode fieldJson = createFieldJson(mapper, fieldInfo, ancestor.getName(), false);
          nameMap.put(fieldName, fieldJson);
        }
      }
    }

    // Step 2: local declared fields
    for (FieldInfo fieldInfo : classInfo.getDeclaredFieldInfo()) {
      if (isAspectWeaverField(fieldInfo)) {
        continue;
      }
      String fieldName = fieldInfo.getName();
      if (nameMap.containsKey(fieldName)) {
        // local shadows the ancestor
        ObjectNode ancestorField = nameMap.get(fieldName);
        String inheritedFrom = ancestorField.get("inheritedFrom").asText();
        ObjectNode localFieldJson = createFieldJson(mapper, fieldInfo, inheritedFrom, true);
        nameMap.put(fieldName, localFieldJson);
      } else {
        ObjectNode localFieldJson = createFieldJson(mapper, fieldInfo, null, false);
        nameMap.put(fieldName, localFieldJson);
      }
    }

    for (ObjectNode field : nameMap.values()) {
      fieldsArray.add(field);
    }
  }

  /**
   * Gathers all ancestor classes and interfaces (including recursively collected superclasses and
   * superinterfaces) for the provided class metadata.
   *
   * @param classInfo metadata of the class whose ancestors are to be gathered.
   * @return an ordered Set of ClassInfo objects representing all ancestors.
   */
  private static Set<ClassInfo> gatherAllAncestors(ClassInfo classInfo) {
    // use an ordered set so we can iterate first through superclasses
    Set<ClassInfo> result = new LinkedHashSet<>();

    // First, superclasses
    result.add(classInfo.getSuperclass());
    result.addAll(classInfo.getSuperclasses());
    // We also collect superclasses of superclasses recursively
    for (ClassInfo sc : classInfo.getSuperclasses()) {
      gatherAllAncestorsRecursive(sc, result);
    }

    // Then, interfaces
    for (ClassInfo iFace : classInfo.getInterfaces()) {
      result.add(iFace);
      result.addAll(iFace.getInterfaces()); // superinterfaces
    }
    // And interfaces for superinterfaces
    for (ClassInfo iFace : classInfo.getInterfaces()) {
      gatherAllAncestorsRecursive(iFace, result);
    }

    // remove null entry if it was somehow added
    result.remove(null);
    return result;
  }

  /**
   * Recursively collects ancestor classes and interfaces for a given class into the provided
   * accumulator set. This helper method ensures that each ancestor is added only once.
   *
   * @param classInfo the current class metadata being processed.
   * @param acc the accumulator Set to which discovered ancestors are added.
   */
  private static void gatherAllAncestorsRecursive(ClassInfo classInfo, Set<ClassInfo> acc) {
    if (classInfo == null || acc.contains(classInfo)) {
      return;
    }
    acc.add(classInfo);
    for (ClassInfo sc : classInfo.getSuperclasses()) {
      gatherAllAncestorsRecursive(sc, acc);
    }
    for (ClassInfo iFace : classInfo.getInterfaces()) {
      gatherAllAncestorsRecursive(iFace, acc);
    }
  }

  /**
   * Adds methods from java.lang.Object to the provided signature map if they are not already
   * present. This ensures that standard Object methods are included in the metadata scan.
   *
   * @param mapper ObjectMapper used for creating JSON nodes.
   * @param signatureMap Map associating method signatures with their JSON representations.
   */
  private static void addJavaLangObjectMethodsViaReflection(
      ObjectMapper mapper, Map<String, ObjectNode> signatureMap) {

    for (Method m : Object.class.getDeclaredMethods()) {
      if (m.isSynthetic() || m.getName().contains("$")) {
        continue;
      }
      String sig = reflectionMethodSignature(m);
      // If it’s not already known, we add it as inherited from java.lang.Object
      if (!signatureMap.containsKey(sig)) {
        ObjectNode methodJson =
            createMethodJsonFromReflection(mapper, m, "java.lang.Object", false);
        signatureMap.put(sig, methodJson);
      }
    }
  }

  // -------------------- JSON creation helpers --------------------

  /**
   * Creates a JSON representation for the given constructor metadata.
   *
   * @param mapper ObjectMapper used to create JSON nodes.
   * @param ctorInfo metadata of the constructor.
   * @return an ObjectNode representing the constructor including its name, modifiers, and
   *     parameters.
   */
  private static ObjectNode createConstructorJson(ObjectMapper mapper, MethodInfo ctorInfo) {
    ObjectNode constructorObject = mapper.createObjectNode();
    constructorObject.put("name", ctorInfo.getName());
    constructorObject.put("modifiers", ctorInfo.getModifiers());

    ArrayNode parametersArray = mapper.createArrayNode();
    for (MethodParameterInfo paramInfo : ctorInfo.getParameterInfo()) {
      ObjectNode paramObject = mapper.createObjectNode();
      paramObject.put("name", paramInfo.getName());
      paramObject.put("modifiers", paramInfo.getModifiers());
      paramObject.put("type", paramInfo.getTypeSignatureOrTypeDescriptor().toString());
      parametersArray.add(paramObject);
    }
    constructorObject.set("parameters", parametersArray);

    return constructorObject;
  }

  /**
   * Creates a JSON representation for the given method metadata.
   *
   * @param mapper ObjectMapper used for JSON creation.
   * @param methodInfo metadata of the method to be serialized.
   * @param inheritedFrom the name of the class from which the method is inherited, or null if
   *     declared locally.
   * @param overridden true if the method overrides an inherited method.
   * @return an ObjectNode representing the method's metadata, including name, modifiers, return
   *     type, and parameters.
   */
  private static ObjectNode createMethodJson(
      ObjectMapper mapper, MethodInfo methodInfo, String inheritedFrom, boolean overridden) {
    ObjectNode methodObject = mapper.createObjectNode();
    methodObject.put("name", methodInfo.getName());
    methodObject.put("modifiers", methodInfo.getModifiers());
    methodObject.put("isStatic", methodInfo.isStatic());
    methodObject.put(
        "returnType", methodInfo.getTypeSignatureOrTypeDescriptor().getResultType().toString());
    methodObject.put("overridden", overridden);
    if (inheritedFrom != null) {
      methodObject.put("inheritedFrom", inheritedFrom);
    }

    ArrayNode parametersArray = mapper.createArrayNode();
    for (MethodParameterInfo paramInfo : methodInfo.getParameterInfo()) {
      ObjectNode paramObject = mapper.createObjectNode();
      paramObject.put("name", paramInfo.getName());
      paramObject.put("modifiers", paramInfo.getModifiers());
      paramObject.put("type", paramInfo.getTypeSignatureOrTypeDescriptor().toString());
      parametersArray.add(paramObject);
    }
    methodObject.set("parameters", parametersArray);

    return methodObject;
  }

  /**
   * Creates a JSON representation from a reflective Method instance.
   *
   * @param mapper ObjectMapper for creating JSON nodes.
   * @param m the Method instance obtained via reflection.
   * @param inheritedFrom the class name from which the method is inherited.
   * @param overridden true if the method is an override of an inherited method.
   * @return an ObjectNode containing the reflective method's metadata.
   */
  private static ObjectNode createMethodJsonFromReflection(
      ObjectMapper mapper, Method m, String inheritedFrom, boolean overridden) {

    ObjectNode methodObject = mapper.createObjectNode();
    methodObject.put("name", m.getName());
    methodObject.put("modifiers", m.getModifiers());
    methodObject.put("isStatic", Modifier.isStatic(m.getModifiers()));
    methodObject.put("returnType", m.getReturnType().getName());
    methodObject.put("overridden", overridden);
    if (inheritedFrom != null) {
      methodObject.put("inheritedFrom", inheritedFrom);
    }

    // Reflection won't give you actual parameter names by default; we would need debug info for
    // that.
    // We'll just label them arg0, arg1, etc.
    ArrayNode parametersArray = mapper.createArrayNode();
    Class<?>[] paramTypes = m.getParameterTypes();
    for (int i = 0; i < paramTypes.length; i++) {
      ObjectNode paramObject = mapper.createObjectNode();
      paramObject.put("name", "arg" + i);
      paramObject.put("modifiers", 0); // reflection doesn't track param-level modifiers easily
      paramObject.put("type", paramTypes[i].getName());
      parametersArray.add(paramObject);
    }
    methodObject.set("parameters", parametersArray);

    return methodObject;
  }

  /**
   * Creates a JSON representation for the given field metadata.
   *
   * @param mapper ObjectMapper used to create JSON nodes.
   * @param fieldInfo metadata of the field.
   * @param inheritedFrom the name of the class from which the field is inherited, or null if
   *     declared locally.
   * @param overridden true if the field overrides or shadows an inherited field.
   * @return an ObjectNode representing the field's metadata, including name, type, and modifiers.
   */
  private static ObjectNode createFieldJson(
      ObjectMapper mapper, FieldInfo fieldInfo, String inheritedFrom, boolean overridden) {
    ObjectNode fieldObject = mapper.createObjectNode();
    fieldObject.put("name", fieldInfo.getName());
    fieldObject.put("modifiers", fieldInfo.getModifiers());
    fieldObject.put("type", fieldInfo.getTypeSignatureOrTypeDescriptor().toString());
    fieldObject.put("isStatic", fieldInfo.isStatic());
    fieldObject.put("overridden", overridden);
    if (inheritedFrom != null) {
      fieldObject.put("inheritedFrom", inheritedFrom);
    }
    return fieldObject;
  }

  // -------------------- signature utilities for override detection --------------------

  /**
   * Generates a unique signature string for the given method based on its name, generic parameters,
   * parameter types, and return type. This signature is used to detect method overrides and to
   * uniquely identify methods.
   *
   * @param methodInfo metadata of the method.
   * @return a string representing the method's signature.
   */
  private static String methodSignature(MethodInfo methodInfo) {
    StringBuilder sb = new StringBuilder();

    // 1) Check if MethodInfo has a generic signature
    //    i.e. if method-level type parameters exist
    var methodSignature = methodInfo.getTypeSignature();
    if (methodSignature != null
        && methodSignature.getTypeParameters() != null
        && !methodSignature.getTypeParameters().isEmpty()) {

      sb.append("<");
      boolean firstParam = true;
      for (var typeParam : methodSignature.getTypeParameters()) {
        if (!firstParam) {
          sb.append(", ");
        }
        sb.append(typeParam.toString()); // includes the bounds, e.g. "K extends ..."
        firstParam = false;
      }
      sb.append("> ");
    }

    // 2) Method name
    sb.append(methodInfo.getName()).append("(");

    // 3) Parameter types
    MethodParameterInfo[] params = methodInfo.getParameterInfo();
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      // the parameter’s type signature (including generics)
      sb.append(params[i].getTypeSignatureOrTypeDescriptor().toString());
    }
    sb.append(")");

    // 4) Return type
    sb.append(" -> ")
        .append(methodInfo.getTypeSignatureOrTypeDescriptor().getResultType().toString());

    return sb.toString();
  }

  /**
   * Generates a unique signature string for the provided reflective Method instance. This signature
   * incorporates type parameters, parameter types, and return type.
   *
   * @param m the reflective Method instance.
   * @return a string representing the method's signature.
   */
  private static String reflectionMethodSignature(Method m) {
    StringBuilder sb = new StringBuilder();

    // 1) If the method has type parameters (e.g. <K extends Comparable<? super K>, V>),
    //    include them at the start.
    TypeVariable<Method>[] typeParameters = m.getTypeParameters();
    if (typeParameters.length > 0) {
      sb.append("<");
      for (int i = 0; i < typeParameters.length; i++) {
        if (i > 0) {
          sb.append(", ");
        }
        TypeVariable<Method> tv = typeParameters[i];
        sb.append(tv.getName());

        // If the bound is not just Object, we show it
        Type[] bounds = tv.getBounds();
        if (bounds.length > 0 && !(bounds.length == 1 && bounds[0] == Object.class)) {
          sb.append(" extends ");
          for (int b = 0; b < bounds.length; b++) {
            if (b > 0) {
              sb.append(" & ");
            }
            sb.append(typeToString(bounds[b]));
          }
        }
      }
      sb.append("> ");
    }

    // 2) Method name
    sb.append(m.getName()).append("(");

    // 3) Parameter types
    Type[] paramTypes = m.getGenericParameterTypes();
    for (int i = 0; i < paramTypes.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(typeToString(paramTypes[i]));
    }
    sb.append(")");

    // 4) Return type
    Type returnType = m.getGenericReturnType();
    sb.append(" -> ").append(typeToString(returnType));

    return sb.toString();
  }

  /**
   * Converts a Type instance to its string representation.
   *
   * @param type the Type to convert.
   * @return the string representation of the type.
   */
  private static String typeToString(Type type) {
    return type.getTypeName();
  }

  /**
   * Determines whether the specified method is synthetic or generated by aspect-weaver tools and
   * should be excluded.
   *
   * @param methodInfo metadata of the method.
   * @return true if the method is synthetic or aspect-weaver generated; false otherwise.
   */
  private static boolean isAspectWeaverMethod(MethodInfo methodInfo) {
    return methodInfo.isSynthetic()
        || methodInfo.getName().contains("_aroundBody")
        || methodInfo.getName().contains("$");
  }

  /**
   * Determines whether the given field is synthetic or generated by aspect-weaver tools and should
   * be excluded.
   *
   * @param fieldInfo metadata of the field.
   * @return true if the field is synthetic or aspect-weaver generated; false otherwise.
   */
  private static boolean isAspectWeaverField(FieldInfo fieldInfo) {
    return fieldInfo.isSynthetic() || fieldInfo.getName().contains("ajc$");
  }
}

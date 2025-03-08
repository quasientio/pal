package net.ittera.pal.core.rpc.meta.java;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import net.ittera.pal.core.rpc.exec.java.CustomClassloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ClassMetadataSerializer {

  private static final Logger logger = LoggerFactory.getLogger(ClassMetadataSerializer.class);
  private static final String PAL_PREFIX = "net.ittera.pal.";
  private static final Set<String> CLASS_PREFIXES_TO_EXCLUDE =
      Set.of("com.sun.", "sun.", "jdk.", PAL_PREFIX);

  private final boolean scanNonPublic;
  @Nullable private final CustomClassloader customClassloader;

  @Inject
  public ClassMetadataSerializer(
      @Named("rpc.allow_nonpublic") String rpcAllowNonpublicStr,
      @Nullable CustomClassloader customClassloader) {
    this.scanNonPublic = Boolean.parseBoolean(rpcAllowNonpublicStr);
    this.customClassloader = customClassloader;
  }

  ClassMetadataSerializer(boolean rpcAllowNonpublic) {
    this.scanNonPublic = rpcAllowNonpublic;
    this.customClassloader = null;
  }

  /**
   * Generate class metadata and return it as a (possibly compressed & encoded ) JSON string.
   *
   * @param compressAndEncode if true, returns Base64-encoded GZip-compressed JSON
   * @param includeClasses optional set of specific classes to include
   * @param additionalExcludePrefixes optional set of additional class prefixes to exclude
   * @param mergeAncestry if true, merges methods and fields from all ancestors
   * @return the path of a temporary file created with the contents of the extracted metadata
   */
  public Path scannedClasspathToJson(
      boolean compressAndEncode,
      @Nullable Set<String> includeClasses,
      @Nullable Set<String> additionalExcludePrefixes,
      boolean mergeAncestry)
      throws Exception {

    ObjectMapper mapper = new ObjectMapper();
    // store all class metadata as an array
    ArrayNode classesArray = mapper.createArrayNode();

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

    try (ScanResult scanResult = classGraph.scan()) {
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

        // add class to array
        classesArray.add(classObject);
      }

      if (logger.isDebugEnabled()) {
        logger.debug("Number of classes returned: {}", classesArray.size());
      }
    }

    return writeAsJSONToFile(classesArray, mapper, compressAndEncode);
  }

  /** Collect declared constructors for a classInfo, excluding synthetic/aspect-weaver items. */
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

  /** Collect methods for a classInfo, excluding synthetic/aspect-weaver items. */
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

  /** Collect fields for a classInfo, excluding synthetic/aspect-weaver items. */
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
   * Merges methods from all ancestors (superclasses + interfaces + their superinterfaces). -
   * "inheritedFrom" is always the ancestor where it was declared. - If the local class overrides a
   * method with the same signature, we replace it with the child's version and set "overridden =
   * true" in the child's JSON.
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
   * Merges fields from all ancestors. Technically fields aren't inherited in the same sense as
   * methods, but we do the same pattern for a "full metadata" view. - "inheritedFrom" is set to the
   * ancestor that declares the field. - If the local class has a field with the same name (i.e.,
   * shadows the ancestor field), we replace the ancestor entry with the child's and set "overridden
   * = true".
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
   * Gathers all superclasses and interfaces (and their superinterfaces) for the given classInfo. We
   * do a breadth or depth approach to unify them in one Set.
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

  /* We explicitly add java.lang.Object methods, which Classgraph leaves out from the scan. */
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

  private static String typeToString(Type type) {
    return type.getTypeName();
  }

  private static boolean isAspectWeaverMethod(MethodInfo methodInfo) {
    return methodInfo.isSynthetic()
        || methodInfo.getName().contains("_aroundBody")
        || methodInfo.getName().contains("$");
  }

  private static boolean isAspectWeaverField(FieldInfo fieldInfo) {
    return fieldInfo.isSynthetic() || fieldInfo.getName().contains("ajc$");
  }

  public Path writeAsJSONToFile(ArrayNode classesArray, ObjectMapper mapper, boolean gzipAndEncode)
      throws Exception {
    // Decide on file suffix
    String suffix = gzipAndEncode ? ".gz.b64" : ".json";
    Path outFile = Files.createTempFile("classinfo_metadata_", suffix);

    // Create the initial file output stream
    try (FileOutputStream fos = new FileOutputStream(outFile.toFile());
        // Conditionally wrap the stream in Base64 → GZIP or just use raw:
        OutputStream finalOut =
            gzipAndEncode ? new GZIPOutputStream(Base64.getEncoder().wrap(fos)) : fos;
        // Create a streaming JsonGenerator that writes into finalOut
        JsonGenerator jGenerator = new JsonFactory().createGenerator(finalOut, JsonEncoding.UTF8)) {
      jGenerator.writeStartArray();
      for (JsonNode data : classesArray) {
        mapper.writeValue(jGenerator, data);
      }
      jGenerator.writeEndArray();
    }

    return outFile;
  }
}

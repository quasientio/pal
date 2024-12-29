package net.ittera.pal.core.rpc.meta.java;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Set;
import javax.annotation.Nullable;
import net.ittera.pal.common.util.GzipBase64Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ClassMetadataSerializer {

  private static final Logger logger = LoggerFactory.getLogger(ClassMetadataSerializer.class);
  private final boolean scanNonPublic;
  private static final String PAL_PREFIX = "net.ittera.pal.";
  private static final Set<String> CLASS_PREFIXES_TO_EXCLUDE =
      Set.of("com.sun.", "sun.", "jdk.", PAL_PREFIX);

  @Inject
  public ClassMetadataSerializer(@Named("rpc.allow_nonpublic") String rpcAllowNonpublicStr) {
    this(Boolean.parseBoolean(rpcAllowNonpublicStr));
  }

  ClassMetadataSerializer(boolean rpcAllowNonpublic) {
    this.scanNonPublic = rpcAllowNonpublic;
  }

  public String scannedClasspathToJson(
      boolean compressAndEncode, @Nullable Set<String> additionalExcludePrefixes)
      throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();

    // enable pretty printing
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    // store all class metadata as an array
    ArrayNode classesArray = mapper.createArrayNode();

    ClassGraph classGraph =
        new ClassGraph()
            .enableClassInfo()
            .enableMethodInfo()
            .enableFieldInfo()
            .acceptPackages()
            .enableSystemJarsAndModules();

    if (scanNonPublic) {
      classGraph.ignoreClassVisibility().ignoreMethodVisibility().ignoreFieldVisibility();
    }

    try (ScanResult scanResult = classGraph.scan()) {
      for (ClassInfo classInfo : scanResult.getAllClasses()) {
        // skip class if it starts with an exclude prefix
        String className = classInfo.getName();
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

        // constructors
        ArrayNode constructorsArray = mapper.createArrayNode();
        for (MethodInfo constructorInfo : classInfo.getDeclaredConstructorInfo()) {
          ObjectNode constructorObject = mapper.createObjectNode();
          constructorObject.put("name", constructorInfo.getName());
          constructorObject.put("modifiers", constructorInfo.getModifiersStr());

          ArrayNode parametersArray = mapper.createArrayNode();
          for (var paramInfo : constructorInfo.getParameterInfo()) {
            ObjectNode paramObject = mapper.createObjectNode();
            paramObject.put("name", paramInfo.getName());
            paramObject.put("type", paramInfo.getTypeSignatureOrTypeDescriptor().toString());
            parametersArray.add(paramObject);
          }
          constructorObject.set("parameters", parametersArray);
          constructorsArray.add(constructorObject);
        }
        classObject.set("constructors", constructorsArray);

        // methods
        ArrayNode methodsArray = mapper.createArrayNode();
        for (MethodInfo methodInfo : classInfo.getDeclaredMethodInfo()) {
          ObjectNode methodObject = mapper.createObjectNode();
          methodObject.put("name", methodInfo.getName());
          methodObject.put("modifiers", methodInfo.getModifiersStr());
          methodObject.put(
              "returnType",
              methodInfo.getTypeSignatureOrTypeDescriptor().getResultType().toString());

          ArrayNode parametersArray = mapper.createArrayNode();
          for (var paramInfo : methodInfo.getParameterInfo()) {
            ObjectNode paramObject = mapper.createObjectNode();
            paramObject.put("name", paramInfo.getName());
            paramObject.put("type", paramInfo.getTypeSignatureOrTypeDescriptor().toString());
            parametersArray.add(paramObject);
          }
          methodObject.set("parameters", parametersArray);
          methodsArray.add(methodObject);
        }
        classObject.set("methods", methodsArray);

        // fields
        ArrayNode fieldsArray = mapper.createArrayNode();
        for (FieldInfo fieldInfo : classInfo.getDeclaredFieldInfo()) {
          ObjectNode fieldObject = mapper.createObjectNode();
          fieldObject.put("name", fieldInfo.getName());
          fieldObject.put("modifiers", fieldInfo.getModifiersStr());
          fieldObject.put("type", fieldInfo.getTypeSignatureOrTypeDescriptor().toString());
          fieldsArray.add(fieldObject);
        }
        classObject.set("fields", fieldsArray);

        // add class to array
        classesArray.add(classObject);
      }

      if (logger.isDebugEnabled()) {
        logger.debug("Number of classes returned: {}", scanResult.getAllClasses().size());
      }
    } catch (Exception e) {
      logger.error("Error generating class metadata", e);
    }

    // convert the json tree to a pretty-printed JSON string
    String classMetadataAsJson = mapper.writeValueAsString(classesArray);

    if (!compressAndEncode) {
      // return plain JSON
      return classMetadataAsJson;
    }

    // return the Base64-encoded, GZip-compressed JSON
    return GzipBase64Utils.encode(classMetadataAsJson);
  }
}

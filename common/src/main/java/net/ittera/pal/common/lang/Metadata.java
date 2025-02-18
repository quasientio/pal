/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.lang;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Metadata {

  private static final Comparator<ParameterInfo> PARAMETER_COMPARATOR =
      Comparator.comparing(ParameterInfo::getType)
          .thenComparing(ParameterInfo::getName, Comparator.nullsFirst(String::compareTo));

  public static final Comparator<ConstructorInfo> CONSTRUCTOR_COMPARATOR =
      (c1, c2) -> {
        // 1. Compare by constructor name
        int nameCompare = c1.getName().compareTo(c2.getName());
        if (nameCompare != 0) {
          return nameCompare;
        }

        // 2. Compare number of parameters
        int paramCountCompare =
            Integer.compare(c1.getParameters().size(), c2.getParameters().size());
        if (paramCountCompare != 0) {
          return paramCountCompare;
        }

        // 3. Compare parameter lists lexicographically
        for (int i = 0; i < c1.getParameters().size(); i++) {
          int cmp =
              PARAMETER_COMPARATOR.compare(c1.getParameters().get(i), c2.getParameters().get(i));
          if (cmp != 0) {
            return cmp;
          }
        }

        // If everything is the same, return 0
        return 0;
      };

  public static final Comparator<MethodInfo> METHOD_COMPARATOR =
      (m1, m2) -> {
        // 1. Compare by name
        int nameCompare = m1.getName().compareTo(m2.getName());
        if (nameCompare != 0) {
          return nameCompare;
        }

        // 2. Compare by return type
        int returnCompare = m1.getReturnType().compareTo(m2.getReturnType());
        if (returnCompare != 0) {
          return returnCompare;
        }

        // 3. Compare number of parameters
        int paramCountCompare =
            Integer.compare(m1.getParameters().size(), m2.getParameters().size());
        if (paramCountCompare != 0) {
          return paramCountCompare;
        }

        // 4. Compare parameter lists lexicographically
        for (int i = 0; i < m1.getParameters().size(); i++) {
          int cmp =
              PARAMETER_COMPARATOR.compare(m1.getParameters().get(i), m2.getParameters().get(i));
          if (cmp != 0) {
            return cmp;
          }
        }

        // If everything is the same, return 0
        return 0;
      };

  public static final Comparator<FieldInfo> FIELD_COMPARATOR =
      Comparator
          // First compare by name
          .comparing(FieldInfo::getName)
          // Then compare by type
          .thenComparing(FieldInfo::getType, Comparator.nullsFirst(String::compareTo));

  public static class ClassInfo {
    String className;
    String simpleName;
    String packageName;
    String majorVersion;
    String minorVersion;
    String sourceFile;
    String superclass;
    boolean isArrayClass;
    List<String> interfaces;
    List<String> superclasses;
    List<String> subclasses;
    int modifiers;
    List<ConstructorInfo> constructors = new ArrayList<>();
    List<MethodInfo> methods = new ArrayList<>();
    List<FieldInfo> fields = new ArrayList<>();
    List<UUID> peerIds = new ArrayList<>();

    public String getClassName() {
      return className;
    }

    public List<ConstructorInfo> getConstructors() {
      return constructors;
    }

    public List<FieldInfo> getFields() {
      return fields;
    }

    public void setClassName(String className) {
      this.className = className;
    }

    public void setConstructors(List<ConstructorInfo> constructors) {
      this.constructors = constructors;
    }

    public void setFields(List<FieldInfo> fields) {
      this.fields = fields;
    }

    public void setMethods(List<MethodInfo> methods) {
      this.methods = methods;
    }

    public List<MethodInfo> getMethods() {
      return methods;
    }

    public int getModifiers() {
      return modifiers;
    }

    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    public List<UUID> getPeerIds() {
      return peerIds;
    }

    public void setPeerIds(List<UUID> peerIds) {
      this.peerIds = peerIds;
    }

    public boolean isArrayClass() {
      return isArrayClass;
    }

    public void setArrayClass(boolean arrayClass) {
      isArrayClass = arrayClass;
    }

    public String getMajorVersion() {
      return majorVersion;
    }

    public void setMajorVersion(String majorVersion) {
      this.majorVersion = majorVersion;
    }

    public String getMinorVersion() {
      return minorVersion;
    }

    public void setMinorVersion(String minorVersion) {
      this.minorVersion = minorVersion;
    }

    public String getPackageName() {
      return packageName;
    }

    public void setPackageName(String packageName) {
      this.packageName = packageName;
    }

    public String getSimpleName() {
      return simpleName;
    }

    public void setSimpleName(String simpleName) {
      this.simpleName = simpleName;
    }

    public String getSourceFile() {
      return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
      this.sourceFile = sourceFile;
    }

    public String getSuperclass() {
      return superclass;
    }

    public void setSuperclass(String superclass) {
      this.superclass = superclass;
    }

    public List<String> getInterfaces() {
      return interfaces;
    }

    public void setInterfaces(List<String> interfaces) {
      this.interfaces = interfaces;
    }

    public List<String> getSubclasses() {
      return subclasses;
    }

    public void setSubclasses(List<String> subclasses) {
      this.subclasses = subclasses;
    }

    public List<String> getSuperclasses() {
      return superclasses;
    }

    public void setSuperclasses(List<String> superclasses) {
      this.superclasses = superclasses;
    }

    @Override
    public String toString() {
      return "ClassInfo{"
          + "className='"
          + className
          + '\''
          + ", simpleName='"
          + simpleName
          + '\''
          + ", package='"
          + packageName
          + '\''
          + ", package='"
          + packageName
          + '\''
          + ", majorVersion="
          + majorVersion
          + ", minorVersion="
          + minorVersion
          + ", sourceFile='"
          + sourceFile
          + '\''
          + ", modifiers="
          + Modifier.toString(modifiers)
          + "superclass='"
          + superclass
          + '\''
          + ", superclasses="
          + superclasses
          + ", interfaces="
          + interfaces
          + ", subclasses="
          + subclasses
          + ", constructors="
          + constructors
          + ", methods="
          + methods
          + ", fields="
          + fields
          + ", peerIds="
          + peerIds
          + '}';
    }
  }

  public static class ConstructorInfo {
    String name;
    int modifiers;
    List<ParameterInfo> parameters = new ArrayList<>();

    public int getModifiers() {
      return modifiers;
    }

    public String getName() {
      return name;
    }

    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setParameters(List<ParameterInfo> parameters) {
      this.parameters = parameters;
    }

    public List<ParameterInfo> getParameters() {
      return parameters;
    }

    @Override
    public String toString() {
      return "ConstructorInfo{"
          + "modifiers="
          + Modifier.toString(modifiers)
          + ", name='"
          + name
          + '\''
          + ", parameters="
          + parameters
          + '}';
    }
  }

  public static class MethodInfo {
    String name;
    int modifiers;
    String returnType;
    List<ParameterInfo> parameters = new ArrayList<>();

    public int getModifiers() {
      return modifiers;
    }

    public String getName() {
      return name;
    }

    public List<ParameterInfo> getParameters() {
      return parameters;
    }

    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setParameters(List<ParameterInfo> parameters) {
      this.parameters = parameters;
    }

    public void setReturnType(String returnType) {
      this.returnType = returnType;
    }

    public String getReturnType() {
      return returnType;
    }

    @Override
    public String toString() {
      return "MethodInfo{"
          + "modifiers="
          + Modifier.toString(modifiers)
          + ", name='"
          + name
          + '\''
          + ", returnType='"
          + returnType
          + '\''
          + ", parameters="
          + parameters
          + '}';
    }
  }

  public static class FieldInfo {
    String name;
    int modifiers;
    String type;

    public int getModifiers() {
      return modifiers;
    }

    public String getName() {
      return name;
    }

    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }

    @Override
    public String toString() {
      return "FieldInfo{"
          + "modifiers="
          + Modifier.toString(modifiers)
          + ", name='"
          + name
          + '\''
          + ", type='"
          + type
          + '\''
          + '}';
    }
  }

  public static class ParameterInfo {
    String name;
    String type;
    int modifiers;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }

    public int getModifiers() {
      return modifiers;
    }

    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    @Override
    public String toString() {
      return "ParameterInfo{"
          + "modifiers="
          + Modifier.toString(modifiers)
          + ", name='"
          + name
          + '\''
          + ", type='"
          + type
          + '\''
          + '}';
    }
  }

  /**
   * Computes a stable MD5 signature for the ClassInfo object. This provides a unique, deterministic
   * signature for the given class metadata.
   */
  public static String computeMD5Sum(ClassInfo classInfo) {
    try {
      // 1. Sort the lists of constructors, methods, and fields
      List<ConstructorInfo> sortedConstructors =
          classInfo.getConstructors().stream().sorted(CONSTRUCTOR_COMPARATOR).toList();

      List<MethodInfo> sortedMethods =
          classInfo.getMethods().stream().sorted(METHOD_COMPARATOR).toList();

      List<FieldInfo> sortedFields =
          classInfo.getFields().stream().sorted(FIELD_COMPARATOR).toList();

      // 2. Sort the class-level lists (interfaces, superclasses, subclasses)
      List<String> sortedInterfaces = new ArrayList<>();
      if (classInfo.getInterfaces() != null) {
        sortedInterfaces = classInfo.getInterfaces().stream().sorted().toList();
      }

      List<String> sortedSuperclasses = new ArrayList<>();
      if (classInfo.getSuperclasses() != null) {
        sortedSuperclasses = classInfo.getSuperclasses().stream().sorted().toList();
      }

      List<String> sortedSubclasses = new ArrayList<>();
      if (classInfo.getSubclasses() != null) {
        sortedSubclasses = classInfo.getSubclasses().stream().sorted().toList();
      }

      // 3. Create signatures for constructors, methods, and fields
      // Constructor signatures
      String ctorSigs =
          sortedConstructors.stream()
              .map(c -> c.getName() + ":" + concatParamTypes(c.getParameters()))
              .collect(Collectors.joining(";"));

      // Method signatures
      String methodSigs =
          sortedMethods.stream()
              .map(
                  m ->
                      m.getName()
                          + ":"
                          + m.getReturnType()
                          + ":"
                          + concatParamTypes(m.getParameters()))
              .collect(Collectors.joining(";"));

      // Field signatures
      String fieldSigs =
          sortedFields.stream()
              .map(f -> f.getName() + ":" + f.getType())
              .collect(Collectors.joining(";"));

      // 4. Flatten the sorted interface, superclasses, and subclasses lists into single strings
      String interfacesSig = String.join(",", sortedInterfaces);
      String superclassesSig = String.join(",", sortedSuperclasses);
      String subclassesSig = String.join(",", sortedSubclasses);

      // 5. Combine all fields (including new ones) into a single string for hashing
      String combined =
          String.join(
              "|",
              safeString(classInfo.getClassName()),
              safeString(classInfo.getSimpleName()),
              safeString(classInfo.getPackageName()),
              safeString(classInfo.getMajorVersion()),
              safeString(classInfo.getMinorVersion()),
              safeString(classInfo.getSourceFile()),
              safeString(classInfo.getSuperclass()),
              String.valueOf(classInfo.isArrayClass()),
              interfacesSig,
              superclassesSig,
              subclassesSig,
              methodSigs,
              fieldSigs,
              ctorSigs);

      // 6. Compute the MD5 hash of the combined string
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] hashBytes = md.digest(combined.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hashBytes);

    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("MD5 algorithm not found", e);
    }
  }

  /** Null-safe string method to avoid `NullPointerException` if any field is null. */
  private static String safeString(String value) {
    return value == null ? "" : value;
  }

  /** Helper method to join parameter types with commas. */
  public static String concatParamTypes(List<ParameterInfo> params) {
    return params.stream().map(ParameterInfo::getType).collect(Collectors.joining(","));
  }

  /** Convert bytes to a hex string. */
  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}

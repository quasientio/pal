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

package com.quasient.pal.common.lang;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents class metadata including constructors, methods, fields, and hierarchy relationships.
 * Provides comparators for consistent ordering of metadata elements and utilities for generating
 * deterministic signatures of class structures.
 */
@SuppressWarnings("unused")
public class Metadata {

  /**
   * Compares {@code ParameterInfo} objects by type first, then by name with null names first. Used
   * to establish consistent parameter ordering for method/constructor comparisons.
   */
  private static final Comparator<ParameterInfo> PARAMETER_COMPARATOR =
      Comparator.comparing(ParameterInfo::getType)
          .thenComparing(ParameterInfo::getName, Comparator.nullsFirst(String::compareTo));

  /**
   * Compares {@code ConstructorInfo} objects by name, parameter count, and parameter types. Used to
   * generate deterministic constructor ordering for signature calculations.
   */
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

  /**
   * Compares {@code MethodInfo} objects by name, return type, parameter count, parameter types, and
   * signature string. Ensures consistent ordering for method signature generation.
   */
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

        // 5. Compare signatures
        if (m1.getSignature() != null && !m1.getSignature().isBlank()) {
          int sigCompare = m1.getSignature().compareTo(m2.getSignature());
          if (sigCompare != 0) {
            return sigCompare;
          }
        }

        // If everything is the same, return 0
        return 0;
      };

  /**
   * Compares {@code FieldInfo} objects by name first, then by type with null types first. Maintains
   * consistent field ordering for signature calculations.
   */
  public static final Comparator<FieldInfo> FIELD_COMPARATOR =
      Comparator
          // First compare by name
          .comparing(FieldInfo::getName)
          // Then compare by type
          .thenComparing(FieldInfo::getType, Comparator.nullsFirst(String::compareTo));

  /**
   * Contains metadata about a Java class including its structure, hierarchy, and relationships.
   * Used to represent class information in serialized form (JSON) for static analysis.
   */
  public static class ClassInfo {
    /** Fully qualified class name */
    String className;

    /** Simple class name without package */
    String simpleName;

    /** Package name from class package declaration */
    String packageName;

    /** Major version number from class file format */
    String majorVersion;

    /** Minor version number from class file format */
    String minorVersion;

    /** Source file name from debug information */
    String sourceFile;

    /** Immediate superclass name */
    String superclass;

    /** Indicates if this class represents an array type */
    boolean isArrayClass;

    /** Implemented interfaces */
    List<String> interfaces;

    /** All ancestor classes in inheritance hierarchy */
    List<String> superclasses;

    /** Known direct subclasses */
    List<String> subclasses;

    /** Java modifiers bitmask (see {@link Modifier}) */
    int modifiers;

    /** Constructors declared in the class */
    List<ConstructorInfo> constructors = new ArrayList<>();

    /** Methods declared in or inherited by the class */
    List<MethodInfo> methods = new ArrayList<>();

    /** Fields declared in or inherited by the class */
    List<FieldInfo> fields = new ArrayList<>();

    /** Unique identifiers for associated peer objects */
    List<UUID> peerIds = new ArrayList<>();

    /**
     * Returns the fully qualified class name.
     *
     * @return Fully qualified class name
     */
    public String getClassName() {
      return className;
    }

    /**
     * Returns the list of constructors.
     *
     * @return List of constructors. Can be sorted using {@link Metadata#CONSTRUCTOR_COMPARATOR}
     */
    public List<ConstructorInfo> getConstructors() {
      return constructors;
    }

    /**
     * Returns the list of fields.
     *
     * @return List of fields. Can be sorted using {@link Metadata#FIELD_COMPARATOR}
     */
    public List<FieldInfo> getFields() {
      return fields;
    }

    /**
     * Assigns the class name.
     *
     * @param className Fully qualified class name
     */
    public void setClassName(String className) {
      this.className = className;
    }

    /**
     * Assigns the constructors list.
     *
     * @param constructors New list of constructors
     */
    public void setConstructors(List<ConstructorInfo> constructors) {
      this.constructors = constructors;
    }

    /**
     * Assigns the fields list.
     *
     * @param fields New list of fields
     */
    public void setFields(List<FieldInfo> fields) {
      this.fields = fields;
    }

    /**
     * Assigns the methods list.
     *
     * @param methods New list of methods
     */
    public void setMethods(List<MethodInfo> methods) {
      this.methods = methods;
    }

    /**
     * Returns the list of methods.
     *
     * @return List of methods. Can be sorted using {@link Metadata#METHOD_COMPARATOR}
     */
    public List<MethodInfo> getMethods() {
      return methods;
    }

    /**
     * Returns the modifiers bitmask representing class visibility and characteristics.
     *
     * @return Modifiers bitmask.
     * @see Modifier
     */
    public int getModifiers() {
      return modifiers;
    }

    /**
     * Sets the modifiers bitmask.
     *
     * @param modifiers Valid modifiers bitmask (see {@link Modifier})
     */
    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    /**
     * Returns the list of associated peer UUIDs to which this class is known.
     *
     * @return List of associated peer UUIDs
     */
    public List<UUID> getPeerIds() {
      return peerIds;
    }

    /**
     * Assigns the peer Ids list.
     *
     * @param peerIds New list of associated peer UUIDs
     */
    public void setPeerIds(List<UUID> peerIds) {
      this.peerIds = peerIds;
    }

    /**
     * Returns {@code true} if this class represents an array type.
     *
     * @return {@code true} if this class represents an array type
     */
    public boolean isArrayClass() {
      return isArrayClass;
    }

    /**
     * Assigns arrayClass to true if this class is an array type.
     *
     * @param arrayClass Set to {@code true} for array types
     */
    public void setArrayClass(boolean arrayClass) {
      isArrayClass = arrayClass;
    }

    /**
     * Returns the major version number.
     *
     * @return Major version number
     */
    public String getMajorVersion() {
      return majorVersion;
    }

    /**
     * Assigns the major version number.
     *
     * @param majorVersion Valid major version number
     */
    public void setMajorVersion(String majorVersion) {
      this.majorVersion = majorVersion;
    }

    /**
     * Returns the minor version number.
     *
     * @return Minor version number
     */
    public String getMinorVersion() {
      return minorVersion;
    }

    /**
     * Assigns the minor version number.
     *
     * @param minorVersion Valid minor version number
     */
    public void setMinorVersion(String minorVersion) {
      this.minorVersion = minorVersion;
    }

    /**
     * Returns the package name
     *
     * @return Package name
     */
    public String getPackageName() {
      return packageName;
    }

    /**
     * Sets the packageName.
     *
     * @param packageName Package name
     */
    public void setPackageName(String packageName) {
      this.packageName = packageName;
    }

    /**
     * Returns the simple class name without package qualifiers.
     *
     * @return Simple class name
     */
    public String getSimpleName() {
      return simpleName;
    }

    /**
     * Assigns the simple class name.
     *
     * @param simpleName Non-null simple class name
     */
    public void setSimpleName(String simpleName) {
      this.simpleName = simpleName;
    }

    /**
     * Returns the source file name as reported in debug information.
     *
     * @return Source file name
     */
    public String getSourceFile() {
      return sourceFile;
    }

    /**
     * Sets the source file name for this class.
     *
     * @param sourceFile Original source file name
     */
    public void setSourceFile(String sourceFile) {
      this.sourceFile = sourceFile;
    }

    /**
     * Returns the immediate superclass name.
     *
     * @return Immediate superclass name
     */
    public String getSuperclass() {
      return superclass;
    }

    /**
     * Assigns the name of the immediate superclass .
     *
     * @param superclass Superclass name
     */
    public void setSuperclass(String superclass) {
      this.superclass = superclass;
    }

    /**
     * Returns the list of implemented interface names.
     *
     * @return List of implemented interface names
     */
    public List<String> getInterfaces() {
      return interfaces;
    }

    /**
     * Assigns the list of implemented interface names.
     *
     * @param interfaces New list of interface names
     */
    public void setInterfaces(List<String> interfaces) {
      this.interfaces = interfaces;
    }

    /**
     * Returns the list of known direct subclass names.
     *
     * @return List of known direct subclass names
     */
    public List<String> getSubclasses() {
      return subclasses;
    }

    /**
     * Assigns the list of known direct subclasses.
     *
     * @param subclasses New list of subclass names
     */
    public void setSubclasses(List<String> subclasses) {
      this.subclasses = subclasses;
    }

    /**
     * Returns the list of all superclass names in the hierarchy.
     *
     * @return List of all superclass names
     */
    public List<String> getSuperclasses() {
      return superclasses;
    }

    /**
     * Assigns the list of superclasses.
     *
     * @param superclasses New list of superclass names
     */
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

  /** Represents metadata about a constructor declaration */
  public static class ConstructorInfo {
    /** Constructor name (matches declaring class simple name) */
    String name;

    /** Modifiers bitmask (see {@link Modifier}) */
    int modifiers;

    /** Ordered list of constructor parameters */
    List<ParameterInfo> parameters = new ArrayList<>();

    /**
     * Returns the modifiers bitmask representing constructor visibility and characteristics.
     *
     * @return Modifiers bitmask
     */
    public int getModifiers() {
      return modifiers;
    }

    /**
     * Returns the constructor name.
     *
     * @return Constructor name matching declaring class's simple name
     */
    public String getName() {
      return name;
    }

    /**
     * Assigns the modifiers bitmask. See {@link Modifier}
     *
     * @param modifiers Valid modifiers bitmask
     */
    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    /**
     * Assigns the name, matching the class simple name.
     *
     * @param name Must match declaring class's simple name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Assigns the ordered list of parameters.
     *
     * @param parameters New list of parameters
     */
    public void setParameters(List<ParameterInfo> parameters) {
      this.parameters = parameters;
    }

    /**
     * Returns the list of parameters.
     *
     * @return List of parameters in declaration order
     */
    public List<ParameterInfo> getParameters() {
      return parameters;
    }

    @Override
    public String toString() {
      return "ConstructorInfo{"
          + "name='"
          + name
          + '\''
          + ", modifiers='"
          + Modifier.toString(modifiers)
          + '\''
          + ", parameters="
          + parameters
          + '}';
    }
  }

  /** Represents metadata about a method declaration or inheritance */
  public static class MethodInfo {
    /** Method name as declared in source code */
    String name;

    /** Modifiers bitmask (see {@link Modifier}) */
    int modifiers;

    /** Indicates static method declaration */
    boolean isStatic;

    /** Class name where this method was originally declared */
    String inheritedFrom;

    /** Indicates override of superclass method */
    boolean overridden;

    /** Return type */
    String returnType;

    /** Method signature */
    String signature;

    /** Ordered list of method parameters */
    List<ParameterInfo> parameters = new ArrayList<>();

    /**
     * Returns the modifiers bitmask representing method visibility and characteristics.
     *
     * @return Modifiers bitmask
     */
    public int getModifiers() {
      return modifiers;
    }

    /**
     * Returns the method name as declared in source code.
     *
     * @return Method name
     */
    public String getName() {
      return name;
    }

    /**
     * Returns the list of parameters in declaration order.
     *
     * @return List of parameters
     */
    public List<ParameterInfo> getParameters() {
      return parameters;
    }

    /**
     * Sets the modifiers bitmask.
     *
     * @param modifiers Valid modifiers bitmask (see {@link Modifier})
     */
    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    /**
     * Assigns the method name.
     *
     * @param name Non-null method name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Assigns the list of parameters in order of declaration.
     *
     * @param parameters New list of parameters
     */
    public void setParameters(List<ParameterInfo> parameters) {
      this.parameters = parameters;
    }

    /**
     * Sets the return type.
     *
     * @param returnType Valid type descriptor
     */
    public void setReturnType(String returnType) {
      this.returnType = returnType;
    }

    /**
     * Returns the method return type descriptor.
     *
     * @return Method return type
     */
    public String getReturnType() {
      return returnType;
    }

    /**
     * Returns the method signature.
     *
     * @return Method signature string
     */
    public String getSignature() {
      return signature;
    }

    /**
     * Sets the signature for this method.
     *
     * @param signature Valid method signature
     */
    public void setSignature(String signature) {
      this.signature = signature;
    }

    /**
     * Returns whether this is a static method.
     *
     * @return {@code true} if this is a static method
     */
    public boolean isStatic() {
      return isStatic;
    }

    /**
     * Sets isStatic to true if this method is static.
     *
     * @param aStatic Set to {@code true} for static methods
     */
    public void setStatic(boolean aStatic) {
      isStatic = aStatic;
    }

    /**
     * Returns the class name where this method was originally declared.
     *
     * @return Class name where this method was declared
     */
    public String getInheritedFrom() {
      return inheritedFrom;
    }

    /**
     * Assigns the name of the class where this method was originally declared.
     *
     * @param inheritedFrom Original declaring class name
     */
    public void setInheritedFrom(String inheritedFrom) {
      this.inheritedFrom = inheritedFrom;
    }

    /**
     * Returns true if this method is an overridden implementation.
     *
     * @return {@code true} if this method overrides a superclass method
     */
    public boolean isOverridden() {
      return overridden;
    }

    /**
     * Sets overridden to true if this method is an overridden implementation.
     *
     * @param overridden Set to {@code true} for override methods
     */
    public void setOverridden(boolean overridden) {
      this.overridden = overridden;
    }

    @Override
    public String toString() {
      return "MethodInfo{"
          + "name='"
          + name
          + '\''
          + ", parameters="
          + parameters
          + ", returnType='"
          + returnType
          + '\''
          + ", modifiers='"
          + Modifier.toString(modifiers)
          + '\''
          + ", inheritedFrom='"
          + inheritedFrom
          + '\''
          + ", overridden="
          + overridden
          + ", signature='"
          + signature
          + '\''
          + '}';
    }
  }

  /** Represents metadata about a field declaration or inheritance */
  public static class FieldInfo {
    /** Field name as declared in source code */
    String name;

    /** Modifiers bitmask (see {@link Modifier}) */
    int modifiers;

    /** Indicates static field declaration */
    boolean isStatic;

    /** Class name where this field was originally declared */
    String inheritedFrom;

    /** Indicates shadowing of superclass field */
    boolean overridden;

    /** Field type descriptor */
    String type;

    /**
     * Returns the modifiers bitmask representing field visibility and characteristics.
     *
     * @return Modifiers bitmask
     */
    public int getModifiers() {
      return modifiers;
    }

    /**
     * Returns the name of this field as declared in source code.
     *
     * @return Field name as declared in source code
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the modifiers bitmask value.
     *
     * @param modifiers Valid modifiers bitmask (see {@link Modifier})
     */
    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    /**
     * Sets the name of this field.
     *
     * @param name Non-null field name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Assigns the type descriptor of this field.
     *
     * @param type Valid type descriptor
     */
    public void setType(String type) {
      this.type = type;
    }

    /**
     * Returns the type descriptor of this field.
     *
     * @return Field type descriptor
     */
    public String getType() {
      return type;
    }

    /**
     * Returns whether this field is static.
     *
     * @return {@code true} if this is a static field
     */
    public boolean isStatic() {
      return isStatic;
    }

    /**
     * Sets isStatic to true if this field is static.
     *
     * @param aStatic Set to {@code true} for static fields
     */
    public void setStatic(boolean aStatic) {
      isStatic = aStatic;
    }

    /**
     * Returns the class name where this field was originally declared.
     *
     * @return Class name where this field was declared
     */
    public String getInheritedFrom() {
      return inheritedFrom;
    }

    /**
     * Assigns the name of the class where this field was originally declared.
     *
     * @param inheritedFrom Original declaring class name
     */
    public void setInheritedFrom(String inheritedFrom) {
      this.inheritedFrom = inheritedFrom;
    }

    /**
     * Returns whether this field shadows a superclass field.
     *
     * @return {@code true} if this field shadows a superclass field
     */
    public boolean isOverridden() {
      return overridden;
    }

    /**
     * Assigns the overridden flag indicating whether this field shadows a superclass field.
     *
     * @param overridden Set to {@code true} for shadowing fields
     */
    public void setOverridden(boolean overridden) {
      this.overridden = overridden;
    }

    @Override
    public String toString() {
      return "FieldInfo{"
          + "name='"
          + name
          + '\''
          + ", type='"
          + type
          + '\''
          + ", modifiers='"
          + Modifier.toString(modifiers)
          + '\''
          + ", inheritedFrom='"
          + inheritedFrom
          + '\''
          + ", overridden="
          + overridden
          + '}';
    }
  }

  /** Represents method/constructor parameter metadata */
  public static class ParameterInfo {
    /** Parameter name from debug information (may be null) */
    String name;

    /** Parameter type descriptor */
    String type;

    /** Modifiers bitmask (see {@link Modifier}) */
    int modifiers;

    /**
     * Returns the parameter name if available from debug info, otherwise null.
     *
     * @return Parameter name if available
     */
    public String getName() {
      return name;
    }

    /**
     * Assigns the parameter name when known.
     *
     * @param name Parameter name (may be null)
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Assign the type descriptor for this parameter.
     *
     * @param type Valid type descriptor
     */
    public void setType(String type) {
      this.type = type;
    }

    /**
     * Returns the parameter type descriptor.
     *
     * @return Parameter type descriptor
     */
    public String getType() {
      return type;
    }

    /**
     * Returns the modifiers bitmask (typically 0 for parameters).
     *
     * @return Modifiers bitmask (typically 0 for parameters)
     */
    public int getModifiers() {
      return modifiers;
    }

    /**
     * Assigns the modifiers bitmask for this parameter.
     *
     * @param modifiers Valid modifiers bitmask (see {@link Modifier})
     */
    public void setModifiers(int modifiers) {
      this.modifiers = modifiers;
    }

    @Override
    public String toString() {
      return "ParameterInfo{"
          + "name='"
          + name
          + '\''
          + ", type='"
          + type
          + '\''
          + ", modifiers='"
          + Modifier.toString(modifiers)
          + '\''
          + '}';
    }
  }

  /**
   * Computes a stable MD5 signature for the ClassInfo object. This provides a unique, deterministic
   * signature for the given class metadata.
   *
   * @param classInfo Class metadata to process (must not be null)
   * @return 32-character lowercase hexadecimal MD5 digest string
   * @throws RuntimeException If MD5 algorithm is unavailable
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

  /**
   * Converts null strings to empty strings to prevent null values in signature calculations.
   *
   * @param value Input string (may be null)
   * @return Empty string for null input, original value otherwise
   */
  private static String safeString(String value) {
    return value == null ? "" : value;
  }

  /**
   * Concatenates parameter types into a comma-separated string for signature generation.
   *
   * @param params List of parameters (may be empty)
   * @return Comma-separated list of parameter type descriptors
   */
  public static String concatParamTypes(List<ParameterInfo> params) {
    return params.stream().map(ParameterInfo::getType).collect(Collectors.joining(","));
  }

  /**
   * Converts byte array to lowercase hexadecimal string representation.
   *
   * @param bytes Binary data to convert (length 16 for MD5)
   * @return 32-character lowercase hex string
   */
  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}

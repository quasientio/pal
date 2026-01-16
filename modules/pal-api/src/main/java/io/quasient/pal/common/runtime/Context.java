/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.runtime;

import io.quasient.pal.common.lang.reflect.ConstructorSignature;
import io.quasient.pal.common.lang.reflect.FieldSignature;
import io.quasient.pal.common.lang.reflect.MethodSignature;
import io.quasient.pal.common.lang.reflect.Params;
import io.quasient.pal.common.lang.reflect.Signature;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.aspectj.lang.JoinPoint;

/**
 * Represents the contextual information extracted from an AspectJ {@link JoinPoint.StaticPart}.
 * This includes details such as the source file name, line number, the class within which the join
 * point occurs, and the signature of the method, constructor, or field involved.
 *
 * <p>This class allows the reconstruction of execution contexts with additional metadata.
 *
 * @see Signature
 */
@SuppressWarnings("rawtypes")
public final class Context {

  /**
   * The name of the source file where the join point is located. This field is non-null and
   * identifies the filename containing the relevant code.
   */
  @Nonnull private final String sourceFilename;

  /**
   * The line number in the source file where the join point occurs. This integer represents the
   * exact line in the source code.
   */
  private final int sourceLine;

  /**
   * The class within which the join point is defined. This field is non-null and references the
   * enclosing class type.
   */
  @Nonnull private final Class withinType;

  /**
   * The signature of the join point, encapsulating method, constructor, or field details. This
   * field is non-null and provides comprehensive information about the executable element.
   */
  @Nonnull private final Signature signature;

  /**
   * Constructs a new {@code Context} instance with the specified details.
   *
   * @param sourceFilename the name of the source file; must not be null
   * @param sourceLine the line number in the source file
   * @param withinType the class within which the join point is defined; must not be null
   * @param signature the signature of the join point; must not be null
   * @throws NullPointerException if any of the non-null parameters are null
   */
  public Context(
      @Nonnull String sourceFilename,
      int sourceLine,
      @Nonnull Class withinType,
      @Nonnull Signature signature) {
    this.sourceFilename = Objects.requireNonNull(sourceFilename);
    this.sourceLine = sourceLine;
    this.withinType = Objects.requireNonNull(withinType);
    this.signature = Objects.requireNonNull(signature);
  }

  /**
   * Retrieves the name of the source file where the join point is located.
   *
   * @return the non-null source filename
   */
  @Nonnull
  public String getSourceFilename() {
    return sourceFilename;
  }

  /**
   * Retrieves the line number in the source file where the join point occurs.
   *
   * @return the source line number
   */
  public int getSourceLine() {
    return sourceLine;
  }

  /**
   * Retrieves the class within which the join point is defined.
   *
   * @return the non-null class type
   */
  @Nonnull
  public Class getWithinType() {
    return withinType;
  }

  /**
   * Retrieves the signature of the join point, which includes details about the method,
   * constructor, or field.
   *
   * @return the non-null signature
   */
  @Nonnull
  public Signature getSignature() {
    return signature;
  }

  /**
   * Parses a {@link JoinPoint.StaticPart} to create a corresponding {@code Context} instance.
   * Extracts relevant information such as source filename, line number, enclosing class, and
   * signature details.
   *
   * @param staticPart the static part of the join point to parse; must not be null
   * @return a {@code Context} instance representing the parsed join point
   * @throws IllegalArgumentException if the signature type is unsupported
   * @throws NullPointerException if any required information from {@code staticPart} is null
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public static Context parseFrom(final JoinPoint.StaticPart staticPart) {
    final String filename = staticPart.getSourceLocation().getFileName();
    final int sourceLine = staticPart.getSourceLocation().getLine();
    final Class withinType = staticPart.getSourceLocation().getWithinType();

    org.aspectj.lang.Signature ajSig = staticPart.getSignature();

    // extract common Signature fields
    final Class declaringType = ajSig.getDeclaringType();
    final String declaringTypeName = ajSig.getDeclaringTypeName();
    final int modifiers = ajSig.getModifiers();
    final String name = ajSig.getName();

    // extract common CodeSignature fields
    if (ajSig instanceof org.aspectj.lang.reflect.CodeSignature ajCodeSig) {

      final Class[] exceptionTypes = ajCodeSig.getExceptionTypes();
      final String[] parameterNames = ajCodeSig.getParameterNames();
      final Class[] parameterTypes = ajCodeSig.getParameterTypes();

      // pull out specific fields of MethodSignature
      if (ajSig instanceof org.aspectj.lang.reflect.MethodSignature ajMethodSig) {
        return new Context(
            filename,
            sourceLine,
            withinType,
            new MethodSignature(
                declaringType,
                declaringTypeName,
                modifiers,
                name,
                exceptionTypes,
                new Params(parameterNames, parameterTypes, ajMethodSig.getMethod().getParameters()),
                ajMethodSig.getMethod(),
                ajMethodSig.getReturnType()));
      }

      // pull out specific fields of ConstructorSignature
      if (ajSig instanceof org.aspectj.lang.reflect.ConstructorSignature ajConsSig) {
        return new Context(
            filename,
            sourceLine,
            withinType,
            new ConstructorSignature(
                declaringType,
                declaringTypeName,
                modifiers,
                name,
                exceptionTypes,
                new Params(
                    parameterNames, parameterTypes, ajConsSig.getConstructor().getParameters()),
                ajConsSig.getConstructor()));
      }
    }

    // pull out specific fields of FieldSignature
    if (ajSig instanceof org.aspectj.lang.reflect.FieldSignature ajFieldSig) {
      return new Context(
          filename,
          sourceLine,
          withinType,
          new FieldSignature(
              declaringType,
              declaringTypeName,
              modifiers,
              name,
              ajFieldSig.getField(),
              ajFieldSig.getFieldType()));
    }

    throw new IllegalArgumentException(
        "Cannot handle signature of type: " + ajSig.getClass().getName());
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Context context = (Context) o;
    return sourceLine == context.sourceLine
        && sourceFilename.equals(context.sourceFilename)
        && withinType.equals(context.withinType)
        && signature.equals(context.signature);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(sourceFilename, sourceLine, withinType, signature);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "Context{"
        + "sourceFilename='"
        + sourceFilename
        + '\''
        + ", sourceLine="
        + sourceLine
        + ", withinType="
        + withinType
        + ", signature="
        + signature
        + '}';
  }
}

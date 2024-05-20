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

package net.ittera.pal.common.runtime;

import java.util.Objects;
import javax.annotation.Nonnull;
import net.ittera.pal.common.lang.reflect.ConstructorSignature;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.lang.reflect.Params;
import net.ittera.pal.common.lang.reflect.Signature;
import org.aspectj.lang.JoinPoint;

/**
 * This class and the ones under .reflect subpackage partly hold info that will be extracted from
 * aspectj's JoinPoint.StaticPart, allowing us to construct instances for unit-testing, and adding
 * more useful contextual information.
 */
@SuppressWarnings("rawtypes")
public final class Context {

  @Nonnull private final String sourceFilename;
  private final int sourceLine;
  @Nonnull private final Class withinType;
  @Nonnull private final Signature signature;

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

  @Nonnull
  public String getSourceFilename() {
    return sourceFilename;
  }

  public int getSourceLine() {
    return sourceLine;
  }

  @Nonnull
  public Class getWithinType() {
    return withinType;
  }

  @Nonnull
  public Signature getSignature() {
    return signature;
  }

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

  @Override
  public int hashCode() {
    return Objects.hash(sourceFilename, sourceLine, withinType, signature);
  }

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

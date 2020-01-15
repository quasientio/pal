package net.ittera.pal.common.lang.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For Ant-path matching:
 * https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Before {
  String clazz();

  String method() default "";

  String[] parameterTypes() default {};

  String field() default "";

  String fieldOpType() default "";
}

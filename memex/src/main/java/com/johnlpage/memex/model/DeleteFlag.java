package com.johnlpage.memex.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD) // This annotation can be applied to fields
@Retention(RetentionPolicy.RUNTIME) // The annotation is available at runtime
public @interface DeleteFlag {
  // An example element with a default value
  String value() default "";
}

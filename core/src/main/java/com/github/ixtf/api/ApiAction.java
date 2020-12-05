package com.github.ixtf.api;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiAction {
    String service();

    String action();
}

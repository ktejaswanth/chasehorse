package com.chasehorse.erp.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.chasehorse.erp.config.SecurityConfig;
import com.chasehorse.erp.config.WebConfig;
import com.chasehorse.erp.web.GlobalExceptionHandler;
import com.chasehorse.erp.web.ProblemWriter;
import com.chasehorse.erp.web.RequestIdFilter;

/** Brings the real security configuration, actor resolver and error handling into a @WebMvcTest slice. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({SecurityConfig.class, WebConfig.class, ProblemWriter.class, GlobalExceptionHandler.class, RequestIdFilter.class})
public @interface ApiWebTest {
}

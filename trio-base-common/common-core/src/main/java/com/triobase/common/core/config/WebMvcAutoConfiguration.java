package com.triobase.common.core.config;

import com.triobase.common.core.result.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class WebMvcAutoConfiguration {
}

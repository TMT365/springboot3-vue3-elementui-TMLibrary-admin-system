package com.tmt.TMLibrary.common.utils;

import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class TimeCostAspect {
    @Around("execution(* com.tmt.TMLibrary.controller.*.*(..))")
    public Object logExecutionTime(@NonNull ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object proceed = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - start;
        // {} is a placeholder for the method signature and execution time
        // In java, we are use the String format method to format the log message, but in this case, we are using the log.info method which supports placeholders.
        // Java原生格式化，不是log的
        //String msg = String.format("%s executed in %d ms", joinPoint.getSignature(), executionTime);
        log.info("{} executed in {}ms", joinPoint.getSignature(), executionTime);
        return proceed;
    }
}

/*
 * @brief 这个类是一个切面类，用于记录方法的执行时间。它使用了Spring AOP的@Aspect注解来定义一个切面，并使用@Around注解来环绕通知指定的切点。
 * 什么时候被调用？ 在启动时，Spring会扫描所有的切面类，并在指定的切点处织入通知方法。在这个类中，切点是com.tmt.TMLibrary.controller包下的所有方法。
 * 当这些方法被调用时，logExecutionTime方法会被执行，它会记录方法的开始时间，调用目标方法，然后记录结束时间，并计算执行时间。最后，它会将方法签名和执行时间记录到日志中。
 * 本质是是生成了一个@Bean的代理类，代理类在调用目标方法前后执行通知方法。你在代码里面调用的目标方法，实际上是调用了代理类的方法，代理类的方法会在调用目标方法前后执行通知方法。
 * @author tmt
 * @version 1.0
 * @since 2026-08-14
 * TimeCostAspect
 */
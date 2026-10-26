package app.podgon.common

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Включает фоновые задачи (outbox писем, очистка просроченных данных). При нескольких инстансах понадобится ShedLock. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig

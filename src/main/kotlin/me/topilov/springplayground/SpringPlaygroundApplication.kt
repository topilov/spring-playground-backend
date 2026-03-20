package me.topilov.springplayground

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SpringPlaygroundApplication

fun main(args: Array<String>) {
    runApplication<SpringPlaygroundApplication>(*args)
}

package me.topilov.springplayground.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object TestObjectMapper {
    val instance: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()
}

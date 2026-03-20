package me.topilov.springplayground.publicapi

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public")
class PublicController {
    @GetMapping("/ping")
    fun ping(): Map<String, String> = mapOf("status" to "ok")
}

package me.topilov.springplayground.profile.exception

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class ProfileNotFoundException : ResponseStatusException(HttpStatus.NOT_FOUND, "Profile was not found")

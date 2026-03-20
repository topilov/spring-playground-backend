package me.topilov.springplayground.auth.exception

class PersistedAuthUserIdMissingException : IllegalStateException("Persisted user must have an id")

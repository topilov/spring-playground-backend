package me.topilov.springplayground.profile.exception

class PersistedProfileUserIdMissingException : IllegalStateException("Persisted user must have an id")

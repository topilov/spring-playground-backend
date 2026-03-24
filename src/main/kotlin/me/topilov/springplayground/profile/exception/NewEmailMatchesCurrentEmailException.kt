package me.topilov.springplayground.profile.exception

class NewEmailMatchesCurrentEmailException : RuntimeException("New email must be different from the current email")

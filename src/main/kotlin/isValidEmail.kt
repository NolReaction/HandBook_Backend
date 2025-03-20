package com.example

fun isValidEmail(email: String): Boolean {
    // Простой шаблон для валидации email
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
    return email.matches(emailRegex)
}
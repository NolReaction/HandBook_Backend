package com.example.functions

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// Структура для хранения количества попыток и времени последней попытки
data class LoginAttempt(val count: Int, val timestamp: Long)

// Хранилище попыток по IP-адресу (или можно использовать email)
val loginAttempts = ConcurrentHashMap<String, LoginAttempt>()

// Функция для обновления счетчика попыток для данного ключа (IP или email)
fun recordLoginAttempt(key: String) {
    val currentTime = System.currentTimeMillis()
    loginAttempts.compute(key) { _, attempt ->
        // Если нет предыдущих попыток или последний запрос был больше минуты назад – начинаем сначала
        if (attempt == null || currentTime - attempt.timestamp > TimeUnit.MINUTES.toMillis(1)) {
            LoginAttempt(1, currentTime)
        } else {
            LoginAttempt(attempt.count + 1, attempt.timestamp)
        }
    }
}

// Функция для проверки, превышен ли лимит попыток (например, 3 попытки за минуту)
fun isBlocked(key: String): Boolean {
    val attempt = loginAttempts[key]
    return attempt != null && attempt.count >= 3 && (System.currentTimeMillis() - attempt.timestamp) < TimeUnit.MINUTES.toMillis(1)
}


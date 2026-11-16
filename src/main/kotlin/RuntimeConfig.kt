package com.example

internal fun environmentValue(
    name: String,
    fallback: String? = null,
    environment: (String) -> String? = System::getenv
): String = environment(name)?.takeIf { it.isNotBlank() }
    ?: fallback
    ?: error("Environment variable $name must be set")

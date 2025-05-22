package com.example.functions

import org.xbill.DNS.Lookup
import org.xbill.DNS.Record
import org.xbill.DNS.Type

fun isEmailDeliverable(email: String): Boolean {
    val domain = email.substringAfter("@")
    val lookup = Lookup(domain, Type.MX)
    val records: Array<Record>? = lookup.run()
    return !records.isNullOrEmpty()
}

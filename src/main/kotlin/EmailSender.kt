package com.example

import java.util.Properties
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailSender {
    fun sendEmail(
        to: String,
        subject: String,
        body: String,
        from: String = "authhelper@mail.ru",
        password: String = "Eiiws0e7AQ14WtisuJYB"
    ) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            // Для SSL-соединения на порту 465:
            // возможно правильно put("mail.smtp.host", "ssl://smtp.mail.ru")
            put("mail.smtp.host", "smtp.mail.ru")
            put("mail.smtp.port", "465")
            put("mail.smtp.socketFactory.port", "465")
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            put("mail.smtp.socketFactory.fallback", "false")
            // Если вы хотите принудительно использовать SSL, то не включайте STARTTLS
            // put("mail.smtp.starttls.enable", "true") // не нужно для порта 465 с SSL
        }

        val session: Session = Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(from, password)
            }
        })

        try {
            val message: Message = MimeMessage(session).apply {
                setFrom(InternetAddress(from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject)
                setText(body)
            }

            Transport.send(message)
            println("Письмо отправлено успешно.")
        } catch (e: MessagingException) {
            throw RuntimeException(e)
        }
    }
}

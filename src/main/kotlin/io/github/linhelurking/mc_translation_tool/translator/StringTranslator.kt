package io.github.linhelurking.mc_translation_tool.translator

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.Reader
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

interface IStringTranslator {
    fun translate(text: String): String
}

class XunfeiStringTranslator(
    private val appID: String,
    private val apiSecret: String,
    private val apiKey: String,
    val srcLang: String = "en",
    val dstLang: String = "cn",
) : IStringTranslator {

    private val apiAddr = "https://ntrans.xfyun.cn/v2/ots"

    companion object {
        fun buildFromEnvVar(): XunfeiStringTranslator {
            val appID = System.getenv("APP_ID") ?: error("No `APP_ID` in environment variable!")
            val apiSecret = System.getenv("API_SECRET") ?: error("No `API_SECRET` in environment variable!")
            val apiKey = System.getenv("API_KEY") ?: error("No `API_KEY` in environment variable!")
            return XunfeiStringTranslator(appID, apiSecret, apiKey)
        }
    }

    private fun buildHttpBody(text: String): String {
        val body = JsonObject()
        val business = JsonObject()
        val common = JsonObject()
        val data = JsonObject()
        common.addProperty("app_id", appID)
        business.addProperty("from", srcLang)
        business.addProperty("to", dstLang)
        val textByte: ByteArray = text.toByteArray(charset("UTF-8"))
        val textBase64 = java.lang.String(Base64.getEncoder().encodeToString(textByte)) as String
        data.addProperty("text", textBase64)
        body.add("common", common)
        body.add("business", business)
        body.add("data", data)
        return body.toString()
    }

    private fun signBody(bodyStr: String): String {
        val messageDigest: MessageDigest
        var encodestr: String? = ""
        try {
            messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(bodyStr.toByteArray(charset("UTF-8")))
            encodestr = Base64.getEncoder().encodeToString(messageDigest.digest())
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        return encodestr!!
    }

    private fun hmacSign(signature: String): String {
        val charset = Charset.forName("UTF-8")
        val mac = Mac.getInstance("hmacsha256")
        val spec = SecretKeySpec(apiSecret.toByteArray(charset), "hmacsha256")
        mac.init(spec)
        val hexDigits = mac.doFinal(signature.toByteArray(charset))
        return Base64.getEncoder().encodeToString(hexDigits)
    }

    private val client = OkHttpClient()

    private fun buildHttpHeaders(bodyStr: String): Map<String, String> {
        val formatter = DateTimeFormatterBuilder()
            .appendPattern("EEE, dd MMM yyyy HH:mm:ss z")
            .toFormatter(Locale.US)
        val dateStr = Instant.now().atZone(TimeZone.getTimeZone("GMT").toZoneId()).format(formatter)
        val host = "ntrans.xfyun.cn"
        val digest = "SHA-256=${signBody(bodyStr)}"
        val requestLine = "POST /v2/ots HTTP/1.1"
        val signOrigin = "host: $host\ndate: $dateStr\n$requestLine\ndigest: $digest"
        val signature = hmacSign(signOrigin)
        val authorization =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line digest\", signature=\"$signature\""

        return mapOf(
            "Content-Type" to "application/json",
            "Host" to host,
            "Date" to dateStr,
            "Digest" to digest,
            "Authorization" to authorization,
        )
    }

    private fun parseResponseBody(reader: Reader): String {
        val jObj = JsonParser.parseReader(reader)!!.asJsonObject!!
        val transResult = jObj.getAsJsonObject("data")!!
            .getAsJsonObject("result")!!
            .getAsJsonObject("trans_result")!!
        return transResult.getAsJsonPrimitive("dst").asString!!
    }

    override fun translate(text: String): String {
        val bodyStr = buildHttpBody(text)
        val headers = buildHttpHeaders(bodyStr)
        val url = URL(apiAddr)
        val conn = url.openConnection() as HttpURLConnection
        headers.forEach { (k, v) ->
            conn.setRequestProperty(k, v)
        }
        conn.doOutput = true
        conn.doInput = true
        val out = PrintWriter(conn.outputStream)
        out.println(bodyStr)
        out.flush()
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            val bufReader = BufferedReader(InputStreamReader(conn.errorStream))
            println("翻译出错！状态码: ${conn.responseCode}，错误信息：${bufReader.readLine()}")
            return ""
        }
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        return parseResponseBody(reader)
    }
}
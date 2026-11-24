package ir.hammeydan.app

import android.os.Handler
import android.os.Looper
import org.json.*
import java.net.*
import java.io.*
import java.util.concurrent.Executors
import javax.net.ssl.SSLException

internal fun obj(vararg values: Pair<String, Any?>) = JSONObject().apply { values.forEach { (k,v) -> put(k,v ?: JSONObject.NULL) } }
internal fun Any?.asObject() = this as? JSONObject ?: JSONObject()
internal fun Any?.asArray() = this as? JSONArray ?: JSONArray()
internal fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun enc(s:String) = URLEncoder.encode(s,"UTF-8")
internal class Api {
    companion object { const val BASE = "https://hooyar-si.ir/meydooon/api.php?a=" }
    private val executor = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())
    @Volatile var token = ""
    var onSessionExpired: (() -> Unit)? = null
    @Volatile private var closed = false
    fun work(task: () -> Unit) { if (!closed) executor.execute(task) }
    fun main(task: () -> Unit) { ui.post { if (!closed) task() } }
    fun close() { closed = true; executor.shutdownNow(); ui.removeCallbacksAndMessages(null) }
    private fun connection(action: String, auth: String): HttpURLConnection = (URL(BASE+action).openConnection() as HttpURLConnection).apply {
        connectTimeout = 9000; readTimeout = 18000; instanceFollowRedirects = false
        if(auth.isNotBlank()) setRequestProperty("Authorization", "Bearer $auth")
    }
    private fun bounded(input: InputStream?, max: Int): ByteArray = input?.use { s ->
        val out = ByteArrayOutputStream(); val buf = ByteArray(8192)
        while(true) { val n=s.read(buf); if(n<0) break; if(out.size()+n>max) throw IOException("response too large"); out.write(buf,0,n) }; out.toByteArray()
    } ?: byteArrayOf()
    fun call(action:String, payload:JSONObject? = null, done:(Any?,String?) -> Unit) {
        val auth = if(action in listOf("guest","login","register")) "" else token
        work {
            var h:HttpURLConnection?=null; var result:Any?=null; var error:String?=null; var expired=false
            try {
                val c=connection(action,auth); h=c; c.setRequestProperty("Accept","application/json")
                if(payload!=null) { c.requestMethod="POST"; c.doOutput=true; c.setRequestProperty("Content-Type","application/json; charset=utf-8"); val bytes=payload.toString().toByteArray(Charsets.UTF_8); c.setFixedLengthStreamingMode(bytes.size); c.outputStream.use { it.write(bytes) } }
                val code=c.responseCode; expired=code==401 && auth.isNotEmpty()
                val raw=bounded(if(code>=400) c.errorStream else c.inputStream,8*1024*1024).toString(Charsets.UTF_8)
                val parsed=try { JSONTokener(raw).nextValue() } catch(_:Exception) { null }
                error=when { code==404 && parsed !is JSONObject -> "مسیر سرور یافت نشد (۴۰۴). پوشه meydooon را بررسی کنید."; code==401 -> parsed.asObject().optString("error","نشست منقضی شده؛ دوباره وارد شوید."); code==429 -> parsed.asObject().optString("error","کمی بعد دوباره تلاش کنید."); code !in 200..299 -> parsed.asObject().optString("error","خطای سرور ($code). دوباره تلاش کنید."); parsed !is JSONObject && parsed !is JSONArray -> "پاسخ سرور معتبر نیست."; else -> null }
                if(error==null) result=parsed
            } catch(_:SocketTimeoutException) { error="پاسخ سرور طول کشید. دوباره تلاش کنید." }
            catch(_:UnknownHostException) { error="اینترنت یا دسترسی به سرور برقرار نیست." }
            catch(_:SSLException) { error="اتصال امن به سرور برقرار نشد." }
            catch(_:Exception) { error="ارتباط با سرور برقرار نشد." }
            finally { h?.disconnect() }
            main { if(expired && token==auth) onSessionExpired?.invoke(); done(result,error) }
        }
    }
    fun photo(id:Int, done:(ByteArray?) -> Unit) { val auth=token; work { var h:HttpURLConnection?=null; var bytes:ByteArray?=null; try { val c=connection("photo&id=$id",auth); h=c; if(c.responseCode==200) bytes=bounded(c.inputStream,4*1024*1024) } catch(_:Exception) {} finally { h?.disconnect() }; main { done(bytes) } } }
}

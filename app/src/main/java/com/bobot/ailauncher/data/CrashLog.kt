package com.bobot.ailauncher.data

import android.content.Context
import android.os.Build
import com.bobot.ailauncher.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录的纯逻辑（文件名/环形裁剪/标题提取/未读与提示判定），JVM 可测。
 *
 * 文件名约定：crash-<epochMillis>.log，时间戳即记录时间，不另存元数据。
 */
object CrashLogLogic {
    const val MAX_KEEP = 10
    private const val PREFIX = "crash-"
    private const val SUFFIX = ".log"

    fun fileNameFor(epochMillis: Long): String = "$PREFIX$epochMillis$SUFFIX"

    /** 解析文件名里的时间戳；不是崩溃记录文件（诊断日志等）返回 null */
    fun parseEpochMillis(fileName: String): Long? {
        if (!fileName.startsWith(PREFIX) || !fileName.endsWith(SUFFIX)) return null
        return fileName.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull()
    }

    /**
     * 环形裁剪计划：保留最近 [keep] 份，返回应删除的文件名（最旧在前）。
     * 非崩溃文件名（解析不出时间戳）不参与计数、永不删。
     */
    fun trimPlan(fileNames: List<String>, keep: Int = MAX_KEEP): List<String> {
        val valid = fileNames
            .mapNotNull { name -> parseEpochMillis(name)?.let { name to it } }
            .sortedBy { it.second }
        if (valid.size <= keep) return emptyList()
        return valid.take(valid.size - keep).map { it.first }
    }

    /**
     * 列表标题：堆栈首行。正文格式为「头信息 + --- + 堆栈」，取分隔线后
     * 第一个非空行（即异常类名: 消息）；没有分隔线时退回全文第一个非空行。
     */
    fun titleOf(content: String): String {
        val lines = content.lines()
        val sep = lines.indexOf("---")
        val body = if (sep >= 0) lines.drop(sep + 1) else lines
        return body.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .ifBlank { "未知异常" }
    }

    /** 未读时间戳集合：晚于已读水位 [readUpTo] 的记录 */
    fun unreadEpochs(epochs: List<Long>, readUpTo: Long): List<Long> =
        epochs.filter { it > readUpTo }

    /**
     * 冷启动是否该提示一次：有未读，且最新一份未读还没提示过
     * （[promptedUpTo] 是已提示到的最大时间戳；看过列表后 readUpTo
     * 会盖过它，自然不再提示）。
     */
    fun shouldPrompt(epochs: List<Long>, readUpTo: Long, promptedUpTo: Long): Boolean {
        val unread = unreadEpochs(epochs, readUpTo)
        val newest = unread.maxOrNull() ?: return false
        return newest > promptedUpTo
    }
}

/** 一条崩溃记录：时间戳 + 堆栈首行标题 + 全文 */
data class CrashRecord(
    val epochMillis: Long,
    val title: String,
    val content: String
)

/**
 * 崩溃本地日志（v0.68.100 卫生批）：侧载分发没有 Play Console，真机崩溃
 * 此前零可见。这里只做本地留痕——不引入任何崩溃上报 SDK、不外发数据：
 * 崩溃写 files/crashes/ 环形 10 份，设置页可看全文、可经系统分享面板
 * 发出去、可删；下次冷启动对没看过的记录非模态提示一次。
 */
object CrashLog {
    private const val PREFS = "crashlog"
    private const val KEY_READ_UP_TO = "read_up_to"
    private const val KEY_PROMPTED_UP_TO = "prompted_up_to"
    private const val DIR_NAME = "crashes"
    private const val DIAG_FILE = "diagnostics.log"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun formatTime(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))

    /**
     * 在 Application.onCreate 安装默认未捕获异常处理器：同步写盘后把异常
     * 链回系统原处理器——崩溃语义（FATAL 日志、进程结束）与系统默认一致，
     * 我们只是路过留一份副本。写盘本身失败也绝不挡链回。
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                // 极少数没有系统处理器可链的情况：按默认语义结束进程
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    /** 同步写一份崩溃记录并做环形裁剪。处理器线程内调用：只做文件 IO，不起协程。 */
    fun writeCrash(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val dir = dir(context)
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val header = buildString {
            appendLine("AI桌面崩溃记录")
            appendLine("time: ${formatTime(nowMillis)}")
            appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(
                "device: ${Build.MANUFACTURER} ${Build.MODEL} / " +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            )
            appendLine("thread: ${thread.name}")
            appendLine("---")
        }
        File(dir, CrashLogLogic.fileNameFor(nowMillis)).writeText(header + sw.toString())
        val names = dir.list()?.toList().orEmpty()
        CrashLogLogic.trimPlan(names).forEach { name ->
            runCatching { File(dir, name).delete() }
        }
    }

    /**
     * 诊断事件留一行（与崩溃记录同目录的 diagnostics.log）。
     * 只许传事件名与非敏感参数——任何时候都不许把 API Key 等敏感值写进来。
     */
    fun diag(context: Context, event: String) {
        runCatching {
            File(dir(context), DIAG_FILE)
                .appendText("${formatTime(System.currentTimeMillis())} $event\n")
        }
    }

    /** 全部记录，最新在前；读不了的文件跳过，不让坏文件打死列表 */
    fun list(context: Context): List<CrashRecord> =
        dir(context).listFiles().orEmpty()
            .mapNotNull { file ->
                val ts = CrashLogLogic.parseEpochMillis(file.name) ?: return@mapNotNull null
                val content = runCatching { file.readText() }.getOrNull()
                    ?: return@mapNotNull null
                CrashRecord(ts, CrashLogLogic.titleOf(content), content)
            }
            .sortedByDescending { it.epochMillis }

    fun unreadCount(context: Context): Int {
        val readUpTo = prefs(context).getLong(KEY_READ_UP_TO, 0L)
        return CrashLogLogic.unreadEpochs(list(context).map { it.epochMillis }, readUpTo).size
    }

    /** 标记全部已读（用户打开过崩溃记录列表时调用） */
    fun markAllRead(context: Context) {
        val maxTs = list(context).maxOfOrNull { it.epochMillis } ?: return
        prefs(context).edit().putLong(KEY_READ_UP_TO, maxTs).apply()
    }

    /** 删除全部崩溃记录（诊断日志保留，不属于崩溃记录） */
    fun deleteAll(context: Context) {
        dir(context).listFiles().orEmpty()
            .filter { CrashLogLogic.parseEpochMillis(it.name) != null }
            .forEach { runCatching { it.delete() } }
    }

    /**
     * 冷启动提示判定：该提示时返回提示文案并推进已提示水位（恰好一次），
     * 否则返回 null。文案由调用方走 IslandToasts 非模态呈现。
     */
    fun promptTextIfNeeded(context: Context): String? {
        val epochs = list(context).map { it.epochMillis }
        val p = prefs(context)
        val readUpTo = p.getLong(KEY_READ_UP_TO, 0L)
        val promptedUpTo = p.getLong(KEY_PROMPTED_UP_TO, 0L)
        if (!CrashLogLogic.shouldPrompt(epochs, readUpTo, promptedUpTo)) return null
        p.edit().putLong(KEY_PROMPTED_UP_TO, epochs.maxOrNull() ?: return null).apply()
        return "上次打开时闪退过，崩溃记录在「设置 · 崩溃记录」里"
    }
}

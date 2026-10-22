package com.bobot.ailauncher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Collator
import java.util.Locale

/**
 * 应用目录仓库缓存（v0.68.95·F-03/F-04：数据新鲜度）。
 *
 * 此前两个主表面各算各的、还都算错了时机：
 * - Dock 的清单是无 key 的 remember{}，进程内只算一次——新装应用永不进
 *   Dock、卸载的应用点开只剩 toast（F-03，违背 .88「保持更新」原则）；
 * - 应用中心三处 remember 在组合期（主线程）各跑一遍 [listLaunchableApps]
 *   （PackageManager 查询 + 逐应用 loadLabel/loadIcon），再同步
 *   [AppSearchIndex.build] 建拼音索引；任一装/卸广播一到，全部同步重跑、
 *   azGroups 换新对象 → LazyColumn 整表重组（F-04，应用中心「刷新感」的
 *   第二根因，.93 修的只是拖动跳转）。
 *
 * 本仓库把「全表枚举 + 搜索索引构建」收敛为全进程一份，后台（IO）构建、
 * StateFlow 递交快照，组合期只读快照：
 * - 失效键 = ([InstalledApps.epoch], [HiddenApps.version])：装/卸/隐藏变化
 *   才重建，绝不轮询；只有隐藏变化时复用上一代原始枚举（枚举是最贵的一步，
 *   隐藏变化不动它），只重派生可见集与索引；
 * - 快照内容：原始枚举 [Snapshot.apps]（含本应用，消费方按各自口径过滤）、
 *   可见集 [Snapshot.visibleApps]（去自己、去隐藏、中文 Collator 排序——与
 *   应用中心 azApps 历史口径逐字一致）、基于可见集的 [Snapshot.index]。
 * - 首页意图层（IntentEntry）的索引保持它自己的懒建路径（R3-D 的启动
 *   成本决策：不打字不建），不在本仓库范围；本仓库只覆盖 Dock 与应用中心，
 *   两者共用这一份全表，不再各建一份。
 *
 * 消费方式：组合层在 LaunchedEffect(epoch/hiddenVersion) 里调 [ensure]
 * （幂等、去重），collectAsState 读 [snapshot]；快照未就绪时为 null，
 * 消费方按空表呈现，构建完成自动换表（与 IntentEntry 懒建同口径）。
 */
object AppCatalog {

    data class Snapshot(
        /** 构建时的已装代次（[InstalledApps.epoch]） */
        val epoch: Int,
        /** 构建时的隐藏集版本（[HiddenApps.version]） */
        val hiddenVersion: Int,
        /** 全部可启动应用原始枚举（含本应用，未排序未过滤） */
        val apps: List<AppInfo>,
        /** 去本应用、去隐藏、Collator(CHINA) 按名称排序——应用中心 azApps 口径 */
        val visibleApps: List<AppInfo>,
        /** 基于 [visibleApps] 的拼音搜索索引（应用中心搜索用） */
        val index: List<AppSearchIndex.Entry>
    )

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val buildMutex = Mutex()

    /** 在途构建的失效键（去重：同一代次只排一次队） */
    @Volatile
    private var pendingKey: Pair<Int, Int>? = null

    /**
     * 确保快照与当前 (epoch, hiddenVersion) 同代。组合层每次 epoch /
     * hiddenVersion 变化时调用；已同代或同代构建已在途时直接返回。
     */
    fun ensure(context: Context) {
        InstalledApps.ensureRegistered(context)
        val epoch = InstalledApps.epoch.value
        val hiddenVersion = HiddenApps.version.intValue
        if (!catalogNeedsBuild(_snapshot.value?.epoch, _snapshot.value?.hiddenVersion, epoch, hiddenVersion)) return
        val key = epoch to hiddenVersion
        synchronized(this) {
            if (pendingKey == key) return
            pendingKey = key
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                rebuild(appContext, epoch, hiddenVersion)
            } finally {
                synchronized(this) { if (pendingKey == key) pendingKey = null }
            }
        }
    }

    private suspend fun rebuild(context: Context, epoch: Int, hiddenVersion: Int) {
        buildMutex.withLock {
            val current = _snapshot.value
            // 排队期间可能已有同代构建完成（多个消费方同时 ensure）
            if (!catalogNeedsBuild(current?.epoch, current?.hiddenVersion, epoch, hiddenVersion)) return
            // 原始枚举只随 epoch 失效；仅隐藏变化时复用上一代枚举
            val raw = if (catalogReusesRaw(current?.epoch, epoch)) {
                current!!.apps
            } else {
                listLaunchableApps(context)
            }
            // 隐藏集在锁内读最新值：构建期间又发生了隐藏变化时，快照内容
            // 按更新后的集合派生，但仍按请求代次标注——消费方下一次 ensure
            // 会以新 hiddenVersion 对齐标注（内容只会更新、不会更旧）。
            val hidden = HiddenApps.getHidden(context)
            val collator = Collator.getInstance(Locale.CHINA)
            val visible = raw
                .filter { it.packageName != context.packageName && it.packageName !in hidden }
                .sortedWith { a, b -> collator.compare(a.label.toString(), b.label.toString()) }
            val index = AppSearchIndex.build(context, visible)
            _snapshot.value = Snapshot(epoch, hiddenVersion, raw, visible, index)
        }
    }
}

/** 纯逻辑接缝（供单测）：当前快照代次与目标代次不一致（或无快照）才需要构建。 */
internal fun catalogNeedsBuild(
    currentEpoch: Int?,
    currentHiddenVersion: Int?,
    epoch: Int,
    hiddenVersion: Int
): Boolean =
    currentEpoch == null || currentEpoch != epoch || currentHiddenVersion != hiddenVersion

/** 纯逻辑接缝（供单测）：同 epoch 才可复用上一代原始枚举，省掉整表 PackageManager 查询。 */
internal fun catalogReusesRaw(currentEpoch: Int?, epoch: Int): Boolean =
    currentEpoch != null && currentEpoch == epoch

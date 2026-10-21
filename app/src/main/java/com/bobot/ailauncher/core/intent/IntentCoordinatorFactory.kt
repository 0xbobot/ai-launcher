package com.bobot.ailauncher.core.intent

import android.content.Context
import com.bobot.ailauncher.core.action.ActionEngine
import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.action.ActionResult
import com.bobot.ailauncher.data.Capability
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.InstalledApps
import com.bobot.ailauncher.data.LlmConfig
import com.bobot.ailauncher.data.LlmRouter
import com.bobot.ailauncher.data.PetRepository

/**
 * 生产装配：把 [IntentCoordinator] 的注入接口接到真实实现
 * （LlmConfig / LlmRouter.plan / CapabilityRegistry / ActionEngine / PetRepository）。
 * 单测不走这里，用假实现替换各接口。
 */
object IntentCoordinatorFactory {

    fun create(context: Context): IntentCoordinator {
        val appContext = context.applicationContext
        // v0.68.88：注册已装应用变化监听（幂等），能力绑定/首页索引靠它失效刷新
        InstalledApps.ensureRegistered(appContext)
        return IntentCoordinator(
            config = LlmConfigAdapter(appContext),
            planner = LlmPlannerAdapter(appContext),
            capabilities = RegistryAdapter(appContext),
            executor = EngineAdapter(appContext),
            speaker = PetSpeaker,
            contextProvider = { IntentContextProvider.current(appContext) }
        )
    }

    private class LlmConfigAdapter(private val context: Context) : IntentConfig {
        override fun hasKey(): Boolean = LlmConfig.hasKey(context)
    }

    private class LlmPlannerAdapter(private val context: Context) : IntentPlanner {
        override suspend fun plan(
            input: String,
            validIds: Set<String>,
            hints: String,
            contextPack: String
        ): PlanOutcome =
            when (
                val r = LlmRouter.plan(
                    baseUrl = LlmConfig.getBaseUrl(context),
                    apiKey = LlmConfig.getApiKey(context),
                    model = LlmConfig.getModel(context),
                    input = input,
                    validIds = validIds,
                    hints = hints,
                    contextPack = contextPack
                )
            ) {
                is LlmRouter.PlanResult.Chat -> PlanOutcome.Chat(r.text)
                is LlmRouter.PlanResult.Action ->
                    PlanOutcome.Action(r.capabilityId, r.params, r.confirmText)
                is LlmRouter.PlanResult.Err -> PlanOutcome.Err(r.message)
            }
    }

    private class RegistryAdapter(private val context: Context) : IntentCapabilities {
        override fun ensureLoaded() = CapabilityRegistry.load(context)

        override fun validIds(): Set<String> = CapabilityRegistry.validIds()

        /**
         * 带参数说明的能力清单（二批：[CapabilityHints] 纯函数生成）。
         * v0.68.88 起只含**本机当前可执行**的能力（[CapabilityRegistry.executableCapabilities]：
         * 提供方装了哪家算哪家、深链能力另有 geo 处理者也算）——没装对应应用的
         * 能力不进清单，不向模型许诺做不到的事；validIds 仍是全集（计划合法性
         * 白名单），万一模型报出清单外能力，执行层会如实回「没有安装支持的应用」。
         */
        override fun hints(): String =
            CapabilityHints.build(
                CapabilityRegistry.executableCapabilities(context)
            )

        override fun find(id: String): Capability? = CapabilityRegistry.find(id)
    }

    private class EngineAdapter(private val context: Context) : IntentActionExecutor {
        override fun submit(request: ActionRequest): ActionResult =
            ActionEngine.submit(context, request)
    }

    private object PetSpeaker : IntentSpeaker {
        override fun say(text: String) = PetRepository.say(text)
    }
}

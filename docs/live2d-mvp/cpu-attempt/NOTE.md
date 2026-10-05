# CPU 尝试记录（2026-10-04）

结论：**本机 CPU 跑不通 inkinesis 分解，GPU 是硬门槛。**

## 确切证据

`tools/imageToRig/config.ts` 的 `preflight()` 里写死了检查：

```ts
if(local && !['cuda','mps'].includes(config.device))
  missing.push('RIG_DEVICE must be cuda or mps; CPU inference is not supported');
```

- `rigConfig()` 的 device 默认值只有两档：darwin→`mps`，其他→`cuda`，**没有 CPU 代码路径**。
- 实测 `RIG_DEVICE=cpu npm run image-to-rig -- --check`，自检直接报：
  `"RIG_DEVICE must be cuda or mps; CPU inference is not supported"`，`available: false`，流程拒绝启动。

## 瓶颈

分解核心是 See-through 步骤，跑的是 pinned 的 Stable Diffusion 系模型
（`.cache/rig/models/layer` 的 unet/vae/text_encoder + depth 模型），
diffusers 推理在该工具链里只接了 cuda/mps 后端。

## 附带发现（次要门槛）

即使绕过 GPU 检查，完整 pipeline 还需要 `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` /
`IMAGE_MODEL` / `RIG_ANALYSIS_MODEL`（分析与表情生成调外部 API），本机同样没有。
但首要的、一票否决的是 GPU 检查。

## 日志

- `check.log`：`--check` 自检输出（含缺失项清单）
- `npm-install.log`：依赖安装成功（167 包，23s），排除"环境没装好"的干扰

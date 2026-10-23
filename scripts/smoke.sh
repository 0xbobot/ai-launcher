#!/usr/bin/env bash
# PROBE SHIM（仅 probe-freeze-* 探针分支）：smoke-test.yml 的 dispatch 通道
# 写死跑 scripts/smoke.sh，探针分支在此转调三路对照短探针 probe_freeze.sh，
# 产物复制进 smoke-out/ 供既有 artifact 上传步骤回收。正式 smoke.sh 在
# 其他分支不受影响。
bash scripts/probe_freeze.sh
RC=$?
mkdir -p smoke-out
cp -r probe-out/. smoke-out/ 2>/dev/null || true
exit $RC

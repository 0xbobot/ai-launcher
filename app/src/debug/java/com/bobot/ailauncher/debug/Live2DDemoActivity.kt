package com.bobot.ailauncher.debug

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.bobot.ailauncher.live2d.demo.GLRenderer
import com.bobot.ailauncher.live2d.demo.LAppDelegate

/**
 * Live2D 渲染验证 demo（debug only，live2d-mvp 分支）。
 *
 * 目标：证明官方示例的 GLSurfaceView 渲染链路能嵌进 Compose——这就是
 * docs/PET_WORLD_ARCHITECTURE.md 里"宿主 A（首页）"的技术路线。
 *
 * 跑法（debug 包）：
 *   adb shell am start -n com.bobot.ailauncher/com.bobot.ailauncher.debug.Live2DDemoActivity
 *
 * 预期：Hiyori（官方示例模型）在真机上渲染出来，自动播放待机动作
 * （呼吸/眨眼），点按身体切换动作。logcat 无 Core 报错即通过。
 */
class Live2DDemoActivity : ComponentActivity() {

    private var glSurfaceView: GLSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LAppDelegate.getInstance().onStart(this)
        setContent {
            Live2DView(onViewCreated = { glSurfaceView = it })
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
    }

    override fun onPause() {
        glSurfaceView?.onPause()
        LAppDelegate.getInstance().onPause()
        super.onPause()
    }

    override fun onDestroy() {
        LAppDelegate.getInstance().onDestroy()
        super.onDestroy()
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun Live2DView(onViewCreated: (GLSurfaceView) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                // 与官方示例一致：OpenGL ES 2.0 + 持续渲染
                setEGLContextClientVersion(2)
                setRenderer(GLRenderer())
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                preserveEGLContextOnPause = true
                setOnTouchListener { v, event ->
                    val glView = v as GLSurfaceView
                    val x = event.x
                    val y = event.y
                    glView.queueEvent {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN ->
                                LAppDelegate.getInstance().onTouchBegan(x, y)
                            MotionEvent.ACTION_UP ->
                                LAppDelegate.getInstance().onTouchEnd(x, y)
                            MotionEvent.ACTION_MOVE ->
                                LAppDelegate.getInstance().onTouchMoved(x, y)
                        }
                    }
                    true
                }
                onViewCreated(this)
            }
        }
    )
}

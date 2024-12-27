package com.example.ar.obsolete

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.SessionPausedException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLES11Ext
import com.example.ar.DisplayRotationHelper
import com.example.ar.R

class MyGLRenderer(private val context: Context, private val arSession: Session) : GLSurfaceView.Renderer {
    private val vertexCoords = floatArrayOf(
        -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f
    )

    private val texCoords = floatArrayOf(
        0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f
    )

    private val vertexBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(vertexCoords.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(vertexCoords)
                position(0)
            }

    private val texCoordBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply {
                put(texCoords)
                position(0)
            }

    private fun loadShaderFromRawResource(resourceId: Int): String {
        return context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
    }

    private val vertexShaderCode = loadShaderFromRawResource(R.raw.camera_feed_vertex_shader)
    private val fragmentShaderCode = loadShaderFromRawResource(R.raw.camera_feed_fragment_shader)

    private var textureId: Int = 0
    private var displayRotationHelper: DisplayRotationHelper? = null
    private var cameraFeedProgram: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureHandle: Int = 0

    init {
        displayRotationHelper = DisplayRotationHelper(context)
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        // Generate the texture ID
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        // Bind and configure the texture
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Load shaders and get attribute locations
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        cameraFeedProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        positionHandle = GLES20.glGetAttribLocation(cameraFeedProgram, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(cameraFeedProgram, "a_TexCoord")
        textureHandle = GLES20.glGetUniformLocation(cameraFeedProgram, "u_Texture")
    }

    override fun onDrawFrame(gl: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        displayRotationHelper?.updateSessionIfNeeded(arSession)

        try {
            val frame: Frame = arSession.update()

            if (isCameraImageAvailable(frame)) {
                renderCameraFeed(frame)
            } else {
                // Optionally, handle the case when the camera image is not available
            }
        } catch (e: CameraNotAvailableException) {
            e.printStackTrace()
        } catch (e: SessionPausedException) {
            e.printStackTrace() // Handle paused session
        }
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        displayRotationHelper?.onSurfaceChanged(width, height)
    }

    private fun renderCameraFeed(frame: Frame) {
        // Ensure the texture ID is bound before rendering
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        // Create a FloatBuffer for the original and transformed UV coordinates
        val texCoordOriginalBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordOriginalBuffer.position(0)

        val texCoordTransformedBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        // Transform display UV coordinates based on the AR frame
        frame.transformDisplayUvCoords(texCoordOriginalBuffer, texCoordTransformedBuffer)

        // Render the camera feed texture
        GLES20.glUseProgram(cameraFeedProgram)

        // Setup vertex data
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        // Setup texture coordinate data with transformed coordinates
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordTransformedBuffer)

        // Bind the texture and draw the frame
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }




    private fun isCameraImageAvailable(frame: Frame): Boolean {
        return try {
            frame.acquireCameraImage().close() // If this succeeds, the image is available
            true
        } catch (e: NotYetAvailableException) {
            false
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    fun getTextureId(): Int {
        return textureId
    }
}

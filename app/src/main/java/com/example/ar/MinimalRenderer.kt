package com.example.ar

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Frame
import com.google.ar.core.Session
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class MinimalRenderer(private val context: Context, private val arSession: Session) : GLSurfaceView.Renderer {

    private val vertexCoords = floatArrayOf(
        -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f
    )
    private val texCoords = floatArrayOf(
        0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f
    )
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(vertexCoords)
            position(0)
        }
    private val texCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(texCoords)
            position(0)
        }

    private var textureId: Int = 0
    private var programId: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureHandle: Int = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
       /// GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Generate the texture ID
        val textures = IntArray(1)
        //GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        // Bind and configure the texture
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Load and compile shaders, then link the program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, loadShaderFromRawResource(R.raw.camera_feed_vertex_shader))
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, loadShaderFromRawResource(R.raw.camera_feed_fragment_shader))

        programId = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        positionHandle = GLES20.glGetAttribLocation(programId, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(programId, "a_TexCoord")
        textureHandle = GLES20.glGetUniformLocation(programId, "u_Texture")
    }

    override fun onDrawFrame(gl: GL10?) {
      //  GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        try {
            val frame: Frame = arSession.update()
            arSession.setDisplayGeometry(1, 200, 200)
           // renderCameraFeed(frame)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    private fun renderCameraFeed(frame: Frame) {
        val texCoordTransformedBuffer: FloatBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        frame.transformDisplayUvCoords(texCoordBuffer, texCoordTransformedBuffer)

        // Render the camera feed texture
        GLES20.glUseProgram(programId)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordTransformedBuffer)

        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun loadShaderFromRawResource(resourceId: Int): String {
        return context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
    }

    fun getTextureId(): Int {
        return textureId
    }
}

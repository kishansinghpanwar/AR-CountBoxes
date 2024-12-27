package com.example.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class MainActivity : AppCompatActivity() , GLSurfaceView.Renderer{
    private var arSession: Session? = null
    private lateinit var myGLSurfaceView: GLSurfaceView
    private var displayRotationHelper: DisplayRotationHelper? = null


    private val backgroundRenderer = BackgroundRenderer()
    private val edgeImage = CameraImageBuffer()
    //private val textureReader: TextureReader = TextureReader()
    // ArCore full resolution texture has a size of 1920 x 1080.
    private val TEXTURE_WIDTH: Int = 1920
    private val TEXTURE_HEIGHT: Int = 1080
    // We choose a lower sampling resolution.
    private val IMAGE_WIDTH: Int = 1024
    private  val IMAGE_HEIGHT: Int = 512
    private var frameBufferIndex = -1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myGLSurfaceView = findViewById(R.id.surfaceview)
        displayRotationHelper = DisplayRotationHelper(this)

        myGLSurfaceView.preserveEGLContextOnPause = true;
        myGLSurfaceView.setEGLContextClientVersion(2);
        myGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        myGLSurfaceView.setRenderer(this);
        myGLSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY;
        myGLSurfaceView.setWillNotDraw(false);

    }


    override fun onResume() {
        super.onResume()
        if (arSession == null) {
            var exception: Exception? = null
            var message: String? = null
            try {
                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                arSession = Session( /* context= */this)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
                exception = e
            } catch (e: Exception) {
                message = "This device does not support AR"
                exception = e
            }
            if (message != null) {
                Log.e("ERROR", "Exception creating session", exception)
                return
            }
        }

        val config: Config? = arSession?.getConfig()
        config?.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY)
        config?.setFocusMode(Config.FocusMode.AUTO)
        arSession?.configure(config)

        // Note that order matters - see the note in onPause(), the reverse applies here.
        arSession?.resume()
        myGLSurfaceView.onResume()
        //displayRotationHelper.onResume()
    }

    public override fun onPause() {
        super.onPause()
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call session.update() and get a SessionPausedException.
        //displayRotationHelper.onPause()
        myGLSurfaceView.onPause()
        arSession?.pause()
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Standard Android full-screen functionality.
            window
                .decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private val depthRenderer = DepthRenderer()
    private val boxRenderer = BoxRenderer()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        // Create the texture and pass it to ARCore session to be filled during update().
        backgroundRenderer.createOnGlThread( /*context=*/this)
        depthRenderer.createOnGlThread( /*context=*/this)
        boxRenderer.createOnGlThread( /*context=*/this)
        arSession?.setCameraTextureName(backgroundRenderer.textureId)
        // The image format can be either IMAGE_FORMAT_RGBA or IMAGE_FORMAT_I8.
        // Set keepAspectRatio to false so that the output image covers the whole viewport.
       // textureReader.create(CameraImageBuffer.IMAGE_FORMAT_I8, IMAGE_WIDTH, IMAGE_HEIGHT, false)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper?.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }
    override fun onDrawFrame(gl: GL10?) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (arSession == null) {
            return
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper!!.updateSessionIfNeeded(arSession!!)

        try {
            arSession?.setCameraTextureName(backgroundRenderer.textureId)

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            val frame: Frame = arSession!!.update()
            val camera: Camera = frame.camera

            // If frame is ready, render camera preview image to the GL surface.
            backgroundRenderer.draw(frame)

            // Retrieve the depth data for this frame.
            val points: FloatBuffer =
                DepthData.create(frame, arSession?.createAnchor(camera.getPose()))
                    ?: return


            // If not tracking, show tracking failure reason instead.
            if (camera.getTrackingState() === TrackingState.PAUSED) {
               /* messageSnackbarHelper.showMessage(
                    this, TrackingStateHelper.getTrackingFailureReasonString(camera)
                )*/
                return
            }

            // Filters the depth data.
            DepthData.filterUsingPlanes(points, arSession?.getAllTrackables(Plane::class.java))

            // Visualize depth points.
            depthRenderer.update(points)
            depthRenderer.draw(camera)

            // Draw boxes around clusters of points.
            val clusteringHelper: PointClusteringHelper = PointClusteringHelper(points)
            val clusters: List<AABB> = clusteringHelper.findClusters()
            for (aabb in clusters) {
                boxRenderer.draw(aabb, camera)
            }
        } catch (t: Throwable) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e("ERROR", "Exception on the OpenGL thread", t)
        }
    }
}



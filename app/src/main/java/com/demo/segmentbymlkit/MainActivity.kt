package com.demo.segmentbymlkit

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Pair
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.Subject
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var preview: ImageView? = null
    private var imageUri: Uri? = null
    private var imageMaxWidth = 0

    // Max height (portrait mode)
    private var imageMaxHeight = 0

    private var subjectSegmenterProcessor = SubjectSegmenterProcessor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_still_image)

        findViewById<View>(R.id.select_image_button).setOnClickListener { view: View ->
            // Menu for selecting either: a) take new photo b) select from existing
            val popup = PopupMenu(this@MainActivity, view)
            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                val itemId = menuItem.itemId
                if (itemId == R.id.select_images_from_local) {
                    startChooseImageIntentForResult()
                    return@setOnMenuItemClickListener true
                } else if (itemId == R.id.take_photo_using_camera) {
                    startCameraIntentForResult()
                    return@setOnMenuItemClickListener true
                }
                false
            }
            val inflater = popup.menuInflater
            inflater.inflate(R.menu.camera_button_menu, popup.menu)
            popup.show()
        }
        findViewById<View>(R.id.segment_button).setOnClickListener { view: View? ->
            val inputImage = InputImage.fromBitmap((preview?.drawable as BitmapDrawable).bitmap, 0)
            //分割处理
            subjectSegmenterProcessor.detectInImage(inputImage).addOnSuccessListener {
                handleBitmap(it, inputImage)
            }.addOnFailureListener { e: Exception? ->
                Log.e(TAG, "Error running subject segmenter.", e)
            }
        }
        preview = findViewById(R.id.preview)

        val rootView = findViewById<View>(R.id.root)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                imageMaxWidth = rootView.width
                imageMaxHeight = rootView.height - findViewById<View>(R.id.control).height
            }
        })

    }

    /**
     * 加工分割后的点位信息
     *
     */
    private fun handleBitmap(result: SubjectSegmentationResult?, inputImage: InputImage) {
        //第一步通过点位生成二值的mask图
        val buffer = maskColorsFromFloatBuffer(result?.subjects, inputImage)
        val maskBitmap = Bitmap.createBitmap(
            buffer, inputImage.width, inputImage.height, Bitmap.Config.ARGB_8888
        )
        //第二步，利用Xfermode比较原图和mask，抠出叠加的图
        val targetBitmap =
            createMaskedBitmap((preview?.drawable as BitmapDrawable).bitmap, maskBitmap)

        preview?.setImageBitmap(targetBitmap)
    }

    @ColorInt
    private fun maskColorsFromFloatBuffer(
        subjects: List<Subject>?, inputImage: InputImage
    ): IntArray {
        @ColorInt val colors = IntArray(inputImage.width * inputImage.height)
        subjects?.size?.let {
            for (k in 0 until it) {
                val subject = subjects[k]
                val rgb = COLORS[k % COLORS.size]
                val color = Color.argb(255, rgb[0], rgb[1], rgb[2])
                val mask = subject.confidenceMask
                for (j in 0 until subject.height) {
                    for (i in 0 until subject.width) {
                        if (mask!!.get() > 0.5) {
                            colors[(subject.startY + j) * inputImage.width + subject.startX + i] =
                                color
                        }
                    }
                }
                // Reset [FloatBuffer] pointer to beginning, so that the mask can be redrawn if screen is
                // refreshed
                mask!!.rewind()
            }
        }
        return colors
    }

    fun createMaskedBitmap(sourceBitmap: Bitmap, maskBitmap: Bitmap): Bitmap {
        // 创建结果Bitmap
        val resultBitmap = createBitmap(sourceBitmap.width, sourceBitmap.height)
        val canvas = Canvas(resultBitmap)

        // 使用PorterDuffXfermode进行混合
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }

        // 先绘制原始图片
        canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
        // 应用掩码
        canvas.drawBitmap(maskBitmap, 0f, 0f, paint)

        return resultBitmap
    }

    private fun startCameraIntentForResult() { // Clean up last time's image
        imageUri = null
        preview!!.setImageBitmap(null)
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.TITLE, "New Picture")
            values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    private fun startChooseImageIntentForResult() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_CHOOSE_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            tryReloadAndDetectInImage()
        } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == Activity.RESULT_OK) {
            // In this case, imageUri is returned by the chooser, save it.
            imageUri = data!!.data
            tryReloadAndDetectInImage()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun tryReloadAndDetectInImage() {
        Log.d(TAG, "Try reload and detect image")
        try {
            if (imageUri == null) {
                return
            }

            val imageBitmap =
                BitmapUtils.getBitmapFromContentUri(contentResolver, imageUri) ?: return

            // Get the dimensions of the image view
            val targetedSize: Pair<Int, Int> = targetedWidthHeight

            // Determine how much to scale down the image
            val scaleFactor =
                (imageBitmap.width.toFloat() / targetedSize.first.toFloat()).coerceAtLeast(
                    imageBitmap.height.toFloat() / targetedSize.second.toFloat()
                )
            val resizedBitmap: Bitmap = imageBitmap.scale(
                (imageBitmap.width / scaleFactor).toInt(),
                (imageBitmap.height / scaleFactor).toInt()
            )
            preview!!.setImageBitmap(resizedBitmap)
        } catch (e: IOException) {
            Log.e(TAG, "Error retrieving saved image")
            imageUri = null
        }
    }

    private val targetedWidthHeight: Pair<Int, Int>
        get() {
            return Pair(imageMaxWidth, imageMaxHeight)
        }

    companion object {
        private const val TAG = "MainActivity"

        private const val REQUEST_IMAGE_CAPTURE = 1001
        private const val REQUEST_CHOOSE_IMAGE = 1002

        private val COLORS = arrayOf(
            intArrayOf(255, 0, 255),
            intArrayOf(0, 255, 255),
            intArrayOf(255, 255, 0),
            intArrayOf(255, 0, 0),
            intArrayOf(0, 255, 0),
            intArrayOf(0, 0, 255),
            intArrayOf(128, 0, 128),
            intArrayOf(0, 128, 128),
            intArrayOf(128, 128, 0),
            intArrayOf(128, 0, 0),
            intArrayOf(0, 128, 0),
            intArrayOf(0, 0, 128)
        )
    }
}
package com.example.cat_and_dog

import android.app.Activity
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.net.URISyntaxException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


data class Recognition(
        val name: String,
        val probability: Float
) {
    override fun toString() =
            "$name : ${probability}%"
}

class MainActivity : AppCompatActivity() {

    private var catDogClassifier = CatDogClassifier(this)

    var labels : List<String>? = null
//    var MODEL_INPUT_SIZE = 128
    var MODEL_INPUT_SIZE = 4 * 128 * 128 * 1
    var image_size = 128
    var BATCH_SIZE = 32

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        labels = getLabels(assets,"labels17.txt")

        btn_gal.setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_PICK
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), 2)
        }

        catDogClassifier
            .initialize()
            .addOnFailureListener { e -> Log.e("SEEEE", "Error to setting up digit classifier.", e) }



        btn_done.setOnClickListener {
            Glide.with(this)
                .asBitmap()
                .load(imageUri)
                .into(object : CustomTarget<Bitmap>(){
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        Log.d("SEEEE","${resource.width} X ${resource.height}")

//                        var res = recognize(resource!!)
                        var res  = "";
                        catDogClassifier
                            .classifyAsync(resource)
                            .addOnSuccessListener { resultText -> res
                                Log.d("SEEEE", resultText)
                                var ans = "";
                                ans = if(resultText.contains("0")){
                                    "Its a CAT."
                                }else{
                                    "Its a DOG."
                                }
                                Toast.makeText(applicationContext,ans,Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("SEEEE", "Error classifying drawing.", e)
                            }
//                        iv_img.setImageBitmap(resource)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        // this is called when imageView is cleared on lifecycle call or for
                        // some other reason.
                        // if you are referencing the bitmap somewhere else too other than this imageView
                        // clear it here as you can no longer have the bitmap
                    }
                })

//            var res = recognize(bitmap!!)

        }
    }

    var imageUri: Uri? = null;

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2 && resultCode == Activity.RESULT_OK) {
            try {
                imageUri = data!!.data!!
                iv_img.setImageURI(imageUri)
            } catch (e: URISyntaxException) {
                e.printStackTrace()
            }
        }
    }


    fun recognize(bitmap: Bitmap): List<Recognition> {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, image_size, image_size, false)
        Log.d("SEEEE","${scaledBitmap.width} X ${scaledBitmap.height}")
        val pixelValues = IntArray(image_size * image_size)
//        bitmap.getPixels(pixelValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        scaledBitmap.getPixels(pixelValues, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)

        var pixel = 0
        var byteBuffer = getModelByteBuffer(assets,"model17.tflite")
        for (i in 0 until image_size) {
            for (j in 0 until image_size) {

                val pixelValue = pixelValues[pixel++]
                byteBuffer.putFloat((pixelValue shr 16 and 0xFF) / 255f)
                byteBuffer.putFloat((pixelValue shr 8 and 0xFF) / 255f)
                byteBuffer.putFloat((pixelValue and 0xFF) / 255f)
            }
        }

        val results = Array(BATCH_SIZE) { FloatArray(labels!!.size) }
//            model.run(byteBuffer, results)
        return parseResults(results)
    }


    @Throws(IOException::class)
    private fun getModelByteBuffer(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Throws(IOException::class)
    private fun getLabels(assetManager: AssetManager, labelPath: String): List<String> {
        val labels = ArrayList<String>()
        val reader = BufferedReader(InputStreamReader(assetManager.open(labelPath)))
        while (true) {
            val label = reader.readLine() ?: break
            labels.add(label)
        }
        reader.close()
        return labels
    }

    private fun parseResults(result: Array<FloatArray>): List<Recognition> {
        val recognitions = mutableListOf<Recognition>()
        labels!!.forEachIndexed { index, label ->
            val probability = result[0][index]
            recognitions.add(Recognition(label, probability))
        }

        return recognitions.sortedByDescending { it.probability }
    }


    override fun onDestroy() {
        catDogClassifier.close()
        super.onDestroy()
    }


}
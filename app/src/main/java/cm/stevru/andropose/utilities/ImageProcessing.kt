package cm.stevru.andropose.utilities

import android.graphics.*
import android.os.Environment
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Log
import androidx.core.graphics.createBitmap
import cm.stevru.andropose.bodypart.KeyPoint
import cm.stevru.andropose.bodypart.Person
import cm.stevru.andropose.bodypart.enums.BodyPartEnum
import cm.stevru.andropose.classifier.Classifier
import cm.stevru.andropose.classifier.TFLiteObjectDetectionAPIModel
import cm.stevru.andropose.fragments.ImageFragment
import cm.stevru.andropose.inference.HeightInference
import cm.stevru.andropose.inference.PoseInference
import cm.stevru.andropose.models.CSVMapperModel
import cm.stevru.andropose.models.CSVMapperWithLineModel
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

class ImageProcessing(singlePoseInference: PoseInference, multiplePoseInference: PoseInference, detectionModel: Classifier?){

    val TAG = "IMAGEPROCESSING"

    /** List of body joints that should be connected **/
    private val bodyJoints = listOf(
        Pair(BodyPartEnum.LEFT_WRIST, BodyPartEnum.LEFT_ELBOW),
        Pair(BodyPartEnum.LEFT_ELBOW, BodyPartEnum.LEFT_SHOULDER),
        Pair(BodyPartEnum.LEFT_SHOULDER, BodyPartEnum.RIGHT_SHOULDER),
        Pair(BodyPartEnum.RIGHT_SHOULDER, BodyPartEnum.RIGHT_ELBOW),
        Pair(BodyPartEnum.RIGHT_ELBOW, BodyPartEnum.RIGHT_WRIST),
        Pair(BodyPartEnum.LEFT_SHOULDER, BodyPartEnum.LEFT_HIP),
        Pair(BodyPartEnum.LEFT_HIP, BodyPartEnum.RIGHT_HIP),
        Pair(BodyPartEnum.RIGHT_HIP, BodyPartEnum.RIGHT_SHOULDER),
        Pair(BodyPartEnum.LEFT_HIP, BodyPartEnum.LEFT_KNEE),
        Pair(BodyPartEnum.LEFT_KNEE, BodyPartEnum.LEFT_ANKLE),
        Pair(BodyPartEnum.RIGHT_HIP, BodyPartEnum.RIGHT_KNEE),
        Pair(BodyPartEnum.RIGHT_KNEE, BodyPartEnum.RIGHT_ANKLE)
    )

    var posenetSingle: PoseInference = singlePoseInference
    var posenetMultiple: PoseInference = multiplePoseInference

    //for drawing keypoints
    private var paint = Paint()
    private var paintText = Paint()

    /** Threshold for confidence score. */
    private val minConfidence = 0.5 //0.5

    /** Radius of circle used to draw keypoints.  */
    private val circleRadius = 3.5f

    /** Single Model input shape for images.   */
    var MODEL_WIDTH = 257
    var MODEL_HEIGHT = 257

    //Multi Model input shape
    var MODEL_WIDTH_1 = 337
    var MODEL_HEIGHT_1 = 337

    private var detector: Classifier? = detectionModel

    //Detector Initialisation

    // Minimum detection confidence to track a detection.
    var MINIMUM_CONFIDENCE_TF_OD_API = 0.5f
    private val TF_OD_API_INPUT_SIZE = 300
    private var cropToFrameTransform: Matrix? = null
    //-- Utility functions

    //CVS Initialisation
    private lateinit var mapperL                      : ArrayList<CSVMapperWithLineModel>
    private lateinit var mapperKP                     : ArrayList<CSVMapperModel>

    lateinit var imgOnlyReconstructedBitmap           :Bitmap
    lateinit var formattedOutput                      : SpannableStringBuilder
    lateinit var formattedSizeInfo                    : SpannableStringBuilder
    lateinit var imgReconstructedLineBitmap           : Bitmap

    lateinit var listMapperKP :MutableList<ArrayList<CSVMapperModel>>// = mutableListOf()
    lateinit var listMapperL : MutableList<ArrayList<CSVMapperWithLineModel>>// = mutableListOf()

    lateinit var listPerson : MutableList<Person>// = mutableListOf()

    /** Set the paint color and size.    */
    private fun setPaint() {
        paint.color = Color.RED
        paint.textSize = 80.0f
        paint.strokeWidth = 2.0f

        paintText.color = Color.GREEN
        paintText.textSize = 5.3f
    }

    val myColor =
        arrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.MAGENTA, Color.BLUE, Color.BLACK)
    val myColorStr =
        arrayOf("RED", "YELLOW", "GREEN", "MAGENTA", "BLUE", "BLACK")

    /** Draw bitmap on Canvas.   */
    private fun draw(canvas: Canvas, person: Person, bitmap: Bitmap): Triple<Bitmap,ArrayList<CSVMapperModel>,ArrayList<CSVMapperWithLineModel>>{
        // Draw `bitmap` and `person` in square canvas.
        val screenWidth: Int
        val screenHeight: Int
        val left: Int
        val right: Int
        val top: Int
        val bottom: Int
        if (canvas.height > canvas.width) {
            screenWidth = canvas.width
            screenHeight = canvas.width
            left = 0
            top = (canvas.height - canvas.width) / 2
        } else {
            screenWidth = canvas.height
            screenHeight = canvas.height
            left = (canvas.width - canvas.height) / 2
            top = 0
        }
        right = left + screenWidth
        bottom = top + screenHeight

        setPaint()
        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            Rect(left, top, right, bottom),
            paint
        )

        val widthRatio = screenWidth.toFloat() / MODEL_WIDTH
        val heightRatio = screenHeight.toFloat() / MODEL_HEIGHT

        mapperKP = ArrayList()
        mapperL = ArrayList()

        // Draw key points over the image.
        for (keyPoint in person.keyPoints) {
            if (keyPoint.score > minConfidence) {
                val position = keyPoint.position
                val adjustedX: Float = position.x.toFloat() * widthRatio + left
                val adjustedY: Float = position.y.toFloat() * heightRatio + top
                canvas.drawCircle(adjustedX, adjustedY, circleRadius, paint)
                //canvas.drawText(keyPoint.bodyPart.name, adjustedX-1, adjustedY+2, paintText)

                // draw circle coordinates to csv model
                val csvMapperKP = CSVMapperModel(
                    adjustedX.toInt(),
                    adjustedY.toInt(),
                    keyPoint.bodyPart.name,
                    keyPoint.score
                )
                mapperKP.add(csvMapperKP)

            }
        }

        for (line in bodyJoints) {
            if (
                (person.keyPoints[line.first.ordinal].score > minConfidence) and
                (person.keyPoints[line.second.ordinal].score > minConfidence)
            ) {
                val startX = person.keyPoints[line.first.ordinal].position.x.toFloat() * widthRatio + left
                val startY = person.keyPoints[line.first.ordinal].position.y.toFloat() * heightRatio + top
                val endX = person.keyPoints[line.second.ordinal].position.x.toFloat() * widthRatio + left
                val endY = person.keyPoints[line.second.ordinal].position.y.toFloat() * heightRatio + top

                canvas.drawLine(startX, startY, endX, endY, paint)

                // draw line coordinates to csv model 2
                val csvMapperL = CSVMapperWithLineModel(
                    person.keyPoints[line.first.ordinal].bodyPart.name,
                    startX,
                    startY,
                    person.keyPoints[line.first.ordinal].score,
                    person.keyPoints[line.second.ordinal].bodyPart.name,
                    endX,
                    endY,
                    person.keyPoints[line.second.ordinal].score
                )
                mapperL.add(csvMapperL)
            }
        }
        return Triple(bitmap,mapperKP,mapperL)
    }

    /** Crop Bitmap to maintain aspect ratio of model input.   */
    private fun resizeBitmap(bitmap: Bitmap,height:Int, width:Int): Bitmap {
        Log.i(TAG, "Crop image entry: ...")
        val bitmapRatio = bitmap.height.toFloat() / bitmap.width
        val modelInputRatio = height.toFloat() / width
        var croppedBitmap = bitmap

        // Acceptable difference between the modelInputRatio and bitmapRatio to skip cropping.
        val maxDifference = 1e-5

        // Checks if the bitmap has similar aspect ratio as the required model input.
        when {
            abs(modelInputRatio - bitmapRatio) < maxDifference -> return croppedBitmap
            modelInputRatio < bitmapRatio -> {
                // New image is taller so we are height constrained.
                val cropHeight = bitmap.height - (bitmap.width.toFloat() / modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    (cropHeight / 2).toInt(),
                    bitmap.width,
                    (bitmap.height - cropHeight).toInt()
                )
            }
            else -> {
                val cropWidth = bitmap.width - (bitmap.height.toFloat() * modelInputRatio)
                croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    (cropWidth / 2).toInt(),
                    0,
                    (bitmap.width - cropWidth).toInt(),
                    bitmap.height
                )
            }
        }
        Log.i(TAG, "Crop image exit: ...")
        return croppedBitmap
    }

    /** Process single pose on an image using Posenet library.   */
    fun processImageEstimateSinglePose(bitmap: Bitmap,boolean: Boolean): Bitmap {

        // Crop bitmap.
        //var outBitmap: Bitmap
        Log.i(TAG, "process image entry: ...")
        val croppedBitmap = resizeBitmap(bitmap,MODEL_WIDTH,MODEL_HEIGHT)
        Log.i(TAG, "Crop value: "+croppedBitmap)
        // Created scaled version of bitmap for model input.

        //val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, MODEL_WIDTH, MODEL_HEIGHT, true)
        //To resize without maintaining the ratio
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_WIDTH, MODEL_HEIGHT, true)

        // Perform inference.
        val person = posenetSingle.estimateSinglePose(scaledBitmap)

        for (keyPoint in person.keyPoints) {
            //Log.d("KEYPOINT", "Body Part : " + keyPoint.bodyPart + ", Keypoint Location : (" + keyPoint.position.x.toFloat().toString() + ", " + keyPoint.position.y.toFloat().toString() + "), Confidence" + keyPoint.score);
        }
        val canvas = Canvas(scaledBitmap)

        // Draw keypoints over the image.
        var (outBitmap,mapperkp,mapperl) = draw(canvas,person, scaledBitmap)

//        var oneListMapperKp : MutableList<ArrayList<CSVMapperModel>> = mutableListOf()
        listMapperKP  = mutableListOf()
        listMapperL  = mutableListOf()
//        var oneListMapperL : MutableList<ArrayList<CSVMapperWithLineModel>> = mutableListOf()
// call save file to another method
//        val outPutDir = FileManagerUtils.onCreateIMGOutputDir(Environment.getExternalStorageDirectory()!!.absolutePath)
        //FileManagerUtils.onSaveImg(outBitmap, "SinglePose_"+imgName, outPutDir)

        Log.i(TAG, "process image exit: ...")

        // make size inference here
        listPerson  = mutableListOf()
        listPerson.add(person)
        listMapperKP.add(mapperkp)
        listMapperL.add(mapperl)
        outBitmap = onSetInfo(listPerson, outBitmap,listMapperKP,listMapperL,boolean)
//        val heightInference = HeightInference()
//        outBitmap = heightInference.estimatPersonHeight(person, outBitmap)
//        imgWithDectection.setImageBitmap(outBitmap)
//
        return outBitmap
    }

    private fun onSetInfo(person: MutableList<Person>, outBitmap: Bitmap,mapperkp: MutableList<ArrayList<CSVMapperModel>>,mapperl: MutableList<ArrayList<CSVMapperWithLineModel>>,boolean: Boolean): Bitmap {

        // Make a local copy of the bitmap
        //var outputBitmap = outBitmap

        var originalBitmap = Bitmap.createBitmap(outBitmap)
        var outputBitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        var emptyBitmap_1 =
            Bitmap.createBitmap(outBitmap.width, outBitmap.height, Bitmap.Config.ARGB_8888)

        var emptyBitmap_2 =
            Bitmap.createBitmap(outBitmap.width, outBitmap.height, Bitmap.Config.ARGB_8888)

        //val myColor = arrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.MAGENTA, Color.BLUE, Color.BLACK)


        // init spannable string builder for size info
        formattedSizeInfo = SpannableStringBuilder()

        // set number of detected persons
        formattedSizeInfo.append("Persons found: P = ${person.size}", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("\n")

        // abbreviations
        formattedSizeInfo.append("\n")
                .append("Acronyms:\n - TH: total height\n - HH: Head Height\n - SW: Shoulder Widht\n - AL: Arm Length\n - LL: Leg Length\n",0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                .append("\n")
        //var i: Int = 0
        for (i in 0 .. person.size-1) {

                // make size inference here
                val heightInference = HeightInference()
                outputBitmap = heightInference.estimatPersonHeight(person.get(i), outputBitmap)

                //imgWithDectection.setImageBitmap(outputBitmap)  To uncomment
                if (i < 5){
                // write key points info to csv-file
                emptyBitmap_1 =
                        CSVMapperUtils.onDrawKPWithLine(mapperl.get(i), emptyBitmap_1, myColor.get(i))
                emptyBitmap_2 =
                        CSVMapperUtils.onDrawOnlyKP(mapperkp.get(i), emptyBitmap_2, myColor.get(i))

                // use written value for reconstructing person skeleton as preview
                emptyBitmap_1 = heightInference.estimatPersonHeight(person.get(i), emptyBitmap_1)
                emptyBitmap_2 = heightInference.estimatPersonHeight(person.get(i), emptyBitmap_2)

                // format data for size inference
                formatContent(person.get(i), myColorStr[i], i, heightInference)
                Log.i(TAG, "Total of persons --- in loop: ${person.size}")
        }else{
                    // write key points info to csv-file
                    emptyBitmap_1 =
                            CSVMapperUtils.onDrawKPWithLine(mapperl.get(i), emptyBitmap_1, Color.CYAN)
                    emptyBitmap_2 =
                            CSVMapperUtils.onDrawOnlyKP(mapperkp.get(i), emptyBitmap_2, Color.CYAN)

                    // use written value for reconstructing person skeleton as preview
                    emptyBitmap_1 = heightInference.estimatPersonHeight(person.get(i), emptyBitmap_1)
                    emptyBitmap_2 = heightInference.estimatPersonHeight(person.get(i), emptyBitmap_2)

                    // format data for size inference
                    formatContent(person.get(i), "CYAN", i, heightInference)
                    Log.i(TAG, "Total of persons --- in loop: ${person.size}")
                }
        }
        Log.i(TAG, "Total of persons: ${person.size}")
        // and then display the reconstruction to 2 last imageView in the horizontal scroll view

        if (boolean){
        //imgReconstructedLine.setImageBitmap(emptyBitmap_1) //To uncomment
            imgReconstructedLineBitmap = Bitmap.createBitmap(emptyBitmap_1)
        imgOnlyReconstructedBitmap = Bitmap.createBitmap(emptyBitmap_2)

            // format the csvMapper to display the content in text view
//            formattedOutput = CSVMapperUtils.showContentPreview(mapperKP)
            formattedOutput = CSVMapperUtils.showContentPreview(mapperkp)

//            //Log.i(tag, "Content: \n$content")
//            //showInfoTv.visibility = View.VISIBLE

            //showSCVContentTV.text = content  To uncomment

            //val personScore: String = String.format("Person score: %.2f", person.score * 100) + "%"
            //showPersonScoreTV.text = personScore To uncomment

            //val personHeadHeight = String.format("Head size: %.2f", heightInference.summaryInference["headHeight"])
            //estimatedHeadHeight.text = personHeadHeight To uncomment

            //val ratioA = String.format("Ratio: %.2f", heightInference.summaryInference["ratio"])
            //estimatedRatioA.text = ratioA To uncomment

            //val personSize = String.format("Size: %.2f cm", heightInference.summaryInference["totalSizeHeadToAnkle"])
            //showPersonSizeTV.text = personSize To uncomment

    }
        return outputBitmap
    }

    fun formatContent(person:Person, personColor:String, personNbr: Int, heightInferenceSummary: HeightInference){
        Log.i(TAG, "PERSONNNNE: $person")
        formattedSizeInfo.append("--------------------------------").append("\n")

        // person number, score and color
        val personScore: String = String.format("%.2f", person.score * 100) + "%"
        formattedSizeInfo.append("#-${personNbr+1} -)  score: $personScore", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("\n")
        formattedSizeInfo.append("Color: $personColor", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("\n")
        formattedSizeInfo.append("ratio (a): ${heightInferenceSummary.summaryInference["ratio"]}", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("\n")

        // body size inference
        formattedSizeInfo.append("TH: ${String.format("%.2f mm", heightInferenceSummary.summaryInference["totalSizeWithRatio"])}", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("\n")
        formattedSizeInfo.append("HH: ${String.format("%.2f mm", heightInferenceSummary.summaryInference["headHeight"])}", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("\n")
        formattedSizeInfo.append("SW: ${String.format("%.2f mm", heightInferenceSummary.summaryInference["shoulderDistance"])}", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("\n")
        formattedSizeInfo.append("AL: ${String.format("%.2f mm", heightInferenceSummary.summaryInference["armLength"])}", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("\n")
        formattedSizeInfo.append("LL: ${String.format("%.2f mm", heightInferenceSummary.summaryInference["legLength"])}", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("\n")
        formattedSizeInfo.append("--------------------------------\n")

    }


    /** Process image for Multi person estimation on an Image.   */
    fun processImageEstimateMultiplePose(bitmap: Bitmap,numPerson:Int,boolean: Boolean): Bitmap {
        // Crop bitmap.
        val outBitmap: Bitmap
        Log.i(TAG, "process image entry: ...")
        //val croppedBitmap = resizeBitmap(bitmap)
        val croppedBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_WIDTH_1, MODEL_HEIGHT_1, true);
        Log.i(TAG, "Crop value: " + croppedBitmap)
        // Created scaled version of bitmap for model input.
        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, MODEL_WIDTH_1, MODEL_HEIGHT_1, true)

        //var resizedBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_WIDTH, MODEL_HEIGHT, true);

        var allPersons : List<Person>  = posenetMultiple.estimateMultiplePose(scaledBitmap,numPerson)

        var originalBitmap1 = Bitmap.createBitmap(scaledBitmap)
        var originalBitmap2: Bitmap = originalBitmap1.copy(Bitmap.Config.ARGB_8888, true)

        //var myColor = arrayOf(Color.BLUE, Color.YELLOW, Color.GREEN, Color.MAGENTA,Color.RED,Color.BLACK ,)

        //var myColor = arrayOf(Color.RED, Color.RED, Color.RED, Color.RED,Color.RED ,Color.RED)

        var colorCounter : Int = 0

        listMapperKP  = mutableListOf()
        listMapperL = mutableListOf()

        listPerson = mutableListOf()

        for(p in allPersons){

            paint.color = myColor[colorCounter]
            val(newBitmap,mapperkp,mapperl) = drawPersonKeypointsOnBitmap(originalBitmap2, p, paint)
            originalBitmap2 = Bitmap.createBitmap(newBitmap)
            listMapperKP.add(mapperkp)
            listMapperL.add(mapperl)
            listPerson.add(p)
            colorCounter++
        }

        if(boolean) {
            originalBitmap2 = onSetInfo(listPerson, originalBitmap2, listMapperKP, listMapperL, boolean)
        }

        Log.i(TAG, "number of persons found: ${allPersons.size}")

        //val outPutDir = FileManagerUtils.onCreateIMGOutputDir(Environment.getExternalStorageDirectory()!!.absolutePath)
        //FileManagerUtils.onSaveImg(originalBitmap2, "MultiplePose_"+imgName, outPutDir)
        //imgReconstructedLine.setImageBitmap(originalBitmap2)

        val canvas = Canvas(scaledBitmap)

        // Draw keypoints over the image.
        Log.i(TAG, "process image exit: ...")

        return originalBitmap2
    }

    //List with all detected Person Location on a Bitmap
    fun countAllPersonOnImage(bitmap: Bitmap): MutableList<Classifier.Recognition> {
        var croppedBitmap = Bitmap.createScaledBitmap(bitmap, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, true);

        val results: List<Classifier.Recognition>
        var personResults: MutableList<Classifier.Recognition> = mutableListOf()
        results = detector?.recognizeImage(croppedBitmap) as List<Classifier.Recognition>

        var location: RectF? = null
        var minimumConfidence: Float = MINIMUM_CONFIDENCE_TF_OD_API
        for (result in results) {
            //location: RectF = result.getLocation()
            location = result.getLocation()
            if (location != null && result.getConfidence() >= minimumConfidence && result.title.equals(
                    "person"
                )) {
                personResults.add(result)
                Log.d(TAG, "######One Persone Detected")
            }
        }

        return personResults
    }

    //dectect all persons on an Image and draw for every person the keypoints : case Detection + estimation
    fun detectPersonsOnImage(bitmap: Bitmap, boolean: Boolean):Bitmap{

        //var croppedBitmap = resizeBitmap(bitmap, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE);  //Not used because of bad ration during testing
        var croppedBitmap = Bitmap.createScaledBitmap(bitmap, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, true);

        val results: List<Classifier.Recognition>

        results = countAllPersonOnImage(bitmap)

        val cropCopyBitmap = Bitmap.createBitmap(croppedBitmap)
        val canvas = Canvas(cropCopyBitmap)
        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        cropToFrameTransform = Matrix()

        val mappedRecognitions: MutableList<Classifier.Recognition> =
            LinkedList<Classifier.Recognition>()
        var location: RectF? = null

        var originalBitmap :Bitmap = Bitmap.createBitmap(bitmap)
        val cropBitmap = Bitmap.createBitmap(croppedBitmap)

        listMapperKP = mutableListOf()
        listMapperL = mutableListOf()
        listPerson = mutableListOf()

        for (result in results) {
            location = result.getLocation()
            canvas.drawRect(location, paint)
            cropToFrameTransform!!.mapRect(location)
            result.setLocation(location)
            mappedRecognitions.add(result)

            Log.d(TAG, "######One Persone Detected")
            //Log.d(TAG,"Location:"+result)
            Log.d(TAG,"Location:"+location)
            originalBitmap = personKeypointsOnBitmap(originalBitmap, cropBitmap, location) //To Uncomment

        }
        Log.i(TAG, "++++++++++++++++++++++++++++++++ Totoaaaaaaaaaaal 2: ${listPerson.size}")
//        val outPutDir = FileManagerUtils.onCreateIMGOutputDir(Environment.getExternalStorageDirectory()!!.absolutePath)
        //FileManagerUtils.onSaveImg(originalBitmap, "Detection_"+imgName, outPutDir)
        //imgOnlyReconstructed.setImageBitmap(originalBitmap)

        // set info here
        if (boolean){
            originalBitmap = onSetInfo(listPerson, originalBitmap, listMapperKP, listMapperL, boolean)
        }
        return  originalBitmap

    }

    //estimate the single pose on a detected person on an Image
    private fun personKeypointsOnBitmap(originalBitmap: Bitmap, cropBitmap: Bitmap, rectF: RectF):Bitmap{


        val paint = Paint()
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f

        val l:Int;
        val t:Int;
        val r:Int;
        val b:Int;
        val w:Int;
        val h:Int;

        l = rectF.left.toInt()
        t = rectF.top.toInt()
        r = rectF.right.toInt()
        b = rectF.bottom.toInt()
        w = rectF.width().toInt()
        h = rectF.height().toInt()

        var rect = Rect(l, t, r, b)
        //Log.d(TAG,"Rectangle:"+rect)

        var Rx:Float = 0F
        var Ry:Float =0F
        Rx = cropBitmap.width.toFloat() / originalBitmap.width.toFloat()
        Ry = cropBitmap.height.toFloat() / originalBitmap.height.toFloat()
        var newRect:Rect
        newRect = Rect((l / Rx).toInt(), (t / Ry).toInt(), (r / Rx).toInt(), (b / Ry).toInt())

        var originalBitmap1 = Bitmap.createBitmap(originalBitmap)
        var originalBitmap2: Bitmap = originalBitmap1.copy(Bitmap.Config.ARGB_8888, true)
        val canvas1 = Canvas(originalBitmap2)

        var cropBitmap1 = cutBitmap(originalBitmap, newRect)

        //var resizedcropBitmap1= resizeBitmap(cropBitmap1!!, MODEL_WIDTH, MODEL_HEIGHT); //not used, bad ration in tests

        var resizedcropBitmap1 = Bitmap.createScaledBitmap(cropBitmap1!!, MODEL_WIDTH, MODEL_HEIGHT, true)

        var (cropBitmap2, person) = ProcessImageDetected(resizedcropBitmap1!!);

        listPerson.add(person)
        val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

        var randomString = (1..5)
            .map { i -> kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("");

        var randomString1 = (1..5)
            .map { i -> kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("");

        randomString = randomString+".PNG"

        randomString1 = randomString1+".PNG"

//        val outPutDir = FileManagerUtils.onCreateIMGOutputDir(Environment.getExternalStorageDirectory()!!.absolutePath)
        //FileManagerUtils.onSaveImg(cropBitmap1, randomString, outPutDir)
        //FileManagerUtils.onSaveImg(cropBitmap2, randomString1, outPutDir)

        var Rx1:Float = 0F
        var Ry1:Float =0F
        Rx1 = cropBitmap2.width.toFloat() / cropBitmap1!!.width.toFloat()
        Ry1 = cropBitmap2.height.toFloat() / cropBitmap1!!.height.toFloat()
        //Log.d(TAG,"Rx1:"+Rx1)
        // Log.d(TAG,"Ry1:"+Ry1)
//        var listMapperKP :MutableList<ArrayList<CSVMapperModel>> = mutableListOf()
//        var listMapperL : MutableList<ArrayList<CSVMapperWithLineModel>> = mutableListOf()
//        var listPerson : MutableList<Person> = mutableListOf()

        var person1:Person = person
        var i:Int =0
        for (i in 0..person.keyPoints.size-1) {
            person1.keyPoints[i].bodyPart = person.keyPoints[i].bodyPart
            person1.keyPoints[i].score = person.keyPoints[i].score
            person1.keyPoints[i].position.x = (person.keyPoints[i].position.x/Rx1).toInt()
            //p1.keyPoints[i].position.x = p1.keyPoints[i].position.x + newRect.left
            person1.keyPoints[i].position.y = (person.keyPoints[i].position.y/Ry1).toInt()
            //p1.keyPoints[i].position.y = p1.keyPoints[i].position.y + newRect.top
        }

        for (i in 0..person1.keyPoints.size-1) {
            person1.keyPoints[i].position.x = person1.keyPoints[i].position.x + newRect.left
            person1.keyPoints[i].position.y = person1.keyPoints[i].position.y + newRect.top
        }

        paint.color = Color.RED

        //return originalBitmap2;
        val(newBitmap,mapperkp,mapperl) = drawPersonKeypointsOnBitmap(originalBitmap2, person1, paint)

        // update mutable lists
        // listPerson.add(person1)
        listMapperL.add(mapperl)
        listMapperKP.add(mapperkp)

        Log.i(TAG, "++++++++++++++++++++++++++++++++ Totoaaaaaaaaaaal 1: ${listPerson.size}, score: ${person.score} +++++++++++++++++++++++++++++++")
        originalBitmap2 = Bitmap.createBitmap(newBitmap)
        //originalBitmap2 = onSetInfo(person1, originalBitmap2)
        return originalBitmap2

    }

    private fun ProcessImageDetected(bitmap: Bitmap): Pair<Bitmap, Person> {
        // Crop bitmap.
        var outBitmap: Bitmap = Bitmap.createBitmap(bitmap)
        Log.i(TAG, "process new image entry: ...")

        val p: Person  = posenetSingle.estimateSinglePose(outBitmap)

        /*** Make size estimation here for detection and estimation **/
        // draw first
//        val canvas = Canvas(outBitmap)
//        outBitmap = draw(canvas, p, outBitmap)
//        outBitmap = onSetInfo(p, outBitmap)
        Log.i(TAG, "process image exit: ...")

        return Pair(outBitmap, p)
    }

    // map info value for size detection
//    fun onMapDataforDetection()
    //draw the given person keypoints on the given bitmap
    private fun drawPersonKeypointsOnBitmap(bitmap: Bitmap, person: Person, paint: Paint,):Triple<Bitmap,ArrayList<CSVMapperModel>,ArrayList<CSVMapperWithLineModel>>{

        var originalBitmap1 = Bitmap.createBitmap(bitmap)
        val originalBitmap2: Bitmap = originalBitmap1.copy(Bitmap.Config.ARGB_8888, true)

        Log.d(TAG,"#########Drawing on Bitmap size: ${originalBitmap2.width.toString()}  x  ${originalBitmap2.height.toString()}")

        val canvas2 = Canvas(originalBitmap2)
        //newnewCroppedBitmap = draw(canvas2,p1,testBitmap2)
//
//        // init or re-init variables
        mapperKP = ArrayList()
        mapperL = ArrayList()

        var mapPerson : MutableMap<BodyPartEnum, KeyPoint> = mutableMapOf()

        for (keyPoint in person.keyPoints) {
            if (keyPoint.score > minConfidence) {
                mapPerson[keyPoint.bodyPart]=keyPoint
                val position = keyPoint.position
                val adjustedX: Float = position.x.toFloat()
                val adjustedY: Float = position.y.toFloat()
                canvas2.drawCircle(adjustedX, adjustedY, circleRadius, paint)
                //canvas2.drawText(keyPoint.bodyPart.name, adjustedX-1, adjustedY+2, paintText)
                // draw circle coordinates to csv model
                val csvMapperKP = CSVMapperModel(
                    adjustedX.toInt(),
                    adjustedY.toInt(),
                    keyPoint.bodyPart.name,
                    keyPoint.score
                )
                mapperKP.add(csvMapperKP)
            }
        }
        for (keyp in mapPerson.values){
            //Log.d(TAG,"Keypoint added to the map:"+keyp!!.bodyPart)
        }
        Log.d(TAG, "Keypoints size for line:" + person.keyPoints.size)
        Log.d(TAG, "Line size:" + bodyJoints.size)
        for (line in bodyJoints) {

            if ((mapPerson.containsKey(line.first) == true) && (mapPerson.containsKey(line.second) == true) )
            {

                if (
                    ((mapPerson.get(line.first)?.score!!  >= minConfidence ) && (mapPerson.get(line.second)?.score!!>= minConfidence))

                ) {
                    val startX = mapPerson.get(line.first)!!.position.x.toFloat()
                    val startY = mapPerson.get(line.first)!!.position.y.toFloat()
                    val endX = mapPerson.get(line.second)!!.position.x.toFloat()
                    val endY = mapPerson.get(line.second)!!.position.y.toFloat()

                    canvas2.drawLine(startX, startY, endX, endY, paint)

                    // draw line coordinates to csv model 2
                    val csvMapperL = CSVMapperWithLineModel(
                        person.keyPoints[line.first.ordinal].bodyPart.name,
                        startX,
                        startY,
                        person.keyPoints[line.first.ordinal].score,
                        person.keyPoints[line.second.ordinal].bodyPart.name,
                        endX,
                        endY,
                        person.keyPoints[line.second.ordinal].score
                    )
                    mapperL.add(csvMapperL)
                }
                else{
                    //Log.d(TAG, "pair not drawn with low confidence:" + line)
                }
            }
            else{
                //Log.d(TAG, "pair not found:" + line)
            }
        }

        // set heigh info here
        //var outputBitmap = originalBitmap2
        //outputBitmap = onSetInfo(person, originalBitmap2)

        return  Triple(originalBitmap2,mapperKP,mapperL)
    }

    //cut a rectangle in a Bitmap
    private fun cutBitmap(bitmap: Bitmap, rect: Rect): Bitmap? {
        val origialBitmap = bitmap
        //Log.d(TAG,"Rect:"+rect)
        val cutBitmap = Bitmap.createBitmap(
            rect?.width()?.toInt()!!,
            rect.height().toInt(), Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(cutBitmap)
        val desRect = Rect(0, 0, rect?.width()?.toInt()!!, rect.height().toInt())
        val srcRect = Rect(
            rect?.left?.toInt()!!, rect.top.toInt(), rect.right.toInt(),
            rect.bottom.toInt()
        )
        canvas.drawBitmap(origialBitmap, srcRect, desRect, null)
        return cutBitmap
    }



}
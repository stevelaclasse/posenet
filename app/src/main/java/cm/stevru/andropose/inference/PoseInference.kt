package cm.stevru.andropose.inference

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import cm.stevru.andropose.bodypart.KeyPoint
import cm.stevru.andropose.bodypart.Person
import cm.stevru.andropose.bodypart.enums.BodyPartEnum
import cm.stevru.andropose.bodypart.enums.DeviceEnum
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

class PoseInference(val context:Context, val fileNameSinglePose: String="posenet_model_v1.tflite",val fileNameMultiplePose: String="posenet_mv1_075_float_from_checkpoints.tflite", val device: DeviceEnum = DeviceEnum.CPU): AutoCloseable {

    var lastInferenceTimeNanos: Long = -1
        private set
    private val TAG = "PoseInference"
    /** An Interpreter for the TFLite model.   */
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val NUM_LITE_THREADS = 4

    private fun getInterpreter(i:Int): Interpreter {
        if (interpreter != null) {
            return interpreter!!
        }
        val options = Interpreter.Options()
        options.setNumThreads(NUM_LITE_THREADS)
        when (device) {
            DeviceEnum.CPU -> { }
            DeviceEnum.GPU -> {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
            }
            DeviceEnum.NNAPI -> options.setUseNNAPI(true)
        }
        if(i==1) {
            interpreter = Interpreter(loadModelFile(fileNameSinglePose, context), options)
            Log.d(TAG,"Single Pose selected")
        }
        else {
            interpreter = Interpreter(loadModelFile(fileNameMultiplePose, context), options)
            Log.d(TAG,"Multiple Pose selected")
        }

        return interpreter!!
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    /** Returns value within [0,1].   */
    private fun sigmoid(x: Float): Float {
        return (1.0f / (1.0f + exp(-x)))
    }

    /**
     * Scale the image to a byteBuffer of [-1,1] values.
     */
    private fun initInputArray(bitmap: Bitmap,i :Int): ByteBuffer {
        val bytesPerChannel = 4
        val inputChannels = 3
        val batchSize = 1
        val inputBuffer = ByteBuffer.allocateDirect(
            batchSize * bytesPerChannel * bitmap.height * bitmap.width * inputChannels
        )
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()
        var mean = 0.0f
        var std = 0.0f

        if (i == 1){  //Single Pose Image Model
        mean = 128.0f
        std = 128.0f
    }
        else{ //Multiple Pose Model
                 mean = 125.0f
                 std = 125.0f
        }
        val intValues = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) - mean) / std)
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) - mean) / std)
            inputBuffer.putFloat(((pixelValue and 0xFF) - mean) / std)
        }
        return inputBuffer
    }

    /** Preload and memory map the model file, returning a MappedByteBuffer containing the model. */
    private fun loadModelFile(path: String, context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength
        )
    }

    /**
     * Initializes an outputMap of 1 * x * y * z FloatArrays for the model processing to populate.
     */
    private fun initOutputMap(interpreter: Interpreter): HashMap<Int, Any> {
        val outputMap = HashMap<Int, Any>()




        // 1 * 9 * 9 * 17 contains heatmaps
/*        val heatmapsShape = interpreter.getOutputTensor(0).shape()
        outputMap[0] = Array(heatmapsShape[0]) {
            Array(heatmapsShape[1]) {
                Array(heatmapsShape[2]) { FloatArray(heatmapsShape[3]) }
            }
        }*/

        // 1 * 9 * 9 * 34 contains offsets
/*        val offsetsShape = interpreter.getOutputTensor(1).shape()
        outputMap[1] = Array(offsetsShape[0]) {
            Array(offsetsShape[1]) { Array(offsetsShape[2]) { FloatArray(offsetsShape[3]) } }
        }*/

        // 1 * 9 * 9 * 32 contains forward displacements
/*        val displacementsFwdShape = interpreter.getOutputTensor(2).shape()
        outputMap[2] = Array(offsetsShape[0]) {
            Array(displacementsFwdShape[1]) {
                Array(displacementsFwdShape[2]) { FloatArray(displacementsFwdShape[3]) }
            }
        }*/

        // 1 * 9 * 9 * 32 contains backward displacements
/*        val displacementsBwdShape = interpreter.getOutputTensor(3).shape()
        outputMap[3] = Array(displacementsBwdShape[0]) {
            Array(displacementsBwdShape[1]) {
                Array(displacementsBwdShape[2]) { FloatArray(displacementsBwdShape[3]) }
            }
        }*/


        for (i in 0 until interpreter.getOutputTensorCount()) {
            val shape: IntArray = interpreter.getOutputTensor(i).shape()
            val output = Array(shape[0]) {
                Array(shape[1]) {
                    Array(shape[2])
                    { FloatArray(shape[3]) }
                }
            }
            outputMap[i] = output
        }

        //Log.i(TAG, "heatmap: ["+heatmapsShape+"]")
        return outputMap
    }

    /**
     * Estimates the pose for a single person.
     * args:
     *      bitmap: image bitmap of frame that should be processed
     * returns:
     *      person: a Person object containing data about keypoint locations and confidence scores
     */
    @Suppress("UNCHECKED_CAST")
    fun estimateSinglePose(bitmap: Bitmap): Person {
        val estimationStartTimeNanos = SystemClock.elapsedRealtimeNanos()
        val inputArray = arrayOf(initInputArray(bitmap,1))
        /*Log.i(
            TAG,
            String.format(
                "Scaling to [-1,1] took %.2f ms",
                1.0f * (SystemClock.elapsedRealtimeNanos() - estimationStartTimeNanos) / 1_000_000
            )
        )*/

        val outputMap = initOutputMap(getInterpreter(1))

        val inferenceStartTimeNanos = SystemClock.elapsedRealtimeNanos()
        getInterpreter(1).runForMultipleInputsOutputs(inputArray, outputMap)
        lastInferenceTimeNanos = SystemClock.elapsedRealtimeNanos() - inferenceStartTimeNanos
      /*  Log.i(
            TAG,
            String.format("Interpreter took %.2f ms", 1.0f * lastInferenceTimeNanos / 1_000_000)
        )*/

        val heatmaps = outputMap[0] as Array<Array<Array<FloatArray>>>
        val offsets = outputMap[1] as Array<Array<Array<FloatArray>>>
        val height = heatmaps[0].size
        val width = heatmaps[0][0].size
        val numKeypoints = heatmaps[0][0][0].size

        // Finds the (row, col) locations of where the keypoints are most likely to be.
        val keypointPositions = Array(numKeypoints) { Pair(0, 0) }
        for (keypoint in 0 until numKeypoints) {
            var maxVal = heatmaps[0][0][0][keypoint]
            var maxRow = 0
            var maxCol = 0
            for (row in 0 until height) {
                for (col in 0 until width) {
                    if (heatmaps[0][row][col][keypoint] > maxVal) {
                        maxVal = heatmaps[0][row][col][keypoint]
                        maxRow = row
                        maxCol = col
                    }
                }
            }
            keypointPositions[keypoint] = Pair(maxRow, maxCol)
        }

        var i:Int

        for (i in 0..keypointPositions.size-1){
            //Log.d(TAG,"######Keypoint:"+ keypointPositions[i].toString())
        }

        // Calculating the x and y coordinates of the keypoints with offset adjustment.
        val xCoords = IntArray(numKeypoints)
        val yCoords = IntArray(numKeypoints)
        val confidenceScores = FloatArray(numKeypoints)
        keypointPositions.forEachIndexed { idx, position ->
            val positionY = keypointPositions[idx].first
            val positionX = keypointPositions[idx].second
            //Log.d(TAG,"positionX="+positionX);
            //Log.d(TAG,"positionY="+positionY);
            yCoords[idx] = (
                    position.first / (height - 1).toFloat() * bitmap.height +
                            offsets[0][positionY][positionX][idx]
                    ).toInt()
            var t:Float = (height - 1).toFloat() * bitmap.height

            xCoords[idx] = (
                    position.second / (width - 1).toFloat() * bitmap.width +
                            offsets[0][positionY]
                                    [positionX][idx + numKeypoints]
                    ).toInt()
            var d:Float = (width - 1).toFloat() * bitmap.width

            confidenceScores[idx] = sigmoid(heatmaps[0][positionY][positionX][idx])

        }

        val person = Person()
        val keypointList = Array(numKeypoints) { KeyPoint() }
        var totalScore = 0.0f
        enumValues<BodyPartEnum>().forEachIndexed { idx, it ->
            keypointList[idx].bodyPart = it
            keypointList[idx].position.x = xCoords[idx]
            keypointList[idx].position.y = yCoords[idx]
            keypointList[idx].score = confidenceScores[idx]
            totalScore += confidenceScores[idx]
        }

        person.keyPoints = keypointList.toList()
        person.score = totalScore / numKeypoints
       person.keyPoints.forEach { k ->
            //Log.i(TAG, "Part:["+k.bodyPart+"], Pos: ("+k.position.x+", "+k.position.y+"), score::-> "+k.score)
        }
        return person
    }

    fun estimateMultiplePose(bitmap: Bitmap,numPerson:Int):List<Person>{

        val inputArray = arrayOf(initInputArray(bitmap,2))

        val outputMap = initOutputMap(getInterpreter(2))

        getInterpreter(2).runForMultipleInputsOutputs(inputArray, outputMap)
        var decodePose :DecodePose
        decodePose = DecodePose(numPerson)
        var allPersons : List<Person>  = decodePose.decodeOutput(outputMap)

        return allPersons
    }

}
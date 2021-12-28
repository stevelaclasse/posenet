package cm.stevru.andropose.fragments

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import cm.stevru.andropose.BuildConfig
import cm.stevru.andropose.R
import cm.stevru.andropose.classifier.Classifier
import cm.stevru.andropose.classifier.TFLiteObjectDetectionAPIModel
import cm.stevru.andropose.inference.PoseInference
import cm.stevru.andropose.utilities.FileManagerUtils
import cm.stevru.andropose.utilities.ImageProcessing
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import nl.bravobit.ffmpeg.FFtask
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.*
import java.io.*
import java.util.*


class VideoFragment : Fragment() {

    lateinit var imageProcessing : ImageProcessing

    private lateinit var videoView: VideoView
    private lateinit var reconstView : VideoView

//    private lateinit var textView: TextView
    private lateinit var loadBtn: Button
    private lateinit var detectBtn: Button
    private lateinit var playBtn: Button

    var applicationContext: Context? = null
    var videoUri: Uri? = null
    var videoOutputUri : Uri? = null
    var mediaControls: MediaController? = null
    var mediaControls_1: MediaController? = null

    var videoName: String? = null
    var absVideoPath: String? = null
    var inputVideoFilePath: String? = null
    var outputVideoFilePath: String? = null
    var outputNoSoundVideoFilePath: String? = null
    var outputAudioFilePath: String? = null

    var privateOutputVideoFilePath: String? = null

    private lateinit var selectModelRdioG             : RadioGroup
    private lateinit var selectedModelValueRdio       : RadioButton
    private lateinit var defineNumOfPose              : NumberPicker


    /** An object for the Posenet library.    */
    lateinit var posenetSingle                    : PoseInference
    lateinit var posenetMultiple                    : PoseInference


    /** An additional thread for running tasks that shouldn't block the UI.   */
    private var backgroundThread: HandlerThread? = null

    /** A [Handler] for running tasks in the background.    */
    private var backgroundHandler: Handler? = null

    private var progressThread: Thread? = null


    private val handler = Handler()

    var switchState = false
    var showHideInfoState = false
    var modelChoose: Int = 0

    lateinit var toastLayout: ViewGroup

    //Detector Initialisation
    private var detector: Classifier? = null
    // Minimum detection confidence to track a detection.
    var MINIMUM_CONFIDENCE_TF_OD_API = 0.5f
    private val TF_OD_API_INPUT_SIZE = 300
    private val TF_OD_API_IS_QUANTIZED = true
    private val TF_OD_API_MODEL_FILE = "detect.tflite"
    private val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"
    var cropSize: Int = TF_OD_API_INPUT_SIZE

    // progress bar
    private lateinit var progressBar: ProgressBar
    private lateinit var progressMsg: TextView


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_video, container, false)
        loadFFMpegBinary()
        initViews(view)
        setSelectModelListener()
        setBtnLoadListener()
        onDetectBtnClicked()
        onPlayBtnClicked()
        onCheckPermissions() // check for permissions
        initDetector()
        return view
    }

    private fun initDetector(){

        try
        {
            detector = TFLiteObjectDetectionAPIModel.create(
                getTheApplicationContext()?.getAssets(),
                TF_OD_API_MODEL_FILE,
                TF_OD_API_LABELS_FILE,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_IS_QUANTIZED
            )
            cropSize = TF_OD_API_INPUT_SIZE
            Log.d(TAG, "initializing classifier! Good")
        }catch (e: java.io.IOException)
        {
            Log.d(TAG, "Exception initializing classifier!")

        }
    }

    private fun initViews(view: View) {
        Log.i(TAG, "Init views: VIDEO")
        videoView = view.findViewById(R.id.video_view_id)
        // reconstruction
        //reconstView = view.findViewById(R.id.video_view_reconst_id)
//        textView = view.findViewById(R.id.text_info_vid_id)
        //textView.text = ""
        Log.i(TAG, "Setting text view to invisible")
//        Log.i(
//            TAG,
//            "Before: TXTV: [${textView.visibility}], View States: [${View.VISIBLE}, ${View.INVISIBLE}, ${View.GONE}"
//        )
//        textView.visibility = View.GONE
//        Log.i(
//            TAG,
//            "After: TXTV: [${textView.visibility}], View States: [${View.VISIBLE}, ${View.INVISIBLE}, ${View.GONE}"
//        )

        loadBtn = view.findViewById(R.id.load_vid_btn_id)
        detectBtn = view.findViewById(R.id.detect_vid_btn_id)

        playBtn = view.findViewById(R.id.button_play_id)

        selectModelRdioG = view.findViewById(R.id.select_model_grp_id)
        modelChoose = selectModelRdioG.checkedRadioButtonId // get default selected

        defineNumOfPose = view.findViewById(R.id.number_of_detection_picker_id)
        val valuesPickers = arrayOf("-1", "1", "2", "3", "4", "5")
        defineNumOfPose.minValue = 0
        defineNumOfPose.maxValue = valuesPickers.size - 1
        defineNumOfPose.displayedValues = valuesPickers

        if (mediaControls == null) {
            // creating an object of media controller class
            mediaControls = MediaController(applicationContext)
            onSetMediaControl(mediaControls!!, videoView)
            //mediaControls!!.setAnchorView(videoView) // set anchor view for the video view
            Log.i(TAG, "Media control for video view set")
        }

        if (mediaControls_1 == null) {
            // creating an object of media controller class
            mediaControls_1 = MediaController(applicationContext)
            //onSetMediaControl(mediaControls_1!!, reconstView)
            //mediaControls_1!!.setAnchorView(reconstView) // set anchor view for the video view
            Log.i(TAG, "Media control 1 for video view set")
        }

        // make the video view for output the inference as invisible
        //reconstView.visibility = View.GONE

        Log.i(TAG, "Done!")

        // init toast inflater
        val inflater = layoutInflater
//        val container: ViewGroup = view.findViewById(R.id.toast_layout_id)
        toastLayout = inflater.inflate(
            R.layout.toast_msg_layout,
            activity!!.findViewById(R.id.toast_layout_id)
        ) as ViewGroup

        // init progress bar and attach it dynamically to the video context
        progressBar = view.findViewById(R.id.progress_bar_vid_id)
        progressBar.visibility = View.GONE

        progressMsg = view.findViewById(R.id.progress_text_id)
        progressMsg.visibility = View.GONE
        playBtn.visibility = View.GONE
        //progressBar = ProgressBar(applicationContext)
    }

    // set media control
    private fun onSetMediaControl(mediaController: MediaController, videoView: VideoView){
        mediaController.setAnchorView(videoView)
    }

    // init progress bar UI
    fun onSetProgressBar(view: View){
        progressBar = ProgressBar(activity)

    }
    fun setBtnLoadListener() {
        loadBtn.setOnClickListener {
            Log.i(TAG, "OnclickLister: Enter")
         /*  val intent: Intent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Video.Media.INTERNAL_CONTENT_URI
            )*/

            val intent: Intent

            if (Build.VERSION.SDK_INT < 19) {
                intent = Intent(Intent.ACTION_PICK)
                intent.type = "video/*"
                //startActivityForResult(photoPickerIntent, REQUEST_CODE_GALLERY_FILES)
            } else {
                intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "video/*"
                //startActivityForResult(photoPickerIntent, REQUEST_CODE_GALLERY_FILES)
            }
            startActivityForResult(intent, VIDEO_REQUEST)
            Log.i(TAG, "INTENT REQUEST STARTED")
        }
    }

    // check radiobutton check state
    fun setSelectModelListener(){
//        modelChoose = selectModelRdioG.checkedRadioButtonId //reset twice
//        selectModelRdioG.clearCheck() // clear all checks
//        selectModelRdioG.check(modelChoose) // set the current selection
        selectModelRdioG.setOnCheckedChangeListener { radioGroup, i ->
            run {
                Log.i(TAG, "Value of i:  $i")
                radioGroup.check(i)
                modelChoose = radioGroup.checkedRadioButtonId
                selectedModelValueRdio = view!!.findViewById(modelChoose)
                val modelChooseStr: String = selectedModelValueRdio.text.toString()
                if (modelChooseStr.toLowerCase().contains("single")){
                    defineNumOfPose.visibility = View.GONE
                    defineNumOfPose.value = 1
//                    Log.i(TAG, "Single model:  $modelChooseStr, picker val: ${defineNumOfPose.value}, choice: $modelChoose")
                }else if (modelChooseStr.toLowerCase().contains("detection")){
                    defineNumOfPose.visibility = View.GONE
                    defineNumOfPose.value = 1
//                    Log.i(TAG, "Detection, picker val: ${defineNumOfPose.value}, choice: $modelChoose")
                }else{
//                    Log.i(TAG, "Multi or?!:  $modelChooseStr, choice: $modelChoose")

                    defineNumOfPose.visibility = View.VISIBLE
                    defineNumOfPose.setOnValueChangedListener { numberPicker, i, i2 ->
                        run {
                            // declare a global variable to get value on change???
                            Log.i(
                                TAG,
                                "i: $i,  i2: $i2,  valuePicked: ${numberPicker.value}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun onPlayBtnClicked(){
        playBtn.setOnClickListener{
            playVideoWithExternalApp()
        }
    }
    fun onDetectBtnClicked() {
        detectBtn.setOnClickListener {

            selectedModelValueRdio = view!!.findViewById(modelChoose)

            val modelChooseStr: String = selectedModelValueRdio.text.toString()

            var captureVideoType = 3

            if (inputVideoFilePath != null && File(inputVideoFilePath).exists()) {
                try {
                    Log.i(TAG, "MyVidURI on DetectButtonClicked:" + videoUri.toString());

                    when {
                        modelChooseStr.toLowerCase(Locale.ROOT).contains("single") -> {
                            Log.i(
                                TAG,
                                "Seletion Single:  ${selectedModelValueRdio.text}, picker: ${defineNumOfPose.value}"
                            )
                            captureVideoType = 1
                            //val singlePoseEstimationBitmap: Bitmap = processImageEstimateSinglePose(bitmap)

                            showHideInfoState = true
                        }
                        modelChooseStr.toLowerCase(Locale.ROOT).contains("detection") -> {
                            Log.i(
                                TAG,
                                "Seletion Detection:  ${selectedModelValueRdio.text}, picker: ${defineNumOfPose.value}"
                            )

                            captureVideoType = 2

                            showHideInfoState = true
                        }
                        modelChooseStr.toLowerCase(Locale.ROOT).contains("multi") -> {
                            Log.i(
                                TAG,
                                "Seletion Multi:  ${selectedModelValueRdio.text}, picker: ${defineNumOfPose.value}"
                            )

                            captureVideoType = 3

                            showHideInfoState = true
                        }
                    }

                    Log.i(TAG, "View displayed")

                    Log.i(TAG, "OnClickListener onDetect: Enter")

                    val job = Thread(Runnable {

                        Log.i(TAG, "Entry THREAD ---------------------------")
// progress ************************************************
                        activity?.runOnUiThread {
                            progressBar.visibility = View.VISIBLE
                            progressBar.isIndeterminate = false

                            loadBtn.isClickable = false
                            loadBtn.isEnabled = false
                            detectBtn.isClickable = false
                            detectBtn.isEnabled = false

                            progressMsg.visibility = View.VISIBLE
                            //reconstView.visibility = View.GONE

                            playBtn.visibility = View.GONE

                        }

                        extractAudiomp3FromVideo(absVideoPath, videoName)

                        captureVideo(absVideoPath, videoName, captureVideoType)

                        addAudiomp3ToVideomp4(
                            outputAudioFilePath,
                            outputNoSoundVideoFilePath,
                            captureVideoType
                        )

                        activity?.runOnUiThread {
                            progressBar.visibility = View.GONE
                            progressBar.isIndeterminate = false

                            progressMsg.visibility = View.GONE
                            progressMsg.text = ""

                            loadBtn.isClickable = true
                            loadBtn.isEnabled = true
                            detectBtn.isClickable = true
                            detectBtn.isEnabled = true

                            //reconstView.visibility = View.VISIBLE
                            //reconstView.setMediaController(mediaControls_1)
                            //reconstView.setVideoPath(outputVideoFilePath)
                        }
                    })

                    job.start()

                    job.join(100)

                    // second toast
                    onShowToast("Click on \"PLAY\" after the process is terminated.\n" +
                            " The video will be opened using an external media player!",
                            "warning", 5)
                } catch (e: NullPointerException) {
                    onShowToast("No Video has been loaded")
//                Toast.makeText(context, "No image has been loaded!", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "Bitmap is null: ${e.message}")
                }
                Log.i(TAG, "Process terminated!")

            }else{
                onShowToast("No Video has been load! Please load a video at first!")
            }
            //val outputDir = FileManagerUtils.onCreateVIDOutputDir(absVideoPath)
        }

    }

fun setVideoOutputReadable(videoPath: String){
    try {
        if (File(videoPath).exists()) {
            val cw = ContextWrapper(getTheApplicationContext())
            val directory = cw.getExternalFilesDir("")
            //val inputStream: InputStream = directory.inputStream()
            val inputStream: InputStream = FileInputStream(videoPath)
            val fos = FileOutputStream(outputVideoFilePath, true)

            val buffer = ByteArray(inputStream.available())
            inputStream.read(buffer)
            // write the stream to file
            fos.write(buffer, 0, buffer.size)
            fos.close()
            inputStream.close()
            Log.i(TAG, "Video set Readable Complete" + videoPath)

        }
        else{
            Log.i(TAG, "Could not perform Video set Readable" + videoPath)
        }

    } catch (ex: java.lang.Exception) {
        ex.printStackTrace()
        Log.i(TAG, "Error on Video set Readable" + videoPath)
    }
}
    // Permissions for media access

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult: Entry (Video)")
        if (resultCode == Activity.RESULT_OK && requestCode == VIDEO_REQUEST) {
            Log.i(TAG, "REQUEST CODE OK")

            var videoname: String
            var videoextension: String

            var tmpFile: File? =null

            videoUri = data?.data

            if((Build.VERSION.SDK_INT < 19)){

            val projection = arrayOf(MediaStore.Video.Media.DATA)
            Log.i(TAG, "Video Uri: [$videoUri],\n projection: [$projection]")


            val cursor: Cursor? = videoUri?.let {
                applicationContext?.contentResolver?.query(
                    it, projection, null, null, null
                )
            }
            Log.i(TAG, "Cursor: [$cursor]")

            if (cursor != null && cursor.moveToFirst()) {
                Log.i(TAG, "Cursor is not null and it is moved to first (Video)")
                try {
                    val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    val videoPath = cursor.getString(idx)
                    Log.i(TAG, "idx: $idx, --> video path: [$videoPath]")
                    Log.i(TAG, "Media control: [$mediaControls]")
                    Log.i(TAG, "Getting video name...")
                    tmpFile = File(videoPath)

                } catch (e: Exception) {
                    Log.i(TAG, "Error while loading video from media store")
                    e.printStackTrace()
                } finally {
                    Log.i(TAG, "Closing cursor...")
                    cursor.close()
                    Log.i(TAG, "Cursor closed")
                }
            } else {
                Log.i(TAG, "Cursor is null")
            }
            Log.i(TAG, "onActivityResult: Exit (Video)")

        }
        else{
                videoname = (videoUri!!.path!!.substringAfterLast("/"))
                videoextension = videoname.substringAfterLast(".")
                tmpFile  = getFileFromUri(
                    applicationContext?.contentResolver!!,
                    videoUri!!,
                    applicationContext!!.cacheDir,
                    videoname
                )

                Log.i(TAG, "Video name only:" + videoname)
                Log.i(TAG, "Video Extension only:" + videoextension)
                Log.i(TAG, "Uri Path with getPath:" + getPath(videoUri))
                Log.i(TAG, "Uri Path Test 2:" + tmpFile.absolutePath)
        }

            videoName = tmpFile!!.name
            absVideoPath = tmpFile.parent

            val cw = ContextWrapper(getTheApplicationContext())
            val directory = cw.getExternalFilesDir("")
            Log.i(TAG, "save directory:" + directory.absoluteFile)

            inputVideoFilePath = absVideoPath + File.separator + videoName

            val outputVideoPath = FileManagerUtils.onCreateVIDOutputDir(directory.absolutePath)
            outputNoSoundVideoFilePath = outputVideoPath.path + File.separator +
                    "${FileManagerUtils.removeFileExtension(videoName)}_temp.mp4"

            val outputAudioPath = FileManagerUtils.onCreateAUOutputDir(directory.absolutePath)
            outputAudioFilePath = outputAudioPath.path + File.separator +
                    "${FileManagerUtils.removeFileExtension(videoName)}_out.mp3"

            Log.i(TAG, "File name [$videoName],\n Path: $absVideoPath \n-- DONE! --")

            videoView.setMediaController(mediaControls) // Set media control
            videoView.setVideoURI(videoUri)

            onShowToast("Video: $videoName loaded", "success")
//            Toast.makeText(applicationContext, "Video is loaded", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "All done!")

        }

        else {
            var newUri = data?.data
            Log.i(TAG,"VideoPlay New Uri:"+newUri)
        }
    }

    private fun getFileFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        directory: File,
        name: String
    ): File {
        val file =
            //File.createTempFile("suffix", ".prefix", directory)
            File(directory, name)
        file.outputStream().use {
            contentResolver.openInputStream(uri)?.copyTo(it)
        }

        return file
    }

    fun getPath(uri: Uri?): String? {
        val projection =
            arrayOf(MediaStore.Video.Media.DATA)
        val cursor: Cursor? =
            applicationContext?.contentResolver?.query(uri!!, projection, null, null, null)
        return if (cursor != null) {
            val column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            cursor.moveToFirst()
            cursor.getString(column_index)
        } else null
    }
    // Permissions for media access
    fun onCheckPermissions() {
        Log.i(TAG, "-- onCheck permissions entry")
        if (ContextCompat.checkSelfPermission(
                this.applicationContext!!,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Access Granted for -- Read ext sto --")
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this.activity!!,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                ActivityCompat.requestPermissions(
                    this.activity!!, arrayOf(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    ), VIDEO_REQUEST
                )

                Log.i(TAG, "Should request permissions called")
            } else {
                ActivityCompat.requestPermissions(
                    this.activity!!, arrayOf(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    ), VIDEO_REQUEST
                )

                Log.i(TAG, "Should request permissions called -- ELSE --")
            }
        }
        Log.i(TAG, "onCheck permissions exit --")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "onRequest permissionResult entry ---")
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Grant Res: ${grantResults.isNotEmpty()}, res: ${grantResults[0]}")
                    if ((ContextCompat.checkSelfPermission(
                            this.activity!!,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED)
                    ) {
                        Log.i(
                            TAG,
                            "Permission storage: ${android.Manifest.permission_group.STORAGE}"
                        )
                        Toast.makeText(context, "Permission granted", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
        Log.i(TAG, "-- onRequest permissionResult exit")
    }

    // very important!
    override fun onAttach(context: Context) {
        super.onAttach(context)
        applicationContext = context
        Log.i(TAG, "Context attached")
    }

    fun getTheApplicationContext(): Context? {
        return applicationContext
    }

    override fun onStart() {
        super.onStart()
        posenetSingle = PoseInference(this.context!!)
        posenetMultiple = PoseInference(this.context!!)
        initDetector()
        imageProcessing = ImageProcessing(posenetSingle, posenetMultiple, detector)
        Log.i(TAG, "onStart called")
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
    }

    override fun onDestroy() {
        super.onDestroy()
        posenetSingle.close()
        posenetMultiple.close()
        Log.i(TAG, "Stopping background threads")
        stopBackgroundThread()
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null

            Log.i(TAG, "Thread successfully stopped")
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("imageAvailableListener").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
        Log.i(TAG, "Image is available: Thread")
    }

    companion object {
        private const val VIDEO_REQUEST = 100
        private const val VIDEO_PLAY = 200
        private val ORIENTATIONS = SparseIntArray()
        private const val TAG = "VIDFRAGMENT"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }


    fun extractAudiomp3FromVideo(videoPath: String?, videoName: String?){
        val startTimer = System.currentTimeMillis()
        Log.i(TAG, "##################Extracting Audiomp3fromVideo#################")
        inputVideoFilePath = videoPath + File.separator + videoName

        // try to show progress of audio extraction

        try {
            //val thread = Thread( Runnable {

                Log.i(TAG, "In runnable...")
                // set progress bar visibility in main thread
//                activity?.runOnUiThread {
//                    progressBar.visibility = View.VISIBLE
//                    // set extraction process to indeterminate
//                    progressBar.isIndeterminate = true
//                }
//
//                activity?.runOnUiThread {
//                    progressMsg.visibility = View.VISIBLE
//                    progressMsg.text = "Extracting audio from video file..."
//                }
                val cw = ContextWrapper(getTheApplicationContext())
                val directory = cw.getExternalFilesDir("")
                Log.i(TAG, "save directory:" + directory.absoluteFile)
                val outputAudioPath = FileManagerUtils.onCreateAUOutputDir(directory.absolutePath)

                //val outputAudioPath = FileManagerUtils.onCreateAUOutputDir(Environment.getExternalStorageDirectory()!!.absolutePath)
                outputAudioFilePath = outputAudioPath.path + File.separator +
                        "${FileManagerUtils.removeFileExtension(videoName)}_out.mp3"

                val complexCommand = arrayOf(
                    "-y",
                    "-i",
                    inputVideoFilePath!!,
                    "-vn",
                    "-ar",
                    "44100",
                    "-ac",
                    "2",
                    "-b:a",
                    "256k",
                    "-f",
                    "mp3",
                    outputAudioFilePath!!
                )
                execFFmpegBinary(complexCommand, 0)

                Log.i(TAG, "------ Audio Extraction terminted -------")

            activity?.runOnUiThread {
                val elapsedTime = (System.currentTimeMillis() - startTimer)
                progressMsg.text = "Audio extraction finished in : ${onConvert(elapsedTime)} sec"
            }
        }catch (t: InterruptedException){
            t.printStackTrace()
        }

       Log.i(TAG, "################## Done : Extracting Audiomp3fromVideo#################")

    }

    fun addAudiomp3ToVideomp4(
        outputAudioFilePath: String?,
        outputNoSoundVideoFilePath: String?,
        captureVideoType: Int
    ){
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "################## addAudiomp3ToVideoMp4 #################")

            val cw = ContextWrapper(getTheApplicationContext())
            val directory = cw.getExternalFilesDir("")
            Log.i(TAG, "save directory:" + directory.absoluteFile)
            val outputVideoPath = FileManagerUtils.onCreateVIDOutputDir(directory.absolutePath)

            //val outputVideoPath = FileManagerUtils.onCreateVIDOutputDir(Environment.getExternalStorageDirectory()!!.absolutePath)
            if(captureVideoType == 1) {
                outputVideoFilePath = outputVideoPath.path + File.separator +
                        "${FileManagerUtils.removeFileExtension(videoName)}_SinglePose.mp4"
            }
            if(captureVideoType == 2) {
                outputVideoFilePath = outputVideoPath.path + File.separator +
                        "${FileManagerUtils.removeFileExtension(videoName)}_D-S.mp4"
            }
            if(captureVideoType == 3) {
                outputVideoFilePath = outputVideoPath.path + File.separator +
                        "${FileManagerUtils.removeFileExtension(videoName)}_MultiPose.mp4"
            }

            val command = arrayOf(
                "-y",
                "-i",
                outputNoSoundVideoFilePath!!,
                "-i",
                outputAudioFilePath!!,
                "-map",
                "0:0",
                "-map",
                "1:0",
                "-acodec",
                "copy",
                "-vcodec",
                "copy",
                outputVideoFilePath!!
            )

            execFFmpegBinary(command, 1)

            Log.i(TAG, "##################Done: addAudiomp3ToVideoMp4#################")

        activity?.runOnUiThread {
            val elapsedTime = (System.currentTimeMillis() - startTime)
            progressMsg.text = "Audio merged back to video in : ${onConvert(elapsedTime)} sec"
        }
    }

    fun deleteTempFile(filePath: String){
        Log.i(TAG, "##################Deleting Temp File#################")
        var file :File = File(filePath)

        if(file.exists()){
            // file.delete()
            Log.i(TAG, "${file.absolutePath} deleted: ${file.delete()}")
        }
        Log.i(TAG, "##################Done: Deleting Temp File#################")
    }

    //-----------------
    fun captureVideo(videoPath: String?, videoName: String?, captureVideoType: Int) {
        val startTimer = System.currentTimeMillis()

        Log.i(TAG, "##################CaptureVideo#################")
        Log.i(TAG, "Entry capture method...")

        //val vidThread = Thread(Runnable {

            Log.i(TAG, "Video path: $videoPath, Vid-Name: $videoName")

            inputVideoFilePath = videoPath + File.separator + videoName

            val inputVideoFile = File(inputVideoFilePath) // current path
            //val outputVideoPath = FileManagerUtils.onCreateVIDOutputDir(videoPath)
            val cw = ContextWrapper(getTheApplicationContext())
            val directory = cw.getExternalFilesDir("")
            Log.i(TAG, "save directory:" + directory.absoluteFile)
            val outputVideoPath = FileManagerUtils.onCreateVIDOutputDir(directory.absolutePath)

            outputNoSoundVideoFilePath = outputVideoPath.path + File.separator +
                    "${FileManagerUtils.removeFileExtension(videoName)}_temp.mp4"

            Log.i(
                TAG,
                "\ninputVidFilePath: $inputVideoFilePath" +
                        "\noutputParentPath: $outputVideoPath" +
                        "\noutVidFilePath: $outputNoSoundVideoFilePath"
            )
            Log.i(
                TAG,
                "Selected Model:  ${selectedModelValueRdio.text}, NumOfPose: ${defineNumOfPose.value}"
            )
            val videoGrabber = FFmpegFrameGrabber(inputVideoFile)
            var frame: Frame?
            val androidFrameConverter = AndroidFrameConverter()
            val frameRecorder: FFmpegFrameRecorder

            try {
                Log.i(TAG, "Trying to process vid...\n--Start Grabber --")
                try {
                    videoGrabber.start()
                } catch (e: FrameGrabber.Exception) {
                    Log.i(TAG, "Failed to start grabber: ${e.message}")
                    e.printStackTrace()
                }
                Log.i(
                    TAG,
                    "--Grabber started--\n-- Initialzing recorder: ${videoGrabber.imageWidth}, ${videoGrabber.imageHeight}, ${videoGrabber.audioChannels} --"
                )
                frameRecorder = FFmpegFrameRecorder(
                    outputNoSoundVideoFilePath, videoGrabber.imageWidth,
                    videoGrabber.imageHeight, videoGrabber.audioChannels
                )


                //frameRecorder.videoCodec = videoGrabber.videoCodec
                frameRecorder.videoCodec = avcodec.AV_CODEC_ID_MPEG4
                frameRecorder.format = "mp4"
                //frameRecorder.format = FileManagerUtils.getFileExtension(videoName)
                frameRecorder.frameRate = videoGrabber.videoFrameRate
                frameRecorder.sampleFormat = videoGrabber.sampleFormat
                frameRecorder.sampleRate = videoGrabber.sampleRate
                //frameRecorder.audioCodec = videoGrabber.audioCodec
                frameRecorder.videoCodecName = "mp4"
                //frameRecorder.videoCodecName = FileManagerUtils.getFileExtension(videoName)
                Log.i(TAG, "AAC: ${avcodec.AV_CODEC_ID_AAC}, AC3: ${avcodec.AV_CODEC_ID_AC3}")
                Log.i(TAG, "VIDC: ${avcodec.AV_CODEC_ID_MPEG4}")
                //frameRecorder.audioBitrate = videoGrabber.audioBitrate
                frameRecorder.videoBitrate = videoGrabber.videoBitrate

                Log.i(TAG, "Record set")
                try {
                    frameRecorder.setAudioChannels(0)
                    Log.i(TAG, "Start the recorder...")
                    frameRecorder.start()
                    Log.i(TAG, "Recorder started successfully")
                } catch (e: FrameGrabber.Exception) {
                    Log.i(TAG, "Error starting recorder: ${e.message}")
                    e.printStackTrace()
                }
                val vfr = videoGrabber.videoFrameRate
                val cts = videoGrabber.timestamp / 1000
                var numberOfFrame = 0
                var totalDuration = videoGrabber.lengthInTime /1000000
                var currentDuration :Long = 0
                var totalNumberOfFrame = videoGrabber.lengthInVideoFrames

                Log.i(TAG, "CURRENT: $vfr, $cts")
                Log.i(TAG, "DONE!")
                Log.i(TAG, "-- Recorder started --")

                // init progress
                ///progressStatus = numberOfFrame
//                progressBar.max = totalNumberOfFrame / 100

                var progress = ((numberOfFrame/totalNumberOfFrame)*100)

                progressBar.setProgress(progress)

                var numPerson :Int = defineNumOfPose.value

                while (videoGrabber.grabFrame() != null) {
                    //while (numberOfFrame != null) {
                    try {
                        frame = videoGrabber.grabImage()

                        if (frame == null) {
                            Log.i(TAG, "Frame is really null")
                            break
                        } else {
                            Log.i(TAG, "Frame is not null")
                            numberOfFrame++

                            Log.i(
                                TAG,
                                "Frame grabbed: ${frame.image.size}, ${frame.imageWidth}, ${frame.imageHeight}, ${frame.imageDepth}, ${frame.timestamp}"
                            )
                            Log.i(TAG, "Frame is -- not -- null --")
                            val timestamp = videoGrabber.timestamp
                            currentDuration = timestamp / 1000000

                            Log.i(TAG, "-- Convert frame to bitmap..")
                            val bitmap: Bitmap = androidFrameConverter.convert(frame)
                            val newFile = outputVideoPath.path + File.separator + "${timestamp}.jpg"
                            val pic = File(newFile)
                            Log.i(TAG, "File name; $pic")

                            Log.i(TAG, "############ hmmmm Next step ###########")

                            try {
                                val fos = FileOutputStream(pic)
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)

                                Log.i(TAG, "Bitmap compressed --")
                                //val bis = ByteArrayInputStream(bos.toByteArray())
                                //val grabber = FFmpegFrameGrabber(bis)
                                Log.i(TAG, "-- Conversion to bitmap OK -- Start HPE --")
                                //val resBitmap = processImageEstimateSinglePose(bitmap) //Here is the Change between


                                var resBitmap:Bitmap? =null
                                if(captureVideoType==1) {
                                    resBitmap = imageProcessing.processImageEstimateSinglePose(
                                        bitmap,
                                        false
                                    ) //Here is the Change between
                                }

                                if(captureVideoType==2) {
                                    resBitmap = imageProcessing.detectPersonsOnImage(
                                        bitmap,
                                        false
                                    ) //Here is the Change between
                                }

                                if(captureVideoType==3) {

                                    if(defineNumOfPose.value == 0){
                                        numPerson = imageProcessing.countAllPersonOnImage(bitmap).size
                                        if(numPerson >5){
                                            numPerson = 5
                                        }
                                    }

                                    resBitmap = imageProcessing.processImageEstimateMultiplePose(
                                        bitmap,
                                        numPerson,
                                        false
                                    ) //Here is the Change between
                                }

                                Log.i(TAG, "--- (${resBitmap!!.width}, ${resBitmap!!.height}) --")
                                val outFrame = androidFrameConverter.convert(resBitmap)

                                frameRecorder.timestamp = timestamp

                                frameRecorder.record(outFrame)
                                Log.i(TAG, "TimeStamp:. $timestamp")
                                if ((timestamp / 1000) > (videoGrabber.frameRate * 1000)) {
                                    Log.i(
                                        TAG,
                                        "---------------------------- mus be reset: ${(timestamp / 1000)}, ${(videoGrabber.frameRate * 1000)} ---------------------------"
                                    )
                                }
                                if (pic.exists()) {
                                    Log.i(TAG, "Removing file: $pic:: ${pic.delete()}")
                                }

                                progress = ((currentDuration*100)/totalDuration).toInt()

                                activity?.runOnUiThread {
                                    val elapsedTime = (System.currentTimeMillis() - startTimer)
                                    progressBar.setProgress(progress)
                                    val msg = "Processing video since : ${onConvert(
                                            elapsedTime
                                    )} sec | $progress%"
                                    progressMsg.text = msg
                                }
                                fos.flush()
                                fos.close()
                                Log.i(TAG, "Pic deleted-------------")
                            } catch (e: IOException) {
                                Log.i(TAG, "Error converting file to bitmap jpg")
                            }



                            // }
                        }

                    } catch (e: FrameGrabber.Exception) {
                        Log.i(TAG, "Record error")
                        e.printStackTrace()
                    }
                }
                Log.i(TAG, "Exit while loop...")
                frameRecorder.stop()
                frameRecorder.release()
                Log.i(TAG, "Recorder stopped")
                videoGrabber.stop()
                videoGrabber.release()

            } catch (e: Exception) {
                Log.i(TAG, "Error processing video!")
                e.printStackTrace()
            }

        activity?.runOnUiThread {
            val elapsedTime = (System.currentTimeMillis() - startTimer)
            progressBar.setProgress(100)
            val msg = "Video processing finished in : ${onConvert(elapsedTime)} sec"
            progressMsg.text = msg
        }

    }

    private fun loadFFMpegBinary() {
        Log.i(TAG, "Init FFMpeg")
        if (FFmpeg.getInstance(applicationContext).isSupported()) {
            // ffmpeg is supported
            versionFFmpeg();
            //ffmpegTestTaskQuit();
        } else {
            // ffmpeg is not supported
            Log.d(TAG, "ffmpeg not supported!");
        }
    }

    private fun versionFFmpeg() {
        FFmpeg.getInstance(applicationContext)
            .execute(arrayOf("-version"), object : ExecuteBinaryResponseHandler() {
                override fun onSuccess(message: String?) {
                    Log.d(TAG, message)
                }

                override fun onProgress(message: String?) {
                    Log.d(TAG, message)
                }
            })
    }

    private fun execFFmpegBinary(command: Array<String>, deleteTempFile: Int) {
        val task: FFtask =
            FFmpeg.getInstance(applicationContext).execute(
                command,
                object : ExecuteBinaryResponseHandler() {
                    override fun onStart() {
                        Log.d(TAG, "FFmpeg Execute Command : on start")
                    }

                    override fun onFinish() {
                        Log.d(TAG, "FFmpeg Execute Command : on finish")
                        //semaphore1.release()
                        if (deleteTempFile == 1) {
                            deleteTempFile(outputNoSoundVideoFilePath!!)
                            deleteTempFile(outputAudioFilePath!!)

                            if (File(outputVideoFilePath).exists()) {

                                playBtn.visibility = View.VISIBLE
                                onShowToast("Video Inference terminated", "success");
                            }

                        }
                        handler.postDelayed(Runnable {
                            Log.d(TAG, "FFmpeg Execute Command : RESTART RENDERING")
                        }, 5000)
                    }

                    override fun onSuccess(message: String) {
                        Log.d(TAG, "FFmpeg Execute Command onSuccess :" + message)
                    }

                    override fun onProgress(message: String) {
                        Log.d(TAG, "FFmpeg Execute Command onProgress :" + message)
                    }

                    override fun onFailure(message: String) {
                        Log.d(TAG, "FFmpeg Execute Command onFailure :" + message)
                    }
                })
        handler.postDelayed(Runnable {
            Log.d(TAG, "FFmpeg Execute Command : STOPPING THE RENDERING!")
            task.sendQuitSignal()
        }, 8000)

    }

    fun onShowToast(msg: String, warningColor: String = "error", duration: Int = 0){
//        val toastInflater : LayoutInflater = activity!!.layoutInflater
        val text : TextView = toastLayout.findViewById(R.id.text_toast_id)
        text.text = msg

        val toastIcon: ImageView = toastLayout.findViewById(R.id.img_toast_id) as ImageView
        // Toast
        val toast: Toast = Toast(getTheApplicationContext())
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0)

        when (warningColor) {
            "success" -> {
                toastIcon.setImageResource(R.drawable.ic_process_successfull)
            }
            "warning" -> {
                toastIcon.setImageResource(R.drawable.ic_warning_msg_toast)
            }
            else -> {
                toastIcon.setImageResource(R.drawable.ic_error_msg_toast)
            }
        }

        toast.view = toastLayout


        if (duration > 0){
            Log.i(TAG, "--------- TIMER LENGTH ------------")
            toast.duration = Toast.LENGTH_LONG
            toast.show()
            val length = duration * 1000L
            val timer = object :CountDownTimer(length, 1000L){
                override fun onTick(p0: Long) {
                    toast.show()
                }

                override fun onFinish() {
                    toast.cancel()
                }
            }
            toast.show()
            timer.start()
        }else{
            toast.duration = Toast.LENGTH_SHORT
            toast.show()
        }
    }

    private fun playVideoWithExternalApp() {

        val cw = ContextWrapper(getTheApplicationContext())
        val directory = cw.getExternalFilesDir("")

        val videoPath = File(
            directory,
            FileManagerUtils.get_Vid_Output_Dir()
        )
        val video = File(videoPath, File(outputVideoFilePath).name)



        if  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            Log.i(TAG, "Build.VERSION.SDK_INT >= Build.VERSION_CODES.N")
            videoOutputUri =
                FileProvider.getUriForFile(
                    getTheApplicationContext()!!,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    video
                )        }else{
            Log.i(TAG, "Build.VERSION.SDK_INT < Build.VERSION_CODES.N")
            videoOutputUri = Uri.fromFile((video))
        }


        val viewIntent = Intent(Intent.ACTION_VIEW)

        var MimeType = "video/mp4"
        viewIntent.setDataAndType(videoOutputUri,MimeType)
        Log.d(TAG, "File Provider Uri Created:" + videoOutputUri)
        Log.i(TAG, "Output video File Path:" + outputVideoFilePath)

        viewIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or FLAG_GRANT_READ_URI_PERMISSION)

        startActivity(viewIntent)

    }

    private fun onConvert(elapsedTime: Long):String{
        var display: String

        val sec = (elapsedTime / 1000L) % 60
        val min = ( elapsedTime / (1000L * 60)) % 60
        val hour = (elapsedTime / (1000L * 60 * 60)) % 24
        display = "$hour : $min : $sec"
        return display
    }
}
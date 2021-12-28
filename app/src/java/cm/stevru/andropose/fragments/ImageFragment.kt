package cm.stevru.andropose.fragments

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import cm.stevru.andropose.R
import cm.stevru.andropose.classifier.Classifier
import cm.stevru.andropose.classifier.TFLiteObjectDetectionAPIModel
import cm.stevru.andropose.inference.PoseInference
import cm.stevru.andropose.models.CSVMapperModel
import cm.stevru.andropose.models.CSVMapperWithLineModel
import cm.stevru.andropose.utilities.CSVMapperUtils
import cm.stevru.andropose.utilities.FileManagerUtils
import cm.stevru.andropose.utilities.ImageProcessing
import java.io.File
import java.util.*


class ImageFragment : Fragment() {

    lateinit var imageProcessing : ImageProcessing
    /** List of body joints that should be connected **/

    private lateinit var imageViewOriginal            : ImageView
    private lateinit var imgWithDectection            : ImageView
    private lateinit var imgOnlyReconstructed         : ImageView
    private lateinit var imgReconstructedLine         : ImageView

    private lateinit var mapperL                      : ArrayList<CSVMapperWithLineModel>
    private lateinit var mapperKP                     : ArrayList<CSVMapperModel>

    private lateinit var showPersonScoreAndSizeTV     : TextView
    private lateinit var showCSVContentTV             : TextView

    private lateinit var loadImgBtn                   : Button
    private lateinit var detectBtn                    : Button

    private lateinit var exportInCSVFileBtn           : Button

    private lateinit var selectModelRdioG             : RadioGroup
    private lateinit var selectedModelValueRdio       : RadioButton

    private lateinit var showOrHideCSVContentSwitch   : SwitchCompat
    private lateinit var defineNumOfPose              : NumberPicker

    /** An object for the Posenet library.    */
    lateinit var posenetSingle                    : PoseInference
    lateinit var posenetMultiple                    : PoseInference

    /** An additional thread for running tasks that shouldn't block the UI.   */
    private var backgroundThread: HandlerThread? = null

    /** A [Handler] for running tasks in the background.    */
    private var backgroundHandler: Handler? = null


    var switchState = false
    var showHideInfoState = false

    var applicationContext : Context? =null
    var imageUri: Uri? = null
    var imgName: String? = null
    var absImgPath: String? = null

    lateinit var toSavedBitmap: Bitmap

    lateinit var horizontalScrollView: HorizontalScrollView

    private var detector: Classifier? = null

    //Detector Initialisation

    // Minimum detection confidence to track a detection.
    var MINIMUM_CONFIDENCE_TF_OD_API = 0.5f
    private val TF_OD_API_INPUT_SIZE = 300
    private val TF_OD_API_IS_QUANTIZED = true
    private val TF_OD_API_MODEL_FILE = "detect.tflite"
    private val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"
    var cropSize: Int = TF_OD_API_INPUT_SIZE
    private var cropToFrameTransform: Matrix? = null

    var modelChoose: Int = 0

    lateinit var toastLayout: ViewGroup

    private var modelSelection: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        Log.i(TAG, "Inflation views")
        val view = inflater.inflate(R.layout.fragment_image, container, false)
        initViews(view)
        setBtnLoadListener()
        setSelectModelListener()
        setShowInfoListener()
        onDetect()
        onExportBtnClicked()
        onCheckPermissions()
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

    private fun initViews(view: View){
        Log.i(TAG, "Init views")
        imageViewOriginal = view.findViewById(R.id.img_view_id)
        loadImgBtn = view.findViewById(R.id.load_img_btn_id)
        detectBtn = view.findViewById(R.id.detect_img_btn_id)
        exportInCSVFileBtn = view.findViewById(R.id.export_btn_id)

        imgWithDectection    = view.findViewById(R.id.img_and_detection_id) // 1.st img
        imgOnlyReconstructed = view.findViewById(R.id.img_reconstructed_id) // 2.img
        imgReconstructedLine = view.findViewById(R.id.img_reconstructed2_id) // 3. img

        showCSVContentTV = view.findViewById(R.id.show_csv_content_id)

        showPersonScoreAndSizeTV = view.findViewById(R.id.show_person_score_size_id)

        showOrHideCSVContentSwitch = view.findViewById(R.id.show_hide_info_id)
        selectModelRdioG = view.findViewById(R.id.select_model_grp_id)
        modelChoose = selectModelRdioG.checkedRadioButtonId // get default selected

        defineNumOfPose = view.findViewById(R.id.number_of_detection_picker_id)

        val valuesPickers = arrayOf("-1", "1", "2", "3", "4", "5")
        defineNumOfPose.minValue = 0
        defineNumOfPose.maxValue = valuesPickers.size - 1
        defineNumOfPose.displayedValues = valuesPickers

        defineNumOfPose.visibility = View.GONE
        exportInCSVFileBtn.visibility = View.GONE
        showOrHideCSVContentSwitch.visibility = View.GONE

        horizontalScrollView = view.findViewById(R.id.horizontal_scroll_id)
        horizontalScrollView.visibility = View.GONE

        hideOrShowInfo(showHideInfoState)

        // init toast inflater
        val inflater = layoutInflater
        toastLayout = inflater.inflate(R.layout.toast_msg_layout, activity!!.findViewById(R.id.toast_layout_id)) as ViewGroup
    }

    private fun hideOrShowInfo(state: Boolean){
        if (state){
            showCSVContentTV.visibility = View.VISIBLE
            showPersonScoreAndSizeTV.visibility = View.VISIBLE

        }else{
            showCSVContentTV.visibility = View.GONE
            showPersonScoreAndSizeTV.visibility = View.GONE

            if (showOrHideCSVContentSwitch.isChecked){
                showOrHideCSVContentSwitch.isChecked = false
            }
        }
    }
    // events
    fun setBtnLoadListener(){
        loadImgBtn.setOnClickListener {
            val intent: Intent

            if (Build.VERSION.SDK_INT < 19) {
                intent = Intent(Intent.ACTION_PICK)
                intent.type = "image/*"
            } else {
                intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "image/*"
            }

            if (showHideInfoState){
                showHideInfoState = false
                hideOrShowInfo(showHideInfoState)
                exportInCSVFileBtn.visibility = View.GONE
                showOrHideCSVContentSwitch.visibility = View.GONE
                horizontalScrollView.visibility = View.GONE
            }
            startActivityForResult(intent, IMG_REQUEST)
        }

    }

    // Switch listener
    fun setShowInfoListener(){
        showOrHideCSVContentSwitch.setOnCheckedChangeListener { _, b ->
            run {
                switchState = b
                hideOrShowInfo(b)
            }
        }
    }
    // check radiobutton check state
    fun setSelectModelListener(){

        selectModelRdioG.setOnCheckedChangeListener { radioGroup, i ->
            run {
                Log.i(TAG, "Value of i:  $i")
                radioGroup.check(i)
                modelChoose = radioGroup.checkedRadioButtonId
                selectedModelValueRdio = view!!.findViewById(modelChoose)
                val modelChooseStr: String = selectedModelValueRdio.text.toString()
                if (modelChooseStr.toLowerCase().contains("single")){
                    defineNumOfPose.visibility = View.GONE
                    defineNumOfPose.value = 0

                }else if (modelChooseStr.toLowerCase().contains("detection")){
                    defineNumOfPose.visibility = View.GONE
                    defineNumOfPose.value = 0
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

    fun onDetect(){
        Log.i(TAG, "Detection button clicked");

        detectBtn.setOnClickListener{

            val imgUri = imageUri
            selectedModelValueRdio = view!!.findViewById(modelChoose)
            val modelChooseStr: String = selectedModelValueRdio.text.toString()

            try {
                Log.i(TAG, "MyImageURI on DetectButtonClicked:" + imgUri.toString());
                val bitmap = MediaStore.Images.Media.getBitmap(context?.contentResolver, imgUri)

                when {
                    modelChooseStr.toLowerCase(Locale.ROOT).contains("single") -> {
                        Log.i(
                            TAG,
                            "Seletion Single:  ${selectedModelValueRdio.text}, picker: ${defineNumOfPose.value}"
                        )
                        val singlePoseEstimationBitmap: Bitmap = imageProcessing.processImageEstimateSinglePose(
                            bitmap,
                            true
                        )

                        imgWithDectection.setImageBitmap(singlePoseEstimationBitmap)
                        imgOnlyReconstructed.setImageBitmap(imageProcessing.imgOnlyReconstructedBitmap)
                        imgReconstructedLine.setImageBitmap(imageProcessing.imgReconstructedLineBitmap)

                        showHideInfoState = true

                        // update selector
                        modelSelection = 0
                    }
                    modelChooseStr.toLowerCase(Locale.ROOT).contains("detection") -> {
                        Log.i(
                            TAG,
                            "Seletion Detction:  ${selectedModelValueRdio.text}, picker: ${defineNumOfPose.value}"
                        )

                        val detectionAndSinglePoseEstimationPoseBitmap: Bitmap = imageProcessing.detectPersonsOnImage(
                            bitmap,
                            true
                        )
//                        imgWithDectection.setImageBitmap(null)
                        imgWithDectection.setImageBitmap(detectionAndSinglePoseEstimationPoseBitmap)
                        imgOnlyReconstructed.setImageBitmap(imageProcessing.imgOnlyReconstructedBitmap)
                        imgReconstructedLine.setImageBitmap(imageProcessing.imgReconstructedLineBitmap)
                        Log.i(TAG, "Picker val in onDetect: ${defineNumOfPose.value}")

                        showHideInfoState = true

                        // update selector
                        modelSelection = 1
                    }
                    modelChooseStr.toLowerCase(Locale.ROOT).contains("multi") -> {
                        Log.i(
                            TAG,
                            "Seletion Multi:  ${selectedModelValueRdio.text}, picker: ${defineNumOfPose.value}"
                        )
                        var MultiplePoseEstimationBitmap: Bitmap

                        var numPerson :Int = defineNumOfPose.value

                        if(defineNumOfPose.value == 0){
                            numPerson = imageProcessing.countAllPersonOnImage(bitmap).size
                            if(numPerson >5){
                                numPerson = 5
                                Log.i(TAG, "More than 5 persons detected, we take maxi 5 persons:")
                            }
                            Log.i(TAG, "Auto number of person for Multiple Poses:" + numPerson)
                        }

                        MultiplePoseEstimationBitmap = imageProcessing.processImageEstimateMultiplePose(
                            bitmap,
                            numPerson,
                            true
                        )

//                        imgWithDectection.setImageBitmap(null)
                        imgWithDectection.setImageBitmap(MultiplePoseEstimationBitmap)

                      imgOnlyReconstructed.setImageBitmap(imageProcessing.imgOnlyReconstructedBitmap)
                        imgReconstructedLine.setImageBitmap(imageProcessing.imgReconstructedLineBitmap)

                        // set info for size
//                        onShowPreview()
                        showHideInfoState = true

                        // update selector
                        modelSelection = 2
                    }
                }

                // make other elements visible
                horizontalScrollView.visibility = View.VISIBLE
                exportInCSVFileBtn.visibility = View.VISIBLE
                showOrHideCSVContentSwitch.visibility = View.VISIBLE

                // set info for size
                onShowPreview()

                // pass current bit map as image to save with its corresponding csv annotations
                toSavedBitmap = Bitmap.createBitmap(bitmap)

                onShowToast("Inference terminated", "success")
                Log.i(TAG, "View displayed")
            }catch (e: NullPointerException){
//                Toast.makeText(context, "No image has been loaded!", Toast.LENGTH_SHORT).show()
                onShowToast("No image has been loaded", "warning")
                Log.i(TAG, "Bitmap is null: ${e.message}")
            }
            Log.i(TAG, "Process terminated!")
        }
    }

    fun onExportBtnClicked(){
        exportInCSVFileBtn.setOnClickListener {
            Log.i(TAG, "Export csv clicked")
            if (showCSVContentTV.text.isNotEmpty()){
                val cw = ContextWrapper(getTheApplicationContext())
                val directory = cw.getExternalFilesDir("")
                Log.i(TAG,"save directory:"+directory.absoluteFile)
                val outPutDir = FileManagerUtils.onCreateIMGOutputDir(directory!!.absolutePath)
                FileManagerUtils.onSaveImg(
                        toSavedBitmap,
                        "copy_$imgName",
                        outPutDir,
                    modelSelection
                )
                CSVMapperUtils.onExport(showCSVContentTV.text.toString(), "copy_$imgName", outPutDir, modelSelection)
//                Log.i(TAG, "${showCSVContentTV.text}")
                onShowToast("Data saved successfully", "success")
            }else{
                onShowToast("Fail to saved data")
            }
        }
    }
    fun onShowPreview(){
//        showInfoTv.visibility = View.VISIBLE
            showCSVContentTV.text = imageProcessing.formattedOutput
            showPersonScoreAndSizeTV.text = imageProcessing.formattedSizeInfo
    }

    // Permissions for media access
    fun onCheckPermissions(){
        Log.i(TAG, "-- onCheck permissions entry")
        if (ContextCompat.checkSelfPermission(
                this.applicationContext!!,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this.activity!!,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1
            );
        }

        if (ContextCompat.checkSelfPermission(
                this.applicationContext!!,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this.activity!!,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            );
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Grant Res: ${grantResults.isNotEmpty()}, res: ${grantResults[0]}")
                    if ((ContextCompat.checkSelfPermission(
                            this.activity!!,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED)
                    ) {
                        onShowToast("Permission granted", "success")
                    }
                } else {
                    onShowToast("Permission denied")
                    //Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN){
                        activity?.finishAffinity()
                    }else {
                        activity?.finish()
                    }
                }
                return
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && requestCode == IMG_REQUEST) {

            imageUri = data?.data
            // 1-) access to internal volume
            var tmpFile: File? = null
            var tmpBitmap: Bitmap


            if ((Build.VERSION.SDK_INT < 19)) {

                val projection = arrayOf(MediaStore.Video.Media.DATA)
                Log.i(TAG, "Image Uri: [$imageUri],\n projection: [$projection]")


                val cursor: Cursor? = imageUri?.let {
                    applicationContext?.contentResolver?.query(
                        it, projection, null, null, null
                    )
                }
                Log.i(TAG, "Cursor: [$cursor]")

                if (cursor != null && cursor.moveToFirst()) {
                    Log.i(TAG, "Cursor is not null and it is moved to first (image)")
                    try {
                        val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                        absImgPath = cursor.getString(idx)
                        Log.i(TAG, "idx: $idx, --> image path: [$absImgPath]")
                        //Log.i(TAG, "Media control: [$mediaControls]")
                        Log.i(TAG, "Getting image name...")
                        tmpFile = File(absImgPath)
                        imgName = tmpFile.name
                        Log.i(TAG, "Image name only:" + imgName)

                    } catch (e: Exception) {
                        Log.i(TAG, "Error while loading image from media store")
                        e.printStackTrace()
                    } finally {
                        Log.i(TAG, "Closing cursor...")
                        cursor.close()
                        Log.i(TAG, "Cursor closed")
                    }
                } else {
                    Log.i(TAG, "Cursor is null")
                }
                Log.i(TAG, "onActivityResult: Exit (Image)")

            } else {
                imgName = (imageUri!!.path!!.substringAfterLast("/"))
                //videoextension = videoname.substringAfterLast(".")
                tmpFile = getFileFromUri(
                    applicationContext?.contentResolver!!,
                    imageUri!!,
                    applicationContext!!.cacheDir,
                    imgName!!
                )
                absImgPath = tmpFile.absolutePath

                Log.i(TAG, "Image name only:" + imgName)
                //Log.i(TAG, "Uri Path with getPath:" + getPath(imageUri))
                Log.i(TAG, "Image Uri Path Test 2:" + tmpFile.absolutePath)
            }

            Log.i(TAG, "Setting bitmap on screen")
            tmpBitmap = BitmapFactory.decodeFile(absImgPath)
            imageViewOriginal.setImageBitmap(tmpBitmap)
            imageViewOriginal.setImageURI(imageUri)

            onShowToast("Image: $imgName loaded", "success")
        }
    }

    private fun getFileFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        directory: File,
        name: String
    ): File {
        val file =
            File(directory, name)
        file.outputStream().use {
            contentResolver.openInputStream(uri)?.copyTo(it)
        }

        return file
    }

    // very important!
    override fun onAttach(context: Context) {
        super.onAttach(context)
        applicationContext = context
        Log.i(TAG, "Context attached")
    }
    fun getTheApplicationContext(): Context?{
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

    //-- Utility functions


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
        private const val TAG = "IMGFRAGMENT"
        private const val IMG_REQUEST = 1
        private val ORIENTATIONS = SparseIntArray()
        private const val FRAGMENT_DIALOG = "dialog"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    fun onShowToast(msg: String, warningColor: String="error"){
//        val toastInflater : LayoutInflater = activity!!.layoutInflater
        val text : TextView = toastLayout.findViewById(R.id.text_toast_id)
        text.text = msg

        val toastIcon: ImageView = toastLayout.findViewById(R.id.img_toast_id) as ImageView
        // Toast
        val toast: Toast = Toast(getTheApplicationContext())
        toast.setGravity(Gravity.CENTER_VERTICAL,0,0)
        toast.duration = Toast.LENGTH_SHORT

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
        toast.show()
    }
    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
            AlertDialog.Builder(activity)
                .setMessage(arguments!!.getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ -> activity!!.finish() }
                .create()

        companion object {

            @JvmStatic
            private val ARG_MESSAGE = "message"

            @JvmStatic
            fun newInstance(message: String): ErrorDialog = ErrorDialog().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }
        }
    }
}
package cm.stevru.andropose.utilities

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

// source: https://openclassrooms.com/fr/courses/5779271-manage-your-data-to-have-a-100-offline-android-app-in-kotlin/5954921-create-a-file-on-external-storage
object FileManagerUtils {
    /*********************
     *  External storage *
     *********************/

    private const val TAG = "FILE_MANAGER"
    private const val OUTPUT_DIR_NAME = "HPE_DIR"
    private val videoDir = "Videos"
    private val imgDir = "Images"
    private val mp3Dir = "Mp3"
    private val IMG_OUTPUT_STORAGE_DIR = OUTPUT_DIR_NAME + File.separator +imgDir
    private val VID_OUTPUT_STORAGE_DIR = OUTPUT_DIR_NAME + File.separator + videoDir
    private val AU_OUTPUT_STORAGE_DIR = OUTPUT_DIR_NAME + File.separator +mp3Dir

    fun get_Img_Output_Dir() :String{
        return IMG_OUTPUT_STORAGE_DIR
    }

    fun get_Output_Dir() :String{
        return OUTPUT_DIR_NAME
    }

    fun get_Vid_Output_Dir() :String{
        return VID_OUTPUT_STORAGE_DIR
    }

    fun get_Audio_Output_Dir() :String{
        return AU_OUTPUT_STORAGE_DIR
    }

    fun onSaveImg(bitmap: Bitmap, fileName: String?, destDir: File?, modelSelection: Int = 0) {
        Log.i(TAG, "Entry saving file to\n ${destDir?.path}")

        //val modifiedImg = File(destDir?.path, "out-${fileName}")
        var outputName =  removeFileExtension(fileName)
        if ( modelSelection == 0){
            outputName += "_single"
        } else if (modelSelection == 1){
            outputName += "_detection_single"
        }else if (modelSelection == 2){
            outputName += "_multi"
        }

        val modifiedImg = File(destDir?.path, "${outputName}.jpg")
        //val modifiedImg = File(IMG_OUTPUT_STORAGE_DIR, "out-${fileName}")
        val fOut = FileOutputStream(modifiedImg)

        bitmap.compress(Bitmap.CompressFormat.PNG, 85, fOut)
        fOut.flush()
        fOut.close()

        Log.i(TAG, "Final name: $modifiedImg")
        Log.i(TAG, "Exit saving file")
    }

    fun onCreateOutputDir(outputDir: String?): File{
        Log.i(TAG, "Starting to create output dir...")
        val outDir = outputDir + File.separator + OUTPUT_DIR_NAME
        Log.i(TAG, "Path to external sto.\n $outputDir,\n final out: $outDir")
        val dir = File(outDir)
        Log.i(TAG, "Check if dir already exists")

        if (dir.exists()){
            Log.i(TAG, "Dir: [${dir.name}], already exists!")
            //return dir
        }else{
            val isDirCreated = dir.mkdirs()
            Log.i(TAG, "Dir does not exits and will be created: $isDirCreated")
            if (isDirCreated){
                Log.i(TAG, "Dir is successfully created!")
            }else{
                Log.i(TAG, "Unable to create dir..")
            }
        }
        Log.i(TAG, "Exiting process to create output dir!")
        return dir
    }
    fun onCreateIMGOutputDir(outputDir: String?): File{

        onCreateOutputDir(outputDir)

        Log.i(TAG, "Starting to create IMG output dir...")
        val outDir = outputDir + File.separator + IMG_OUTPUT_STORAGE_DIR
        Log.i(TAG, "Path to IMG external sto.\n $outputDir,\n final out: $outDir")
        val dir = File(outDir)
        Log.i(TAG, "Check if IMG dir already exists")

        if (dir.exists()){
            Log.i(TAG, "IMG Dir: [${dir.name}], already exists!")
            //return dir
        }else{
            val isDirCreated = dir.mkdirs()
            Log.i(TAG, "IMG Dir does not exits and will be created: $isDirCreated")
            if (isDirCreated){
                Log.i(TAG, "IMG Dir is successfully created!")
            }else{
                Log.i(TAG, "Unable to create IMG dir..")
            }
        }
        Log.i(TAG, "Exiting process to create output IMG dir!")
        return dir
    }

    fun onCreateVIDOutputDir(outputDir: String?): File{

        onCreateOutputDir(outputDir)

        Log.i(TAG, "Starting to create output VID dir...")
        val outDir = outputDir + File.separator + VID_OUTPUT_STORAGE_DIR
        Log.i(TAG, "Path to VID external sto.\n $outputDir,\n final out: $outDir")
        val dir = File(outDir)
        Log.i(TAG, "Check if VID dir already exists")

        if (dir.exists()){
            Log.i(TAG, "VID Dir: [${dir.name}], already exists!")
            //return dir
        }else{
            val isDirCreated = dir.mkdirs()
            Log.i(TAG, "VID Dir does not exits and will be created: $isDirCreated")
            if (isDirCreated){
                Log.i(TAG, "VID Dir is successfully created!")
            }else{
                Log.i(TAG, "Unable to create VID dir..")
            }
        }
        Log.i(TAG, "Exiting process to create output VID dir!")
        return dir
    }

    fun onCreateAUOutputDir(outputDir: String?): File{

        onCreateOutputDir(outputDir)

        Log.i(TAG, "Starting to create output AUDIO dir...")
        val outDir = outputDir + File.separator + AU_OUTPUT_STORAGE_DIR
        Log.i(TAG, "Path to AUDIO external sto.\n $outputDir,\n final out: $outDir")
        val dir = File(outDir)
        Log.i(TAG, "Check if AUDIO dir already exists")

        if (dir.exists()){
            Log.i(TAG, "AUDIO Dir: [${dir.name}], already exists!")
            //return dir
        }else{
            val isDirCreated = dir.mkdirs()
            Log.i(TAG, "AUDIO Dir does not exits and will be created: $isDirCreated")
            if (isDirCreated){
                Log.i(TAG, "AUDIO Dir is successfully created!")
            }else{
                Log.i(TAG, "AUDIO Unable to create dir..")
            }
        }
        Log.i(TAG, "AUDIO Exiting process to create output dir!")
        return dir
    }

    fun removeFileExtension(fileName: String?): String?{
        Log.i(TAG, "Starting to remove file ext. for : $fileName...")
        val fileNameWithoutExt = File(fileName!!).nameWithoutExtension
        Log.i(TAG, "Res: $fileNameWithoutExt")
        return fileNameWithoutExt
    }

    fun getFileExtension(fileName: String?): String?{
        Log.i(TAG, "Starting to remove file ext. for : $fileName...")
        val fileExt = File(fileName!!).extension
        Log.i(TAG, "Res: $fileExt")
        return fileExt
    }
}
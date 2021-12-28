package cm.stevru.andropose.utilities

import android.util.Log
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4
import org.bytedeco.javacv.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object VideoProcessingUtils {
    private const val TAG = "VID_PROC"
    private lateinit var VIDEO_EXT: String

    fun captureVideo(videoPath: String?, videoName: String?){
        Log.i(TAG, "Entry capture method...")
        Log.i(TAG, "Video path: $videoPath, Vid-Name: $videoName")

        val inputVideoPath = videoPath + File.separator + videoName

        val inputVideoFile = File(inputVideoPath) // current path
        val outputVideoPath = FileManagerUtils.onCreateVIDOutputDir(videoPath)
        val outputVideoFile = outputVideoPath.path +File.separator+
                "${FileManagerUtils.removeFileExtension(videoName)}_out.${FileManagerUtils.getFileExtension(videoName)}"

        Log.i(TAG, "inputPath: $inputVideoPath\ninputVidFile: $inputVideoFile\noutputPath: $outputVideoPath\noutVidFile: $outputVideoFile")
        val videoGrabber = FFmpegFrameGrabber(inputVideoFile)
        var frame: Frame
        val androidFrameConverter: AndroidFrameConverter
        val frameRecorder: FFmpegFrameRecorder

        try {
            Log.i(TAG, "Trying to process vid...\n--Start Grabber --")
            try {
                videoGrabber.start()
            }catch (e: FrameGrabber.Exception){
                Log.i(TAG, "Failed to start grabber: ${e.message}")
                e.printStackTrace()
            }
            Log.i(TAG,"--Grabber started--\n-- Initialzing recorder: ${videoGrabber.imageWidth}, ${videoGrabber.imageHeight}, ${videoGrabber.audioChannels} --")
            frameRecorder = FFmpegFrameRecorder(outputVideoFile, videoGrabber.imageWidth,
                videoGrabber.imageHeight, videoGrabber.audioChannels)
            Log.i(TAG,"\nVC: ${videoGrabber.videoCodec}," +
                    "\nFR: ${videoGrabber.frameRate},\nSF: ${videoGrabber.sampleFormat}," +
                    "\nSR: ${videoGrabber.sampleRate}, EXt: ${FileManagerUtils.getFileExtension(videoName)}, ${videoGrabber.format}" +
                    "\nAC: ${videoGrabber.audioCodec}," +
                    "\nACN: ${videoGrabber.audioCodecName}," +
                    "\nVCN: ${videoGrabber.videoCodecName}" +
                    "\nVFR: ${videoGrabber.videoFrameRate}" +
                    "\n" +
                    "AFR: ${videoGrabber.audioFrameRate}" +
                    "\n AO: ${videoGrabber.audioOptions}" +
                    "\n VO ${videoGrabber.videoOptions}" +
                    "\n FC: ${videoGrabber.frameNumber}")
            frameRecorder.videoCodec = videoGrabber.videoCodec
            frameRecorder.format = FileManagerUtils.getFileExtension(videoName)
            frameRecorder.frameRate = videoGrabber.frameRate
            frameRecorder.sampleFormat = videoGrabber.sampleFormat
            frameRecorder.sampleRate = videoGrabber.sampleRate
            frameRecorder.audioCodec = videoGrabber.audioCodec



            Log.i(TAG, "Record set")
                try {
                    Log.i(TAG, "Start the recorder...")
                    frameRecorder.start()
                    Log.i(TAG,"Recorder started successfully")
                }catch (e: FrameGrabber.Exception){
                    Log.i(TAG,"Error starting recorder: ${e.message}")
                    e.printStackTrace()
                }
                Log.i(TAG, "DONE!")
            Log.i(TAG, "-- Recorder started --")

            while(videoGrabber.grabFrame() != null){
                try {
                    frame = videoGrabber.grabFrame()
                    Log.i(TAG, "Frame grabbed: ${frame.image.size}, ${frame.imageWidth}, ${frame.imageHeight}, ${frame.imageDepth}, ${frame.timestamp}")
                    if(frame !=  null){
                        frameRecorder.timestamp = videoGrabber.timestamp
                        frameRecorder.record(frame)
                        //Log.i(TAG, "Time stamp: ${videoGrabber.timestamp}")
                    }else{
                        Log.i(TAG, "Frame is null")
                    }
                }catch (e: Exception){
                    Log.i(TAG,"Record error")
                    e.printStackTrace()
                }
            }
            frameRecorder.stop()
            frameRecorder.release()
            Log.i(TAG, "Recorder stopped")
            videoGrabber.stop()
            videoGrabber.release()
            Log.i(TAG, "Grabber stopped!")
        }catch (e: Exception){
            Log.i(TAG, "Error processing video!")
            e.printStackTrace()
        }
    }
}
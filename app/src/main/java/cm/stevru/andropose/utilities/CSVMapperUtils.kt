package cm.stevru.andropose.utilities

import android.graphics.*
import android.text.*
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.core.text.set
import cm.stevru.andropose.models.CSVMapperModel
import cm.stevru.andropose.models.CSVMapperWithLineModel
import java.io.*
import java.lang.Exception

object CSVMapperUtils {
    val tag = "CSVU"

    val bitmapSize : Int = 257
    // header for model that prints key points and draw line
    private val headerLine = "x1,y1,part_name1,part_score1,x2,y2,part_name2,part_score2"

    // Header for model that prints only key points
    private var headerKP: String = "x,y,part_name,part_score"

    private var paint = Paint()
    private var paintText = Paint()

    private lateinit var canvasL : Canvas
    private lateinit var canvasKP: Canvas

    private lateinit var bitmapL: Bitmap
    private lateinit var bitmapKP : Bitmap
    private val circleRadius = 3.5f

    // Draw key points, joint lines and text
    fun onDrawKPWithLine(csvModelList: List<CSVMapperWithLineModel>,bitmap: Bitmap, color: Int) : Bitmap {
        //Log.i(tag, "starting to dra bitmap")
        // val tmpBitmap =
        bitmapL = Bitmap.createBitmap(bitmap)
        canvasL = Canvas(bitmapL)

        paint.color = color
        paint.strokeWidth = 2.0f

        paintText.color = Color.BLACK
        paintText.textSize = 8.5f
        paintText.typeface = Typeface.MONOSPACE
        paintText.style = Paint.Style.FILL_AND_STROKE

        //Log.i(tag, "Drawing elem in canvas")
        for (elem in csvModelList){
            canvasL.drawCircle(elem.lineStartX, elem.lineStartY, circleRadius, paint)
            canvasL.drawCircle(elem.lineStopX, elem.lineStopY, circleRadius, paint)
            canvasL.drawLine(elem.lineStartX, elem.lineStartY, elem.lineStopX, elem.lineStopY, paint)
//            elem.fromPartName?.let { canvasL.drawText(it, elem.lineStartX -(0.15f * elem.lineStartX), elem.lineStartY -(.05f * elem.lineStartY), paintText) }
//            elem.toPartName?.let { canvasL.drawText(it, elem.lineStopX -(0.15f * elem.lineStopX), elem.lineStopY -(.05f * elem.lineStopY), paintText) }
        }
        //Log.i(tag, "Done!")
        return bitmapL
    }

    // draw only circles with text
    fun onDrawOnlyKP(csvModelList: List<CSVMapperModel>,bitmap: Bitmap,color: Int) : Bitmap {
        //Log.i(tag, "starting to dra bitmap")
        // val tmpBitmap =
        bitmapKP = Bitmap.createBitmap(bitmap)
        canvasKP = Canvas(bitmapKP)

        paint.color = color
        paint.strokeWidth = 2.0f

        paintText.color = Color.BLACK
        paintText.textSize = 8.5f
        paintText.typeface = Typeface.MONOSPACE
        paintText.style = Paint.Style.FILL_AND_STROKE

        //Log.i(tag, "Drawing elem in canvas 2")
        for (elem in csvModelList){
            canvasKP.drawCircle(elem.x.toFloat(), elem.y.toFloat(), circleRadius, paint)
//            elem.partName?.let { canvasKP.drawText(it, elem.x -(0.15f * elem.x), elem.y-(.05f * elem.y), paintText) }
        }
        //Log.i(tag, "Done 1")
        return bitmapKP
    }

    fun showContentPreview(csvModelList: MutableList<ArrayList<CSVMapperModel>>): SpannableStringBuilder{
        //Log.i(tag, "Entry in content preview")
        val head = headerKP.split(",")
        val header = "${head[0]}   ${head[1]}   ${head[2]}   ${head[3]}"
        val formattedOutput: SpannableStringBuilder = SpannableStringBuilder()

        formattedOutput
                .append(header)
                .append("\n\n")
                //.set(0, header.length-1, ForegroundColorSpan(Color.MAGENTA))

        //Log.i(tag, "header added")
        csvModelList.forEach { action-> action.forEach {
            formattedOutput.append("${it.x}   ${it.y}   ${it.partName}   "+String.format("%.2f", it.partScore * 100)+"%", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            formattedOutput.append("\n")
        } }

        return formattedOutput
    }

    fun onExport(text: String, outputName: String, outputDir: File, modelSelection: Int = 0){
        val content = text.split("\n")

        // add extension to name

        var fileName = FileManagerUtils.removeFileExtension(outputName)

        if ( modelSelection == 0){
            fileName += "_single"
        } else if (modelSelection == 1){
            fileName += "_detection_single"
        }else if (modelSelection == 2){
            fileName += "_multi"
        }


        fileName = "$fileName.csv"
        fileName = outputDir.path+File.separator+fileName

        var csvWriter: FileWriter? = null
        try {
            csvWriter = FileWriter(fileName)

            val header = prepareToAppend(content[0])
            csvWriter.append(header)
            csvWriter.append("\n")

            for (i in 2 until content.size-1){
                val newLine = prepareToAppend(content[i])
                csvWriter.append(newLine)
                csvWriter.append("\n")
            }
            Log.i(tag, "Content successfully wrote")
        }catch (io: IOException){
            Log.i(tag, "Error writting content to csv file,  : ${io.message}")
        }finally {
            try {
                csvWriter!!.flush()
                csvWriter.close()
            }catch (e: Exception){
                Log.i(tag, "Error closing writer  : ${e.message}")
            }
        }
    }

    private fun prepareToAppend(line: String): String{
        var newLine = line.replace("   ", " ");
        newLine = newLine.replace(" ", ",")

        return newLine
    }

//    fun showContentPreview(csvModelList: ArrayList<CSVMapperModel>): SpannableStringBuilder{
//        //Log.i(tag, "Entry in content preview")
//        val head = headerKP.split(",")
//        val header = "${head[0]}    ${head[1]}    ${head[2]}    ${head[3]}"
//        val formattedOutput: SpannableStringBuilder = SpannableStringBuilder()
//
//        formattedOutput
//                .append(header)
//                .append("\n\n")
//        //.set(0, header.length-1, ForegroundColorSpan(Color.MAGENTA))
//
//        //Log.i(tag, "header added")
//
//        csvModelList.forEach {
//            //Log.i(tag, it.getContentToString().replace(",", "  "))
//            formattedOutput.append("${it.x}  ${it.y}  ${it.partName}  "+String.format("%.2f", it.partScore * 100)+"%", 0.1f, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
//            formattedOutput.append("\n")
//        }
//        //Log.i(tag, "\n\nFinal  content:\n$formattedOutput")
//
//        return formattedOutput
//    }
}
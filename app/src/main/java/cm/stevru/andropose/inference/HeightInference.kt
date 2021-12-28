package cm.stevru.andropose.inference

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import cm.stevru.andropose.bodypart.Person
import cm.stevru.andropose.bodypart.Position
import cm.stevru.andropose.bodypart.enums.BodyPartEnum
import kotlin.math.abs
import kotlin.math.sqrt

//import org.bytedeco.opencv.opencv_core.Mat


class HeightInference {

    lateinit var bitmap: Bitmap
    var summaryInference: MutableMap<String, Float> = mutableMapOf()

    private final val TAG = "HHE"

    var pixel_mm_distance = 0f

    var eye_mm_distance = 65f

    fun estimatPersonHeight(person: Person, srcBitmap: Bitmap):Bitmap{
        bitmap = srcBitmap
       // Log.i(TAG, "image resolution: (width=${bitmap.width}, height=${bitmap.height}")
       // Log.i(TAG, "person: $person")
        //Log.i(TAG, "Bitmap: ${bitmap.width}, ${bitmap.height}")
        val hh = estimateHeadHeight(person)

        if(person.root.score == 0.0f){
        bitmap = drawHeight(bitmap, hh[0].x.toFloat(), hh[1].x.toFloat(), hh[0].y.toFloat(), hh[1].y.toFloat())
            Log.i(TAG,"Single Estimation, no RootPoint Needed")
        }

        else {

            bitmap = drawHeight(
                    bitmap,
                    hh[0].x.toFloat(),
                    hh[1].x.toFloat(),
                    hh[0].y.toFloat(),
                    hh[1].y.toFloat()
//                    person.root.position.x.toFloat(),
            )
//                bitmap = drawHeight(
//                    bitmap,
//                    hh[0].x.toFloat(),
//                    hh[2].x.toFloat(),
//                    hh[0].y.toFloat(),
//                    hh[3].y.toFloat(),
//                    person.root.position.x.toFloat(),
//                )
            Log.i(TAG,"Multi Estimation with RootPoint")
        }
        return bitmap
    }

    private fun drawHeight(bitmap: Bitmap, x1: Float, x2:Float, y1:Float, y2:Float):Bitmap{
        //Log.i(TAG,"on draw height..")
        val canvas = Canvas(bitmap)
        //Log.i(TAG, "Canvas: ${canvas.width}, ${canvas.height}")

        val paint = Paint()
        paint.color = Color.BLUE
        //canvas.drawLine( x1, y1, x2, y2, paint)
        canvas.drawLine( x1, y1, x2, y2, paint)
        val top = Position()
        top.x = x1.toInt()
        top.y = y1.toInt()

        val bottom = Position()
        bottom.x = x2.toInt()
        bottom.y = y2.toInt()

        val middle = estimateEyesMiddlePoint(top, bottom)
        val tt = estimateHH(top, bottom)
//        summaryInference["totalSizeHeadToAnkle"] = tt
//        canvas.drawText("%.2f mm".format(summaryInference["totalSizeWithRatio"]), (middle.x.toFloat() - top.x/2), middle.y.toFloat(), paint)
//        Log.i(TAG, "###### Size estimated: $tt, coord: (${middle.x}, ${middle.y}),  other:. ${summaryInference["totalSizeWithRatio"]} ######")
//        Log.i(TAG, "###### Size estimated: $tt, coord ori: (${bottom.x}, ${bottom.y}) ######")
//        Log.i(TAG, "###### Size estimated: $tt, coord ori: (${top.x}, ${top.y}) ######")
//        Log.i(TAG, "###### img size: (${bitmap.width}, ${bitmap.height}), coord ori: (${top.x}, ${top.y}) ######")
//        Log.i(TAG, "###### (1): (${top.x/2}, ${top.y/2}), (2): (${ (top.x/2)-middle.x}, ${(top.y/2)-middle.y}) ######")
//        Log.i(TAG, "###### img size: (${bitmap.width}, ${bitmap.height}), coord ori: (${top.x}, ${top.y}) ######")
//        if( (bitmap.height - y1) >= 20f){
//            // to avoid displaying text for out of
//            //canvas.drawText("%.2f cm".format(tt), x1, y1 + 10.5f, paint)
//            canvas.drawText("%f cm".format(summaryInference["totalSizeWithRatio"]), middle.x.toFloat(), middle.y.toFloat(), paint)
//            Log.i(TAG, "Size estimated: $tt, coord: ($x1, ${bitmap.height - y1})")
//            Log.i(TAG, "Bitmap size: (${bitmap.width}, ${bitmap.height})")
//        }else {
//            //canvas.drawText("%.2f cm".format(tt), x1, y1, paint)
//            canvas.drawText("%.2f cm".format(tt), x0, y1, paint)
//            Log.i(TAG, "Size estimated: $tt, coord: ($x1, $y1)")
//            Log.i(TAG, "Bitmap size: (${bitmap.width}, ${bitmap.height})")
//        }
        return bitmap
    }

    private fun estimateHeadHeight(person: Person): ArrayList<Position>{
        val positions = arrayOf(Position(), Position(), Position(), Position())
       // Log.i(TAG,"Size of array: ${positions.size}")


        for (keypoint in person.keyPoints){
            if(keypoint.bodyPart.name == BodyPartEnum.LEFT_EYE.name){

                val leftEye = Position()
                leftEye.x = keypoint.position.x
                leftEye.y = keypoint.position.y
                positions[1] = leftEye
            }
            if (keypoint.bodyPart.name == BodyPartEnum.RIGHT_EYE.name){

                val rightEye = Position()
                rightEye.x = keypoint.position.x
                rightEye.y = keypoint.position.y
                positions[0] = rightEye
            }
            if (keypoint.bodyPart.name == BodyPartEnum.NOSE.name){

                val nose = Position()
                nose.x = keypoint.position.x
                nose.y = keypoint.position.y
                positions[2] = nose
            }

            if (keypoint.bodyPart.name == BodyPartEnum.LEFT_ANKLE.name){

                val leftAnkle = Position()
                leftAnkle.x = keypoint.position.x
                leftAnkle.y = keypoint.position.y
                positions[3] = leftAnkle
            }
        }

        val  headHeight = ArrayList<Position>()

        // middle position between both eyes
        val mp = estimateEyesMiddlePoint(positions[1], positions[0])
        val d = noseEyeDistance(positions[2], mp)
        val a = estimateA(d)

        // add ratio to summary
        summaryInference["ratio"] = a

        //val hh = estimateHH(a)

        //top
        val foreHead = Position()
        foreHead.x = (positions[1].x - (mp.x/3))

        // from the middle position between the two eyes we add 4 times the value of "a"
        // this means, that we reduce the y-axis with 4xa.
        foreHead.y = (mp.y - (1.618f * 4 * a)).toInt()

        headHeight.add(foreHead)
        // bottom
        val chin = Position()
        chin.x = foreHead.x
        // from the nose position to the chin postion we need 3 times "a"
        chin.y = (positions[2].y + (3 * a).toInt())
        // estimated head height
        val hh = estimateHH(chin, foreHead)

        val hh_mm = hh*pixel_mm_distance

        summaryInference["headHeight"] = hh_mm
        // must be the distance between the forehead and chin
        headHeight.add(chin)

        // total size
        val height = Position()
        height.x = foreHead.x
        height.y = (8 * hh).toInt()

        // compute size

        summaryInference["totalSizeWithRatio"] = (8 * hh_mm)//total size
        summaryInference["shoulderDistance"] = (2 * hh_mm) // shoulder distance
        summaryInference["armLength"] = (3 * hh_mm) // arm length
        summaryInference["legLength"] = (4 * hh_mm) // leg length

        val ankle = Position()
        ankle.x = foreHead.x
        ankle.y = positions[3].y
        val totalLenght = noseEyeDistance(foreHead, ankle)
        headHeight.add(ankle)
        Log.i(TAG, "************ mp: ${mp.x}, ${mp.y}, d=$d, a=$a, hh=$hh, 8*hh=${8*hh}, " +
                "headheight(topx=(${headHeight[0].x}, topy=${headHeight[0].y}), (bottomx=${headHeight[1].x}, bottomy=${headHeight[1].y}\n" +
                "Top to ankle dist=$totalLenght")

        return headHeight
    }

    // estimate middle point between both eyes
    private fun estimateEyesMiddlePoint(left: Position, right: Position):Position{
        val middle = Position()
        middle.x = (left.x + right.x) / 2
        middle.y = (left.y + right.y) / 2

        var between_eye_distance = abs(left.x - right.x)

        pixel_mm_distance = eye_mm_distance / between_eye_distance.toFloat()

        return middle
    }

    // estimate distance between middle point and the nose
    private fun noseEyeDistance(nose: Position, middlePoint: Position): Float{
        val distance = sqrt((nose.x - middlePoint.x) * (nose.x - middlePoint.x) + (nose.y - middlePoint.y) * (nose.y - middlePoint.y).toFloat())
        return distance.toFloat()
    }

    // estimate the unknown variable a
    private fun estimateA(distance:Float): Float{
        // (1.618 * 2a - a) = distance --> a = distance / (2.236)
        return (distance / 2.236f)
    }

    // estimate head height
    private fun estimateHH(foreHead:Position, chin:Position):Float{
        //(1.618f * 3 * a) + (3 * a)
        val hh = sqrt((chin.x - foreHead.x)*(chin.x - foreHead.x) + (chin.y - foreHead.y)*(chin.y - foreHead.y).toFloat())
        Log.i(TAG, " ******************* Head Height estimated: ${hh}mm,\n total size: ${8 * hh}mm\n shoulder size: ${2 * hh}mm\n arm size: ${3*hh}mm\n leg size: ${4*hh}mm")
        return hh
    }

    fun formatPersonHeight(){

    }
}
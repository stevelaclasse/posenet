package cm.stevru.andropose.models

import android.util.Log

class CSVMapperWithLineModel {

    var fromPartName         : String?   = null
    var toPartName           : String?   = null
    var fromPartScore        : Float     = 0.0f
    var toPartScore          : Float     = 0.0f
    var lineStartX           : Float     = 0.0f
    var lineStopX            : Float     = 0.0f
    var lineStartY           : Float     = 0.0f
    var lineStopY            : Float     = 0.0f

    val tag = "csvmodel2"

    constructor(){
        Log.i(tag, "Default constructor called")}
    constructor(from:String?, startX: Float, startY: Float, fromScore:Float, to: String?, endX: Float, endY: Float, toScore:Float){
        Log.i(tag, "constructor called")

        this.fromPartName   = from
        this.lineStartX     = startX
        this.lineStartY     = startY
        this.toPartName     = to
        this.lineStopX      = endX
        this.lineStopY      = endY
        this.toPartScore    = toScore
        this.fromPartScore  = fromScore
    }

    override fun toString(): String {
        return "Keypoint{[x=$lineStartX, y=$lineStartY, partName=$fromPartName, $lineStopX, $lineStopY, $toPartName partScore=$fromPartScore]}"
    }
    fun getContentToString(): String{
        return "$lineStartX,$lineStartY,$fromPartName,$fromPartScore,$lineStopX,$lineStopY,$toPartName,$toPartScore"
    }
}
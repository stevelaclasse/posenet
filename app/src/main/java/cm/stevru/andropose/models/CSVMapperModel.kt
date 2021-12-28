package cm.stevru.andropose.models

import android.util.Log

class CSVMapperModel {
    var x : Int = 0
    var y : Int = 0
    var partName: String?= null
    var partScore: Float = 0.0f

    val tag = "csvmodel"

    constructor(){Log.i(tag, "Default constructor called")}
    constructor(x:Int, y:Int, partName:String?, partScore:Float){
        Log.i(tag, "constructor called")
        this.x=x
        this.y=y
        this.partName=partName
        this.partScore=partScore
    }

    override fun toString(): String {
        return "Keypoint{[x=$x, y=$y, partName=$partName, partScore=$partScore]}"
    }

    fun getContentToString(): String{
        return "$x,$y,$partName,$partScore"
    }
}
package cm.stevru.andropose.bodypart

import java.util.*
import kotlin.collections.HashMap

class Person {

    //var keyPoints: Array<AdapterView.AdapterContextMenuInfo>
    var keyPoints: List<KeyPoint> = listOf<KeyPoint>()
    var root:  KeyPoint = KeyPoint()
    var score: Float = 0.0f
}
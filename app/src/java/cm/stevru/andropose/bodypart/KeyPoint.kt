package cm.stevru.andropose.bodypart

import cm.stevru.andropose.bodypart.enums.BodyPartEnum

class KeyPoint {
    var bodyPart: BodyPartEnum = BodyPartEnum.NOSE
    var position: Position = Position()
    var score: Float = 0.0f
}
//+++++++++++++++ Tflite Flutter Plugin Code Used Here ++++++++++++++++

package cm.stevru.andropose.inference;

import android.util.Log;

import com.google.android.material.animation.ImageMatrixProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import cm.stevru.andropose.bodypart.KeyPoint;
import cm.stevru.andropose.bodypart.Person;
import cm.stevru.andropose.bodypart.Position;
import cm.stevru.andropose.bodypart.enums.BodyPartEnum;
import cm.stevru.andropose.fragments.ImageFragment;

public class DecodePose {

    private String TAG = "decodePose";

    String[] partNames = {
            "nose", "leftEye", "rightEye", "leftEar", "rightEar", "leftShoulder",
            "rightShoulder", "leftElbow", "rightElbow", "leftWrist", "rightWrist",
            "leftHip", "rightHip", "leftKnee", "rightKnee", "leftAnkle", "rightAnkle"
    };


    String[][] poseChain = {
            {"nose", "leftEye"}, {"leftEye", "leftEar"}, {"nose", "rightEye"},
            {"rightEye", "rightEar"}, {"nose", "leftShoulder"},
            {"leftShoulder", "leftElbow"}, {"leftElbow", "leftWrist"},
            {"leftShoulder", "leftHip"}, {"leftHip", "leftKnee"},
            {"leftKnee", "leftAnkle"}, {"nose", "rightShoulder"},
            {"rightShoulder", "rightElbow"}, {"rightElbow", "rightWrist"},
            {"rightShoulder", "rightHip"}, {"rightHip", "rightKnee"},
            {"rightKnee", "rightAnkle"}
    };

    List<String> myBodyParts = new ArrayList<>();



    double threshold = 0.5;     // defaults to 0.5
    int nmsRadius= 10; //20
    int numResults; //5
    int inputSize = 337;
    int outputStride = 16;
    int localMaximumRadius = 1;
    Map<Integer, Object> outputMap;
    int height;
    int width;

    Map<String, Integer> partsIds = new HashMap<>();
    List<Integer> parentToChildEdges = new ArrayList<>();
    List<Integer> childToParentEdges = new ArrayList<>();

    public DecodePose(int numberPerson) {
        numResults = numberPerson;
    }

    protected List<Person> decodeOutput(Map<Integer, Object> outputMap) {
        this.outputMap = outputMap;
        for (int i = 0; i < partNames.length; ++i)
            partsIds.put(partNames[i], i);

        for (int i = 0; i < poseChain.length; ++i) {
            parentToChildEdges.add(partsIds.get(poseChain[i][1]));
            childToParentEdges.add(partsIds.get(poseChain[i][0]));
        }

        float[][][] scores = ((float[][][][]) outputMap.get(0))[0];
        float[][][] offsets = ((float[][][][]) outputMap.get(1))[0];
        float[][][] displacementsFwd = ((float[][][][]) outputMap.get(2))[0];
        float[][][] displacementsBwd = ((float[][][][]) outputMap.get(3))[0];

        this.height = scores.length;
        this.width = scores[0].length;
        int  numKeypoints = scores[0][0].length;
        Log.d(TAG,"######Height:"+height);
        Log.d(TAG,"######Width:"+width);
        Log.d(TAG,"#####NumKeypoints:"+numKeypoints);

        for (BodyPartEnum bodyPartEnum : BodyPartEnum.values()){
                myBodyParts.add(bodyPartEnum.toString());
        }

        PriorityQueue<Map<String, Object>> pq = buildPartWithScoreQueue(scores, threshold, localMaximumRadius);

        int numParts = scores[0][0].length;
        int numEdges = parentToChildEdges.size();
        int sqaredNmsRadius = nmsRadius * nmsRadius;

        List<Map<String, Object>> results = new ArrayList<>();

        List<Person> allpersons = new ArrayList<>();

        while (results.size() < numResults && pq.size() > 0) {

            Map<String, Object> root = pq.poll();
            float[] rootPoint = getImageCoords(root, outputStride, numParts, offsets);

            if (withinNmsRadiusOfCorrespondingPoint(
                    results, sqaredNmsRadius, rootPoint[0], rootPoint[1], (int) root.get("partId")))
                continue;

            Map<String, Object> keypoint = new HashMap<>();
            keypoint.put("score", root.get("score"));
            keypoint.put("part", partNames[(int) root.get("partId")]);
            keypoint.put("partNum", (int) root.get("partId"));
            keypoint.put("y", rootPoint[0] / inputSize);
            keypoint.put("x", rootPoint[1] / inputSize);
            keypoint.put("pos_y",root.get("y"));
            keypoint.put("pos_x", root.get("x"));


            Map<Integer, Map<String, Object>> keypoints = new HashMap<>();
            keypoints.put((int) root.get("partId"), keypoint);
            for (int edge = numEdges - 1; edge >= 0; --edge) {
                int sourceKeypointId = parentToChildEdges.get(edge);
                int targetKeypointId = childToParentEdges.get(edge);
                if (keypoints.containsKey(sourceKeypointId) && !keypoints.containsKey(targetKeypointId)) {
                    keypoint = traverseToTargetKeypoint(edge, keypoints.get(sourceKeypointId),
                            targetKeypointId, scores, offsets, outputStride, displacementsBwd);
                    keypoints.put(targetKeypointId, keypoint);


                }
            }

            for (int edge = 0; edge < numEdges; ++edge) {
                int sourceKeypointId = childToParentEdges.get(edge);
                int targetKeypointId = parentToChildEdges.get(edge);
                if (keypoints.containsKey(sourceKeypointId) && !keypoints.containsKey(targetKeypointId)) {
                    keypoint = traverseToTargetKeypoint(edge, keypoints.get(sourceKeypointId),
                            targetKeypointId, scores, offsets, outputStride, displacementsFwd);
                    keypoints.put(targetKeypointId, keypoint);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("keypoints", keypoints);
            result.put("score", getInstanceScore(keypoints, numParts));
            result.put("root_name",partNames[(int) root.get("partId")]);
            result.put("root_id",(int) root.get("partId"));
            result.put("root_y",(int)rangeScale(rootPoint[0] / inputSize));
            result.put("root_x",(int) rangeScale(rootPoint[1] / inputSize));
            results.add(result);
            Log.d(TAG,"########Result:"+result);

        }

        return mapKeypointsToPerson(results);
    }

    public List<Person> mapKeypointsToPerson(List<Map<String, Object>> results) {
        List<Person> allpersons = new ArrayList<>();

        Person person;
        List<KeyPoint> keyPoints;
        KeyPoint keyPoint ;
        Position position ;

        List<Map<String, Object>> myResults = results;

        int min ;
        while ((min=mostLeftResult(myResults)) != -1)
        {
            //min = mostLeftResult(results);

            Map<Integer, Map<String, Object>> keypoints = new HashMap<>();

            Map<String, Object> result = new HashMap<>();

            result = myResults.get(min);

            keypoints = (Map<Integer, Map<String, Object>>) result.get("keypoints");

            person = new Person();

            keyPoints = new ArrayList<KeyPoint>();

            for (Map<String, Object> key_point : keypoints.values()) {

                int keypointId = (int) key_point.get("partNum");

                position = new Position();


                position.setX((int)rangeScale((float)(key_point.get("x")))) ;

                position.setY((int)rangeScale((float)(key_point.get("y")))) ;

                keyPoint = new KeyPoint();

                keyPoint.setPosition(position);

                keyPoint.setScore((Float) key_point.get("score"));

                keyPoint.setBodyPart(BodyPartEnum.valueOf(myBodyParts.get(keypointId)));

                keyPoints.add(keyPoint);
            }

            person.setScore((Float) result.get("score"));

            person.setKeyPoints(keyPoints);

            //adding the rootKeypoint

            KeyPoint rootKeyPoint = new KeyPoint();

            int rootKeypointId = (int) result.get("root_id");

            rootKeyPoint.setBodyPart(BodyPartEnum.valueOf(myBodyParts.get(rootKeypointId)));

            Position rootPosition = new Position();

            rootPosition.setX((int)result.get("root_x")) ;

            rootPosition.setY((int)result.get("root_y")) ;

            rootKeyPoint.setPosition(rootPosition);

            rootKeyPoint.setScore(0.01f);  //Just to test

            person.setRoot(rootKeyPoint);

            Log.d(TAG,"Number of Keypoints:"+person.getKeyPoints().size());

            myResults.remove(min);

            allpersons.add(person);

    }

        return  allpersons;
    }

    public int mostLeftResult(List<Map<String, Object>> results){
        int min = 0;
        if(!results.isEmpty()) {
            for (int i = 0; i < results.size(); i++) {
                if ((int) results.get(i).get("root_x") <= (int) results.get(min).get("root_x")) {
                    min = i;
                }
            }

            Log.i(TAG,"Min Indice:"+min+" With Value:"+results.get(min).get("root_x"));
            return min;
        }
        else{
            return -1;
        }

    }

    public float rangeScale(float position){
        float OldMax = 1 ;
        float OldMin = 0 ;
        float NewMax = inputSize ;
        float NewMin = 0 ;
        float OldRange = (OldMax - OldMin);
        float NewRange = (NewMax - NewMin) ;
        float NewPosition = (((position - OldMin) * NewRange) / OldRange) + NewMin ;

        return NewPosition ;
    }

    public int[] calculateCoordinate(int posX, int posY,int keypointId,int numParts, float[][][] offsets){
        float x = (((float)posX/(float)(width-1))*inputSize)+offsets[posY][posX][keypointId+numParts];
        float y = (((float)posY/(float)(height-1))*inputSize)+offsets[posY][posX][keypointId];

        return new int[]{(int)x,(int)y};
    }

    PriorityQueue<Map<String, Object>> buildPartWithScoreQueue(float[][][] scores,
                                                               double threshold,
                                                               int localMaximumRadius) {
        PriorityQueue<Map<String, Object>> pq =
                new PriorityQueue<>(
                        1,
                        new Comparator<Map<String, Object>>() {
                            @Override
                            public int compare(Map<String, Object> lhs, Map<String, Object> rhs) {
                                return Float.compare((float) rhs.get("score"), (float) lhs.get("score"));
                            }
                        });

        for (int heatmapY = 0; heatmapY < scores.length; ++heatmapY) {
            for (int heatmapX = 0; heatmapX < scores[0].length; ++heatmapX) {
                for (int keypointId = 0; keypointId < scores[0][0].length; ++keypointId) {
                    float score = sigmoid(scores[heatmapY][heatmapX][keypointId]);
                    if (score < threshold) continue;

                    if (scoreIsMaximumInLocalWindow(
                            keypointId, score, heatmapY, heatmapX, localMaximumRadius, scores)) {
                        Map<String, Object> res = new HashMap<>();
                        res.put("score", score);
                        res.put("y", heatmapY);
                        res.put("x", heatmapX);
                        res.put("partId", keypointId);
                        res.put("partName",partNames[keypointId]);
                        pq.add(res);
                    }
                }
            }
        }

        return pq;
    }

    boolean scoreIsMaximumInLocalWindow(int keypointId,
                                        float score,
                                        int heatmapY,
                                        int heatmapX,
                                        int localMaximumRadius,
                                        float[][][] scores) {
        boolean localMaximum = true;
        int height = scores.length;
        int width = scores[0].length;

        int yStart = Math.max(heatmapY - localMaximumRadius, 0);
        int yEnd = Math.min(heatmapY + localMaximumRadius + 1, height);
        for (int yCurrent = yStart; yCurrent < yEnd; ++yCurrent) {
            int xStart = Math.max(heatmapX - localMaximumRadius, 0);
            int xEnd = Math.min(heatmapX + localMaximumRadius + 1, width);
            for (int xCurrent = xStart; xCurrent < xEnd; ++xCurrent) {
                if (sigmoid(scores[yCurrent][xCurrent][keypointId]) > score) {
                    localMaximum = false;
                    break;
                }
            }
            if (!localMaximum) {
                break;
            }
        }

        return localMaximum;
    }


    float[] getImageCoords(Map<String, Object> keypoint,
                           int outputStride,
                           int numParts,
                           float[][][] offsets) {
        int heatmapY = (int) keypoint.get("y");
        int heatmapX = (int) keypoint.get("x");

        int keypointId = (int) keypoint.get("partId");
        float offsetY = offsets[heatmapY][heatmapX][keypointId];
        float offsetX = offsets[heatmapY][heatmapX][keypointId + numParts];

        float y = heatmapY * outputStride + offsetY;
        float x = heatmapX * outputStride + offsetX;
        return new float[]{y, x};
    }

    boolean withinNmsRadiusOfCorrespondingPoint(List<Map<String, Object>> poses,
                                                float squaredNmsRadius,
                                                float y,
                                                float x,
                                                int keypointId) {
        for (Map<String, Object> pose : poses) {
            Map<Integer, Object> keypoints = (Map<Integer, Object>) pose.get("keypoints");
            Map<String, Object> correspondingKeypoint = (Map<String, Object>) keypoints.get(keypointId);
            float _x = (float) correspondingKeypoint.get("x") * inputSize - x;
            float _y = (float) correspondingKeypoint.get("y") * inputSize - y;
            float squaredDistance = _x * _x + _y * _y;
            if (squaredDistance <= squaredNmsRadius)
                return true;
        }

        return false;
    }

    Map<String, Object> traverseToTargetKeypoint(int edgeId,
                                                 Map<String, Object> sourceKeypoint,
                                                 int targetKeypointId,
                                                 float[][][] scores,
                                                 float[][][] offsets,
                                                 int outputStride,
                                                 float[][][] displacements) {
        int height = scores.length;
        int width = scores[0].length;
        int numKeypoints = scores[0][0].length;
        float sourceKeypointY = (float) sourceKeypoint.get("y") * inputSize;
        float sourceKeypointX = (float) sourceKeypoint.get("x") * inputSize;

        int[] sourceKeypointIndices = getStridedIndexNearPoint(sourceKeypointY, sourceKeypointX,
                outputStride, height, width);

        float[] displacement = getDisplacement(edgeId, sourceKeypointIndices, displacements);

        float[] displacedPoint = new float[]{
                sourceKeypointY + displacement[0],
                sourceKeypointX + displacement[1]
        };

        float[] targetKeypoint = displacedPoint;
        int newTargetKeypointX = 0;
        int newTargetKeypointY = 0;

        final int offsetRefineStep = 2;
        for (int i = 0; i < offsetRefineStep; i++) {
            int[] targetKeypointIndices = getStridedIndexNearPoint(targetKeypoint[0], targetKeypoint[1],
                    outputStride, height, width);

            int targetKeypointY = targetKeypointIndices[0];
            int targetKeypointX = targetKeypointIndices[1];

            newTargetKeypointY = targetKeypointIndices[0];
            newTargetKeypointX = targetKeypointIndices[1];

            float offsetY = offsets[targetKeypointY][targetKeypointX][targetKeypointId];
            float offsetX = offsets[targetKeypointY][targetKeypointX][targetKeypointId + numKeypoints];

            targetKeypoint = new float[]{
                    targetKeypointY * outputStride + offsetY,
                    targetKeypointX * outputStride + offsetX
            };
        }

        int[] targetKeypointIndices = getStridedIndexNearPoint(targetKeypoint[0], targetKeypoint[1],
                outputStride, height, width);

        float score = sigmoid(scores[targetKeypointIndices[0]][targetKeypointIndices[1]][targetKeypointId]);

        Map<String, Object> keypoint = new HashMap<>();
        keypoint.put("score", score);
        keypoint.put("part", partNames[targetKeypointId]);
        keypoint.put("partNum", targetKeypointId);
        keypoint.put("y", targetKeypoint[0] / inputSize);
        keypoint.put("x", targetKeypoint[1] / inputSize);
        keypoint.put("pos_y", targetKeypointIndices[0]);
        keypoint.put("pos_x", targetKeypointIndices[1]);

        return keypoint;
    }

    int[] getStridedIndexNearPoint(float _y, float _x, int outputStride, int height, int width) {
        int y_ = Math.round(_y / outputStride);
        int x_ = Math.round(_x / outputStride);
        int y = y_ < 0 ? 0 : y_ > height - 1 ? height - 1 : y_;
        int x = x_ < 0 ? 0 : x_ > width - 1 ? width - 1 : x_;
        return new int[]{y, x};
    }

    float[] getDisplacement(int edgeId, int[] keypoint, float[][][] displacements) {
        int numEdges = displacements[0][0].length / 2;
        int y = keypoint[0];
        int x = keypoint[1];
        return new float[]{displacements[y][x][edgeId], displacements[y][x][edgeId + numEdges]};
    }

    float getInstanceScore(Map<Integer, Map<String, Object>> keypoints, int numKeypoints) {
        float scores = 0;
        for (Map.Entry<Integer, Map<String, Object>> keypoint : keypoints.entrySet())
            scores += (float) keypoint.getValue().get("score");
        return scores / numKeypoints;
    }

    private float sigmoid(final float x) {
        return (float) (1. / (1. + Math.exp(-x)));
    }
}




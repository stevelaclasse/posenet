package cm.stevru.andropose.audioprocessing;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
// Copyright (c) 2018 ArsalImam
// All Rights Reserved
//
public class AudioExtractor {

    private static final String TAG = "Decoder";

    @SuppressLint("NewApi")
    public void addAudioToVideo(String audioSrcPath, String videoSrcPath, String videoDstPath) throws IOException {

        MediaExtractor videoExtractor = new MediaExtractor();
        videoExtractor.setDataSource(videoSrcPath);
        videoExtractor.selectTrack(0);

        MediaExtractor audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(audioSrcPath);
        audioExtractor.selectTrack(0);

        MediaMuxer muxer = new MediaMuxer(videoDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);


        MediaFormat videoFormat = videoExtractor.getTrackFormat(0);
        MediaFormat audioFormat = audioExtractor.getTrackFormat(0);

        int videoTrack = muxer.addTrack(videoFormat);
        int audioTrack = muxer.addTrack(audioFormat);

        Log.d(TAG, "Video Info " + videoFormat.toString() );
        Log.d(TAG, "Audio Info " + audioFormat.toString() );

        boolean sawEOS = false;
        int frameCount = 0;
        int offset = 100;
        int sampleSize = 256 * 1024;

        ByteBuffer videoBuffer = ByteBuffer.allocate(sampleSize);
        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();



        videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        muxer.start();

        while (!sawEOS)
        {
            videoBufferInfo.offset = offset;
            videoBufferInfo.size = videoExtractor.readSampleData(videoBuffer, offset);


            if (videoBufferInfo.size < 0)
            {
                Log.d(TAG, "saw input EOS.");
                sawEOS = true;
                videoBufferInfo.size = 0;

            }
            else
            {
                videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                videoBufferInfo.flags = videoExtractor.getSampleFlags();
                muxer.writeSampleData(videoTrack, videoBuffer, videoBufferInfo);
                videoExtractor.advance();


                frameCount++;
                Log.d(TAG, "Frame (" + frameCount + ") Video PresentationTimeUs:" + videoBufferInfo.presentationTimeUs +" Flags:" + videoBufferInfo.flags +" Size(KB) " + videoBufferInfo.size / 1024);


            }
        }
        Log.d(TAG, "Frames of Video :" + frameCount);


        ByteBuffer audioBuffer = ByteBuffer.allocate(sampleSize);
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

        sawEOS = false;
        frameCount =0;
        while (!sawEOS)
        {
            frameCount++;

            audioBufferInfo.offset = offset;
            audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, offset);

            if (audioBufferInfo.size < 0)
            {
                Log.d(TAG, "saw input EOS.");
                sawEOS = true;
                audioBufferInfo.size = 0;
            }
            else
            {
                audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                audioBufferInfo.flags = audioExtractor.getSampleFlags();
                muxer.writeSampleData(audioTrack, audioBuffer, audioBufferInfo);
                audioExtractor.advance();

                Log.d(TAG, "Frame (" + frameCount + ") Audio PresentationTimeUs:" + audioBufferInfo.presentationTimeUs +" Flags:" + audioBufferInfo.flags +" Size(KB) " + audioBufferInfo.size / 1024);

            }
        }

        // Toast.makeText(getApplicationContext() , "frame:" + frameCount2 , Toast.LENGTH_SHORT).show();

        muxer.stop();
        muxer.release();



    }


    @SuppressLint("NewApi")
    public void extractAudioFromVideo(String videoSrcPath, String audioDstPath) throws IOException {

        MediaExtractor audioExtractor = new MediaExtractor();
        audioExtractor.setDataSource(videoSrcPath);

        int trackCount = audioExtractor.getTrackCount();

        Log.i(TAG,"Total Number Of Tracks:"+trackCount);

        MediaMuxer muxer = new MediaMuxer(audioDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // Set up the tracks.
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
        String mimeType;
        MediaFormat format;

        for (int i = 0; i < trackCount; i++) {
            audioExtractor.selectTrack(i);
            format = audioExtractor.getTrackFormat(i);

            mimeType = format.getString(MediaFormat.KEY_MIME);
            Log.i(TAG,"MimeTypeFound:"+mimeType);
            if (mimeType.startsWith("audio/")) {
                int dstIndex = muxer.addTrack(format);
                Log.i(TAG,"Track Added:"+format.toString());
                Log.i(TAG,"Index of the Added Track:"+dstIndex);
                indexMap.put(i, dstIndex);
            }

        }

        Log.i(TAG,"IndexMap Content:"+indexMap);


        boolean sawEOS = false;
        int frameCount = 0;
        int offset = 100;
        int sampleSize = 256 * 1024;
        int trackIndex;

        ByteBuffer audioBuffer = ByteBuffer.allocate(sampleSize);
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
        audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        muxer.start();

        while (!sawEOS)
        {
            audioBufferInfo.offset = offset;
            audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, offset);


            if (audioBufferInfo.size < 0)
            {
                Log.d(TAG, "saw input EOS.");
                sawEOS = true;
                audioBufferInfo.size = 0;

            }
            else
            {
                audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                audioBufferInfo.flags = audioExtractor.getSampleFlags();
                trackIndex = audioExtractor.getSampleTrackIndex();
                if(indexMap.containsKey(trackIndex)) {
                    muxer.writeSampleData(indexMap.get(trackIndex), audioBuffer, audioBufferInfo);
                }
                audioExtractor.advance();


                frameCount++;
                Log.d(TAG, "Frame (" + frameCount + ") Video PresentationTimeUs:" + audioBufferInfo.presentationTimeUs +" Flags:" + audioBufferInfo.flags +" Size(KB) " + audioBufferInfo.size / 1024);


            }
        }

        muxer.stop();
        muxer.release();



    }
}

package com.example.android.common.audioplayer;

import android.annotation.TargetApi;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import java.nio.ByteBuffer;

/**
 * 音频解码,播放线程
 * Created by tianbin on 16/5/18.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class DecodeAudioRunnable implements Runnable {

    /**
     * 正常播放结束标识
     */
    private boolean isPlayOver;

    private MediaExtractor mExtractor;
    private MediaCodec mAudioDecoder;
    private AudioTrack mAudioTrack;
    private MediaFormat mMediaFormat;

    private OnPlayListener mOnPlayListener;
    /**
     * 是否正在运行
     */
    private boolean isRunning;

    public DecodeAudioRunnable(MediaExtractor extractor, MediaCodec mediaCodec, AudioTrack audioTrack, MediaFormat mediaFormat) {
        mExtractor = extractor;
        mAudioDecoder = mediaCodec;
        mAudioTrack = audioTrack;
        mMediaFormat = mediaFormat;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void run() {
        ByteBuffer[] inputBuffersAudio = null;
        ByteBuffer[] outputBuffersAudio = null;
        MediaCodec.BufferInfo infoAudio = null;

        while (!Thread.interrupted() && !isPlayOver) {
            inputBuffersAudio = mAudioDecoder.getInputBuffers();
            outputBuffersAudio = mAudioDecoder.getOutputBuffers();
            infoAudio = new MediaCodec.BufferInfo();

            int inIndex = -1;
            try {
                inIndex = mAudioDecoder.dequeueInputBuffer(10000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (inIndex >= 0) {
                ByteBuffer buffer = inputBuffersAudio[inIndex];
                int sampleSize = mExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    mAudioDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    buffer.clear();
                    isPlayOver = true;
                } else {
                    mAudioDecoder.queueInputBuffer(inIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    buffer.clear();
                    mExtractor.advance();
                }

            }

            int outIndex = -1;
            try {
                outIndex = mAudioDecoder.dequeueOutputBuffer(infoAudio, 10000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    outputBuffersAudio = mAudioDecoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    mAudioTrack.setPlaybackRate(mMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;
                default:
                    if (outIndex >= 0) {
                        ByteBuffer buffer = outputBuffersAudio[outIndex];
                        byte[] chunk = new byte[infoAudio.size];
                        buffer.get(chunk);
                        buffer.clear();
                        if (chunk.length > 0) {
                            mAudioTrack.write(chunk, 0, chunk.length);
                        }
                        mAudioDecoder.releaseOutputBuffer(outIndex, false);
                    }
                    break;
            }
            if (isPlayOver) {
                break;
            }
        }

        //播放完毕
        if (isPlayOver) {
            onComplete();
        }
    }

    private void onComplete() {
        releaseResource();
        if (mOnPlayListener != null) {
            mOnPlayListener.onComplete();
        }
    }

    private void releaseResource() {
        if (mAudioDecoder != null) {
            mAudioDecoder.stop();
            mAudioDecoder.release();
            mAudioDecoder = null;
        }
        if (mExtractor != null) {
            mExtractor.release();
            mExtractor = null;
        }
    }

    public void setOnPlayListener(OnPlayListener onPlayListener) {
        mOnPlayListener = onPlayListener;
    }

    public interface OnPlayListener {
        void onComplete();
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            new Thread(this).start();
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}

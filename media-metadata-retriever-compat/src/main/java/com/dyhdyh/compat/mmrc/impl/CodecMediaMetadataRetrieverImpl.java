package com.dyhdyh.compat.mmrc.impl;

import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.dyhdyh.compat.mmrc.IMediaMetadataRetriever;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 基于
 * author  dengyuhan
 * created 2017/5/26 14:55
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class CodecMediaMetadataRetrieverImpl implements IMediaMetadataRetriever {

    private MediaExtractor mMediaExtractor;
    private MediaFormat mTrackFormat;
    private MediaCodec mMediaCodec;
    private MediaCodec.BufferInfo mBufferInfo;
    private long presentationTimeUs = 0, duration = 0;
    private int mCurFrameIndex = -1;

    private final int COLOR_FORMAT_MTK = 19;
    private final int COLOR_FORMAT = 21;
    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;

    public CodecMediaMetadataRetrieverImpl() {
        this.mMediaExtractor = new MediaExtractor();
    }

    @Override
    public void setDataSource(String path) {
        try {
            this.mMediaExtractor.setDataSource(path);
            //
            init();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void init() throws IOException {
        int videoIndex = 0;
        int numTracks = mMediaExtractor.getTrackCount();
        for (int i = 0; i < numTracks; ++i) {
            MediaFormat format = mMediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoIndex = i;
            }
        }

        int colorFormat;
        //平台差异
        if (Build.HARDWARE.toLowerCase().contains("mt")) {
            //mtk
            colorFormat = COLOR_FORMAT_MTK;
        } else {
            colorFormat = COLOR_FORMAT;
        }

        mTrackFormat = mMediaExtractor.getTrackFormat(videoIndex);
        String mime = mTrackFormat.getString(MediaFormat.KEY_MIME);
        mMediaCodec = MediaCodec.createDecoderByType(mime);
        mTrackFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

        mMediaCodec.configure(mTrackFormat, null, null, 0);
        mMediaCodec.start();

        mMediaExtractor.selectTrack(videoIndex);
        // 用来存放目标文件的数据
        mInputBuffers = mMediaCodec.getInputBuffers();
        // 解码后的数据
        mOutputBuffers = mMediaCodec.getOutputBuffers();
        mBufferInfo = new MediaCodec.BufferInfo();
    }
    private final long DEFAULT_TIMEOUT_US = 10000;
    @Override
    public Bitmap getFrameAtTime() {
        Bitmap bitmap = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            int outputBufferId = mMediaCodec.dequeueOutputBuffer(mBufferInfo, DEFAULT_TIMEOUT_US);
            Image image = mMediaCodec.getOutputImage(outputBufferId);
            /*
            Image.Plane[] planes = image.getPlanes();
            if (planes.length > 0) {
                ByteBuffer buffer = planes[0].getBuffer();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                bitmap = BitmapFactory.decodeByteArray(data, 0, 0);
            }
            */
        }
        //decode();
        return bitmap;
    }


    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }
    private void decode() {
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        if (!sawOutputEOS) {
            final long kTimeOutUs = 10;
            int inputBufIndex = mMediaCodec.dequeueInputBuffer(kTimeOutUs);
            if (inputBufIndex >= 0) {
                ByteBuffer dstBuf = mInputBuffers[inputBufIndex];

                int sampleSize = mMediaExtractor.readSampleData(dstBuf, 0);
                if (sampleSize < 0) {
                    sawInputEOS = true;
                    sampleSize = 0;
                } else {
                    presentationTimeUs = mMediaExtractor.getSampleTime();
                }

                mMediaCodec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs,
                        sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                if (!sawInputEOS) {
                    mMediaExtractor.advance();
                }

                int outputBufIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, kTimeOutUs);
                /*if (outputBufIndex >= 0) {
                    mCurFrameIndex += 1;
                    if (index == mCurFrameIndex) {
                        ByteBuffer buf = mOutputBuffers[outputBufIndex];
                        final byte[] nv12Data = new byte[buf.capacity()];
                        buf.position(mBufferInfo.offset);
                        buf.limit(mBufferInfo.offset + buf.capacity());
                        buf.get(nv12Data);
                        buf.clear();
                        if (nv12Data.length > 0) {
                            long start = System.currentTimeMillis();

                            byte[] argbData = new byte[width * height * 4];

                            if (colorFormat == 21) {
                                byte[] i420Data = new byte[width * height * 3 / 2];
                                LibYuvConverter.yuvToYuv(nv12Data, width, height, i420Data, LibYuvConverter.IYuvConverterType.NV12ToI420Internal);
                                LibYuvConverter.yuvToRgb(i420Data, width, height, argbData, LibYuvConverter.IYuvConverterType.I420ToARGBInternal);
                            } else {
                                LibYuvConverter.yuvToRgb(nv12Data, width, height, argbData, LibYuvConverter.IYuvConverterType.I420ToARGBInternal);
                            }

                            try {
                                int[] pixels = new int[width * height];

                                int bytesCount = width * height * 4;
                                LibYuvConverter.byteArrayToIntArray(argbData, pixels, bytesCount, LibYuvConverter.IYuvConverterType.ByteArrayToIntArrayInternal);

                                Bitmap finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                finalBitmap.setPixels(pixels, 0, width, 0, 0, width, height);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        }

                        mMediaCodec.releaseOutputBuffer(outputBufIndex, false);
                        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true;
                        }
                    }
                }*/
            }
        }
    }

    @Override
    public Bitmap getFrameAtTime(long timeUs, int option) {
        return null;
    }

    @Override
    public Bitmap getScaledFrameAtTime(long timeUs, int width, int height) {
        return null;
    }

    @Override
    public Bitmap getScaledFrameAtTime(long timeUs, int option, int width, int height) {
        return null;
    }

    @Override
    public byte[] getEmbeddedPicture() {
        return new byte[0];
    }

    @Override
    public String extractMetadata(String keyCode) {
        return null;
    }

    @Override
    public void release() {

    }
}

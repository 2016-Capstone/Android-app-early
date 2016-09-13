package kr.ac.kmu.ncs.dronecontroller.view;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.parrot.arsdk.arcontroller.ARCONTROLLER_STREAM_CODEC_TYPE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import kr.ac.kmu.ncs.dronecontroller.Constants;

/**
 * Created by NCS-KSW on 2016-09-12.
 *
 * SurfaceView : 그림을 그리는 뷰, UI Th가 아닌곳에서도 출력 가능, 반복적으로 화면에 뿌리는 작업을 할 때 사용, 주로 동영상 재생에 사용, 메인윈도우보다 뒤에 위치
 * SurfaceHolder : Surface를 관리하는 객체체 */
public class BebopVideoView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "BebopVideoView";

    private MediaCodec mMediaCodec;
    private Lock mReadyLock;

    private boolean mIsCodecConfigured = false;
    private ByteBuffer mSpsBuffer;
    private ByteBuffer mPpsBuffer;
    private ByteBuffer[] mBuffers;

    /*
    Constructor
    */
    public BebopVideoView(Context context) {
        super(context);
        customInit();
    }

    public BebopVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        customInit();
    }

    public BebopVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        customInit();
    }

    private void customInit() {
        mReadyLock = new ReentrantLock();
        getHolder().addCallback(this);  //set SurfaceHolder
    }
    // end

    public void displayFrame(ARFrame frame) {
        mReadyLock.lock();

        if ((mMediaCodec != null)) {
            if (mIsCodecConfigured) {
                // Here we have either a good PFrame, or an IFrame
                int index = -1;

                try {
                    index = mMediaCodec.dequeueInputBuffer(Constants.VIDEO_DEQUEUE_TIMEOUT);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error while dequeue input buffer");
                }
                if (index >= 0) {
                    ByteBuffer b;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        b = mMediaCodec.getInputBuffer(index);
                    } else {
                        b = mBuffers[index];
                        b.clear();
                    }

                    if (b != null) {
                        b.put(frame.getByteData(), 0, frame.getDataSize());
                    }

                    try {
                        mMediaCodec.queueInputBuffer(index, 0, frame.getDataSize(), 0, 0);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error while queue input buffer");
                    }
                }
            }

            // Try to display previous frame
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outIndex;
            try {
                outIndex = mMediaCodec.dequeueOutputBuffer(info, 0);

                while (outIndex >= 0) {
                    mMediaCodec.releaseOutputBuffer(outIndex, true);
                    outIndex = mMediaCodec.dequeueOutputBuffer(info, 0);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error while dequeue input buffer (outIndex)");
            }
        }


        mReadyLock.unlock();
    }

    public void configureDecoder(ARControllerCodec codec) {
        mReadyLock.lock();

        if (codec.getType() == ARCONTROLLER_STREAM_CODEC_TYPE_ENUM.ARCONTROLLER_STREAM_CODEC_TYPE_H264) {
            ARControllerCodec.H264 codecH264 = codec.getAsH264();

            mSpsBuffer = ByteBuffer.wrap(codecH264.getSps().getByteData());
            mPpsBuffer = ByteBuffer.wrap(codecH264.getPps().getByteData());
        }

        if ((mMediaCodec != null) && (mSpsBuffer != null)) {
            configureMediaCodec();
        }

        mReadyLock.unlock();
    }

    private void configureMediaCodec() {
        MediaFormat format = MediaFormat.createVideoFormat(Constants.VIDEO_MIME_TYPE, Constants.VIDEO_WIDTH, Constants.VIDEO_HEIGHT);   //  set video format
        format.setByteBuffer("csd-0", mSpsBuffer);
        format.setByteBuffer("csd-1", mPpsBuffer);

        mMediaCodec.configure(format, getHolder().getSurface(), null, 0);
        mMediaCodec.start();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mBuffers = mMediaCodec.getInputBuffers();
        }

        mIsCodecConfigured = true;
    }

    private void initMediaCodec(String type) {
        //  create decoder codec by type "video/avc"
        try {
            mMediaCodec = MediaCodec.createDecoderByType(type);
        } catch (IOException e) {
            Log.e(TAG, "Exception", e);
        }

        //  configure codec
        if ((mMediaCodec != null) && (mSpsBuffer != null)) {
            configureMediaCodec();
        }
    }

    private void releaseMediaCodec() {
        if (mMediaCodec != null) {
            if (mIsCodecConfigured) {
                mMediaCodec.stop();
                mMediaCodec.release();
            }
            mIsCodecConfigured = false;
            mMediaCodec = null;
        }
    }

    /**
     * Automatically called when SurfaceView be created
     * @param holder
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mReadyLock.lock();  //  for mutual exclusive
        initMediaCodec(Constants.VIDEO_MIME_TYPE);  //  "video/avc"
        mReadyLock.unlock();    //  for mutual exclusive
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        // DO NOTHING!
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mReadyLock.lock();  //  for mutual exclusive
        releaseMediaCodec();
        mReadyLock.unlock();    //  for mutual exclusive
    }
}

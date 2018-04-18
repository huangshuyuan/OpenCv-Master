package org.opencv.samples.facedetect;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class FdActivity extends Activity implements CvCameraViewListener2 {

    private static final String TAG             = "OCVSample::Activity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    public static final  int    JAVA_DETECTOR   = 0;
    public static final  int    NATIVE_DETECTOR = 1;

    private MenuItem mItemFace50;
    private MenuItem mItemFace40;
    private MenuItem mItemFace30;
    private MenuItem mItemFace20;
    private MenuItem mItemType;

    private Mat                   mRgba;
    private Mat                   mGray;
    private File                  mCascadeFile;
    private CascadeClassifier     mJavaDetector;
    private DetectionBasedTracker mNativeDetector;

    private int mDetectorType = JAVA_DETECTOR;
    private String[] mDetectorName;

    private float mRelativeFaceSize = 0.2f;
    private int   mAbsoluteFaceSize = 0;

    private JavaCameraView mOpenCvCameraView;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("detection_based_tracker");

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);
                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.enableView();
                }


                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public FdActivity() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";
        mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);

        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);

        //前置摄像头 CameraBridgeViewBase.CAMERA_ID_BACK为后置摄像头

    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    Mat Matlin, gMatlin;
    int absoluteFaceSize;

    public void onCameraViewStarted(int width, int height) {
        //        mGray = new Mat();
        //        mRgba = new Mat();

        mRgba = new Mat(width, height, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC4);
        Matlin = new Mat(width, height, CvType.CV_8UC4);
        gMatlin = new Mat(width, height, CvType.CV_8UC4);

        absoluteFaceSize = (int) (height * 0.2);
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    int faceSerialCount = 0;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame InputFrame) {
        mGray = InputFrame.gray();
        mRgba = InputFrame.rgba();
        int rotation = mOpenCvCameraView.getDisplay().getRotation();

        //使前置的图像也是正的
        Core.flip(mRgba, mRgba, 1);
        Core.flip(mGray, mGray, 1);

        //MatOfRect faces = new MatOfRect();

        if (rotation == Surface.ROTATION_0) {
            MatOfRect faces = new MatOfRect();
            Core.rotate(mGray, gMatlin, Core.ROTATE_90_CLOCKWISE);
            Core.rotate(mRgba, Matlin, Core.ROTATE_90_CLOCKWISE);
            if (mJavaDetector != null) {
                mJavaDetector.detectMultiScale(gMatlin, faces, 1.1, 2, 2, new Size(absoluteFaceSize, absoluteFaceSize), new Size());
            }

            Rect[] faceArray = faces.toArray();

            int faceCount = faceArray.length;
            if (faceCount > 0) {
                faceSerialCount++;
            } else {
                faceSerialCount = 0;
            }
            if (faceSerialCount > 5) {
                mOpenCvCameraView.takePhoto("sdcard/" + UUID.randomUUID() + ".jpg");
                faceSerialCount = -5000;
                Log.i("takephoto", "takephoto1");

            }
            for (int i = 0; i < faceArray.length; i++)

                Imgproc.rectangle(Matlin, faceArray[i].tl(), faceArray[i].br(), new Scalar(0, 255, 0, 255), 2);
            Core.rotate(Matlin, mRgba, Core.ROTATE_90_COUNTERCLOCKWISE);

        } else {
            MatOfRect faces = new MatOfRect();
            if (mJavaDetector != null)
                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

            Rect[] faceArray = faces.toArray();

            int faceCount = faceArray.length;
            if (faceCount > 0) {
                faceSerialCount++;
            } else {
                faceSerialCount = 0;
            }
            if (faceSerialCount > 5) {
                mOpenCvCameraView.takePhoto("sdcard/" + UUID.randomUUID() + ".jpg");
                faceSerialCount = -5000;
                Log.i("takephoto", "takephoto");
            }
            for (int i = 0; i < faceArray.length; i++)

                Imgproc.rectangle(mRgba, faceArray[i].tl(), faceArray[i].br(), new Scalar(0, 255, 0, 255), 2);
        }

        return mRgba;
    }


    //    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
    //
    //        mRgba = inputFrame.rgba();
    //        mGray = inputFrame.gray();
    //
    //        Core.flip(this.mRgba, this.mRgba, 1);  //flipCode>0将mRgbaF水平翻转（沿Y轴翻转）得到mRgba
    //
    //        if (mAbsoluteFaceSize == 0) {
    //            int height = mGray.rows();
    //            if (Math.round(height * mRelativeFaceSize) > 0) {
    //                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
    //            }
    //            mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
    //        }
    //
    //        MatOfRect faces = new MatOfRect();
    //
    //        if (mDetectorType == JAVA_DETECTOR) {
    //            if (mJavaDetector != null)
    //                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
    //                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
    //        } else if (mDetectorType == NATIVE_DETECTOR) {
    //            if (mNativeDetector != null)
    //                mNativeDetector.detect(mGray, faces);
    //        } else {
    //            Log.e(TAG, "Detection method is not selected!");
    //        }
    //
    //        Rect[] facesArray = faces.toArray();
    //
    //        int faceCount = facesArray.length;
    //        if (faceCount > 0) {
    //            faceSerialCount++;
    //        } else {
    //            faceSerialCount = 0;
    //        }
    //        if (faceSerialCount > 5) {
    //            mOpenCvCameraView.takePhoto("sdcard/" + UUID.randomUUID() + ".jpg");
    //            faceSerialCount = -5000;
    //            Log.i("takephoto", "takephoto");
    //        }
    //
    //
    //        for (int i = 0; i < facesArray.length; i++)
    //            Imgproc.rectangle(mRgba, facesArray[i].br(), facesArray[i].tl(), FACE_RECT_COLOR, 3);
    //
    //        Core.rotate(mRgba, mRgba, Core.ROTATE_90_COUNTERCLOCKWISE);
    //
    //        return mRgba;
    //    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        mItemType = menu.add(mDetectorName[mDetectorType]);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mItemFace50)
            setMinFaceSize(0.5f);
        else if (item == mItemFace40)
            setMinFaceSize(0.4f);
        else if (item == mItemFace30)
            setMinFaceSize(0.3f);
        else if (item == mItemFace20)
            setMinFaceSize(0.2f);
        else if (item == mItemType) {
            int tmpDetectorType = (mDetectorType + 1) % mDetectorName.length;
            item.setTitle(mDetectorName[tmpDetectorType]);
            setDetectorType(tmpDetectorType);
        }
        return true;
    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

    private void setDetectorType(int type) {
        if (mDetectorType != type) {
            mDetectorType = type;

            if (type == NATIVE_DETECTOR) {
                Log.i(TAG, "Detection Based Tracker enabled");
                mNativeDetector.start();
            } else {
                Log.i(TAG, "Cascade detector enabled");
                mNativeDetector.stop();
            }
        }
    }
}

package com.vividvr.vividvr_unity;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import at.jumpch.sdk.JCVideoCapture;
import at.jumpch.sdk.JCVideoCaptureCallback;
import imageProcessing.ColorBlobDetector;

/**
 * Created by abhipray on 8/27/16.
 */
public class VividUnityPlugin {

    private static final String    TAG                 = "VividUnityPlugin";
    public static final int        JAVA_DETECTOR       = 0;
    public static final int        NATIVE_DETECTOR     = 1;

    private final AtomicReference<Mat> mRgba = new AtomicReference<Mat>();

    private Mat 					mIntermediateMat;

    private int                    mDetectorType       = JAVA_DETECTOR;

    private CustomSufaceView mOpenCvCameraView;
    private List<Size> mResolutionList;


    double iThreshold = 0;

    private Scalar mBlobColorHsv;
    private Scalar               	mBlobColorRgba;
    private ColorBlobDetector mDetector;
    private Mat                  	mSpectrum;
    private boolean				mIsColorSelected = false;

    private Size                 	SPECTRUM_SIZE;
    private Scalar               	CONTOUR_COLOR;
    private Scalar               	CONTOUR_COLOR_WHITE;

    final Handler mHandler = new Handler();
    int numberOfFingers = 0;

    private Context mContext;

    JCVideoCapture videocap;
    JCVideoCaptureCallback videocallback = new JCVideoCaptureCallback() {
        @Override
        public void capturerStarted(boolean b) {
            Log.i(TAG, "captureStarted: " + Boolean.toString(b));
        }

        @Override
        public void provideFrame(byte[] bytes, int i, int i1, int i2) {
            Log.i(TAG, "provideFrameA: ");
        }

        @Override
        public void provideFrame(byte[] bytes, int i, int i1, int i2, long l, int i3) {
            Log.i(TAG, "provideFrameB: " + Long.toString(l));
            Mat mat = new Mat(i1+i1/2,i, CvType.CV_8UC1);
            mat.put(0, 0, bytes);
            onCameraFrame(mat);
        }
    };

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(mContext) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    // 640x480
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public VividUnityPlugin() {

    }

    public void init_plugin(Context aContext, int thresh) {
        mContext = aContext;
        iThreshold = thresh;
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, mContext, mLoaderCallback);
    }

    public int get_number_of_fingers() {
        return this.numberOfFingers;
    }

    private boolean is_capturing = false;
    public void start_capturing() {
        if(!is_capturing) {
            mRgba.set(new Mat());
            mIntermediateMat = new Mat();
            mRgba.set(new Mat(480, 480, CvType.CV_8UC4));
            mDetector = new ColorBlobDetector();
            mSpectrum = new Mat();
            mBlobColorRgba = new Scalar(255);
            mBlobColorHsv = new Scalar(255);
            SPECTRUM_SIZE = new Size(200, 64);
            CONTOUR_COLOR = new Scalar(255, 0, 0, 255);
            CONTOUR_COLOR_WHITE = new Scalar(255, 255, 255, 255);

            videocap = new JCVideoCapture();
            videocap.selectCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
            videocap.startCapture(480, 480, 8, mContext, videocallback);
            videocap.enableHD(false);
            is_capturing = true;
        }
    }

    public void stop_capturing() {
        videocap.stopCapture();
        is_capturing = false;
    }

    public void calibrate(int x, int y) {
        if(is_capturing) {
            Mat copy = mRgba.get().clone();
            int cols = copy.cols();
            int rows = copy.rows();

//        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
//        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

//        int x = (int)event.getX() - xOffset;
//        int y = (int)event.getY() - yOffset;

            Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

            if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return;

            Rect touchedRect = new Rect();

            touchedRect.x = (x > 5) ? x - 5 : 0;
            touchedRect.y = (y > 5) ? y - 5 : 0;

            touchedRect.width = (x + 5 < cols) ? x + 5 - touchedRect.x : cols - touchedRect.x;
            touchedRect.height = (y + 5 < rows) ? y + 5 - touchedRect.y : rows - touchedRect.y;

            Mat touchedRegionRgba = copy.submat(touchedRect);

            Mat touchedRegionHsv = new Mat();
            Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

            // Calculate average color of touched region
            mBlobColorHsv = Core.sumElems(touchedRegionHsv);
            int pointCount = touchedRect.width * touchedRect.height;
            for (int i = 0; i < mBlobColorHsv.val.length; i++)
                mBlobColorHsv.val[i] /= pointCount;

            mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

            Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                    ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

            mDetector.setHsvColor(mBlobColorHsv);

            Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

            mIsColorSelected = true;

            copy.release();
            touchedRegionRgba.release();
            touchedRegionHsv.release();
        }
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);
        pointMatHsv.release();
        return new Scalar(pointMatRgba.get(0, 0));
    }

    private void onCameraFrame(Mat yuv) {
        Imgproc.cvtColor(yuv, mRgba.get(), Imgproc.COLOR_YUV2RGBA_NV21, 4);
        yuv.release();

        //Imgproc.blur(mRgba, mRgba, new Size(5,5));
        Imgproc.GaussianBlur(mRgba.get(), mRgba.get(), new org.opencv.core.Size(3, 3), 1, 1);


        if (!mIsColorSelected) return;

        List<MatOfPoint> contours = mDetector.getContours();
        mDetector.process(mRgba.get());

        Log.d(TAG, "Contours count: " + contours.size());

        if (contours.size() <= 0) {
            return;
        }

        RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(0)	.toArray()));

        double boundWidth = rect.size.width;
        double boundHeight = rect.size.height;
        int boundPos = 0;

        for (int i = 1; i < contours.size(); i++) {
            rect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(i).toArray()));
            if (rect.size.width * rect.size.height > boundWidth * boundHeight) {
                boundWidth = rect.size.width;
                boundHeight = rect.size.height;
                boundPos = i;
            }
        }

        Rect boundRect = Imgproc.boundingRect(new MatOfPoint(contours.get(boundPos).toArray()));
        Imgproc.rectangle(mRgba.get(), boundRect.tl(), boundRect.br(), CONTOUR_COLOR_WHITE, 2, 8, 0 );

        Log.d(TAG,
                " Row start ["+
                        (int) boundRect.tl().y + "] row end ["+
                        (int) boundRect.br().y+"] Col start ["+
                        (int) boundRect.tl().x+"] Col end ["+
                        (int) boundRect.br().x+"]");

        int rectHeightThresh = 0;
        double a = boundRect.br().y - boundRect.tl().y;
        a = a * 0.7;
        a = boundRect.tl().y + a;

        Log.d(TAG,
                " A ["+a+"] br y - tl y = ["+(boundRect.br().y - boundRect.tl().y)+"]");

        //Core.rectangle( mRgba, boundRect.tl(), boundRect.br(), CONTOUR_COLOR, 2, 8, 0 );
        Imgproc.rectangle(mRgba.get(), boundRect.tl(), new Point(boundRect.br().x, a), CONTOUR_COLOR, 2, 8, 0 );

        MatOfPoint2f pointMat = new MatOfPoint2f();
        Imgproc.approxPolyDP(new MatOfPoint2f(contours.get(boundPos).toArray()), pointMat, 3, true);
        contours.set(boundPos, new MatOfPoint(pointMat.toArray()));

        MatOfInt hull = new MatOfInt();
        MatOfInt4 convexDefect = new MatOfInt4();
        Imgproc.convexHull(new MatOfPoint(contours.get(boundPos).toArray()), hull);

        if(hull.toArray().length < 3) return;

        Imgproc.convexityDefects(new MatOfPoint(contours.get(boundPos)	.toArray()), hull, convexDefect);

        List<MatOfPoint> hullPoints = new LinkedList<MatOfPoint>();
        List<Point> listPo = new LinkedList<Point>();
        for (int j = 0; j < hull.toList().size(); j++) {
            listPo.add(contours.get(boundPos).toList().get(hull.toList().get(j)));
        }

        MatOfPoint e = new MatOfPoint();
        e.fromList(listPo);
        hullPoints.add(e);

        List<MatOfPoint> defectPoints = new LinkedList<MatOfPoint>();
        List<Point> listPoDefect = new LinkedList<Point>();
        for (int j = 0; j < convexDefect.toList().size(); j = j+4) {
            Point farPoint = contours.get(boundPos).toList().get(convexDefect.toList().get(j+2));
            Integer depth = convexDefect.toList().get(j+3);
            if(depth > iThreshold && farPoint.y < a){
                listPoDefect.add(contours.get(boundPos).toList().get(convexDefect.toList().get(j+2)));
            }
            Log.d(TAG, "defects ["+j+"] " + convexDefect.toList().get(j+3));
        }

        MatOfPoint e2 = new MatOfPoint();
        e2.fromList(listPo);
        defectPoints.add(e2);

        Log.d(TAG, "hull: " + hull.toList());
        Log.d(TAG, "defects: " + convexDefect.toList());

        Imgproc.drawContours(mRgba.get(), hullPoints, -1, CONTOUR_COLOR, 3);

        int defectsTotal = (int) convexDefect.total();
        Log.d(TAG, "Defect total " + defectsTotal);

        this.numberOfFingers = listPoDefect.size();
        if(this.numberOfFingers > 5) this.numberOfFingers = 5;

//        mHandler.post(mUpdateFingerCountResults);
    }
}

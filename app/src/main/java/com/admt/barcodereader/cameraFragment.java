package com.admt.barcodereader;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.Type;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import static java.lang.Math.abs;
import static java.lang.Math.min;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link cameraFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 * This fragment is used to grab an image from the camera and
 * look for barcodes in it. If it finds a barcode, this is passed
 * back to the parent activity.
 */
public class cameraFragment extends Fragment
{

    CameraSource cameraBarcodeReader = null;
    final int cameraPermissionsRequestId = 1; // used in callback to identify the
                                        // corresponding request for a response.
    private static final String TAG = "ADMTBarcodeReaderCamera";

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundthread;
    private Handler mBackgroundImageHandler;
    private HandlerThread mBackgroundImageThread;


    private CaptureRequest.Builder mCaptureRequestBuilder;

    private BarcodeDetector mBarcodeDetector;
    private onBarcodeReadListener mBarcodeReadCallback;

    private int mSensorOrientation;
    private int mSelectedCameraFacing = 0;
    private Rect mActiveSensorArraySize = null;

    Size mMinimumSize = new Size(10,10);

    private boolean mStartingCamera = false;
    private boolean mPreviewSurfaceReady = false;
    private CameraDevice mCameraDevice = null;
    private CameraCaptureSession mCameraCaptureSession = null;
    private ImageReader mImageReader = null;
    private SurfaceTexture mDummyPreview = null;
    private Surface mDummySurface = null;


    private Bitmap mLatestBitmap = null;
    private Semaphore mLatestBitmapSemaphore = new Semaphore(1, true);

    private boolean torchOn = false;


    // YUV to RGBA to screen render stuff
    RenderScript rs;
    ScriptC_yuv420888toRGB mYuv420;
    //private Type.Builder ;
    private Allocation in, out;

    private BarcodeProcessor mBarcodeProcessor = null;

    private Size mSelectedSize = null;

    private static SparseIntArray orientations = new SparseIntArray();

    private boolean mFlashScreen = false;

    public interface onBarcodeReadListener
    {
        void onBarcodeRead(String barcodeValue);
    }

    /**
     * Small class used to process the barcodes detected by the  detector.
     */
    private class BarcodeProcessor
    {
        private long mDelayMillis = 1000; // before another code can be scanned
        private long mDelayTimeoutEndMillis = 0;

        public void receiveDetections(SparseArray<Barcode> detections)
        {
            // Send the first detected barcode to the containing class, then wait mDelayMillis
            // milliseconds. This prevents repeated/multiple detections in a short time, which
            // otherwise tend to makes the phone buzz like a hive of angry bees.
            if (detections.size() > 0)
            {
                long currentTimeMillis = SystemClock.elapsedRealtime();
                if(currentTimeMillis > mDelayTimeoutEndMillis)
                {
                    int key = detections.keyAt(0);
                    String barcodeNumber = detections.get(key).rawValue;
                    Log.d(TAG, String.format("Detected barcode %s", barcodeNumber));

                    mBarcodeReadCallback.onBarcodeRead(barcodeNumber);

                    mDelayTimeoutEndMillis = currentTimeMillis + mDelayMillis;
                }
            }
        }

        public void cancelDelay()
        {
            mDelayTimeoutEndMillis = SystemClock.elapsedRealtime();
        }

        public void release()
        {

        }
    }

    public void cancelBarcodeReadWait()
    {
        if(mBarcodeProcessor != null)
            mBarcodeProcessor.cancelDelay();
    }

    public void flashScreen()
    {
        mFlashScreen = true;
    }

    public void toggleTorch()
    {
        torchOn = !torchOn;

        if(torchOn)
        {
            mCaptureRequestBuilder.set(
                    CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        }
        else
        {
            mCaptureRequestBuilder.set(
                    CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }



        try {
            mCameraCaptureSession.stopRepeating();

            mCameraCaptureSession.setRepeatingRequest(
                    mCaptureRequestBuilder.build(),
                    cameraCaptureSessionCaptureCallback,
                    mBackgroundHandler
            );
        }
        catch (android.hardware.camera2.CameraAccessException ex)
        {
            Log.d(TAG, "setTorch: " + ex.getMessage());
        }
    }



    private void startCamera()
    {
        mStartingCamera = true;

        mBackgroundthread = new HandlerThread("camera background");
        mBackgroundthread.start();
        mBackgroundHandler = new Handler(mBackgroundthread.getLooper());

        mBackgroundImageThread = new HandlerThread("image processor background");
        mBackgroundImageThread.start();
        mBackgroundImageHandler = new Handler(mBackgroundImageThread.getLooper());

        SharedPreferences prefs = getActivity().getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        boolean useFrontCamera = prefs.getBoolean(
                getString(R.string.preferences_use_front_camera), true);
        
        int desiredCameraFacing = CameraCharacteristics.LENS_FACING_FRONT;
        if(useFrontCamera)
            Log.d(TAG, "Desired camera facing: front");
        else
        {
            desiredCameraFacing = CameraCharacteristics.LENS_FACING_BACK;
            Log.d(TAG, "Desired camera facing: back");
        }

        try
        {
            CameraManager cameraManager = getActivity().getSystemService(CameraManager.class);
            String[] cameraIDs = cameraManager.getCameraIdList();

            for(String cameraID : cameraIDs)
            {
                CameraCharacteristics cc = cameraManager.getCameraCharacteristics(cameraID);
                int lensFacing = cc.get(CameraCharacteristics.LENS_FACING);
                String lensFacingText =
                        (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) ? "front":"back";
                Log.d(TAG, "Camera " +
                        cameraID +
                        " facing: " +
                        lensFacingText);

                if(lensFacing == desiredCameraFacing)
                {
                    Log.d(TAG, "Selecting camera " + cameraID);

                    mSelectedCameraFacing = lensFacing; // used by imagereader

                    int sensorOrientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);

                    switch(sensorOrientation)
                    {
                        case 0:
                            orientations = new SparseIntArray();
                            orientations.append(Surface.ROTATION_0,     0);
                            orientations.append(Surface.ROTATION_90,    270);
                            orientations.append(Surface.ROTATION_180,   180);
                            orientations.append(Surface.ROTATION_270,   90);
                            break;

                        case 90:
                            orientations = new SparseIntArray();
                            orientations.append(Surface.ROTATION_0,     90);
                            orientations.append(Surface.ROTATION_90,    0);
                            orientations.append(Surface.ROTATION_180,   270);
                            orientations.append(Surface.ROTATION_270,   180);
                            break;

                        case 180:
                            orientations = new SparseIntArray();
                            orientations.append(Surface.ROTATION_0,     180);
                            orientations.append(Surface.ROTATION_90,    90);
                            orientations.append(Surface.ROTATION_180,   0);
                            orientations.append(Surface.ROTATION_270,   270);
                            break;

                        case 270:
                            orientations = new SparseIntArray();
                            orientations.append(Surface.ROTATION_0,     270);
                            orientations.append(Surface.ROTATION_90,    180);
                            orientations.append(Surface.ROTATION_180,   90);
                            orientations.append(Surface.ROTATION_270,   0);
                            break;
                    }


                    int cameraPermission = ContextCompat.checkSelfPermission(
                            getContext(), Manifest.permission.CAMERA);

                    // shouldn't ever be denied if the program has got to this point
                    if (cameraPermission == PackageManager.PERMISSION_GRANTED)
                    {
                        cameraManager.openCamera(cameraID, cameraDeviceStateCallback, mBackgroundHandler);

                        break;
                    }
                }
            }

            // At this point, access to the camera has been requested. The remainder of the set up
            // process will be completed once the camera itself has started up. The code for this
            // is located in the cameraDeviceStateCallback

        }
        catch (android.hardware.camera2.CameraAccessException ex)
        {
            Log.e(TAG, "Camera access exception: " + ex.getMessage());
        }
    }

    private boolean imageDimensionsSwapped(int displayRotation, int sensorOrientation)
    {
        switch(displayRotation)
        {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if(sensorOrientation == 90 || sensorOrientation == 270)
                    return true;
                break;

            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if(sensorOrientation == 0 || sensorOrientation == 180)
                    return true;
                break;

            default:
                Log.e(TAG, "Display rotation is invalid");
        }
        return false;
    }


    // Given a target preview frame size, find the smallest size that is at least as big as the
    // target frame size
    private Size findAppropriateFrameSize(
            Size targetSize, Size[] availableSizes, Size minimumSize)
    {
        Size bestMatch = availableSizes[0];
        for(Size size : availableSizes)
        {
            if(size.getHeight() >= targetSize.getHeight()
                    && size.getWidth() >= targetSize.getWidth()
                    && size.getHeight() >= minimumSize.getHeight()
                    && size.getWidth() >= minimumSize.getWidth()
                    )
            {
                if(size.getWidth() < bestMatch.getWidth() &&
                        size.getHeight() < bestMatch.getHeight())
                    bestMatch = size;
            }
        }

        String s = String.format("Target size: %dx%d, selected size: %dx%d",
                targetSize.getWidth(),
                targetSize.getHeight(),
                bestMatch.getWidth(),
                bestMatch.getHeight());
        Log.d(TAG, "findAppropriateFrameSize: " + s);

        return bestMatch;
    }

    CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice camera)
        {
            // find a useable frame size and create a request to the camera.
            try
            {
                String cameraID = camera.getId();
                mCameraDevice = camera;

                // Find out what ratios the camera supports, match one to the size of the preview.
                // if necessary, find the closest ratio, set it, and scale the display to match.
                // This is done here, rather than in startcamera(), because the size is part of the
                // capture request. The size of the camera output is also relevant to the
                // ImageReader class.
                CameraManager cameraManager = getActivity().getSystemService(CameraManager.class);
                CameraCharacteristics cameraCharacteristics =
                        cameraManager.getCameraCharacteristics(cameraID);

                StreamConfigurationMap map = cameraCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] availableSizes = map.getOutputSizes(ImageFormat.YUV_420_888);// todo review test for ImageFormat.RGB_565

                Log.d(TAG, "Available preview sizes:");
                for(Size size : availableSizes)
                {
                    String s = String.format("%dx%d,\t ratio (w/h): %f",
                            size.getWidth(),
                            size.getHeight(),
                            (float)size.getWidth()/size.getHeight());
                    Log.d(TAG, s);
                }

                SurfaceView cameraPreviewSurface = getActivity().findViewById(R.id.cameraPreview);
                int previewWidth = cameraPreviewSurface.getWidth();
                int previewHeight = cameraPreviewSurface.getHeight();
                Size targetSize = new Size(previewWidth,previewHeight);

                // TODO: FIX THIS?
                int displayRotation =
                        getActivity().getWindowManager().getDefaultDisplay().getRotation();
                mSensorOrientation = cameraCharacteristics.get(
                        CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions =
                        imageDimensionsSwapped(displayRotation, mSensorOrientation);

                if(swappedDimensions)
                {
                    int w = targetSize.getHeight();
                    int h = targetSize.getWidth();
                    targetSize = new Size(w, h);
                }

                Size selectedSize =
                        findAppropriateFrameSize(targetSize, availableSizes, mMinimumSize);
                mSelectedSize = selectedSize;

                // components used to process images
                mImageReader = ImageReader.newInstance(
                        selectedSize.getWidth(),
                        selectedSize.getHeight(),
                        ImageFormat.YUV_420_888,
                        20
                );// todo review test for ImageFormat.RGB_565

                mImageReader.setOnImageAvailableListener(
                        onImageAvailableListener, mBackgroundImageHandler);

                rs = RenderScript.create(getContext());
                mYuv420 = new ScriptC_yuv420888toRGB(rs);



                mDummyPreview = new SurfaceTexture(1);
                mDummySurface = new Surface(mDummyPreview);

                // create a capture session that outputs to the preview surface and the surface
                // used by mImageReader. The latter is ultimately passed to the barcode detector
                // to do the magic.
                ArrayList<Surface> outputSurfaces = new ArrayList<Surface>();
                outputSurfaces.add(mDummySurface);
                outputSurfaces.add(mImageReader.getSurface());

                mCaptureRequestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                for (Surface s : outputSurfaces)
                {
                    mCaptureRequestBuilder.addTarget(s);
                }

                int jpegRotation = orientations.get(displayRotation);
                mActiveSensorArraySize = cameraCharacteristics
                        .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

//                mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegRotation);


//                mCaptureRequestBuilder.set(
//                        CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
//                mCaptureRequestBuilder.set(
//                        CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_BARCODE);

                mCaptureRequestBuilder.set(
                        CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                mCaptureRequestBuilder.set(
                        CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);

                mCaptureRequestBuilder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        );

                mCaptureRequestBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON
                        );


                camera.createCaptureSession(
                        outputSurfaces, cameraCaptureSessionStateCallback, mBackgroundHandler);
            }
            catch (CameraAccessException ex)
            {
                Log.e(TAG, ex.getMessage());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera)
        {

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error)
        {

        }
    };

    CameraCaptureSession.StateCallback cameraCaptureSessionStateCallback =
            new CameraCaptureSession.StateCallback()
    {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session)
        {
            try
            {
                mCameraCaptureSession = session;
                session.setRepeatingRequest(
                        mCaptureRequestBuilder.build(),
                        cameraCaptureSessionCaptureCallback,
                        mBackgroundHandler
                );
            }
            catch (CameraAccessException ex)
            {
                Log.e(TAG, "onConfigured: " + ex.getMessage());
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session)
        {

        }
    };

    CameraCaptureSession.CaptureCallback cameraCaptureSessionCaptureCallback =
            new CameraCaptureSession.CaptureCallback()
            {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber)
                {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                    //Log.d(TAG, "onCaptureStarted: Start frame capture...");
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    //Log.d(TAG, "onCaptureCompleted: frame capture completed");

                    String stateLabel = "";
                    switch (result.get(CaptureResult.CONTROL_AF_STATE)) {
                        case CaptureRequest.CONTROL_AF_STATE_INACTIVE:
                            stateLabel = "INACTIVE";
                            break;

                        case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                            stateLabel = "PASSIVE SCAN";
                            break;

                        case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                            stateLabel = "PASSIVE FOCUSED";
                            break;

                        case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                            stateLabel = "ACTIVE SCAN";
                            break;

                        case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                            stateLabel = "FOCUS LOCKED";
                            break;

                        case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                            stateLabel = "NOT FOCUS LOCKED";
                            break;

                        case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                            stateLabel = "PASSIVE UNFOCUSED";
                            break;
                    }
                    //if(result.get(CaptureResult.CONTROL_AF_STATE) != CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED)
                    //Log.v(TAG, "autofocus: " + stateLabel);


                }
            };

    private class barcodeFinderRunnable implements Runnable
    {
        @Override
        public void run()
        {
            mBarcodeProcessor = new BarcodeProcessor();
            while(true)
            {
                try {
                    mLatestBitmapSemaphore.acquire();
                    if (mLatestBitmap == null) {
                        // nothing to do, so just release the semaphore
                        mLatestBitmapSemaphore.release();
                        //Log.d(TAG, "barcodeFinderRunnable: No image available");
                        Thread.sleep(200);
                    }
                    else
                    {
                        //Log.d(TAG, "barcodeFinderRunnable: Searching for barcode");
                        Frame frame = new Frame.Builder()
                                .setBitmap(mLatestBitmap)
                                .build();

                        mLatestBitmapSemaphore.release();

                        if (mBarcodeDetector.isOperational())
                            mBarcodeProcessor.receiveDetections(mBarcodeDetector.detect(frame));
                    }
                }
                catch (java.lang.InterruptedException ex)
                {
                    Log.d(TAG, "barcodeFinderRunnable: " + ex.getMessage());
                }
                catch (java.lang.NullPointerException ex)
                {
                    Log.d(TAG, "barcodeFinderRunnable: " + ex.getMessage());
                }
            }
        }
    }

    // borrowed this from the internet
    private Bitmap YUV_420_888_toRGB(Image image, int width, int height){
        // Get the three image planes
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] y = new byte[buffer.remaining()];
        buffer.get(y);

        buffer = planes[1].getBuffer();
        byte[] u = new byte[buffer.remaining()];
        buffer.get(u);

        buffer = planes[2].getBuffer();
        byte[] v = new byte[buffer.remaining()];
        buffer.get(v);

        // get the relevant RowStrides and PixelStrides
        // (we know from documentation that PixelStride is 1 for y)
        int yRowStride= planes[0].getRowStride();
        int uvRowStride= planes[1].getRowStride();  // we know from   documentation that RowStride is the same for u and v.
        int uvPixelStride= planes[1].getPixelStride();  // we know from   documentation that PixelStride is the same for u and v.




        // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
        // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
        Type.Builder typeUcharY = new Type.Builder(rs, Element.U8(rs));
        typeUcharY.setX(yRowStride).setY(height);
        Allocation yAlloc = Allocation.createTyped(rs, typeUcharY.create());
        yAlloc.copy1DRangeFrom(0, y.length, y);
        mYuv420.set_ypsIn(yAlloc);

        Type.Builder typeUcharUV = new Type.Builder(rs, Element.U8(rs));
        // note that the size of the u's and v's are as follows:
        //      (  (width/2)*PixelStride + padding  ) * (height/2)
        // =    (RowStride                          ) * (height/2)
        // but I noted that on the S7 it is 1 less...
        typeUcharUV.setX(u.length);
        Allocation uAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        uAlloc.copyFrom(u);
        mYuv420.set_uIn(uAlloc);

        Allocation vAlloc = Allocation.createTyped(rs, typeUcharUV.create());
        vAlloc.copyFrom(v);
        mYuv420.set_vIn(vAlloc);

        // handover parameters
        mYuv420.set_picWidth(width);
        mYuv420.set_uvRowStride (uvRowStride);
        mYuv420.set_uvPixelStride (uvPixelStride);

        Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Allocation outAlloc = Allocation.createFromBitmap(rs, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);

        Script.LaunchOptions lo = new Script.LaunchOptions();
        lo.setX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
        lo.setY(0, height);

        mYuv420.forEach_doConvert(outAlloc,lo);
        outAlloc.copyTo(outBitmap);

        return outBitmap;
    }

    ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener()
    {
        @Override
        public void onImageAvailable(ImageReader reader)
        {
            //Log.d(TAG, "onImageAvailable: Start process image");
            Bitmap imageBitmap = null;
            Image image = reader.acquireLatestImage();

            //Image image = reader.acquireNextImage();
            int imageWidth, imageHeight;

            // The image sometimes comes back null. Not sure why, but may be do with the GC running
            if (image == null)
            {
                //Log.d(TAG, "onImageAvailable: Skipping null image");
                return;
            }

            try
            {
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
                imageBitmap = YUV_420_888_toRGB(image, imageWidth, imageHeight);
                image.close();

                int imageBitmapHeight = imageBitmap.getHeight();
                // if image needs rotating, do so by creating a new bitmap at the correct rotation
                if (imageBitmap.getHeight() == mSelectedSize.getHeight())
                {

                }

                SurfaceView surfaceView = (SurfaceView) getActivity().findViewById(R.id.cameraPreview);
                Surface surface = surfaceView.getHolder().getSurface();
                Canvas canvas = surface.lockHardwareCanvas();

                int canvasWidth = canvas.getWidth();
                int canvasHeight = canvas.getHeight();


                float widthRatio, heightRatio, scale_ratio;
                int cropLeft, cropTop, cropWidth, cropHeight;
                Matrix scaleMatrix = new Matrix();

                // deal with the image being dimensions being backwards in some device orientations,
                // due to the orientation of the sensor relative to the device.
                int displayRotation =
                        getActivity().getWindowManager().getDefaultDisplay().getRotation();
                boolean swappedDimensions =
                        imageDimensionsSwapped(displayRotation, mSensorOrientation);

                if (swappedDimensions)
                {
                    scaleMatrix.preScale(1,1);
                    scaleMatrix.setRotate((float) mSensorOrientation);

                    int tmp = canvasWidth;
                    canvasWidth = canvasHeight;
                    canvasHeight = tmp;
                }

                // find the smaller ratio between the width/height of the canvas and of the image.
                // Use this to find the portion of the image in the dimension with the larger ratio
                // that can be used. The shape of the cropped image is determined by the shape of the
                // canvas

                float canvas_ratio = (float) canvasWidth/canvasHeight;
                widthRatio = (float) imageWidth / canvasWidth;
                heightRatio = (float) imageHeight / canvasHeight;

                if (widthRatio < heightRatio){
                    cropHeight = Math.round(imageWidth / canvas_ratio);
                    cropWidth = imageWidth;
                    if(cropHeight> imageHeight)
                        cropHeight = imageHeight;
                    cropTop = (int) Math.round(((float) imageHeight-cropHeight)/2);
                    cropLeft = 0;
                    scale_ratio = (float) canvasWidth/imageWidth;
                }else{
                    cropHeight = imageHeight;
                    cropWidth = Math.round(canvas_ratio * imageHeight);
                    if(cropWidth> imageWidth)
                        cropWidth = imageWidth;
                    cropLeft = (int) Math.round(((float) imageWidth-cropWidth)/2) ;
                    cropTop = 0 ;
                    scale_ratio = (float) canvasHeight/imageHeight;
                }

                if (cropTop < 0 || cropLeft < 0){
                    cropTop = 0 ;
                    cropLeft = 0;
                }

                if (mSelectedCameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {

                    scaleMatrix.postScale(-scale_ratio, scale_ratio);
                }else {

                    scaleMatrix.postScale(scale_ratio, scale_ratio);
                }

//                cropTop = 256;
//                cropLeft = 0;
//                cropWidth = 960;
//                cropHeight = 682;
//                int displayTop = 0;
//                int displayLeft = 60;

                Bitmap croppedBitmap = Bitmap.createBitmap(
                        imageBitmap, cropLeft, cropTop, cropWidth, cropHeight, scaleMatrix, false);

                try
                {
                    mLatestBitmapSemaphore.acquire();
                    mLatestBitmap = croppedBitmap;
                    mLatestBitmapSemaphore.release();
                }
                catch (java.lang.InterruptedException ex)
                {
                    Log.d(TAG, "onImageAvailable: " + ex.getMessage());
                }



                if(!mFlashScreen)
                    canvas.drawBitmap(croppedBitmap, 0, 0, null);
                else
                {
                    mFlashScreen = false;
                    canvas.drawRGB(255,255,255);
                }

                surface.unlockCanvasAndPost(canvas);
//                Log.d(TAG, "onImageAvailable: End process image");

            }
            catch (java.lang.IllegalStateException ex)
            {
                Log.d(TAG, "onImageAvailable: " + ex.getMessage());
                return;
            }
        }
    };

    private void stopCamera()
    {
        if(mCameraCaptureSession != null)
        {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if(mCameraDevice != null)
        {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if(mImageReader != null)
        {
            mImageReader.close();
            mImageReader = null;
        }

        if(mDummyPreview != null)
        {
            mDummyPreview = null;
        }

        if(mDummySurface != null)
        {
            mDummySurface = null;
        }

        mStartingCamera = false;
    }


    SurfaceHolder.Callback cameraPreviewSurfaceCallback = new SurfaceHolder.Callback()
    {

        @Override
        public void surfaceCreated(SurfaceHolder holder)
        {
            // (re)create camera with the appropriate preview resolution
//            if (cameraBarcodeReader != null)
//                cameraBarcodeReader.release();

            mBarcodeDetector = new BarcodeDetector.Builder(getContext())
                    .setBarcodeFormats(Barcode.ALL_FORMATS)// TODO Allow selection of code types in settings
                    .build();

            if (mBarcodeDetector.isOperational())
            {
                Log.d(TAG, "Barcode detector operational");
                barcodeFinderRunnable barcodeFinder = new barcodeFinderRunnable();
                Thread t = new Thread(barcodeFinder, "barcodeFinderThread");
                t.start();
            }
            else
            {
                Log.d(TAG, "Barcode detector inoperational");
                Toast.makeText(getContext(),
                        "Barcode detector inoperational",
                        Toast.LENGTH_SHORT).show();
            }

//            if(mBarcodeProcessor == null)
//                mBarcodeProcessor = new BarcodeProcessor();

            Context context = getActivity();

            int cameraPermission = ContextCompat.checkSelfPermission(
                    getContext(), Manifest.permission.CAMERA);


            if(!mStartingCamera)
            {
                if (cameraPermission == PackageManager.PERMISSION_GRANTED)
                {
                    startCamera();
                }
                else if (cameraPermission == PackageManager.PERMISSION_DENIED)
                {
                    requestPermissions(new String[]{Manifest.permission.CAMERA},
                            cameraPermissionsRequestId);

                    // camera is started in the response handler
                }
            }



            mPreviewSurfaceReady = true;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
        {}

        public void surfaceDestroyed (SurfaceHolder holder)
        {}
    };

    public cameraFragment()
    {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment cameraFragment.
     */
    public static cameraFragment newInstance()
    {
        cameraFragment fragment = new cameraFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        MainActivity mainActivity = (MainActivity)getActivity();
        mBarcodeReadCallback = (onBarcodeReadListener)mainActivity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onStart()
    {
        super.onStart();

        SurfaceView cameraPreviewSurface = (SurfaceView)getActivity()
                .findViewById(R.id.cameraPreview);

        cameraPreviewSurface.getHolder().addCallback(cameraPreviewSurfaceCallback);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // The camera may already be in the process of being started. If this is the case,
        // do nothing. Otherwise, attempt to start the camera. The camera can only be started
        // once the preview surface is ready. The callback for the creation of the surface
        // is where starting the camera is usually handled. The call to start it here applies to
        // the condition where the preview surface already exists, such as when the user navigates
        // back to the activity containing this fragment.

        if(mPreviewSurfaceReady && !mStartingCamera)
        {
            int cameraPermission = ContextCompat.checkSelfPermission(
                    getContext(), Manifest.permission.CAMERA);

            if (cameraPermission == PackageManager.PERMISSION_GRANTED)
            {
                startCamera();
            }
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        stopCamera();
    }


    @Override
    public void onStop()
    {
        super.onStop();
        stopCamera();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        //if(cameraBarcodeReader != null)
        //    cameraBarcodeReader.release();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch(requestCode)
        {
            case cameraPermissionsRequestId:
            {
                if(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && cameraBarcodeReader != null
                        && !mStartingCamera)
                {
                    startCamera();
                }
            }
        }
    }
}

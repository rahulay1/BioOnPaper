package com.lauszus.bioonpaper;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.lauszus.bioonpaper.jama.Matrix;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public class VerifyActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{
    private static final int PERMISSIONS_REQUEST_CODE = 0;
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private Toast mToast;
    private SharedPreferences prefs;
    private TinyDB tinydb;
    private Toolbar mToolbar;
    private NativeMethods.TrainFacesTask mTrainFacesTask;
    private ImageView imageView;

    private int DIM_IMG_SIZE_X = 160;//299;
    private int DIM_IMG_SIZE_Y = 160;//299;
    private int DIM_PIXEL_SIZE = 3;
    Bitmap selected_image;

    private int[] intValues;

    private ByteBuffer imgData = null;

    // options for model interpreter
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    // tflite graph
    private Interpreter tflite;
    private byte[][] labelProbArrayB = null;
    private Handler mainHandler = new Handler();
    String[] integerStrings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar); // Sets the Toolbar to act as the ActionBar for this Activity window

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();


        prefs = PreferenceManager.getDefaultSharedPreferences(this);


        tinydb = new TinyDB(this); // Used to store ArrayLists in the shared preferences

        final GestureDetector mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Show flip animation when the camera is flipped due to a double tap
                flipCameraAnimation();
                return true;
            }
        });

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_java_surface_view);
        mOpenCvCameraView.setCameraIndex(prefs.getInt("mCameraIndex", CameraBridgeViewBase.CAMERA_ID_FRONT));
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });

        String qrcode = getIntent().getStringExtra("qrcode");
        integerStrings = qrcode.split(" ");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadOpenCV();
                } else {
                    showToast("Permission required!", Toast.LENGTH_LONG);
                    finish();
                }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Store threshold values
        SharedPreferences.Editor editor = prefs.edit();
        editor.apply();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Request permission if needed
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED/* || ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED*/)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA/*, Manifest.permission.WRITE_EXTERNAL_STORAGE*/}, PERMISSIONS_REQUEST_CODE);
        else
            loadOpenCV();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    NativeMethods.loadNativeLibraries(); // Load native libraries after(!) OpenCV initialization
                    //Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();

                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    private void loadOpenCV() {
        if (!OpenCVLoader.initDebug(true)) {
            //Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
        } else {
            //Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat mRgbaTmp = inputFrame.rgba();

        // Flip image to get mirror effect
        int orientation = mOpenCvCameraView.getScreenOrientation();
        if (mOpenCvCameraView.isEmulator()) // Treat emulators as a special case
            Core.flip(mRgbaTmp, mRgbaTmp, 1); // Flip along y-axis
        else {
            switch (orientation) { // RGB image
                case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                    if (mOpenCvCameraView.mCameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK)
                        Core.rotate(mRgbaTmp, mRgbaTmp, Core.ROTATE_180); // Flip along y-axis
                    //Core.flip(mRgbaTmp, mRgbaTmp, 1); // Flip along y-axis
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                    if (mOpenCvCameraView.mCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT)
                        Core.flip(mRgbaTmp, mRgbaTmp, 0); // Flip along x-axis
                    else
                        Core.flip(mRgbaTmp, mRgbaTmp, -1); // Flip along both axis
                    break;
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    if (mOpenCvCameraView.mCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT)
                        Core.flip(mRgbaTmp, mRgbaTmp, 1); // Flip along y-axis
                    break;
            }
        }
        mRgba = mRgbaTmp;

        return mRgba;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START))
            drawer.closeDrawer(GravityCompat.START);
        else
            super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_face_recognition_app, menu);
        // Show rear camera icon if front camera is currently used and front camera icon if back camera is used
        MenuItem menuItem = menu.findItem(R.id.flip_camera);
        if (mOpenCvCameraView.mCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT)
            menuItem.setIcon(R.drawable.ic_camera_front_white_24dp);
        else
            menuItem.setIcon(R.drawable.ic_camera_rear_white_24dp);
        return true;
    }

    private void flipCameraAnimation() {
        // Flip the camera
        mOpenCvCameraView.flipCamera();

        // Do flip camera animation
        View v = mToolbar.findViewById(R.id.flip_camera);
        ObjectAnimator animator = ObjectAnimator.ofFloat(v, "rotationY", v.getRotationY() +360);
        animator.setDuration(500);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                supportInvalidateOptionsMenu(); // This will call onCreateOptionsMenu()
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animator.start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.flip_camera:
                flipCameraAnimation();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    class VerifyThread extends Thread{
        @Override
        public void run() {

            reSizeImage();

            try{
                tflite = new Interpreter(loadModelFile(), tfliteOptions);
            } catch (Exception ex){
                ex.printStackTrace();
            }
            imgData = ByteBuffer.allocateDirect(DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
            imgData.order(ByteOrder.nativeOrder());
            labelProbArrayB= new byte[1][512];

            // convert bitmap to byte array
            convertBitmapToByteBuffer(selected_image);
            // pass byte data to the graph
            tflite.run(imgData, labelProbArrayB);
            final float distance =KNN.getDistance(integerStrings, labelProbArrayB[0]);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    showToast("FACE IS " +distance, Toast.LENGTH_LONG);//+result
                }
            });
            if (distance <= 400000){
                Intent myIntent =new Intent(getBaseContext(),   Accept.class);
                startActivity(myIntent);
            }else{
                Intent myIntent =new Intent(getBaseContext(),   Reject.class);
                startActivity(myIntent);
            }

            return;
        }
    }

    private void reSizeImage() {
        final Bitmap bitmap=Bitmap.createBitmap(mRgba.width(),mRgba.height(), Bitmap.Config.ARGB_8888);;
        Utils.matToBitmap(mRgba, bitmap);
        Matrix matrix = new Matrix(bitmap.getWidth(),bitmap.getHeight());
        matrix.postRotate(270);
        selected_image =Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);


        faceDetect(selected_image);
        selected_image=getResizedBitmap(selected_image,DIM_IMG_SIZE_X, DIM_IMG_SIZE_X);

        imageView=findViewById(R.id.imageView1);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(selected_image);
            }
        });
    }

    public void verify(View view) {
        VerifyThread thread = new VerifyThread();
        thread.start();
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("my_facenet_512.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // resizes bitmap to given dimensions
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }

    // converts bitmap to byte array which is passed in the tflite graph
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // loop through all pixels
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                // get rgb values from intValues where each int holds the rgb values for a pixel.
                // if quantized, convert each rgb value to a byte, otherwise to a float

                imgData.put((byte) ((val >> 16) & 0xFF));
                imgData.put((byte) ((val >> 8) & 0xFF));
                imgData.put((byte) (val & 0xFF));
            }
        }
    }

    private Bitmap faceDetect(Bitmap photo) {
        FaceDetector faceDetector = new  FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .build();

        if(!faceDetector.isOperational()){
            //Toast.makeText(this,"Face Detector Could not setup", Toast.LENGTH_SHORT).show();
            //return;
        }

        Frame frame = new Frame.Builder().setBitmap(photo).build();
        SparseArray<Face> sparseArray =faceDetector.detect(frame);

        for (int i =0; i<sparseArray.size();i++) {
            Face face = sparseArray.valueAt(i);
            int x1 = (int) face.getPosition().x;
            int y1 = (int) face.getPosition().y;

            final Bitmap photoCropped = Bitmap.createBitmap( (int) face.getWidth(), (int) face.getHeight(),Bitmap.Config.ARGB_8888);                    ;

            for (int x = 0; x < face.getWidth()-1; x++) {
                for (int y = 0; y < face.getHeight()-1; y++) {
                    int ix = x1+x;
                    int iy = y1+y;
//                    int ix = (x + x1)-100;
//                    int iy = (y + y1)-100;
                    photoCropped.setPixel( x, y,photo.getPixel(ix, iy));
                }
            }
            imageView=findViewById(R.id.imageView1);
//            mainHandler.post(new Runnable() {
//                @Override
//                public void run() {
//                    imageView.setImageBitmap(photoCropped);
//                }
//            });
            selected_image=photoCropped;
        }

        return selected_image;
    }


    private void showToast(String message, int duration) {
        if (duration != Toast.LENGTH_SHORT && duration != Toast.LENGTH_LONG)
            throw new IllegalArgumentException();
        if (mToast != null && mToast.getView().isShown())
            mToast.cancel(); // Close the toast if it is already open
        mToast = Toast.makeText(this, message, duration);
        mToast.show();
    }

    private Matrix matToMatrix(Mat A, int row){
        Matrix B = new Matrix(row,1);
        for (int r=0;  r<row;r++){
            double [] imagevalue =A.get(r,0);
            B.set(r,0, imagevalue[0]);
            //Log.i(TAG, "RAHUL : "  +B.get(r,c));
        }
        return B;
    }
}

package com.example.qiangwang.surfaceviewtest;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener{
    private static final String TAG = "SurfaceviewTest";
    private final String SERVER_IP = "http://192.168.1.100";
    private final String car= SERVER_IP+"/car.mp4";
    private final String mp4= SERVER_IP+"/video.mp4";
    private final String hevc= SERVER_IP+"/4k.mpg";

    private static final File FILES_DIR = Environment.getExternalStorageDirectory();

    private static int saveNumber = 0;
    private static int maxStoreNum = 5;

    FFmpegMediaMetadataRetriever mmr;
    Thread worker;
    MediaPlayer player1;
    private SurfaceView sv1;
    private Surface surface;
    private Button btnTest;
    private Button btnStart;
    private ImageView iv;

    TextureView tv;

    AssetFileDescriptor fileDescriptor;

    public void LOGI(String msg){
        Log.i(TAG, msg);
    }
    public void LOGE(String msg){
        Log.e(TAG, msg);
    }


    public void capture1(View v) {

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US);
        String fname = "/sdcard/"+sdf.format(new Date())+ ".png";

        View view = v.getRootView();
        view.setDrawingCacheEnabled(true);
        view.buildDrawingCache();
        Bitmap bitmap = view.getDrawingCache();
        if(bitmap!= null)
        {
            System.out.println("bitmap got!");
            try{
                FileOutputStream out = new FileOutputStream(fname);
                bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
                System.out.println("file" + fname + "output done.");
            }catch(Exception e) {
                e.printStackTrace();
            }

        }else{
            System.out.println("bitmap is NULL!");
        }
    }


    private void storeToSDCard(Bitmap bitmap){
        try {
            String fname = "/sdcard/baustem/abcd.png";
            FileOutputStream out = new FileOutputStream(fname);
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void getframeTest(){
        LOGE("getFrameTest()");
        try {

            int pos = player1.getCurrentPosition();

            DateFormat df = new SimpleDateFormat("HH:mm:ss");

            LOGE("before getframe:time:"+df.format(new Date()));
            Bitmap bitmap = getFrame_mmr(pos);
            LOGE("after getframe:time:"+df.format(new Date()));


            iv.setImageBitmap(bitmap);
            iv.setVisibility(View.VISIBLE);
//            storeToSDCard(bitmap);

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private void startVideo(){
        LOGE("startVideo()");

        mmr = new FFmpegMediaMetadataRetriever();
        mmr.setDataSource(car);

        mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ALBUM);
        mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ARTIST);
        new Thread(new Runnable() {
            @Override
            public void run() {
                LOGE(" mmr.getFrameAtTime(100*1000*1000);");
                mmr.getFrameAtTime(100*1000*1000);
            }
        }).start();
//        mmr.getFrameAtTime(1*1000*1000);
//        mmr.getFrameAtTime();



        player1 = new MediaPlayer();
        player1.reset();
        player1.setLooping(true);

        player1.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player1.setDisplay(sv1.getHolder());

        try {

            player1.setDataSource(car);

            player1.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    // TODO Auto-generated method stub
                    LOGE("onPrepared()..............");
                    mp.start();
                }
            });

            player1.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveFrame() throws IOException {
        // glReadPixels gives us a ByteBuffer filled with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  To use the Bitmap
        // constructor that takes an int[] array with pixel data, we need an int[] filled
        // with little-endian ARGB data.
        //
        // If we implement this as a series of buf.get() calls, we can spend 2.5 seconds just
        // copying data around for a 720p frame.  It's better to do a bulk get() and then
        // rearrange the data in memory.  (For comparison, the PNG compress takes about 500ms
        // for a trivial frame.)
        //
        // So... we set the ByteBuffer to little-endian, which should turn the bulk IntBuffer
        // get() into a straight memcpy on most Android devices.  Our ints will hold ABGR data.
        // Swapping B and R gives us ARGB.  We need about 30ms for the bulk get(), and another
        // 270ms for the color swap.
        //
        // We can avoid the costly B/R swap here if we do it in the fragment shader (see
        // http://stackoverflow.com/questions/21634450/ ).
        //
        // Having said all that... it turns out that the Bitmap#copyPixelsFromBuffer()
        // method wants RGBA pixels, not ARGB, so if we create an empty bitmap and then
        // copy pixel data in we can avoid the swap issue entirely, and just copy straight
        // into the Bitmap from the ByteBuffer.
        //
        // Making this even more interesting is the upside-down nature of GL, which means
        // our output will look upside-down relative to what appears on screen if the
        // typical GL conventions are used.  (For ExtractMpegFrameTest, we avoid the issue
        // by inverting the frame when we render it.)
        //
        // Allocating large buffers is expensive, so we really want mPixelBuf to be
        // allocated ahead of time if possible.  We still get some allocations from the
        // Bitmap / PNG creation.

//        mPixelBuf.rewind();
//        GLES20.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
//                mPixelBuf);


        File outputFile = new File(FILES_DIR,
                String.format("frame-%02d.png", saveNumber));

        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(outputFile.toString()));
            Bitmap bmp = tv.getBitmap();
//            mPixelBuf.rewind();
//            bmp.copyPixelsFromBuffer(mPixelBuf);
//                ((MainActivity)MainActivity.getContext()).RenderVideoBytesFrame(bmp,mPixelBuf,new Rect(0,0,1080,720),new Rect(0,0,mWidth,mHeight));
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
            bmp.recycle();
            saveNumber++;
        } finally {
            if (bos != null) bos.close();
        }
//        if (VERBOSE) {
//            Log.d(TAG, "Saved " + mWidth + "x" + mHeight + " frame as '" + filename + "'");
//        }
    }


    public void startPlayer(String url){
        if(player1 != null){
            Bitmap bitmap = tv.getBitmap();
            iv.setImageBitmap(bitmap);

            iv.setVisibility(View.VISIBLE);

            try {
                saveFrame();
            } catch (IOException e) {
                e.printStackTrace();
            }

            player1.reset();
            return;
        }
        player1 = new MediaPlayer();
        player1.setLooping(true);

        player1.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player1.setSurface(surface);
//        player1.setDisplay(sv1.getHolder());
        try {
//                fileDescriptor = getAssets().openFd("video.mp4");
//                player.setDataSource(fileDescriptor.getFileDescriptor(),fileDescriptor.getStartOffset(),fileDescriptor.getLength());

            player1.setDataSource(url);
            player1.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    // TODO Auto-generated method stub
                    LOGE("onPrepared()..............");
                    mp.start();
                    iv.setVisibility(View.INVISIBLE);
                }
            });
            player1.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void surfaceViewSetup(){
//        sv1 = (SurfaceView)findViewById(R.id.sv);
//        SurfaceHolder holder1 = sv1.getHolder();
//        holder1.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//        holder1.addCallback(new DummySurfaceHolderCallback());
    }

    void textureViewSetup(){
        tv = (TextureView)findViewById(R.id.tv);
        tv.setSurfaceTextureListener(this);
    }

    private static int counter = 0;

    private void toggleVideo(){
        if(counter++ %2 == 0){
            startPlayer(mp4);
        }else{
            startPlayer(hevc);
        }
    }

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iv = (ImageView)findViewById(R.id.iv);
//        iv.setVisibility(View.INVISIBLE);

        btnTest = (Button)findViewById(R.id.btn_test);
        btnStart = (Button)findViewById(R.id.btn_start);

        btnTest.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                toggleVideo();
            }
        });
        btnStart.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
            }
        });
        textureViewSetup();
//        surfaceViewSetup();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
//  tv.requestFocus();
//  webview.loadUrl(SERVER_IP);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        LOGE("onSurfaceTextureAvailable()");
        this.surface = new Surface(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    class DummySurfaceHolderCallback implements  SurfaceHolder.Callback{
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // TODO Auto-generated method stub
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // TODO Auto-generated method stub
            LOGE("surfaceCreated()");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO Auto-generated method stub

        }
    }

    class MySurfaceHolderCallback implements SurfaceHolder.Callback{

        private String url;
        MySurfaceHolderCallback(String url){
            this.url = url;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // TODO Auto-generated method stub
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // TODO Auto-generated method stub
            LOGE("surfaceCreated()");

            player1 = new MediaPlayer();
            player1.reset();
            player1.setLooping(true);

            player1.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player1.setDisplay(holder);

            try {
//                fileDescriptor = getAssets().openFd("video.mp4");
//                player.setDataSource(fileDescriptor.getFileDescriptor(),fileDescriptor.getStartOffset(),fileDescriptor.getLength());

             player1.setDataSource(url);
//    player.prepare();
//    player.start();

                player1.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        // TODO Auto-generated method stub
                        LOGE("onPrepared()..............");
                        mp.start();
                    }
                });
                player1.prepareAsync();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO Auto-generated method stub

        }
    }





//public class ScreenShot {
//  private BufferedImage image = null;
//  /**
//   * @param args
//   */
//  public static void main(String[] args) {
//   // TODO Auto-generated method stub
//   AndroidDebugBridge.init(false); //
//   ScreenShot screenshot = new ScreenShot();
//   IDevice device = screenshot.getDevice();
//
//   for (int i = 0; i < 10; i++) {
//    Date date=new Date();
//    SimpleDateFormat df=new SimpleDateFormat("MM-dd-HH-mm-ss");
//    String nowTime = df.format(date);
//    screenshot.getScreenShot(device, "Robotium" + nowTime);
//    try {
//     Thread.sleep(1000);
//    } catch (InterruptedException e) {
//     // TODO Auto-generated catch block
//     e.printStackTrace();
//    }
//   }
//  }
//
//
//  public void getScreenShot(IDevice device,String filename) {
//   RawImage rawScreen = null;
//   try {
//    rawScreen = device.getScreenshot();
//   } catch (TimeoutException e) {
//    // TODO Auto-generated catch block
//    e.printStackTrace();
//   } catch (AdbCommandRejectedException e) {
//    // TODO Auto-generated catch block
//    e.printStackTrace();
//   } catch (IOException e) {
//    // TODO Auto-generated catch block
//    e.printStackTrace();
//   }
//   if (rawScreen != null) {
//    Boolean landscape = false;
//    int width2 = landscape ? rawScreen.height : rawScreen.width;
//    int height2 = landscape ? rawScreen.width : rawScreen.height;
//    if (image == null) {
//     image = new BufferedImage(width2, height2,
//       BufferedImage.TYPE_INT_RGB);
//    } else {
//     if (image.getHeight() != height2 || image.getWidth() != width2) {
//      image = new BufferedImage(width2, height2,
//        BufferedImage.TYPE_INT_RGB);
//     }
//    }
//    int index = 0;
//    int indexInc = rawScreen.bpp >> 3;
//    for (int y = 0; y < rawScreen.height; y++) {
//     for (int x = 0; x < rawScreen.width; x++, index += indexInc) {
//      int value = rawScreen.getARGB(index);
//      if (landscape)
//       image.setRGB(y, rawScreen.width - x - 1, value);
//      else
//       image.setRGB(x, y, value);
//     }
//    }
//    try {
//     ImageIO.write((RenderedImage) image, "PNG", new File("D:/"
//       + filename + ".jpg"));
//    } catch (IOException e) {
//     // TODO Auto-generated catch block
//     e.printStackTrace();
//    }
//   }
//  }
//
//  /**
//   * 获取得到device对象
//   * @return
//   */
//  private IDevice getDevice(){
//   IDevice device;
//   AndroidDebugBridge bridge = AndroidDebugBridge
//     .createBridge("adb", true);//如果代码有问题请查看API，修改此处的参数值试一下
//   waitDevicesList(bridge);
//   IDevice devices[] = bridge.getDevices();
//   device = devices[0];
//   return device;
//  }
//
//  /**
//   * 等待查找device
//   * @param bridge
//   */
//  private void waitDevicesList(AndroidDebugBridge bridge) {
//   int count = 0;
//   while (bridge.hasInitialDeviceList() == false) {
//    try {
//     Thread.sleep(500);
//     count++;
//    } catch (InterruptedException e) {
//    }
//    if (count > 240) {
//     System.err.print("等待获取设备超时");
//     break;
//    }
//   }
//  }
//
//}




    public static Bitmap getVideoFrame(FileDescriptor FD) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(FD);

            String s = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);

//        return retriever.getFrameAtTime();
            return retriever.getFrameAtTime(-1 ,MediaMetadataRetriever.OPTION_PREVIOUS_SYNC);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
            }
        }
        return null;
    }




    void foo(){
        Process process = null;
        try{
            process = Runtime.getRuntime().exec("su");
            PrintStream outputStream = null;
            try {
                outputStream = new PrintStream(new BufferedOutputStream(process.getOutputStream(), 8192));
                outputStream.println("screencap -p /sdcard/abc.png");
                outputStream.flush();
            }catch(Exception e){
                e.printStackTrace();
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
            process.waitFor();
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            if(process != null){
                process.destroy();
            }
        }
    }

    private Bitmap getFrame_mmr(int milliseconds){
        LOGE("getFrame_mmr(),milliseconds="+milliseconds);
//        FFmpegMediaMetadataRetriever mmr = new FFmpegMediaMetadataRetriever();
//        mmr.setDataSource(car);
//        mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ALBUM);
//        mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ARTIST);
//        Bitmap b = mmr.getFrameAtTime(1, FFmpegMediaMetadataRetriever.OPTION_CLOSEST); // frame at 2 seconds
        Bitmap b = mmr.getFrameAtTime(milliseconds*1000, FFmpegMediaMetadataRetriever.OPTION_CLOSEST); // frame at 2 seconds
//        byte [] artwork = mmr.getEmbeddedPicture();


//        mmr.release();
        return b;
    }







}





package com.mobao360.sunshine;  

import java.util.ArrayList;
import java.util.Arrays;
import java.lang.Short;
import java.lang.reflect.Array;

import android.content.Context;  
import android.graphics.Canvas;  
import android.graphics.Color;  
import android.graphics.DashPathEffect;  
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;  
import android.graphics.PathEffect;  
import android.graphics.Rect;  
import android.media.AudioRecord;  
import android.util.Log;  
import android.view.SurfaceView;  

public class AudioProcess {  
    public static final float pi= (float) 3.1415926;  
    //应该把处理前后处理后的普线都显示出来  
    private ArrayList<short[]> inBuf = new ArrayList<short[]>();//原始录入数据  
    private ArrayList<int[]> outBuf = new ArrayList<int[]>();//处理后的数据  
    private boolean isRecording = false;  
  
    Context mContext;  
    private int shift = 30;  
    public int frequence = 0;  
      
    private int length = 256;  
    //y轴缩小的比例  
    public int rateY = 21;  
    //y轴基线  
    public int baseLine = 0;  
    //初始化画图的一些参数  
    public void initDraw(int rateY, int baseLine,Context mContext, int frequence){  
        this.mContext = mContext;  
        this.rateY = rateY;  
        this.baseLine = baseLine;  
        this.frequence = frequence;  
    }  
    //启动程序  
    public void start(AudioRecord audioRecord, int minBufferSize, SurfaceView sfvSurfaceView) {  
        isRecording = true;  
        new RecordThread(audioRecord, minBufferSize).start();  
        new DrawThread(sfvSurfaceView).start();  
    }  
    //停止程序  
    public void stop(SurfaceView sfvSurfaceView){  
        isRecording = false;  
        inBuf.clear();  
    }  
      
    //录音线程  
    class RecordThread extends Thread{  
        private AudioRecord audioRecord;  
        private int minBufferSize;  
        private long currenttime;  
        private long endtime;  
        private long time = 1;  
          
        public RecordThread(AudioRecord audioRecord,int minBufferSize){  
            this.audioRecord = audioRecord;  
            this.minBufferSize = minBufferSize;  
        }  
          
        public void run(){  
            try{  
                short[] buffer = new short[minBufferSize];  
                audioRecord.startRecording();  
                while(isRecording){  
                    int res = audioRecord.read(buffer, 0, minBufferSize);  
                    synchronized (inBuf){  
                        inBuf.add(buffer);  
                    }  
                    //保证长度为2的幂次数  
                    length= up2int(res);  
                    short[]tmpBuf = new short[length];  
                    System.arraycopy(buffer, 0, tmpBuf, 0, length);  
                      
                    Complex[]complexs = new Complex[length];  
                    int[]outInt = new int[length];  
                    for(int i=0;i < length; i++){  
                        Short short1 = tmpBuf[i];  
                        complexs[i] = new Complex(short1.doubleValue());  
                    }  
                    
                    currenttime = System.currentTimeMillis();  
                    fft(complexs,length); 
                    endtime = System.currentTimeMillis();  
                    time = endtime - currenttime;  
                    //Log.i("FFT耗时", String.valueOf(time));
                    
                    
                    for (int i = 0; i < length; i++) {  
                        outInt[i] = complexs[i].getIntValue();  
                    }  
                    synchronized (outBuf) {  
                        outBuf.add(outInt);  
                    }  
                }  
                audioRecord.stop();  
            }catch (Exception e) {  
                // TODO: handle exception  
                Log.i("Rec E",e.toString());  
            }  
              
        }  
    }  
  
    //绘图线程  
    class DrawThread extends Thread{  
        //画板  
        private SurfaceView sfvSurfaceView;  
        //当前画图所在屏幕x轴的坐标  
        //画笔  
        private Paint mPaint;  
        private Paint tPaint;  
        private Paint dashPaint;  
        public DrawThread(SurfaceView sfvSurfaceView) {  
            this.sfvSurfaceView = sfvSurfaceView;  
            //设置画笔属性  
            mPaint = new Paint();  
            mPaint.setColor(Color.BLUE);  
            mPaint.setStrokeWidth(2);  
            mPaint.setAntiAlias(true);  
              
            tPaint = new Paint();  
            tPaint.setColor(Color.YELLOW);  
            tPaint.setStrokeWidth(1);  
            tPaint.setAntiAlias(true);  
              
            //画虚线  
            dashPaint = new Paint();  
            dashPaint.setStyle(Paint.Style.STROKE);  
            dashPaint.setColor(Color.GRAY);  
            Path path = new Path();  
            path.moveTo(0, 10);  
            path.lineTo(480,10);   
            PathEffect effects = new DashPathEffect(new float[]{5,5,5,5},1);  
            dashPaint.setPathEffect(effects);  
        }  
          
        @SuppressWarnings("unchecked")  
        public void run() {  
            while (isRecording) {  
                ArrayList<int[]>buf = new ArrayList<int[]>();  
                synchronized (outBuf) {  
                    if (outBuf.size() == 0) {  
                        continue;  
                    }  
                    buf = (ArrayList<int[]>)outBuf.clone();  
                    outBuf.clear();  
                }  
                //根据ArrayList中的short数组开始绘图  
                for(int i = 0; i < buf.size(); i++){  
                    int[]tmpBuf = buf.get(i);  
                    SimpleDraw(tmpBuf, rateY, baseLine);  
                    //Log.i("测速", String.valueOf(i) + "+" + String.valueOf(tmpBuf.length));
                    //Log.i("数据", String.valueOf(tmpBuf)); 
                    //System.out.println(Arrays.asList(tmpBuf));
//                    for (int i1 = 0; i1 < tmpBuf.length;i1++)	//获取到数组长度
//                    {
//                		System.out.print(tmpBuf[i1] + ",");	//循环输出每个元素
//                	}
                    //Log.i("数据", String.valueOf(tmpBuf));
                }  
                  
            }  
        }  
          
        /**  
         * 绘制指定区域  
         *   
         * @param start  
         *            X 轴开始的位置(全屏)  
         * @param buffer  
         *             缓冲区  
         * @param rate  
         *            Y 轴数据缩小的比例  
         * @param baseLine  
         *            Y 轴基线  
         */   
  
        private void SimpleDraw(int[] buffer, int rate, int baseLine){  
        	int max_value = 0;
        	int max_freq = 0;
            Canvas canvas = sfvSurfaceView.getHolder().lockCanvas(  
                    new Rect(0, 0, buffer.length,sfvSurfaceView.getHeight()));  
            canvas.drawColor(Color.BLACK);  
            canvas.drawText("幅度值", 0, 3, 2, 15, tPaint);  
            canvas.drawText("原点(0,0)", 0, 7, 5, baseLine + 15, tPaint);  
            canvas.drawText("频率(HZ)", 0, 6, sfvSurfaceView.getWidth() - 50, baseLine + 30, tPaint);  
            canvas.drawLine(shift, 20, shift, baseLine, tPaint);  
            canvas.drawLine(shift, baseLine, sfvSurfaceView.getWidth(), baseLine, tPaint);  
            canvas.save();  
            canvas.rotate(30, shift, 20);  
            canvas.drawLine(shift, 20, shift, 30, tPaint);  
            canvas.rotate(-60, shift, 20);  
            canvas.drawLine(shift, 20, shift, 30, tPaint);  
            canvas.rotate(30, shift, 20);  
            canvas.rotate(30, sfvSurfaceView.getWidth()-1, baseLine);  
            canvas.drawLine(sfvSurfaceView.getWidth() - 1, baseLine, sfvSurfaceView.getWidth() - 11, baseLine, tPaint);  
            canvas.rotate(-60, sfvSurfaceView.getWidth()-1, baseLine);  
            canvas.drawLine(sfvSurfaceView.getWidth() - 1, baseLine, sfvSurfaceView.getWidth() - 11, baseLine, tPaint);  
            canvas.restore();  
            tPaint.setStyle(Style.STROKE);  
            for(int index = 64; index <= 512; index = index + 64){  
                canvas.drawLine(shift + index, baseLine, shift + index, 40, dashPaint);  
                String str = String.valueOf(frequence / 1024 * index);  
                canvas.drawText( str, 0, str.length(), shift + index - 15, baseLine + 15, tPaint);  
            }  
            int y;  
            for(int i = 0; i < buffer.length/2; i = i + 1){  
                y = baseLine - buffer[i] / rateY ;  
                if(y > sfvSurfaceView.getHeight() * 0.7 )
                {
                	y = baseLine;
                }else
                {
                	if(max_value > y)
                	{
                		
                	}
                	else {
						max_value = y;
						max_freq = i;
					}
                	//y = y/2 ;
                	//Log.i("频段值", String.valueOf(y) + "+" + String.valueOf(i) );             	
                }         
                canvas.drawLine(2*i + shift, baseLine, 2*i +shift, y, mPaint);  
            }  
            if(max_freq>0) 
        		Log.i("频段值", String.valueOf(max_freq) ); //8000Hz采样-3759Hz极值（241&271）
            max_value = 0;
			max_freq = 0;
            sfvSurfaceView.getHolder().unlockCanvasAndPost(canvas);  
        }  
    }  
      
    /** 
     * 向上取最接近iint的2的幂次数.比如iint=320时,返回256 
     * @param iint 
     * @return 
     */  
    private int up2int(int iint) {  
        int ret = 1;  
        while (ret<=iint) {  
            ret = ret << 1;  
        }  
        return ret>>1;  
    }  
      
    //快速傅里叶变换  
    public void fft(Complex[] xin,int N)  
    {  
        int f,m,N2,nm,i,k,j,L;//L:运算级数  
        float p;  
        int e2,le,B,ip;  
        Complex w = new Complex();  
        Complex t = new Complex();  
        N2 = N / 2;//每一级中蝶形的个��,同时也代表m位二进制数最高位的十进制权值  
        f = N;//f是为了求流程的级数而设立的  
        for(m = 1; (f = f / 2) != 1; m++);                             //得到流程图的共几级  
        nm = N - 2;  
        j = N2;  
        /******倒序运算——雷德算法******/  
        for(i = 1; i <= nm; i++)  
        {  
            if(i < j)//防止重复交换  
            {  
                t = xin[j];  
                xin[j] = xin[i];  
                xin[i] = t;  
            }  
            k = N2;  
            while(j >= k)  
            {  
                j = j - k;  
                k = k / 2;  
            }  
            j = j + k;  
        }  
        /******蝶形图计算部分******/  
        for(L=1; L<=m; L++)                                    //从第1级到第m级  
        {  
            e2 = (int) Math.pow(2, L);  
            //e2=(int)2.pow(L);  
            le=e2+1;  
            B=e2/2;  
            for(j=0;j<B;j++)                                    //j从0到2^(L-1)-1  
            {  
                p=2*pi/e2;  
                w.real = Math.cos(p * j);  
                //w.real=Math.cos((double)p*j);                                   //系数W  
                w.image = Math.sin(p*j) * -1;  
                //w.imag = -sin(p*j);  
                for(i=j;i<N;i=i+e2)                                //计算具有相同系数的数据  
                {  
                    ip=i+B;                                           //对应蝶形的数据间隔为2^(L-1)  
                    t=xin[ip].cc(w);  
                    xin[ip] = xin[i].cut(t);  
                    xin[i] = xin[i].sum(t);  
                }  
            }  
        }  
    }  
}  
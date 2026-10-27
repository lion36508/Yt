package com.lion.downloader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

public class StarrySkyView extends View {
    private Paint starPaint;
    private Paint shootingStarPaint;
    private Star[] stars;
    private ShootingStar[] shootingStars;
    private Random random;
    private int width, height;

    public StarrySkyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        random = new Random();
        
        // टिमटिमाते तारों के लिए
        starPaint = new Paint();
        starPaint.setColor(Color.WHITE);
        starPaint.setAntiAlias(true);

        // उल्का पिंड (Shooting Stars) के लिए
        shootingStarPaint = new Paint();
        shootingStarPaint.setColor(Color.parseColor("#CCFFFFFF")); // हल्का पारदर्शी सफेद
        shootingStarPaint.setStrokeWidth(6f);
        shootingStarPaint.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        
        // 150 छोटे-बड़े तारे
        stars = new Star[150];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star(random.nextInt(width), random.nextInt(height), random.nextFloat() * 4);
        }
        
        // 4 उल्का पिंड जो आसमान में दौड़ेंगे
        shootingStars = new ShootingStar[4];
        for (int i = 0; i < shootingStars.length; i++) {
            shootingStars[i] = new ShootingStar();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. गहरा नीला आसमान (ऊपर डार्क ब्लू, नीचे पूरा ब्लैक)
        LinearGradient gradient = new LinearGradient(0, 0, 0, height, 
                Color.parseColor("#0B0B2A"), Color.parseColor("#000000"), Shader.TileMode.CLAMP);
        Paint bgPaint = new Paint();
        bgPaint.setShader(gradient);
        canvas.drawRect(0, 0, width, height, bgPaint);

        // 2. तारे (Stars)
        for (Star star : stars) {
            starPaint.setAlpha(100 + random.nextInt(155)); // टिमटिमाने का इफ़ेक्ट
            canvas.drawCircle(star.x, star.y, star.radius, starPaint);
        }

        // 3. उल्का पिंड (Shooting Stars)
        for (ShootingStar ss : shootingStars) {
            if (ss.active) {
                // उल्का पिंड की लाइन ड्रा करें
                canvas.drawLine(ss.x, ss.y, ss.x + ss.length, ss.y - ss.length, shootingStarPaint);
                // स्पीड से नीचे की तरफ लाएं
                ss.x -= ss.speed;
                ss.y += ss.speed;
                
                // स्क्रीन से बाहर जाने पर निष्क्रिय करें
                if (ss.x < 0 || ss.y > height) {
                    ss.active = false;
                }
            } else {
                // कुछ समय बाद रैंडमली नया उल्का पिंड पैदा करें
                if (random.nextInt(100) > 96) {
                    ss.reset();
                }
            }
        }

        // एनिमेशन को लूप में चलाने के लिए हर 30ms में रीफ्रेश करें (लगभग 30 FPS)
        postInvalidateDelayed(30);
    }

    // तारे का डेटा स्टोर करने के लिए क्लास
    class Star {
        float x, y, radius;
        Star(float x, float y, float radius) { 
            this.x = x; this.y = y; this.radius = radius; 
        }
    }

    // उल्का पिंड का डेटा स्टोर करने के लिए क्लास
    class ShootingStar {
        float x, y, speed, length;
        boolean active = false;
        void reset() {
            x = width / 2 + random.nextInt(width); // दाईं ओर से शुरू होगा
            y = -100; // ऊपर से गिरना शुरू होगा
            speed = 25 + random.nextInt(40); // बहुत तेज स्पीड
            length = 80 + random.nextInt(150); // पूंछ की लंबाई
            active = true;
        }
    }
}

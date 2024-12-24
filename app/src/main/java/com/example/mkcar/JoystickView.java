package com.example.mkcar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class JoystickView extends View {

    private Paint outerCirclePaint;
    private Paint innerCirclePaint;
    private float outerCircleRadius;
    private float innerCircleRadius;
    private float centerX;
    private float centerY;
    private float innerCircleX;
    private float innerCircleY;

    private JoystickListener joystickListener;

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        outerCirclePaint = new Paint();
        outerCirclePaint.setColor(Color.DKGRAY);
        outerCirclePaint.setStyle(Paint.Style.FILL);

        innerCirclePaint = new Paint();
        innerCirclePaint.setColor(Color.MAGENTA);
        innerCirclePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = getWidth() / 2f;
        centerY = getHeight() / 2f;
        outerCircleRadius = Math.min(w, h) / 3f;
        innerCircleRadius = Math.min(w, h) / 6f;
        innerCircleX = centerX;
        innerCircleY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(centerX, centerY, outerCircleRadius, outerCirclePaint);
        canvas.drawCircle(innerCircleX, innerCircleY, innerCircleRadius, innerCirclePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float distance = (float) Math.sqrt(Math.pow(event.getX() - centerX, 2) + Math.pow(event.getY() - centerY, 2));
        float maxDistance = outerCircleRadius;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (distance < maxDistance) {
                    innerCircleX = event.getX();
                    innerCircleY = event.getY();
                } else {
                    float ratio = maxDistance / distance;
                    innerCircleX = centerX + (event.getX() - centerX) * ratio;
                    innerCircleY = centerY + (event.getY() - centerY) * ratio;
                }
                if (joystickListener != null) {
                    float normalizedX = (innerCircleX - centerX) / maxDistance;
                    float normalizedY = (innerCircleY - centerY) / maxDistance;
                    joystickListener.onJoystickMoved(normalizedX, normalizedY, getId());
                }
                break;

            case MotionEvent.ACTION_UP:
                innerCircleX = centerX;
                innerCircleY = centerY;
                if (joystickListener != null) {
                    joystickListener.onJoystickMoved(0, 0, getId());
                }
                break;
        }
        invalidate();
        return true;
    }

    public void setJoystickListener(JoystickListener listener) {
        this.joystickListener = listener;
    }

    public interface JoystickListener {
        void onJoystickMoved(float xPercent, float yPercent, int id);
    }
}

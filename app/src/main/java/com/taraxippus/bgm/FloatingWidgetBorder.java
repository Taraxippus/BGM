package com.taraxippus.bgm;

import android.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.util.*;

public class FloatingWidgetBorder extends FrameLayout
{
	private ImageView mLeftHandle;
    private ImageView mRightHandle;
    private ImageView mTopHandle;
    private ImageView mBottomHandle;

	int activeHandle = -1;
	final float handleWidth;
	final int padding;
	public float aspectRatio = 1;
	boolean preserveAspectRatio;
	
	final WindowManager windowManager;
	WindowManager.LayoutParams paramsF, paramsB;
	View view;
	
	public FloatingWidgetBorder(Context context)
	{
		super(context);

		windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		
		padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
		paramsB = new WindowManager.LayoutParams(
			300 + padding * 2,
			300 + padding * 2,
			WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
			WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
			PixelFormat.TRANSLUCENT);
		
	    setBackgroundResource(R.drawable.widget_resize_shadow);
        setForeground(getResources().getDrawable(R.drawable.widget_resize_frame));
        setPadding(0, 0, 0, 0);
		setClipChildren(false);

        final int handleMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 13, context.getResources().getDisplayMetrics());
		handleWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, context.getResources().getDisplayMetrics());

        LayoutParams lp;
        mLeftHandle = new ImageView(context);
        mLeftHandle.setImageResource(R.drawable.ic_widget_resize_handle);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
							  Gravity.LEFT | Gravity.CENTER_VERTICAL);
        lp.leftMargin = handleMargin;
        addView(mLeftHandle, lp);
        mRightHandle = new ImageView(context);
        mRightHandle.setImageResource(R.drawable.ic_widget_resize_handle);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
							  Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        lp.rightMargin = handleMargin;
        addView(mRightHandle, lp);
        mTopHandle = new ImageView(context);
        mTopHandle.setImageResource(R.drawable.ic_widget_resize_handle);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
							  Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        lp.topMargin = handleMargin;
        addView(mTopHandle, lp);
        mBottomHandle = new ImageView(context);
        mBottomHandle.setImageResource(R.drawable.ic_widget_resize_handle);
        lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
							  Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        lp.bottomMargin = handleMargin;
        addView(mBottomHandle, lp);

	}
	
	public void show(WindowManager.LayoutParams paramsF, View view, boolean preserveAspectRatio)
	{
		this.view = view;
		this.paramsF = paramsF;
		this.preserveAspectRatio = preserveAspectRatio;
		
		paramsB.x = paramsF.x;
		paramsB.y = paramsF.y;
		paramsB.width = paramsF.width + padding * 2;
		paramsB.height = paramsF.height + padding * 2;
		
		if (this.getVisibility() == View.INVISIBLE)
		{
			this.setVisibility(View.VISIBLE);

			windowManager.addView(this, paramsB);
		}
		else
			windowManager.updateViewLayout(this, paramsB);
			
	}

	private int initialX, initialY, initialWidth, initialHeight;

	private float initialTouchX;
	private float initialTouchY;
	
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (view == null)
			return false;

		switch(event.getAction())
		{
			case MotionEvent.ACTION_DOWN:
				initialX = paramsF.x;
				initialY = paramsF.y;
				initialWidth = paramsF.width;
				initialHeight = paramsF.height;
				initialTouchX = event.getRawX();
				initialTouchY = event.getRawY();

				this.setActiveHandle(event.getX(), event.getY());

				if (this.activeHandle == -1)
				{
					this.setVisibility(View.INVISIBLE);
					windowManager.removeView(this);
				}
					
				break;

			case MotionEvent.ACTION_UP:
				this.setActiveHandle(-1);
				break;

			case MotionEvent.ACTION_OUTSIDE:
			case MotionEvent.ACTION_CANCEL:
				this.setVisibility(View.INVISIBLE);
				windowManager.removeView(this);
				break;

			case MotionEvent.ACTION_MOVE:

				paramsF.x = initialX;
				paramsF.y = initialY;
				paramsF.width = initialWidth;
				paramsF.height = initialHeight;

				if (this.activeHandle == -1)
				{
					paramsF.x += (int) (event.getRawX() - initialTouchX);
					paramsF.y += (int) (event.getRawY() - initialTouchY);
				}
				else
				{
					if ((this.activeHandle & 0b1) != 0)
					{
						paramsF.x += (int) (event.getRawX() - initialTouchX) / 2F;
						paramsF.width -= (int) (event.getRawX() - initialTouchX);
					}
					if ((this.activeHandle & 0b10) != 0)
					{
						paramsF.x += (int) (event.getRawX() - initialTouchX) / 2F;
						paramsF.width += (int) (event.getRawX() - initialTouchX);
					}
					if ((this.activeHandle & 0b100) != 0)
					{
						paramsF.y += (int) (event.getRawY() - initialTouchY) / 2F;
						paramsF.height -= (int) (event.getRawY() - initialTouchY);
					}
					if ((this.activeHandle & 0b1000) != 0)
					{
						paramsF.y += (int) (event.getRawY() - initialTouchY) / 2F;
						paramsF.height += (int) (event.getRawY() - initialTouchY);
					}

					paramsF.width = Math.max((int) this.handleWidth, paramsF.width);
					paramsF.height = Math.max((int) this.handleWidth, paramsF.height);

					if (preserveAspectRatio)
						if ((this.activeHandle & 0b11) != 0)
							paramsF.height = (int) (paramsF.width / aspectRatio);

						else
							paramsF.width = (int) (paramsF.height * aspectRatio);
					
				}

				paramsB.x = paramsF.x;
				paramsB.y = paramsF.y;
				paramsB.width = paramsF.width + padding * 2;
				paramsB.height = paramsF.height + padding * 2;

				windowManager.updateViewLayout(view, paramsF);
				windowManager.updateViewLayout(this, paramsB);

				break;
		}
		
		return super.onTouchEvent(event);
	}

	
	
	public int getHandle(float x, float y)
	{
		int flags = 0;

		if (x < handleWidth)
		{
			flags = flags | 0b1;
		}
		if (x > getWidth() - handleWidth)
		{
			flags = flags | 0b10;
		}
		if (y < handleWidth)
		{
			flags = flags | 0b100;
		}
		if (y > getHeight() - handleWidth)
		{
			flags = flags | 0b1000;
		}

		return flags == 0 ? -1 : flags;
	}

	public void setActiveHandle(float x, float y)
	{
		this.setActiveHandle(getHandle(x, y));
	}

	public void setActiveHandle(int handle)
	{
		this.activeHandle = handle;

		this.mLeftHandle.setAlpha((handle & 0b1) != 0 ? 1F : 0F);
		this.mRightHandle.setAlpha((handle & 0b10) != 0  ? 1F : 0F);
		this.mTopHandle.setAlpha((handle & 0b100) != 0  ? 1F : 0F);
		this.mBottomHandle.setAlpha((handle & 0b1000) != 0  ? 1F : 0F);
	}
}

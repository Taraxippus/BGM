package com.taraxippus.bgm;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.media.audiofx.*;
import android.media.session.*;
import android.net.*;
import android.net.wifi.*;
import android.opengl.*;
import android.os.*;
import android.preference.*;
import android.support.annotation.*;
import android.text.*;
import android.view.*;
import android.view.GestureDetector.*;
import android.view.WindowManager.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import com.taraxippus.bgm.gl.*;
import java.net.*;
import java.util.*;
import org.json.*;

import android.media.session.MediaController;

public class BGMService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnVideoSizeChangedListener, AudioManager.OnAudioFocusChangeListener, MediaPlayer.OnSeekCompleteListener, SharedPreferences.OnSharedPreferenceChangeListener
{
	public static final String SESSION = "com.taraxippus.bgm";
	public static final String WIFI_LOCK = "com.taraxippus.bgm";
	public static final int NOTIFICATION_ID = 1;
	
	public static final String ACTION_PLAY = "com.taraxippus.bgm.action.PLAY";
	public static final String ACTION_PAUSE = "com.taraxippus.bgm.action.PAUSE";
	public static final String ACTION_STOP = "com.taraxippus.bgm.action.STOP";
	public static final String ACTION_SKIP_NEXT = "com.taraxippus.bgm.action.SKIP_NEXT";
	public static final String ACTION_SKIP_PREVIOUS = "com.taraxippus.bgm.action.SKIP_PREVIOUS";
	public static final String ACTION_FAST_FORWARD = "com.taraxippus.bgm.action.FAST_FORWARD";
	public static final String ACTION_REWIND = "com.taraxippus.bgm.action.REWIND";
	public static final String ACTION_OPEN = "com.taraxippus.bgm.action.OPEN";
	public static final String ACTION_SEEK = "com.taraxippus.bgm.action.SEEK";
	public static final String ACTION_VIDEO = "com.taraxippus.bgm.action.VIDEO";
	public static final String ACTION_VISUALIZER = "com.taraxippus.bgm.action.VISUALIZER";
	public static final String ACTION_VISUALIZER_UNPIN = "com.taraxippus.bgm.action.UNPIN";
	public static final String ACTION_WEBVIEW = "com.taraxippus.bgm.action.WEBVIEW";
	
	final Random random = new Random();
	
	String url, nextUrl;
	ArrayList<Stream> streams = new ArrayList<>();
	
	boolean repeat;
	boolean shuffle;
	
	int playlistIndex;
	int time = -1, tries;
	String[] urls;
	String[] titles;
	String[] artists;
	NextSongTask nextSongTask;
	
	boolean wasPlaying;
	boolean fallbackPlayer;
	
	Visualizer visualizer;
	MediaPlayer player;
	MediaSession session;
	MediaController controller;
	
	WifiManager.WifiLock wifiLock;
	private static boolean preparing = true, removeStuttering = false;
	
	private WindowManager windowManager;
	private NotificationManager notificationManager;
	
	private GLRenderer view_renderer;
	private SurfaceView view_video;
	protected View view, view_progress, view_visualizer;
	private TextView text_title, text_artist;
	private ImageView button_play, button_next, button_previous;
	private View button_close;
	private SeekBar view_seek;
	private FloatingWidgetBorder border;
	private WebView fallbackView;
	private LayoutParams paramsF1, paramsF2, paramsF1F, paramsW;
	public Runnable onViewLayoutChanged;
	
	String playAction = ACTION_PLAY;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (wifiLock == null)
			wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, WIFI_LOCK);
			
		if (windowManager == null)
		{
			windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
			notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		}
			
		if (intent != null && intent.hasExtra("urls") && (url == null || !intent.hasExtra("savedState")))
		{
			if (intent.getBooleanExtra("add", false) && urls != null)
			{
				String[] urls1 = new String[urls.length + intent.getStringArrayExtra("urls").length];
				String[] titles1 = new String[titles.length + intent.getStringArrayExtra("titles").length];
				String[] artists1 = new String[artists.length + intent.getStringArrayExtra("artists").length];
				
				System.arraycopy(urls, 0, urls1, 0, urls.length);
				System.arraycopy(intent.getStringArrayExtra("urls"), 0, urls1, urls.length, intent.getStringArrayExtra("urls").length);
				System.arraycopy(titles, 0, titles1, 0, titles.length);
				System.arraycopy(intent.getStringArrayExtra("titles"), 0, titles1, titles.length, intent.getStringArrayExtra("titles").length);
				System.arraycopy(artists, 0, artists1, 0, artists.length);
				System.arraycopy(intent.getStringArrayExtra("artists"), 0, artists1, artists.length, intent.getStringArrayExtra("artists").length);
				
				urls = urls1;
				titles = titles1;
				artists = artists1;
				
				buildNotification(R.drawable.pause, "Pause", ACTION_PAUSE);
				
				if (button_next != null)
					onViewLayoutChanged.run();
			}
			else
			{
				if (nextSongTask != null)
					nextSongTask.cancel(true);

				releaseMediaSession();

				repeat = intent.getBooleanExtra("repeat", true);
				shuffle = intent.getBooleanExtra("shuffle", false);
				urls = intent.getStringArrayExtra("urls");
				titles = intent.getStringArrayExtra("titles");
				artists = intent.getStringArrayExtra("artists");
				if (urls.length == 1)
					time = intent.getIntExtra("time", -1) * 1000;
				playlistIndex = intent.getIntExtra("index", 0);
				
				buildNotification(R.drawable.load, "Loading", ACTION_PLAY);
				
				url = "";
				initViews();
				initVisualizerView();
			}
		}
		else if (url == null)
		{
			stopSelf();
			return -1;
		}
			
		if (player == null)
			initMediaSession();
		
		if (intent != null && controller != null)
			handleIntent(intent);
		
		return super.onStartCommand(intent, flags, startId);
	}
	
	public void initVisualizer()
	{
		if (visualizer != null)
			releaseVisualizer();
			
		if (player != null && view_renderer != null)
		{
			visualizer = new Visualizer(player.getAudioSessionId());
			visualizer.setEnabled(false);
			visualizer.setCaptureSize((view_renderer.COUNT + 1) * 2);
			visualizer.setDataCaptureListener(view_renderer, 10000, true, true);
			visualizer.setEnabled(true);
		}
	}
	
	public void initVisualizerView()
	{
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("visualizer", false))
		{
			if (view_visualizer == null)
			{
				notificationManager.cancel(NOTIFICATION_ID + 3);
					
				view_visualizer = LayoutInflater.from(this).inflate(R.layout.visualizer, null);
				final View layout_controls = view_visualizer.findViewById(R.id.layout_controls);
				final View button_close = view_visualizer.findViewById(R.id.button_close);
				final View button_pin = view_visualizer.findViewById(R.id.button_pin);
				
				GLSurfaceView visualizer = (GLSurfaceView) view_visualizer.findViewById(R.id.visualizer);
				visualizer.setEGLContextClientVersion(2);
				visualizer.setPreserveEGLContextOnPause(true);
				visualizer.setEGLConfigChooser(new ConfigChooser(this));
				visualizer.getHolder().setFormat(PixelFormat.RGBA_8888);
				visualizer.setRenderer(view_renderer = new GLRenderer(this));
				visualizer.setZOrderMediaOverlay(true);
				
				if (border == null)
				{
					border = new FloatingWidgetBorder(this);
					border.setVisibility(View.INVISIBLE);
				}

				if (paramsF2 == null)
				{
					paramsF2 = new WindowManager.LayoutParams(
						300,
						300,
						LayoutParams.TYPE_SYSTEM_ALERT,
						LayoutParams.FLAG_LAYOUT_NO_LIMITS | LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_HARDWARE_ACCELERATED,
						PixelFormat.TRANSLUCENT);

					paramsF2.y = -300;
					paramsF2.gravity = Gravity.CENTER;
				}
				paramsF2.flags &= ~LayoutParams.FLAG_NOT_TOUCHABLE;
				
				windowManager.addView(view_visualizer, paramsF2);
				
				button_close.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							releaseVisualizerViews();
							
							Intent intent = new Intent(BGMService.this, BGMService.class);
							intent.setAction(ACTION_VISUALIZER);

							Notification.Builder builder = new Notification.Builder(BGMService.this)
								.setSmallIcon(R.drawable.launcher)
								.setContentText("Click to show again")
								.setContentTitle("Closed visualizer")
								.setContentIntent(PendingIntent.getService(BGMService.this, 0, intent, 0));

							notificationManager.notify(NOTIFICATION_ID + 3, builder.build());
						}		
					});
					
				button_pin.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							paramsF2.flags |= LayoutParams.FLAG_NOT_TOUCHABLE;
							windowManager.updateViewLayout(view_visualizer, paramsF2);
							
							Animation fade = AnimationUtils.loadAnimation(BGMService.this, R.anim.fade_out);
							fade.setDuration(300);
							fade.setAnimationListener(new Animation.AnimationListener()
								{
									@Override
									public void onAnimationStart(Animation p1) {}

									@Override
									public void onAnimationEnd(Animation p1)
									{
										layout_controls.setVisibility(View.INVISIBLE);
									}

									@Override
									public void onAnimationRepeat(Animation p1) {}
								});
							layout_controls.startAnimation(fade);
							
							Intent intent = new Intent(BGMService.this, BGMService.class);
							intent.setAction(ACTION_VISUALIZER_UNPIN);

							Notification.Builder builder = new Notification.Builder(BGMService.this)
								.setSmallIcon(R.drawable.launcher)
								.setContentText("Click to unpin")
								.setContentTitle("Pinned visualizer")
								.setContentIntent(PendingIntent.getService(BGMService.this, 0, intent, 0));

							notificationManager.notify(NOTIFICATION_ID + 3, builder.build());
						}		
					});

				view_visualizer.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							layout_controls.setVisibility(View.VISIBLE);
							layout_controls.bringToFront();
							
							Animation fade = AnimationUtils.loadAnimation(BGMService.this, R.anim.fade_in);
							fade.setDuration(300);
							layout_controls.startAnimation(fade);
						}
					});
				layout_controls.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							Animation fade = AnimationUtils.loadAnimation(BGMService.this, R.anim.fade_out);
							fade.setDuration(300);
							fade.setAnimationListener(new Animation.AnimationListener()
								{
									@Override
									public void onAnimationStart(Animation p1) {}

									@Override
									public void onAnimationEnd(Animation p1)
									{
										layout_controls.setVisibility(View.INVISIBLE);
									}

									@Override
									public void onAnimationRepeat(Animation p1) {}
								});
							layout_controls.startAnimation(fade);
						}
					});

				View.OnLongClickListener showBorder = new View.OnLongClickListener()
				{
					@Override
					public boolean onLongClick(View p1)
					{
						border.show(paramsF2, view_visualizer, false);
						return true;
					}
				};
				view_visualizer.setOnLongClickListener(showBorder);
				layout_controls.setOnLongClickListener(showBorder);
				
				if (player != null)
					initVisualizer();
			}
		}
		else
			releaseVisualizerViews();
	}
	
	public void releaseVisualizer()
	{
		if (visualizer != null)
		{
			visualizer.setEnabled(false);
			visualizer.release();
			visualizer = null;
		}
	}
	
	public void releaseVisualizerViews()
	{
		releaseVisualizer();
		if (view_visualizer != null)
		{
			windowManager.removeView(view_visualizer);

			view_visualizer = null;

			if (border.getVisibility() == View.VISIBLE && border.view == view_visualizer)
				windowManager.removeView(border);

			if (view == null && fallbackView == null)
				border = null;
		}
	}
	
	public void releaseViews()
	{
		if (view != null)
		{
			if (player != null)
				player.setDisplay(null);
			
			windowManager.removeView(view);

			view = null;
			button_close = null;

			if (border.getVisibility() == View.VISIBLE && border.view == view)
				windowManager.removeView(border);
				
			if (view_visualizer == null && fallbackView == null)
				border = null;
		}
	}
	
	public void releaseFallback()
	{
		if (fallbackView != null)
		{
			if (player != null)
				player.setDisplay(null);

			windowManager.removeView(fallbackView);

			fallbackView = null;

			if (border.getVisibility() == View.VISIBLE && border.view == fallbackView)
				windowManager.removeView(border);

			if (view_visualizer == null && view == null)
				border = null;
		}
	}
	
	public void initViews()
	{
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("video", false))
		{
			if (view == null)
			{
				notificationManager.cancel(NOTIFICATION_ID + 2);
					
				view = LayoutInflater.from(this).inflate(R.layout.video, null);
				view_video = (SurfaceView) view.findViewById(R.id.video);
				view_progress = view.findViewById(R.id.progress_video);
				final View layout_controls = view.findViewById(R.id.layout_controls);
				button_close = view.findViewById(R.id.button_close);
				button_play = (ImageView) view.findViewById(R.id.button_play);
				button_next = (ImageView) view.findViewById(R.id.button_next);
				button_previous = (ImageView) view.findViewById(R.id.button_previous);
				final TextView text_time = (TextView) view.findViewById(R.id.text_time);
				view_seek = (SeekBar) view.findViewById(R.id.seek);
				text_title = (TextView) view.findViewById(R.id.text_title);
				text_artist = (TextView) view.findViewById(R.id.text_artist);
				
				view_video.getHolder().setFormat(PixelFormat.TRANSPARENT);
				view_video.setZOrderMediaOverlay(true);
				
				if (playlistIndex > 0 && playlistIndex < titles.length)
				{
					text_title.setText(Html.fromHtml(titles[playlistIndex]));
					text_artist.setText(Html.fromHtml(artists[playlistIndex]));
				}
				
				if (border == null)
				{
					border = new FloatingWidgetBorder(this);
					border.setVisibility(View.INVISIBLE);
				}
					
				if (paramsF1 == null)
				{
					paramsF1 = new WindowManager.LayoutParams(
						400,
						225,
						LayoutParams.TYPE_SYSTEM_ALERT,
						LayoutParams.FLAG_LAYOUT_NO_LIMITS | LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_HARDWARE_ACCELERATED,
						PixelFormat.TRANSLUCENT);
						
					paramsF1F = new WindowManager.LayoutParams(
						getResources().getDisplayMetrics().widthPixels,
						getResources().getDisplayMetrics().heightPixels,
						LayoutParams.TYPE_SYSTEM_ALERT,
						LayoutParams.FLAG_LAYOUT_NO_LIMITS | LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_HARDWARE_ACCELERATED,
						PixelFormat.TRANSLUCENT);
					
					paramsF1.gravity = Gravity.CENTER;
					border.aspectRatio = 16 / 9F;
				}
				
				windowManager.addView(view, paramsF1);
				
				if (player != null && !preparing)
					view_progress.setVisibility(View.GONE);
				
				final Drawable thumb = view_seek.getThumb();
				final Drawable thumb_hidden = new ColorDrawable(Color.TRANSPARENT);
				view_seek.setThumb(thumb_hidden);
				
				if (player != null && !preparing)
					view_seek.setMax(player.getDuration() / 1000);
				
				final Handler handler = new Handler();
				new Runnable()
					{
						@Override
						public void run()
						{
							if (player != null && !preparing && view_seek.getThumb() == thumb_hidden)
								view_seek.setProgress(player.getCurrentPosition() / 1000);

							if (player != null && !preparing)
								view_seek.setSecondaryProgress(player.getCurrentPosition() / 1000);
							
							if (view != null)
								handler.postDelayed(this, 1000);
						}
					}.run();
				final Runnable hide = new Runnable()
				{
					@Override
					public void run()
					{
						if (view == null || layout_controls.getVisibility() == View.INVISIBLE)
							return;

						Animation fade = AnimationUtils.loadAnimation(BGMService.this, R.anim.fade_out);
						fade.setDuration(600);
						fade.setAnimationListener(new Animation.AnimationListener()
							{
								@Override
								public void onAnimationStart(Animation p1) {}

								@Override
								public void onAnimationEnd(Animation p1)
								{
									layout_controls.setVisibility(View.INVISIBLE);
								}

								@Override
								public void onAnimationRepeat(Animation p1) {}
							});
						layout_controls.startAnimation(fade);
					}
				};
				
					
				view_seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
					{
						@Override
						public void onProgressChanged(SeekBar p1, int p2, boolean user)
						{
							if (text_time != null)
								text_time.setText(StringUtils.toTime(p2));
								
						}

						@Override
						public void onStartTrackingTouch(SeekBar p1)
						{
							view_seek.setThumb(thumb);
							handler.removeCallbacks(hide);
						}

						@Override
						public void onStopTrackingTouch(SeekBar p1)
						{
							view_seek.setThumb(thumb_hidden);
							view_seek.setSecondaryProgress(view_seek.getProgress());
							
							if (player != null && !preparing)
								player.seekTo(view_seek.getProgress() * 1000);
								
							handler.postDelayed(hide, 5000);
						}
					});
					
				button_play.setImageResource(player != null && !preparing && player.isPlaying() ? R.drawable.pause : R.drawable.play);
				button_play.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							Intent intent = new Intent(BGMService.this, BGMService.class);
							intent.setAction(playAction);
							handleIntent(intent);
							
							handler.removeCallbacks(hide);
							handler.postDelayed(hide, 5000);
						}
					});
					
				button_next.setOnClickListener(new View.OnClickListener()
				{
						@Override
						public void onClick(View p1)
						{
							Intent intent = new Intent(BGMService.this, BGMService.class);
							intent.setAction(ACTION_SKIP_NEXT);
							handleIntent(intent);

							handler.removeCallbacks(hide);
							handler.postDelayed(hide, 5000);
						}
				});
				
				button_previous.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							Intent intent = new Intent(BGMService.this, BGMService.class);
							intent.setAction(ACTION_SKIP_PREVIOUS);
							handleIntent(intent);

							handler.removeCallbacks(hide);
							handler.postDelayed(hide, 5000);
						}
					});
					
				view_video.getHolder().addCallback(new SurfaceHolder.Callback()
				{
						@Override
						public void surfaceCreated(SurfaceHolder p1)
						{
							if (player != null)
								player.setDisplay(view_video.getHolder());
						}

						@Override
						public void surfaceChanged(SurfaceHolder p1, int p2, int p3, int p4) {}
				
						@Override
						public void surfaceDestroyed(SurfaceHolder p1) {}
				});
				
				button_close.setOnClickListener(new View.OnClickListener()
				{
						@Override
						public void onClick(View p1)
						{
							releaseViews();
							
							Intent intent = new Intent(BGMService.this, BGMService.class);
							intent.setAction(ACTION_VIDEO);
							
							Notification.Builder builder = new Notification.Builder(BGMService.this)
								.setSmallIcon(R.drawable.launcher)
								.setContentText("Click to show again")
								.setContentTitle("Closed video player")
								.setContentIntent(PendingIntent.getService(BGMService.this, 0, intent, 0));

							notificationManager.notify(NOTIFICATION_ID + 2, builder.build());
						}		
				});
				
				view.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							layout_controls.setVisibility(View.VISIBLE);
							layout_controls.bringToFront();
							Animation fade = AnimationUtils.loadAnimation(BGMService.this, R.anim.fade_in);
							fade.setDuration(300);
							layout_controls.startAnimation(fade);
							
							handler.postDelayed(hide, 5000);
						}
					});
				layout_controls.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							Animation fade = AnimationUtils.loadAnimation(BGMService.this, R.anim.fade_out);
							fade.setDuration(300);
							fade.setAnimationListener(new Animation.AnimationListener()
								{
									@Override
									public void onAnimationStart(Animation p1) {}

									@Override
									public void onAnimationEnd(Animation p1)
									{
										layout_controls.setVisibility(View.INVISIBLE);
									}

									@Override
									public void onAnimationRepeat(Animation p1) {}
								});
							layout_controls.startAnimation(fade);
							
							handler.removeCallbacks(hide);
						}
					});

				View.OnLongClickListener showBorder = new View.OnLongClickListener()
					{
						@Override
						public boolean onLongClick(View p1)
						{
							if (view.getLayoutParams() == paramsF1F)
							{
								onViewLayoutChanged.run();
								windowManager.updateViewLayout(view, paramsF1);
							}
							
							border.show(paramsF1, view, true);
								
							return true;
						}
					};
				view.setOnLongClickListener(showBorder);
				layout_controls.setOnLongClickListener(showBorder);
				
				SimpleOnGestureListener listener = new SimpleOnGestureListener()
				{
					@Override
					public boolean onDoubleTap(android.view.MotionEvent e)
					{
						if (view.getLayoutParams() == paramsF1F)
						{
							windowManager.updateViewLayout(view, paramsF1);
							
							onViewLayoutChanged.run();
						}
						else
						{
							Point size = new Point();
							windowManager.getDefaultDisplay().getRealSize(size);
							
							paramsF1F.width = size.x;
							paramsF1F.height = (int) (paramsF1F.width / border.aspectRatio);
							int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
							int status = 0;
							if (resourceId > 0)
								status = getResources().getDimensionPixelSize(resourceId);
							
							paramsF1F.x = -(getResources().getDisplayMetrics().widthPixels - size.x) / 2;
							paramsF1F.y = -(getResources().getDisplayMetrics().heightPixels - size.y + status) / 2;
							
							windowManager.updateViewLayout(view, paramsF1F);
							text_title.setVisibility(View.VISIBLE);
							text_artist.setVisibility(View.VISIBLE);
							text_time.setVisibility(View.VISIBLE);
							view_seek.setVisibility(View.VISIBLE);
							button_next.setVisibility((urls.length > 1 || shuffle) ? View.VISIBLE : View.GONE);
							button_previous.setVisibility((urls.length > 1 || shuffle) ? View.VISIBLE : View.GONE);
						}
						return true;
					}
				};
				final GestureDetector detector = new GestureDetector(this, listener);
				detector.setOnDoubleTapListener(listener);
				
				View.OnTouchListener gestureTouch = new View.OnTouchListener()
				{
						@Override
						public boolean onTouch(View p1, MotionEvent p2)
						{
							return detector.onTouchEvent(p2);
						}
				};
				view.setOnTouchListener(gestureTouch);
				layout_controls.setOnTouchListener(gestureTouch);
				
				final float small = 150 * getResources().getDisplayMetrics().density;
				final float medium = 200 * getResources().getDisplayMetrics().density;
				
				onViewLayoutChanged = new Runnable()
				{
					@Override
					public void run()
					{
						text_title.setVisibility(paramsF1.width < small ? View.GONE : View.VISIBLE);
						text_artist.setVisibility(paramsF1.width < medium ? View.GONE : View.VISIBLE);
						text_time.setVisibility(paramsF1.width < medium ? View.GONE : View.VISIBLE);
						view_seek.setVisibility(paramsF1.width < small ? View.GONE : View.VISIBLE);
						button_next.setVisibility((urls.length > 1 || shuffle) && paramsF1.width >= medium ? View.VISIBLE : View.GONE);
						button_previous.setVisibility((urls.length > 1 || shuffle) && paramsF1.width >= medium ? View.VISIBLE : View.GONE);
					}
				};
				onViewLayoutChanged.run();
			}
		}
		else 
			releaseViews();
	}

	public void initFallback()
	{
		if (fallbackPlayer)
		{
			if (border == null)
			{
				border = new FloatingWidgetBorder(this);
				border.setVisibility(View.INVISIBLE);
			}
			
			if (fallbackView == null)
			{
				WebView webView = new WebView(this)
				{
					@Override
					public boolean dispatchKeyEvent(KeyEvent event)
					{
						if (fallbackView != null && event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK)
						{
							if (fallbackView.getProgress() != 100)
								fallbackView.stopLoading();

							else if (fallbackView.canGoBack())
								fallbackView.goBack();

							else
								return false;

							return true;
						}
						return super.dispatchKeyEvent(event);
					}
				};
				webView.getSettings().setJavaScriptEnabled(true);
				webView.addJavascriptInterface(new Object()
					{
						
					}, "JSInterface");
				webView.setWebViewClient(new WebViewClient()
					{
						@Override
						public boolean shouldOverrideUrlLoading(WebView view, String request)
						{
							view.loadUrl(request);
							return true;
						}
						
						@Override
						public void onPageFinished(WebView view, String url)
						{
							view.evaluateJavascript("var player = document.getElementsByTagName('video')[0]; player.muted = false;", null);
							buildNotification(R.drawable.pause, "Pause", ACTION_PAUSE);
						}
					});
				webView.setWebChromeClient(new WebChromeClient()
					{
					});
				webView.setOnLongClickListener(new View.OnLongClickListener()
					{
						@Override
						public boolean onLongClick(View view)
						{
							border.show(paramsW, view, true);

							return true;
						}
					});
					
				fallbackView = webView;
				paramsW = new WindowManager.LayoutParams(
					800,
					800,
					LayoutParams.TYPE_SYSTEM_ALERT,
					LayoutParams.FLAG_NOT_TOUCH_MODAL,	
					PixelFormat.TRANSLUCENT);
				paramsW.gravity = Gravity.CENTER;
				windowManager.addView(fallbackView, paramsW);
			}
			
			if (fallbackView.getUrl() == null || !fallbackView.getUrl().equals(urls[playlistIndex]))
				fallbackView.loadUrl(urls[playlistIndex]);
		}
		else
			releaseFallback();
	}

	public void releaseMediaSession()
	{
		if (player != null)
		{
			if (!preparing && player.isPlaying())
				player.stop();

			player.reset();
			player.release();
			player = null;
			preparing = true;
		}
		fallbackPlayer = false;
		releaseFallback();
	}

	public void initMediaSession()
	{
		if (url == null || url.isEmpty())
		{
			if (urls != null && urls.length > 0)
			{
				if (playlistIndex < 0)
					playlistIndex = 0;
				if (playlistIndex >= urls.length)
					playlistIndex = urls.length - 1;
				
				if (nextSongTask != null)
					nextSongTask.cancel(true);

				fallbackPlayer = false;
				
				nextSongTask = new NextSongTask();
				nextSongTask.execute(urls[playlistIndex]);
				
				buildNotification(R.drawable.load, "Loading", ACTION_PLAY);
			}
			
			return;
		}
			
		if (fallbackPlayer)
		{
			initFallback();
			if (button_close != null)
				button_close.callOnClick();
		}
		else
		{
			releaseMediaSession();

			try
			{
				player = new MediaPlayer();
				player.setAudioStreamType(AudioManager.STREAM_MUSIC);
				HashMap<String, String> headers = new HashMap<>();
				headers.put("Origin", "https://www.youtube.com");
				headers.put("Referer", "https://www.youtube.com/watch?v=" + YouTubeHelper.getId(Uri.parse(urls[playlistIndex])));
				player.setDataSource(this, Uri.parse(url.trim()), headers);
				player.setOnPreparedListener(this);
				player.setOnCompletionListener(this);
				player.setOnErrorListener(this);
				player.setOnVideoSizeChangedListener(this);
				player.setOnSeekCompleteListener(this);
				player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
				player.setLooping(urls.length == 1 && repeat);
				player.setPlaybackParams(player.getPlaybackParams().setSpeed(PreferenceManager.getDefaultSharedPreferences(BGMService.this).getFloat("speed", 1)));

				if (view != null && view_video.getHolder().getSurface() != null && view_video.getHolder().getSurface().isValid())
					player.setDisplay(view_video.getHolder());

				if (view_visualizer != null)
					initVisualizer();
			}
			catch (Exception e)
			{
				System.out.println("Something is wrong with url: " + url);
				e.printStackTrace();
				url = "";
				initMediaSession();

				return;
			}
		}
		
		if (session == null)
		{
			session = new MediaSession(this, SESSION);
			controller = new MediaController(this, session.getSessionToken());

			session.setCallback(new MediaSession.Callback()
				{
					@Override
					public void onPlay() 
					{
						super.onPlay();

						if (player == null)
							initMediaSession();

						else if (!preparing && !player.isPlaying())
						{
							AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
							int result = audioManager.requestAudioFocus(BGMService.this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

							if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
							{
								player.start();
								
								float volume = PreferenceManager.getDefaultSharedPreferences(BGMService.this).getFloat("volume", 1);
								player.setVolume(volume, volume);
								
								if (!wifiLock.isHeld())
									wifiLock.acquire();
									
								buildNotification(R.drawable.pause, "Pause", ACTION_PAUSE);
							}
						}
					}

					@Override
					public void onPause() 
					{
						super.onPause();

						if (!preparing && player != null && player.isPlaying())
						{
							player.pause();
							((AudioManager) getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(BGMService.this);
							
							if (wifiLock.isHeld() && (nextSongTask == null || nextSongTask.getStatus() != AsyncTask.Status.RUNNING))
								wifiLock.release();
								
							buildNotification(R.drawable.play, "Play", ACTION_PLAY);
						}
					}

					@Override
					public void onSkipToNext() 
					{
						if (urls.length > 1 && (playlistIndex + 1 != urls.length || repeat) || shuffle)
						{
							releaseMediaSession();
							
							if (urls.length == 1)
							{
								urls[0] = nextUrl;
							}
							else
							{
								if (shuffle)
									playlistIndex = random.nextInt(urls.length);

								else if (playlistIndex + 1 == urls.length)
									playlistIndex = 0;
									
								else
									playlistIndex++;
								
							}
						
							url = "";
							initMediaSession();
						}
						else if (!repeat)
						{
							if (!preparing && player != null && player.isPlaying())
							{
								player.seekTo(0);

								controller.getTransportControls().pause();
								buildNotification(R.drawable.play, "Play", ACTION_PLAY);
							}
							else if (!preparing)
							{
								if (wifiLock.isHeld())
									wifiLock.release();

								buildNotification(R.drawable.play, "Play", ACTION_PLAY);
							}
						}
					}

					@Override
					public void onSkipToPrevious()
					{
						super.onSkipToPrevious();
						
						if (!preparing && (player != null && player.getCurrentPosition() > 1000))
							player.seekTo(0);
						
						else if (urls.length > 1)
						{
							if (shuffle)
							{
								playlistIndex = random.nextInt(urls.length);
							}
							else if (playlistIndex == 0)
							{
								return;
							}
							else
							{
								playlistIndex--;
							}
							
							releaseMediaSession();

							url = "";
							initMediaSession();
						}
					}

					@Override
					public void onFastForward()
					{
						if (!preparing && player != null)
							player.seekTo(Math.min(player.getDuration() - 1, player.getCurrentPosition() + 10000));
						
						super.onFastForward();
					}

					@Override
					public void onRewind() 
					{
						if (!preparing && player != null)
							player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
						
						super.onRewind();
					}

					@Override
					public void onStop()
					{
						super.onStop();

						if (player != null && !preparing && player.isPlaying())
							player.stop();

						((AudioManager) getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(BGMService.this);
						
						stopForeground(true);
						stopSelf();
					}

					@Override
					public void onSeekTo(long pos)
					{
						super.onSeekTo(pos);
					}

					@Override
					public void onSetRating(Rating rating) 
					{
						super.onSetRating(rating);
					}
				});
				
			PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
		}
			
		buildNotification(R.drawable.load, "Load", ACTION_PLAY);
		if (player != null)
			player.prepareAsync();
		preparing = true;
		
		if (view_progress != null)
			view_progress.setVisibility(View.VISIBLE);
	}
	
	private Notification.Action generateAction(int icon, String title, String intentAction) 
	{
		Intent intent = new Intent(this, BGMService.class);
		intent.setAction(intentAction);
		intent.putExtra("repeat", repeat);
		intent.putExtra("shuffle", shuffle);
		intent.putExtra("urls", urls);
		intent.putExtra("titles", titles);
		intent.putExtra("artists", artists);
		intent.putExtra("time", time / 1000);
		intent.putExtra("index", playlistIndex);
		intent.putExtra("savedState", true);
		PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, 0);
		return new Notification.Action.Builder(icon, title, pendingIntent).build();
	}
	
	private void buildNotification(final int icon, final String title, final String intentAction) 
	{
		playAction = intentAction;
		
		Notification.Action action = generateAction(icon, title, intentAction);
		Notification.MediaStyle style = new Notification.MediaStyle();
		
		Intent intent = new Intent(this, BGMService.class);
		intent.setAction(ACTION_OPEN);
		
		Notification.Builder builder = new Notification.Builder(this)
            .setSmallIcon(R.drawable.launcher)
            .setContentTitle(Html.fromHtml(titles[playlistIndex]))
            .setContentText(Html.fromHtml(artists[playlistIndex]) + (fallbackPlayer ? " (WebView)" : ""))
			.setPriority(Notification.PRIORITY_HIGH)
			.setContentIntent(PendingIntent.getService(this, 0, intent, 0))
            .setStyle(style)
			.setShowWhen(false);

		if (urls.length > 1 || shuffle)
		{
			builder.addAction(generateAction(R.drawable.skip_previous, "Skip to previous", ACTION_SKIP_PREVIOUS));
			builder.addAction(action);
			builder.addAction(generateAction(R.drawable.skip_next, "Skip to next", ACTION_SKIP_NEXT));
		}
		else
		{
			builder.addAction(generateAction(R.drawable.rewind, "Rewind", ACTION_REWIND));
			builder.addAction(action);
			builder.addAction(generateAction(R.drawable.fast_forward, "Fast Forward", ACTION_FAST_FORWARD));
		}
	
		builder.addAction(generateAction(R.drawable.stop, "Stop", ACTION_STOP));
		
		if (session != null)
			style.setMediaSession(session.getSessionToken());
			
		style.setShowActionsInCompactView(1);
		
		Notification notification = builder.build();
		
		final PendingIntent preferenceIntent = PendingIntent.getActivity(this, 0, new Intent(this, PreferenceActivity.class), 0);
		
		notification.contentView.setOnClickPendingIntent(android.R.id.icon, preferenceIntent);
		notification.bigContentView.setOnClickPendingIntent(android.R.id.icon, preferenceIntent);
		
		notificationManager.notify(NOTIFICATION_ID, notification);
		startForeground(NOTIFICATION_ID, notification);
		
		if (button_play != null)
			button_play.setImageResource(icon);
			
		if (text_title != null)
		{
			text_title.setText(Html.fromHtml(titles[playlistIndex]));
			text_artist.setText(Html.fromHtml(artists[playlistIndex]));
		}
	}
			
	public void handleIntent(Intent intent)
	{
		if (intent == null || intent.getAction() == null)
			return;

		notificationManager.cancel(NOTIFICATION_ID + 1);
		
		if (controller == null)
			return;
		
		String action = intent.getAction();

		if (action.equalsIgnoreCase(ACTION_PLAY))
			controller.getTransportControls().play();
			
		else if (action.equalsIgnoreCase(ACTION_PAUSE))
			controller.getTransportControls().pause();
			
		else if (action.equalsIgnoreCase(ACTION_STOP))
			controller.getTransportControls().stop();
			
		else if (action.equalsIgnoreCase(ACTION_FAST_FORWARD))
			controller.getTransportControls().fastForward();
			
		else if (action.equalsIgnoreCase(ACTION_REWIND))
			controller.getTransportControls().rewind();
			
		else if (action.equalsIgnoreCase(ACTION_SKIP_NEXT))
			controller.getTransportControls().skipToNext();

		else if (action.equalsIgnoreCase(ACTION_SKIP_PREVIOUS))
			controller.getTransportControls().skipToPrevious();
			
		else if (action.equalsIgnoreCase(ACTION_OPEN))
		{
			Intent intent1 = new Intent(this, MainActivity.class);
			intent1.setAction(Intent.ACTION_SEND);
			intent1.putExtra(Intent.EXTRA_TEXT, urls[playlistIndex] + (player != null ? (urls[playlistIndex].contains("?") ? "&t=" : "?t=") + StringUtils.toUrlTime((player.getCurrentPosition()) / 1000) : ""));
			intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent1);
		}
		else if (action.equalsIgnoreCase(ACTION_VIDEO))
			initViews();
			
		else if (action.equalsIgnoreCase(ACTION_VISUALIZER))
			initVisualizerView();
			
		else if (action.equalsIgnoreCase(ACTION_VISUALIZER_UNPIN))
		{
			if (view_visualizer != null)
			{
				LayoutParams paramsF = (LayoutParams) view_visualizer.getLayoutParams();
				paramsF.flags &= ~LayoutParams.FLAG_NOT_TOUCHABLE;
				windowManager.updateViewLayout(view_visualizer, paramsF);
				
				notificationManager.cancel(NOTIFICATION_ID + 3);
			}
		}
	}
	
	@Override
	public IBinder onBind(Intent p1)
	{
		return null;
	}

	@Override
	public void onDestroy()
	{
		notificationManager.cancel(NOTIFICATION_ID + 1);
		notificationManager.cancel(NOTIFICATION_ID + 2);
		notificationManager.cancel(NOTIFICATION_ID + 3);
		
		releaseMediaSession();
		releaseViews();
		releaseVisualizerViews();
		releaseFallback();
		
		if (session != null)
			session.release();
			
		if (nextSongTask != null)
			nextSongTask.cancel(true);
			
		if (wifiLock.isHeld())
			wifiLock.release();
		
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		
		super.onDestroy();
	}
	

	@Override
	public boolean onError(MediaPlayer p1, int p2, int p3)
	{
		System.err.println("Media player error: " + p2 + " " + p3 + "\nURL=" + url);
		buildNotification(R.drawable.alert, "Error", ACTION_PLAY);
		boolean nico = NicoHelper.isValidUrl(Uri.parse(urls[playlistIndex]));
		
		if (wifiLock.isHeld())
			wifiLock.release();
			
		time = player.getCurrentPosition();
		releaseMediaSession();
		url = nico ? "" : tries == 2 ? "" : streams.get(((tries + 1) % 2) % streams.size()).url;
		
		//if (!wasPlaying)
			//return true;
		
		if (!nico && tries >= 3 || tries >= 6)
		{
			Notification.Builder builder = new Notification.Builder(BGMService.this)
				.setSmallIcon(R.drawable.alert)
				.setContentText(Html.fromHtml(titles[playlistIndex]))
				.setContentTitle("An error occured");
		
			notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
			
			if (!nico)
				fallbackPlayer = true;
		}
			
		if (tries < (nico ? 6 : 4))
		{
			tries++;
			controller.getTransportControls().play();
		}
		else
		{
			tries = 0;
			controller.getTransportControls().skipToNext();
			Toast.makeText(BGMService.this, "BGM skipped a song, because an error occured :/", Toast.LENGTH_SHORT).show();
		}
			
		return true;
	}

	@Override
	public void onCompletion(MediaPlayer p1)
	{
		controller.getTransportControls().skipToNext();
	}
	
	@Override
	public void onPrepared(MediaPlayer p1)
	{
		preparing = false;
		wasPlaying = true;
		
		controller.getTransportControls().play();
		tries = 0;
		
		if (NicoHelper.isValidUrl(Uri.parse(urls[playlistIndex])))
		{
			player.seekTo(Math.min(50, player.getDuration() - 1));
			removeStuttering = true;
		}
		else if (time > 0)
		{
			player.seekTo(time);
			time = -1;
		}
		else
			player.seekTo(0);
		
		if (view_progress != null)
			view_progress.setVisibility(View.INVISIBLE);
			
		if (view_seek != null)
			view_seek.setMax(player.getDuration() / 1000);
	}

	@Override
	public void onSeekComplete(MediaPlayer p1)
	{
		if (removeStuttering)
		{
			removeStuttering = false;
			if (time > 0)
			{
				player.seekTo(time);
				time = -1;
			}
			else
				player.seekTo(0);
		}
		else if (view_seek != null && player != null && !preparing)
		{
			view_seek.setSecondaryProgress(player.getCurrentPosition() / 1000);
			view_seek.setProgress(player.getCurrentPosition() / 1000);
		}
	}
	
	@Override
	public void onVideoSizeChanged(MediaPlayer p1, int width, int height)
	{
		if (view != null)
		{
			border.aspectRatio = (float) width / height;
			
			final LayoutParams paramsF = (LayoutParams) view.getLayoutParams();
			final LayoutParams paramsB = (LayoutParams) border.getLayoutParams();
			
			paramsF.height = (int) (paramsF.width / border.aspectRatio);
			windowManager.updateViewLayout(view, paramsF);
			
			if (paramsB != null && border.getVisibility() == View.VISIBLE && border.view == view)
			{
				paramsB.height = paramsF.height + border.padding * 2;
				windowManager.updateViewLayout(border, paramsB);
			}
			
			onViewLayoutChanged.run();
		}
	}

	@Override
	public void onAudioFocusChange(int focusChange)
	{
		if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("audioFocus", true))
			return;
		
		//wasPlaying = false;
		
		switch (focusChange)
		{
			case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
			case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
			case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
			case AudioManager.AUDIOFOCUS_GAIN:
				if (player == null)
					initMediaSession();
					
				else if (!preparing && !player.isPlaying() && wasPlaying)
					controller.getTransportControls().play();
					
				if (player != null)
				{
					float volume = PreferenceManager.getDefaultSharedPreferences(this).getFloat("volume", 1);
					player.setVolume(volume, volume);
				}
				
				wasPlaying = true;
				
				break;

			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			case AudioManager.AUDIOFOCUS_LOSS:
				if (!preparing && player != null)
				{
					wasPlaying = player.isPlaying();
					
					if (player.isPlaying())
						controller.getTransportControls().pause();
				}
				break;

			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				if (!preparing && player != null)
				{
					if (player.isPlaying())
					{
						float volume = PreferenceManager.getDefaultSharedPreferences(this).getFloat("volume", 1);
						player.setVolume(volume * 0.1F, volume * 0.1F);
					}

					wasPlaying = player.isPlaying();
				}
				
				break;
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences p1, String key)
	{
		if (key.equals("volume") && !preparing && player != null && player.isPlaying())
		{
			float volume = PreferenceManager.getDefaultSharedPreferences(this).getFloat("volume", 1);
			player.setVolume(volume, volume);
		}
		else if (key.equals("video"))
			initViews();
			
		else if (key.equals("speed") && !preparing && player != null && player.isPlaying())
			player.setPlaybackParams(player.getPlaybackParams().setSpeed(PreferenceManager.getDefaultSharedPreferences(BGMService.this).getFloat("speed", 1)));
		
		else if (key.equals("visualizer"))
			initVisualizerView();
	}
	
	public class NextSongTask extends AsyncTask<String, Void, String>
	{
		@Override
		protected String doInBackground(String[] p1)
		{
			if (!wifiLock.isHeld())
				wifiLock.acquire();
				
			try
			{
				while (!NetworkHelper.isInternetAvailable(BGMService.this))
				{
					Thread.sleep(1000);
					
					if (isCancelled())
						return null;
				}
				
				Uri parsed = Uri.parse(p1[0]);
				
				if (NicoHelper.isValidUrl(parsed))
				{
					String id = NicoHelper.getId(parsed);
					
					if (!NicoHelper.LOGIN)
						NicoHelper.login(BGMService.this);
					
					String watchApiURL = NetworkHelper.getSync(true, null, "http://api.gadget.nicovideo.jp/v2.0/video/videos/" + id + "/play?playModeCode=auto", "User-Agent", NicoHelper.USER_AGENT);
					
					if (watchApiURL == null)
						return null;
						
					String result = StringUtils.unescapeJava(new JSONObject(NetworkHelper.getSync(true, null,  new JSONObject(watchApiURL).getString("watchApiUrl"), "User-Agent", NicoHelper.USER_AGENT)).getJSONObject("data").getString("audio_url"));
					parsed = Uri.parse(result);
					artists[playlistIndex] = parsed.getQueryParameter("artist");
					titles[playlistIndex] = parsed.getQueryParameter("title");
					
					fallbackPlayer = false;
					
					return result;
				}
				
				String page = NetworkHelper.getPage(p1[0]);
				
				if (isCancelled())
					return null;
				
				int index0, index1, index2;
				
				index0 = page.indexOf("class=\"video-list-item related-list-item show-video-time\"");
				index1 = index0 == -1 ? -1 : page.indexOf("href=\"", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 6);
				
				if (index2 != -1)
					nextUrl = "https://www.youtube.com" + page.substring(index1 + 6, index2);
				else
					nextUrl = url;
					
				index0 = page.indexOf("class=\"yt-user-info\"");
				index1 = index0 == -1 ? -1 : page.indexOf(">", index0 + 35);
				index2 = index1 == -1 ? -1 : page.indexOf("</a>", index1);

				if (index2 != -1)
					artists[playlistIndex] = page.substring(index1 + 1, index2);

				index0 = page.indexOf("id=\"eow-title\"");
				index1 = index0 == -1 ? -1 : page.indexOf(">", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("</span>", index1);

				if (index2 != -1)
					titles[playlistIndex] = page.substring(index1 + 1, index2);
				
					/*
				index0 = page.indexOf("quality=" + PreferenceManager.getDefaultSharedPreferences(BGMService.this).getString("quality", "high"));
				index0 = index0 == -1 ? page.indexOf("quality=") : index0;
				index1 = index0 == -1 ? -1 : page.indexOf("url=", index0);
				index2 = index1 == -1 ? -1 : page.indexOf(",", index1);
				index0 = index1 == -1 ? -1 : page.indexOf("\\u0026", index1);
				index2 = index2 == -1 ? index0 : index0 == -1 ? index2 : Math.min(index0, index2);
				
				String url1 = URLDecoder.decode(page.substring(index1 + 4, index2)).toString();
				
				if (index2 == -1)
					return null;
				
				index0 = page.indexOf("quality_label=");
				index0 = index0 == -1 ? page.indexOf("quality=medium") : index0;
				index1 = index0 == -1 ? -1 : page.indexOf("url=", index0);
				index2 = index1 == -1 ? -1 : page.indexOf(",", index1);
				index0 = index1 == -1 ? -1 : page.indexOf("\\u0026", index1);
				index2 = index2 == -1 ? index0 : index0 == -1 ? index2 : Math.min(index0, index2);

				String url2 = URLDecoder.decode(page.substring(index1 + 4, index2)).toString();
				
				*/
				
				streams.clear();
				String quality = PreferenceManager.getDefaultSharedPreferences(BGMService.this).getString("quality", "high");
				if (quality.equals("Low"))
					preferedQuality = 1;
					
				else if (quality.equals("Medium"))
					preferedQuality = 2;
					
				else if (quality.equals("High"))
					preferedQuality = 3;
					
				else 
					preferedQuality = 0;
					
				index0 = page.indexOf("\"url_encoded_fmt_stream_map\"");
				String map1 = URLDecoder.decode(page.substring(index0 + 30, page.indexOf("\"", index0 + 30)));
				String[] map = map1.split(",");
				
				for (int i = 1; i <= map.length; ++i)
				{
					if (!map[i - 1].contains("url=") || i < map.length && !map[i].contains("url="))
					{
						streams.add(new Stream(map[i - 1] + "," + map[i]));
						i++;
					}
					else
						streams.add(new Stream(map[i - 1]));
				}
				
				index0 = page.indexOf("\"adaptive_fmts\"");
				String map2 = URLDecoder.decode(page.substring(index0 + 17, page.indexOf("\"", index0 + 17)));
				
				map = map2.split(",");

				for (int i = 1; i <= map.length; ++i)
				{
					if (i < map.length && (!map[i - 1].contains("url=") || !map[i].contains("url=")))
					{
						streams.add(new Stream(map[i - 1] + "," + map[i]));
						i++;
					}
					else
						streams.add(new Stream(map[i - 1]));
				}
				
				/*
				OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(Environment.getExternalStorageDirectory() + "/bgm_debug.txt"));
				w.write(p1[0] + " - " + titles[playlistIndex] + "\n\n" + "\n\nMaps:\n");
				
				w.write(map1.replace("\\u0026", "\n").replace(",", "\n\n"));
				w.write("\n\n");
				w.write(map2.replace("\\u0026", "\n").replace(",", "\n\n"));				w.write("\n\nFull:\n");
				w.write(page);
				w.flush();
				w.close();
				*/
				
				Collections.sort(streams);
				
				//for (Stream s : streams)
					//System.out.println(s);
				
				NetworkHelper.getSync(true, new NetworkHelper.NetworkListener()
				{
					@Override
					public void onServerRequestComplete(String response)
					{
						fallbackPlayer = false;
					}

					@Override
					public void onErrorOccurred(String errorMessage)
					{
						fallbackPlayer = true;
					}
				}, streams.get(0).url);
					
				return streams.get(0).url;
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(String result)
		{
			if (isCancelled())
				return;
				
			if (result != null)
			{
				url = result;
				initMediaSession();
			}
			else
			{
				System.err.println("Couldn't find next song");
				Toast.makeText(BGMService.this, "BGM skipped a song, because it wasn't available :/", Toast.LENGTH_SHORT).show();
				
				if (controller != null)
					controller.getTransportControls().skipToNext();
			}
		}
	}
	
	int preferedQuality = 3;
	
	private class Stream implements Comparable<Stream>
	{
		@Override
		public int compareTo(BGMService.Stream p1)
		{
			if (this.qualityInt == preferedQuality && p1.qualityInt != preferedQuality)
				return -1;
			else if (this.qualityInt != preferedQuality && p1.qualityInt == preferedQuality)
				return 1;
				
			if (this.qualityInt != p1.qualityInt)
				return (int) -Math.signum(this.qualityInt - p1.qualityInt);
				
			if (this.hasCodec && !p1.hasCodec)
				return -1;
			else if (!this.hasCodec && p1.hasCodec)
				return 1;
				
			return 0;
		}
		
		public String url;
		private String quality = "?", type;
		private boolean hasCodec;
		private int qualityInt;
		
		public Stream(String s)
		{
			String[] args = s.split("\\\\u0026");
			for (String arg : args)
			{
				if (arg.startsWith("url="))
					this.url = arg.substring(4);

				else if (arg.startsWith("type="))
				{
					this.type = arg.substring(5);
					this.hasCodec = this.type.contains("codec");
				}

				else if (arg.startsWith("quality="))
				{
					this.quality = arg.substring(8);
				}
				else if (arg.startsWith("quality_label="))
					this.quality = arg.substring(14);

			}

			if (this.url == null)
			{
				System.err.println("No url: " + s);
				this.url = "";
				this.qualityInt = -1;
			}
			
			if (this.quality.equals("small") || this.quality.equals("144p") || this.quality.equals("240p"))
				qualityInt = 1;

			else if (this.quality.equals("medium") || this.quality.equals("360p") || this.quality.equals("480p"))
				qualityInt = 2;

			else if (this.quality.equals("hd720") || this.quality.equals("720p") || this.quality.equals("1080p"))
				qualityInt = 3;
				
			else
				qualityInt = 0;
		}

		@Override
		public String toString()
		{
			return "Stream: " + quality + "; " + type + "; url=" + url;
		}
	}
}

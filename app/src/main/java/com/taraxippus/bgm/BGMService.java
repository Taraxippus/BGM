package com.taraxippus.bgm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Rating;
import android.media.audiofx.Visualizer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.net.wifi.WifiManager;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.taraxippus.bgm.StringUtils;
import com.taraxippus.bgm.gl.ConfigChooser;
import com.taraxippus.bgm.gl.GLRenderer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.Random;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

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
	
	final Random random = new Random();
	
	String url, nextUrl;
	
	boolean repeat;
	boolean shuffle;
	
	int playlistIndex;
	int time = -1;
	String[] urls;
	String[] titles;
	String[] artists;
	NextSongTask nextSongTask;
	
	boolean wasPlaying;
	
	Visualizer visualizer;
	MediaPlayer player;
	MediaSession session;
	MediaController controller;
	
	WifiManager.WifiLock wifiLock;
	private static boolean preparing = true;
	
	private WindowManager windowManager;
	private GLRenderer view_renderer;
	private SurfaceView view_video;
	private View view, view_progress, view_visualizer;
	private ImageView button_play;
	private SeekBar view_seek;
	private FloatingWidgetBorder border;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (wifiLock == null)
			wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, WIFI_LOCK);
		
		if (intent != null && intent.hasExtra("urls"))
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
				time = intent.getIntExtra("time", -1) * 1000;
				playlistIndex = intent.getIntExtra("index", 0);
				
				buildNotification(R.drawable.load, "Loading", ACTION_PLAY);
				
				if (nextSongTask != null)
					nextSongTask.cancel(true);

				nextSongTask = new NextSongTask();
				nextSongTask.execute(urls[playlistIndex]);
			}
			
			initViews();
			initVisualizerView();
		}
		else if (url == null || url.isEmpty())
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
				((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
					.cancel(NOTIFICATION_ID + 3);
					
				windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
				
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

				final LayoutParams paramsF2 = new WindowManager.LayoutParams(
					300,
					300,
					LayoutParams.TYPE_SYSTEM_ALERT,
					LayoutParams.FLAG_LAYOUT_NO_LIMITS | LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_HARDWARE_ACCELERATED,
					PixelFormat.TRANSLUCENT);
					
				paramsF2.y = -300;
				paramsF2.gravity = Gravity.CENTER;
				windowManager.addView(view_visualizer, paramsF2);
				
				button_close.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							releaseVisualizerViews();
							
							Intent intent = new Intent(getApplicationContext(), BGMService.class);
							intent.setAction(ACTION_VISUALIZER);

							Notification.Builder builder = new Notification.Builder(BGMService.this)
								.setSmallIcon(R.drawable.launcher)
								.setContentText("Click to show again")
								.setContentTitle("Closed visualizer")
								.setContentIntent(PendingIntent.getService(getApplicationContext(), 0, intent, 0));

							((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
								.notify(NOTIFICATION_ID + 3, builder.build());
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
							
							Intent intent = new Intent(getApplicationContext(), BGMService.class);
							intent.setAction(ACTION_VISUALIZER_UNPIN);

							Notification.Builder builder = new Notification.Builder(BGMService.this)
								.setSmallIcon(R.drawable.launcher)
								.setContentText("Click to unpin")
								.setContentTitle("Pinned visualizer")
								.setContentIntent(PendingIntent.getService(getApplicationContext(), 0, intent, 0));

							((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
								.notify(NOTIFICATION_ID + 3, builder.build());
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

			if (view == null)
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

			if (border.getVisibility() == View.VISIBLE && border.view == view)
				windowManager.removeView(border);
				
			if (view_visualizer == null)
				border = null;
		}
	}
	
	public void initViews()
	{
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("video", false))
		{
			if (view == null)
			{
				((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
					.cancel(NOTIFICATION_ID + 2);
					
				windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
				
				view = LayoutInflater.from(this).inflate(R.layout.video, null);
				view_video = (SurfaceView) view.findViewById(R.id.video);
				view_progress = view.findViewById(R.id.progress_video);
				final View layout_controls = view.findViewById(R.id.layout_controls);
				final View button_close = view.findViewById(R.id.button_close);
				button_play = (ImageView) view.findViewById(R.id.button_play);
				final TextView text_time = (TextView) view.findViewById(R.id.text_time);
				view_seek = (SeekBar) view.findViewById(R.id.seek);
				
				if (border == null)
				{
					border = new FloatingWidgetBorder(this);
					border.setVisibility(View.INVISIBLE);
				}
					
				final LayoutParams paramsF1 = new WindowManager.LayoutParams(
					400,
					300,
					LayoutParams.TYPE_SYSTEM_ALERT,
					LayoutParams.FLAG_LAYOUT_NO_LIMITS | LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_HARDWARE_ACCELERATED,
					PixelFormat.TRANSLUCENT);

				paramsF1.gravity = Gravity.CENTER;
				windowManager.addView(view, paramsF1);
				
				if (player != null && !preparing)
					view_progress.setVisibility(View.GONE);
				
				final Drawable thumb = view_seek.getThumb();
				final Drawable thumb_hidden = new ColorDrawable(Color.TRANSPARENT);
				view_seek.setThumb(thumb_hidden);
				
				final Handler handler = new Handler();
				new Runnable()
					{
						@Override
						public void run()
						{
							if (player != null && view_seek.getThumb() == thumb_hidden)
								view_seek.setProgress(player.getCurrentPosition() / 1000);

							if (view != null)
								handler.postDelayed(this, 1000);
						}
					}.run();
	
				
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
						}

						@Override
						public void onStopTrackingTouch(SeekBar p1)
						{
							view_seek.setThumb(thumb_hidden);
							
							if (player != null)
								player.seekTo(view_seek.getProgress() * 1000);
						}
					});
					
				button_play.setImageResource(player != null && !preparing && player.isPlaying() ? R.drawable.pause : R.drawable.play);
				button_play.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							Intent intent = new Intent(getApplicationContext(), BGMService.class);
							intent.setAction(player != null && !preparing && player.isPlaying() ? ACTION_PAUSE : ACTION_PLAY);
							handleIntent(intent);
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
							
							Intent intent = new Intent(getApplicationContext(), BGMService.class);
							intent.setAction(ACTION_VIDEO);
							
							Notification.Builder builder = new Notification.Builder(BGMService.this)
								.setSmallIcon(R.drawable.launcher)
								.setContentText("Click to show again")
								.setContentTitle("Closed video player")
								.setContentIntent(PendingIntent.getService(getApplicationContext(), 0, intent, 0));

							((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
								.notify(NOTIFICATION_ID + 2, builder.build());
						}		
				});
				
				view.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							layout_controls.setVisibility(View.VISIBLE);
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
							border.show(paramsF1, view, true);
							return true;
						}
					};
				view.setOnLongClickListener(showBorder);
				layout_controls.setOnLongClickListener(showBorder);
			}
		}
		else 
			releaseViews();
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
	}

	public void initMediaSession()
	{
		if (url == null)
			return;
		
		if (player != null)
			releaseMediaSession();
		
		try
		{
			player = new MediaPlayer();
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			player.setDataSource(url);
			player.setOnPreparedListener(this);
			player.setOnCompletionListener(this);
			player.setOnErrorListener(this);
			player.setOnVideoSizeChangedListener(this);
			player.setOnSeekCompleteListener(this);
			player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
			player.setLooping(urls.length == 0 && repeat);

			if (view != null && view_video.getHolder().getSurface() != null && view_video.getHolder().getSurface().isValid())
				player.setDisplay(view_video.getHolder());
				
			if (view_visualizer != null)
				initVisualizer();
		}
		catch (Exception e)
		{
			System.out.println("Something is wrong with url: " + url);
			e.printStackTrace();
			stopSelf();
			
			return;
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
						if (urls.length > 1 || shuffle)
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
									if (repeat)
										playlistIndex = 0;
									else
									{
										if (player != null && !preparing)
										{
											player.seekTo(0);
											controller.getTransportControls().pause();
										}

										return;
									}
								else
									playlistIndex++;
								
							}
						
							if (nextSongTask != null)
								nextSongTask.cancel(true);

							nextSongTask = new NextSongTask();
							nextSongTask.execute(urls[playlistIndex]);
						}
						else if (!preparing && player != null && player.isPlaying())
						{
							player.seekTo(0);
								
							controller.getTransportControls().pause();
						}
						else if (!preparing)
						{
							if (wifiLock.isHeld())
								wifiLock.release();
							
							buildNotification(R.drawable.play, "Play", ACTION_PLAY);
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

							if (nextSongTask != null)
								nextSongTask.cancel(true);

							nextSongTask = new NextSongTask();
							nextSongTask.execute(urls[playlistIndex]);
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
		player.prepareAsync();
		preparing = true;
		
		if (view_progress != null)
			view_progress.setVisibility(View.VISIBLE);
	}
	
	private Notification.Action generateAction(int icon, String title, String intentAction) 
	{
		Intent intent = new Intent(getApplicationContext(), BGMService.class);
		intent.setAction(intentAction);
		PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, 0);
		return new Notification.Action.Builder(icon, title, pendingIntent).build();
	}
	
	private void buildNotification(final int icon, final String title, final String intentAction) 
	{
		Notification.Action action = generateAction(icon, title, intentAction);
		Notification.MediaStyle style = new Notification.MediaStyle();
		
		Intent intent = new Intent(getApplicationContext(), BGMService.class);
		intent.setAction(ACTION_OPEN);
		
		Notification.Builder builder = new Notification.Builder(this)
            .setSmallIcon(R.drawable.launcher)
            .setContentTitle(Html.fromHtml(titles[playlistIndex]))
            .setContentText(Html.fromHtml(artists[playlistIndex]))
			.setContentIntent(PendingIntent.getService(getApplicationContext(), 0, intent, 0))
            .setStyle(style)
			.setShowWhen(false);

		if (urls.length > 1 || shuffle)
		{
			builder.addAction(generateAction(R.drawable.skip_previous, "Skip to previous", ACTION_SKIP_PREVIOUS));
			builder.addAction(action);
			builder.addAction(generateAction(R.drawable.skip_next, "Skip to next", ACTION_SKIP_NEXT));
			builder.addAction(generateAction(R.drawable.stop, "Stop", ACTION_STOP));
		}
		else
		{
			builder.addAction(generateAction(R.drawable.rewind, "Rewind", ACTION_REWIND));
			builder.addAction(action);
			builder.addAction(generateAction(R.drawable.fast_forward, "Fast Forward", ACTION_FAST_FORWARD));
			builder.addAction(generateAction(R.drawable.stop, "Stop", ACTION_STOP));
		}
	
		style.setShowActionsInCompactView(1);
		
		Notification notification = builder.build();
		
		((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
			.notify(NOTIFICATION_ID, notification);
		startForeground(NOTIFICATION_ID, notification);
		
		if (button_play != null)
		{
			button_play.setImageResource(icon);
			button_play.setOnClickListener(new View.OnClickListener()
			{
					@Override
					public void onClick(View p1)
					{
						Intent intent = new Intent(getApplicationContext(), BGMService.class);
						intent.setAction(intentAction);
						handleIntent(intent);
					}
			});
		}
	}
			
	public void handleIntent(Intent intent)
	{
		if (intent == null || intent.getAction() == null)
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
			intent1.putExtra(Intent.EXTRA_TEXT, urls[playlistIndex] + (player != null ? "&t=" + StringUtils.toUrlTime((player.getCurrentPosition()) / 1000) : ""));
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
				
				((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
					.cancel(NOTIFICATION_ID + 3);
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
		releaseMediaSession();
		releaseViews();
		releaseVisualizerViews();
		
		if (session != null)
			session.release();
			
		if (nextSongTask != null)
			nextSongTask.cancel(true);
			
		if (wifiLock.isHeld())
			wifiLock.release();
		
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
			
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(NOTIFICATION_ID);
		nm.cancel(NOTIFICATION_ID + 1);
		nm.cancel(NOTIFICATION_ID + 2);
		nm.cancel(NOTIFICATION_ID + 3);
		
		super.onDestroy();
	}
	

	@Override
	public boolean onError(MediaPlayer p1, int p2, int p3)
	{
		System.err.println("Media player error: " + p2 + " " + p3 + "\nURL=" + url);
		buildNotification(R.drawable.alert, "Error", ACTION_PLAY);
		
		if (wifiLock.isHeld())
			wifiLock.release();
		releaseMediaSession();
		
		Notification.Builder builder = new Notification.Builder(BGMService.this)
			.setSmallIcon(R.drawable.launcher)
			.setContentText(titles[playlistIndex])
			.setContentTitle("An error occured");

		((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
			.notify(NOTIFICATION_ID + 1, builder.build());
		
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
		
		controller.getTransportControls().play();
		if (time > 0)
		{
			if (urls.length == 1)
			{
				player.seekTo(time);
			}
				
			time = -1;
		}
		
		if (view_progress != null)
			view_progress.setVisibility(View.INVISIBLE);
			
		if (view_seek != null)
			view_seek.setMax(player.getDuration() / 1000);
	}

	@Override
	public void onSeekComplete(MediaPlayer p1)
	{
		if (view_seek != null)
			view_seek.setProgress(player.getCurrentPosition() / 1000);
	}
	
	@Override
	public void onVideoSizeChanged(MediaPlayer p1, int width, int height)
	{
		border.aspectRatio = (float) width / height;
		
		if (view != null)
		{
			final LayoutParams paramsF = (LayoutParams) view.getLayoutParams();
			final LayoutParams paramsB = (LayoutParams) border.getLayoutParams();
			
			paramsF.height = (int) (paramsF.width / border.aspectRatio);
			windowManager.updateViewLayout(view, paramsF);
			
			if (paramsB != null && border.getVisibility() == View.VISIBLE && border.view == view)
			{
				paramsB.height = paramsF.height + border.padding * 2;
				windowManager.updateViewLayout(border, paramsB);
			}
		}
	}

	@Override
	public void onAudioFocusChange(int focusChange)
	{
		if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("audioFocus", true))
			return;
		
		switch (focusChange)
		{
			case AudioManager.AUDIOFOCUS_GAIN:
				if (player == null)
					initMediaSession();
					
				else if (!preparing && !player.isPlaying() && wasPlaying)
					controller.getTransportControls().play();
					
				float volume = PreferenceManager.getDefaultSharedPreferences(this).getFloat("volume", 1);
				player.setVolume(volume, volume);
				break;

			case AudioManager.AUDIOFOCUS_LOSS:
				if (!preparing && player != null)
				{
					wasPlaying = player.isPlaying();
					
					if (player.isPlaying())
						controller.getTransportControls().pause();
				}
				break;

			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
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
						volume = PreferenceManager.getDefaultSharedPreferences(this).getFloat("volume", 1);
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
				HttpClient httpclient = new DefaultHttpClient(); 
				HttpGet httpget = new HttpGet(p1[0]);
				HttpResponse response = httpclient.execute(httpget); 
				HttpEntity entity = response.getEntity();

				if (isCancelled())
					return null;
					
				BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));

				StringBuilder sb = new StringBuilder();

				String line;
				while ((line = reader.readLine()) != null)
				{
					if (isCancelled())
						return null;
					
					sb.append(line);
				}

				reader.close();

				String page = sb.toString();
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
				
				index0 = page.indexOf("quality=" + PreferenceManager.getDefaultSharedPreferences(BGMService.this).getString("quality", "High").toLowerCase());
				index0 = index0 == -1 ? page.indexOf("quality=") : index0;
				index1 = index0 == -1 ? -1 : page.indexOf("url=", index0);
				index2 = index1 == -1 ? -1 : page.indexOf(",", index1);
				index0 = index1 == -1 ? -1 : page.indexOf("\\u0026", index1);
				index2 = index2 == -1 ? index0 : index0 == -1 ? index2 : Math.min(index0, index2);
				
				if (index2 != -1)
				{
					return URLDecoder.decode(page.substring(index1 + 4, index2)).toString();
				}
				
				return null;
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
				controller.getTransportControls().skipToNext();
			}
		}
	}
}

package com.taraxippus.bgm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.Random;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class BGMService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener
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
	
	final Random random = new Random();
	
	String url;
	String artist, title;
	
	boolean loop;
	boolean shuffle;
	
	boolean playlist;
	int playlistIndex;
	String[] urls;
	NextSongTask nextSongTask;
	
	boolean wasPlaying;
	
	MediaPlayer player;
	MediaSession session;
	MediaController controller;
	
	WifiManager.WifiLock wifiLock;
	
	private static boolean preparing = true;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null && intent.hasExtra("url"))
		{
			releaseMediaSession();
			
			url = intent.getStringExtra("url");
			artist = intent.getStringExtra("artist");
			title = intent.getStringExtra("title");
			loop = intent.getBooleanExtra("loop", true);
			shuffle = intent.getBooleanExtra("shuffle", false);
			playlist = intent.getBooleanExtra("playlist", false);
			urls = intent.getStringArrayExtra("urls");
			
			if (playlist && shuffle)
				playlistIndex = random.nextInt(urls.length);
			else
				playlistIndex = 0;
		}
			
		if (player == null)
			initMediaSession();
		
		if (intent != null && controller != null)
			handleIntent(intent);
		
		return super.onStartCommand(intent, flags, startId);
	}
	
	public void releaseMediaSession()
	{
		if (player != null)
		{
			if (!preparing && player.isPlaying())
				player.stop();

			player.release();
			player = null;
			preparing = true;
		}
	}

	public void initMediaSession()
	{
		try
		{
			player = new MediaPlayer();
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			player.setDataSource(url);
			player.setOnPreparedListener(this);
			player.setOnCompletionListener(this);
			player.setOnErrorListener(this);
			player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
			player.setLooping(!playlist && loop);
		}
		catch (Exception e)
		{
			System.out.println("Something is wrong with url: " + url);
			e.printStackTrace();
			stopSelf();
			
			return;
		}
		
		if (wifiLock == null)
		{
			wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, WIFI_LOCK);

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

						if (!preparing && !player.isPlaying())
						{
							AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
							int result = audioManager.requestAudioFocus(BGMService.this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

							if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
							{
								player.start();
								wifiLock.acquire();
								
								buildNotification(generateAction(R.drawable.pause, "Pause", ACTION_PAUSE));
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
							wifiLock.release();
							
							buildNotification(generateAction(R.drawable.play, "Play", ACTION_PLAY));
						}
					}

					@Override
					public void onSkipToNext() 
					{
						if (playlist)
						{
							if (shuffle)
								playlistIndex = random.nextInt(urls.length);

							else if (playlistIndex + 1 == urls.length)
								if (loop)
									playlistIndex = 0;
								else
								{
									player.seekTo(0);
									controller.getTransportControls().pause();
									return;
								}
							else
								playlistIndex++;

							releaseMediaSession();
							
							if (nextSongTask != null)
								nextSongTask.cancel(true);
							
							nextSongTask = new NextSongTask();
							nextSongTask.execute(urls[playlistIndex]);
						}
						else
						{
							if (shuffle)
							{
								;
							}
							else
							{
								player.seekTo(0);
								controller.getTransportControls().pause();
							}
						}
					}

					@Override
					public void onSkipToPrevious()
					{
						super.onSkipToPrevious();
						
						if (!preparing && player != null && player.isPlaying())
							player.seekTo(0);
							
						else if (playlist)
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
						if (!preparing && player != null && player.isPlaying())
							player.seekTo(Math.min(player.getDuration() - 1, player.getCurrentPosition() + 10000));
						
						super.onFastForward();
					}

					@Override
					public void onRewind() 
					{
						if (!preparing && player != null && player.isPlaying())
							player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
						
						super.onRewind();
					}

					@Override
					public void onStop()
					{
						super.onStop();

						if (player != null)
							player.stop();

						stopForeground(false);
						NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
						notificationManager.cancel(NOTIFICATION_ID);
						Intent intent = new Intent(getApplicationContext(), BGMService.class);
						stopService(intent);
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
		}
			
		player.prepareAsync();
		preparing = true;
	}
	
	private Notification.Action generateAction(int icon, String title, String intentAction) 
	{
		Intent intent = new Intent(getApplicationContext(), BGMService.class);
		intent.setAction(intentAction);
		PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), NOTIFICATION_ID, intent, 0);
		return new Notification.Action.Builder(icon, title, pendingIntent).build();
	}
	
	private void buildNotification(Notification.Action action)
	{
		Notification.MediaStyle style = new Notification.MediaStyle();

		Intent intent = new Intent(getApplicationContext(), BGMService.class);
		intent.setAction(ACTION_STOP);
		
		PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), NOTIFICATION_ID, intent, 0);
		Notification.Builder builder = new Notification.Builder(this)
            .setSmallIcon(R.drawable.launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setDeleteIntent(pendingIntent)
            .setStyle(style)
			//.setColor(0xFFE65100)
			.setShowWhen(false);
			
		if (playlist || shuffle)
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
		
		Notification notification = builder.build();
		
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(NOTIFICATION_ID, notification);
		startForeground(NOTIFICATION_ID, notification);
	}
			
	public void handleIntent(Intent intent)
	{
		if (intent == null || intent.getAction() == null)
			return;

		String action = intent.getAction();

		if( action.equalsIgnoreCase(ACTION_PLAY))
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
	}
	
	@Override
	public IBinder onBind(Intent p1)
	{
		return null;
	}

	@Override
	public void onDestroy()
	{
		if (player != null)
			player.release();
			
		if (session != null)
			session.release();
			
		super.onDestroy();
	}
	

	@Override
	public boolean onError(MediaPlayer p1, int p2, int p3)
	{
		System.err.println("Media player error: " + p2 + " " + p3);
		player.reset();
		controller.getTransportControls().pause();
		return false;
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
	}

	@Override
	public void onAudioFocusChange(int focusChange)
	{
		switch (focusChange)
		{
			case AudioManager.AUDIOFOCUS_GAIN:
				if (player == null)
					initMediaSession();
					
				else if (!preparing && !player.isPlaying() && wasPlaying)
					controller.getTransportControls().play();
					
				player.setVolume(1.0f, 1.0f);
				break;

			case AudioManager.AUDIOFOCUS_LOSS:
				if (!preparing)
				{
					wasPlaying = player.isPlaying();
					
					if (player.isPlaying())
						controller.getTransportControls().pause();
				}
				break;

			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
				if (!preparing)
				{
					wasPlaying = player.isPlaying();
					
					if (player.isPlaying()) 
						controller.getTransportControls().pause();
				}
				
				break;

			case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				if (!preparing)
				{
					if (player.isPlaying())
						player.setVolume(0.1f, 0.1f);

					wasPlaying = player.isPlaying();
				}
				
				break;
		}
	}
	
	public class NextSongTask extends AsyncTask<String, Void, String[]>
	{
		@Override
		protected String[] doInBackground(String[] p1)
		{
			try
			{
				String[] result = new String[3];
				
				HttpClient httpclient = new DefaultHttpClient(); 
				HttpGet httpget = new HttpGet(p1[0]);
				HttpResponse response = httpclient.execute(httpget); 
				HttpEntity entity = response.getEntity();

				BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));

				StringBuilder sb = new StringBuilder();

				String line;
				while ((line = reader.readLine()) != null)
				{
					sb.append(line);
				}

				reader.close();

				String page = sb.toString();
				int index0, index1, index2;
				
				index1 = page.indexOf("\"title\":\"");
				index2 = index1 == -1 ? -1 : page.indexOf("\",\"", index1 + 9);

				if (index2 != -1)
					result[0] = page.substring(index1 + 9, index2).replace("\\\"", "\"").replace("\\/", "/");

				index0 = page.indexOf("\"author\"");
				index1 = index0 == -1 ? -1 : page.indexOf("\"", index0 + 8);
				index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 1);

				if (index2 != -1)
					result[1] = page.substring(index1 + 1, index2);

				index1 = page.indexOf("quality=");
				index2 = index1 == -1 ? -1 : page.indexOf(",", index1);
				
				String quality = page.substring(index1 + 8, index2);
				
				index0 = page.indexOf("quality=" + quality);
				index1 = index0 == -1 ? -1 : page.indexOf("url=", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("\\u0026", index1);

				if (index2 != -1)
				{
					result[2] = URLDecoder.decode(page.substring(index1 + 4, index2)).toString();
				}
				
				return result;
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(String[] result)
		{
			if (result != null)
			{
				title = result[0];
				artist = result[1];
				url = result[2];
		
				initMediaSession();
				System.out.println("Playing next song: " + title);
			}
			else
			{
				System.err.println("Couldn't find next song");
				controller.getTransportControls().skipToNext();
			}
		}
	}
}

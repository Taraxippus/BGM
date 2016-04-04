package com.taraxippus.bgm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.taraxippus.bgm.R;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import java.io.InputStream;
import android.app.ActivityGroup;
import android.widget.LinearLayout;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ScrollView;
import android.util.TypedValue;
import android.text.TextUtils;

public class MainActivity extends Activity 
{
	ProgressBar progress;
	ImageView image_video, image_artist, image_menu;
	TextView text_title, text_description;
	Button button_cancel;
	ImageButton button_shuffle, button_play, button_repeat;
	LinearLayout layout_title;
	
	String page;
	String id;
	String streamUrl;
	String title, artist, description;
	Bitmap bitmap_video;
	Bitmap bitmap_artist;
	
	boolean playlist;
	String playlistTitle;
	ArrayList<String> urls = new ArrayList<String>();
	ArrayList<String> titles = new ArrayList<String>();
	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
       	setContentView(R.layout.main);
		
		progress = (ProgressBar) findViewById(R.id.progress);
		image_video = (ImageView) findViewById(R.id.image_video);
		image_artist = (ImageView) findViewById(R.id.image_artist);
		image_menu = (ImageView) findViewById(R.id.image_menu);
		text_title = (TextView) findViewById(R.id.text_title);
		text_description = (TextView) findViewById(R.id.text_description);
		button_cancel = (Button) findViewById(R.id.button_cancel);
		button_shuffle = (ImageButton) findViewById(R.id.button_shuffle);
		button_repeat = (ImageButton) findViewById(R.id.button_repeat);
		button_play = (ImageButton) findViewById(R.id.button_play);
		layout_title = (LinearLayout) findViewById(R.id.layout_title);
		
		text_description.setMovementMethod(LinkMovementMethod.getInstance());
		
		final LinearLayout layout_buttons = (LinearLayout)  findViewById(R.id.layout_buttons);
		final ScrollView scroll = (ScrollView) findViewById(R.id.scroll);
		scroll .setOnScrollChangeListener(new ScrollView.OnScrollChangeListener()
		{
				@Override
				public void onScrollChange(View p1, int p2, int p3, int p4, int p5)
				{
					if (scroll.canScrollVertically(1))
						layout_buttons.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
					else
						layout_buttons.setElevation(0);
				}
		});
		
		button_cancel.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View p1)
				{
					finish();
				}
			});
		
		if (!isInternetAvailable())
		{
			progress.setVisibility(View.GONE);
			
			layout_title.setVisibility(View.VISIBLE);
			image_menu.setVisibility(View.GONE);
			text_title.setText("No connection");
			
			button_cancel.setVisibility(View.VISIBLE);
		}
		else
		{
			button_play.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View p1)
					{
						Intent intent = new Intent(MainActivity.this, BGMService.class);
						intent.setAction(BGMService.ACTION_PLAY);
						intent.putExtra("url", streamUrl);
						intent.putExtra("artist", artist);
						intent.putExtra("title", title);
						intent.putExtra("repeat", false);
						intent.putExtra("shuffle", false);
						intent.putExtra("playlist", playlist);
						intent.putExtra("urls", urls.toArray(new String[urls.size()]));
						startService(intent);

						finish();
					}
				});
			button_repeat.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View p1)
					{
						Intent intent = new Intent(MainActivity.this, BGMService.class);
						intent.setAction(BGMService.ACTION_PLAY);
						intent.putExtra("url", streamUrl);
						intent.putExtra("artist", artist);
						intent.putExtra("title", title);
						intent.putExtra("repeat", true);
						intent.putExtra("shuffle", false);
						intent.putExtra("playlist", playlist);
						intent.putExtra("urls", urls.toArray(new String[urls.size()]));
						startService(intent);

						finish();
					}
				});
			button_shuffle.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View p1)
					{
						if (playlist)
						{
							Intent intent = new Intent(MainActivity.this, BGMService.class);
							intent.setAction(BGMService.ACTION_PLAY);
							intent.putExtra("url", streamUrl);
							intent.putExtra("artist", artist);
							intent.putExtra("title", title);
							intent.putExtra("repeat", false);
							intent.putExtra("shuffle", true);
							intent.putExtra("playlist", playlist);
							intent.putExtra("urls", urls.toArray(new String[urls.size()]));
							startService(intent);

							finish();
						}
						else
						{
							progress.setVisibility(View.VISIBLE);
							button_cancel.setVisibility(View.GONE);
							button_repeat.setVisibility(View.GONE);
							button_shuffle.setVisibility(View.GONE);
							button_play.setVisibility(View.GONE);
							text_description.setVisibility(View.GONE);
							image_menu.setImageResource(R.drawable.menu_down);
							layout_buttons.setElevation(0);
							
							new ParsePageTask().execute("Mix - " + title + ": " + "http://www.youtube.com/watch?v=" + id + "&list=RD" + id);
						}
					}
				});
			
			layout_title.setOnClickListener(new View.OnClickListener()
			{
					@Override
					public void onClick(View p1)
					{
						if (text_description.getVisibility() != View.VISIBLE)
						{
							text_description.setVisibility(View.VISIBLE);
							image_menu.setImageResource(R.drawable.menu_up);
							
							if (scroll.canScrollVertically(1))
								layout_buttons.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
						}
						else
						{
							text_description.setVisibility(View.GONE);
							image_menu.setImageResource(R.drawable.menu_down);
							layout_buttons.setElevation(0);
						}
					}
			});
				
			String url;
			if (getIntent().getAction().equals(Intent.ACTION_VIEW))
			{
				url = getIntent().getData().toString();
			}
			else
				url = getIntent().getStringExtra(android.content.Intent.EXTRA_TEXT);
				
				
			new ParsePageTask().execute(url);
		}
		
    }
	
	public boolean isInternetAvailable() 
	{
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		return cm.getActiveNetworkInfo() != null;
    }
	
	public class ParsePageTask extends AsyncTask<String, Void, Integer>
	{
		@Override
		protected Integer doInBackground(String[] p1)
		{
			try
			{
				String url = p1[0];
				int index0, index1, index2;
				Matcher m;
				
				if (url.contains("list="))
				{
					index0 = Math.max(url.indexOf("http://"), url.indexOf("https://"));
					playlistTitle = url.substring(0, index0 - 2);
					url = url.substring(index0);
					playlist = true;
					
					HttpClient httpclient = new DefaultHttpClient(); 
					HttpGet httpget = new HttpGet(url);
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

					page = sb.toString();
					
					index2 = 0;
					while (true)
					{
						index0 = page.indexOf("pl-video yt-uix-tile ", index2);
						if (index0 == -1)
							index0 = page.indexOf("yt-uix-scroller-scroll-unit", index2);
							
						index1 = index0 == -1 ? -1 : page.indexOf("data-video-id=\"", index0);
						index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 15);
						
						if (index2 != -1)
						{
							urls.add("https://youtu.be/" + page.substring(index1 + 15, index2));
						}
						else
							break;
							
						index1 = index0 == -1 ? -1 : page.indexOf("data-title=\"", index0);
						index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 12);

						if (index2 != -1)
						{
							titles.add(page.substring(index1 + 12, index2));
						}
						else
							break;
					}
					
					url = urls.get(0);
				}
				
				if (url.contains("youtu.be"))
					id = url.substring(url.lastIndexOf("/") + 1);
				else
				{
					index0 = url.indexOf("v=");
					index1 = url.indexOf("&", index0);
					id = url.substring(index0 + 2, index1 == -1 ? url.length() : index1);
				}
					
				HttpClient httpclient = new DefaultHttpClient(); 
				HttpGet httpget = new HttpGet(url);
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

				page = sb.toString();

				index0 = page.indexOf("\"author\"");
				index1 = index0 == -1 ? -1 : page.indexOf("\"", index0 + 8);
				m = Pattern.compile("(?<!\\\\)\"").matcher(page);
				m.find(index1 + 1);
				index2 = index1 == -1 ? -1 : m.start();
				
				if (index2 != -1)
					artist = Utils.unescapeJava(page.substring(index1 + 1, index2));
					
				index1 = page.indexOf("\"title\":\"");
				m = Pattern.compile("(?<!\\\\)\"").matcher(page);
				m.find(index1 + 9);
				index2 = index1 == -1 ? -1 : m.start();

				if (index2 != -1)
					title = Utils.unescapeJava(page.substring(index1 + 9, index2));
				
				index1 = page.indexOf("<p id=\"eow-description\" >");
				index2 = index1 == -1 ? -1 : page.indexOf("</p>", index1);
					
				if (index2 != -1)
					description = page.substring(index1 + 25, index2);
				
				InputStream in = null;
				
				try
				{
					in = new URL("https://i.ytimg.com/vi/" + id + "/maxresdefault.jpg").openStream();
				}
				catch (Exception e1)
				{
					try
					{
						in = new URL("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg").openStream();
					}
					catch (Exception e2)
					{
						try
						{
							in = new URL("https://i.ytimg.com/vi/" + id + "/hqdefault.jpg").openStream();
						}
						catch (Exception e3)
						{
							try
							{
								in = new URL("https://i.ytimg.com/vi/" + id + "/default.jpg").openStream();
							}
							catch (Exception e4)
							{
								in = null;
								bitmap_video = null;
							}
						}
					}
				}
				
				if (in != null)
				{
					bitmap_video = BitmapFactory.decodeStream(in);
					
					in.close();
				}
				
				index0 = page.indexOf("video-thumb  yt-thumb yt-thumb-48 g-hovercard");
				index1 = index0 == -1 ? -1 : page.indexOf("data-thumb=\"", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 12);
				
				if (index2 != -1)
				{
					try
					{
						in = new URL(page.substring(index1 + 12, index2).replaceFirst("^//", "")).openStream();
					}
					catch (Exception e1)
					{
						e1.printStackTrace();
						in = null;
					}
					
					if (in != null)
					{
						bitmap_artist = BitmapFactory.decodeStream(in);

						in.close();
					}
				}
				
				index0 = page.indexOf("quality=");
				index1 = index0 == -1 ? -1 : page.indexOf("url=", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("\\u0026", index1);
				
				if (index2 != -1)
				{
					streamUrl = URLDecoder.decode(page.substring(index1 + 4, index2)).toString();
						
					return 0;
				}
				else if (page.contains("unavailable-message"))
				{
					return 1;
				}
				else
					return 2;
					
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}

			return -1;
		}

		@Override
		protected void onPostExecute(Integer result)
		{
			progress.setVisibility(View.GONE);
			layout_title.setVisibility(View.VISIBLE);
			button_cancel.setVisibility(View.VISIBLE);
			
			if (result != 0)
			{
				image_menu.setVisibility(View.GONE);
				
				if (result == 1)
				{
					int index1 = page.indexOf("<h1 id=\"unavailable-message\" class=\"message\">");
					int index2 = index1 == -1 ? -1 : page.indexOf("</h1>", index1 + 45);
					
					if (index2 != -1)
						text_title.setText(page.substring(index1 + 45, index2).trim());
					else
						text_title.setText("Video unavailable");
				}
				else
					text_title.setText("Couln't parse page. Is this really a public YouTube video or playlist?");

			}
			else
			{
				if (bitmap_video != null)
				{
					image_video.setVisibility(View.VISIBLE);
					image_video.setImageBitmap(bitmap_video);
					
					if (bitmap_artist != null)
					{
						image_artist.setVisibility(View.VISIBLE);
						image_artist.setImageBitmap(bitmap_artist);
						image_artist.setBackgroundDrawable(new BitmapDrawable(bitmap_artist));
					}
				}
				
				button_repeat.setVisibility(View.VISIBLE);
				button_shuffle.setVisibility(View.VISIBLE);
				button_play.setVisibility(View.VISIBLE);
				
				text_title.setText(playlist ? playlistTitle : title);
				
				if (playlist)
				{
					description = "";
					for (String title : titles)
						description += title + "<br />";
					text_description.setText(Html.fromHtml(description));
					text_description.setEllipsize(TextUtils.TruncateAt.END);
				}
				else
					text_description.setText(Html.fromHtml(description));
			}
		}
	}
}

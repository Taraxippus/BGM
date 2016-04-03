package com.taraxippus.bgm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import android.widget.SpinnerAdapter;
import android.widget.ArrayAdapter;
import java.util.ArrayList;

public class MainActivity extends Activity 
{
	ProgressBar progress;
	TextView text;
	Spinner mode;
	Button play;
	ArrayAdapter<String> adapter;
	
	String id;
	String page;
	String quality;
	String title;
	String artist;
	
	boolean playlist;
	String playlistTitle;
	ArrayList<String> urls = new ArrayList<String>();
	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
       	setContentView(R.layout.main);
		
		progress = (ProgressBar) findViewById(R.id.progress);
		text = (TextView) findViewById(R.id.text);
		mode = (Spinner) findViewById(R.id.spinner);
		play = (Button) findViewById(R.id.button);
		
		mode.setAdapter(adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item));
		play.setEnabled(false);
		
		if (!isInternetAvailable())
		{
			progress.setVisibility(View.GONE);
			text.setText("No connection");
			
			play.setEnabled(true);
			play.setText("Close");
			play.setOnClickListener(new View.OnClickListener()
			{
					@Override
					public void onClick(View p1)
					{
						finish();
					}
			});
		}
		else
		{
			play.setOnClickListener(new View.OnClickListener()
			{
					@Override
					public void onClick(View p1)
					{
						if (!playlist && mode.getSelectedItemPosition() == 1)
						{
							play.setEnabled(false);
							progress.setVisibility(View.VISIBLE);
							mode.setSelection(0);
							mode.setVisibility(View.GONE);
							
							new ParsePageTask().execute("Mix - " + title + ": " + "http://www.youtube.com/watch?v=" + id + "&list=RD" + id);
						}
						else
						{
							play.setVisibility(View.GONE);
							mode.setVisibility(View.GONE);
							progress.setVisibility(View.VISIBLE);
							new ParseUrlTask().execute();
						}
					}
			});
			
			new ParsePageTask().execute(getIntent().getStringExtra(android.content.Intent.EXTRA_TEXT));
		}
		
    }
	
	public boolean isInternetAvailable() 
	{
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		return cm.getActiveNetworkInfo() != null;
    }
	
	public class ParsePageTask extends AsyncTask<String, Void, String>
	{
		@Override
		protected String doInBackground(String[] p1)
		{
			try
			{
				String url = p1[0];
				int index0, index1, index2;
				
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
					}
					
					url = urls.get(0);
				}
				
				if (url.contains("youtu.be"))
					id = url.substring(url.lastIndexOf("/") + 1);
				else
				{
					index0 = page.indexOf("v=");
					index1 = page.indexOf("&", index0);
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
				index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 1);
				
				if (index2 != -1)
					artist = page.substring(index1 + 1, index2);
					
				index1 = page.indexOf("\"title\":\"");
				index2 = index1 == -1 ? -1 : page.indexOf("\",\"", index1 + 9);

				if (index2 != -1)
					title = page.substring(index1 + 9, index2).replace("\\\"", "\"").replace("\\/", "/");
				
				index1 = page.indexOf("quality=");
				index2 = index1 == -1 ? -1 : page.indexOf(",", index1);

				if (index2 != -1)
				{
					return page.substring(index1 + 8, index2);
				}
				else if (page.contains("unavailable-message"))
				{
					return "#unavailable-message";
				}
				else
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
			adapter.clear();
			
			if (result == null || result.length() == 0 || result.equals("#unavailable-message"))
			{
				progress.setVisibility(View.GONE);
				mode.setVisibility(View.GONE);
				
				if (result != null && result.equals("#unavailable-message"))
				{
					int index1 = page.indexOf("<h1 id=\"unavailable-message\" class=\"message\">");
					int index2 = index1 == -1 ? -1 : page.indexOf("</h1>", index1 + 45);
					
					if (index2 != -1)
						text.setText(page.substring(index1 + 45, index2).trim());
					else
						text.setText("Video unavailable");
				}
				else
					text.setText("Couln't parse page. Is this really a public YouTube video or playlist?");

				play.setEnabled(true);
				play.setTextColor(0xFFFF9800);
				play.setText("Close");
				play.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							finish();
						}
					});
			}
			else
			{
				quality = result;
				
				progress.setVisibility(View.GONE);
				
				play.setEnabled(true);
				play.setTextColor(0xFFFF9800);
				
				if (playlist)
				{
					text.setText(playlistTitle);
					
					adapter.add("Loop playlist");
					adapter.add("Suffle playlist");
				}
				else
				{
					text.setText(title);
					
					adapter.add("Loop video");
					adapter.add("Play Mix");
					adapter.add("Play single video");
				}
				
				mode.setVisibility(View.VISIBLE);
			}
		}
	}
	
	public class ParseUrlTask extends AsyncTask<String, Void, String[]>
	{
		@Override
		protected String[] doInBackground(String[] p1)
		{
			try
			{
				int index0 = page.indexOf("quality=" + quality);
				int index1 = index0 == -1 ? -1 : page.indexOf("url=", index0);
				int index2 = index1 == -1 ? -1 : page.indexOf("\\u0026", index1);
				
				if (index2 != -1)
				{
					return new String[] {URLDecoder.decode(page.substring(index1 + 4, index2)).toString()};
				}
				else
				{
					return null;
				}
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
			if (result == null)
			{
				progress.setVisibility(View.GONE);
				text.setText("Couln't find video stream :(");

				play.setVisibility(View.VISIBLE);
				play.setText("Close");
				play.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							finish();
						}
					});
			}
			else
			{
				Intent intent = new Intent(MainActivity.this, BGMService.class);
				intent.setAction(BGMService.ACTION_PLAY);
				intent.putExtra("url", result[0]);
				intent.putExtra("artist", artist);
				intent.putExtra("title", title);
				intent.putExtra("loop", mode.getSelectedItemPosition() == 0);
				intent.putExtra("shuffle", mode.getSelectedItemPosition() == 1);
				intent.putExtra("playlist", playlist);
				intent.putExtra("urls", urls.toArray(new String[urls.size()]));
				startService(intent);

				finish();
			}
		}
	}
}

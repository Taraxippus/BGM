package com.taraxippus.bgm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import com.taraxippus.bgm.R;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import android.util.LruCache;
import android.app.AlertDialog;

public class MainActivity extends Activity 
{
	ProgressBar progress;
	SeekBar seek;
	ViewSwitcher switcher_video;
	Switch switch_auto;
	ImageView image_video, image_video2, image_artist, image_menu;
	TextView text_title, text_artist, text_description, text_time;
	Button button_cancel;
	ImageButton button_shuffle, button_play, button_repeat, button_overflow;
	View layout_title, layout_buttons, layout_image;
	RecyclerView recycler;
	
	String page, id, playListId, url, loadMoreUrl;
	String title, artist, description;
	Bitmap bitmap_video;
	Bitmap bitmap_artist;
	int duration;
	int time;
	int playlistIndex = 0;
	
	boolean playlist, loadMore, mix;
	String playlistTitle;
	List<String> ids = new ArrayList<String>();
	List<String> urls = new ArrayList<String>();
	List<String> titles = new ArrayList<String>();
	List<String> artists = new ArrayList<String>();
	LruCache<String, Bitmap> bitmap_cache;
	
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
       	setContentView(R.layout.main);
		
		progress = (ProgressBar) findViewById(R.id.progress);
		seek = (SeekBar) findViewById(R.id.seek);
		image_video = (ImageView) findViewById(R.id.image_video);
		image_video2 = (ImageView) findViewById(R.id.image_video2);
		image_artist = (ImageView) findViewById(R.id.image_artist);
		
		button_cancel = (Button) findViewById(R.id.button_cancel);
		button_shuffle = (ImageButton) findViewById(R.id.button_shuffle);
		button_repeat = (ImageButton) findViewById(R.id.button_repeat);
		button_play = (ImageButton) findViewById(R.id.button_play);
		button_overflow = (ImageButton) findViewById(R.id.button_overflow);
		
		switcher_video = (ViewSwitcher) findViewById(R.id.switcher_video);
		
		layout_image = findViewById(R.id.layout_image);
		layout_buttons = findViewById(R.id.layout_buttons);
		
		recycler = (RecyclerView) findViewById(R.id.recycler);
		recycler.setOnScrollChangeListener(new RecyclerView.OnScrollChangeListener()
		{
				@Override
				public void onScrollChange(View p1, int p2, int p3, int p4, int p5)
				{
					if (recycler.canScrollVertically(1))
						layout_buttons.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
					else
						layout_buttons.setElevation(0);
						
					if (recycler.canScrollVertically(-1))
						layout_image.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
					else
						layout_image.setElevation(0);
				}
		});
		recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
		recycler.setAdapter(new DescriptionAdapter());
		new ItemTouchHelper(new PlaylistTouchHelperCallback()).attachToRecyclerView(recycler);
		
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
			
			if (layout_title != null)
			{
				layout_title.setVisibility(View.VISIBLE);
				image_menu.setVisibility(View.GONE);
				text_title.setText("No internet connection");
			}
			
			button_cancel.setVisibility(View.VISIBLE);
		}
		else
		{
			bitmap_cache = new LruCache<String, Bitmap>( (int) (Runtime.getRuntime().maxMemory() / 1024) / 4) 
			{
				@Override
				protected int sizeOf(String key, Bitmap bitmap) 
				{
					return bitmap.getByteCount() / 1024;
				}
			};
			
			button_play.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View p1)
					{
						startService(false, false, false);
					}
				});
			button_repeat.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View p1)
					{
						startService(false, true, false);
					}
				});
			button_shuffle.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View p1)
					{
						if (playlist)
						{
							startService(true, false, false);
						}
						else
						{
							progress.setVisibility(View.VISIBLE);
							seek.setVisibility(View.GONE);
							text_time.setVisibility(View.GONE);
							button_cancel.setVisibility(View.GONE);
							button_repeat.setVisibility(View.GONE);
							button_shuffle.setVisibility(View.GONE);
							button_play.setVisibility(View.GONE);
							button_overflow.setVisibility(View.GONE);
							text_description.setVisibility(View.GONE);
							switch_auto.setVisibility(View.GONE);
							image_menu.setImageResource(R.drawable.menu_down);
							image_artist.setVisibility(View.GONE);
							layout_buttons.setElevation(0);
							layout_image.setElevation(0);
							
							text_title.setText(Html.fromHtml("Mix - " + title));
							text_artist.setText("Playlist");
							
							url = "http://www.youtube.com/watch?v=" + id + "&list=RD" + id;
							new ParsePageTask().execute(url);
						}
					}
				});
				
			button_overflow.setOnClickListener(new View.OnClickListener()
			{
					@Override
					public void onClick(View p1)
					{
						PopupMenu popup = new PopupMenu(MainActivity.this, button_overflow);
						popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
						{
								@Override
								public boolean onMenuItemClick(MenuItem item)
								{
									switch (item.getItemId())
									{
										case R.id.item_preferences:
											startActivity(new Intent(MainActivity.this, PreferenceActivity.class));
											return true;
											
										case R.id.item_view:
											startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(!playlist ? (url.contains("?") ? url + "&" : url + "?") + "t=" + StringUtils.toUrlTime(seek.getProgress()) : url)));
											return true;
											
										case R.id.item_add:
											startService(false, false, true);
											Toast.makeText(MainActivity.this, "Added to queue", Toast.LENGTH_SHORT).show();
											return true;
											
										default:
											return false;
									}
								}
						});
						
						MenuInflater inflater = popup.getMenuInflater();
						inflater.inflate(R.menu.main, popup.getMenu());
						popup.show();
					}
			});
			
			seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
			{
					@Override
					public void onProgressChanged(SeekBar p1, int p2, boolean p3)
					{
						text_time.setText(StringUtils.toTime(p2));
					}

					@Override
					public void onStartTrackingTouch(SeekBar p1)
					{
						text_time.setVisibility(View.VISIBLE);
					}

					@Override
					public void onStopTrackingTouch(SeekBar p1)
					{
						text_time.setVisibility(View.GONE);
					}
			});
				
			if (getIntent().getAction().equals(Intent.ACTION_VIEW))
			{
				url = getIntent().getData().toString();
			}
			else
				url = getIntent().getStringExtra(android.content.Intent.EXTRA_TEXT);
				
				
			new ParsePageTask().execute(url);
		}
		
    }
	
	public void startService(boolean shuffle, boolean repeat, boolean add)
	{
		Intent intent = new Intent(MainActivity.this, BGMService.class);
		intent.setAction(BGMService.ACTION_PLAY);
		
		intent.putExtra("repeat", repeat);
		intent.putExtra("shuffle", playlist ? shuffle : switch_auto.isChecked());
		intent.putExtra("urls", urls.toArray(new String[urls.size()]));
		intent.putExtra("titles", titles.toArray(new String[titles.size()]));
		intent.putExtra("artists", artists.toArray(new String[artists.size()]));
		intent.putExtra("add", add);
		intent.putExtra("time", seek.getProgress());
		intent.putExtra("index", playlistIndex);
			
		startService(intent);

		if (!add)
			finish();
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
				url = p1[0];
				int index0, index1, index2;
				
				if (url.contains("list="))
				{
					titles.clear();
					artists.clear();
					urls.clear();
					
					index0 = Math.max(url.indexOf("http://"), url.indexOf("https://"));
					MainActivity.this.url = url = url.substring(index0);
					playlist = true;
					boolean playListSite = url.contains("playlist?");
					
					index0 = url.indexOf("list=");
					index1 = url.indexOf("&", index0);
					playListId = url.substring(index0 + 5, index1 == -1 ? url.length() : index1);
					
					index0 = url.indexOf("index=");
					index1 = url.indexOf("&", index0);
					if (index0 != -1)
						playlistIndex = Integer.parseInt(url.substring(index0 + 6, index1 == -1 ? url.length() : index1));
					
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
					
					int index3 = 0;
					if (playListSite)
					{
						index0 = page.indexOf("class=\"pl-header-title\"");
						index1 = index0 == -1 ? -1 : page.indexOf(">", index0);
						index2 = index1 == -1 ? -1 : page.indexOf("</h1>", index1);

						playlistTitle = index2 == -1 ? "Playlist" : page.substring(index1 + 1, index2);
						
						while (true)
						{
							index0 = page.indexOf("pl-video yt-uix-tile ", index3);
							index1 = index0 == -1 ? -1 : page.indexOf("data-video-id=\"", index0);
							index3 = index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 15);

							if (index2 != -1)
							{
								ids.add(page.substring(index1 + 15, index2));
								urls.add("https://www.youtube.com/watch?v=" + page.substring(index1 + 15, index2));
							}
							else
								break;

							index1 = index0 == -1 ? -1 : page.indexOf("data-title=\"", index0);
							index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 12);

							if (index2 != -1)
								titles.add(page.substring(index1 + 12, index2));
							else
								titles.add("" + titles.size());

							index0 = page.indexOf("pl-video-owner", index0);
							index1 = index0 == -1 ? -1 : page.indexOf(">", index0 + 17);
							index2 = index1 == -1 ? -1 : page.indexOf("</a>", index1 + 1);

							if (index2 != -1)
								artists.add(page.substring(index1 + 1, index2));
							else
								artists.add("Unknown Artist");
						}
							
						index1 = page.indexOf("data-uix-load-more-href=\"");
						index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 25);
						
						if (index2 != -1)
						{
							loadMore = true;
							loadMoreUrl = "https://www.youtube.com" + page.substring(index1 + 25, index2);
						}
						else 
							loadMore = false;
					}
					else
					{
						index1 = page.indexOf("data-list-title=\"");
						index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 17);

						playlistTitle = index2 == -1 ? "Playlist" : page.substring(index1 + 17, index2);
						
						while (true)
						{
							index0 = page.indexOf("yt-uix-scroller-scroll-unit", index3);
							index1 = index0 == -1 ? -1 : page.indexOf("data-video-id=\"", index0);
							index3 = index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 15);

							if (index2 != -1)
							{
								ids.add(page.substring(index1 + 15, index2));
								urls.add("https://www.youtube.com/watch?v=" + page.substring(index1 + 15, index2) + "&gl=US");
							}
							else
								break;

							index1 = index0 == -1 ? -1 : page.indexOf("data-video-title=\"", index0);
							index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 18);

							if (index2 != -1)
								titles.add(page.substring(index1 + 18, index2));
							else
								titles.add("" + titles.size());

							index1 = index0 == -1 ? -1 : page.indexOf("data-video-username=\"", index0);
							index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 21);

							if (index2 != -1)
								artists.add(page.substring(index1 + 21, index2));
							else
								artists.add("Unknown Artist");
						}
						
						mix = page.contains("playlist-mix-icon yt-sprite");
						
						if (mix)
						{
							playlistIndex = 0;
							
							loadMore = true;
							loadMoreUrl = urls.get(urls.size() - 1) + "&list=RD" + ids.get(ids.size() - 1);
						}
						else 
							loadMore = false;
					}
					
					url = urls.get(playlistIndex);
				}
				
				if (url.contains("youtu.be"))
				{
					index0 = url.lastIndexOf("/");
					index1 = url.indexOf("?", index0);
					id = url.substring(index0 + 1, index1 == -1 ? url.length() : index1);
				}
				else
				{
					index0 = url.indexOf("v=");
					index1 = url.indexOf("&", index0);
					id = url.substring(index0 + 2, index1 == -1 ? url.length() : index1);
				}
				
				time = 0;
				
				index0 = url.indexOf("t=");
				index1 = index0 == -1 ? - 1 : url.indexOf("&", index0);
				if (index0 != -1)
					time = StringUtils.fromUrlTime(url.substring(index0 + 2, index1 == -1 ? url.length() : index1));
			
				url = "https://www.youtube.com/watch?v=" + id + "&gl=US";
					
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

				index0 = page.indexOf("class=\"yt-user-info\"");
				index1 = index0 == -1 ? -1 : page.indexOf(">", index0 + 35);
				index2 = index1 == -1 ? -1 : page.indexOf("</a>", index1);
				
				if (index2 != -1)
					artist = page.substring(index1 + 1, index2);
					
				index0 = page.indexOf("id=\"eow-title\"");
				index1 = index0 == -1 ? -1 : page.indexOf(">", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("</span>", index1);
				
				if (index2 != -1)
					title = page.substring(index1 + 1, index2);
				
				index1 = page.indexOf("<meta itemprop=\"duration\" content=\"");
				index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 35);
				
				if (index2 != -1)
					duration = StringUtils.fromUrlTime(page.substring(index1 + 37, index2));
					
				index0 = page.indexOf("class=\"watch-time-text\"");
				index1 = index0 == -1 ? -1 : page.indexOf(">", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("</strong>", index1);
				
				if (index2 != -1)
					description = page.substring(index1 + 1, index2) + " • ";
				
				index0 = page.indexOf("class=\"watch-view-count\"");
				index1 = index0 == -1 ? -1 : page.indexOf(">", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("</div>", index1);
				
				if (index2 != -1)
					description += page.substring(index1 + 1, index2) + "<br /><br />";
				
				index0 = page.indexOf("id=\"eow-description\"");
				index1 = index0 == -1 ? -1 : page.indexOf(">", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("</p>", index1);
					
				if (index2 != -1)
					description += page.substring(index1 + 1, index2);
				
				bitmap_video = getBitmap(id);
				
				index0 = page.indexOf("video-thumb  yt-thumb yt-thumb-48 g-hovercard");
				index1 = index0 == -1 ? -1 : page.indexOf("data-thumb=\"", index0);
				index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 12);
				
				InputStream in;
				
				if (index2 != -1)
				{
					try
					{
						in = new URL(page.substring(index1 + 12, index2).replaceFirst("^//", "http://")).openStream();
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
				
				index0 = page.indexOf("quality=" + PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("quality", "High").toLowerCase());
				index0 = index0 == -1 ? page.indexOf("quality=") : index0;
				index1 = index0 == -1 ? -1 : page.indexOf("url=", index0);
				index2 = index1 == -1 ? -1 : page.indexOf(",", index1);
				index0 = index1 == -1 ? -1 : page.indexOf("\\u0026", index1);
				index2 = index2 == -1 ? index0 : index0 == -1 ? index2 : Math.min(index0, index2);
				
				if (index2 != -1)
				{
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
				System.out.println("Error parsing url:\n" + url);
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
						text_title.setText(Html.fromHtml(page.substring(index1 + 45, index2).trim()));
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
					switcher_video.setVisibility(View.VISIBLE);
					image_video.setImageBitmap(bitmap_video);
					
					if (!playlist && bitmap_artist != null)
					{
						image_artist.setVisibility(View.VISIBLE);
						image_artist.setImageBitmap(bitmap_artist);
						image_artist.setBackgroundDrawable(new BitmapDrawable(bitmap_artist));
					}
					else
						image_artist.setVisibility(View.GONE);
				}
				
				button_repeat.setVisibility(View.VISIBLE);
				button_shuffle.setVisibility(View.VISIBLE);
				button_play.setVisibility(View.VISIBLE);
				button_overflow.setVisibility(View.VISIBLE);
				
				if (playlist)
				{
					text_title.setText(Html.fromHtml(playlistTitle));
					text_artist.setText("Playlist");
					text_description.setText(titles.size() + (loadMore ? "+" : "") + (titles.size() == 1 ? " video" : " videos"));
					
					new LoadImageTask().execute();
				}
				else
				{
					seek.setVisibility(View.VISIBLE);
					seek.setMax(duration);
					seek.setProgress(time);
					
					text_title.setText(Html.fromHtml(title));
					text_artist.setText(Html.fromHtml(artist));
					setDescription(description);
					
					titles.add(title);
					artists.add(artist);
					urls.add(url);
				}
					
				recycler.getAdapter().notifyDataSetChanged();
			}
		}
	}

	protected void setDescription(String html)
	{
		CharSequence sequence = Html.fromHtml(html);
		SpannableStringBuilder strBuilder = new SpannableStringBuilder(sequence);
		URLSpan[] urls = strBuilder.getSpans(0, sequence.length(), URLSpan.class);   
		
		int start, end;
		char[] text;
		ClickableSpan newSpan;
		for (URLSpan span : urls) 
		{
			if (span.getURL().equals("#"))
			{
				start = strBuilder.getSpanStart(span);
				end = strBuilder.getSpanEnd(span);
				text = new char[end - start];
				strBuilder.getChars(start, end, text, 0);
				
				newSpan = new SeekSpan(new String(text));
				
				strBuilder.setSpan(newSpan, start, end, strBuilder.getSpanFlags(span));
				strBuilder.removeSpan(span);
			}
		}
		text_description.setText(strBuilder);       
	}
	
	public class SeekSpan extends ClickableSpan
	{
		final int target;
		
		public SeekSpan(String text)
		{
			target = StringUtils.fromTime(text);
		}
		
		@Override
		public void onClick(View v)
		{
			if (target <= seek.getMax())
				seek.setProgress(target);
		}
	}
	
	public Bitmap getBitmap(String id)
	{
		InputStream in = null;
		Bitmap bitmap = null;
		
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
						bitmap = null;
					}
				}
			}
		}

		if (in != null)
		{
			bitmap = BitmapFactory.decodeStream(in);

			try
			{
				in.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		
		return bitmap;
	}
	
	public class LoadImageTask extends AsyncTask<Void, Void, Void>
	{
		@Override
		protected Void doInBackground(Void[] p1)
		{
			for (int i = 0; i < ids.size(); ++i)
			{
				if (bitmap_cache.get(ids.get(i)) != null)
					continue;
					
				try
				{
					InputStream in = new URL("https://i.ytimg.com/vi/" + ids.get(i) + "/mqdefault.jpg").openStream();
					bitmap_cache.put(ids.get(i), Bitmap.createBitmap(BitmapFactory.decodeStream(in)));
					in.close();

					MainActivity.this.runOnUiThread(new UpdateRunnable(i + 1));
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			
			return null;
		}
	}
	
	public class UpdateRunnable implements Runnable
	{
		final int index;
		
		public UpdateRunnable(int index)
		{
			this.index = index;
		}

		@Override
		public void run()
		{
			recycler.getAdapter().notifyItemChanged(index);
		}
	}
	
	public class DescriptionAdapter extends RecyclerView.Adapter implements View.OnClickListener
	{
		public class VideoViewHolder extends RecyclerView.ViewHolder
		{
			final TextView title;
			final TextView artist;
			final ImageView image;
			final ImageButton overflow;
			
			public VideoViewHolder(View v)
			{
				super(v);
				
				title = (TextView) v.findViewById(R.id.text_title);
				artist = (TextView) v.findViewById(R.id.text_artist);
				image = (ImageView) v.findViewById(R.id.image_video);
				overflow = (ImageButton) v.findViewById(R.id.button_overflow);
				
				overflow.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							PopupMenu popup = new PopupMenu(MainActivity.this, overflow);
							popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
								{
									@Override
									public boolean onMenuItemClick(MenuItem item)
									{
										int index = recycler.getChildAdapterPosition(itemView);
										
										Intent intent;
										switch (item.getItemId())
										{
											case R.id.item_view:
												startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urls.get(index - 1))));
												return true;
											
											case R.id.item_play:
												intent = new Intent(MainActivity.this, MainActivity.class);
												intent.setAction(Intent.ACTION_SEND);
												intent.putExtra(Intent.EXTRA_TEXT, urls.get(index - 1));
												startActivity(intent);
												return true;
												
											case R.id.item_mix:
												intent = new Intent(MainActivity.this, MainActivity.class);
												intent.setAction(Intent.ACTION_SEND);
												intent.putExtra(Intent.EXTRA_TEXT, "http://www.youtube.com/watch?v=" + ids.get(index - 1) + "&list=RD" + ids.get(index - 1));
												startActivity(intent);
												return true;
					
											case R.id.item_add:
												intent = new Intent(MainActivity.this, BGMService.class);
												intent.setAction(BGMService.ACTION_PLAY);

												intent.putExtra("repeat", false);
												intent.putExtra("shuffle", false);
												intent.putExtra("urls", new String[] {urls.get(index - 1)});
												intent.putExtra("titles", new String[] {titles.get(index - 1)});
												intent.putExtra("artists", new String[] {artists.get(index - 1)});
												intent.putExtra("add", true);
												intent.putExtra("time", seek.getProgress());
												
												startService(intent);
												
												Toast.makeText(MainActivity.this, "Added to queue", Toast.LENGTH_SHORT).show();
												
												return true;
												
											case R.id.item_remove:
												urls.remove(index - 1);
												titles.remove(index - 1);
												artists.remove(index - 1);
												if (bitmap_cache.get(ids.get(index - 1)) != null)
													bitmap_cache.get(ids.get(index - 1)).recycle();
												bitmap_cache.remove(ids.get(index - 1));
												ids.remove(index - 1);

												text_description.setText(titles.size() + (loadMore ? "+" : "") + (titles.size() == 1 ? " Video" : " Videos"));
												recycler.getAdapter().notifyItemRemoved(index);

												if (index - 1 == playlistIndex)
												{
													playlistIndex = 0;
													recycler.getAdapter().notifyItemChanged(1);
												}
												return true;
												
											default:
												return false;
										}
									}
								});

							MenuInflater inflater = popup.getMenuInflater();
							inflater.inflate(R.menu.playlist, popup.getMenu());
							popup.show();
						}
					});
					
				if (!isInternetAvailable())
				{
					layout_title.setVisibility(View.VISIBLE);
					image_menu.setVisibility(View.GONE);
					text_title.setText("No internet connection");
				}
			}
		}
		
		public class HeaderViewHolder extends RecyclerView.ViewHolder
		{
			public HeaderViewHolder(View v)
			{
				super(v);

				image_menu = (ImageView) v.findViewById(R.id.image_menu);
				text_title = (TextView) v.findViewById(R.id.text_title);
				text_artist = (TextView) v.findViewById(R.id.text_artist);
				text_description = (TextView) v.findViewById(R.id.text_description);
				text_time = (TextView) v.findViewById(R.id.text_time);
				
				switch_auto = (Switch) v.findViewById(R.id.switch_auto);
				
				layout_title = v.findViewById(R.id.layout_title);
				
				text_description.setLinksClickable(true);
				text_description.setMovementMethod(LinkMovementMethod.getInstance());
				layout_title.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							if (progress.getVisibility() == View.VISIBLE)
								return;
							
							if (text_description.getVisibility() != View.VISIBLE)
							{
								text_description.setVisibility(View.VISIBLE);
								if (!playlist)
									switch_auto.setVisibility(View.VISIBLE);
								image_menu.setImageResource(R.drawable.menu_up);

								if (playlist)
									recycler.getAdapter().notifyDataSetChanged();

								if (recycler.canScrollVertically(1))
									layout_buttons.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
							}
							else
							{
								text_description.setVisibility(View.GONE);
								switch_auto.setVisibility(View.GONE);
								image_menu.setImageResource(R.drawable.menu_down);

								if (playlist)
									recycler.getAdapter().notifyDataSetChanged();

								layout_buttons.setElevation(0);
								layout_image.setElevation(0);
							}
						}
					});
			}
		}
		
		public class LoadMoreViewHolder extends RecyclerView.ViewHolder
		{
			final TextView text_title;
			final ProgressBar progress;
			
			public LoadMoreViewHolder(View v)
			{
				super(v);

				text_title = (TextView) v.findViewById(R.id.text_title);
				progress = (ProgressBar) v.findViewById(R.id.progress);
				
				text_title.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View p1)
						{
							text_title.setVisibility(View.INVISIBLE);
							progress.setVisibility(View.VISIBLE);
							
							new LoadMoreTask().execute();
						}
					});
			}
		}
		
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup group, int type)
		{
			if (type == 0)
			{
				return new HeaderViewHolder(LayoutInflater.from(group.getContext()).inflate(R.layout.item_header, group, false));
			}
			else if (type == 2)
			{
				return new LoadMoreViewHolder(LayoutInflater.from(group.getContext()).inflate(R.layout.item_load_more, group, false));
			}
			else
			{
				View v = LayoutInflater.from(group.getContext()).inflate(R.layout.item_playlist, group, false);

				v.setOnClickListener(this);

				return new VideoViewHolder(v);
			}
		}

		@Override
		public void onBindViewHolder(RecyclerView.ViewHolder holder, int index)
		{
			if (getItemViewType(index) == 1)
			{
				VideoViewHolder v = (VideoViewHolder) holder;

				v.title.setText(Html.fromHtml(titles.get(index - 1)));
				v.artist.setText(Html.fromHtml(artists.get(index - 1)));
				if (bitmap_cache.get(ids.get(index - 1)) == null)
					v.image.setImageResource(R.drawable.launcher);
				else
					v.image.setImageBitmap(bitmap_cache.get(ids.get(index - 1)));
				
				v.overflow.setImageResource(index - 1 == playlistIndex ? R.drawable.dots_vertical : R.drawable.dots_vertical_disabled);
				v.itemView.setBackgroundColor(index - 1 == playlistIndex ? 0xFF303030 : 0xFF424242);
			}
		}

		@Override
		public int getItemCount()
		{
			return playlist && text_description != null && text_description.getVisibility() == View.VISIBLE ? 1 + titles.size() + (loadMore ? 1 : 0) : 1;
		}

		@Override
		public int getItemViewType(int position)
		{
			return position == 0 ? 0 : loadMore && position == getItemCount() - 1 ? 2 : 1;
		}
		
		@Override
		public void onClick(View v)
		{
			final int index = recycler.getChildAdapterPosition(v) - 1;
			final int old = playlistIndex + 1;
			
			if (index == playlistIndex)
				return;
			
			playlistIndex = index;
			
			RecyclerView.ViewHolder v1 = recycler.findViewHolderForAdapterPosition(old);
			if (v1 != null)
			{
				v1.itemView.setBackgroundColor(0xFF424242);
				((ImageButton) v1.itemView.findViewById(R.id.button_overflow)).setImageResource(R.drawable.dots_vertical_disabled);
			}
			v.setBackgroundColor(0xFF303030);
			((ImageButton) v.findViewById(R.id.button_overflow)).setImageResource(R.drawable.dots_vertical);
			new ChangeImageTask().execute(ids.get(index));
		}
	}
	
	public class ChangeImageTask extends AsyncTask<String, Void, Bitmap>
	{
		@Override
		protected Bitmap doInBackground(String[] p1)
		{
			Bitmap bitmap = getBitmap(p1[0]);
			if (bitmap == null)
				return null;
			
			float ratio = (float) bitmap_video.getWidth() / bitmap_video.getHeight();
			
			Bitmap bitmap1;
			if ((float) bitmap.getWidth() / bitmap.getHeight() > ratio)
				bitmap1 = Bitmap.createBitmap(bitmap, (int) ((bitmap.getWidth() - bitmap.getHeight() * ratio) / 2F), 0, (int) (bitmap.getHeight() * ratio), bitmap.getHeight());
			else
				bitmap1 = Bitmap.createBitmap(bitmap, 0, (int) ((bitmap.getHeight() - bitmap.getWidth() / ratio) / 2F), bitmap.getWidth(), (int) (bitmap.getWidth() / ratio));
			
			if (bitmap != bitmap1)
				bitmap.recycle();
			
			return bitmap1;
		}

		@Override
		protected void onPostExecute(Bitmap result)
		{
			if (result == null)
				return;
			
			if (switcher_video.getDisplayedChild() == 0)
			{
				if (image_video2.getDrawable() instanceof BitmapDrawable)
					((BitmapDrawable) image_video2.getDrawable()).getBitmap().recycle();
					
				image_video2.setImageBitmap(result);
				switcher_video.showNext();
			}
			else
			{
				if (image_video.getDrawable() instanceof BitmapDrawable)
					((BitmapDrawable) image_video.getDrawable()).getBitmap().recycle();
				
				image_video.setImageBitmap(result);
				switcher_video.showPrevious();
			}
		}
	}
	
	public class LoadMoreTask extends AsyncTask<String, Void, String>
	{

		@Override
		protected String doInBackground(String[] p1)
		{
			try
			{
				HttpClient httpclient = new DefaultHttpClient(); 
				HttpGet httpget = new HttpGet(loadMoreUrl);
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
			
				int index0, index1, index2, index3 = 0;
				if (mix)
				{
					for (int i = 0; true; i++)
					{
						index0 = page.indexOf("yt-uix-scroller-scroll-unit", index3);
						index1 = index0 == -1 ? -1 : page.indexOf("data-video-id=\"", index0);
						index3 = index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 15);
						
						if (index2 != -1)
						{
							if (i == 0)
								continue;
								
							ids.add(page.substring(index1 + 15, index2));
							urls.add("https://www.youtube.com/watch?v=" + page.substring(index1 + 15, index2) + "&gl=US");
						}
						else
							break;
							

						index1 = index0 == -1 ? -1 : page.indexOf("data-video-title=\"", index0);
						index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 18);

						if (index2 != -1)
							titles.add(page.substring(index1 + 18, index2));
						else
							titles.add("" + titles.size());

						index1 = index0 == -1 ? -1 : page.indexOf("data-video-username=\"", index0);
						index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 21);

						if (index2 != -1)
							artists.add(page.substring(index1 + 21, index2));
						else
							artists.add("Unknown Artist");
					}
					
					loadMoreUrl = urls.get(urls.size() - 1) + "&list=RD" + ids.get(ids.size() - 1);
				}
				else
				{
					page = StringUtils.unescapeJava(page);
					
					while (true)
					{
						index0 = page.indexOf("pl-video yt-uix-tile ", index3);
						index1 = index0 == -1 ? -1 : page.indexOf("data-video-id=\"", index0);
						index3 = index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 15);

						if (index2 != -1)
						{
							ids.add(page.substring(index1 + 15, index2));
							urls.add("https://www.youtube.com/watch?v=" + page.substring(index1 + 15, index2));
						}
						else
							break;

						index1 = index0 == -1 ? -1 : page.indexOf("data-title=\"", index0);
						index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 12);

						if (index2 != -1)
							titles.add(page.substring(index1 + 12, index2));
						else
							titles.add("" + titles.size());

						index0 = page.indexOf("pl-video-owner", index0);
						index1 = index0 == -1 ? -1 : page.indexOf(">", index0 + 17);
						index2 = index1 == -1 ? -1 : page.indexOf("</a>", index1 + 1);

						if (index2 != -1)
							artists.add(page.substring(index1 + 1, index2));
						else
							artists.add("Unknown Artist");
					}

					index1 = page.indexOf("data-uix-load-more-href=\"");
					index2 = index1 == -1 ? -1 : page.indexOf("\"", index1 + 25);

					if (index2 != -1)
					{
						loadMore = true;
						loadMoreUrl = "https://www.youtube.com" + page.substring(index1 + 25, index2);
					}
					else 
						loadMore = false;
				}
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
			DescriptionAdapter.LoadMoreViewHolder vh = (DescriptionAdapter.LoadMoreViewHolder) recycler.getChildViewHolder(recycler.getChildAt(recycler.getChildCount() - 1));
			vh.text_title.setVisibility(View.VISIBLE);
			vh.progress.setVisibility(View.INVISIBLE);
			
			recycler.getAdapter().notifyDataSetChanged();
			text_description.setText(titles.size() + (loadMore ? "+" : "") + (titles.size() == 1 ? " Video" : " Videos"));
			
			new LoadImageTask().execute();
		}
	}
	
	public class PlaylistTouchHelperCallback extends ItemTouchHelper.Callback
	{
		@Override
		public boolean isLongPressDragEnabled() 
		{
			return true;
		}

		@Override
		public boolean isItemViewSwipeEnabled()
		{
			return true;
		}

		@Override
		public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder)
		{
			if (!(viewHolder instanceof DescriptionAdapter.VideoViewHolder))
				return 0;
			
			int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
			int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
			return makeMovementFlags(dragFlags, swipeFlags);
		}

		@Override
		public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target)
		{
			int fromPosition = viewHolder.getAdapterPosition();
			int toPosition = target.getAdapterPosition();
			
			if (!(viewHolder instanceof DescriptionAdapter.VideoViewHolder) || !(target instanceof DescriptionAdapter.VideoViewHolder))
				return false;
			
			if (fromPosition < toPosition)
			{
				for (int i = fromPosition; i < toPosition; i++)
				{
					Collections.swap(ids, i - 1, i);
					Collections.swap(urls, i - 1, i);
					Collections.swap(titles, i - 1, i);
					Collections.swap(artists, i - 1, i);
				}
			} 
			else
			{
				for (int i = fromPosition; i > toPosition; i--) 
				{
					Collections.swap(ids, i - 1, i - 2);
					Collections.swap(urls, i - 1, i - 2);
					Collections.swap(titles, i - 1, i - 2);
					Collections.swap(artists, i - 1, i - 2);
				}
			}
			
			if (fromPosition - 1 == playlistIndex)
				playlistIndex = toPosition - 1;
			else if (toPosition - 1 == playlistIndex)
				playlistIndex = fromPosition - 1;
			
			recycler.getAdapter().notifyItemMoved(fromPosition, toPosition);
			
			return true;
		}

		@Override
		public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction)
		{
			int index = viewHolder.getAdapterPosition();
			
			urls.remove(index - 1);
			titles.remove(index - 1);
			artists.remove(index - 1);
			if (bitmap_cache.get(ids.get(index - 1)) != null)
				bitmap_cache.get(ids.get(index - 1)).recycle();
			bitmap_cache.remove(ids.get(index - 1));
			ids.remove(index - 1);
			
			text_description.setText(titles.size() + (loadMore ? "+" : "") + (titles.size() == 1 ? " Video" : " Videos"));
			recycler.getAdapter().notifyItemRemoved(index);
			
			if (index - 1 == playlistIndex)
			{
				playlistIndex = 0;
				recycler.getAdapter().notifyItemChanged(1);
			}
		}
	}
}

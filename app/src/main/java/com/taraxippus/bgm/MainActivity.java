package com.taraxippus.bgm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.LruCache;
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
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.message.BasicHeader;
import org.json.JSONArray;
import org.json.JSONObject;
import android.graphics.drawable.NinePatchDrawable;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.StringEntity;
import java.net.URLEncoder;
import org.json.JSONException;

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
	String title = "", artist = "", description = "";
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
	
	private static final SimpleDateFormat formatIn = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	private static final SimpleDateFormat formatOut = new SimpleDateFormat("MMM d, yyyy");
	
	Mode mode;
	
	private enum Mode { YOUTUBE, NICO, NONE }
	
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
		
		if (!NetworkHelper.isInternetAvailable(this))
		{
			progress.setVisibility(View.GONE);
			
			if (layout_title != null)
			{
				layout_title.setVisibility(View.VISIBLE);
				image_menu.setVisibility(View.GONE);
				text_title.setText("No internet connection");
			}
			
			button_cancel.setVisibility(View.VISIBLE);
			return;
		}
		
			
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
							
							if (mode == Mode.YOUTUBE)
								url = "https://www.youtube.com/watch?v=" + id + "&list=RD" + id;
							
							else if (mode == Mode.NICO)
								url = "http://nico.ms/" + id + "?mix=true&title=" + URLDecoder.decode(title);
							
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
										case R.id.item_video:
											item.setChecked(!item.isChecked());
											PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putBoolean("video", item.isChecked()).apply();
											return true;
										
										case R.id.item_visualizer:
											item.setChecked(!item.isChecked());
											PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putBoolean("visualizer", item.isChecked()).apply();
											return true;
											
										case R.id.item_preferences:
											startActivity(new Intent(MainActivity.this, PreferenceActivity.class));
											return true;
											
										case R.id.item_view:
											startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(!playlist ? (url.contains("?") ? url + "&" : url + "?") + "t=" + StringUtils.toUrlTime(seek.getProgress()) : url)));
											return true;
											
										case R.id.item_download:
											if (mode == Mode.NICO)
											{
												String id = NicoHelper.getId(Uri.parse(url));

												if (!NicoHelper.LOGIN)
													NicoHelper.login(MainActivity.this);

												NetworkHelper.get(true, new NetworkHelper.NetworkListener()
													{
														@Override
														public void onServerRequestComplete(String response)
														{
															try
															{
																NetworkHelper.get(true, new NetworkHelper.NetworkListener()
																	{
																		@Override
																		public void onServerRequestComplete(String response)
																		{
																			try
																			{
																				String result = StringUtils.unescapeJava(new JSONObject(response).getJSONObject("data").getString("audio_url"));
																				DownloadManager.Request r = new DownloadManager.Request(Uri.parse(result));

																				r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, Html.fromHtml(title).toString() + ".m4a");
																				r.allowScanningByMediaScanner();
																				r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

																				DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
																				dm.enqueue(r);
																			}
																			catch (JSONException e)
																			{}
																		}

																		@Override
																		public void onErrorOccurred(String errorMessage)
																		{
																			Toast.makeText(MainActivity.this, errorMessage, 0).show();
																		}


																	}, new JSONObject(response).getString("watchApiUrl"), "User-Agent", NicoHelper.USER_AGENT);
															}
															catch (JSONException e)
															{}
														}

														@Override
														public void onErrorOccurred(String errorMessage)
														{
															Toast.makeText(MainActivity.this, errorMessage, 0).show();
														}
												}, "http://api.gadget.nicovideo.jp/v2.0/video/videos/" + id + "/play?playModeCode=auto", "User-Agent", NicoHelper.USER_AGENT);

												return true;
											}
											
											final ArrayList<CharSequence> items = new ArrayList<>();
											final ArrayList<CharSequence> downloadURLs = new ArrayList<>();
											
											int index0, index1, index2, index3 = page.indexOf("url_encoded_fmt_stream_map");
											
											while (true)
											{
												index3 = index0 = page.indexOf("quality=", index3 + 1);
												
												if (index3 == -1)
													break;
												
												try
												{
													index1 = page.indexOf("type=", index3);
													index2 = page.indexOf(",", index3);
													index0 = page.indexOf("\\u0026", index3);
													index2 = index2 == -1 ? index0 : index0 == -1 ? index2 : Math.min(index0, index2);
													
													items.add(page.substring(index1 + 5, page.indexOf("%3B", index1)).replace("%2F", "/") + ", " + page.substring(index3 + 8, index2));
													
													index1 = page.indexOf("url=", index3);
													index2 = index1 == -1 ? -1 : page.indexOf(",", index1);
													index0 = index1 == -1 ? -1 : page.indexOf("\\u0026", index1);
													index2 = index2 == -1 ? index0 : index0 == -1 ? index2 : Math.min(index0, index2);

													downloadURLs.add(URLDecoder.decode(page.substring(index1 + 4, index2)));
												}
												catch (Exception e) {}
											}
											
											AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
											builder.setTitle("Choose quality");
											builder.setItems(items.toArray(new CharSequence[items.size()]), new DialogInterface.OnClickListener()
												{
													public void onClick(DialogInterface dialog, int which) 
													{
														DownloadManager.Request r = new DownloadManager.Request(Uri.parse(downloadURLs.get(which).toString()));

														r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, Html.fromHtml(title).toString() + ".mp4");
														r.allowScanningByMediaScanner();
														r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
													
														DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
														dm.enqueue(r);
													}
												});
											builder.show();
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
						
						popup.getMenu().findItem(R.id.item_video).setChecked(PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getBoolean("video", false));
						popup.getMenu().findItem(R.id.item_visualizer).setChecked(PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getBoolean("visualizer", false));
						if (mode == Mode.NICO)
							popup.getMenu().findItem(R.id.item_view).setTitle("View on NicoNico");
						
						popup.show();
					}
			});
			
			final Drawable thumb =seek.getThumb();
			final Drawable thumb_hidden = new ColorDrawable(Color.TRANSPARENT);
			seek.setThumb(thumb_hidden);
			
			seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
			{
					@Override
					public void onProgressChanged(SeekBar p1, int p2, boolean p3)
					{
						if (text_time != null)
							text_time.setText(StringUtils.toTime(p2));
					}

					@Override
					public void onStartTrackingTouch(SeekBar p1)
					{
						text_time.setVisibility(View.VISIBLE);
						seek.setThumb(thumb);
					}

					@Override
					public void onStopTrackingTouch(SeekBar p1)
					{
						text_time.setVisibility(View.GONE);
						seek.setThumb(thumb_hidden);
					}
			});
				
			if (getIntent().getAction().equals(Intent.ACTION_VIEW))
			{
				url = getIntent().getData().toString();
			}
			else
				url = getIntent().getStringExtra(android.content.Intent.EXTRA_TEXT);
				
			String[] words = url.split(" |\\n");
			
			Uri uri = null;
			mode = Mode.NONE;
			for (String word : words)
			{
				uri = Uri.parse(word);
				
				if (YouTubeHelper.isValidUrl(uri))
				{
					mode = Mode.YOUTUBE;
					url = word;
					break;
				}
				else if (NicoHelper.isValidUrl(uri))
				{
					mode = Mode.NICO;
					url = word;
					break;
				}
			}
			
			if (mode == Mode.NONE)
			{
				progress.setVisibility(View.GONE);

				if (layout_title != null)
				{
					layout_title.setVisibility(View.VISIBLE);
					image_menu.setVisibility(View.GONE);
					text_title.setText("Please use a valid url!");
				}

				button_cancel.setVisibility(View.VISIBLE);
				return;
			}
				
			new ParsePageTask().execute(url);
    }

	final ArrayList<LoadImageTask> loadImageTasks = new ArrayList<>();
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		
		bitmap_cache.trimToSize(0);
		
		for (LoadImageTask task : loadImageTasks)
			task.cancel(true);
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
	
	public class ParsePageTask extends AsyncTask<String, Void, Integer>
	{
		@Override
		protected Integer doInBackground(String[] p1)
		{
			try
			{
				url = p1[0];
				Uri uri = Uri.parse(url);
				
				if (mode == Mode.NONE)
					return -1;
					
				else if (mode == Mode.NICO)
				{
					id = NicoHelper.getId(uri);
					mix = uri.getBooleanQueryParameter("mix", false);
					
					if (!NicoHelper.LOGIN)
						NicoHelper.login(MainActivity.this);
						
					if (mix || NicoHelper.isPlaylist(uri))
					{
						titles.clear();
						artists.clear();
						urls.clear();
						
						playlist = true;
						
						if (mix)
						{
							page = NetworkHelper.getSync(true, null, "http://api.gadget.nicovideo.jp/v1/videos/" + id + "/recommendations", "User-Agent", NicoHelper.USER_AGENT);
							playlistTitle = "Mix - " + uri.getQueryParameter("title");
							playlistIndex = 0;
							ids.add(id);
							urls.add("http://nico.ms/" + id);
							titles.add(uri.getQueryParameter("title"));
							artists.add("");
							
							JSONArray items = new JSONObject(page).getJSONArray("contents");
							int count = items.length();
							for (int i = 0; i < count; ++i)
							{
								ids.add(items.getJSONObject(i).getJSONObject("value").getString("id"));
								urls.add("http://nico.ms/" + ids.get(urls.size()));
								titles.add(items.getJSONObject(i).getJSONObject("value").getString("title"));
								artists.add("");
							}

							float ratio = bitmap_video == null ? 16 / 9F : bitmap_video.getWidth() / (float) bitmap_video.getHeight();
							Bitmap bitmap = NicoHelper.getIcon(ids.get(playlistIndex));
							Bitmap bitmap1;
							if ((float) bitmap.getWidth() / bitmap.getHeight() > ratio)
								bitmap1 = Bitmap.createBitmap(bitmap, (int) ((bitmap.getWidth() - bitmap.getHeight() * ratio) / 2F), 0, (int) (bitmap.getHeight() * ratio), bitmap.getHeight());
							else
								bitmap1 = Bitmap.createBitmap(bitmap, 0, (int) ((bitmap.getHeight() - bitmap.getWidth() / ratio) / 2F), bitmap.getWidth(), (int) (bitmap.getWidth() / ratio));

							if (bitmap != bitmap1)
								bitmap.recycle();

							bitmap_video = bitmap1;
							title = titles.get(playlistIndex);
							artist = artists.get(playlistIndex);
							loadMoreUrl = "http://api.gadget.nicovideo.jp/v1/videos/" + ids.get(ids.size() - 1) + "/recommendations";
							loadMore = true;
						}
						else if (uri.getPathSegments().get(0).equals("tag") || uri.getPathSegments().get(0).equals("search"))
						{
							boolean tag = uri.getPathSegments().get(0).equals("tag");
							page = NetworkHelper.getSync(true, null, "http://api.gadget.nicovideo.jp/video/videos/by_" + (tag ? "tag" : "keyword") + "?sortOrderTypeCode=d&page=0&sortKeyTypeCode=v&pageSize=50&" + (tag ? "tag=" : "keyword=") + URLEncoder.encode(id), "User-Agent", NicoHelper.USER_AGENT);

							JSONObject parsed = new JSONObject(page);	
							playlistTitle = (tag ? "Tag: " : "Search: ") + id;
							playlistIndex = 0;

							JSONArray items = parsed.getJSONArray("items");
							int count = items.length();
							for (int i = 0; i < count; ++i)
								if (!items.getJSONObject(i).isNull("id"))
								{
									ids.add(items.getJSONObject(i).getString("id"));
									urls.add("http://nico.ms/" + ids.get(urls.size()));
									titles.add(items.getJSONObject(i).getString("title"));
									artists.add("");
								}

							float ratio = 16F / 9F;
							if (!items.getJSONObject(playlistIndex).isNull("originalResolution"))
							{
								JSONObject resolution = items.getJSONObject(playlistIndex).getJSONObject("originalResolution");
								ratio = resolution.getInt("width") / (float) resolution.getInt("height");
							}

							Bitmap bitmap = NicoHelper.getIcon(ids.get(playlistIndex));
							Bitmap bitmap1;
							if ((float) bitmap.getWidth() / bitmap.getHeight() > ratio)
								bitmap1 = Bitmap.createBitmap(bitmap, (int) ((bitmap.getWidth() - bitmap.getHeight() * ratio) / 2F), 0, (int) (bitmap.getHeight() * ratio), bitmap.getHeight());
							else
								bitmap1 = Bitmap.createBitmap(bitmap, 0, (int) ((bitmap.getHeight() - bitmap.getWidth() / ratio) / 2F), bitmap.getWidth(), (int) (bitmap.getWidth() / ratio));

							if (bitmap != bitmap1)
								bitmap.recycle();

							bitmap_video = bitmap1;
							title = titles.get(playlistIndex);
							artist = artists.get(playlistIndex);
							
							loadMore = !parsed.isNull("next");
							if (loadMore)
								loadMoreUrl = "http://api.gadget.nicovideo.jp/video/videos/by_" + (tag ? "tag" : "keyword") + "?sortOrderTypeCode=d&page=" + parsed.getInt("next") + "&sortKeyTypeCode=v&pageSize=50&" + (tag ? "tag=" : "keyword=") + URLEncoder.encode(id);
						}
						else
						{
							page = NetworkHelper.getSync(true, null, "http://api.gadget.nicovideo.jp/user/mylists/" + id + "?page=0&pageSize=100", "User-Agent", NicoHelper.USER_AGENT);
						
							JSONObject parsed = new JSONObject(page);	
							playlistTitle = parsed.getString("name");
							playlistIndex = 0;

							JSONObject entries = parsed.getJSONObject("myListEntries");	
							String next = entries.getString("next");

							if (!next.equals("null"))
							{
								loadMore = true;
								loadMoreUrl =  "http://api.gadget.nicovideo.jp/user/mylists/" + id + "?page=" + next + "&pageSize=100";
							}

							JSONArray items = entries.getJSONArray("items");
							int count = items.length();
							for (int i = 0; i < count; ++i)
								if (!items.getJSONObject(i).isNull("videoId"))
								{
									ids.add(items.getJSONObject(i).getString("videoId"));
									urls.add("http://nico.ms/" + ids.get(urls.size()));
									titles.add(items.getJSONObject(i).getString("title"));
									artists.add("");
								}

							float ratio = 16F / 9F;
							if (!items.getJSONObject(playlistIndex).isNull("video") && !items.getJSONObject(playlistIndex).getJSONObject("video").isNull("originalResolution"))
							{
								JSONObject resolution = items.getJSONObject(playlistIndex).getJSONObject("video").getJSONObject("originalResolution");
								ratio = resolution.getInt("width") / (float) resolution.getInt("height");
							}
							
							Bitmap bitmap = NicoHelper.getIcon(ids.get(playlistIndex));
							Bitmap bitmap1;
							if ((float) bitmap.getWidth() / bitmap.getHeight() > ratio)
								bitmap1 = Bitmap.createBitmap(bitmap, (int) ((bitmap.getWidth() - bitmap.getHeight() * ratio) / 2F), 0, (int) (bitmap.getHeight() * ratio), bitmap.getHeight());
							else
								bitmap1 = Bitmap.createBitmap(bitmap, 0, (int) ((bitmap.getHeight() - bitmap.getWidth() / ratio) / 2F), bitmap.getWidth(), (int) (bitmap.getWidth() / ratio));

							if (bitmap != bitmap1)
								bitmap.recycle();

							bitmap_video = bitmap1;
							title = titles.get(playlistIndex);
							artist = artists.get(playlistIndex);
						}
					}
					else
					{
						id = NicoHelper.getId(uri);
						url = "http://nico.ms/" + id;

						page = NetworkHelper.getSync(true, null, "http://api.gadget.nicovideo.jp/video/videos/" + id, "User-Agent", NicoHelper.USER_AGENT);

						JSONObject parsed = new JSONObject(page);	  
						title = parsed.getString("title");

						description = "Published on " + formatOut.format(formatIn.parse(parsed.getString("uploadTime"))) + "・" + String.format("%,d", parsed.getInt("viewCount"), Locale.US).replace(',', '.') + " Views<br /><br />" + parsed.getString("description") + "<br /><br /><b>";	
						JSONArray tags = parsed.getJSONArray("tags");
						int count = tags.length();
						for (int i = 0; i < count; ++i)
							description = description + "<br />【<a href=\"http://nicovideo.jp/tag/" + tags.getJSONObject(i).getString("name") + "/\">" + tags.getJSONObject(i).getString("name") + "</a>】";
						description = description + "</b>";
						duration = parsed.getInt("lengthInSeconds");
						
						float ratio = 16F / 9F;
						if (!parsed.isNull("originalResolution"))
						{
							JSONObject resolution = parsed.getJSONObject("originalResolution");
							ratio = resolution.getInt("width") / (float) resolution.getInt("height");
						}
						
						Bitmap bitmap = NicoHelper.getIcon(id);
						Bitmap bitmap1;
						if ((float) bitmap.getWidth() / bitmap.getHeight() > ratio)
							bitmap1 = Bitmap.createBitmap(bitmap, (int) ((bitmap.getWidth() - bitmap.getHeight() * ratio) / 2F), 0, (int) (bitmap.getHeight() * ratio), bitmap.getHeight());
						else
							bitmap1 = Bitmap.createBitmap(bitmap, 0, (int) ((bitmap.getHeight() - bitmap.getWidth() / ratio) / 2F), bitmap.getWidth(), (int) (bitmap.getWidth() / ratio));

						if (bitmap != bitmap1)
							bitmap.recycle();

						bitmap_video = bitmap1;

						parsed = new JSONObject(NetworkHelper.getSync(true, null, "http://api.gadget.nicovideo.jp/user/profiles/" + parsed.getString("userId"), "User-Agent", NicoHelper.USER_AGENT));	
						artist = parsed.getString("nickname");

						InputStream in;
						try
						{
							in = new URL(parsed.getString("thumbnailUrl")).openStream();
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

						if (uri.getQueryParameterNames().contains("t"))
							time = StringUtils.fromUrlTime(uri.getQueryParameter("t"));
						else 
							time = 0;
						
					}
					
					return 0;
				}
				else
				{
					int index0, index1, index2;

					if (uri.getQueryParameterNames().contains("list"))
					{
						titles.clear();
						artists.clear();
						urls.clear();

						playlist = true;
						boolean playListSite = uri.getLastPathSegment().equals("playlist");

						playListId = uri.getQueryParameter("list");
						
						if (uri.getQueryParameterNames().contains("index"))
							playlistIndex = Integer.parseInt(uri.getQueryParameter("index"));
							
						page = NetworkHelper.getPage(url.replace("http://", "https://"));

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
									urls.add("https://www.youtube.com/watch?v=" + page.substring(index1 + 15, index2));
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
						uri = Uri.parse(url);
					}

					id = YouTubeHelper.getId(uri);
					
					if (uri.getQueryParameterNames().contains("t"))
						time = StringUtils.fromUrlTime(uri.getQueryParameter("t"));
					else 
						time = 0;
						
					url = "https://www.youtube.com/watch?v=" + id;
					page = NetworkHelper.getPage(url);

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

					bitmap_video = YouTubeHelper.getIcon(id);

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

					index0 = page.indexOf("quality=" + PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getString("quality", "high"));
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
					text_title.setText("Couln't parse page.");

				System.out.println("Video unavailable:\n" + url);
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
		
		ClickableSpan newSpan;
		for (URLSpan span : urls) 
		{
			if (span.getURL().equals("#"))
			{
				start = strBuilder.getSpanStart(span);
				end = strBuilder.getSpanEnd(span);
				
				newSpan = new SeekSpan(strBuilder.subSequence(start, end).toString());
				
				strBuilder.setSpan(newSpan, start, end, strBuilder.getSpanFlags(span));
				strBuilder.removeSpan(span);
			}
			else if (span.getURL().startsWith("/"))
			{
				start = strBuilder.getSpanStart(span);
				end = strBuilder.getSpanEnd(span);

				newSpan = new URLSpan("http://www.youtube.com" + span.getURL());

				strBuilder.setSpan(newSpan, start, end, strBuilder.getSpanFlags(span));
				strBuilder.removeSpan(span);
			}
		}
		
		Matcher matcher = Pattern.compile("(mylist/|sm|nm|so|co)\\d\\d\\d\\d\\d\\d\\d+").matcher(sequence);
		while (matcher.find())
			strBuilder.setSpan(new URLSpan("http://nico.ms/" + sequence.subSequence(matcher.start(), matcher.end())), matcher.start(), matcher.end(), 0);
		
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
	
	class LoadImageTask extends AsyncTask<String, Void, Void>
	{

		@Override
		protected void onPreExecute()
		{
			loadImageTasks.add(this);
		}

		@Override
		protected void onPostExecute(Void result)
		{
			loadImageTasks.remove(this);
		}
		
		final float ratio = 16 / 9F;
		@Override
		protected Void doInBackground(String... ids1)
		{
			Bitmap bitmap, bitmap1;
			for (int i = 0; i < ids1.length; ++i)
			{
				if (bitmap_cache.get(ids1[i]) != null)
					continue;
					
				if (isCancelled())
					return null;
					
				try
				{
					InputStream in = new URL(mode == Mode.YOUTUBE ? YouTubeHelper.getSmallIcon(ids1[i]) : NicoHelper.getSmallIcon(ids1[i])).openStream();
					bitmap = BitmapFactory.decodeStream(in);
					
					if (mode == Mode.NICO)
					{
						if ((float) bitmap.getWidth() / bitmap.getHeight() > ratio)
							bitmap1 = Bitmap.createBitmap(bitmap, (int) ((bitmap.getWidth() - bitmap.getHeight() * ratio) / 2F), 0, (int) (bitmap.getHeight() * ratio), bitmap.getHeight());
						else
							bitmap1 = Bitmap.createBitmap(bitmap, 0, (int) ((bitmap.getHeight() - bitmap.getWidth() / ratio) / 2F), bitmap.getWidth(), (int) (bitmap.getWidth() / ratio));

						if (bitmap != bitmap1)
							bitmap.recycle();
							
						bitmap_cache.put(ids1[i], bitmap1);
					}
					else
						bitmap_cache.put(ids1[i], bitmap);
					
					in.close();

					if (isCancelled())
						return null;
					
					MainActivity.this.runOnUiThread(new UpdateRunnable(ids.indexOf(ids1[i]) + 1));
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
										
										if (index < 0)
											return false;
										
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
												
												String url;
												
												if (mode == Mode.YOUTUBE)
													url = "http://www.youtube.com/watch?v=" + ids.get(index - 1) + "&list=RD" + ids.get(index - 1);

												else
													url = "http://nico.ms/" + ids.get(index - 1) + "?mix=true&title=" + URLDecoder.decode(titles.get(index - 1));
												
												intent.putExtra(Intent.EXTRA_TEXT, url);
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
							
							if (mode == Mode.NICO)
								popup.getMenu().findItem(R.id.item_view).setTitle("View on NicoNico");
							
							popup.show();
						}
					});
					
				if (!NetworkHelper.isInternetAvailable(MainActivity.this))
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
							
							new LoadMoreTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
				{
					v.image.setImageResource(R.drawable.launcher);
					new LoadImageTask().execute(ids.get(index - 1));
				}
				else
					v.image.setImageBitmap(bitmap_cache.get(ids.get(index - 1)));
				
				v.overflow.setImageResource(index - 1 == playlistIndex ? R.drawable.dots_vertical : R.drawable.dots_vertical_disabled);
				v.itemView.setBackgroundColor(index - 1 == playlistIndex ? 0xFF303030 : 0xFF424242);
			}
			else if (getItemViewType(index) == 2)
			{
				LoadMoreViewHolder v = (LoadMoreViewHolder) holder;
				
				v.text_title.setVisibility(loadingMore ? View.INVISIBLE : View.VISIBLE);
				v.progress.setVisibility(loadingMore ? View.VISIBLE : View.INVISIBLE);
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
			new ChangeImageTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, ids.get(index));
		}
	}
	
	public class ChangeImageTask extends AsyncTask<String, Void, Bitmap>
	{
		@Override
		protected Bitmap doInBackground(String[] p1)
		{
			Bitmap bitmap = mode == Mode.YOUTUBE ? YouTubeHelper.getIcon(p1[0]) : NicoHelper.getIcon(p1[0]);
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
	
	boolean loadingMore = false;
	
	public class LoadMoreTask extends AsyncTask<String, Void, String>
	{
		@Override
		protected String doInBackground(String[] p1)
		{
			loadingMore = true;
			
			try
			{
				if (mode == Mode.NICO)
				{
					Uri uri = Uri.parse(url);
					
					if (mix)
					{
						page = NetworkHelper.getSync(true, null, loadMoreUrl, "User-Agent", NicoHelper.USER_AGENT);
						
						JSONArray items = new JSONObject(page).getJSONArray("contents");
						int count = items.length();
						for (int i = 0; i < count; ++i)
						{
							ids.add(items.getJSONObject(i).getJSONObject("value").getString("id"));
							urls.add("http://nico.ms/" + ids.get(urls.size()));
							titles.add(items.getJSONObject(i).getJSONObject("value").getString("title"));
							artists.add("");
						}
						
						loadMoreUrl =  "http://api.gadget.nicovideo.jp/v1/videos/" + ids.get(ids.size() - 1) + "/recommendations";
					}
					else if (uri.getPathSegments().get(0).equals("tag") || uri.getPathSegments().get(0).equals("search"))
					{
						boolean tag = uri.getPathSegments().get(0).equals("tag");
						
						page = NetworkHelper.getSync(true, null, loadMoreUrl, "User-Agent", NicoHelper.USER_AGENT);

						JSONObject entries = new JSONObject(page);	
						String next = entries.getString("next");

						if (!next.equals("null"))
						{
							loadMore = true;
							loadMoreUrl = "http://api.gadget.nicovideo.jp/video/videos/by_" + (tag ? "tag" : "keyword") + "?sortOrderTypeCode=d&page=" + next + "&sortKeyTypeCode=v&pageSize=50&" + (tag ? "tag=" : "keyword=") + URLEncoder.encode(id);
						}
						else 
							loadMore = false;

						JSONArray items = entries.getJSONArray("items");
						int count = items.length();
						for (int i = 0; i < count; ++i)
						{
							ids.add(items.getJSONObject(i).getString("id"));
							urls.add("http://nico.ms/" + ids.get(urls.size()));
							titles.add(items.getJSONObject(i).getString("title"));
							artists.add("");
						}
					}
					else
					{
						page = NetworkHelper.getSync(true, null, loadMoreUrl, "User-Agent", NicoHelper.USER_AGENT);

						JSONObject entries = new JSONObject(page).getJSONObject("myListEntries");	
						String next = entries.getString("next");

						if (!next.equals("null"))
						{
							loadMore = true;
							loadMoreUrl =  "http://api.gadget.nicovideo.jp/user/mylists/" + id + "?page=" + next + "&pageSize=100";
						}
						else loadMore = false;

						JSONArray items = entries.getJSONArray("items");
						int count = items.length();
						for (int i = 0; i < count; ++i)
						{
							ids.add(items.getJSONObject(i).getString("videoId"));
							urls.add("http://nico.ms/" + ids.get(urls.size()));
							titles.add(items.getJSONObject(i).getString("title"));
							artists.add("");
						}
					}
				}
				else if (mode == Mode.YOUTUBE)
				{
					page = NetworkHelper.getPage(loadMoreUrl);

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
								urls.add("https://www.youtube.com/watch?v=" + page.substring(index1 + 15, index2));
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
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
			loadingMore = false;
			return null;
		}

		@Override
		protected void onPostExecute(String result)
		{
			recycler.getAdapter().notifyDataSetChanged();
			text_description.setText(titles.size() + (loadMore ? "+" : "") + (titles.size() == 1 ? " Video" : " Videos"));
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

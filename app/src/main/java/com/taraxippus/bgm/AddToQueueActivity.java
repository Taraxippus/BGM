package com.taraxippus.bgm;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;

public class AddToQueueActivity extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        finish();
		
		String[] urls = getIntent().getStringExtra(android.content.Intent.EXTRA_TEXT).split(" ");
		
		ArrayList<String> validURLs = new ArrayList<>();
		
		Uri uri;
		for (String url : urls)
		{
			try
			{
				uri = Uri.parse(url);
				if (YouTubeHelper.isValidUrl(uri) || NicoHelper.isValidUrl(uri))
					validURLs.add(url);
			}
			catch (Exception e) {}
		}
		
		Intent intent = new Intent(this, BGMService.class);
		intent.setAction(BGMService.ACTION_PLAY);

		intent.putExtra("repeat", false);
		intent.putExtra("shuffle", false);
		intent.putExtra("urls", validURLs.toArray(new String[validURLs.size()]));
		String[] loading = new String[validURLs.size()];
		Arrays.fill(loading, 0, validURLs.size(), "Loading...");
		intent.putExtra("titles", loading);
		intent.putExtra("artists", validURLs.toArray(new String[validURLs.size()]));
		intent.putExtra("add", true);
		intent.putExtra("time", 0);
		intent.putExtra("index", 0);

		startService(intent);
		
		Toast.makeText(this, "Added " + (validURLs.size() == 1 ? "1 Track" : validURLs.size() + " Tracks") + " to Queue", Toast.LENGTH_SHORT).show();
    }
}

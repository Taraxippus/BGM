package com.taraxippus.bgm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import java.net.URL;

public class AddToQueueActivity extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        finish();
		
		String url = getIntent().getStringExtra(android.content.Intent.EXTRA_TEXT);
		
		try
		{
			new URL(url);
		}
		catch (Exception e)
		{
			Toast.makeText(this, "This is not an URL to a youtube video!", Toast.LENGTH_SHORT).show();
			return;
		}
		
		Intent intent = new Intent(this, BGMService.class);
		intent.setAction(BGMService.ACTION_PLAY);

		intent.putExtra("repeat", false);
		intent.putExtra("shuffle", false);
		intent.putExtra("urls", new String[] {url});
		intent.putExtra("titles", new String[] {"Loading..."});
		intent.putExtra("artists", new String[] {url});
		intent.putExtra("add", true);
		intent.putExtra("time", 0);
		intent.putExtra("index", 0);

		startService(intent);
		
		Toast.makeText(this, "Added to queue", Toast.LENGTH_SHORT).show();
    }
}

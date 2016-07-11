package com.taraxippus.bgm;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebViewActivity extends Activity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		WebView webView = new WebView(this);
		webView.setWebViewClient(new WebViewClient() 
		{  
				@Override  
				public boolean shouldOverrideUrlLoading(WebView view, String url)  
				{  
					return false;
				}
			}); 
		webView.getSettings().setJavaScriptEnabled(true);
		webView.loadUrl("https://m.youtube.com/signin");
		
		setContentView(webView);
		CookieSyncManager.createInstance(this);
		CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		
		CookieSyncManager.getInstance().startSync();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		
		CookieSyncManager.getInstance().stopSync();
	}
}

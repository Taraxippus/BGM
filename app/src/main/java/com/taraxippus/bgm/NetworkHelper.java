package com.taraxippus.bgm;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkHelper
{
	static CookieManager cookieManager;
	
	static
	{
		cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		CookieHandler.setDefault(cookieManager);
	}
	
	public static String getPage(String url)
	{
		return getSync(true, null, url);
	}
	
	public static boolean isInternetAvailable(Context context) 
	{
		return ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null;
    }
	
	public static void addCookie(String key, String value, String domain)
	{
		cookieManager.getCookieStore().add(null, new HttpCookie(key, value));
	}
	
	public static String getCookie(String key)
	{
		for (HttpCookie cookie : cookieManager.getCookieStore().getCookies())
			if (cookie.getName() == key)
				return cookie.getValue();
			
		return null;
	}
	
	public static String getSync(boolean get, NetworkListener listener, String url, String... headers)
	{
		HashMap<String, String> headerMap = new HashMap<>();
		for (int i = 0; i < headers.length - 1; i += 2)
			headerMap.put(headers[i], headers[i + 1]);
			
		return new URLTask(listener, headerMap, get).doInBackground(url);
	}
	
	public static void get(boolean get, NetworkListener listener, String url, String... headers)
	{
		HashMap<String, String> headerMap = new HashMap<>();
		for (int i = 0; i < headers.length - 1; i += 2)
			headerMap.put(headers[i], headers[i + 1]);

		new URLTask(listener, headerMap, get).execute(url);
	}
	
	public static interface NetworkListener
	{
		void onServerRequestComplete(String response);
		void onErrorOccurred(String errorMessage);
	}
	
	public static class URLTask extends AsyncTask<String, Void, String>
	{
		final NetworkListener listener;
		final Map<String, String> headers;
		final boolean get;
		
		public URLTask(NetworkListener listener, Map<String, String> headers, boolean get)
		{
			this.listener = listener;
			this.headers = headers;
			this.get = get;
		}
		
		@Override
        protected String doInBackground(String... params)
        {
			try
			{
				URL url = new URL(params[0]);
				HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.setRequestProperty("User-Agent", System.getProperty("http.agent").replace(" Mobile", "").replace("Android", "Linux"));
				urlConnection.setReadTimeout(10000 /* milliseconds */);
				urlConnection.setConnectTimeout(15000 /* milliseconds */);
				urlConnection.setRequestMethod(get ? "GET" : "POST");
				urlConnection.setInstanceFollowRedirects(true);
				
				if (cookieManager.getCookieStore().getCookies().size() > 0)
					urlConnection.setRequestProperty("Cookie", TextUtils.join(";",  cookieManager.getCookieStore().getCookies()));    
				
				if (headers != null)
					for (String s : headers.keySet())
						if (s != null && headers.get(s) != null)
							urlConnection.setRequestProperty(s, headers.get(s));
				
				try
				{
					if (!get)
						urlConnection.setDoInput(true);
					
					if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK)
					{
						BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
						StringBuilder sb = new StringBuilder();

						String line;
						while ((line = reader.readLine()) != null)
						{
							sb.append(line);
						}

						reader.close();
						

						Map<String, List<String>> headerFields = urlConnection.getHeaderFields();
						List<String> cookiesHeader = headerFields.get("Set-Cookie");

						if (cookiesHeader != null)
							for (String cookie : cookiesHeader)
								cookieManager.getCookieStore().add(null, HttpCookie.parse(cookie).get(0));
							           
						return sb.toString();
					}
					else
					{
						if (listener != null)
							listener.onErrorOccurred("Bad response: " + urlConnection.getResponseMessage());
							
						System.out.println(urlConnection + ": StatusCode=" + urlConnection.getResponseCode() + ", ReasonPhrase=" + urlConnection.getResponseMessage());
					}
				}
				catch (Exception e)
				{
					if (listener != null)
						listener.onErrorOccurred(e.getMessage());
						
					e.printStackTrace();
				}
				finally 
				{
					urlConnection.disconnect();
				}
			}
			catch (Exception e)
			{
				if (listener != null)
					listener.onErrorOccurred(e.getMessage());
					
				e.printStackTrace();
			}
			
			return null;
		}
		
		@Override
        protected void onPostExecute(String response)
		{
			if (response != null && listener != null)
				listener.onServerRequestComplete(response);
		}
	}
}

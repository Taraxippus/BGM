package com.taraxippus.bgm;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import com.taraxippus.bgm.LoginActivity;
import com.taraxippus.bgm.NetworkHelper;
import java.io.InputStream;
import java.net.URL;
import org.apache.http.message.BasicHeader;

public class NicoHelper
{
	public static final String USER_AGENT = "Niconico/1.0 (Linux; U; Android " + Build.VERSION.RELEASE + "; en_US; nicobox_android " + Build.MODEL + ") Version/1.6.0";
	public static boolean LOGIN = false;
	
	public static void login(final Context context)
	{
		if (!NetworkHelper.isInternetAvailable(context))
			return;
		
		String user_session = PreferenceManager.getDefaultSharedPreferences(context).getString("nico_user_session", "");
		
		if (user_session.isEmpty())
			context.startActivity(new Intent(context, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		
		else
		{
			NetworkHelper.addCookie("user_session", user_session, ".nicovideo.jp");
			NetworkHelper.addCookie("SP_SESSION_KEY", user_session, ".nicovideo.jp");
			NetworkHelper.addCookie("nicosid", PreferenceManager.getDefaultSharedPreferences(context).getString("nico_sid", ""), ".nicovideo.jp");
			
			NetworkHelper.get(true, new NetworkHelper.NetworkListener()
				{
					@Override
					public void onServerRequestComplete(String response)
					{
						LOGIN = true;
					}

					@Override
					public void onErrorOccurred(String errorMessage)
					{
						context.startActivity(new Intent(context, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
					}

				}, "https://public.api.nicovideo.jp/v1/user.json?additionals=premium", "User-Agent", USER_AGENT);
		}
	}
	
	public static boolean isValidUrl(Uri uri)
	{
		return uri != null && uri.getHost() != null && (uri.getHost().equals("nicovideo.jp") || uri.getHost().equals("www.nicovideo.jp") || uri.getHost().equals("sp.nicovideo.jp") || uri.getHost().equals("nico.ms"));
	}
	
	public static boolean isPlaylist(Uri uri)
	{
		return uri.getPathSegments().get(0).equals("mylist") || uri.getPathSegments().get(0).equals("tag") || uri.getPathSegments().get(0).equals("search");
	}
	
	public static String getId(Uri uri)
	{
		return uri.getLastPathSegment();
	}
	
	public static String getSmallIcon(String id)
	{
		return "http://tn-skr3.smilevideo.jp/smile?i=" + id.substring(2);
	}
	
	public static Bitmap getIcon(String id)
	{
		InputStream in = null;
		Bitmap bitmap = null;

		try
		{
			in = new URL("http://tn-skr3.smilevideo.jp/smile?i=" + id.substring(2) + ".L").openStream();
		}
		catch (Exception e1)
		{
			try
			{
				in = new URL("http://tn-skr3.smilevideo.jp/smile?i=" + id.substring(2) + ".M").openStream();
			}
			catch (Exception e3)
			{
				try
				{
					in = new URL("http://tn-skr3.smilevideo.jp/smile?i=" + id.substring(2)).openStream();
				}
				catch (Exception e4)
				{
					in = null;
					bitmap = null;
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
}

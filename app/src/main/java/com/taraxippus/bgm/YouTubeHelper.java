package com.taraxippus.bgm;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.InputStream;
import java.net.URL;

public class YouTubeHelper
{
	public static boolean isValidUrl(Uri uri)
	{
		return uri != null && uri.getHost() != null && (uri.getHost().equals("www.youtube.com") || uri.getHost().equals("youtube.com") || uri.getHost().equals("m.youtube.com") || uri.getHost().equals("youtu.be"));
	}
	
	public static String getId(Uri uri)
	{
		return uri.getHost().equals("youtu.be") ? uri.getLastPathSegment() : uri.getQueryParameter("v");
	}
	
	public static String getSmallIcon(String id)
	{
		return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
	}
	
	public static Bitmap getIcon(String id)
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
}

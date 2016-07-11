package com.taraxippus.bgm;

import android.webkit.CookieManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import java.io.IOException;

public class NetworkHelper
{
	public static String getPage(String url) throws IOException 
	{
		BasicCookieStore store = getCookieStore(CookieManager.getInstance().getCookie("youtube.com"), "youtube.com");
		HttpContext context = new BasicHttpContext();
		DefaultHttpClient httpclient = new DefaultHttpClient(); 
//		httpclient.setCookieStore(store);
//		context.setAttribute(ClientContext.COOKIE_STORE, store);
		HttpResponse response = httpclient.execute(new HttpGet(url), context); 
		BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

		StringBuilder sb = new StringBuilder();

		String line;
		while ((line = reader.readLine()) != null)
		{
			sb.append(line);
		}

		reader.close();
		return sb.toString();
	}
	
	public static BasicCookieStore getCookieStore(String cookies, String domain)
	{
		BasicCookieStore cs = new BasicCookieStore();
		
		if (cookies == null)
			return cs;
			
        String[] cookieValues = cookies.split(";");
        
        BasicClientCookie cookie;
        for (int i = 0; i < cookieValues.length; i++)
		{
            String[] split = cookieValues[i].split("=");
			
            if (split.length == 2)
                cookie = new BasicClientCookie(split[0], split[1]);
            else
                cookie = new BasicClientCookie(split[0], null);

            //cookie.setDomain(domain);
            cs.addCookie(cookie);
        }
        return cs;

	}
}

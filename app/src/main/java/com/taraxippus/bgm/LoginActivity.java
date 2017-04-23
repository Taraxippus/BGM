package com.taraxippus.bgm;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.taraxippus.bgm.NetworkHelper;
import com.taraxippus.bgm.R;
import java.net.URLEncoder;
import android.widget.EditText;
import org.apache.http.message.BasicHeader;

public class LoginActivity extends Activity
{
	EditText text_username, text_password;
	View progress, button_login;
	
	public LoginActivity()
	{}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dialog_login);
		
		progress = findViewById(R.id.progress);
		text_username = (EditText) findViewById(R.id.text_username);
		text_password = (EditText) findViewById(R.id.text_password);
		button_login = findViewById(R.id.button_login);
		
		button_login.setOnClickListener(new View.OnClickListener()
			{

				@Override
				public void onClick(View p1)
				{
					button_login.setVisibility(View.GONE);
					progress.setVisibility(View.VISIBLE);
					text_username.setEnabled(false);
					text_password.setEnabled(false);
					
					NetworkHelper.get(false, new NetworkHelper.NetworkListener()
						{
							@Override
							public void onServerRequestComplete(String response)
							{
								if (response.contains("<session_key>"))
								{
									String user_session = response.substring(response.indexOf("<session_key>") + 13, response.indexOf("</session_key>"));
									String sid = NetworkHelper.getCookie("nicosid");
									NetworkHelper.addCookie("user_session", user_session, ".nicovideo.jp");
									NetworkHelper.addCookie("SP_SESSION_KEY", user_session, ".nicovideo.jp");
									PreferenceManager.getDefaultSharedPreferences(LoginActivity.this).edit()
									.putString("nico_user_session", user_session)
									.putString("nico_sid", sid).apply();
									Toast.makeText(LoginActivity.this, "Logged In", Toast.LENGTH_SHORT).show();
									NicoHelper.LOGIN = true;
									finish();
								}
								else
									runOnUiThread(new OnErrorRunnable());
							}

							@Override
							public void onErrorOccurred(String errorMessage)
							{
								runOnUiThread(new OnErrorRunnable());
							}

						}, "https://account.nicovideo.jp/api/v1/login?site=nicobox_android&mail_tel=" + URLEncoder.encode(((TextView) findViewById(R.id.text_username)).getText().toString()) + "&password=" + URLEncoder.encode(((TextView) findViewById(R.id.text_password)).getText().toString()), null, "User-Agent", NicoHelper.USER_AGENT);
					
				}
		});
	}
	
	public class OnErrorRunnable implements Runnable
	{
		@Override
		public void run()
		{
			Toast.makeText(LoginActivity.this, "Try again", Toast.LENGTH_SHORT).show();
			
			button_login.setVisibility(View.VISIBLE);
			progress.setVisibility(View.GONE);
			text_username.setEnabled(true);
			text_password.setEnabled(true);
			text_password.getText().clear();
		}
	}
}

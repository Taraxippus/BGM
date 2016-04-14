package com.taraxippus.bgm;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class PreferenceActivity extends Activity
{
	public PreferenceActivity()
	{
		super();
	}
	
	@Override
    protected void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferenceFragment()).commit();
    }

	public static class PreferenceFragment extends android.preference.PreferenceFragment
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			addPreferencesFromResource(R.xml.preferences);
		}
	}
}

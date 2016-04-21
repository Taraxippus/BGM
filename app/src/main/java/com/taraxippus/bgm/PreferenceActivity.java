package com.taraxippus.bgm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import com.taraxippus.bgm.R;

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
			
			chooseValue("volume", "Volume", "", 0, 1, 20, 1);
		}
		
		public void chooseValue(final String key, final String name, final String unit, final float min, final float max, final int scale, final float def)
		{
			final Preference p = findPreference(key);

			if (p == null)
			{
				System.err.println("Couldn't find preference: " + key);
				return;
			}

			final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());

			final String summary = p.getSummary().toString();

			p.setSummary(summary + "\nCurrent: "
						 + (int) (preferences.getFloat(key, def) * 100) / 100F + unit);

			p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
				{
					@Override
					public boolean onPreferenceClick(Preference p1)
					{
						final float last = preferences.getFloat(key, def);

						final AlertDialog alertDialog = new AlertDialog.Builder(getContext()).create();
						alertDialog.setTitle("Change " + name);

						final View v = getActivity().getLayoutInflater().inflate(R.layout.preference_slider, null);
						alertDialog.setView(v);

						final SeekBar slider = (SeekBar) v.findViewById(R.id.slider);
						slider.setMax((int) ((max - min) * scale));
						slider.setProgress((int) (scale * (last - min)));

						final TextView text_value = (TextView) v.findViewById(R.id.text_value);
						text_value.setText(String.format("%.2f", (int) (last * 100) / 100F) + unit);

						slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
							{
								@Override
								public void onProgressChanged(SeekBar p1, int p2, boolean p3)
								{
									preferences.edit().putFloat(key, (float) slider.getProgress() / scale + min).commit();

									text_value.setText(String.format("%.2f", (int) (preferences.getFloat(key, def) * 100) / 100F) + unit);

									p.setSummary(summary + "\nCurrent: "
												 + (int) (preferences.getFloat(key, def) * 100) / 100F + unit);
								}

								@Override
								public void onStartTrackingTouch(SeekBar p1) {}

								@Override
								public void onStopTrackingTouch(SeekBar p1) {}
							});

						alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new AlertDialog.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface p1, int p2)
								{
									alertDialog.dismiss();
								}
							});
						alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", new AlertDialog.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface p1, int p2)
								{
									preferences.edit().putFloat(key, last).commit();

									p.setSummary(summary + "\nCurrent: "
												 + (int) (preferences.getFloat(key, def) * 100) / 100F + unit);

									alertDialog.cancel();
								}
							});
						alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Reset", new AlertDialog.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface p1, int p2) {}
							});

						alertDialog.show();

						alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener()
							{
								@Override
								public void onClick(View p1)
								{
									slider.setProgress((int) ((def - min) * scale));
								}
							});

						return true;
					}
				});
		}
	}
}

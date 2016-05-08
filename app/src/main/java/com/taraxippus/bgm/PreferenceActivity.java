package com.taraxippus.bgm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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
			chooseColor("color", "#FFFFFF");
		}
		
		public void chooseColor(final String sharedPreference, final String def)
		{
			final Preference p = findPreference(sharedPreference);
			final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
			
			if (p == null)
			{
				System.err.println("Couldn't find preference: " + sharedPreference);
				return;
			}

			try
			{
				Color.parseColor(preferences.getString(sharedPreference, def));
			}
			catch (Exception e)
			{
				preferences.edit().putString(sharedPreference, def).apply();
			}

			int colorInt = 0xFF000000 | Color.parseColor(preferences.getString(sharedPreference, def));
			p.setIcon(getIcon(colorInt));

			p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
				{
					@Override
					public boolean onPreferenceClick(Preference p1)
					{
						int colorInt = Color.parseColor(preferences.getString(sharedPreference, def));

						final AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
						alertDialog.setTitle("Choose color");

						final View v = getActivity().getLayoutInflater().inflate(R.layout.color, null);
						alertDialog.setView(v);

						final View color = v.findViewById(R.id.color);
						color.getBackground().setColorFilter(0xFF000000 | colorInt, PorterDuff.Mode.MULTIPLY);

						final SeekBar red = (SeekBar) v.findViewById(R.id.red);
						red.setProgress(Color.red(colorInt));
						final SeekBar green = (SeekBar) v.findViewById(R.id.green);
						green.setProgress(Color.green(colorInt));
						final SeekBar blue = (SeekBar) v.findViewById(R.id.blue);
						blue.setProgress(Color.blue(colorInt));
						final EditText hex = (EditText) v.findViewById(R.id.hex);
						hex.setText(Integer.toHexString(colorInt & 0x00_FFFFFF).toUpperCase());

						SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener()
						{
							@Override
							public void onProgressChanged(SeekBar p1, int p2, boolean p3)
							{
								int colorInt = fromRGB(red.getProgress(), green.getProgress(), blue.getProgress());
								color.getBackground().setColorFilter(colorInt,PorterDuff.Mode.MULTIPLY);
								hex.setText(Integer.toHexString(colorInt).substring(2).toUpperCase());
							}

							@Override
							public void onStartTrackingTouch(SeekBar p1)
							{

							}

							@Override
							public void onStopTrackingTouch(SeekBar p1)
							{

							}
						};
						red.setOnSeekBarChangeListener(listener);
						green.setOnSeekBarChangeListener(listener);
						blue.setOnSeekBarChangeListener(listener);

						hex.setOnEditorActionListener(new EditText.OnEditorActionListener()
							{
								@Override
								public boolean onEditorAction(TextView p1, int p2, KeyEvent p3)
								{
									if (p2 == EditorInfo.IME_ACTION_GO)
									{
										int colorInt = p1.getText().length() == 0 ? 0 : Integer.parseInt(p1.getText().toString(), 16);
										color.getBackground().setColorFilter(0xFF000000 | colorInt, PorterDuff.Mode.MULTIPLY);
										red.setProgress(Color.red(colorInt));
										green.setProgress(Color.green(colorInt));
										blue.setProgress(Color.blue(colorInt));

										return false;
									}
									return true;
								}	
							});

						alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Choose", new AlertDialog.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface p1, int p2)
								{
									int colorInt = fromRGB(red.getProgress(), green.getProgress(), blue.getProgress());
									hex.setText(Integer.toHexString(colorInt).substring(2).toUpperCase());

									preferences.edit().putString(sharedPreference, "#" + hex.getText().toString()).apply();
									p.setIcon(getIcon(0xFF000000 | colorInt));

									alertDialog.dismiss();
								}
							});
						alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", new AlertDialog.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface p1, int p2)
								{
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
									hex.setText(def.toUpperCase());
									int colorInt = Integer.parseInt(def.substring(1), 16);
									color.getBackground().setColorFilter(0xFF000000 | colorInt, PorterDuff.Mode.MULTIPLY);
									red.setProgress(Color.red(colorInt));
									green.setProgress(Color.green(colorInt));
									blue.setProgress(Color.blue(colorInt));
								}
							});

						return true;
					}
				});
		}

		public Drawable getIcon(int color)
		{
			Drawable circle = getActivity().getResources().getDrawable(R.drawable.circle);
			circle.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
			return circle;
		}

		public static int fromRGB(int red, int green, int blue)
		{
			red = (red << 16) & 0x00FF0000;
			green = (green << 8) & 0x0000FF00;
			blue = blue & 0x000000FF;
			return 0xFF000000 | red | blue | green;
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

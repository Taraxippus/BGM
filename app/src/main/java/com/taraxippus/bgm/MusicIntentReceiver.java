package com.taraxippus.bgm;

import android.content.Context;
import android.content.Intent;

public class MusicIntentReceiver extends android.content.BroadcastReceiver
{
	@Override
	public void onReceive(Context ctx, Intent intent) 
	{
		if (intent.getAction().equals(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY))
		{
			Intent intent1 = new Intent(ctx.getApplicationContext(), BGMService.class);
			intent1.setAction(BGMService.ACTION_PAUSE);
			ctx.startService(intent1);
		}
	}
}
	


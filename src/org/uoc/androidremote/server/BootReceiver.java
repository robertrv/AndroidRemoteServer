package org.uoc.androidremote.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Intent mServiceIntent = new Intent();
		mServiceIntent.setAction("org.uoc.androidremote.server.ServersWatchDog");
		context.startService(mServiceIntent);
	}
}
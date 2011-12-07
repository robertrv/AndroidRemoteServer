package org.uoc.androidremote.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class BootReceiver extends BroadcastReceiver {

	private static final String LOGTAG = BootReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle b = new Bundle();
		b.putBoolean("startVnc", true);
		b.putBoolean("startMng", true);
		Utils.tryStartService(context, LOGTAG,
				ServersControllerService.class.getCanonicalName(), b);
	}

}
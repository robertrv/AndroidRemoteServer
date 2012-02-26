/*
 *  This file is part of Android Remote.
 *
 *  Android Remote is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Leeser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  Android Remote is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Leeser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uoc.androidremote.server;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Mainly takes care of preparing the environment to run the two servers on the 
 * device
 */
public class AppServer extends Application {

	private static final String LOGTAG = "VNC";

	/* (non-Javadoc)
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate(); 

		if (isFirstRun()) {
			createBinary();
		}
	}

	public boolean isFirstRun()
	{
		int versionCode = 0;
		try {
			versionCode = getPackageManager()
			.getPackageInfo(getPackageName(), PackageManager.GET_META_DATA)
			.versionCode;
		} catch (NameNotFoundException e) {
			Log.e(LOGTAG, "Package not found... Odd, since we're in that package...", e);
		}

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int lastFirstRun = prefs.getInt("last_run", 0);

		if (lastFirstRun >= versionCode) {
			Log.d(LOGTAG, "Not first run");
			return false;
		}
		Log.d(LOGTAG, "First run for version " + versionCode);

		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt("last_run", versionCode);
		editor.commit();
		return true;
	}

	public void createBinary()  
	{
		copyBinary(R.raw.androidvncserver, getFilesDir().getAbsolutePath() + "/androidvncserver");
		copyBinary(R.raw.indexvnc, getFilesDir().getAbsolutePath()+"/index.vnc");

		Process sh;
		try {
			sh = Runtime.getRuntime().exec("su");

			OutputStream os = sh.getOutputStream();

			writeCommand(os, "killall androidvncserver");
			writeCommand(os, "killall -KILL androidvncserver");			
			//chmod 777 SHOULD exist
			writeCommand(os, "chmod 777 " + getFilesDir().getAbsolutePath() + "/androidvncserver");
			os.close();
		} catch (IOException e) {
			Log.v(LOGTAG,e.getMessage());		
		}catch (Exception e) {
			Log.v(LOGTAG,e.getMessage());		
		}
	}

	public void copyBinary(int id,String path)
	{
		try {
			InputStream ins = getResources().openRawResource(id);
			int size = ins.available();

			// Read the entire resource into a local byte buffer.
			byte[] buffer = new byte[size];
			ins.read(buffer);
			ins.close();

			FileOutputStream fos = new FileOutputStream(path);
			fos.write(buffer);
			fos.close();
		}
		catch (Exception e)
		{
			Log.v(LOGTAG,"public void createBinary(): " + e.getMessage());
		}


	}  

	static void writeCommand(OutputStream os, String command) throws Exception
	{
		os.write((command + "\n").getBytes("ASCII"));
	} 
	

}

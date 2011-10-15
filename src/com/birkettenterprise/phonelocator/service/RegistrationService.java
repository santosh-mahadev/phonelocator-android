/**
 * 
 *  Copyright 2011 Birkett Enterprise Ltd
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * 
 */

package com.birkettenterprise.phonelocator.service;

import java.util.Iterator;
import java.util.Vector;

import com.birkettenterprise.phonelocator.protocol.RegistrationResponse;
import com.birkettenterprise.phonelocator.protocol.Session;
import com.birkettenterprise.phonelocator.util.Setting;
import com.birkettenterprise.phonelocator.util.SettingsHelper;
import com.birkettenterprise.phonelocator.util.SettingsManager;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class RegistrationService extends Service {

    private final IBinder mBinder = new RegistrationServiceBinder();
    
    private Handler mHandler;
    private Session mSession;
    private Throwable mException;
    private Vector<Runnable> mObservers;
    private RegistrationResponse mRegistrationResponse;
    private RegisrationRunnable mRegisrationRunnable;
    private Thread mWorkerThread;
    
    private class RegisrationRunnable implements Runnable {

		public void run() {
			SettingsManager settingsManager = SettingsManager.getInstance(this, RegistrationService.this);
			
			try {
				mSession.connect();
				mRegistrationResponse = mSession.register();
				mSession.authenticate(mRegistrationResponse.getAuthenticationToken());
				
				Vector<Setting> settings =  mSession.synchronizeSettings(null);
				settingsManager.setSettings(settings);
				
				SettingsHelper.storeResponse(PreferenceManager.getDefaultSharedPreferences(RegistrationService.this), mRegistrationResponse.getAuthenticationToken(), mRegistrationResponse.getRegistrationUrl());
			} catch (Throwable e) {
				mException = e;
			} finally {
				settingsManager.releaseInstance(this);
				settingsManager = null;
				try {
					mSession.close();
				} catch (Throwable e) {
					// ignore
				}
			}
			updateObservers();

			mWorkerThread = null;
		}
    	
    }
    
	public class RegistrationServiceBinder extends Binder {
        public RegistrationService getService() {
            return RegistrationService.this;
        }
    }
	
	private void updateObservers() {
		synchronized (mObservers) {
			Iterator<Runnable> iterator = mObservers.iterator();
			while (iterator.hasNext()) {
				Runnable nextRunnable = iterator.next();
				mHandler.post(nextRunnable);
			}
			mObservers.removeAllElements();
		}

	}

    @Override
    public void onCreate() {
    	super.onCreate();
    	mHandler = new Handler();
    	mSession = new Session();
    	mObservers = new Vector<Runnable>();
    	mRegisrationRunnable = new RegisrationRunnable();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	return START_STICKY;
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    public void register() {
    	if (mWorkerThread != null) {
    		throw new RuntimeException("already registering");
    	}
    	mRegistrationResponse = null;
    	mException = null;
    	mWorkerThread = new Thread(mRegisrationRunnable);
    	mWorkerThread.start();
    }
    
    public void addObserver(Runnable observer) {
    	synchronized (mObservers) {
    		mObservers.add(observer);
    	}
    }
    
    public void removeObserver(Runnable observer) {
    	synchronized (mObservers) {
    		mObservers.remove(observer);
    	}
    }
    
    public boolean isSuccess() {
    	return mRegistrationResponse != null && mException == null;
    }
    
    public RegistrationResponse getResponse() {
    	return mRegistrationResponse;
    }
    
    public Throwable getException() {
    	return mException;
    }
    
    public boolean isRunning() {
    	return mWorkerThread != null;
    }
}

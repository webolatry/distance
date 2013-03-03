/*
 * Copyright (C) 2013 Tom Bruns
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

/*
 * Code style - following Android standards 
 */

package com.webolatry.distance;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;

import com.webolatry.distance.service.Point;
import com.webolatry.distance.service.Service;
import com.webolatry.distance.service.ServiceException;

/**
 * Android activity with controls for obtaining two gps positions and calling a
 * service to compute the distance between those points
 * 
 * @author Tom
 * 
 */
public class DistanceActivity extends Activity implements LocationListener {

	/** the Android gps service */
	private LocationManager mLocationManager;

	/** output for gps status */
	private TextView mEditStatus;
	/** output for the start location */
	private TextView mEditPoint1;
	/** output for the end location */
	private TextView mEditPoint2;
	/** output for the computed distance */
	private TextView mEditDistance;

	/** current running get-distance task */
	private DistanceTask mTask;

	/** current location from gps */
	private Point mGPSPoint;
	/** user-set start location */
	private Point mPoint1;
	/** user-set end location */
	private Point mPoint2;

	/** for persisting the state of user-set point 1 */
	private static final String STATE_POINT1 = "STATE_POINT1";
	/** for persisting the state of user-set point 2 */
	private static final String STATE_POINT2 = "STATE_POINT2";

	/**
	 * Called when the activity is starting (Activity override)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/* get the various ui elements */
		mEditStatus = (TextView) findViewById(R.id.editStatus);
		mEditPoint1 = (TextView) findViewById(R.id.editPoint1);
		mEditPoint2 = (TextView) findViewById(R.id.editPoint2);
		mEditDistance = (TextView) findViewById(R.id.editDistance);

		/*
		 * if an error occurs restoring the point state, display a message
		 */
		boolean error = false;

		/* restore the saved point1, if available */
		try {
			mPoint1 = restorePointState(STATE_POINT1);
		} catch (IOException e) {
			e.printStackTrace();
			error = true;
		}

		/* restore the saved point2, if available */
		try {
			mPoint2 = restorePointState(STATE_POINT2);
		} catch (IOException e) {
			e.printStackTrace();
			error = true;
		}

		if (error) {
			/* if one point failed, consider both bad */
			mPoint1 = null;
			mPoint2 = null;
			showMessage(
					"An error occurred restoring saved start and end locations",
					"Error");
		}

		/* populate UI with restored point values */
		if (mPoint1 != null) {
			mEditPoint1.setText(formatPoint(mPoint1));
		} else {
			mEditPoint1.setText("");
		}
		
		if (mPoint2 != null) {
			mEditPoint2.setText(formatPoint(mPoint2));
		} else {
			mEditPoint2.setText("");
		}
		
		/* get the gps service */
		mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

		/* set the initial gps status message */
		if (mLocationManager != null) {
			mEditStatus.setText("Waiting for GPS");
		} else {
			mEditStatus.setText("GPS Not Supported");
		}
		
		/* button handler - gps settings */
		ImageButton buttonStatus = (ImageButton) findViewById(R.id.buttonStatus);
		buttonStatus.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				Intent intent = new Intent(
						Settings.ACTION_LOCATION_SOURCE_SETTINGS);
				startActivity(intent);
			}
		});

		/* button handler - set point 1 */
		ImageButton buttonPoint1 = (ImageButton) findViewById(R.id.buttonPoint1);
		buttonPoint1.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				if (mGPSPoint == null) {
					Toast.makeText(getApplicationContext(),
							"GPS Location Not Available", Toast.LENGTH_SHORT)
							.show();
				} else {
					mPoint1 = new Point(mGPSPoint);
					mEditPoint1.setText(formatPoint(mPoint1));
				}
			}
		});

		/* button handler - set point 2 */
		ImageButton buttonPoint2 = (ImageButton) findViewById(R.id.buttonPoint2);
		buttonPoint2.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				if (mGPSPoint == null) {
					Toast.makeText(getApplicationContext(),
							"GPS Location Not Available", Toast.LENGTH_SHORT)
							.show();
				} else {
					mPoint2 = new Point(mGPSPoint);
					mEditPoint2.setText(formatPoint(mPoint2));
				}
			}
		});

		/* button handler - get distance */
		ImageButton buttonDistance = (ImageButton) findViewById(R.id.buttonDistance);
		buttonDistance.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				if (mPoint1 == null) {
					Toast.makeText(getApplicationContext(),
							"Start Location Not Set", Toast.LENGTH_SHORT)
							.show();
				} else if (mPoint2 == null) {
					Toast.makeText(getApplicationContext(),
							"End Location Not Set", Toast.LENGTH_SHORT).show();
				} else {

					if (mPoint1.x == mPoint2.x && mPoint1.y == mPoint2.y) {

						/*
						 * if the two points are the same, don't bother
						 * requesting the distance from the service
						 */
						distanceComputed(0.0);

					} else {

						/* cancel any running task */
						if (mTask != null)
							mTask.cancel(true);

						/* start async task to obtain distance from server */
						mTask = new DistanceTask(mPoint1, mPoint2);
						mTask.execute();
					}
				}
			}
		});
	}

	/**
	 * Perform any final cleanup before an activity is destroyed. (Activity
	 * override)
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();

		/* save the user established points, to be restored in onCreate */
		try {
			savePointState(mPoint1, STATE_POINT1);
			savePointState(mPoint2, STATE_POINT2);
		} catch (IOException e) {
			e.printStackTrace();
			/* showing an alert won't work here, the activity is closing
			 * TODO some other way to inform the user of problems
			 * saving point state
			 */
		}

		/* cancel any running task, otherwise the wait dialog becomes orphaned */
		if (mTask != null)
			mTask.cancel(true);
	}

	/**
	 * Called after onRestoreInstanceState(Bundle), onRestart(), or onPause(),
	 * for your activity to start interacting with the user. (Activity override)
	 */
	@Override
	protected void onResume() {

		if (mLocationManager != null) {
			mLocationManager.requestLocationUpdates(
					LocationManager.GPS_PROVIDER, 1000, 10f, this);

			if (mLocationManager
					.isProviderEnabled(LocationManager.GPS_PROVIDER))
				onProviderEnabled(LocationManager.GPS_PROVIDER);
			else
				onProviderDisabled(LocationManager.GPS_PROVIDER);
		}
		super.onResume();
	}

	/**
	 * Called as part of the activity lifecycle when an activity is going into
	 * the background, but has not (yet) been killed. (Activity override)
	 */
	@Override
	protected void onPause() {

		/* GPS, as it turns out, consumes battery like crazy */
		if (mLocationManager != null)
			mLocationManager.removeUpdates(this);

		super.onPause();
	}

	/**
	 * Called when the location has changed. (LocationListener implementation)
	 */
	public void onLocationChanged(Location location) {

		mEditStatus.setText("Location Available");
		mGPSPoint = new Point(location.getLongitude(), location.getLatitude());
	}

	/**
	 * Called when the provider is disabled by the user. (LocationListener
	 * implementation)
	 */
	public void onProviderDisabled(String provider) {

		mEditStatus.setText("Disabled");
		mGPSPoint = null;
	}

	/**
	 * Called when the provider is enabled by the user (LocationListener
	 * implementation)
	 */
	public void onProviderEnabled(String provider) {

		mEditStatus.setText("Enabled");
		mGPSPoint = null;
	}

	/**
	 * Called when the provider status changes. (LocationListener
	 * implementation)
	 */
	public void onStatusChanged(String provider, int status, Bundle extras) {

		mGPSPoint = null;

		switch (status) {

		case LocationProvider.OUT_OF_SERVICE:
			mEditStatus.setText("Out Of Service");
			break;

		case LocationProvider.TEMPORARILY_UNAVAILABLE:
			mEditStatus.setText("Temporarily Unavailable");
			break;

		case LocationProvider.AVAILABLE:
			mEditStatus.setText("Available");
			break;
		}
	}

	/**
	 * Called by the compute-distance-task when the distance has been
	 * successfully computed
	 */
	private void distanceComputed(double distance) {

		if (distance == 0.0) {

			mEditDistance.setText("0 miles");

		} else if (distance < 1.0) {

			DecimalFormat formater = new DecimalFormat("#.##");
			distance = distance * 5280.0;
			StringBuilder sb = new StringBuilder(32);
			sb.append(formater.format(distance));
			sb.append(" feet");
			mEditDistance.setText(sb.toString());

		} else {

			DecimalFormat formater = new DecimalFormat("#.##");
			StringBuilder sb = new StringBuilder(32);
			sb.append(formater.format(distance));
			sb.append(" miles");
			mEditDistance.setText(sb.toString());
		}
	}

	/**
	 * Called by the compute-distance-task when the distance fails to compute,
	 * either due to an error, user-cancel, or programmatic cancel (orientation
	 * changes)
	 * @param errorMessage 
	 */
	private void distanceFailedToCompute(String errorMessage) {
		
		if (errorMessage != null) {
			mEditDistance.setText(errorMessage);
		} else {
			mEditDistance.setText("");
		}
	}

	/**
	 * Format the geographic point for output
	 * 
	 * @param point the point to format
	 * @return a string with degrees-minutes-seconds
	 */
	private String formatPoint(Point point) {

		StringBuilder sb = new StringBuilder(32);

		if (point.y >= 0.0) {
			sb.append("N ");
		} else {
			sb.append("S ");
		}
		
		double remainder = Math.abs(point.y);
		int degrees = (int) Math.abs(point.y);
		remainder = (remainder - degrees) * 60.0;
		int minutes = (int) remainder;
		remainder = (remainder - minutes) * 60.0;
		int seconds = (int) remainder;

		sb.append(Integer.toString(degrees));
		sb.append("°");
		sb.append(Integer.toString(minutes));
		sb.append("'");
		sb.append(Integer.toString(seconds));
		sb.append("\", ");

		if (point.x >= 0.0) {
			sb.append("E ");
		} else {
			sb.append("W ");
		}
		
		remainder = Math.abs(point.x);
		degrees = (int) remainder;
		remainder = (remainder - degrees) * 60.0;
		minutes = (int) remainder;
		remainder = (remainder - minutes) * 60.0;
		seconds = (int) remainder;

		sb.append(Integer.toString(degrees));
		sb.append("°");
		sb.append(Integer.toString(minutes));
		sb.append("'");
		sb.append(Integer.toString(seconds));
		sb.append("\"");

		return sb.toString();
	}

	/**
	 * Saves the point data to restore between Activity lifecycles
	 * 
	 * @param point the data to save
	 * @param name  identifier for persistence data
	 * @throws IOException 
	 */
	private void savePointState(Point point, String name) throws IOException {

		/* remove any existing saved state */
		deleteFile(name);

		if (point != null) {

			try {

				/* save point to a file */
				FileOutputStream fos;
				fos = openFileOutput(name, MODE_PRIVATE);
				DataOutputStream dos = new DataOutputStream(fos);
				dos.writeDouble(point.x);
				dos.writeDouble(point.y);

			} catch (IOException e) {
				/* problem unknown */
				e.printStackTrace();
				throw e;
			}
		}
	}

	/**
	 * Loads and returns the saved point data
	 * 
	 * @param name identifier for persistence data
	 * @return the saved point data
	 * @throws IOException
	 */
	private Point restorePointState(String name) throws IOException {

		try {

			/* read point from a file */
			FileInputStream fis;
			fis = openFileInput(name);
			DataInputStream dis = new DataInputStream(fis);

			double x = dis.readDouble();
			double y = dis.readDouble();

			return new Point(x, y);

		} catch (FileNotFoundException e) {
			/* most likely the point state has not yet been saved, swallow this exception */
			e.printStackTrace();
			return null;

		} catch (IOException e) {
			/* unexpected error, the persisted point data could be corrupted */
			e.printStackTrace();
			throw e;
		}
	}

	private void showMessage(String message, String title) {

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(title).setMessage(message).setCancelable(true)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * Computes the distance between two points in a background thread.
	 * 
	 * @author Tom
	 * 
	 */
	private class DistanceTask extends AsyncTask<Void, Void, Double> {

		/** the wait dialog displayed while the distance service is accessed */
		private ProgressDialog waitDialog;
		/** the service used to obtain the distance */
		private Service mService;
		/** the start location */
		private Point mPoint1;
		/** the end location */
		private Point mPoint2;
		/** error message from service */
		private String mErrorMessage;

		/**
		 * constructor
		 * 
		 * @param point1
		 * @param point2
		 */
		public DistanceTask(Point point1, Point point2) {

			mPoint1 = point1;
			mPoint2 = point2;
			mService = new Service();
		}

		/**
		 * This is executed in the background thread
		 */
		@Override
		protected Double doInBackground(Void... param) {

			try {

				double distance = mService.GetDistance(mPoint1, mPoint2);
				return Double.valueOf(distance);

			} catch (ServiceException e) {

				/* return used for success/fail and value of success, 
				 * any error captured in string
				 */
	            Throwable cause = e.getCause();
	            if (cause != null)
	            	mErrorMessage = cause.getMessage();
	            else
	            	mErrorMessage = e.getMessage();
	            
				return null;
			}
		}

		/**
		 * Displays the modal wait dialog for the entire duration of the task.
		 * Runs on the UI thread before doInBackground.
		 */
		@Override
		protected void onPreExecute() {

			/* show the please-wait dialog (modal) */
			waitDialog = ProgressDialog.show(DistanceActivity.this,
					"Please wait...", "Computing Distance...", true, true);

			/* Set a listener to be invoked when the dialog is dismissed */
			waitDialog.setOnCancelListener(new OnCancelListener() {
				/**
				 * This method is invoked when the dialog is canceled.
				 */
				public void onCancel(DialogInterface dialog) {

					DistanceTask.this.cancel(true);
				}
			});
		}

		/**
		 * Runs on the UI thread after doInBackground
		 */
		@Override
		protected void onPostExecute(Double result) {

			/* close the wait dialog */
			runOnUiThread(new Runnable() {
				// @Override
				public void run() {
					if (waitDialog != null) {
						waitDialog.dismiss();
						waitDialog = null;
					}
				}
			});

			/* send result to activity/UI */
			if (result == null) {
				distanceFailedToCompute(mErrorMessage);
			} else {
				distanceComputed(result);
			}
		}

		/**
		 * Runs on the UI thread after cancel(boolean) is invoked and
		 * doInBackground(Object[]) has finished.
		 */
		@Override
		protected void onCancelled() {

			distanceFailedToCompute(null);
		}
	}
}

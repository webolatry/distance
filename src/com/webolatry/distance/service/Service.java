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

package com.webolatry.distance.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

/* http://code.google.com/p/google-gson, version 2.2.2 used */
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import android.net.Uri;

public class Service {

	/* json parser/formatter */
	private Gson mGson = new Gson();

	/**
	 * Query the ArcGIS Online service for the distance between two points
	 * @param point1 the start point (lat/lon, WGS84)
	 * @param point2 the end point (lat/lon, WGS84)
	 * @return the distance between the input points, in miles
	 * @throws ServiceException
	 */
	public double GetDistance(Point point1, Point point2) throws ServiceException {

		try {

			/* prepare point 1 */
			Geometry geometry1 = new Geometry(point1);

			/* prepare point 2 */
			Geometry geometry2 = new Geometry(point2);

			/* convert point 1 data to a json string */
			String geometry1Json = mGson.toJson(geometry1);

			/* convert point 2 data to a json string */
			String geometry2Json = mGson.toJson(geometry2);

			/* build the get request */
			Uri uri = new Uri.Builder()
					.scheme("http")
					// ESRI test server
					.authority("sampleserver3.arcgisonline.com")
					// Distance api
					.path("ArcGIS/rest/services/Geometry/GeometryServer/distance")
					// request json response
					.appendQueryParameter("f", "json")
					// points are GCS_WGS_1984
					.appendQueryParameter("sr", "4326")
					// request geodesic distance
					.appendQueryParameter("geodesic", "true")
					// request return in esriSRUnit_SurveyMile
					.appendQueryParameter("distanceUnit", "9035")
					// from here
					.appendQueryParameter("geometry1", geometry1Json)
					// to here
					.appendQueryParameter("geometry2", geometry2Json).build();

			/* send request to server, get response */
			InputStream source = retrieveStream(uri.toString());
			Reader reader = new InputStreamReader(source);

			/* parse the response */
			Response response = mGson.fromJson(reader, Response.class);

			/* return the distance */
			return response.distance;

		} catch (IllegalArgumentException e) {

			throw new ServiceException(e);

		} catch (IOException e) {

			throw new ServiceException(e);

		} catch (JsonSyntaxException e) {

			throw new ServiceException(e);

		} catch (JsonIOException e) {

			throw new ServiceException(e);

		} catch (Exception e) {

			throw new ServiceException(e);
		}
	}

	/**
	 * Http get with specified uri
	 * @param uri the url and encoded input parameters
	 * @return the response stream from the server
	 */
	private InputStream retrieveStream(String uri) throws IllegalArgumentException, IOException, ServiceException {

		HttpGet getRequest = new HttpGet(uri);

		try {

			DefaultHttpClient client = new DefaultHttpClient();
			HttpResponse getResponse = client.execute(getRequest);

			int status = getResponse.getStatusLine().getStatusCode();
						
			if (status != HttpStatus.SC_OK) {
				throw new ServiceException(getResponse.getStatusLine().getReasonPhrase());
			}

			HttpEntity getResponseEntity = getResponse.getEntity();
			if (getResponseEntity == null) {
				throw new ServiceException("The server did not respond");
			}

			return getResponseEntity.getContent();

		} catch (IOException e) {

			getRequest.abort();
			throw e;
		}
	}
}

/*
 * Copyright (C) 2010 Tom Bruns
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

package com.webolatry.distance.service;

import com.google.gson.annotations.SerializedName;
import com.webolatry.distance.service.Point;

/**
 * one point of a distance query (annotated for json)
 * 
 * @author Tom
 * @see http://sampleserver3.arcgisonline.com/ArcGIS/SDK/REST/index.html?distance.html
 */
public class Geometry {
	
	public Geometry(Point point) {
		geometry = point;
		geometryType = new String("esriGeometryPoint");
	}

	@SerializedName("geometryType")
	public String geometryType;
	@SerializedName("geometry")
	public Point geometry;
}

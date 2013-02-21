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

package com.webolatry.distance.service;

/**
 * Exception thrown by service requests
 * @author Tom
 *
 */
public class ServiceException extends Exception {

	private static final long serialVersionUID = -6627993036223674911L;

	/**
	 * constructor for scenarios that aren't the result of catching another exception type
	 * @param message
	 */
	public ServiceException(String message) {
		super(message);
	}

	/**
	 * constructor for scenarios that are the result of catching another exception type
	 * @param message
	 */
	public ServiceException(Throwable cause) {
		super(cause);
	}
}

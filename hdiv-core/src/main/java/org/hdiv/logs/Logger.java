/**
 * Copyright 2005-2010 hdiv.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hdiv.logs;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hdiv.util.HDIVUtil;

/**
 * Log that shows the attacks detected by HDIV. It includes type of attack and the
 * identity (application user) of the user. Since the way to obtain this user
 * may vary from application to application, an standard interface has been defined
 * to be implemented by each application.
 * <p>
 * Log format = type;target;parameter;value;userLocalIP;ip;userId
 * </p>
 * 
 * @author Roberto Velasco
 * @see org.hdiv.logs.IUserData
 */
public class Logger {

	/**
	 * Commons Logging instance.
	 */
	private static Log log = LogFactory.getLog(Logger.class);

	/**
	 * Obtains user data from the request
	 */
	private IUserData userData;

	/**
	 * Logger is initialized.
	 * 
	 */
	public void init() {
	}

	/**
	 * Prints formatted attack produced by the user if the logging level defined in the Web application rate should be at least INFO.
	 * 
	 * @param type Error type
	 * @param target target name
	 * @param parameter parameter name
	 * @param value parameter value
	 */
	public void log(String type, String target, String parameter, String value) {
		
		String formatedData = this.format(type, target, parameter, value);		
		log.info(formatedData);
	}

	/**
	 * <p>
	 * Formatted text with information from the attack produced by the user. The log format is as follows:
	 * </p>
	 * <p>
	 * <code>[error type];[target];[parameter];[value];[user local IP adress];[IP adress of the client or the last proxy that sent the request];[userId]</code>
	 * </p>
	 * 
	 * @param type Error type
	 * @param target target name
	 * @param parameter parameter name
	 * @param value parameter value
	 * @return String Formatted text with the attach.
	 */
	public String format(String type, String target, String parameter, String value) {

		HttpServletRequest request = this.getHttpServletRequest();
		
		StringBuffer buffer = new StringBuffer();	
		buffer.append(type);
		buffer.append(";");
		buffer.append(target);
		buffer.append(";");
		buffer.append(parameter);
		buffer.append(";");
		buffer.append(value);
		buffer.append(";");
		buffer.append(this.getUserLocalIP(request));
		buffer.append(";");
		buffer.append(request.getRemoteAddr());
		buffer.append(";");
		buffer.append(this.userData.getUsername(request));
		
		return buffer.toString();
	}
	
	/**
	 * @param request the HttpServletRequest of the request
	 * @return Returns the remote user IP address if behind the proxy.
	 */
	private String getUserLocalIP(HttpServletRequest request) {

		String ipAddress = null;

		if (request.getHeader("X-Forwarded-For") == null) {
			ipAddress = request.getRemoteAddr();
		} else {
			ipAddress = request.getHeader("X-Forwarded-For");
		}
		return ipAddress;
	}

	/**
	 * Obtains the request instance
	 * @return request
	 */
	protected HttpServletRequest getHttpServletRequest(){
		
		return HDIVUtil.getHttpServletRequest();
		
	}
	
	public IUserData getUserData() {
		return userData;
	}

	public void setUserData(IUserData userData) {
		this.userData = userData;
	}
}
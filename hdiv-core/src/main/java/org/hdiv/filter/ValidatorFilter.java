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

package org.hdiv.filter;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hdiv.application.IApplication;
import org.hdiv.config.HDIVConfig;
import org.hdiv.config.multipart.IMultipartConfig;
import org.hdiv.session.ISession;
import org.hdiv.util.HDIVUtil;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * An unique filter exists within HDIV. This filter has two responsabilities:
 * initialize and validate. In fact, the actual validation is not implemented in
 * this class, it is delegated to ValidatorHelper.
 * 
 * @author Roberto Velasco
 * @author Gorka Vicente
 * @see org.hdiv.filter.ValidatorHelperThreadLocal
 */
public class ValidatorFilter extends OncePerRequestFilter {

	/**
	 * Commons Logging instance.
	 */
	private static Log log = LogFactory.getLog(ValidatorFilter.class);

	/**
	 * HDIV configuration object
	 */
	private HDIVConfig hdivConfig;
	
	/**
	 * IValidationHelper object
	 */
	private IValidationHelper validationHelper;
	
	/**
	 * The multipart config
	 */
	private IMultipartConfig multipartConfig;
	
	/**
	 * Creates a new ValidatorFilter object.
	 */
	public ValidatorFilter() {

	}

	/**
	 * Called by the web container to indicate to a filter that it is being
	 * placed into service.
	 * 
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	protected void initFilterBean() throws ServletException {
		
		ServletContext servletContext = getServletContext();
		WebApplicationContext context = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);
		
		this.hdivConfig = (HDIVConfig) context.getBean("config");
		this.validationHelper = (IValidationHelper) context.getBean("validatorHelper");
		if(context.containsBean("multipartConfig")){
			//For applications without Multipart requests
			this.multipartConfig = (IMultipartConfig) context.getBean("multipartConfig");
		}
		IApplication application = (IApplication) context.getBean("application");
		ISession session = (ISession) context.getBean("sessionHDIV");
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBeanClassLoader(context.getClassLoader());
		String messageSourcePath = (String)context.getBean("messageSourcePath");
		messageSource.setBasename(messageSourcePath);
		
		HDIVUtil.setApplication(application, servletContext);
		HDIVUtil.setMessageSource(messageSource, servletContext);
		HDIVUtil.setISession(session, servletContext);
		HDIVUtil.setHDIVConfig(this.hdivConfig, servletContext);
		
	}

	/**
	 * Called by the container each time a request/response pair is passed
	 * through the chain due to a client request for a resource at the end of
	 * the chain.
	 * 
	 * @param servletRequest request
	 * @param servletResponse response
	 * @param filterChain filter chain
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
	 *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
				
		this.initHDIV(request);		
		ResponseWrapper responseWrapper = new ResponseWrapper(response);
		RequestWrapper requestWrapper = getRequestWrapper(request);
		
		try {			

			boolean legal = false;
			boolean isSizeLimitExceeded = false;

			if (this.isMultipartContent(request.getContentType())) {

				requestWrapper.setMultipart(true);
				
				try {
					
					if(this.multipartConfig == null){
						throw new RuntimeException("No 'multipartConfig' configured. It is required to multipart requests.");
					}
					
					this.multipartConfig.handleMultipartRequest(requestWrapper, super.getServletContext());

				} catch (DiskFileUpload.SizeLimitExceededException e) {
					request.setAttribute(IMultipartConfig.FILEUPLOAD_EXCEPTION, e);
					isSizeLimitExceeded = true;
					legal = true;
				} catch (MaxUploadSizeExceededException e) {
					isSizeLimitExceeded = true; 
					legal = true;
					request.setAttribute(IMultipartConfig.FILEUPLOAD_EXCEPTION, e);
				} catch (FileUploadException e) {
					isSizeLimitExceeded = true;
					legal = true;
					if (e.getCause() == null)
						e = new FileUploadException(e.getMessage());
					request.setAttribute(IMultipartConfig.FILEUPLOAD_EXCEPTION, e);
				}
			}

			if (!isSizeLimitExceeded) {
				legal = this.validationHelper.validate(requestWrapper);
			}

			if (legal) {
				processRequest(requestWrapper, responseWrapper, filterChain);
			} else {
				response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + hdivConfig.getErrorPage()));
			}

		} catch (IOException e){
			//Internal framework exception, rethrow exception
			throw e;
		} catch (ServletException e){
			//Internal framework exception, rethrow exception
			throw e;
		} catch (Exception e) {
			if(log.isErrorEnabled()){
				log.error("Exception in request validation:");
				log.error("Message: "+e.getMessage());
				
				StringBuffer buffer = new StringBuffer();
	            StackTraceElement[] trace = e.getStackTrace();
	            for (int i=0; i < trace.length; i++){
	            	buffer.append("\tat " + trace[i] + System.getProperty("line.separator"));
	            }
				log.error("StackTrace: "+buffer.toString());
				log.error("Cause: "+e.getCause());
				log.error("Exception: "+e.toString());
			}
			response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + hdivConfig.getErrorPage()));
		} finally {
			HDIVUtil.resetLocalData();
		}
	}
	
	/**
	 * Initialize HDIV HTTP session
	 * 
	 * @param request HTTP request
	 */
	public void initHDIV(HttpServletRequest request) {		
		HDIVUtil.setHttpServletRequest(request);
	}

	/**
	 * Utility method that determines whether the request contains multipart
	 * content.
	 * 
	 * @param contentType content type
	 * @return <code>true</code> if the request is multipart.
	 *         <code>false</code> otherwise.
	 */
	public boolean isMultipartContent(String contentType) {
		return ((contentType != null) && (contentType.indexOf("multipart/form-data") != -1));
	}

	/**
	 * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
	 * methods.
	 * 
	 * @param requestWrapper request wrapper
	 * @param responseWrapper response wrapper
	 * @param filterChain filter chain
	 * @throws Exception if there is an error in request process.
	 */
	protected void processRequest(RequestWrapper requestWrapper, ResponseWrapper responseWrapper,
			FilterChain filterChain) throws IOException, ServletException {

		this.validationHelper.startPage(requestWrapper);
		filterChain.doFilter(requestWrapper, responseWrapper);		
		this.validationHelper.endPage(requestWrapper);
	}

	/**
	 * Crea el wrapper del request
	 * 
	 * @param request HTTP request
	 * @return the request wrapper
	 */
	protected RequestWrapper getRequestWrapper(HttpServletRequest request){

		RequestWrapper requestWrapper = new RequestWrapper(request);
		requestWrapper.setConfidentiality(this.hdivConfig.getConfidentiality());
		requestWrapper.setCookiesConfidentiality(this.hdivConfig.isCookiesConfidentialityActivated());
	
		return requestWrapper;
	}
	
}

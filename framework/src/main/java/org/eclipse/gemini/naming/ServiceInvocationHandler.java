/*******************************************************************************
 * Copyright (c) 2010, 2015 Oracle.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution. 
 * The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 * and the Apache License v2.0 is available at 
 *     http://www.opensource.org/licenses/apache2.0.php.
 * You may elect to redistribute this code under either of these licenses.
 *
 * Contributors:
 *     Bob Nettleton (Oracle) - Initial Reference Implementation
 ******************************************************************************/  

package org.eclipse.gemini.naming;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

class ServiceInvocationHandler implements InvocationHandler {
	
	private static final Logger logger = Logger.getLogger(ServiceInvocationHandler.class.getName());
	
	private final BundleContext m_callerBundleContext;
	
	/* backing OSGi service */
	private Object m_osgiService;

	/* service tracker for the backing service */
	protected ServiceTracker m_serviceTracker;
	
	/* ServiceReference for the backing service */
	private ServiceReference m_serviceReference;

	/* the URL information used to rebind the backing service if necessary */
	private final OSGiURLParser m_urlParser;
	
	
	ServiceInvocationHandler(BundleContext callerBundleContext, ServiceReference serviceReference, OSGiURLParser urlParser, Object osgiService) {
		m_callerBundleContext = callerBundleContext;
		// initialize backing service 
		m_osgiService = osgiService;
		m_serviceReference = serviceReference;
		m_urlParser = urlParser;
		
		// open a tracker for just this service
		m_serviceTracker = 
			new ServiceTracker(m_callerBundleContext, m_serviceReference, null);
		m_serviceTracker.open();
	}
	
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return SecurityUtils.invokePrivilegedAction(new ServiceInvokeAction(method, args));
	}


	private Object handleMethodInvocation(Method method, Object[] args) throws Throwable {
		if (isServiceAvailable()) {
			return invokeMethodOnService(method, args);
		} else {
			// attempt to obtain another service reference to match this interface
			if(obtainService()) {
				return invokeMethodOnService(method, args);
			}
		}
		
		throw new ServiceException("Backing service is not available for invocation", 
				                    ServiceException.UNREGISTERED);
	}


	private Object invokeMethodOnService(Method method, Object[] args) throws Throwable {
		try {
			return ReflectionUtils.invokeMethodOnObject(method, m_osgiService, args);
		}
		catch (IllegalAccessException illegalAccessException) {
			throw new ServiceException("An error occurred while trying to invoke on this service, please verify that this service's interface is public", illegalAccessException);
		}
	}
	
	protected void close() {
		try {
			m_callerBundleContext.ungetService(m_serviceReference);
		}
		catch (Throwable throwable) {
			logger.log(Level.FINER, 
					   "An Exception occurred while trying to unget the backing OSGi service",
					   throwable);
		}
		
		m_serviceTracker.close();
	}
	
	
	
	@Override
	protected void finalize() throws Throwable {
		close();
	}


	private boolean isServiceAvailable() {
		return m_serviceTracker.size() == 1;
	}
	
	protected boolean obtainService() {
		m_serviceTracker.close();
		try {
			ServiceReference[] serviceReferences = 
				m_callerBundleContext.getServiceReferences(m_urlParser.getServiceInterface(),
							                               m_urlParser.getFilter());
			if (serviceReferences != null) {
				final ServiceReference[] sortedServiceReferences = 
					ServiceUtils.sortServiceReferences(serviceReferences);
				
				// reset the tracker
				return resetBackingService(sortedServiceReferences[0]);
			 } else {
				 // attempt to locate service using service name property
				 ServiceReference[] serviceReferencesByName = 
					ServiceUtils.getServiceReferencesByServiceName(m_callerBundleContext, m_urlParser);
				if (serviceReferencesByName != null) {
					ServiceReference[] sortedServiceReferences = 
						ServiceUtils.sortServiceReferences(serviceReferencesByName);
					// reset the tracker
					return resetBackingService(sortedServiceReferences[0]);
				}
				
			 }
		}
		catch (InvalidSyntaxException invalidSyntaxException) {
			logger.log(Level.SEVERE, 
					   "An error in the filter syntax for this OSGi lookup has occurred.",
					   invalidSyntaxException);
		}
		
		return false;
   }


	private boolean resetBackingService(ServiceReference serviceReference) {
		m_serviceTracker = 
			new ServiceTracker(m_callerBundleContext, serviceReference, null);
		m_serviceTracker.open();
		
		// reset the service
		m_osgiService = m_serviceTracker.getService();
		if(m_osgiService!= null) {
			return true;
		}
		
		return false;
	}
	

	private class ServiceInvokeAction extends ReflectiveInvokeAction {

		ServiceInvokeAction(Method method, Object[] args) {
			super(method, args);
		}

		@Override
		public Object invokeMethod(Method method, Object[] args) throws Throwable {
			return handleMethodInvocation(method, args);
		}
	}

}
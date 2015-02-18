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

import java.security.AccessControlException;
import java.security.AccessController;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.spi.ObjectFactory;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * A URL context factory that supports lookups of OSGi services.
 * 
 * 
 */
class OSGiURLContextFactory implements ObjectFactory {

	private static final String OSGI_BUNDLE_CONTEXT_LOOKUP = "osgi:framework/bundleContext";

	private static final Logger logger = Logger.getLogger(OSGiURLContextFactory.class.getName());
	
	private final BundleContext	m_bundleContext;

	public OSGiURLContextFactory(BundleContext bundleContext) {
		m_bundleContext = bundleContext;
	}

	@Override
	public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable environment) throws Exception {
		return new OSGiURLContext(m_bundleContext);
	}

	/**
	 * The OSGi URL Context that is handed back to the Gemini Naming Implementation.
	 * <p/>
	 * This URL Context only supports the Context.lookup(String) method.
	 * All other method invocations on this context will result in an
	 * OperationNotSupportedException being thrown.
	 */
	private static class OSGiURLContext extends NotSupportedContext {

		private final BundleContext	m_bundleContext;

		public OSGiURLContext(BundleContext bundleContext) {
			super("This operation is not supported by the OSGi URL Context");
			m_bundleContext = bundleContext;
		}
		

		@Override
		public Object lookup(String name) throws NamingException {
			String osgiURL = name;
			try {
				if(osgiURL.equals(OSGI_BUNDLE_CONTEXT_LOOKUP)) {
					// return the caller's BundleContext
					AdminPermission adminPermission = 
						new AdminPermission(m_bundleContext.getBundle(), AdminPermission.CONTEXT);
					
					try {
						AccessController.checkPermission(adminPermission);
						return m_bundleContext;
					} catch (AccessControlException accessControlException) {
						NamingException namingException = new NameNotFoundException("BundleContext not available, caller does not have the correct permission.");
						namingException.setRootCause(accessControlException);
						throw namingException;
					}
					
				}
				
				Object requestedService = obtainService(osgiURL);
				if (requestedService != null) {
					return requestedService;
				}
			}
			catch (InvalidSyntaxException e) {
				NamingException namingException = new NamingException(
						"Error occurred while parsing the OSGi URL");
				namingException.initCause(e);
				throw namingException;
			}

			throw new NameNotFoundException(
					"The OSGi service referred to by the URL = "
							+ osgiURL
							+ " could not be located in the OSGi Service Registry");
		}
		

		/**
		 * Obtain the service requested in the "osgi" URL. Currently, this
		 * method uses the Factory Manager's bundle context.
		 * 
		 * @param osgiURL the URL for the OSGi service requested
		 * @return the OSGi Service requested, or null if the service cannot be
		 *         found
		 * @throws InvalidSyntaxException if an error occurs while parsing the
		 *         OSGi filer (if specified)
		 */
		private Object obtainService(String osgiURL)
				throws InvalidSyntaxException {
			OSGiURLParser urlParser = new OSGiURLParser(osgiURL);
			try {
				urlParser.parse();
			}
			catch (IllegalStateException stateException) {
				logger.log(Level.SEVERE, "An exception occurred while trying to parse this osgi URL", stateException);
				return null;
			}
			if (urlParser.getServiceInterface() == null) {
				return null;
			}

			return getService(m_bundleContext, urlParser);
		}

		private static Object getService(BundleContext bundleContext, OSGiURLParser urlParser) throws InvalidSyntaxException {
			ServiceReference[] serviceReferences = 
				bundleContext.getServiceReferences(urlParser.getServiceInterface(), 
						                           urlParser.getFilter());
			if (serviceReferences != null) {
				final ServiceReference[] sortedServiceReferences = 
					ServiceUtils.sortServiceReferences(serviceReferences);
				if (urlParser.isServiceListURL()) {
					// return a Context that can handle service.id lookups
					return new OSGiServiceListContext(bundleContext, sortedServiceReferences, urlParser);
				}
				else {
					ServiceProxyInfo proxyInfo = ReflectionUtils.getProxyForSingleService(bundleContext, urlParser, sortedServiceReferences[0]);
					return proxyInfo.getService();
				}
			}
			else {
				// service interface name may not map to a published name
				// check the registry for a service that supports the
				// osgi.jndi.serviceName property
				ServiceReference[] serviceReferencesByName = 
					ServiceUtils.getServiceReferencesByServiceName(bundleContext, urlParser);
				if (serviceReferencesByName != null) {
					final ServiceReference[] sortedServiceReferences = 
						ServiceUtils.sortServiceReferences(serviceReferencesByName);
					ServiceProxyInfo proxyInfo = 
						ReflectionUtils.getProxyForSingleService(bundleContext, urlParser, sortedServiceReferences[0]);
					return proxyInfo.getService();
				}
			}

			return null;
		}
	
	}
	
}

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

import java.util.Arrays;
import java.util.Comparator;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.jndi.JNDIConstants;
import org.osgi.util.tracker.ServiceTracker;

/**
 * This class holds utility methods for handling OSGi services/service references
 *
 * 
 * 
 * @version $Revision$
 */
class ServiceUtils {
	/* private constructor, static utility class */
	private ServiceUtils() {}

	/**
	 * Utility method to sort an array of ServiceReferences using the service
	 * ranking (if specified).
	 * 
	 * This utility should follow any service ranking rules already defined in
	 * the OSGi specification.
	 * 
	 * @param serviceTracker tracker to use to provide the initial array to sort
	 * @return sorted array of ServiceReferences, or a zero-length array if no
	 *         matching services were found
	 */
	static ServiceReference[] sortServiceReferences(ServiceTracker serviceTracker) {
		final ServiceReference[] serviceReferences = serviceTracker
				.getServiceReferences();
		if (serviceReferences == null) {
			return new ServiceReference[0];
		}
	
		return sortServiceReferences(serviceReferences);
	}

	
	/**
	 * Utility method to sort an array of ServiceReferences using the OSGi
	 * service ranking.  
	 * 
	 * This utility should follow any service ranking rules already defined in
	 * the OSGi specification.
	 * 
	 * 
	 * @param serviceReferences an array of ServiceReferences to sort
	 * @return the array of ServiceReferences passed into this method, but sorted 
	 *         according to OSGi service ranking.  
	 */
	static ServiceReference[] sortServiceReferences(
			final ServiceReference[] serviceReferences) {
		Arrays.sort(serviceReferences, new Comparator() {
			@Override
			public int compare(Object objectOne, Object objectTwo) {
				ServiceReference serviceReferenceOne = (ServiceReference) objectOne;
				ServiceReference serviceReferenceTwo = (ServiceReference) objectTwo;
				return serviceReferenceTwo.compareTo(serviceReferenceOne);
			}
		});

		return serviceReferences;
	}

	
	/**
	 * Utility method to obtain the list of ServiceReferences that match 
	 * a query using the JNDI "service name" service property.  
	 * 
	 * @param bundleContext the BundleContext to use to obtain services
	 * @param urlParser the parser associated with this request
	 * @return an array of ServiceReferences that match the given request
	 * @throws InvalidSyntaxException on filter parsing error
	 */
	static ServiceReference[] getServiceReferencesByServiceName(BundleContext bundleContext, OSGiURLParser urlParser)
			throws InvalidSyntaxException {
		final String serviceNameFilter = "("
				+ JNDIConstants.JNDI_SERVICENAME + "="
				+ urlParser.getServiceInterface() + ")";
		ServiceReference[] serviceReferencesByName = 
			bundleContext.getServiceReferences(null, serviceNameFilter);
		return serviceReferencesByName;
	}
	
}

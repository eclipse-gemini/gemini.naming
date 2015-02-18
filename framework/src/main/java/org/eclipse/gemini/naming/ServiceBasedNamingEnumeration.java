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

import java.util.NoSuchElementException;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Abstract NamingEnumeration implementation that contains the 
 * basic logic for an enumeration over a set of NameClassPair objects.  
 * 
 * 
 *
 * 
 * @version $Revision$
 */
abstract class ServiceBasedNamingEnumeration implements NamingEnumeration {

	protected boolean				m_isOpen	= false;
	protected int					m_index	= -1;
	protected final BundleContext	m_bundleContext;
	protected final String			m_interfaceName;
	protected final ServiceReference[]	m_serviceReferences;
	protected NameClassPair[]		m_nameClassPairs;

	public ServiceBasedNamingEnumeration(BundleContext bundleContext, ServiceReference[] serviceReferences, String interfaceName) {
		m_bundleContext = bundleContext;
		if(interfaceName == null) {
			m_interfaceName = "";
		} else {
			m_interfaceName = interfaceName;
		}
		
		m_serviceReferences = serviceReferences;
		if(m_serviceReferences.length > 0) {
			m_isOpen = true;
			m_index = 0;
		}
	}

	@Override
	public void close() throws NamingException {
		m_isOpen = false;
	}

	@Override
	public boolean hasMore() throws NamingException {
		checkIsOpen();
		return (isIndexValid());
	}

	@Override
	public Object next() throws NamingException {
		checkIsOpen();
		return internalNextElement();
	}

	@Override
	public boolean hasMoreElements() {
		if(!m_isOpen) {
			return false;
		} else {
			return (isIndexValid());
		}
		
	}

	@Override
	public Object nextElement() {
		return internalNextElement();
	}

	private void checkIsOpen() throws NamingException {
		if (!m_isOpen) {
			throw new NamingException("Operation cannot complete, since this NamingEnumeration has been closed");
		}
	}

	private boolean isIndexValid() {
		return m_index < m_nameClassPairs.length;
	}

	private Object internalNextElement() {
		if(isIndexValid()) {
			return internalNextClassPair();
		} else {
			throw new NoSuchElementException("No additional elements exist in this NamingEnumeration");
		}
	}

	private NameClassPair internalNextClassPair() {
		return m_nameClassPairs[m_index++];
	}

}
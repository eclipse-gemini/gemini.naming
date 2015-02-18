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

import java.util.Hashtable;
import java.util.Map;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.spi.DirObjectFactory;
import javax.naming.spi.ObjectFactory;

import org.osgi.framework.BundleContext;

class ProviderAdminImpl implements CloseableProviderAdmin {

	private final OSGiInitialContextFactoryBuilder	m_objectFactoryBuilder;

	ProviderAdminImpl(BundleContext bundleContext) {
		m_objectFactoryBuilder = 
			new OSGiInitialContextFactoryBuilder(bundleContext, bundleContext);
	}

	@Override
	public Object getObjectInstance(Object refInfo, Name name, Context context, Map environment) throws NamingException {
		synchronized (m_objectFactoryBuilder) {
			Hashtable jndiEnvironment = new Hashtable();
			if (environment != null) {
				jndiEnvironment.putAll(environment);
			}
			ObjectFactory objectFactory = 
				m_objectFactoryBuilder.createObjectFactory(refInfo, jndiEnvironment);
			try {
				return objectFactory.getObjectInstance(refInfo, name, context, jndiEnvironment);
			}
			catch (Exception e) {
				NamingException namingException = new NamingException(
						"Error while attempting to resolve reference");
				namingException.initCause(e);
				throw namingException;
			}
		}
	}

	@Override
	public Object getObjectInstance(Object refInfo, Name name, Context context, Map environment, Attributes attributes) throws NamingException {
		synchronized (m_objectFactoryBuilder) {
			Hashtable jndiEnvironment = new Hashtable();
			if (environment != null) {
				jndiEnvironment.putAll(environment);
			}
			DirObjectFactory dirObjectFactory = m_objectFactoryBuilder
					.getDirObjectFactory(refInfo, jndiEnvironment);
			try {
				return dirObjectFactory.getObjectInstance(refInfo, name,
						context, jndiEnvironment, attributes);
			}
			catch (Exception e) {
				NamingException namingException = new NamingException(
						"Error while attempting to resolve reference");
				namingException.initCause(e);
				throw namingException;
			}
		}
	}
	
	@Override
	public void close() {
		synchronized (m_objectFactoryBuilder) {
			m_objectFactoryBuilder.close();
		}
	}
}

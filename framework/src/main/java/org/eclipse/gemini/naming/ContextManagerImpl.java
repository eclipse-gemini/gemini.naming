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

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.naming.directory.DirContext;
import javax.naming.spi.InitialContextFactory;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

class ContextManagerImpl implements CloseableContextManager {

	private static final Logger logger = Logger.getLogger(ContextManagerImpl.class.getName());
	
	private final OSGiInitialContextFactoryBuilder	m_builder;
	
	/* list of Context implementations */
	private final Map<Context, Object> m_listOfContexts =
		Collections.synchronizedMap(new WeakHashMap<Context, Object>());

	ContextManagerImpl(Bundle callingBundle, BundleContext implBundleContext) {
		// create a new builder for each client bundle
		// since the JNDI services (factories) should be accessed
		// by the JNDIContextManager service on behalf of the calling bundle
		m_builder = new OSGiInitialContextFactoryBuilder(callingBundle.getBundleContext(), implBundleContext);
	}


	@Override
	public Context newInitialContext() throws NamingException {
		synchronized (m_builder) {
			final Context initialContext = createNewInitialContext(new Hashtable());
			m_listOfContexts.put(initialContext, null);
			return initialContext;
		}
	}

	@Override
	public Context newInitialContext(Map environment)
			throws NamingException {
		synchronized (m_builder) {
			final Context initialContext = createNewInitialContext(environment);
			m_listOfContexts.put(initialContext, null);
			return initialContext;
		}
	}

	@Override
	public DirContext newInitialDirContext() throws NamingException {
		synchronized (m_builder) {
			Context contextToReturn = createNewInitialContext(new Hashtable());
			if (contextToReturn instanceof DirContext) {
				m_listOfContexts.put(contextToReturn, null);
				return (DirContext) contextToReturn;
			}
		}
		
		throw new NoInitialContextException("DirContext could not be created.  The matching InitialContextFactory did not create a matching type."); 
	}

	@Override
	public DirContext newInitialDirContext(Map environment) throws NamingException {
		synchronized (m_builder) {
			Context context = createNewInitialContext(environment);
			if (context instanceof DirContext) {
				m_listOfContexts.put(context, null);
				return (DirContext) context;
			}
		}
		
		throw new NoInitialContextException("DirContext could not be created.  The matching InitialContextFactory did not create a matching type.");
	}
	
	/**
	 * Closes all the known context implementations that have 
	 * been provided by this service.  
	 */
	@Override
	public void close() {
		// close known Context implementations
		synchronized (m_listOfContexts) {
			Set<Context> iterator = m_listOfContexts.keySet();
			if (iterator != null) {
				// call close() on all known contexts
				for (Context context : iterator) {
					try {
						context.close();
					} catch (NamingException e) {
						logger.log(
								Level.INFO,
								"NamingException occurred while trying to close an existing JNDI Context",
								e);
					}
				}
			}
		}
		
		m_listOfContexts.clear();
		
		synchronized (m_builder) {
			// close the Builder implementation
			m_builder.close();
		}
	}

	private Context createNewInitialContext(final Map environment)
			throws NamingException {
		final Hashtable jndiEnvironment = new Hashtable(environment);
		InitialContextFactory factory = 
			m_builder.createInitialContextFactory(jndiEnvironment);
		return factory.getInitialContext(jndiEnvironment);
	}

}

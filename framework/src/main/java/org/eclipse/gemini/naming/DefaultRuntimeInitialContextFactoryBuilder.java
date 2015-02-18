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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import javax.naming.spi.InitialContextFactoryBuilder;

/**
 * An InitialContextFactoryBuilder that is responsible for providing access to
 * the JNDI implementations made available by the JDK/JRE. These implementations
 * include LDAP, DNS, RMI, etc.
 * 
 * This Builder interface must be registered as a service by the JNDI
 * implementation.
 * 
 * @version $Revision$
 */
class DefaultRuntimeInitialContextFactoryBuilder implements
		InitialContextFactoryBuilder {

	private static final Logger	logger	= 
		Logger.getLogger(DefaultRuntimeInitialContextFactoryBuilder.class.getName());

	@Override
	public InitialContextFactory createInitialContextFactory(Hashtable environment) throws NamingException {
		if ((environment != null) && 
				(environment.get(Context.INITIAL_CONTEXT_FACTORY) != null)) {
			final String initialContextFactoryName = 
				(String) environment.get(Context.INITIAL_CONTEXT_FACTORY);

			// attempt to load this provider from the system classpath
			try {
				Class clazz = 
					getClass().getClassLoader().loadClass(initialContextFactoryName);
				return (InitialContextFactory) clazz.newInstance();
			}
			catch (Exception e) {
				logger.log(Level.FINEST,
							 "Error while trying to load system-level JNDI provider",
							 e);
			}
		}

		return null;
	}

}

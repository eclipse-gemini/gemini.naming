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

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapContext;
import javax.naming.spi.InitialContextFactory;
import java.util.Hashtable;

/**
 * A Wrapper implementation of InitialContextFactory, used to support URL
 * context factories in Gemini Naming.
 * 
 */
class InitialContextFactoryWrapper implements InitialContextFactory {
	private final InitialContextFactory	m_initialContextFactory;
	private final FactoryManager		m_factoryManager;

	public InitialContextFactoryWrapper(InitialContextFactory initialContextFactory, FactoryManager factoryManager) {
		m_initialContextFactory = initialContextFactory;
		m_factoryManager = factoryManager;
	}

	@Override
	public Context getInitialContext(Hashtable environment) throws NamingException {
		final Context contextToReturn = 
			m_initialContextFactory.getInitialContext(environment);

		if (contextToReturn instanceof LdapContext) {
			final LdapContextWrapperImpl ldapContextWrapper = new LdapContextWrapperImpl((LdapContext)contextToReturn, m_factoryManager);
			setupFactoryAssociation(ldapContextWrapper);
			return ServiceAwareContextFactory.createServiceAwareLdapContextWrapper(m_initialContextFactory, ldapContextWrapper, m_factoryManager);
		} else if(contextToReturn instanceof DirContext) {
			final DirContextWrapperImpl dirContextWrapper = new DirContextWrapperImpl((DirContext)contextToReturn, m_factoryManager);
			setupFactoryAssociation(dirContextWrapper);
			return ServiceAwareContextFactory.createServiceAwareDirContextWrapper(m_initialContextFactory, dirContextWrapper, m_factoryManager);
		} else {
			final ContextWrapperImpl contextWrapper = new ContextWrapperImpl(contextToReturn, m_factoryManager);
			setupFactoryAssociation(contextWrapper);
			return ServiceAwareContextFactory.createServiceAwareContextWrapper(m_initialContextFactory, contextWrapper, m_factoryManager);
		}
		
		
	}

	private void setupFactoryAssociation(final Context contextWrapper) {
		if(m_initialContextFactory instanceof BuilderSupportedInitialContextFactory) {
			BuilderSupportedInitialContextFactory builderFactory = 
				(BuilderSupportedInitialContextFactory)m_initialContextFactory;
			// this Context is backed by an InitialContextFactoryBuilder service
			m_factoryManager.associateFactoryService(builderFactory.getBuilder(), contextWrapper);
		} else {
			// this Context is backed by an InitialContextFactory service
			m_factoryManager.associateFactoryService(m_initialContextFactory, contextWrapper);
		}
	}
}

/*******************************************************************************
 * Copyright (c) 2013 SAP AG.
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
 *     Violeta Georgieva (SAP AG) - Initial Contribution
 ******************************************************************************/
package org.eclipse.gemini.naming;

import javax.naming.NamingException;
import javax.naming.ldap.Control;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;
import javax.naming.ldap.LdapContext;

public class LdapContextWrapperImpl extends DirContextWrapperImpl implements LdapContext {

	private final LdapContext m_ldapContext;

	LdapContextWrapperImpl(LdapContext ldapContext, FactoryManager factoryManager) {
		super(ldapContext, factoryManager);
		m_ldapContext = ldapContext;
	}

	public ExtendedResponse extendedOperation(ExtendedRequest request)
			throws NamingException {
		return m_ldapContext.extendedOperation(request);
	}

	public LdapContext newInstance(Control[] requestControls)
			throws NamingException {
		return m_ldapContext.newInstance(requestControls);
	}

	public void reconnect(Control[] connCtls) throws NamingException {
		m_ldapContext.reconnect(connCtls);
	}

	public Control[] getConnectControls() throws NamingException {
		return m_ldapContext.getConnectControls();
	}

	public void setRequestControls(Control[] requestControls)
			throws NamingException {
		m_ldapContext.setRequestControls(requestControls);
	}

	public Control[] getRequestControls() throws NamingException {
		return m_ldapContext.getRequestControls();
	}

	public Control[] getResponseControls() throws NamingException {
		return m_ldapContext.getResponseControls();
	}

}
